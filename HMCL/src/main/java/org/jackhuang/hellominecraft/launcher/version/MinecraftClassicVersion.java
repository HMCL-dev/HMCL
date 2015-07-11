/*
 * Copyright 2013 huangyuhui <huanghongxun2008@126.com>
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.
 */
package org.jackhuang.hellominecraft.launcher.version;

import java.io.File;
import java.util.ArrayList;
import java.util.Set;
import org.jackhuang.hellominecraft.launcher.launch.IMinecraftProvider;
import org.jackhuang.hellominecraft.launcher.utils.download.DownloadType;

/**
 *
 * @author huangyuhui
 */
public class MinecraftClassicVersion extends MinecraftVersion {

    public MinecraftClassicVersion() {
        super();

        mainClass = "net.minecraft.client.Minecraft";
        id = "Classic";
        type = "release";
        processArguments = assets = releaseTime = time = null;
        minecraftArguments = "${auth_player_name} ${auth_session} --workDir ${game_directory}";
        libraries = new ArrayList<>();
        libraries.add(new MinecraftOldLibrary("lwjgl"));
        libraries.add(new MinecraftOldLibrary("jinput"));
        libraries.add(new MinecraftOldLibrary("lwjgl_util"));
    }

    @Override
    public Object clone() {
        return super.clone();
    }

    @Override
    public MinecraftVersion resolve(IMinecraftProvider manager, Set<String> resolvedSoFar, DownloadType sourceType) {
        return this;
    }

    @Override
    public File getJar(File gameDir) {
        return new File(gameDir, "bin/minecraft.jar");
    }

    @Override
    public File getJar(File gameDir, String suffix) {
        return new File(gameDir, "bin/minecraft" + suffix + ".jar");
    }

    @Override
    public File getNatives(File gameDir) {
        return new File(gameDir, "bin/natives");
    }

    @Override
    public boolean isAllowedToUnpackNatives() {
        return false;
    }
}
