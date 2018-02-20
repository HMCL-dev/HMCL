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

import com.google.gson.JsonParseException;
import org.jackhuang.hmcl.mod.Modpack;
import org.jackhuang.hmcl.util.CompressingUtils;
import org.jackhuang.hmcl.util.JsonUtils;
import org.jackhuang.hmcl.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * @author huangyuhui
 */
public final class HMCLModpackManager {

    public static final List<String> MODPACK_BLACK_LIST = Arrays.asList(
            "usernamecache.json", "usercache.json", // Minecraft
            "launcher_profiles.json", "launcher.pack.lzma", // Minecraft Launcher
            "pack.json", "launcher.jar", "hmclmc.log", // HMCL
            "manifest.json", "minecraftinstance.json", ".curseclient", // Curse
            "minetweaker.log", // Mods
            "logs", "versions", "assets", "libraries", "crash-reports", "NVIDIA", "AMD", "screenshots", "natives", "native", "$native", "server-resource-packs", // Minecraft
            "downloads", // Curse
            "asm", "backups", "TCNodeTracker", "CustomDISkins", "data" // Mods
    );
    public static final List<String> MODPACK_SUGGESTED_BLACK_LIST = Arrays.asList(
            "fonts", // BetterFonts
            "saves", "servers.dat", "options.txt", // Minecraft
            "blueprints" /* BuildCraft */,
            "optionsof.txt" /* OptiFine */,
            "journeymap" /* JourneyMap */,
            "optionsshaders.txt",
            "mods/VoxelMods");

    public static ModAdviser.ModSuggestion suggestMod(String fileName, boolean isDirectory) {
        if (match(MODPACK_BLACK_LIST, fileName, isDirectory))
            return ModAdviser.ModSuggestion.HIDDEN;
        if (match(MODPACK_SUGGESTED_BLACK_LIST, fileName, isDirectory))
            return ModAdviser.ModSuggestion.NORMAL;
        else
            return ModAdviser.ModSuggestion.SUGGESTED;
    }

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
        Modpack manifest = JsonUtils.fromNonNullJson(manifestJson, Modpack.class);
        String gameJson = CompressingUtils.readTextZipEntry(file, "minecraft/pack.json");
        Version game = JsonUtils.fromNonNullJson(gameJson, Version.class);
        if (game.getJar() == null)
            if (StringUtils.isBlank(manifest.getVersion()))
                throw new JsonParseException("Cannot recognize the game version of modpack " + file + ".");
            else
                return manifest.setManifest(HMCLModpackManifest.INSTANCE);
        else
            return manifest.setManifest(HMCLModpackManifest.INSTANCE).setGameVersion(game.getJar());
    }
}
