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

import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.download.ComponentVersionList;
import org.jackhuang.hmcl.task.GetTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.versioning.VersionNumber;

import java.net.URI;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;

import static org.jackhuang.hmcl.download.forge.ForgeInstallation.fromLookupVersion;
import static org.jackhuang.hmcl.download.forge.ForgeInstallation.toLookupVersion;

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

    @Override
    public Task<?> refreshAsync() {
        return new GetTask(FORGE_LIST).thenGetJsonAsync(ForgeVersionRoot.class)
                .thenAcceptAsync(root -> {
                    lock.writeLock().lock();

                    try {
                        if (root == null)
                            return;
                        versions.clear();

                        for (Map.Entry<String, int[]> entry : root.mcversion().entrySet()) {
                            String gameVersion = fromLookupVersion(VersionNumber.normalize(entry.getKey()));
                            for (int v : entry.getValue()) {
                                ForgeVersion version = root.number().get(v);
                                if (version == null)
                                    continue;
                                String installer = null;
                                for (String[] file : version.files())
                                    if (file.length > 1) {
                                        String classifier = version.mcversion() + "-" + version.version()
                                                + (StringUtils.isNotBlank(version.branch()) ? "-" + version.branch() : "");
                                        String fileName = root.artifact() + "-" + classifier + "-" + file[1] + "." + file[0];
                                        installer = root.webpath() + classifier + "/" + fileName;
                                    }

                                if (installer == null)
                                    continue;

                                versions.put(gameVersion, new ForgeRemoteVersion(
                                        toLookupVersion(version.mcversion()),
                                        version.version(),
                                        version.modified() > 0 ? Instant.ofEpochSecond(version.modified()) : null,
                                        Collections.singletonList(installer)
                                ));
                            }
                        }
                    } finally {
                        lock.writeLock().unlock();
                    }
                });
    }

    public static final URI FORGE_LIST = URI.create("https://hmcl.glavo.site/metadata/forge/");
}
