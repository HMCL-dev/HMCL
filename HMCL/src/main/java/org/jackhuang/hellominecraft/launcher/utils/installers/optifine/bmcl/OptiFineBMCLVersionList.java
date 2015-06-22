/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraft.launcher.utils.installers.optifine.bmcl;

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
import org.jackhuang.hellominecraft.launcher.utils.installers.InstallerVersionList;
import org.jackhuang.hellominecraft.launcher.utils.installers.optifine.OptiFineVersion;
import org.jackhuang.hellominecraft.utils.tinystream.CollectionUtils;
import org.jackhuang.hellominecraft.utils.NetUtils;
import org.jackhuang.hellominecraft.utils.StrUtils;

/**
 *
 * @author hyh
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
        String s = NetUtils.doGet("http://bmclapi.bangbang93.com/optifine/versionlist");

        versionMap = new HashMap<>();
        versions = new ArrayList<>();

        if (s == null) return;
        root = C.gson.fromJson(s, new TypeToken<ArrayList<OptiFineVersion>>() {
        }.getType());
        for(OptiFineVersion v : root) {
            v.mirror = v.mirror.replace("http://optifine.net/http://optifine.net/", "http://optifine.net/");

            if (StrUtils.isBlank(v.mcver)) {
                Pattern p = Pattern.compile("OptiFine (.*) HD");
                Matcher m = p.matcher(v.ver);
                while (m.find()) v.mcver = m.group(1);
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
        return "OptiFine - BMCLAPI(By: bangbang93)";
    }

}
