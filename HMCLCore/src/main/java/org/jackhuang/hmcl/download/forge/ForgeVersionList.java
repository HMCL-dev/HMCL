/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2018  huangyuhui <huanghongxun2008@126.com>
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
 * along with this program.  If not, see {http://www.gnu.org/licenses/}.
 */
package org.jackhuang.hmcl.download.forge;

import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.download.VersionList;
import org.jackhuang.hmcl.task.GetTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.Constants;
import org.jackhuang.hmcl.util.NetworkUtils;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.VersionNumber;

import java.util.*;

/**
 *
 * @author huangyuhui
 */
public final class ForgeVersionList extends VersionList<Void> {

    public static final ForgeVersionList INSTANCE = new ForgeVersionList();

    private ForgeVersionList() {
    }

    @Override
    public Task refreshAsync(DownloadProvider downloadProvider) {
        final GetTask task = new GetTask(NetworkUtils.toURL(downloadProvider.injectURL(FORGE_LIST)));
        final List<Task> dependents = Collections.singletonList(task);
        return new Task() {

            @Override
            public Collection<Task> getDependents() {
                return dependents;
            }

            @Override
            public void execute() {
                lock.writeLock().lock();

                try {
                    ForgeVersionRoot root = Constants.GSON.fromJson(task.getResult(), ForgeVersionRoot.class);
                    if (root == null)
                        return;
                    versions.clear();

                    for (Map.Entry<String, int[]> entry : root.getGameVersions().entrySet()) {
                        Optional<String> gameVersion = VersionNumber.parseVersion(entry.getKey());
                        if (!gameVersion.isPresent())
                            continue;
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
                                    jar = downloadProvider.injectURL(root.getWebPath() + classifier + "/" + fileName);
                                }

                            if (jar == null)
                                continue;
                            versions.put(gameVersion.get(), new ForgeRemoteVersion(
                                    version.getGameVersion(), version.getVersion(), jar
                            ));
                        }
                    }
                } finally {
                    lock.writeLock().unlock();
                }
            }

        };
    }

    public static final String FORGE_LIST = "https://files.minecraftforge.net/maven/net/minecraftforge/forge/json";
}
