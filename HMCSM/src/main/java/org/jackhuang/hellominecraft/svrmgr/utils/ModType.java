/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2013  huangyuhui
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see {http://www.gnu.org/licenses/}.
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
 *
 * @author huangyuhui
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
                while (e.hasMoreElements()) {
                    ZipEntry zipEnt = (ZipEntry) e.nextElement();
                    if (zipEnt.isDirectory())
                        continue;
                    gbkPath = zipEnt.getName();
                    if ("mcmod.info".equals(gbkPath))
                        return ForgeMod;
                    else if ("mcpmod.info".equals(gbkPath))
                        return Forge;
                    else if ("ModLoader.class".equals(gbkPath))
                        isModLoader = true;
                    else if (gbkPath.trim().startsWith("mod_"))
                        return ModLoaderMod;
                }
            }
        } catch (Exception e) {

        } finally {
            try {
                if (zipFile != null)
                    zipFile.close();
            } catch (IOException ex) {
                Logger.getLogger(ModType.class.getName()).log(Level.SEVERE, null, ex);
            } catch (Throwable t) {

            }
        }
        if (isModLoader)
            return ModLoaderMod;
        else
            return Unknown;
    }

    public static String getModTypeShowName(int type) {
        java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("org/jackhuang/hellominecraftlauncher/I18N"); // NOI18N
        switch (type) {
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
