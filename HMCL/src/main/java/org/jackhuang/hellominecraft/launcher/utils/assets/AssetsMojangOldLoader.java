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
package org.jackhuang.hellominecraft.launcher.utils.assets;

import java.io.File;
import java.util.ArrayList;
import org.jackhuang.hellominecraft.HMCLog;
import org.jackhuang.hellominecraft.launcher.launch.IMinecraftProvider;
import org.jackhuang.hellominecraft.launcher.utils.download.IDownloadProvider;
import org.jackhuang.hellominecraft.launcher.version.MinecraftVersion;
import org.jackhuang.hellominecraft.utils.VersionNumber;
import org.jackhuang.hellominecraft.tasks.Task;
import org.jackhuang.hellominecraft.utils.functions.Consumer;

/**
 *
 * @author huangyuhui
 */
public class AssetsMojangOldLoader extends IAssetsHandler {

    private static final String URL = "http://bmclapi.bangbang93.com/resources/";

    public AssetsMojangOldLoader(String name) {
        super(name);
    }

    @Override
    public void getList(MinecraftVersion mv, IMinecraftProvider mp, final Consumer<String[]> dl) {
        AssetsLoader al = new AssetsLoader(URL);
        al.failedEvent.register((sender, e) -> {
            HMCLog.warn("Failed to get assets list.", e);
            dl.accept(null);
            return true;
        });
        al.successEvent.register((sender, t) -> {
            assetsDownloadURLs = new ArrayList<>();
            assetsLocalNames = new ArrayList<>();
            contents = t;
            for (Contents c : t) {
                assetsDownloadURLs.add(c.key);
                assetsLocalNames.add(new File(mp.getAssets(), c.key.replace("/", File.separator)));
            }
            dl.accept(assetsDownloadURLs.toArray(new String[1]));
            return true;
        });
        new Thread(al).start();
    }

    @Override
    public boolean isVersionAllowed(String formattedVersion) {
        VersionNumber r = VersionNumber.check(formattedVersion);
        if (r == null) return false;
        return VersionNumber.check("1.7.2").compareTo(r) >= 0
                && VersionNumber.check("1.6.0").compareTo(r) <= 0;
    }

    @Override
    public Task getDownloadTask(IDownloadProvider sourceType) {
        return new AssetsTask(URL);
    }
}
