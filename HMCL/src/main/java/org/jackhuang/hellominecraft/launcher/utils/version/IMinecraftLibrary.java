/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.jackhuang.hellominecraft.launcher.utils.version;

import java.io.File;
import org.jackhuang.hellominecraft.launcher.utils.download.DownloadType;

/**
 *
 * @author hyh
 */
public abstract class IMinecraftLibrary {
    
    public String name;
    public IMinecraftLibrary(String name) {
	this.name = name;
    }
    
    public abstract boolean isRequiredToUnzip();
    public abstract String[] getDecompressExtractRules();
    public abstract void init();
    public abstract boolean allow();
    public abstract File getFilePath(File gameDir);
    public abstract String getDownloadURL(String urlBase, DownloadType downloadType);
    
    @Override
    public boolean equals(Object obj) {
	if(obj instanceof MinecraftLibrary)
	    return ((MinecraftLibrary) obj).name.equals(name);
	return false;
    }

    @Override
    public int hashCode() {
	int hash = 3;
	hash = 89 * hash + (this.name != null ? this.name.hashCode() : 0);
	return hash;
    }

    @Override
    protected abstract Object clone();
}
