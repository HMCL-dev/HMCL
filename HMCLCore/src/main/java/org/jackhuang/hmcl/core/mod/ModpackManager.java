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
package org.jackhuang.hmcl.core.mod;

import java.awt.Dimension;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.FileSystemException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipFile;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import org.jackhuang.hmcl.util.C;
import org.jackhuang.hmcl.api.HMCLog;
import org.jackhuang.hmcl.core.GameException;
import org.jackhuang.hmcl.core.service.IMinecraftProvider;
import org.jackhuang.hmcl.core.service.IMinecraftService;
import org.jackhuang.hmcl.core.version.MinecraftVersion;
import org.jackhuang.hmcl.api.func.CallbackIO;
import org.jackhuang.hmcl.util.sys.CompressingUtils;
import org.jackhuang.hmcl.util.sys.FileUtils;
import org.jackhuang.hmcl.util.sys.ZipEngine;
import org.jackhuang.hmcl.util.task.Task;
import org.jackhuang.hmcl.util.net.WebPage;
import org.jackhuang.hmcl.util.MinecraftVersionRequest;
import org.jackhuang.hmcl.util.sys.IOUtils;
import org.jackhuang.hmcl.util.task.NoShownTaskException;

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

    /**
     * Install the compressed modpack.
     *
     * @param input modpack.zip
     * @param service MinecraftService, whose version service only supports
     * MinecraftVersionManager.
     * @param id new version id, if null, will use suggested name from
     * modpack
     *
     * @return The installing Task, may take long time, please consider
     * TaskWindow.
     */
    public static Task install(JFrame parFrame, final File input, final IMinecraftService service, final String idFUCK) {
        return new Task() {
            Collection<Task> c = new ArrayList<>();

            @Override
            public void executeTask(boolean areDependTasksSucceeded) throws Exception {
                String id = idFUCK;
                String description = C.i18n("modpack.task.install.will");

                // Read modpack name and description from `modpack.json`
                try (ZipFile zip = new ZipFile(input)) {
                    HashMap<String, String> map = C.GSON.fromJson(new InputStreamReader(zip.getInputStream(zip.getEntry("modpack.json")), "UTF-8"), HashMap.class);
                    if (map != null) {
                        if (id == null)
                            if (map.containsKey("name") && map.get("name") instanceof String)
                                id = map.get("name");
                        if (id != null)
                            description += id;
                        if (map.containsKey("description") && map.get("description") instanceof String)
                            description += "\n" + map.get("description");
                    }
                    if (id == null)
                        throw new IllegalStateException("Illegal modpack id!");
                }

                // Show a window to let the user know that what and how the modpack is.
                Object msgs[] = new Object[2];
                msgs[0] = C.i18n("modpack.task.install");
                msgs[1] = new WebPage(description);
                ((WebPage) msgs[1]).setPreferredSize(new Dimension(800, 350));
                int result = JOptionPane.showOptionDialog(parFrame, msgs, (String) msgs[0], JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null, null, null);
                if (result == JOptionPane.NO_OPTION)
                    throw new NoShownTaskException("Operation was canceled by user.");

                File versions = new File(service.baseDirectory(), "versions");

                // `minecraft` folder is the existent root folder of the modpack
                // Then we will decompress the modpack and there would be a folder named `minecraft`
                // So if `minecraft` folder does exist, backup it and then restore it.
                File oldFile = new File(versions, "minecraft"), newFile = null;
                if (oldFile.exists()) {
                    newFile = new File(versions, "minecraft-" + System.currentTimeMillis());
                    if (newFile.isDirectory())
                        FileUtils.deleteDirectory(newFile);
                    else if (newFile.isFile())
                        if (!newFile.delete())
                            HMCLog.warn("Failed to delete file " + newFile);
                    if (!oldFile.renameTo(newFile))
                        HMCLog.warn("Failed to rename " + oldFile + " to " + newFile);
                }

                // If the user install the modpack into an existent version, maybe it wants to update the modpack
                // So backup the game, copy the saved games.
                File preVersion = new File(versions, id), preVersionRenamed = null;
                if (preVersion.exists()) {
                    HMCLog.log("Backing up the game");
                    String preId = id + "-" + System.currentTimeMillis();
                    if (!preVersion.renameTo(preVersionRenamed = new File(versions, preId)))
                        HMCLog.warn("Failed to rename pre-version folder " + preVersion + " to a temp folder " + preVersionRenamed);
                    if (!new File(preVersionRenamed, id + ".json").renameTo(new File(preVersionRenamed, preId + ".json")))
                        HMCLog.warn("Failed to rename pre json to new json");

                    if (!new File(preVersionRenamed, id + ".jar").renameTo(new File(preVersionRenamed, preId + ".jar")))
                        HMCLog.warn("Failed to rename pre jar to new jar");
                }

                try {
                    final AtomicInteger b = new AtomicInteger(0);
                    HMCLog.log("Decompressing modpack");
                    CompressingUtils.unzip(input, versions, t -> {
                        if (t.equals("minecraft/pack.json"))
                            b.incrementAndGet();
                        return true;
                    }, true);

                    // No pack.json here, illegal modpack.
                    if (b.get() < 1)
                        throw new FileNotFoundException(C.i18n("modpack.incorrect_format.no_json"));
                    File nowFile = new File(versions, id);
                    if (oldFile.exists() && !oldFile.renameTo(nowFile))
                        HMCLog.warn("Failed to rename incorrect json " + oldFile + " to " + nowFile);

                    File json = new File(nowFile, "pack.json");
                    MinecraftVersion mv = C.GSON.fromJson(FileUtils.read(json), MinecraftVersion.class);
                    if (mv.jar == null)
                        throw new FileNotFoundException(C.i18n("modpack.incorrect_format.no_jar"));

                    c.add(service.download().downloadMinecraftJar(mv, new File(nowFile, id + ".jar")));
                    mv.jar = null;
                    FileUtils.write(json, C.GSON.toJson(mv));
                    if (!json.renameTo(new File(nowFile, id + ".json")))
                        HMCLog.warn("Failed to rename pack.json to new id");

                    // Restore the saved game from the old version.
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
                    if (newFile != null && !newFile.renameTo(oldFile))
                        HMCLog.warn("Failed to restore version minecraft");
                }
            }

            @Override
            public String getInfo() {
                return C.i18n("modpack.task.install");
            }

            @Override
            public Collection<Task> getAfterTasks() {
                return c;
            }
        };

    }

    public static final List<String> MODPACK_BLACK_LIST = Arrays.asList(new String[] { "usernamecache.json", "asm", "logs", "backups", "versions", "assets", "usercache.json", "libraries", "crash-reports", "launcher_profiles.json", "NVIDIA", "AMD", "TCNodeTracker", "screenshots", "natives", "native", "$native", "pack.json", "launcher.jar", "minetweaker.log", "launcher.pack.lzma", "hmclmc.log" });
    public static final List<String> MODPACK_SUGGESTED_BLACK_LIST = Arrays.asList(new String[] { "fonts", "saves", "servers.dat", "options.txt", "optionsof.txt", "journeymap", "optionsshaders.txt", "mods/VoxelMods" });

    public static ModAdviser MODPACK_PREDICATE = (String fileName, boolean isDirectory) -> {
        if (match(MODPACK_BLACK_LIST, fileName, isDirectory))
            return ModAdviser.ModSuggestion.HIDDEN;
        if (match(MODPACK_SUGGESTED_BLACK_LIST, fileName, isDirectory))
            return ModAdviser.ModSuggestion.NORMAL;
        return ModAdviser.ModSuggestion.SUGGESTED;
    };

    private static boolean match(final List<String> l, String fileName, boolean isDirectory) {
        for (String s : l)
            if (isDirectory) {
                if (fileName.startsWith(s + "/"))
                    return true;
            } else if (fileName.equals(s))
                return true;
        return false;
    }

    /**
     * Export the game to a mod pack file.
     *
     * @param output mod pack file.
     * @param baseFolder if the game dir type is ROOT_FOLDER, use ".minecraft",
     * or use ".minecraft/versions/{MCVER}/"
     * @param version to locate version.json
     *
     * @throws IOException if create tmp directory failed
     */
    public static void export(File output, IMinecraftProvider provider, String version, List<String> blacklist, Map<String, String> modpackPreferences, CallbackIO<ZipEngine> callback) throws IOException, GameException {
        final ArrayList<String> b = new ArrayList<>(MODPACK_BLACK_LIST);
        if (blacklist != null)
            b.addAll(blacklist);
        b.add(version + ".jar");
        b.add(version + ".json");
        HMCLog.log("Compressing game files without some files in blacklist, including files or directories: usernamecache.json, asm, logs, backups, versions, assets, usercache.json, libraries, crash-reports, launcher_profiles.json, NVIDIA, TCNodeTracker");
        ZipEngine zip = null;
        try {
            zip = new ZipEngine(output);
            zip.putDirectory(provider.getRunDirectory(version), (String x, Boolean y) -> {
                for (String s : b)
                    if (y) {
                        if (x.startsWith(s + "/"))
                            return null;
                    } else if (x.equals(s))
                        return null;
                return "minecraft/" + x;
            });

            MinecraftVersion mv = provider.getVersionById(version).resolve(provider);
            MinecraftVersionRequest r = MinecraftVersionRequest.minecraftVersion(provider.getMinecraftJar(version));
            if (r.type != MinecraftVersionRequest.OK)
                throw new FileSystemException(C.i18n("modpack.cannot_read_version") + ": " + MinecraftVersionRequest.getResponse(r));
            mv.jar = r.version;
            mv.runDir = "version";
            zip.putTextFile(C.GSON.toJson(mv), "minecraft/pack.json");
            zip.putTextFile(C.GSON.toJson(modpackPreferences), "modpack.json");
            if (callback != null)
                callback.call(zip);
        } finally {
            IOUtils.closeQuietly(zip);
        }
    }

}
