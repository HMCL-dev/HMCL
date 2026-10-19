const fs = require("fs");

module.exports = async ({ github, context, core }) => {
    const prNumberPath = process.env.PULL_REQUEST_NUMBER_PATH;
    if (!prNumberPath || !fs.existsSync(prNumberPath) || !fs.statSync(prNumberPath).isFile()) {
        core.info("Pull request number file not found or invalid path.");
        return;
    }

    const reportsPath = process.env.REPORTS_PATH;
    if (!reportsPath || !fs.existsSync(reportsPath) || !fs.statSync(reportsPath).isFile()) {
        core.info("Static analysis reports file not found or invalid path.");
        return;
    }

    const PULL_REQUEST_NUMBER = parseInt(fs.readFileSync(prNumberPath, "utf8").trim(), 10);
    if (isNaN(PULL_REQUEST_NUMBER)) {
        core.error("Failed to parse the pull request number into a valid integer.");
        return;
    }

    const { owner, repo } = context.repo;
    core.info(`Processing pull request #${PULL_REQUEST_NUMBER} for repository ${owner}/${repo}`);

    let pr;
    try {
        const response = await github.rest.pulls.get({
            owner,
            repo,
            pull_number: PULL_REQUEST_NUMBER,
        });
        pr = response.data;
    } catch (error) {
        core.error(`Failed to fetch details for PR #${PULL_REQUEST_NUMBER}: ${error.message}`);
        return;
    }

    core.info(`Current pull request state: ${pr.state}`);
    if (pr.state !== "open") {
        core.info("The pull request is not open.");
        return;
    }

    const run = context.payload.workflow_run;
    if (!run) {
        core.error("context.payload.workflow_run is undefined.");
        return;
    }

    if (pr.head.sha !== run.head_sha) {
        core.warning(`PR head SHA (${pr.head.sha}) does not match workflow run head SHA (${run.head_sha}).`);
        return;
    }

    if (pr.head.ref !== run.head_branch) {
        core.warning(`PR head branch (${pr.head.ref}) does not match workflow run head branch (${run.head_branch}).`);
        return;
    }

    if (pr.head.repo.full_name !== run.head_repository.full_name) {
        core.warning(`PR head repository (${pr.head.repo.full_name}) does not match workflow run repository (${run.head_repository.full_name}).`);
        return;
    }

    const changedFiles = new Set();
    try {
        core.info("Fetching the list of changed files in the pull request...");
        const files = await github.paginate(github.rest.pulls.listFiles, {
            owner,
            repo,
            pull_number: PULL_REQUEST_NUMBER,
        });

        for (const file of files) {
            changedFiles.add(file.filename);
        }
        core.info(`Successfully fetched ${changedFiles.size} changed file(s).`);
    } catch (error) {
        core.error(`Failed to fetch changed files for the pull request: ${error.message}`);
        return;
    }

    const maxCommentCount = 10;
    const commentFlag = `<!-- check codes comment flag (#${PULL_REQUEST_NUMBER}) -->`;
    const comments = [`${commentFlag}\n\nStatic analysis found the following issues (max comment count ${maxCommentCount}):`];

    const reports = fs.readFileSync(reportsPath, "utf8").trim().split("\n").filter(Boolean);
    core.info(`Parsing ${reports.length} report line(s) from the static analysis output...`);

    for (const report of reports) {
        if (comments.length - 1 >= maxCommentCount) {
            core.info(`Reached the maximum comment limit (${maxCommentCount}).`);
            break;
        }

        try {
            const data = JSON.parse(report);
            if (typeof data !== "object" || Array.isArray(data)) continue;

            const { location, message, severity, code } = data;
            if (typeof location !== "object" || Array.isArray(location)) continue;
            if (typeof message !== "string") continue;
            const severities = ["UNKNOWN_SEVERITY", "ERROR", "WARNING", "INFO", 0, 1, 2, 3];
            if (!severities.includes(severity)) continue;
            const severityText = typeof severity === "string" ? severity : severities[severity];
            if (typeof code !== "object" || Array.isArray(code)) continue;

            const path = location.path;
            if (!changedFiles.has(path)) continue;

            const startLine = location.range?.start?.line;
            if (typeof startLine !== "number") continue;

            const endLine = location.range?.end?.line ?? startLine;
            if (typeof endLine !== "number") continue;

            const codeText = code.value ? `[${code.value}] ` : "";
            comments.push(`https://github.com/${owner}/${repo}/blob/${run.head_sha}/${path}#L${startLine}-L${endLine}\n\n**[${severityText}]** ${codeText}\n\n${message}`);
        } catch (error) {
            core.warning(`Skipping malformed report line: ${error.message}`);
        }
    }

    if (comments.length <= 1) {
        core.info("No relevant diagnostics found to report.");
        comments.push("None.");
    } else {
        core.info(`Successfully matched and processed ${comments.length - 1} issue(s) within changed files.`);
    }

    let commentId = null;
    try {
        core.info("Searching for existing review comment on the pull request...");
        const iterator = github.paginate.iterator(github.rest.issues.listComments, {
            owner,
            repo,
            issue_number: PULL_REQUEST_NUMBER,
            per_page: 100
        });

        for await (const item of iterator) {
            const comments = item.data;
            const comment = comments.find(comment => comment.body && comment.body.startsWith(commentFlag));
            if (comment) {
                commentId = comment.id;
                core.info(`Found existing review comment (${commentId}).`);
                break;
            }
        }
    } catch (error) {
        core.warning(`Failed to fetch existing comments: ${error.message}`);
    }

    const commentBody = comments.join("\n\n---\n\n");
    if (commentId !== null) {
        try {
            await github.rest.issues.updateComment({
                owner,
                repo,
                comment_id: commentId,
                body: commentBody
            });
            core.info(`Successfully updated existing review comment (${commentId}).`);
        } catch (error) {
            core.warning(`Failed to update existing review comment (${commentId}): ${error.message}.`);
            commentId = null;
        }
    }

    if (commentId === null) {
        try {
            await github.rest.issues.createComment({
                owner,
                repo,
                issue_number: PULL_REQUEST_NUMBER,
                body: commentBody
            });
            core.info("Successfully posted a new review comment to the pull request.");
        } catch (error) {
            core.error(`Failed to post review comment: ${error.message}`);
            return;
        }
    }
}
