/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2017  huangyuhui <huanghongxun2008@126.com>
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
package org.jackhuang.hmcl.game;


import org.jackhuang.hmcl.mod.Modpack;
import org.jackhuang.hmcl.task.TaskResult;
import org.jackhuang.hmcl.util.Constants;
import org.jackhuang.hmcl.util.Logging;
import org.jackhuang.hmcl.util.ZipEngine;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Export the game to a mod pack file.
 */
public class HMCLModpackExportTask extends TaskResult<ZipEngine> {
    private final DefaultGameRepository repository;
    private final String version;
    private final List<String> whitelist;
    private final Modpack modpack;
    private final File output;
    private final String id;

    public HMCLModpackExportTask(DefaultGameRepository repository, String version, List<String> whitelist, Modpack modpack, File output) {
        this(repository, version, whitelist, modpack, output, ID);
    }

    /**
     * @param output  mod pack file.
     * @param version to locate version.json
     */
    public HMCLModpackExportTask(DefaultGameRepository repository, String version, List<String> whitelist, Modpack modpack, File output, String id) {
        this.repository = repository;
        this.version = version;
        this.whitelist = whitelist;
        this.modpack = modpack;
        this.output = output;
        this.id = id;

        onDone().register(event -> {
            if (event.isFailed()) output.delete();
        });
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void execute() throws Exception {
        ArrayList<String> blackList = new ArrayList<>(HMCLModpackManager.MODPACK_BLACK_LIST);
        blackList.add(version + ".jar");
        blackList.add(version + ".json");
        Logging.LOG.info("Compressing game files without some files in blacklist, including files or directories: usernamecache.json, asm, logs, backups, versions, assets, usercache.json, libraries, crash-reports, launcher_profiles.json, NVIDIA, TCNodeTracker");
        try (ZipEngine zip = new ZipEngine(output)) {
            zip.putDirectory(repository.getRunDirectory(version), (String pathName, Boolean isDirectory) -> {
                for (String s : blackList)
                    if (isDirectory) {
                        if (pathName.startsWith(s + "/"))
                            return null;
                    } else if (pathName.equals(s))
                        return null;
                for (String s : whitelist)
                    if (pathName.equals(s + (isDirectory ? "/" : "")))
                        return "minecraft/" + pathName;
                return null;
            });

            Version mv = repository.getVersion(version).resolve(repository);
            String gameVersion = GameVersion.minecraftVersion(repository.getVersionJar(version));
            if (gameVersion == null)
                throw new IllegalStateException("Cannot parse the version of " + version);
            zip.putTextFile(Constants.GSON.toJson(mv.setJar(gameVersion)), "minecraft/pack.json"); // Making "jar" to gameVersion is to be compatible with old HMCL.
            zip.putTextFile(Constants.GSON.toJson(modpack.setGameVersion(gameVersion)), "modpack.json"); // Newer HMCL only reads 'gameVersion' field.
        }
    }

    public static final String ID = "zip_engine";
}
