/*
 * Hello Minecraft! Server Manager.
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
package org.jackhuang.hellominecraft.svrmgr.util;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.jackhuang.hellominecraft.util.C;
import org.jackhuang.hellominecraft.util.sys.IOUtils;

/**
 * 判断mod类型
 *
 * @author huangyuhui
 */
public class ModType {

    public static final int FORGE_MOD = 0;
    public static final int MODLOADER_MOD = 1;
    public static final int FORGE = 2;
    public static final int MODLOADER = 3;
    public static final int UNKOWN = 4;

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
                        return FORGE_MOD;
                    else if ("mcpmod.info".equals(gbkPath))
                        return FORGE;
                    else if ("ModLoader.class".equals(gbkPath))
                        isModLoader = true;
                    else if (gbkPath.trim().startsWith("mod_"))
                        return MODLOADER_MOD;
                }
            }
        } catch (Exception e) {

        } finally {
            IOUtils.closeQuietly(zipFile);
        }
        if (isModLoader)
            return MODLOADER_MOD;
        else
            return UNKOWN;
    }

    public static String getModTypeShowName(int type) {
        switch (type) {
        case FORGE_MOD:
            return C.i18n("ForgeMod");
        case FORGE:
            return C.i18n("Forge");
        case MODLOADER:
            return C.i18n("ModLoader");
        case MODLOADER_MOD:
            return C.i18n("ModLoaderMod");
        default:
            return C.i18n("Unknown");
        }
    }
}
