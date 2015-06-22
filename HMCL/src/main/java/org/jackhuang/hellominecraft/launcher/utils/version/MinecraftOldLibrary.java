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
public class MinecraftOldLibrary extends MinecraftLibrary {

    public MinecraftOldLibrary(String name) {
	super(name);
    }

    @Override
    public boolean isRequiredToUnzip() {
	return false;
    }

    @Override
    public void init() {
    }

    @Override
    public boolean allow() {
	return true;
    }

    @Override
    public File getFilePath(File gameDir) {
	return new File(gameDir, "bin/" + name + ".jar");
    }

    @Override
    public Object clone() {
	return new MinecraftOldLibrary(name);
    }

    @Override
    public String getDownloadURL(String urlBase, DownloadType downloadType) {
	return null;
    }
    
}
