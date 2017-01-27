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
package org.jackhuang.hellominecraft.launcher.core.install.liteloader;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.jackhuang.hellominecraft.util.C;
import org.jackhuang.hellominecraft.launcher.core.version.MinecraftLibrary;
import org.jackhuang.hellominecraft.launcher.core.install.InstallerVersionList;
import org.jackhuang.hellominecraft.launcher.core.install.InstallerVersionNewerComparator;
import org.jackhuang.hellominecraft.util.StrUtils;
import org.jackhuang.hellominecraft.util.tasks.Task;
import org.jackhuang.hellominecraft.util.tasks.TaskInfo;
import org.jackhuang.hellominecraft.util.tasks.download.HTTPGetTask;

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
            HTTPGetTask task = new HTTPGetTask(C.URL_LITELOADER_LIST);

            @Override
            public Collection<Task> getDependTasks() {
                return Arrays.asList(task.setTag("Official Liteloader Download Site"));
            }

            @Override
            public void executeTask(boolean areDependTasksSucceeded) throws Throwable {
                if (!areDependTasksSucceeded)
                    return;
                String s = task.getResult();

                root = C.GSON.fromJson(s, LiteLoaderVersionsRoot.class);

                versionMap = new HashMap<>();
                versions = new ArrayList<>();

                for (Map.Entry<String, LiteLoaderMCVersions> arr : root.versions.entrySet()) {
                    ArrayList<InstallerVersion> al = new ArrayList<>();
                    LiteLoaderMCVersions mcv = arr.getValue();
                    if (mcv == null || mcv.artefacts == null || mcv.artefacts.get("com.mumfrey:liteloader") == null)
                        continue;
                    for (Map.Entry<String, LiteLoaderVersion> entry : mcv.artefacts.get("com.mumfrey:liteloader").entrySet()) {
                        if ("latest".equals(entry.getKey()))
                            continue;
                        LiteLoaderVersion v = entry.getValue();
                        LiteLoaderInstallerVersion iv = new LiteLoaderInstallerVersion(v.version, StrUtils.formatVersion(arr.getKey()));
                        iv.universal = "http://dl.liteloader.com/versions/com/mumfrey/liteloader/" + arr.getKey() + "/" + v.file;
                        iv.tweakClass = v.tweakClass;
                        iv.libraries = Arrays.copyOf(v.libraries, v.libraries.length);
                        iv.installer = "http://dl.liteloader.com/redist/" + iv.mcVersion + "/liteloader-installer-" + iv.selfVersion.replace("_", "-") + ".jar";
                        al.add(iv);
                        versions.add(iv);
                    }
                    Collections.sort(al, new InstallerVersionNewerComparator());
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

    public static class LiteLoaderInstallerVersion extends InstallerVersion {

        public MinecraftLibrary[] libraries;
        public String tweakClass;

        public LiteLoaderInstallerVersion(String selfVersion, String mcVersion) {
            super(selfVersion, mcVersion);
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 13 * hash + Arrays.deepHashCode(this.libraries);
            hash = 13 * hash + Objects.hashCode(this.tweakClass);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null || !(obj instanceof LiteLoaderVersionList)) 
                return false;
            if (this == obj)
                return true;
            final LiteLoaderInstallerVersion other = (LiteLoaderInstallerVersion) obj;
            if (!Objects.equals(this.tweakClass, other.tweakClass))
                return false;
            return Arrays.deepEquals(this.libraries, other.libraries);
        }

        

    }

}
