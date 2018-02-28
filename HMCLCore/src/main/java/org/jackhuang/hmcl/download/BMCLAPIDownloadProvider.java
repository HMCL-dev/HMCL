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
package org.jackhuang.hmcl.download;

import org.jackhuang.hmcl.download.forge.ForgeVersionList;
import org.jackhuang.hmcl.download.game.GameVersionList;
import org.jackhuang.hmcl.download.liteloader.LiteLoaderVersionList;
import org.jackhuang.hmcl.download.optifine.OptiFineBMCLVersionList;

/**
 *
 * @author huang
 */
public class BMCLAPIDownloadProvider implements DownloadProvider {

    public static final BMCLAPIDownloadProvider INSTANCE = new BMCLAPIDownloadProvider();

    private BMCLAPIDownloadProvider() {
    }

    @Override
    public String getLibraryBaseURL() {
        return "https://bmclapi2.bangbang93.com/libraries/";
    }

    @Override
    public String getVersionListURL() {
        return "https://bmclapi2.bangbang93.com/mc/game/version_manifest.json";
    }

    @Override
    public String getVersionBaseURL() {
        return "https://bmclapi2.bangbang93.com/versions/";
    }

    @Override
    public String getAssetIndexBaseURL() {
        return "https://bmclapi2.bangbang93.com/indexes/";
    }

    @Override
    public String getAssetBaseURL() {
        return "https://bmclapi2.bangbang93.com/assets/";
    }

    @Override
    public VersionList<?> getVersionListById(String id) {
        switch (id) {
            case "game":
                return GameVersionList.INSTANCE;
            case "forge":
                return ForgeVersionList.INSTANCE;
            case "liteloader":
                return LiteLoaderVersionList.INSTANCE;
            case "optifine":
                return OptiFineBMCLVersionList.INSTANCE;
            default:
                throw new IllegalArgumentException("Unrecognized version list id: " + id);
        }
    }

    @Override
    public String injectURL(String baseURL) {
        return baseURL
                .replace("https://launchermeta.mojang.com", "https://bmclapi2.bangbang93.com")
                .replace("https://launcher.mojang.com", "https://bmclapi2.bangbang93.com")
                .replace("https://libraries.minecraft.net", "https://bmclapi2.bangbang93.com/libraries")
                .replaceFirst("https?://files\\.minecraftforge\\.net/maven", "https://bmclapi2.bangbang93.com/maven")
                .replace("http://dl.liteloader.com/versions/versions.json", "https://bmclapi2.bangbang93.com/maven/com/mumfrey/liteloader/versions.json")
                .replace("http://dl.liteloader.com/versions", "https://bmclapi2.bangbang93.com/maven");
    }

}
