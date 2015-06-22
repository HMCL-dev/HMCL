/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
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
 * @author hyh
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
        if(versionMap == null) {
            versionMap = new HashMap<String, List<InstallerVersion>>();
            versions = new ArrayList<InstallerVersion>();
        }
        
        for (String x : neededVersions) {
            if(versionMap.containsKey(x)) continue;
            String s = NetUtils.doGet("http://bmclapi2.bangbang93.com/forge/minecraft/" + x);

            if (s == null) {
                continue;
            }

            try {
                root = C.gson.fromJson(s, new TypeToken<ArrayList<ForgeVersion>>(){}.getType());
                for (ForgeVersion v : root) {
                    InstallerVersion iv = new InstallerVersion(v.version, StrUtils.formatVersion(v.minecraft));

                    List<InstallerVersion> al = ArrayUtils.tryGetMapWithList(versionMap, StrUtils.formatVersion(v.minecraft));
                    iv.changelog = v.downloads.changelog;
                    iv.installer = "http://bmclapi.bangbang93.com/forge/getforge/" + iv.mcVersion + "/" + iv.selfVersion;
                    iv.universal = ArrayUtils.getEnd(v.downloads.universal);
                    al.add(iv);
                    versions.add(iv);
                }
            } catch(JsonSyntaxException e) {
                HMCLog.warn("Failed to parse BMCLAPI response.", e);
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
