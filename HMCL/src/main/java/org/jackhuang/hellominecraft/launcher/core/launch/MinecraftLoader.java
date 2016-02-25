/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2013  huangyuhui
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
package org.jackhuang.hellominecraft.launcher.core.launch;

import java.io.File;
import java.io.IOException;
import java.util.List;
import org.jackhuang.hellominecraft.util.StrUtils;
import org.jackhuang.hellominecraft.util.func.Function;
import org.jackhuang.hellominecraft.util.logging.HMCLog;
import org.jackhuang.hellominecraft.util.system.IOUtils;
import org.jackhuang.hellominecraft.util.system.OS;
import org.jackhuang.hellominecraft.launcher.core.GameException;
import org.jackhuang.hellominecraft.launcher.core.auth.UserProfileProvider;
import org.jackhuang.hellominecraft.launcher.core.version.MinecraftLibrary;
import org.jackhuang.hellominecraft.launcher.core.version.MinecraftVersion;
import org.jackhuang.hellominecraft.launcher.core.service.IMinecraftService;

/**
 *
 * @author huangyuhui
 */
public class MinecraftLoader extends AbstractMinecraftLoader {

    public MinecraftLoader(LaunchOptions p, IMinecraftService provider, UserProfileProvider lr) throws GameException {
        super(p, provider, p.getLaunchVersion(), lr);
    }

    @Override
    protected void makeSelf(List<String> res) throws GameException {
        StringBuilder library = new StringBuilder(options.isCanceledWrapper() ? "" : "-cp=");
        for (MinecraftLibrary l : version.libraries)
            if (l.allow() && !l.isRequiredToUnzip())
                library.append(l.getFilePath(gameDir).getAbsolutePath()).append(File.pathSeparator);
        File f = version.getJar(service.baseDirectory());
        if (!f.exists())
            throw new GameException("Minecraft jar does not exists");
        library.append(IOUtils.tryGetCanonicalFilePath(f)).append(File.pathSeparator);
        if (options.isCanceledWrapper())
            res.add("-cp");
        res.add(library.toString().substring(0, library.length() - File.pathSeparator.length()));
        String mainClass = version.mainClass;
        res.add((options.isCanceledWrapper() ? "" : "-mainClass=") + mainClass);

        String[] splitted = StrUtils.tokenize(version.minecraftArguments);

        String game_assets = assetProvider.apply(version);

        for (String t : splitted) {
            t = t.replace("${auth_player_name}", lr.getUserName());
            t = t.replace("${auth_session}", lr.getSession());
            t = t.replace("${auth_uuid}", lr.getUserId());
            t = t.replace("${version_name}", options.getVersionName());
            t = t.replace("${profile_name}", options.getName());
            t = t.replace("${version_type}", options.getType());
            t = t.replace("${game_directory}", service.version().getRunDirectory(version.id).getAbsolutePath());
            t = t.replace("${game_assets}", game_assets);
            t = t.replace("${assets_root}", service.asset().getAssets().getAbsolutePath());
            t = t.replace("${auth_access_token}", lr.getAccessToken());
            t = t.replace("${user_type}", lr.getUserType());
            t = t.replace("${assets_index_name}", version.getAssetsIndex().getId());
            t = t.replace("${user_properties}", lr.getUserProperties());
            res.add(t);
        }

        if (res.indexOf("--gameDir") != -1 && res.indexOf("--workDir") != -1) {
            res.add("--workDir");
            res.add(gameDir.getAbsolutePath());
        }
    }

    @Override
    protected void appendJVMArgs(List<String> list) {
        super.appendJVMArgs(list);

        try {
            if (OS.os() == OS.OSX) {
                list.add("-Xdock:icon=" + service.asset().getAssetObject(version.getAssetsIndex().getId(), "icons/minecraft.icns").getAbsolutePath());
                list.add("-Xdock:name=Minecraft");
            }
        } catch (IOException e) {
            HMCLog.err("Failed to append jvm arguments when searching for asset objects.", e);
        }
    }

    private final Function<MinecraftVersion, String> DEFAULT_ASSET_PROVIDER = t -> {
        return new File(service.baseDirectory(), "assets").getAbsolutePath();
    };

    private Function<MinecraftVersion, String> assetProvider = DEFAULT_ASSET_PROVIDER;

    public void setAssetProvider(Function<MinecraftVersion, String> assetProvider) {
        if (assetProvider == null)
            this.assetProvider = DEFAULT_ASSET_PROVIDER;
        else
            this.assetProvider = assetProvider;
    }
}
