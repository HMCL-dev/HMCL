/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.jackhuang.hmcl.upgrade;

import com.google.gson.annotations.SerializedName;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.util.io.HttpRequest;
import org.jackhuang.hmcl.util.io.JarUtils;

import java.io.IOException;

import static org.jackhuang.hmcl.util.Pair.pair;

public final class GitHubSHAChecker {
    private GitHubSHAChecker() {
    }

    private static class GitHubWorkflowRunLookup {
        private static class GitHubWorkflowRun {
            @SerializedName("head_sha")
            private String sha;

            @SerializedName("id")
            private long id;
        }

        @SerializedName("workflow_runs")
        private GitHubWorkflowRun[] runs;
    }

    private static class GitHubArtifactsLookup {
        private static class GitHubArtifact {
            @SerializedName("url")
            private String url;
        }

        @SerializedName("artifacts")
        private GitHubArtifact[] artifacts;
    }

    private static final String GITHUB_TOKEN = "Bearer " + JarUtils.getManifestAttribute("GitHub-Api-Token", "");

    private static final String JAVA_CI_WORKFLOW = "gradle.yml";

    private static final String WORKFLOW_LOOKUP_URL = String.format("https://api.github.com/repos/%s/actions/workflows/%s/runs", Metadata.OFFICIAL_REPOSITORY, JAVA_CI_WORKFLOW);

    private static final String ARTIFACTS_LOOKUP_URL = String.format("https://api.github.com/repos/%s/actions/runs/%%d/artifacts", Metadata.OFFICIAL_REPOSITORY);
    
    private static Boolean isSelfVerified = null;

    public static boolean isSelfVerified() throws IOException {
        if (isSelfVerified != null) {
            return isSelfVerified;
        }

        for (int page = 1; ; page++) {
            GitHubWorkflowRunLookup lookup = HttpRequest.GET(WORKFLOW_LOOKUP_URL, pair("branch", Metadata.OFFICIAL_BRANCH), pair("event", "push"), pair("head_sha", Metadata.GITHUB_SHA), pair("page", Integer.toString(page)))
                    .header("Accept", "application/vnd.github+json")
                    .authorization(GITHUB_TOKEN)
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .getJson(GitHubWorkflowRunLookup.class);

            if (lookup.runs.length == 0) {
                isSelfVerified = false;
                return false;
            }

            for (GitHubWorkflowRunLookup.GitHubWorkflowRun run : lookup.runs) {
                if (run.sha.equals(Metadata.GITHUB_SHA)) {
                    isSelfVerified = true;
                    return true;
                }
            }
        }
    }
}
