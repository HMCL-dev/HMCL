/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraft.svrmgr.server;

import java.io.File;
import java.io.IOException;
import java.util.zip.ZipFile;
import org.jackhuang.hellominecraft.HMCLog;

/**
 *
 * @author huangyuhui
 */
public class ServerChecker {
    
    public static boolean isServerJar(File f) {
        ZipFile file;
        try {
            file = new ZipFile(f);
        } catch (IOException ex) {
	    HMCLog.warn("", ex);
            return false;
        }
        if(file.getEntry("org/bukkit/craftbukkit/Main.class") != null) {
            return true;
        }
        if(file.getEntry("net/minecraft/server/MinecraftServer.class") != null) {
            return true;
        }
        return false;
    }
    
}
