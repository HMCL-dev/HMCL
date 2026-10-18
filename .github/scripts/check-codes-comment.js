const fs = require("fs");

module.exports = async ({ github, context, core }) => {
    const prNumberPath = process.env.PULL_REQUEST_NUMBER_PATH;
    if (!prNumberPath || !fs.existsSync(prNumberPath) || !fs.statSync(prNumberPath).isFile()) {
        core.info("Pull request number file not found.");
        return;
    }

    const reportsPath = process.env.REPORTS_PATH;
    if (!reportsPath || !fs.existsSync(reportsPath) || !fs.statSync(reportsPath).isFile()) {
        core.info("Reports file not found.");
        return;
    }

    const PULL_REQUEST_NUMBER = parseInt(fs.readFileSync(prNumberPath, "utf8").trim(), 10);
    if (isNaN(PULL_REQUEST_NUMBER)) {
        core.info("Failed to parse the pull request number.");
        return;
    }

    const { owner, repo } = context.repo;

    let pr;
    try {
        const response = await github.rest.pulls.get({
            owner,
            repo,
            pull_number: PULL_REQUEST_NUMBER,
        });
        pr = response.data;
    } catch (error) {
        core.info(`Failed to fetch PR #${PULL_REQUEST_NUMBER}: ${error.message}`);
        return;
    }

    core.info(`Current PR state: ${pr.state}`);
    if (pr.state !== "open") {
        core.info("The pull request is not open. Skipping comment creation.");
        return;
    }

    const run = context.payload.workflow_run;
    if (!run) {
        core.info("context.payload.workflow_run is undefined. Ensure this script runs on the 'workflow_run' event.");
        return;
    }

    if (pr.head.sha !== run.head_sha) {
        core.info("PR head SHA does not match the workflow run head SHA. Skipping.");
        return;
    }

    if (pr.head.ref !== run.head_branch) {
        core.info("PR head branch does not match the workflow run head branch. Skipping.");
        return;
    }

    if (pr.head.repo.full_name !== run.head_repository.full_name) {
        core.info("PR head repository fullname does not match the workflow run head repository fullname. Skipping.");
        return;
    }

    const changedFiles = new Set();
    try {
        const files = await github.paginate(github.rest.pulls.listFiles, {
            owner,
            repo,
            pull_number: PULL_REQUEST_NUMBER,
        });

        for (const file of files) {
            changedFiles.add(file.filename);
        }
    } catch (error) {
        core.info(`Failed to fetch PR files: ${error.message}`);
        return;
    }

    const maxCommentCount = 10;
    const comments = [`<!-- check codes comment flag -->Static analysis found the following issues (max comment count ${maxCommentCount}):`];
    const reports = fs.readFileSync(reportsPath, "utf8").trim().split("\n").filter(Boolean);
    for (const report of reports) {
        if (comments.length > maxCommentCount) break;
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
            core.warning(`Failed to parse report: ${error.message}`);
        }
    }

    if (comments.length <= 1) {
        core.info("No diagnostics found to report as comments.");
        comments.push("None.");
    }

    try {
        await github.rest.issues.createComment({
            owner,
            repo,
            issue_number: PULL_REQUEST_NUMBER,
            body: comments.join("\n\n---\n\n"),
        });
        core.info("Review comments successfully posted to the pull request.");
    } catch (error) {
        core.info(`Failed to post review comments to GitHub: ${error.message}`);
        return;
    }
}
