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
package org.jackhuang.hmcl.download.liteloader;

import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.download.RemoteVersion;
import org.jackhuang.hmcl.download.VersionList;
import org.jackhuang.hmcl.task.GetTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.io.HttpRequest;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * @author huangyuhui
 */
public final class LiteLoaderVersionList extends VersionList<LiteLoaderRemoteVersion> {

    private final DownloadProvider downloadProvider;

    public LiteLoaderVersionList(DownloadProvider downloadProvider) {
        this.downloadProvider = downloadProvider;
    }

    @Override
    public boolean hasType() {
        return true;
    }

    public static final String LITELOADER_LIST = "https://dl.liteloader.com/versions/versions.json";

    @Override
    public Task<?> refreshAsync(String gameVersion) {
        return new GetTask(downloadProvider.injectURLWithCandidates(LITELOADER_LIST))
                .thenGetJsonAsync(LiteLoaderVersionsRoot.class)
                .thenAcceptAsync(root -> {
                    LiteLoaderGameVersions versions = root.getVersions().get(gameVersion);
                    if (versions == null) {
                        return;
                    }

                    LiteLoaderRemoteVersion snapshot = null;
                    if (versions.getSnapshots() != null) {
                        try {
                            snapshot = loadSnapshotVersion(gameVersion, versions.getSnapshots().getLiteLoader().get("latest"));
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    }

                    lock.writeLock().lock();
                    try {
                        this.versions.clear();

                        if (versions.getRepoitory() != null && versions.getArtifacts() != null) {
                            loadArtifactVersion(gameVersion, versions.getRepoitory(), versions.getArtifacts());
                        }

                        if (snapshot != null) {
                            this.versions.put(gameVersion, snapshot);
                        }
                    } finally {
                        lock.writeLock().unlock();
                    }
                });
    }

    @Override
    public Task<?> refreshAsync() {
        throw new UnsupportedOperationException();
    }

    private void loadArtifactVersion(String gameVersion, LiteLoaderRepository repository, LiteLoaderBranch branch) {
        for (Map.Entry<String, LiteLoaderVersion> entry : branch.getLiteLoader().entrySet()) {
            String branchName = entry.getKey();
            LiteLoaderVersion v = entry.getValue();
            if ("latest".equals(branchName))
                continue;

            versions.put(gameVersion, new LiteLoaderRemoteVersion(
                    gameVersion, v.getVersion(), RemoteVersion.Type.RELEASE,
                    Collections.singletonList(repository.getUrl() + "com/mumfrey/liteloader/" + gameVersion + "/" + v.getFile()),
                    v.getTweakClass(), v.getLibraries()
            ));
        }
    }

    // Workaround for https://github.com/HMCL-dev/HMCL/issues/3147: Some LiteLoader artifacts aren't published on http://dl.liteloader.com/repo
    private static final String SNAPSHOT_METADATA = "https://repo.mumfrey.com/content/repositories/snapshots/com/mumfrey/liteloader/%s-SNAPSHOT/maven-metadata.xml";
    private static final String SNAPSHOT_FILE = "https://repo.mumfrey.com/content/repositories/snapshots/com/mumfrey/liteloader/%s-SNAPSHOT/liteloader-%s-%s-%s-release.jar";

    private LiteLoaderRemoteVersion loadSnapshotVersion(String gameVersion, LiteLoaderVersion v) throws IOException {
        String root = HttpRequest.GET(String.format(SNAPSHOT_METADATA, gameVersion)).getString();
        Document document = Jsoup.parseBodyFragment(root);
        String timestamp = Objects.requireNonNull(document.select("timestamp"), "timestamp").text();
        String buildNumber = Objects.requireNonNull(document.select("buildNumber"), "buildNumber").text();

        return new LiteLoaderRemoteVersion(
                gameVersion, timestamp + "-" + buildNumber, RemoteVersion.Type.SNAPSHOT,
                Collections.singletonList(String.format(SNAPSHOT_FILE, gameVersion, gameVersion, timestamp, buildNumber)),
                v.getTweakClass(), v.getLibraries()
        );
    }
}
