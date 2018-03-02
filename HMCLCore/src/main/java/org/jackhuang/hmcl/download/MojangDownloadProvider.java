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
import org.jackhuang.hmcl.download.optifine.OptiFineVersionList;

/**
 * @see <a href="http://wiki.vg">http://wiki,vg</a>
 * @author huangyuhui
 */
public class MojangDownloadProvider implements DownloadProvider {

    private final boolean isChina;

    public MojangDownloadProvider(boolean isChina) {
        this.isChina = isChina;
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
        if (baseURL == null)
            return null;
        //else if (baseURL.contains("scala-swing") || baseURL.contains("scala-xml") || baseURL.contains("scala-parser-combinators"))
        //    return baseURL.replace("http://files.minecraftforge.net/maven", "http://ftb.cursecdn.com/FTB2/maven");
        /*else if (baseURL.contains("typesafe") || baseURL.contains("scala"))
            if (isChina)
                return baseURL.replace("http://files.minecraftforge.net/maven", "http://maven.aliyun.com/nexus/content/groups/public");
            else
                return baseURL.replace("http://files.minecraftforge.net/maven", "http://repo1.maven.org/maven2");
        */else
            return baseURL;
    }
}
