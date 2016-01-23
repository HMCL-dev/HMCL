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
package org.jackhuang.hellominecraft.launcher.core.mod;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystemException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jackhuang.hellominecraft.utils.C;
import org.jackhuang.hellominecraft.utils.HMCLog;
import org.jackhuang.hellominecraft.launcher.core.GameException;
import org.jackhuang.hellominecraft.launcher.core.service.IMinecraftProvider;
import org.jackhuang.hellominecraft.launcher.core.version.MinecraftVersion;
import org.jackhuang.hellominecraft.utils.system.Compressor;
import org.jackhuang.hellominecraft.utils.system.FileUtils;

/**
 * A mod pack(*.zip) includes these things:
 * <ul>
 * <li>sth created by the game automatically, including "mods", "scripts",
 * "config", etc..
 * <li>pack.json, the same as Minecraft version configuration file:
 * "gameDir/versions/{MCVER}/{MCVER}.json", will be renamed to that one.
 * <li>all things should be included in "minecraft" folder under the root
 * folder.
 * </ul>
 *
 * This class can manage mod packs, for example, importing and exporting, the
 * format of game is the offical one.
 * Not compatible with MultiMC(no instance.cfg) & FTB(not leaving mcversion in
 * pack.json).
 *
 * @author huangyuhui
 */
public final class ModpackManager {

    public static void install(File input, File installFolder, String id) throws IOException, FileAlreadyExistsException {
        File versions = new File(installFolder, "versions");
        File oldFile = new File(versions, "minecraft"), newFile = null;
        if (oldFile.exists()) {
            newFile = new File(versions, "minecraft-" + System.currentTimeMillis());
            if (newFile.isDirectory())
                FileUtils.deleteDirectory(newFile);
            else if (newFile.isFile())
                newFile.delete();
            oldFile.renameTo(newFile);
        }

        try {
            AtomicBoolean b = new AtomicBoolean(false);
            HMCLog.log("Decompressing modpack");
            Compressor.unzip(input, versions, t -> {
                             if (t.equals("minecraft/pack.json"))
                                 b.set(true);
                             return true;
                         });
            if (!b.get())
                throw new FileNotFoundException("the mod pack is not in a correct format.");
            File nowFile = new File(versions, id);
            oldFile.renameTo(nowFile);

            new File(nowFile, "pack.json").renameTo(new File(nowFile, id + ".json"));
        } finally {
            FileUtils.deleteDirectoryQuietly(oldFile);
            if (newFile != null)
                newFile.renameTo(oldFile);
        }
    }

    /**
     * Export the game to a mod pack file.
     *
     * @param output     mod pack file.
     * @param baseFolder if the game dir type is ROOT_FOLDER, use ".minecraft",
     *                   or use ".minecraft/versions/{MCVER}/"
     * @param version    to locate version.json
     *
     * @throws IOException if create tmp directory failed
     */
    public static void export(File output, IMinecraftProvider provider, String version) throws IOException, GameException {
        File tmp = new File(System.getProperty("java.io.tmpdir"), "hmcl-modpack");
        tmp.mkdirs();

        File root = new File(tmp, "minecraft");

        HMCLog.log("Copying files from game directory.");
        FileUtils.copyDirectory(provider.getRunDirectory(version), root);
        File pack = new File(root, "pack.json");
        MinecraftVersion mv = provider.getVersionById(version).resolve(provider);
        try {
            FileUtils.writeStringToFile(pack, C.gsonPrettyPrinting.toJson(mv));
            String[] blacklist = { "usernamecache.json", "asm", "logs", "backups", "versions", "assets", "usercache.json", "libraries", "crash-reports", "launcher_profiles.json", "NVIDIA", "TCNodeTracker" };
            HMCLog.log("Removing files in blacklist, including files or directories: usernamecache.json, asm, logs, backups, versions, assets, usercache.json, libraries, crash-reports, launcher_profiles.json, NVIDIA, TCNodeTracker");
            for (String s : blacklist) {
                File f = new File(root, s);
                if (f.isFile())
                    f.delete();
                else if (f.isDirectory())
                    FileUtils.deleteDirectory(f);
            }
            HMCLog.log("Compressing game files");
            Compressor.zip(tmp, output);
        } finally {
            FileUtils.deleteDirectory(tmp);
        }
    }

}
