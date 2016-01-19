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
package org.jackhuang.hellominecraft.launcher.core.installers.optifine.bmcl;

import com.google.gson.reflect.TypeToken;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jackhuang.hellominecraft.C;
import org.jackhuang.hellominecraft.utils.ArrayUtils;
import org.jackhuang.hellominecraft.launcher.core.installers.InstallerVersionList;
import org.jackhuang.hellominecraft.launcher.core.installers.optifine.OptiFineVersion;
import org.jackhuang.hellominecraft.utils.NetUtils;
import org.jackhuang.hellominecraft.utils.StrUtils;

/**
 *
 * @author huangyuhui
 */
public class OptiFineBMCLVersionList extends InstallerVersionList {

    private static OptiFineBMCLVersionList instance;

    public static OptiFineBMCLVersionList getInstance() {
        if (null == instance)
            instance = new OptiFineBMCLVersionList();
        return instance;
    }

    public ArrayList<OptiFineVersion> root;
    public Map<String, List<InstallerVersion>> versionMap;
    public List<InstallerVersion> versions;

    @Override
    public void refreshList(String[] needed) throws Exception {
        String s = NetUtils.get("http://bmclapi.bangbang93.com/optifine/versionlist");

        versionMap = new HashMap<>();
        versions = new ArrayList<>();

        if (s == null)
            return;
        root = C.gson.fromJson(s, new TypeToken<ArrayList<OptiFineVersion>>() {
                           }.getType());
        for (OptiFineVersion v : root) {
            v.mirror = v.mirror.replace("http://optifine.net/http://optifine.net/", "http://optifine.net/");

            if (StrUtils.isBlank(v.mcver)) {
                Pattern p = Pattern.compile("OptiFine (.*) HD");
                Matcher m = p.matcher(v.ver);
                while (m.find())
                    v.mcver = m.group(1);
            }
            InstallerVersion iv = new InstallerVersion(v.ver, StrUtils.formatVersion(v.mcver));

            List<InstallerVersion> al = ArrayUtils.tryGetMapWithList(versionMap, StrUtils.formatVersion(v.mcver));
            String url = "http://bmclapi.bangbang93.com/optifine/" + iv.selfVersion.replace(" ", "%20");
            iv.installer = iv.universal = v.mirror;
            al.add(iv);
            versions.add(iv);
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
        return "OptiFine - BMCLAPI(By: bangbang93)";
    }

}
