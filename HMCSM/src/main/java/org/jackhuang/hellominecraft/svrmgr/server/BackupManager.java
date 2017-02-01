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
package org.jackhuang.hellominecraft.svrmgr.server;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import org.jackhuang.hellominecraft.util.log.HMCLog;
import org.jackhuang.hellominecraft.util.sys.CompressingUtils;
import org.jackhuang.hellominecraft.svrmgr.setting.SettingsManager;
import org.jackhuang.hellominecraft.svrmgr.util.Utilities;
import org.jackhuang.hellominecraft.util.func.Consumer;
import org.jackhuang.hellominecraft.util.sys.FileUtils;
import org.jackhuang.hellominecraft.util.sys.IOUtils;

/**
 *
 * @author huangyuhui
 */
public class BackupManager {

    public static String backupDir() {
        return Utilities.getGameDir() + "backups-HMCSM" + File.separator;
    }

    public static void getBackupList(Consumer<String> c) {
        IOUtils.findAllFile(new File(backupDir()), c);
    }

    public static void addWorldBackup(final String folder) {
        new File(backupDir()).mkdirs();
        Thread t = new Thread() {
            @Override
            public void run() {
                try {
                    SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH-mm-ss");
                    CompressingUtils.zip(Utilities.getGameDir() + folder + File.separator,
                                   backupDir() + "world+" + f.format(new Date()) + "+" + folder + ".zip");
                } catch (IOException ex) {
                    HMCLog.warn("Failed to compress world pack.", ex);
                }
            }
        };
        t.start();
    }

    public static void findAllWorlds(Consumer<String> callback) {
        String gameDir = Utilities.getGameDir();
        IOUtils.findAllDir(new File(gameDir), folder -> {
                           String worldPath = gameDir + folder + File.separator;
                           IOUtils.findAllFile(new File(worldPath), f -> {
                                               if ("level.dat".equals(f))
                                                   callback.accept(folder);
                                           });
                       });
    }

    public static void restoreBackup(File backupFile) {
        try {
            String name = FileUtils.getExtension(backupFile.getName());
            String[] info = name.split("\\+");
            String folder = info[2];
            File world = new File(Utilities.getGameDir() + folder + File.separator);
            FileUtils.deleteDirectoryQuietly(world);
            world.mkdirs();
            CompressingUtils.unzip(backupFile, world);
        } catch (IOException ex) {
            HMCLog.warn("Failed to decompress world pack.", ex);
        }
    }

    public static void backupAllWorlds() {
        findAllWorlds(world -> {
            if (!SettingsManager.settings.inactiveWorlds.contains(world))
                addWorldBackup(world);
        });
    }

    public static void backupAllPlugins() {
        new File(backupDir()).mkdirs();
        Thread t = new Thread() {
            @Override
            public void run() {
                try {
                    SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH-mm-ss");
                    CompressingUtils.zip(Utilities.getGameDir() + "plugins" + File.separator,
                                   backupDir() + "plugin+" + f.format(new Date()) + "+plugins.zip");
                } catch (IOException ex) {
                    HMCLog.warn("Failed to compress world pack with plugins.", ex);
                }
            }
        };
        t.start();
    }

}
