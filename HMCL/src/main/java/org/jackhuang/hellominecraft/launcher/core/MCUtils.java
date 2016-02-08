/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2013  huangyuhui <huanghongxun2008@126.com>
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
package org.jackhuang.hellominecraft.launcher.core;

import java.io.File;
import java.io.IOException;
import org.jackhuang.hellominecraft.util.system.FileUtils;
import org.jackhuang.hellominecraft.util.system.IOUtils;
import org.jackhuang.hellominecraft.util.system.OS;

/**
 *
 * @author huang
 */
public final class MCUtils {

    public static File getWorkingDirectory(String baseName) {
        String userhome = System.getProperty("user.home", ".");
        File file;
        switch (OS.os()) {
        case LINUX:
            file = new File(userhome, '.' + baseName + '/');
            break;
        case WINDOWS:
            String appdata = System.getenv("APPDATA");
            if (appdata != null)
                file = new File(appdata, "." + baseName + '/');
            else
                file = new File(userhome, '.' + baseName + '/');
            break;
        case OSX:
            file = new File(userhome, "Library/Application Support/" + baseName);
            break;
        default:
            file = new File(userhome, baseName + '/');
        }
        return file;
    }

    public static File getLocation() {
        return getWorkingDirectory("minecraft");
    }

    public static String minecraft() {
        if (OS.os() == OS.OSX)
            return "minecraft";
        return ".minecraft";
    }

    public static File getInitGameDir() {
        File gameDir = IOUtils.currentDir();
        if (gameDir.exists()) {
            gameDir = new File(gameDir, MCUtils.minecraft());
            if (!gameDir.exists()) {
                File newFile = MCUtils.getLocation();
                if (newFile.exists())
                    gameDir = newFile;
            }
        }
        return gameDir;
    }

    public static final String PROFILE = "{\"selectedProfile\": \"(Default)\",\"profiles\": {\"(Default)\": {\"name\": \"(Default)\"}},\"clientToken\": \"88888888-8888-8888-8888-888888888888\"}";

    public static void tryWriteProfile(File gameDir) throws IOException {
        File file = new File(gameDir, "launcher_profiles.json");
        if (!file.exists())
            FileUtils.writeStringToFile(file, PROFILE);
    }
}
