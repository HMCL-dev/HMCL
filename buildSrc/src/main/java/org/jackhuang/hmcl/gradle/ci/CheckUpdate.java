/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2025 huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.gradle.ci;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// @author Glavo
public abstract class CheckUpdate extends DefaultTask {
    private static final Logger LOGGER = Logging.getLogger(CheckUpdate.class);

    private static final String WORKFLOW_JOB = "org.jenkinsci.plugins.workflow.job.WorkflowJob";
    private static final String WORKFLOW_MULTI_BRANCH_PROJECT = "org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject";
    private static final String FREE_STYLE_PROJECT = "hudson.model.FreeStyleProject";

    @Input
    public abstract Property<String> getApi();

    @Input
    public abstract Property<String> getTagPrefix();

    public CheckUpdate() {
        getOutputs().upToDateWhen(task -> false);
    }

    private static URI getLastSuccessfulBuildUri(String baseUri) {
        String suffix = "lastSuccessfulBuild/api/json";
        return URI.create(baseUri.endsWith("/")
                ? baseUri + suffix
                : baseUri + "/" + suffix
        );
    }

    @TaskAction
    public void run() throws Exception {
        String uri = getApi().get();
        LOGGER.info("Fetching metadata from {}", uri);

        BuildMetadata buildMetadata;

        try (var helper = new Helper()) {
            JsonObject body = Objects.requireNonNull(helper.fetch(URI.create(uri), JsonObject.class));
            String jobType = Objects.requireNonNull(body.getAsJsonPrimitive("_class"), "Missing _class property")
                    .getAsString();

            if (WORKFLOW_MULTI_BRANCH_PROJECT.equals(jobType)) {
                Pattern namePattern = Pattern.compile("release%2F3\\.\\d+");

                List<BuildMetadata> metadatas = Objects.requireNonNull(helper.gson.fromJson(body.getAsJsonObject("jobs"), new TypeToken<List<SubJobInfo>>() {
                        }), "jobs")
                        .stream()
                        .filter(it -> WORKFLOW_JOB.equals(it._class()))
                        .filter(it -> namePattern.matcher(it.name()).matches())
                        .filter(it -> !it.color().equals("disabled"))
                        .map(it -> {
                            try {
                                return fetchBuildInfo(helper, getLastSuccessfulBuildUri(it.url));
                            } catch (Throwable e) {
                                throw new GradleException("Failed to retrieve build info from " + it.url(), e);
                            }
                        }).sorted(Comparator.comparing(BuildMetadata::timestamp))
                        .toList();

                if (metadatas.isEmpty())
                    throw new GradleException("Failed to retrieve build metadata from " + uri);

                buildMetadata = metadatas.get(metadatas.size() - 1);
            } else if (WORKFLOW_JOB.equals(jobType) || FREE_STYLE_PROJECT.equals(jobType)) {
                buildMetadata = fetchBuildInfo(helper, getLastSuccessfulBuildUri(uri));
            } else {
                throw new GradleException("Unsupported job type: " + jobType);
            }
        }

        LOGGER.info("Build metadata found: {}", buildMetadata);

        String githubEnv = Objects.requireNonNullElse(System.getenv("GITHUB_ENV"), "");
        if (githubEnv.isBlank())
            LOGGER.warn("GITHUB_ENV is not set");

        try (PrintWriter writer = githubEnv.isBlank()
                ? null
                : new PrintWriter(Files.newBufferedWriter(Path.of(githubEnv), StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.APPEND))) {

            BiConsumer<String, String> addEnv = (name, value) -> {
                String item = name + "=" + value;
                LOGGER.info(item);
                if (writer != null)
                    writer.println(item);
            };

            addEnv.accept("HMCL_COMMIT_SHA", buildMetadata.revision());
            addEnv.accept("HMCL_VERSION", buildMetadata.version());
            addEnv.accept("HMCL_TAG_NAME", getTagPrefix().get() + buildMetadata.version());
        }
    }

    private record BuildMetadata(String version, String revision, long timestamp) {
    }

    private BuildMetadata fetchBuildInfo(Helper helper, URI uri) throws IOException, InterruptedException {
        LOGGER.info("Fetching build info from {}", uri);

        BuildInfo buildInfo = Objects.requireNonNull(helper.fetch(uri, BuildInfo.class), "build info");

        String revision = Objects.requireNonNullElse(buildInfo.actions(), List.<ActionInfo>of())
                .stream()
                .filter(action -> "hudson.plugins.git.util.BuildData".equals(action._class()))
                .map(ActionInfo::lastBuiltRevision)
                .map(BuiltRevision::SHA1)
                .findFirst()
                .orElseThrow(() -> new GradleException("Could not find revision"));
        if (!revision.matches("[0-9a-z]{40}"))
            throw new GradleException("Invalid revision: " + revision);

        Pattern fileNamePattern = Pattern.compile("HMCL-(?<version>\\d+(?:\\.\\d+)+)\\.jar");
        String version = Objects.requireNonNullElse(buildInfo.artifacts(), List.<ArtifactInfo>of())
                .stream()
                .map(ArtifactInfo::fileName)
                .map(fileNamePattern::matcher)
                .filter(Matcher::matches)
                .map(matcher -> matcher.group("version"))
                .findFirst()
                .orElseThrow(() -> new GradleException("Could not find .jar artifact"));

        return new BuildMetadata(version, revision, buildInfo.timestamp());
    }

    private static final class Helper implements AutoCloseable {
        private final HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();
        private final Gson gson = new Gson();

        private <T> T fetch(URI uri, Class<T> type) throws IOException, InterruptedException {
            HttpResponse<String> response = client.send(HttpRequest.newBuilder(uri).build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new IOException("Bad status code: " + response.statusCode());
            }

            return gson.fromJson(response.body(), type);
        }

        @Override
        public void close() throws Exception {
            // HttpClient implements AutoCloseable since Java 21
            if (((Object) client) instanceof AutoCloseable closeable) {
                closeable.close();
            }
        }
    }

    private record SubJobInfo(String _class, String name, String url, String color) {

    }

    private record BuildInfo(long number,
                             long timestamp,
                             List<ArtifactInfo> artifacts,
                             List<ActionInfo> actions
    ) {
    }

    record ArtifactInfo(String fileName) {
    }

    record ActionInfo(String _class, BuiltRevision lastBuiltRevision) {
    }

    record BuiltRevision(String SHA1) {
    }
}
