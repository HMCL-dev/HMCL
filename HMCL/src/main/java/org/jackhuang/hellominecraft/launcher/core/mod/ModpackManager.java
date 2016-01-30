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
import java.nio.file.FileSystemException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.jackhuang.hellominecraft.util.C;
import org.jackhuang.hellominecraft.util.logging.HMCLog;
import org.jackhuang.hellominecraft.launcher.core.GameException;
import org.jackhuang.hellominecraft.launcher.core.service.IMinecraftProvider;
import org.jackhuang.hellominecraft.launcher.core.service.IMinecraftService;
import org.jackhuang.hellominecraft.launcher.core.version.MinecraftVersion;
import org.jackhuang.hellominecraft.util.func.BiFunction;
import org.jackhuang.hellominecraft.util.func.Predicate;
import org.jackhuang.hellominecraft.util.system.Compressor;
import org.jackhuang.hellominecraft.util.system.FileUtils;
import org.jackhuang.hellominecraft.util.system.ZipEngine;
import org.jackhuang.hellominecraft.util.tasks.Task;
import org.jackhuang.hellominecraft.util.version.MinecraftVersionRequest;

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
 * Not compatible with MultiMC(no instance.cfg processor) & FTB(not leaving
 * mcversion(jar) in pack.json).
 *
 * @author huangyuhui
 */
public final class ModpackManager {

    public static Task install(final File input, final IMinecraftService service, final String id) {
        return new Task() {
            Collection<Task> c = new ArrayList<>();

            @Override
            public void executeTask() throws Throwable {
                File versions = new File(service.baseDirectory(), "versions");
                File oldFile = new File(versions, "minecraft"), newFile = null;
                if (oldFile.exists()) {
                    newFile = new File(versions, "minecraft-" + System.currentTimeMillis());
                    if (newFile.isDirectory())
                        FileUtils.deleteDirectory(newFile);
                    else if (newFile.isFile())
                        newFile.delete();
                    oldFile.renameTo(newFile);
                }

                File preVersion = new File(versions, id), preVersionRenamed = null;
                if (preVersion.exists()) {
                    HMCLog.log("Backing up the game");
                    String preId = id + "-" + System.currentTimeMillis();
                    preVersion.renameTo(preVersionRenamed = new File(versions, preId));
                    new File(preVersionRenamed, id + ".json").renameTo(new File(preVersionRenamed, preId + ".json"));
                    new File(preVersionRenamed, id + ".jar").renameTo(new File(preVersionRenamed, preId + ".jar"));
                }

                try {
                    final AtomicInteger b = new AtomicInteger(0);
                    HMCLog.log("Decompressing modpack");
                    Compressor.unzip(input, versions, new Predicate<String>() {
                                     @Override
                                     public boolean apply(String t) {
                                         if (t.equals("minecraft/pack.json"))
                                             b.incrementAndGet();
                                         return true;
                                     }
                                 }, true);
                    if (b.get() < 1)
                        throw new FileNotFoundException(C.i18n("modpack.incorrect_format.no_json"));
                    File nowFile = new File(versions, id);
                    oldFile.renameTo(nowFile);

                    File json = new File(nowFile, "pack.json");
                    MinecraftVersion mv = C.gson.fromJson(FileUtils.readFileToString(json), MinecraftVersion.class);
                    if (mv.jar == null)
                        throw new FileNotFoundException(C.i18n("modpack.incorrect_format.no_jar"));

                    c.add(service.download().downloadMinecraftJarTo(mv.jar, new File(nowFile, id + ".jar")));
                    mv.jar = null;
                    FileUtils.writeStringToFile(json, C.gsonPrettyPrinting.toJson(mv));
                    json.renameTo(new File(nowFile, id + ".json"));

                    if (preVersionRenamed != null) {
                        HMCLog.log("Restoring saves");
                        File presaves = new File(preVersionRenamed, "saves");
                        File saves = new File(nowFile, "saves");
                        if (presaves.exists()) {
                            FileUtils.deleteDirectory(saves);
                            FileUtils.copyDirectory(presaves, saves);
                        }
                    }
                } finally {
                    FileUtils.deleteDirectoryQuietly(oldFile);
                    if (newFile != null)
                        newFile.renameTo(oldFile);
                }
            }

            @Override
            public String getInfo() {
                return C.i18n("modpack.install.task");
            }

            @Override
            public Collection<Task> getAfterTasks() {
                return c;
            }
        };

    }

