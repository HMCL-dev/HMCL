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

import com.google.gson.JsonParseException;
import org.jackhuang.hmcl.mod.Modpack;
import org.jackhuang.hmcl.util.CompressingUtils;
import org.jackhuang.hmcl.util.Constants;
import org.jackhuang.hmcl.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * @author huangyuhui
 */
public final class HMCLModpackManager {

    public static final List<String> MODPACK_BLACK_LIST = Arrays.asList("usernamecache.json", "asm", "logs", "backups", "versions", "assets", "usercache.json", "libraries", "crash-reports", "launcher_profiles.json", "NVIDIA", "AMD", "TCNodeTracker", "screenshots", "natives", "native", "$native", "pack.json", "launcher.jar", "minetweaker.log", "launcher.pack.lzma", "hmclmc.log");
    public static final List<String> MODPACK_SUGGESTED_BLACK_LIST = Arrays.asList("fonts", "saves", "servers.dat", "options.txt", "optionsof.txt", "journeymap", "optionsshaders.txt", "mods/VoxelMods");

    public static ModAdviser MODPACK_PREDICATE = (String fileName, boolean isDirectory) -> {
        if (match(MODPACK_BLACK_LIST, fileName, isDirectory))
            return ModAdviser.ModSuggestion.HIDDEN;
        if (match(MODPACK_SUGGESTED_BLACK_LIST, fileName, isDirectory))
            return ModAdviser.ModSuggestion.NORMAL;
        else
            return ModAdviser.ModSuggestion.SUGGESTED;
    };

    private static boolean match(List<String> l, String fileName, boolean isDirectory) {
        for (String s : l)
            if (isDirectory) {
                if (fileName.startsWith(s + "/"))
                    return true;
            } else if (fileName.equals(s))
                return true;
        return false;
    }

    /**
     * Read the manifest in a HMCL modpack.
     *
     * @param file a HMCL modpack file.
     * @throws IOException if the file is not a valid zip file.
     * @throws JsonParseException if the manifest.json is missing or malformed.
     * @return the manifest of HMCL modpack.
     */
    public static Modpack readHMCLModpackManifest(File file) throws IOException, JsonParseException {
        String manifestJson = CompressingUtils.readTextZipEntry(file, "modpack.json");
        Modpack manifest = Constants.GSON.fromJson(manifestJson, Modpack.class);
        if (manifest == null)
            throw new JsonParseException("`modpack.json` not found. " + file + " is not a valid HMCL modpack.");
        String gameJson = CompressingUtils.readTextZipEntry(file, "minecraft/pack.json");
        Version game = Constants.GSON.fromJson(gameJson, Version.class);
        if (game == null)
            throw new JsonParseException("`minecraft/pack.json` not found. " + file + " iot a valid HMCL modpack.");
        if (game.getJar() == null)
            if (StringUtils.isBlank(manifest.getVersion()))
                throw new JsonParseException("Cannot recognize the game version of modpack " + file + ".");
            else
                return manifest.setManifest(HMCLModpackManifest.INSTANCE);
        else
            return manifest.setManifest(HMCLModpackManifest.INSTANCE).setGameVersion(game.getJar());
    }
}
