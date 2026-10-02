const fs = require("fs");

module.exports = async ({ github, context, core }) => {
    const versionsFilePath = "HMCLCore/src/main/resources/assets/game/versions.txt";

    // Read the last line of versions.txt (the latest known version)
    const content = fs.readFileSync(versionsFilePath, "utf8");
    const lines = content.split("\n").filter(line => line.length > 0);
    const lastVersion = lines[lines.length - 1];
    core.info(`Last version in versions.txt: ${lastVersion}`);

    // Fetch the official Minecraft version manifest
    const response = await fetch("https://piston-meta.mojang.com/mc/game/version_manifest.json");
    if (!response.ok) {
        core.setFailed(`Failed to fetch version manifest: ${response.status} ${response.statusText}`);
        return;
    }
    const manifest = await response.json();

    const latestRelease = manifest.latest.release;
    core.info(`Latest release from manifest: ${latestRelease}`);

    // Skip if versions.txt is already up to date
    if (lastVersion === latestRelease) {
        core.info("versions.txt is already up to date.");
        core.setOutput("updated", "false");
        return;
    }

    // Collect versions between lastVersion (exclusive) and latestRelease (inclusive).
    // The manifest is ordered newest-first, so entries above latestRelease
    // (e.g. snapshots published after the latest release) are discarded.
    const newVersions = [];
    let reachedRelease = false;
    for (const version of manifest.versions) {
        if (!reachedRelease) {
            // Skip anything newer than the latest release
            if (version.id !== latestRelease) {
                continue;
            }
            reachedRelease = true;
        }
        if (version.id === lastVersion) {
            break;
        }
        newVersions.push(version.id);
    }

    if (!reachedRelease) {
        core.warning(`Latest release ${latestRelease} was not found in the manifest versions list.`);
    }

    if (newVersions.length === 0) {
        core.info("No new versions found to add.");
        core.setOutput("updated", "false");
        return;
    }

    // Reverse to chronological order (oldest first) for appending
    newVersions.reverse();

    // Append the new versions to versions.txt
    let newContent = content;
    if (!newContent.endsWith("\n")) {
        newContent += "\n";
    }
    newContent += newVersions.join("\n") + "\n";
    fs.writeFileSync(versionsFilePath, newContent);

    core.info(`Added ${newVersions.length} new version(s):`);
    for (const v of newVersions) {
        core.info(`  ${v}`);
    }

    core.setOutput("updated", "true");
};