    public static final List<String> MODPACK_BLACK_LIST = Arrays.asList(new String[] { "usernamecache.json", "asm", "logs", "backups", "versions", "assets", "usercache.json", "libraries", "crash-reports", "launcher_profiles.json", "NVIDIA", "TCNodeTracker", "screenshots", "natives", "native", "hmclversion.cfg", "pack.json" });
    public static final List<String> MODPACK_SUGGESTED_BLACK_LIST = Arrays.asList(new String[] { "saves", "servers.dat", "options.txt", "optionsshaders.txt", "mods/VoxelMods" });

    /**
     * &lt; String, Boolean, Boolean &gt;: Folder/File name, Is Directory,
     * Return 0: non blocked, 1: non shown, 2: suggested, checked.
     */
    public static final BiFunction<String, Boolean, Integer> MODPACK_PREDICATE = new BiFunction<String, Boolean, Integer>() {
        @Override
        public Integer apply(String x, Boolean y) {
            if (ModpackManager.MODPACK_BLACK_LIST_PREDICATE.apply(x, y))
                return 1;
            if (ModpackManager.MODPACK_SUGGESTED_BLACK_LIST_PREDICATE.apply(x, y))
                return 2;
            return 0;
        }
    };

    public static final BiFunction<String, Boolean, Boolean> MODPACK_BLACK_LIST_PREDICATE = modpackPredicateMaker(MODPACK_BLACK_LIST);
    public static final BiFunction<String, Boolean, Boolean> MODPACK_SUGGESTED_BLACK_LIST_PREDICATE = modpackPredicateMaker(MODPACK_SUGGESTED_BLACK_LIST);

    private static BiFunction<String, Boolean, Boolean> modpackPredicateMaker(final List<String> l) {
        return new BiFunction<String, Boolean, Boolean>() {
            @Override
            public Boolean apply(String x, Boolean y) {
                for (String s : l)
                    if (y) {
                        if (x.startsWith(s + "/"))
                            return true;
                    } else if (x.equals(s))
                        return true;
                return false;
            }
        };
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
    public static void export(File output, IMinecraftProvider provider, String version, List<String> blacklist) throws IOException, GameException {
        final ArrayList<String> b = new ArrayList<>(MODPACK_BLACK_LIST);
        if (blacklist != null)
            b.addAll(blacklist);
        b.add(version + ".jar");
        b.add(version + ".json");
        HMCLog.log("Compressing game files without some files in blacklist, including files or directories: usernamecache.json, asm, logs, backups, versions, assets, usercache.json, libraries, crash-reports, launcher_profiles.json, NVIDIA, TCNodeTracker");
        ZipEngine zip = null;
        try {
            zip = new ZipEngine(output);
            zip.putDirectory(provider.getRunDirectory(version), new BiFunction<String, Boolean, String>() {
                             @Override
                             public String apply(String x, Boolean y) {
                                 for (String s : b)
                                     if (y) {
                                         if (x.startsWith(s + "/"))
                                             return null;
                                     } else if (x.equals(s))
                                         return null;
                                 return "minecraft/" + x;
                             }
                         });

            MinecraftVersion mv = provider.getVersionById(version).resolve(provider);
            MinecraftVersionRequest r = MinecraftVersionRequest.minecraftVersion(provider.getMinecraftJar(version));
            if (r.type != MinecraftVersionRequest.OK)
                throw new FileSystemException(C.i18n("modpack.cannot_read_version") + ": " + MinecraftVersionRequest.getResponse(r));
            mv.jar = r.version;
            mv.runDir = "version";
            zip.putTextFile(C.gsonPrettyPrinting.toJson(mv), "minecraft/pack.json");
        } finally {
            if (zip != null)
                zip.closeFile();
        }
    }

}
