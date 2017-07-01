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
package org.jackhuang.hmcl.core.launch;

import org.jackhuang.hmcl.api.game.LaunchOptions;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.jackhuang.hmcl.util.StrUtils;
import org.jackhuang.hmcl.api.HMCLog;
import org.jackhuang.hmcl.util.sys.OS;
import org.jackhuang.hmcl.core.GameException;
import org.jackhuang.hmcl.api.auth.UserProfileProvider;
import org.jackhuang.hmcl.core.version.MinecraftLibrary;
import org.jackhuang.hmcl.core.service.IMinecraftService;

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
        StringBuilder library = new StringBuilder("");
        ArrayList<MinecraftLibrary> opt = new ArrayList<>();
        for (MinecraftLibrary l : version.libraries)
            if (l.allow() && !l.isNative()) {
                if (l.getName().toLowerCase(Locale.US).contains("optifine")) {
                    opt.add(l);
                    continue;
                }
                File f = service.version().getLibraryFile(version, l);
                if (f == null)
                    continue;
                library.append(f.getAbsolutePath()).append(File.pathSeparator);
            }
        for (MinecraftLibrary l : opt) {
            File f = service.version().getLibraryFile(version, l);
            if (f == null)
                continue;
            library.append(f.getAbsolutePath()).append(File.pathSeparator);
        }
        File f = version.getJar(service.baseDirectory());
        if (!f.exists())
            throw new GameException("Minecraft jar does not exists");
        library.append(f.getAbsolutePath()).append(File.pathSeparator);
        res.add("-cp");
        res.add(library.toString().substring(0, library.length() - File.pathSeparator.length()));
        res.add(version.mainClass);

        if (version.minecraftArguments == null)
            throw new GameException(new NullPointerException("Minecraft Arguments can not be null."));
        String[] splitted = StrUtils.tokenize(version.minecraftArguments);

        String game_assets = assetProvider.provide(version, !options.isNotCheckGame());

        for (String t : splitted) {
            t = t.replace("${auth_player_name}", lr.getUserName());
            t = t.replace("${auth_session}", lr.getSession());
            t = t.replace("${auth_uuid}", lr.getUserId());
            t = t.replace("${version_name}", options.getVersionName());
            t = t.replace("${profile_name}", options.getName());
            t = t.replace("${version_type}", options.getType());
            t = t.replace("${game_directory}", service.version().getRunDirectory(version.id).getAbsolutePath());
            t = t.replace("${game_assets}", game_assets);
            t = t.replace("${assets_root}", service.asset().getAssets(version.getAssetsIndex().getId()).getAbsolutePath());
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

    private final IAssetProvider DEFAULT_ASSET_PROVIDER = (t, allow) -> {
        return new File(service.baseDirectory(), "assets").getAbsolutePath();
    };

    private IAssetProvider assetProvider = DEFAULT_ASSET_PROVIDER;

    public void setAssetProvider(IAssetProvider assetProvider) {
        if (assetProvider == null)
            this.assetProvider = DEFAULT_ASSET_PROVIDER;
        else
            this.assetProvider = assetProvider;
    }
}
