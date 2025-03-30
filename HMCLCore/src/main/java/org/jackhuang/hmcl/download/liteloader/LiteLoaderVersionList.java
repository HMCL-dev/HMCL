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
import org.jackhuang.hmcl.util.io.HttpRequest;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;

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

    public static final String LITELOADER_LIST = "http://dl.liteloader.com/versions/versions.json";

    @Override
    public CompletableFuture<?> refreshAsync() {
        return HttpRequest.GET(downloadProvider.injectURL(LITELOADER_LIST)).getJsonAsync(LiteLoaderVersionsRoot.class)
                .thenAcceptAsync(root -> {
                    List<CompletableFuture<List<LiteLoaderRemoteVersion>>> futures = new ArrayList<>();
                    for (Map.Entry<String, LiteLoaderGameVersions> entry : root.getVersions().entrySet()) {
                        String gameVersion = entry.getKey();
                        LiteLoaderGameVersions liteLoader = entry.getValue();

                        if (liteLoader.getRepoitory() != null && liteLoader.getArtifacts() != null) {
                            futures.add(CompletableFuture.supplyAsync(() -> loadArtifactVersion(gameVersion, liteLoader.getRepoitory(), liteLoader.getArtifacts())));
                        }

                        if (liteLoader.getSnapshots() != null) {
                            LiteLoaderVersion v = liteLoader.getSnapshots().getLiteLoader().get("latest");
                            futures.add(CompletableFuture.supplyAsync(() -> {
                                try {
                                    return Collections.singletonList(loadSnapshotVersion(gameVersion, v));
                                } catch (IOException e) {
                                    throw new UncheckedIOException(e);
                                }
                            }));
                        }
                    }

                    for (CompletableFuture<List<LiteLoaderRemoteVersion>> future : futures) {
                        future.join();
                    }

                    lock.writeLock().lock();
                    try {
                        versions.clear();

                        for (CompletableFuture<List<LiteLoaderRemoteVersion>> future : futures) {
                            for (LiteLoaderRemoteVersion v : future.getNow(null)) {
                                versions.put(v.getGameVersion(), v);
                            }
                        }
                    } finally {
                        lock.writeLock().unlock();
                    }
                });
    }

    private List<LiteLoaderRemoteVersion> loadArtifactVersion(String gameVersion, LiteLoaderRepository repository, LiteLoaderBranch branch) {
        List<LiteLoaderRemoteVersion> versions = new ArrayList<>();

        for (Map.Entry<String, LiteLoaderVersion> entry : branch.getLiteLoader().entrySet()) {
            String branchName = entry.getKey();
            LiteLoaderVersion v = entry.getValue();
            if ("latest".equals(branchName))
                continue;

            versions.add(new LiteLoaderRemoteVersion(
                    gameVersion, v.getVersion(), RemoteVersion.Type.RELEASE,
                    Collections.singletonList(repository.getUrl() + "com/mumfrey/liteloader/" + gameVersion + "/" + v.getFile()),
                    v.getTweakClass(), v.getLibraries()
            ));
        }

        return versions;
    }

    // Workaround for https://github.com/HMCL-dev/HMCL/issues/3147: Some LiteLoader artifacts aren't published on http://dl.liteloader.com/repo
    private static final String SNAPSHOT_METADATA = "https://repo.mumfrey.com/content/repositories/snapshots/com/mumfrey/liteloader/%s-SNAPSHOT/maven-metadata.xml";
    private static final String SNAPSHOT_FILE = "https://repo.mumfrey.com/content/repositories/snapshots/com/mumfrey/liteloader/%s-SNAPSHOT/liteloader-%s-%s-%s.jar";

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
