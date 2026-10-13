/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026 huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.gradle.component;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.annotations.SerializedName;
import org.apache.maven.artifact.versioning.ComparableVersion;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.internal.impldep.org.bouncycastle.asn1.eac.CVCertificate;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/// @author Glavo
@NotNullByDefault
public abstract class UpdateLiteLoaderVersionList extends DefaultTask {

    private static final String LITELOADER_LIST = "https://dl.liteloader.com/versions/versions.json";

    private static String fetch(HttpClient client, String url) throws IOException, InterruptedException {
        HttpResponse<String> response = client.send(HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );
        if (response.statusCode() != 200) {
            throw new IOException("Failed to fetch LiteLoader version list: " + response.statusCode());
        }
        return response.body();
    }

    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    @TaskAction
    public void run() throws Exception {
        Path outputFile = getOutputFile().getAsFile().get().toPath();

        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        try {
            String body = fetch(client, LITELOADER_LIST);
            LiteLoaderVersionsRoot root = new Gson().fromJson(body, LiteLoaderVersionsRoot.class);

            var records = new ArrayList<LiteLoaderRemoteVersionRecord>();

            for (Map.Entry<String, LiteLoaderGameVersions> entry : root.versions().entrySet()) {
                String version = entry.getKey();
                LiteLoaderGameVersions gameVersions = entry.getValue();

                if (gameVersions.snapshots != null) {
                    loadSnapshotVersion(records, client, version, gameVersions.snapshots.liteLoader().get("latest"));
                }

                if (gameVersions.repository != null && gameVersions.artifacts != null) {
                    loadArtifactVersion(records, client, version, gameVersions.repository, gameVersions.artifacts);
                }
            }

            records.sort(Comparator.naturalOrder());

            Files.createDirectories(outputFile.getParent());
            Files.writeString(outputFile, new GsonBuilder().setPrettyPrinting().create()
                    .toJson(records));
        } finally {
            // Since Java21, HttpClient implements AutoCloseable: https://bugs.openjdk.org/browse/JDK-8304165
            if (((Object) client) instanceof AutoCloseable closeable) {
                closeable.close();
            }
        }
    }

    // Workaround for https://github.com/HMCL-dev/HMCL/issues/3147: Some LiteLoader artifacts aren't published on http:s//dl.liteloader.com/repo
    private static final String SNAPSHOT_METADATA = "https://repo.mumfrey.com/content/repositories/snapshots/com/mumfrey/liteloader/%s-SNAPSHOT/maven-metadata.xml";
    private static final String SNAPSHOT_FILE = "https://repo.mumfrey.com/content/repositories/snapshots/com/mumfrey/liteloader/%s-SNAPSHOT/liteloader-%s-%s-%s-release.jar";

    private void loadSnapshotVersion(
            List<LiteLoaderRemoteVersionRecord> records,
            HttpClient client, String gameVersion, LiteLoaderVersion v) throws IOException, InterruptedException {
        String root = fetch(client, String.format(SNAPSHOT_METADATA, gameVersion));
        Document document = Jsoup.parseBodyFragment(root);
        String timestamp = Objects.requireNonNull(document.select("timestamp"), "timestamp").text();
        String buildNumber = Objects.requireNonNull(document.select("buildNumber"), "buildNumber").text();

        records.add(new LiteLoaderRemoteVersionRecord(
                gameVersion, timestamp + "-" + buildNumber, true,
                String.format(SNAPSHOT_FILE, gameVersion, gameVersion, timestamp, buildNumber),
                v.tweakClass(), v.libraries()
        ));
    }

    private void loadArtifactVersion(
            List<LiteLoaderRemoteVersionRecord> records,
            HttpClient client, String gameVersion, LiteLoaderRepository repository, LiteLoaderBranch branch) {

        for (Map.Entry<String, LiteLoaderVersion> entry : branch.liteLoader().entrySet()) {
            String branchName = entry.getKey();
            LiteLoaderVersion v = entry.getValue();
            if ("latest".equals(branchName))
                continue;

            records.add(new LiteLoaderRemoteVersionRecord(
                    gameVersion, v.version(), false,
                    repository.url().replaceFirst("^http:", "https:") + "com/mumfrey/liteloader/" + gameVersion + "/" + v.file(),
                    v.tweakClass(), v.libraries()
            ));
        }
    }

    private record LiteLoaderRemoteVersionRecord(
            String gameVersion,
            String version,
            boolean snapshot,
            String url,
            String tweakClass,
            JsonArray libraries
    ) implements Comparable<LiteLoaderRemoteVersionRecord> {

        @Override
        public int compareTo(LiteLoaderRemoteVersionRecord that) {
            int c = new ComparableVersion(this.gameVersion).compareTo(new ComparableVersion(that.gameVersion));
            if (c != 0)
                return c;

            return new ComparableVersion(this.version).compareTo(new ComparableVersion(that.version));
        }
    }

    private record LiteLoaderVersion(
            String tweakClass, String file, String version, String md5, String timestamp,
            int lastSuccessfulBuild, JsonArray libraries) {

    }

    private record LiteLoaderBranch(@SerializedName("libraries") JsonArray libraries,
                                    @SerializedName("com.mumfrey:liteloader") Map<String, LiteLoaderVersion> liteLoader) {

    }

    private record LiteLoaderRepository(@SerializedName("stream") String stream,
                                        @SerializedName("type") String type,
                                        @SerializedName("url") String url,
                                        @SerializedName("classifier") String classifier) {

    }

    private record LiteLoaderGameVersions(@SerializedName("repo") @Nullable LiteLoaderRepository repository,
                                          @SerializedName("artefacts") @Nullable LiteLoaderBranch artifacts,
                                          @SerializedName("snapshots") @Nullable LiteLoaderBranch snapshots) {

    }

    private record LiteLoaderVersionsMeta(@SerializedName("description") String description,
                                          @SerializedName("authors") String authors,
                                          @SerializedName("url") String url) {

    }

    private record LiteLoaderVersionsRoot(
            Map<String, LiteLoaderGameVersions> versions,
            LiteLoaderVersionsMeta meta
    ) {

    }
}
