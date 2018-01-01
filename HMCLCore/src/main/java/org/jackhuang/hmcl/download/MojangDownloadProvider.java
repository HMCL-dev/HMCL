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
package org.jackhuang.hmcl.download;

import org.jackhuang.hmcl.download.forge.ForgeVersionList;
import org.jackhuang.hmcl.download.game.GameVersionList;
import org.jackhuang.hmcl.download.liteloader.LiteLoaderVersionList;
import org.jackhuang.hmcl.download.optifine.OptiFineVersionList;

/**
 * @see {@link http://wiki.vg}
 * @author huangyuhui
 */
public final class MojangDownloadProvider implements DownloadProvider {

    public static final MojangDownloadProvider INSTANCE = new MojangDownloadProvider();

    private MojangDownloadProvider() {
    }

    @Override
    public String getLibraryBaseURL() {
        return "https://libraries.minecraft.net/";
    }

    @Override
    public String getVersionListURL() {
        return "https://launchermeta.mojang.com/mc/game/version_manifest.json";
    }

    @Override
    public String getVersionBaseURL() {
        return "http://s3.amazonaws.com/Minecraft.Download/versions/";
    }

    @Override
    public String getAssetIndexBaseURL() {
        return "http://s3.amazonaws.com/Minecraft.Download/indexes/";
    }

    @Override
    public String getAssetBaseURL() {
        return "http://resources.download.minecraft.net/";
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
                return OptiFineVersionList.INSTANCE;
            default:
                throw new IllegalArgumentException("Unrecognized version list id: " + id);
        }
    }

    @Override
    public String injectURL(String baseURL) {
        if (baseURL.contains("net/minecraftforge/forge"))
            return baseURL;
        else
            return baseURL.replace("http://files.minecraftforge.net/maven", "http://ftb.cursecdn.com/FTB2/maven");
    }
}
