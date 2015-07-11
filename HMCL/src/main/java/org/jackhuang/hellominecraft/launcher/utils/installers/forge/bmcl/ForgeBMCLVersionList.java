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
package org.jackhuang.hellominecraft.launcher.utils.installers.forge.bmcl;

import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jackhuang.hellominecraft.C;
import org.jackhuang.hellominecraft.HMCLog;
import org.jackhuang.hellominecraft.utils.ArrayUtils;
import org.jackhuang.hellominecraft.utils.StrUtils;
import org.jackhuang.hellominecraft.launcher.utils.installers.InstallerVersionList;
import org.jackhuang.hellominecraft.utils.NetUtils;

/**
 *
 * @author huangyuhui
 */
public class ForgeBMCLVersionList extends InstallerVersionList {

    private static ForgeBMCLVersionList instance;

    public static ForgeBMCLVersionList getInstance() {
        if (instance == null) instance = new ForgeBMCLVersionList();
        return instance;
    }

    public ArrayList<ForgeVersion> root;
    public Map<String, List<InstallerVersion>> versionMap;
    public List<InstallerVersion> versions;

    @Override
    public void refreshList(String[] neededVersions) throws Exception {
        if (versionMap == null) {
            versionMap = new HashMap<>();
            versions = new ArrayList<>();
        }

        for (String x : neededVersions) {
            if (versionMap.containsKey(x)) continue;
            String s = NetUtils.doGet("http://bmclapi2.bangbang93.com/forge/minecraft/" + x);

            if (s == null)
                continue;

            try {
                root = C.gson.fromJson(s, new TypeToken<ArrayList<ForgeVersion>>() {
                }.getType());
                for (ForgeVersion v : root) {
                    InstallerVersion iv = new InstallerVersion(v.version, StrUtils.formatVersion(v.minecraft));

                    List<InstallerVersion> al = ArrayUtils.tryGetMapWithList(versionMap, StrUtils.formatVersion(v.minecraft));
                    iv.changelog = v.downloads.changelog;
                    iv.installer = ArrayUtils.getEnd(v.downloads.installer);
                    iv.universal = ArrayUtils.getEnd(v.downloads.universal);
                    al.add(iv);
                    versions.add(iv);
                }
            } catch (JsonSyntaxException e) {
                HMCLog.warn("Failed to parse BMCLAPI2 response.", e);
            }
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
        return "Forge - BMCLAPI (By: bangbang93)";
    }
}
