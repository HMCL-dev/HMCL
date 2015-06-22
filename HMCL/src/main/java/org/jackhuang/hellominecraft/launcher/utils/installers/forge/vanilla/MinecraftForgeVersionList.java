/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraft.launcher.utils.installers.forge.vanilla;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jackhuang.hellominecraft.C;
import org.jackhuang.hellominecraft.utils.StrUtils;
import org.jackhuang.hellominecraft.launcher.utils.installers.InstallerVersionList;
import org.jackhuang.hellominecraft.launcher.utils.installers.InstallerVersionNewerComparator;
import org.jackhuang.hellominecraft.utils.NetUtils;

/**
 *
 * @author hyh
 */
public class MinecraftForgeVersionList extends InstallerVersionList {
    private static MinecraftForgeVersionList instance;
    public static MinecraftForgeVersionList getInstance() {
	if(instance == null)
	    instance = new MinecraftForgeVersionList();
	return instance;
    }

    public MinecraftForgeVersionRoot root;
    public Map<String, List<InstallerVersion>> versionMap;
    public List<InstallerVersion> versions;
    
    @Override
    public void refreshList(String[] needed) throws Exception {
        String s = NetUtils.doGet(C.URL_FORGE_LIST);
        if(root!=null) return;
        
	root = C.gson.fromJson(s, MinecraftForgeVersionRoot.class);
	
	versionMap = new HashMap<String, List<InstallerVersion>>();
	versions = new ArrayList<InstallerVersion>();
	
	for(Map.Entry<String, int[]> arr : root.mcversion.entrySet()) {
            String mcver = StrUtils.formatVersion(arr.getKey());
	    ArrayList<InstallerVersion> al = new ArrayList<InstallerVersion>();
	    for(int num : arr.getValue()) {
		MinecraftForgeVersion v = root.number.get(num);
		InstallerVersion iv = new InstallerVersion(v.version, StrUtils.formatVersion(v.mcversion));
		for(String[] f : v.files) {

		    String ver = v.mcversion + "-" + v.version;
		    if(!StrUtils.isBlank(v.branch)) {
			ver = ver + "-" + v.branch;
		    }
		    String filename = root.artifact + "-" + ver + "-" + f[1] + "." + f[0];
		    String url = root.webpath + "/" + ver + "/" + filename;
		    if(f[1].equals("installer")) {
			iv.installer = url;
		    } else if(f[1].equals("universal")) {
			iv.universal = url;
		    } else if(f[1].equals("changelog")) {
			iv.changelog = url;
		    }
		}
		if(StrUtils.isBlank(iv.installer) || StrUtils.isBlank(iv.universal)) {
		    continue;
		}
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
        if(StrUtils.isBlank(mcVersion)) return versions;
	List c = versionMap.get(mcVersion);
	if(c == null) return versions;
        Collections.sort(c, InstallerVersionComparator.INSTANCE);
	return c;
    }

    @Override
    public String getName() {
        return "Forge - MinecraftForge Offical Site";
    }
}
