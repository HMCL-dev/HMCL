/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraft.launcher.utils.version;

import java.io.File;
import java.util.ArrayList;
import java.util.Set;
import org.jackhuang.hellominecraft.launcher.launch.IMinecraftProvider;
import org.jackhuang.hellominecraft.launcher.utils.download.DownloadType;

/**
 *
 * @author hyh
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
