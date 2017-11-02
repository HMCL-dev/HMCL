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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jackhuang.hmcl.util.StrUtils;
import org.jackhuang.hmcl.api.HMCLog;
import org.jackhuang.hmcl.util.sys.OS;
import org.jackhuang.hmcl.core.GameException;
import org.jackhuang.hmcl.api.auth.UserProfileProvider;
import org.jackhuang.hmcl.core.version.MinecraftLibrary;
import org.jackhuang.hmcl.core.service.IMinecraftService;
import org.jackhuang.hmcl.core.version.Arguments;

/**
 *
 * @author huangyuhui
 */
public class MinecraftLoader extends AbstractMinecraftLoader {

    public MinecraftLoader(LaunchOptions p, IMinecraftService provider, UserProfileProvider lr) throws GameException {
        super(p, provider, p.getLaunchVersion(), lr);
        
        if (version.arguments == null)
            version.arguments = new Arguments();
        if (version.arguments.jvm == null)
            version.arguments.jvm = Arguments.DEFAULT_JVM_ARGUMENTS;
    }

    @Override
    protected void makeSelf(List<String> res) throws GameException {
        String game_assets = assetProvider.provide(version, !options.isNotCheckGame());
        Map<String, String> configuration = getConfigurations();
        
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
            throw new GameException("Minecraft jar does not exist");
        library.append(f.getAbsolutePath()).append(File.pathSeparator);
        String classpath = library.toString().substring(0, library.length() - File.pathSeparator.length());
        configuration.put("${classpath}", classpath);
        configuration.put("${natives_directory}", service.version().getDecompressNativesToLocation(version).getAbsolutePath());
        configuration.put("${game_assets}", game_assets);
        
        res.addAll(Arguments.parseArguments(version.arguments.jvm, configuration));
        res.add(version.mainClass);

        Map<String, Boolean> features = new HashMap<>();

        if (version.arguments.game != null)
            res.addAll(Arguments.parseArguments(version.arguments.game, configuration, features));
        res.addAll(Arguments.parseStringArguments(Arrays.asList(StrUtils.tokenize(version.minecraftArguments)), configuration));

        if (res.indexOf("--gameDir") != -1 && res.indexOf("--workDir") != -1) {
            res.add("--workDir");
            res.add(gameDir.getAbsolutePath());
        }
    }

    protected Map<String, String> getConfigurations() {
        HashMap<String, String> map = new HashMap();
        map.put("${auth_player_name}", lr.getUserName());
        map.put("${auth_session}", lr.getSession());
        map.put("${auth_uuid}", lr.getUserId());
        map.put("${version_name}", options.getVersionName());
        map.put("${profile_name}", options.getName());
        map.put("${version_type}", options.getType());
        map.put("${game_directory}", service.version().getRunDirectory(version.id).getAbsolutePath());
        map.put("${assets_root}", service.asset().getAssets(version.getAssetsIndex().getId()).getAbsolutePath());
        map.put("${auth_access_token}", lr.getAccessToken());
        map.put("${user_type}", lr.getUserType());
        map.put("${assets_index_name}", version.getAssetsIndex().getId());
        map.put("${user_properties}", lr.getUserProperties());
        map.put("${natives_directory}", service.version().getDecompressNativesToLocation(version).getAbsolutePath());
        return map;
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

        list.add("-Dminecraft.client.jar=" + version.getJar(service.baseDirectory()).getAbsolutePath());

        /*
        if (version.logging != null && version.logging.containsKey("client")) {
            LoggingInfo logging = version.logging.get("client");
            File file = service.asset().getLoggingObject(version.getAssetsIndex().getId(), logging);
            if (file.exists())
                list.add(logging.argument.replace("${path}", file.getAbsolutePath()));
        }
         */
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
