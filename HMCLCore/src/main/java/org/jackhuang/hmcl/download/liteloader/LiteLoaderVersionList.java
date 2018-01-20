/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2017  huangyuhui <huanghongxun2008@126.com>
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
package org.jackhuang.hmcl.download.liteloader;

import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.download.RemoteVersion;
import org.jackhuang.hmcl.download.VersionList;
import org.jackhuang.hmcl.task.GetTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.Constants;
import org.jackhuang.hmcl.util.NetworkUtils;
import org.jackhuang.hmcl.util.VersionNumber;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/**
 *
 * @author huangyuhui
 */
public final class LiteLoaderVersionList extends VersionList<LiteLoaderRemoteVersionTag> {

    public static final LiteLoaderVersionList INSTANCE = new LiteLoaderVersionList();

    private LiteLoaderVersionList() {
    }

    @Override
    public Task refreshAsync(DownloadProvider downloadProvider) {
        GetTask task = new GetTask(NetworkUtils.toURL(downloadProvider.injectURL(LITELOADER_LIST)));
        return new Task() {
            @Override
            public Collection<Task> getDependents() {
                return Collections.singleton(task);
            }

            @Override
            public void execute() {
                LiteLoaderVersionsRoot root = Constants.GSON.fromJson(task.getResult(), LiteLoaderVersionsRoot.class);
                versions.clear();

                for (Map.Entry<String, LiteLoaderGameVersions> entry : root.getVersions().entrySet()) {
                    String gameVersion = entry.getKey();
                    LiteLoaderGameVersions liteLoader = entry.getValue();
                    String gg = VersionNumber.parseVersion(gameVersion);
                    if (gg == null)
                        continue;
                    doBranch(gg, gameVersion, liteLoader.getRepoitory(), liteLoader.getArtifacts(), false);
                    doBranch(gg, gameVersion, liteLoader.getRepoitory(), liteLoader.getSnapshots(), true);
                }
            }

            private void doBranch(String key, String gameVersion, LiteLoaderRepository repository, LiteLoaderBranch branch, boolean snapshot) {
                if (branch == null || repository == null)
                    return;

                for (Map.Entry<String, LiteLoaderVersion> entry : branch.getLiteLoader().entrySet()) {
                    String branchName = entry.getKey();
                    LiteLoaderVersion v = entry.getValue();
                    if ("latest".equals(branchName))
                        continue;

                    versions.put(key, new RemoteVersion<>(gameVersion,
                            v.getVersion().replace("SNAPSHOT", "SNAPSHOT-" + v.getLastSuccessfulBuild()),
                            snapshot
                                    ? "http://jenkins.liteloader.com/LiteLoader " + gameVersion + "/lastSuccessfulBuild/artifact/build/libs/liteloader-" + v.getVersion() + "-release.jar"
                                    : downloadProvider.injectURL(repository.getUrl() + "com/mumfrey/liteloader/" + gameVersion + "/" + v.getFile()),
                            new LiteLoaderRemoteVersionTag(v.getTweakClass(), v.getLibraries())
                    ));
                }
            }
        };
    }

    public static final String LITELOADER_LIST = "http://dl.liteloader.com/versions/versions.json";
}
