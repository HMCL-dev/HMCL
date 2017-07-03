/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2013  huangyuhui <huanghongxun2008@126.com>
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
package org.jackhuang.hmcl.core.install.liteloader;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.jackhuang.hmcl.core.download.DownloadType;
import org.jackhuang.hmcl.util.C;
import org.jackhuang.hmcl.core.install.InstallerVersionList;
import org.jackhuang.hmcl.core.install.InstallerVersionNewerComparator;
import org.jackhuang.hmcl.core.version.MinecraftLibrary;
import org.jackhuang.hmcl.util.StrUtils;
import org.jackhuang.hmcl.util.task.Task;
import org.jackhuang.hmcl.util.task.TaskInfo;
import org.jackhuang.hmcl.util.net.HTTPGetTask;

/**
 *
 * @author huangyuhui
 */
public class LiteLoaderVersionList extends InstallerVersionList {

    private static volatile LiteLoaderVersionList instance = null;

    public static LiteLoaderVersionList getInstance() {
        if (instance == null)
            instance = new LiteLoaderVersionList();
        return instance;
    }

    public LiteLoaderVersionsRoot root;

    @Override
    public Task refresh(String[] needed) {
        if (root != null)
            return null;
        return new TaskInfo(C.i18n("install.liteloader.get_list")) {
            HTTPGetTask task = new HTTPGetTask(DownloadType.getSuggestedDownloadType().getProvider().getParsedDownloadURL(C.URL_LITELOADER_LIST));

            @Override
            public Collection<Task> getDependTasks() {
                return Arrays.asList(task.setTag("Official Liteloader Download Site"));
            }

            private void doBranch(String version, LiteLoaderRepo repo, LiteLoaderBranch branch, List<InstallerVersion> result, boolean snapshot) {
                if (branch == null || branch.liteLoader == null)
                    return;
                for (Map.Entry<String, LiteLoaderVersion> entry : branch.liteLoader.entrySet()) {
                    if ("latest".equals(entry.getKey()))
                        continue;
                    LiteLoaderVersion v = entry.getValue();
                    LiteLoaderInstallerVersion iv = new LiteLoaderInstallerVersion(v.version.replace("SNAPSHOT", "SNAPSHOT-" + v.lastSuccessfulBuild), StrUtils.formatVersion(version));
                    if (snapshot)
                        iv.universal = String.format("http://jenkins.liteloader.com/view/%s/job/LiteLoader %s/lastSuccessfulBuild/artifact/build/libs/liteloader-%s-release.jar", version, version, v.version);
                    else
                        iv.universal = DownloadType.getSuggestedDownloadType().getProvider().getParsedDownloadURL(repo.url + "com/mumfrey/liteloader/" + version + "/" + v.file);
                    iv.tweakClass = v.tweakClass;
                    HashSet<MinecraftLibrary> set = new HashSet<>();
                    if (v.libraries != null)
                        set.addAll(v.libraries);
                    iv.libraries = set.toArray(new MinecraftLibrary[set.size()]);
                    result.add(iv);
                }
            }
            
            @Override
            public void executeTask(boolean areDependTasksSucceeded) throws Exception {
                if (!areDependTasksSucceeded)
                    return;
                String s = task.getResult();

                root = C.GSON.fromJson(s, LiteLoaderVersionsRoot.class);

                versionMap = new HashMap<>();
                versions = new ArrayList<>();

                for (Map.Entry<String, LiteLoaderMCVersions> arr : root.versions.entrySet()) {
                    ArrayList<InstallerVersion> al = new ArrayList<>();
                    LiteLoaderMCVersions mcv = arr.getValue();
                    doBranch(arr.getKey(), mcv.repo, mcv.artefacts, al, false);
                    doBranch(arr.getKey(), mcv.repo, mcv.snapshots, al, true);
                    Collections.sort(al, new InstallerVersionNewerComparator());
                    versions.addAll(al);
                    versionMap.put(StrUtils.formatVersion(arr.getKey()), al);
                }

                Collections.sort(versions, InstallerVersionComparator.INSTANCE);
            }
        };
    }

    @Override
    public String getName() {
        return "LiteLoader - LiteLoader Official Site(By: Mumfrey)";
    }

}
