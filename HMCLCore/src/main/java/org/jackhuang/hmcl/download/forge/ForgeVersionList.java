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
package org.jackhuang.hmcl.download.forge;

import org.glavo.url.WebURL;
import org.jackhuang.hmcl.download.ComponentVersionList;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.task.GetTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.versioning.VersionNumber;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;

/**
 *
 * @author huangyuhui
 */
public final class ForgeVersionList extends ComponentVersionList<ForgeRemoteVersion> {
    private final DownloadProvider downloadProvider;

    public ForgeVersionList(DownloadProvider downloadProvider) {
        this.downloadProvider = downloadProvider;
    }

    @Override
    public boolean hasType() {
        return false;
    }

    private static String toLookupVersion(String gameVersion) {
        return "1.7.10-pre4".equals(gameVersion) ? "1.7.10_pre4" : gameVersion;
    }

    private static String fromLookupVersion(String lookupVersion) {
        return "1.7.10_pre4".equals(lookupVersion) ? "1.7.10-pre4" : lookupVersion;
    }

    @Override
    public Task<?> refreshAsync() {
        return new GetTask(FORGE_LIST).thenGetJsonAsync(ForgeVersionRoot.class)
                .thenAcceptAsync(root -> {
                    lock.writeLock().lock();

                    try {
                        if (root == null)
                            return;
                        versions.clear();

                        for (Map.Entry<String, int[]> entry : root.getGameVersions().entrySet()) {
                            String gameVersion = fromLookupVersion(VersionNumber.normalize(entry.getKey()));
                            for (int v : entry.getValue()) {
                                ForgeVersion version = root.getNumber().get(v);
                                if (version == null)
                                    continue;
                                String jar = null;
                                for (String[] file : version.getFiles())
                                    if (file.length > 1 && "installer".equals(file[1])) {
                                        String classifier = version.getGameVersion() + "-" + version.getVersion()
                                                + (StringUtils.isNotBlank(version.getBranch()) ? "-" + version.getBranch() : "");
                                        String fileName = root.getArtifact() + "-" + classifier + "-" + file[1] + "." + file[0];
                                        jar = root.getWebPath() + classifier + "/" + fileName;
                                    }

                                if (jar == null)
                                    continue;

                                versions.put(gameVersion, new ForgeRemoteVersion(
                                        toLookupVersion(version.getGameVersion()),
                                        version.getVersion(),
                                        version.getModified() > 0 ? Instant.ofEpochSecond(version.getModified()) : null,
                                        Collections.singletonList(jar)
                                ));
                            }
                        }
                    } finally {
                        lock.writeLock().unlock();
                    }
                });
    }

    /// Base URL for Forge version metadata maintained for the launcher.
    public static final WebURL FORGE_LIST = WebURL.parse("https://hmcl.glavo.site/metadata/forge/");
}
