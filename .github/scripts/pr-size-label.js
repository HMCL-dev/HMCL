module.exports = async ({ github, context, core }) => {
    const owner = context.repo.owner;
    const repo = context.repo.repo;
    const ignoredFilePatterns = [
        /(?:^|\/)\.cnb\/.+\.ya?ml$/i,
        /(?:^|\/)\.gemini\/.+\.ya?ml$/i,
        /(?:^|\/)\.gitee\/.+\.ya?ml$/i,
        /(?:^|\/)\.github\/.+\.ya?ml$/i,
        /(?:^|\/)\.idea\/.+\.xml$/i,
        /(?:^|\/)\.editorconfig$/i,
        /(?:^|\/)\.gitattributes$/i,
        /(?:^|\/)\.gitignore$/i,
        /(?:^|\/).+\.kts$/i,
        /(?:^|\/).+\.md$/i,
        /(?:^|\/)LICENSE(?:_.*)?$/i,
        /(?:^|\/)gradlew(?:\.bat|\.ps1)?$/i,
        /(?:^|\/)gradle\/wrapper\/gradle-wrapper\.properties$/i,
        /(?:^|\/)lang\/.+\.properties$/i,
        /(?:^|\/)l10n\/.+\.properties$/i,
        /(?:^|\/)checkstyle\.xml$/i,
        /(?:^|\/).+\.(?:png|jpe?g|gif|ico|svg|json|ttf|woff2?|webp|avif|jxl|heic|mp4|webm|mp3|ogg|wav|txt|toml)$/i
    ];

    async function processPR(issueNumber) {
        const files = await github.paginate(github.rest.pulls.listFiles, {
            owner, repo, pull_number: issueNumber, per_page: 100,
        });

        const countedFiles = [];
        const ignoredFiles = [];
        let totalChanges = 0;

        for (const file of files) {
            const ignored = ignoredFilePatterns.some(pattern => pattern.test(file.filename));
            if (ignored) {
                ignoredFiles.push(file.filename);
                continue;
            }

            const fileChanges = file.changes ?? ((file.additions || 0) + (file.deletions || 0));
            totalChanges += fileChanges;
            countedFiles.push(`${file.filename} (${fileChanges})`);
        }

        let targetLabel;
        if (totalChanges <= 9) targetLabel = "1+";
        else if (totalChanges <= 39) targetLabel = "10+";
        else if (totalChanges <= 99) targetLabel = "40+";
        else if (totalChanges <= 499) targetLabel = "100+";
        else if (totalChanges <= 999) targetLabel = "500+";
        else if (totalChanges <= 1999) targetLabel = "1000+";
        else if (totalChanges <= 4999) targetLabel = "2000+";
        else targetLabel = "5000+";

        const currentLabels = await github.paginate(github.rest.issues.listLabelsOnIssue, {
            owner, repo, issue_number: issueNumber, per_page: 100,
        });

        const sizeLabels = currentLabels.map(label => label.name).filter(name => /^\d+\+$/.test(name));

        for (const name of sizeLabels) {
            if (name !== targetLabel) {
                await github.rest.issues.removeLabel({
                    owner, repo, issue_number: issueNumber, name,
                }).catch(() => { });
            }
        }

        if (!currentLabels.some(label => label.name === targetLabel)) {
            await github.rest.issues.addLabels({
                owner, repo, issue_number: issueNumber, labels: [targetLabel],
            });
        }

        core.info(`Pull request #${issueNumber} has ${totalChanges} counted changed lines, applied ${targetLabel}.`);
        core.info(`Ignored files: ${ignoredFiles.length ? ignoredFiles.join(", ") : "none"}`);
        core.info(`Counted files: ${countedFiles.length ? countedFiles.join(", ") : "none"}`);
    }

    if (context.eventName === "workflow_dispatch") {
        core.info("Manual trigger detected. Fetching all open pull requests...");

        const openPRs = await github.paginate(github.rest.pulls.list, {
            owner, repo, state: "open", per_page: 100,
        });

        const unlabelledPRs = openPRs.filter(pr => {
            return !pr.labels.some(label => /^\d+\+$/.test(label.name));
        });

        core.info(`Found ${openPRs.length} open pull request(s), ${unlabelledPRs.length} of them lack a size label.`);

        for (const pr of unlabelledPRs) {
            core.info(`----------------------------------------`);
            core.info(`Processing unlabelled pull request #${pr.number}: ${pr.title}`);
            await processPR(pr.number);
        }
    } else {
        const pr = context.payload.pull_request;
        if (!pr) {
            core.info("No pull request found in payload, skipping.");
            return;
        }
        await processPR(pr.number);
    }
}
