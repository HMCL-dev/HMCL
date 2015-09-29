/*
 * Copyright 2013 huangyuhui <huanghongxun2008@126.com>
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.
 */
package org.jackhuang.hellominecraft.launcher.utils.installers.forge.vanilla;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jackhuang.hellominecraft.C;
import org.jackhuang.hellominecraft.launcher.settings.Settings;
import org.jackhuang.hellominecraft.utils.StrUtils;
import org.jackhuang.hellominecraft.launcher.utils.installers.InstallerVersionList;
import org.jackhuang.hellominecraft.launcher.utils.installers.InstallerVersionNewerComparator;
import org.jackhuang.hellominecraft.utils.NetUtils;

/**
 *
 * @author huangyuhui
 */
public class MinecraftForgeVersionList extends InstallerVersionList {

    private static MinecraftForgeVersionList instance;

    public static MinecraftForgeVersionList getInstance() {
        if (instance == null)
            instance = new MinecraftForgeVersionList();
        return instance;
    }

    public MinecraftForgeVersionRoot root;
    public Map<String, List<InstallerVersion>> versionMap;
    public List<InstallerVersion> versions;

    @Override
    public void refreshList(String[] needed) throws Exception {
        String s = NetUtils.doGet(Settings.getInstance().getDownloadSource().getProvider().getParsedLibraryDownloadURL(C.URL_FORGE_LIST));
        if (root != null) return;

        root = C.gson.fromJson(s, MinecraftForgeVersionRoot.class);

        versionMap = new HashMap<>();
        versions = new ArrayList<>();

        for (Map.Entry<String, int[]> arr : root.mcversion.entrySet()) {
            String mcver = StrUtils.formatVersion(arr.getKey());
            ArrayList<InstallerVersion> al = new ArrayList<>();
            for (int num : arr.getValue()) {
                MinecraftForgeVersion v = root.number.get(num);
                InstallerVersion iv = new InstallerVersion(v.version, StrUtils.formatVersion(v.mcversion));
                for (String[] f : v.files) {

                    String ver = v.mcversion + "-" + v.version;
                    if (!StrUtils.isBlank(v.branch))
                        ver = ver + "-" + v.branch;
                    String filename = root.artifact + "-" + ver + "-" + f[1] + "." + f[0];
                    String url = Settings.getInstance().getDownloadSource().getProvider().getParsedLibraryDownloadURL(root.webpath + ver + "/" + filename);
                    switch (f[1]) {
                        case "installer":
                            iv.installer = url;
                            break;
                        case "universal":
                            iv.universal = url;
                            break;
                        case "changelog":
                            iv.changelog = url;
                            break;
                    }
                }
                if (StrUtils.isBlank(iv.installer) || StrUtils.isBlank(iv.universal))
                    continue;
                Collections.sort(al, new InstallerVersionNewerComparator());
                al.add(iv);
                versions.add(iv);
            }

            versionMap.put(StrUtils.formatVersion(mcver), al);
        }

        Collections.sort(versions, new InstallerVersionComparator());
    }

    @Override
    public List<InstallerVersion> getVersions(String mcVersion) {
        if (versions == null || versionMap == null) return null;
        if (StrUtils.isBlank(mcVersion)) return versions;
        List c = versionMap.get(mcVersion);
        if (c == null) return versions;
        Collections.sort(c, InstallerVersionComparator.INSTANCE);
        return c;
    }

    @Override
    public String getName() {
        return "Forge - MinecraftForge Offical Site";
    }
}
