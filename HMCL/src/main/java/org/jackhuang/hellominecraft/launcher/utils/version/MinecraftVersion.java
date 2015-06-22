/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraft.launcher.utils.version;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jackhuang.hellominecraft.launcher.launch.IMinecraftProvider;
import org.jackhuang.hellominecraft.launcher.utils.download.DownloadType;
import org.jackhuang.hellominecraft.utils.ArrayUtils;

/**
 *
 * @author hyh
 */
public class MinecraftVersion implements Cloneable, Comparable<MinecraftVersion> {

    public String minecraftArguments, mainClass, time, id, type, processArguments,
	    releaseTime, assets, jar, inheritsFrom;
    public int minimumLauncherVersion;
    public boolean hidden;

    public List<MinecraftLibrary> libraries;

    public MinecraftVersion() {
    }

    public MinecraftVersion(String minecraftArguments, String mainClass, String time, String id, String type, String processArguments, String releaseTime, String assets, String jar, String inheritsFrom, int minimumLauncherVersion, List<MinecraftLibrary> libraries, boolean hidden) {
	this();
	this.minecraftArguments = minecraftArguments;
	this.mainClass = mainClass;
	this.time = time;
	this.id = id;
	this.type = type;
	this.processArguments = processArguments;
	this.releaseTime = releaseTime;
	this.assets = assets;
	this.jar = jar;
	this.inheritsFrom = inheritsFrom;
	this.minimumLauncherVersion = minimumLauncherVersion;
        this.hidden = hidden;
	if(libraries == null) this.libraries = new ArrayList<>();
	else {
	    this.libraries = new ArrayList<>(libraries.size());
	    for (IMinecraftLibrary library : libraries) {
		this.libraries.add((MinecraftLibrary) library.clone());
	    }
	}
    }

    @Override
    public Object clone() {
	return new MinecraftVersion(minecraftArguments, mainClass, time, id, type, processArguments, releaseTime, assets, jar, inheritsFrom, minimumLauncherVersion, libraries, hidden);
    }

    public MinecraftVersion resolve(IMinecraftProvider manager, DownloadType sourceType) {
	return resolve(manager, new HashSet<>(), sourceType);
    }

    protected MinecraftVersion resolve(IMinecraftProvider manager, Set<String> resolvedSoFar, DownloadType sourceType) {
	if (inheritsFrom == null) {
	    return this;
	}
	if (!resolvedSoFar.add(id)) {
	    throw new IllegalStateException("Circular dependency detected.");
	}

	MinecraftVersion parent = manager.getVersionById(inheritsFrom);
	if(parent == null) {
	    if(!manager.install(inheritsFrom, sourceType)) return this;
	    parent = manager.getVersionById(inheritsFrom);
	}
	parent = parent.resolve(manager, resolvedSoFar, sourceType);
	MinecraftVersion result = new MinecraftVersion(
		this.minecraftArguments != null ? this.minecraftArguments : parent.minecraftArguments,
		this.mainClass != null ? this.mainClass : parent.mainClass,
		this.time, this.id, this.type, parent.processArguments, this.releaseTime,
		this.assets != null ? this.assets : parent.assets,
		this.jar != null ? this.jar : parent.jar,
		null, parent.minimumLauncherVersion,
		this.libraries != null ? ArrayUtils.merge(this.libraries, parent.libraries) : parent.libraries, this.hidden);
	
	return result;
    }

    public File getJar(File gameDir) {
	String jarId = this.jar == null ? this.id : this.jar;
	return new File(gameDir, "versions/" + jarId + "/" + jarId + ".jar");
    }

    public File getJar(File gameDir, String suffix) {
	String jarId = this.jar == null ? this.id : this.jar;
	return new File(gameDir, "versions/" + jarId + "/" + jarId + suffix + ".jar");
    }

    public File getNatives(File gameDir) {
	return new File(gameDir, "versions/" + id + "/" + id
		+ "-natives");
    }
    
    public boolean isAllowedToUnpackNatives() {
	return true;
    }

    @Override
    public int compareTo(MinecraftVersion o) {
        return id.compareTo(((MinecraftVersion) o).id);
    }
}
