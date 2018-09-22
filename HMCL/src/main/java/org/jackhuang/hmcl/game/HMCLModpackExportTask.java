/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2018  huangyuhui <huanghongxun2008@126.com>
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
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.Constants;
import org.jackhuang.hmcl.util.Logging;
import org.jackhuang.hmcl.util.io.Zipper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Export the game to a mod pack file.
 */
public class HMCLModpackExportTask extends Task {
    private final DefaultGameRepository repository;
    private final String version;
    private final List<String> whitelist;
    private final Modpack modpack;
    private final File output;

    /**
     * @param output  mod pack file.
     * @param version to locate version.json
     */
    public HMCLModpackExportTask(DefaultGameRepository repository, String version, List<String> whitelist, Modpack modpack, File output) {
        this.repository = repository;
        this.version = version;
        this.whitelist = whitelist;
        this.modpack = modpack;
        this.output = output;

        onDone().register(event -> {
            if (event.isFailed()) output.delete();
        });
    }

    @Override
    public void execute() throws Exception {
        ArrayList<String> blackList = new ArrayList<>(HMCLModpackManager.MODPACK_BLACK_LIST);
        blackList.add(version + ".jar");
        blackList.add(version + ".json");
        Logging.LOG.info("Compressing game files without some files in blacklist, including files or directories: usernamecache.json, asm, logs, backups, versions, assets, usercache.json, libraries, crash-reports, launcher_profiles.json, NVIDIA, TCNodeTracker");
        try (Zipper zip = new Zipper(output.toPath())) {
            zip.putDirectory(repository.getRunDirectory(version).toPath(), "minecraft", path -> {
                for (String s : blackList)
                    if (path.equals(s))
                        return false;
                for (String s : whitelist)
                    if (path.equals(s))
                        return true;
                return false;
            });

            Version mv = repository.getResolvedVersion(version);
            String gameVersion = GameVersion.minecraftVersion(repository.getVersionJar(version))
                    .orElseThrow(() ->  new IllegalStateException("Cannot parse the version of " + version));
            zip.putTextFile(Constants.GSON.toJson(mv.setJar(gameVersion)), "minecraft/pack.json"); // Making "jar" to gameVersion is to be compatible with old HMCL.
            zip.putTextFile(Constants.GSON.toJson(modpack.setGameVersion(gameVersion)), "modpack.json"); // Newer HMCL only reads 'gameVersion' field.
        }
    }
}
