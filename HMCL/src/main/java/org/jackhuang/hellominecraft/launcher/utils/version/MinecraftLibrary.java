/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraft.launcher.utils.version;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import org.jackhuang.hellominecraft.launcher.utils.download.DownloadType;
import org.jackhuang.hellominecraft.utils.OS;
import org.jackhuang.hellominecraft.utils.StrUtils;

/**
 *
 * @author hyh
 */
public class MinecraftLibrary extends IMinecraftLibrary implements Cloneable {

    public ArrayList<Rules> rules;
    public String url, formatted=null;
    //public boolean serverreq=true, clientreq=true;
    public String[] checksums;
    public Natives natives;
    public Extract extract;

    public MinecraftLibrary(String name) {
	super(name);
    }

    public MinecraftLibrary(ArrayList<Rules> rules, String url, String[] checksums, Natives natives, String name, Extract extract) {
	super(name);
	this.rules = rules == null ? null : (ArrayList<Rules>) rules.clone();
	this.url = url;
	this.checksums = checksums == null ? null : Arrays.copyOf(checksums, checksums.length);
	this.natives = natives == null ? null : (Natives)natives.clone();
        this.extract = extract == null ? null :(Extract)extract.clone();
    }
    
    @Override
    public Object clone() {
	return new MinecraftLibrary(rules, url, checksums, natives, name, extract);
    }

    /**
     * is the library allowed to load.
     *
     * @return
     */
    @Override
    public boolean allow() {
	boolean flag = false;
	if (rules == null || rules.isEmpty()) {
	    flag = true;
	} else {
	    for (Rules r : rules) {
		if (r.action.equals("disallow")) {
		    if (r.os != null && (StrUtils.isBlank(r.os.name) || r.os.name.equalsIgnoreCase(OS.os().toString()))) {
			flag = false;
			break;
		    }
		} else {
		    if (r.os != null && (StrUtils.isBlank(r.os.name) || r.os.name.equalsIgnoreCase(OS.os().toString()))) {
			flag = true;
		    }
		    if (r.os == null) {
			flag = true;
		    }
		}
	    }
	}
	return flag;
    }

    private String formatArch(String nati) {
	String arch = System.getProperty("os.arch");
	if (arch.contains("64")) {
	    arch = "64";
	} else {
	    arch = "32";
	}
	if (nati == null) {
	    return "";
	}
	return nati.replace("${arch}", arch);
    }

    private String getNative() {
	OS os = OS.os();
	if (os == OS.WINDOWS) {
	    return formatArch(natives.windows);
	} else if (os == OS.OSX) {
	    return formatArch(natives.osx);
	} else {
	    return formatArch(natives.linux);
	}
    }

    @Override
    public boolean isRequiredToUnzip() {
	return natives != null && allow();
    }

    @Override
    public void init() {
	String str = name;
	String[] s = str.split(":");
	str = s[0];
	str = str.replace('.', File.separatorChar);
	if (natives == null) {
	    str += File.separator + s[1] + File.separator + s[2]
		    + File.separator + s[1] + '-' + s[2] + ".jar";
	} else {
	    str += File.separator + s[1] + File.separator + s[2]
		    + File.separator + s[1] + '-' + s[2] + '-';
	    str += getNative();
	    str += ".jar";
	}
	formatted = str;
    }

    @Override
    public File getFilePath(File gameDir) {
	return new File(gameDir, "libraries" + File.separatorChar + formatted);
    }

    @Override
    public String getDownloadURL(String urlBase, DownloadType downloadType) {
	if(StrUtils.isNotBlank(url)&&downloadType.getProvider().isAllowedToUseSelfURL()) urlBase = this.url;
	return urlBase + formatted.replace('\\', '/');
    }

    @Override
    public String[] getDecompressExtractRules() {
        return extract == null || extract.exclude == null ? new String[0] : extract.exclude;
    }
}
