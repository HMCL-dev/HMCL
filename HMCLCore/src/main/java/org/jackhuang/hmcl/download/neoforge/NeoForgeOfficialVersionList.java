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

package org.jackhuang.hmcl.download.neoforge;

import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.download.VersionList;
import org.jackhuang.hmcl.task.GetTask;
import org.jackhuang.hmcl.task.Task;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class NeoForgeOfficialVersionList extends VersionList<NeoForgeRemoteVersion> {
    private final DownloadProvider downloadProvider;

    public NeoForgeOfficialVersionList(DownloadProvider downloadProvider) {
        this.downloadProvider = downloadProvider;
    }

    @Override
    public boolean hasType() {
        return true;
    }

    private static final String OLD_URL = "https://maven.neoforged.net/api/maven/versions/releases/net/neoforged/forge";
    private static final String META_URL = "https://maven.neoforged.net/api/maven/versions/releases/net/neoforged/neoforge";

    @Override
    public Optional<NeoForgeRemoteVersion> getVersion(String gameVersion, String remoteVersion) {
        if (gameVersion.equals("1.20.1")) {
            remoteVersion = NeoForgeRemoteVersion.normalize(remoteVersion);
        }
        return super.getVersion(gameVersion, remoteVersion);
    }

    @Override
    public Task<?> refreshAsync() {
        return Task.allOf(
                new GetTask(downloadProvider.injectURLWithCandidates(OLD_URL)).thenGetJsonAsync(OfficialAPIResult.class),
                new GetTask(downloadProvider.injectURLWithCandidates(META_URL)).thenGetJsonAsync(OfficialAPIResult.class)
        ).thenAcceptAsync(results -> {
            lock.writeLock().lock();

            try {
                versions.clear();

                for (String version : results.get(0).versions) {
                    versions.put("1.20.1", new NeoForgeRemoteVersion(
                            "1.20.1", NeoForgeRemoteVersion.normalize(version),
                            Collections.singletonList(
                                    "https://maven.neoforged.net/releases/net/neoforged/forge/" + version + "/forge-" + version + "-installer.jar"
                            )
                    ));
                }

                for (String version : results.get(1).versions) {
                    String mcVersion;

                    try {
                        int si1 = version.indexOf('.'), si2 = version.indexOf('.', version.indexOf('.') + 1);
                        int majorVersion = Integer.parseInt(version.substring(0, si1));
                        if (majorVersion == 0) { // Snapshot version.
                            mcVersion = version.substring(si1 + 1, si2);
                        } else {
                            String ver = version.substring(0, Integer.parseInt(version.substring(si1 + 1, si2)) == 0 ? si1 : si2);
                            if (majorVersion >= 26) {
                                int separator = version.indexOf('+');
                                mcVersion = separator < 0 ? ver : ver + "-" + version.substring(separator + 1);
                            } else {
                                mcVersion = "1." + ver;
                            }
                        }
                    } catch (RuntimeException e) {
                        LOG.warning(String.format("Cannot parse NeoForge version %s for cracking its mc version.", version), e);
                        continue;
                    }

                    versions.put(mcVersion, new NeoForgeRemoteVersion(
                            mcVersion, NeoForgeRemoteVersion.normalize(version),
                            Collections.singletonList(
                                    "https://maven.neoforged.net/releases/net/neoforged/neoforge/" + version + "/neoforge-" + version + "-installer.jar"
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
