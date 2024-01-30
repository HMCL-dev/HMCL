package org.jackhuang.hmcl.download.neoforge;

import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.download.VersionList;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.io.HttpRequest;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.jackhuang.hmcl.util.Lang.wrap;

public final class NeoForgeOfficialVersionList extends VersionList<NeoForgeRemoteVersion> {
    private final DownloadProvider downloadProvider;

    public NeoForgeOfficialVersionList(DownloadProvider downloadProvider) {
        this.downloadProvider = downloadProvider;
    }

    @Override
    public boolean hasType() {
        return false;
    }

    private static final String OLD_URL = "https://maven.neoforged.net/api/maven/versions/releases/net/neoforged/forge";

    private static final String META_URL = "https://maven.neoforged.net/api/maven/versions/releases/net/neoforged/neoforge";

    @Override
    public CompletableFuture<?> refreshAsync() {
        return CompletableFuture.supplyAsync(wrap(() -> new OfficialAPIResult[]{
                HttpRequest.GET(downloadProvider.injectURL(OLD_URL)).getJson(OfficialAPIResult.class),
                HttpRequest.GET(downloadProvider.injectURL(META_URL)).getJson(OfficialAPIResult.class)
        })).thenAccept(results -> {
            lock.writeLock().lock();

            try {
                versions.clear();

                for (String version : results[0].versions) {
                    versions.put("1.20.1", new NeoForgeRemoteVersion(
                            "1.20.1", StringUtils.removePrefix(version, "1.20.1-"),
                            Lang.immutableListOf(
                                    downloadProvider.injectURL("https://maven.neoforged.net/releases/net/neoforged/forge/" + version + "/forge-" + version + "-installer.jar")
                            )
                    ));
                }

                for (String version : results[1].versions) {
                    String mcVersion = "1." + version.substring(0, version.indexOf('.', version.indexOf('.') + 1));
                    versions.put(mcVersion, new NeoForgeRemoteVersion(
                            mcVersion, version,
                            Lang.immutableListOf(
                                    downloadProvider.injectURL("https://maven.neoforged.net/releases/net/neoforged/neoforge/" + version + "/neoforge-" + version + "-installer.jar")
                            )
                    ));
                }
            } finally {
                lock.writeLock().unlock();
            }
        });
    }

    private static final class OfficialAPIResult {
        private final boolean isSnapshot;

        private final List<String> versions;

        public OfficialAPIResult(boolean isSnapshot, List<String> versions) {
            this.isSnapshot = isSnapshot;
            this.versions = versions;
        }
    }
}
