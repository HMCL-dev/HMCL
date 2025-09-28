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
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// @author Glavo
public abstract class CheckUpdate extends DefaultTask {
    private static final Logger LOGGER = Logging.getLogger(CheckUpdate.class);

    @Input
    public abstract Property<String> getApi();

    @Input
    public abstract Property<String> getTagPrefix();

    public CheckUpdate() {
        getOutputs().upToDateWhen(task -> false);
    }

    private static <T> T fetch(URI uri, Class<T> type) throws IOException, InterruptedException {
        // // HttpClient implements Closeable since Java 21
        //noinspection resource
        var client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();

        HttpResponse<String> response = client.send(HttpRequest.newBuilder(uri).build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new IOException("Bad status code: " + response.statusCode());
        }

        return new Gson().fromJson(response.body(), type);
    }

    @TaskAction
    public void run() throws IOException, InterruptedException {
        String githubEnv = Objects.requireNonNullElse(System.getenv("GITHUB_ENV"), "");
        if (githubEnv.isBlank())
            LOGGER.warn("GITHUB_ENV is not set");

        String uri = getApi().get();
        LOGGER.info("Fetching metadata from {}", uri);
        BuildInfo buildInfo = Objects.requireNonNull(fetch(URI.create(uri), BuildInfo.class),
                "Could not fetch build info");

        try (PrintWriter writer = githubEnv.isBlank()
                ? null
                : new PrintWriter(Files.newBufferedWriter(Path.of(githubEnv), StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.APPEND))) {

            BiConsumer<String, String> addEnv = (name, value) -> {
                String item = name + "=" + value;
                LOGGER.info(item);
                if (writer != null)
                    writer.println(item);
            };

            String revision = Objects.requireNonNullElse(buildInfo.actions(), List.<BuildInfo.ActionInfo>of())
                    .stream()
                    .filter(action -> "hudson.plugins.git.util.BuildData".equals(action._class))
                    .map(BuildInfo.ActionInfo::lastBuiltRevision)
                    .map(BuildInfo.ActionInfo.BuiltRevision::SHA1)
                    .findFirst()
                    .orElseThrow(() -> new GradleException("Could not find revision"));
            if (revision.matches("[0-9a-z]{40}"))
                addEnv.accept("HMCL_COMMIT_SHA", revision);
            else
                throw new GradleException("Invalid revision: " + revision);

            Pattern fileNamePattern = Pattern.compile("HMCL-(?<version>\\d+(?:\\.\\d+)+)\\.jar");
            String version = Objects.requireNonNullElse(buildInfo.artifacts(), List.<BuildInfo.ArtifactInfo>of())
                    .stream()
                    .map(BuildInfo.ArtifactInfo::fileName)
                    .map(fileNamePattern::matcher)
                    .filter(Matcher::matches)
                    .map(matcher -> matcher.group("version"))
                    .findFirst()
                    .orElseThrow(() -> new GradleException("Could not find .jar artifact"));
            addEnv.accept("HMCL_VERSION", version);
            addEnv.accept("HMCL_TAG_NAME", getTagPrefix().get() + version);
        }
    }

    private record BuildInfo(long number,
                             List<ArtifactInfo> artifacts,
                             List<ActionInfo> actions
    ) {

        record ArtifactInfo(String fileName) {
        }

        record ActionInfo(String _class, BuiltRevision lastBuiltRevision) {
            record BuiltRevision(String SHA1) {
            }
        }
    }

}
