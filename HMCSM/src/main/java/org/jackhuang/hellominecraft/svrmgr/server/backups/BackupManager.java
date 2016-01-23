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
package org.jackhuang.hellominecraft.svrmgr.server.backups;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import org.jackhuang.hellominecraft.utils.HMCLog;
import org.jackhuang.hellominecraft.utils.system.Compressor;
import org.jackhuang.hellominecraft.svrmgr.settings.SettingsManager;
import org.jackhuang.hellominecraft.svrmgr.utils.Utilities;
import org.jackhuang.hellominecraft.utils.system.FileUtils;
import org.jackhuang.hellominecraft.utils.system.IOUtils;

/**
 *
 * @author huangyuhui
 */
public class BackupManager {

    public static String backupDir() {
        return Utilities.getGameDir() + "backups-HMCSM" + File.separator;
    }

    public static ArrayList<String> getBackupList() {
        String gameDir = backupDir();
        return IOUtils.findAllFile(new File(gameDir));
    }

    public static void addWorldBackup(final String folder) {
        new File(backupDir()).mkdirs();
        Thread t = new Thread() {
            @Override
            public void run() {
                try {
                    SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH-mm-ss");
                    Compressor.zip(Utilities.getGameDir() + folder + File.separator,
                                   backupDir() + "world+" + f.format(new Date()) + "+" + folder + ".zip");
                } catch (IOException ex) {
                    HMCLog.warn("Failed to compress world pack.", ex);
                }
            }
        };
        t.start();
    }

    public static ArrayList<String> findAllWorlds() {
        String gameDir = Utilities.getGameDir();
        ArrayList<String> folders = IOUtils.findAllDir(new File(gameDir));
        ArrayList<String> result = new ArrayList<>();
        for (String folder : folders) {
            String worldPath = gameDir + folder + File.separator;
            ArrayList<String> files = IOUtils.findAllFile(new File(worldPath));
            if (files.contains("level.dat"))
                result.add(folder);
        }
        return result;
    }

    public static void restoreBackup(File backupFile) {
        try {
            String name = FileUtils.getExtension(backupFile.getName());
            String[] info = name.split("\\+");
            String folder = info[2];
            File world = new File(Utilities.getGameDir() + folder + File.separator);
            FileUtils.deleteDirectoryQuietly(world);
            world.mkdirs();
            Compressor.unzip(backupFile, world);
        } catch (IOException ex) {
            HMCLog.warn("Failed to decompress world pack.", ex);
        }
    }

    public static void backupAllWorlds() {
        ArrayList<String> al = findAllWorlds();
        for (String world : al)
            if (!SettingsManager.settings.inactiveWorlds.contains(world))
                addWorldBackup(world);
    }

    public static void backupAllPlugins() {
        new File(backupDir()).mkdirs();
        Thread t = new Thread() {
            @Override
            public void run() {
                try {
                    SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH-mm-ss");
                    Compressor.zip(Utilities.getGameDir() + "plugins" + File.separator,
                                   backupDir() + "plugin+" + f.format(new Date()) + "+plugins.zip");
                } catch (IOException ex) {
                    HMCLog.warn("Failed to compress world pack with plugins.", ex);
                }
            }
        };
        t.start();
    }

}
