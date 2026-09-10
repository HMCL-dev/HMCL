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
    public abstract Property<String> getUri();

    public CheckUpdate() {
        getOutputs().upToDateWhen(task -> false);
    }

    private static URI toURI(String baseUri, String suffix) {
        return URI.create(baseUri.endsWith("/")
                ? baseUri + suffix
                : baseUri + "/" + suffix
        );
    }

    private static String concatUri(String base, String... others) {
        var builder = new StringBuilder(base);
        for (String other : others) {
            if (builder.charAt(builder.length() - 1) != '/') {
                builder.append('/');
            }
            builder.append(Objects.requireNonNull(other));
        }
        return builder.toString();
    }

    @TaskAction
    public void run() throws Exception {
        String uri = getUri().get();
        URI apiUri = URI.create(concatUri(uri, "api", "json"));
        LOGGER.quiet("Fetching metadata from {}", apiUri);

        BuildMetadata buildMetadata;

        try (var helper = new Helper()) {
            JsonObject body = Objects.requireNonNull(helper.fetch(apiUri, JsonObject.class));
            String jobType = Objects.requireNonNull(body.getAsJsonPrimitive("_class"), "Missing _class property")
                    .getAsString();

            if (WORKFLOW_MULTI_BRANCH_PROJECT.equals(jobType)) {
                Pattern namePattern = Pattern.compile("release%2F3\\.\\d+");

                List<BuildMetadata> metadatas = Objects.requireNonNull(helper.gson.fromJson(body.get("jobs"), new TypeToken<List<SubJobInfo>>() {
                        }), "jobs")
                        .stream()
                        .filter(it -> WORKFLOW_JOB.equals(it._class()))
                        .filter(it -> namePattern.matcher(it.name()).matches())
                        .filter(it -> !it.color().equals("disabled"))
                        .map(it -> {
                            try {
                                return fetchBuildInfo(helper, it.url);
                            } catch (Throwable e) {
                                throw new GradleException("Failed to fetch build info from " + it.url(), e);
                            }
                        }).sorted(Comparator.comparing(BuildMetadata::timestamp))
                        .toList();

                if (metadatas.isEmpty())
                    throw new GradleException("Failed to fetch build metadata from " + apiUri);

                buildMetadata = metadatas.get(metadatas.size() - 1);
            } else if (WORKFLOW_JOB.equals(jobType) || FREE_STYLE_PROJECT.equals(jobType)) {
                buildMetadata = fetchBuildInfo(helper, uri);
            } else {
                throw new GradleException("Unsupported job type: " + jobType);
            }
        }

        LOGGER.quiet("Build metadata found: {}", buildMetadata);

        String githubEnv = Objects.requireNonNullElse(System.getenv("GITHUB_ENV"), "");
        if (githubEnv.isBlank())
            LOGGER.warn("GITHUB_ENV is not set");

        try (PrintWriter writer = githubEnv.isBlank()
                ? null
                : new PrintWriter(Files.newBufferedWriter(Path.of(githubEnv), StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.APPEND))) {

            BiConsumer<String, String> addEnv = (name, value) -> {
                String item = name + "=" + value;
                LOGGER.quiet(item);
                if (writer != null)
                    writer.println(item);
            };

            addEnv.accept("HMCL_COMMIT_SHA", buildMetadata.revision());
            addEnv.accept("HMCL_VERSION", buildMetadata.version());
            addEnv.accept("HMCL_TAG_NAME", "v" + buildMetadata.version());
            addEnv.accept("HMCL_CI_DOWNLOAD_BASE_URI", buildMetadata.downloadBaseUri);
        }
    }

    private record BuildMetadata(String version, String revision, long timestamp, String downloadBaseUri) {
    }

    private BuildMetadata fetchBuildInfo(Helper helper, String jobUri) throws IOException, InterruptedException {
        URI uri = URI.create(concatUri(jobUri, "lastSuccessfulBuild", "api", "json"));

        LOGGER.quiet("Fetching build info from {}", uri);

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
        ArtifactInfo jarArtifact = Objects.requireNonNullElse(buildInfo.artifacts(), List.<ArtifactInfo>of()).stream()
                .filter(it -> fileNamePattern.matcher(it.fileName()).matches())
                .findFirst()
                .orElseThrow(() -> new GradleException("Could not find .jar artifact"));

        String fileName = jarArtifact.fileName();
        String relativePath = jarArtifact.relativePath();
        if (!relativePath.endsWith("/" + fileName)) {
            throw new GradleException("Invalid artifact relative path: " + jarArtifact);
        }

        Matcher matcher = fileNamePattern.matcher(fileName);
        if (!matcher.matches()) {
            throw new AssertionError("Artifact: " + jarArtifact.fileName());
        }

        String version = matcher.group("version");

        String downloadBaseUrl = concatUri(buildInfo.url(), "artifact",
                relativePath.substring(0, relativePath.length() - fileName.length() - 1));

        return new BuildMetadata(version, revision, buildInfo.timestamp(), downloadBaseUrl);
    }

    private static final class Helper implements AutoCloseable {
        private final HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();
        private final Gson gson = new Gson();

        private <T> T fetch(URI uri, Class<T> type) throws IOException, InterruptedException {
            HttpResponse<String> response = client.send(HttpRequest.newBuilder(uri).build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new IOException("Bad status code " + response.statusCode() + " for " + uri);
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

    private record BuildInfo(String url,
                             long number,
                             long timestamp,
                             List<ArtifactInfo> artifacts,
                             List<ActionInfo> actions
    ) {
    }

    record ArtifactInfo(String fileName, String relativePath) {
    }

    record ActionInfo(String _class, BuiltRevision lastBuiltRevision) {
    }

    record BuiltRevision(String SHA1) {
    }
}
