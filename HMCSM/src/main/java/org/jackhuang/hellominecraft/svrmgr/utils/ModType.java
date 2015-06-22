/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraft.svrmgr.utils;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 判断mod类型
 * @author hyh
 */
public class ModType {

    public static final int ForgeMod = 0;
    public static final int ModLoaderMod = 1;
    public static final int Forge = 2;
    public static final int ModLoader = 3;
    public static final int Unknown = 4;
    
    public static int getModType(String path) {
        return getModType(new File(path));
    }

    public static int getModType(File path) {
        boolean isModLoader = false;
        ZipFile zipFile = null;
        try {
            if (path.exists()) {
                zipFile = new ZipFile(path);
                String gbkPath;
                java.util.Enumeration e = zipFile.entries();
                while (e.hasMoreElements())
                {
                    ZipEntry zipEnt = (ZipEntry) e.nextElement();
                    if(zipEnt.isDirectory()) continue;
                    gbkPath = zipEnt.getName();
                    if("mcmod.info".equals(gbkPath))
                        return ForgeMod;
                    else if("mcpmod.info".equals(gbkPath))
                        return Forge;
                    else if("ModLoader.class".equals(gbkPath))
                        isModLoader = true;
                    else if(gbkPath.trim().startsWith("mod_"))
                        return ModLoaderMod;
                }
            }
        } catch(Exception e) {
            
        } finally {
            try {
                if(zipFile != null)
                    zipFile.close();
            } catch (IOException ex) {
                Logger.getLogger(ModType.class.getName()).log(Level.SEVERE, null, ex);
            } catch (Throwable t) {
                
            }
        }
        if(isModLoader)
            return ModLoaderMod;
        else
            return Unknown;
    }
    
    public static String getModTypeShowName(int type) {
        java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("org/jackhuang/hellominecraftlauncher/I18N"); // NOI18N
        switch(type) {
            case ForgeMod:
                return bundle.getString("ForgeMod");
            case Forge:
                return bundle.getString("Forge");
            case ModLoader:
                return bundle.getString("ModLoader");
            case ModLoaderMod:
                return bundle.getString("ModLoaderMod");
            default:
                return bundle.getString("Unknown");
        }
    }
}
