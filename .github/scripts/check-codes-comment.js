const fs = require("fs");

module.exports = async ({ github, context, core }) => {
    const prNumberPath = process.env.PULL_REQUEST_NUMBER_PATH;
    if (!prNumberPath || !fs.existsSync(prNumberPath) || !fs.statSync(prNumberPath).isFile()) {
        core.setFailed("Pull request number file not found.");
        return;
    }

    const reportsPath = process.env.REPORTS_PATH;
    if (!reportsPath || !fs.existsSync(reportsPath) || !fs.statSync(reportsPath).isFile()) {
        core.setFailed("Reports file not found.");
        return;
    }

    const PULL_REQUEST_NUMBER = parseInt(fs.readFileSync(prNumberPath, "utf8").trim(), 10);
    if (isNaN(PULL_REQUEST_NUMBER)) {
        core.setFailed("Failed to parse the pull request number.");
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
        core.setFailed(`Failed to fetch PR #${PULL_REQUEST_NUMBER}: ${error.message}`);
        return;
    }

    core.info(`Current PR state: ${pr.state}`);
    if (pr.state !== "open") {
        core.setFailed("The pull request is not open. Skipping comment creation.");
        return;
    }

    const run = context.payload.workflow_run;
    if (!run) {
        core.setFailed("context.payload.workflow_run is undefined. Ensure this script runs on the 'workflow_run' event.");
        return;
    }

    if (pr.head.sha !== run.head_sha) {
        core.setFailed("PR head SHA does not match the workflow run head SHA. Skipping.");
        return;
    }

    if (pr.head.ref !== run.head_branch) {
        core.setFailed("PR head branch does not match the workflow run head branch. Skipping.");
        return;
    }

    if (pr.head.repo.full_name !== run.head_repository.full_name) {
        core.setFailed("PR head repository fullname does not match the workflow run head repository fullname. Skipping.");
        return;
    }

    const comments = [];
    const maxCommentCount = 10;
    const reports = fs.readFileSync(reportsPath, "utf8").trim().split("\n").filter(Boolean);
    for (const report of reports) {
        if (comments.length >= maxCommentCount) break;
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
            const line = location.range?.start?.line;
            const codeText = code.value ? `[${code.value}] ` : "";

            if (path && line) {
                comments.push({
                    path,
                    line,
                    side: "RIGHT",
                    body: `**[${severityText}]** ${codeText}\n\n${message}`
                });
            }
        } catch (error) {
            core.warning(`Failed to parse report: ${error.message}`);
        }
    }

    if (comments.length === 0) {
        core.info("No diagnostics found to report as comments.");
        return;
    }

    core.info(`Successfully prepared ${comments.length} comment${comments.length > 1 ? "s" : ""} to post.`);

    try {
        await github.rest.pulls.createReview({
            owner,
            repo,
            pull_number: PULL_REQUEST_NUMBER,
            commit_id: run.head_sha,
            body: "🤖 Static analysis found the following issues (max count 10):",
            event: "COMMENT",
            comments: comments
        });
        core.info("Review comments successfully posted to the pull request.");
    } catch (error) {
        core.setFailed(`Failed to post review comments to GitHub: ${error.message}`);
        return;
    }
}
