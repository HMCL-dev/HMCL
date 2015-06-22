/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraft.launcher.utils.installers.liteloader;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jackhuang.hellominecraft.C;
import org.jackhuang.hellominecraft.launcher.utils.version.MinecraftLibrary;
import org.jackhuang.hellominecraft.launcher.utils.installers.InstallerVersionList;
import org.jackhuang.hellominecraft.launcher.utils.installers.InstallerVersionList.InstallerVersion;
import org.jackhuang.hellominecraft.launcher.utils.installers.InstallerVersionNewerComparator;
import org.jackhuang.hellominecraft.utils.NetUtils;
import org.jackhuang.hellominecraft.utils.StrUtils;

/**
 *
 * @author hyh
 */
public class LiteLoaderVersionList extends InstallerVersionList {
    private static LiteLoaderVersionList instance;
    public static LiteLoaderVersionList getInstance() {
	if(instance == null) {
	    instance = new LiteLoaderVersionList();
	}
	return instance;
    }

    public LiteLoaderVersionsRoot root;
    public Map<String, List<InstallerVersion>> versionMap;
    public List<InstallerVersion> versions;

    @Override
    public void refreshList(String[] needed) throws Exception {
        String s = NetUtils.doGet(C.URL_LITELOADER_LIST);
        if(root != null) return;
        
	root = C.gson.fromJson(s, LiteLoaderVersionsRoot.class);
	
	versionMap = new HashMap<String, List<InstallerVersion>>();
	versions = new ArrayList<InstallerVersion>();
	
	for(Map.Entry<String, LiteLoaderMCVersions> arr : root.versions.entrySet()) {
	    ArrayList<InstallerVersion> al = new ArrayList<InstallerVersion>();
            LiteLoaderMCVersions mcv = arr.getValue();
	    for(Map.Entry<String, LiteLoaderVersion> entry : mcv.artefacts.get("com.mumfrey:liteloader").entrySet()) {
                if("latest".equals(entry.getKey())) continue;
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
    public List<InstallerVersion> getVersions(String mcVersion) {
        if (versions == null || versionMap == null) return null;
        if(StrUtils.isBlank(mcVersion)) return versions;
	List c = versionMap.get(mcVersion);
	if(c == null) return versions;
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
