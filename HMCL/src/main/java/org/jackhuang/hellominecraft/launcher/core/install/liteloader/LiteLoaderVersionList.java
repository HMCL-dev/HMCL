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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jackhuang.hellominecraft.util.C;
import org.jackhuang.hellominecraft.launcher.core.version.MinecraftLibrary;
import org.jackhuang.hellominecraft.launcher.core.install.InstallerVersionList;
import org.jackhuang.hellominecraft.launcher.core.install.InstallerVersionList.InstallerVersion;
import org.jackhuang.hellominecraft.launcher.core.install.InstallerVersionNewerComparator;
import org.jackhuang.hellominecraft.util.NetUtils;
import org.jackhuang.hellominecraft.util.StrUtils;

/**
 *
 * @author huangyuhui
 */
public class LiteLoaderVersionList extends InstallerVersionList {

    private static LiteLoaderVersionList instance;

    public static LiteLoaderVersionList getInstance() {
        if (instance == null)
            instance = new LiteLoaderVersionList();
        return instance;
    }

    public LiteLoaderVersionsRoot root;
    public Map<String, List<InstallerVersion>> versionMap;
    public List<InstallerVersion> versions;

    @Override
    public void refreshList(String[] needed) throws Exception {
        String s = NetUtils.get(C.URL_LITELOADER_LIST);
        if (root != null)
            return;

        root = C.gson.fromJson(s, LiteLoaderVersionsRoot.class);

        versionMap = new HashMap<>();
        versions = new ArrayList<>();

        for (Map.Entry<String, LiteLoaderMCVersions> arr : root.versions.entrySet()) {
            ArrayList<InstallerVersion> al = new ArrayList<>();
            LiteLoaderMCVersions mcv = arr.getValue();
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

    @Override
    public List<InstallerVersion> getVersionsImpl(String mcVersion) {
        if (versions == null || versionMap == null)
            return null;
        if (StrUtils.isBlank(mcVersion))
            return versions;
        List c = versionMap.get(mcVersion);
        if (c == null)
            return versions;
        Collections.sort(c, InstallerVersionComparator.INSTANCE);
        return c;
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
    }

}
