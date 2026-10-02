const fs = require("fs");

const gameVersionsFilePath = "HMCL/src/main/resources/assets/chunkbase/game_versions.json";

// Chunkbase rejects requests without a browser-like User-Agent, so pretend to be a regular desktop browser.
const browserUserAgent =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36";

// Chunkbase apps to check, mapped to the corresponding key in game_versions.json.
const chunkbaseApps = [
    { url: "https://www.chunkbase.com/apps/seed-map", key: "seed-map" },
    { url: "https://www.chunkbase.com/apps/stronghold-finder", key: "stronghold-finder" },
    { url: "https://www.chunkbase.com/apps/nether-fortress-finder", key: "nether-fortress" },
    { url: "https://www.chunkbase.com/apps/endcity-finder", key: "end-city" },
];

// Matches Java Edition options such as <option value="java//java_1_21_9"> and captures "1_21_9".
// The "java//" prefix deliberately excludes latest/experimental, large biomes and bedrock options.
const javaVersionOptionPattern = /<option\s+value="java\/\/java_(\d+(?:_\d+)*)"/g;

// Extracts the supported Java Edition versions (newest first) from a Chunkbase app page.
async function fetchJavaVersions(url) {
    const response = await fetch(url, {
        headers: {
            "User-Agent": browserUserAgent,
            "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
        }
    });
    if (!response.ok) {
        throw new Error(`Failed to fetch ${url}: ${response.status} ${response.statusText}`);
    }

    const html = await response.text();
    const versions = [];
    const seen = new Set();
    let match;
    while ((match = javaVersionOptionPattern.exec(html)) !== null) {
        // e.g. "1_21_9" -> "1.21.9"
        const version = match[1].replace(/_/g, ".");
        if (!seen.has(version)) {
            seen.add(version);
            versions.push(version);
        }
    }

    if (versions.length === 0) {
        throw new Error(`No Java version options found on ${url}, the page structure may have changed.`);
    }

    return versions;
}

module.exports = async ({ github, context, core }) => {
    const gameVersions = JSON.parse(fs.readFileSync(gameVersionsFilePath, "utf8"));

    let updated = false;
    for (const { url, key } of chunkbaseApps) {
        let latestVersions;
        try {
            latestVersions = await fetchJavaVersions(url);
        } catch (e) {
            // Keep checking the other apps even if one page fails to load.
            core.setFailed(e);
            continue;
        }

        const currentVersions = gameVersions[key] ?? [];
        const upToDate = currentVersions.length === latestVersions.length &&
            currentVersions.every((version, index) => version === latestVersions[index]);
        if (upToDate) {
            core.info(`${key} is already up to date.`);
            continue;
        }

        core.info(`${key} updated:`);
        core.info(`  old: ${JSON.stringify(currentVersions)}`);
        core.info(`  new: ${JSON.stringify(latestVersions)}`);
        gameVersions[key] = latestVersions;
        updated = true;
    }

    if (updated) {
        fs.writeFileSync(gameVersionsFilePath, JSON.stringify(gameVersions, null, 2) + "\n");
    }

    core.setOutput("updated", updated ? "true" : "false");
};
