/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2019  huangyuhui <huanghongxun2008@126.com> and contributors
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
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.jackhuang.hmcl.mod;

import org.jackhuang.hmcl.util.Lang;

import java.util.List;

/**
 * @author huangyuhui
 */
public interface ModAdviser {
    ModSuggestion advise(String fileName, boolean isDirectory);

    enum ModSuggestion {
        SUGGESTED,
        NORMAL,
        HIDDEN
    }

    List<String> MODPACK_BLACK_LIST = Lang.immutableListOf(
            "usernamecache.json", "usercache.json", // Minecraft
            "launcher_profiles.json", "launcher.pack.lzma", // Minecraft Launcher
            "pack.json", "launcher.jar", "hmclmc.log", "cache", // HMCL
            "manifest.json", "minecraftinstance.json", ".curseclient", // Curse
            "minetweaker.log", // Mods
            ".fabric", ".mixin.out", // Fabric
            "jars", "logs", "versions", "assets", "libraries", "crash-reports", "NVIDIA", "AMD", "screenshots", "natives", "native", "$native", "server-resource-packs", // Minecraft
            "downloads", // Curse
            "asm", "backups", "TCNodeTracker", "CustomDISkins", "data" // Mods
    );

    List<String> MODPACK_SUGGESTED_BLACK_LIST = Lang.immutableListOf(
            "fonts", // BetterFonts
            "saves", "servers.dat", "options.txt", // Minecraft
            "blueprints" /* BuildCraft */,
            "optionsof.txt" /* OptiFine */,
            "journeymap" /* JourneyMap */,
            "optionsshaders.txt",
            "mods/VoxelMods");

    static ModAdviser.ModSuggestion suggestMod(String fileName, boolean isDirectory) {
        if (match(MODPACK_BLACK_LIST, fileName, isDirectory))
            return ModAdviser.ModSuggestion.HIDDEN;
        if (match(MODPACK_SUGGESTED_BLACK_LIST, fileName, isDirectory))
            return ModAdviser.ModSuggestion.NORMAL;
        else
            return ModAdviser.ModSuggestion.SUGGESTED;
    }

    static boolean match(List<String> l, String fileName, boolean isDirectory) {
        for (String s : l)
            if (isDirectory) {
                if (fileName.startsWith(s + "/"))
                    return true;
            } else if (fileName.equals(s))
                return true;
        return false;
    }
}
