/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2013  huangyuhui <huanghongxun2008@126.com>
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
package org.jackhuang.hellominecraft.launcher.core.download;

import java.util.Locale;
import org.jackhuang.hellominecraft.launcher.core.install.InstallerVersionList;
import org.jackhuang.hellominecraft.util.lang.SupportedLocales;

/**
 *
 * @author huangyuhui
 */
public class MojangDownloadProvider extends IDownloadProvider {

    @Override
    public InstallerVersionList getForgeInstaller() {
        return org.jackhuang.hellominecraft.launcher.core.install.forge.MinecraftForgeVersionList.getInstance();
    }

    @Override
    public InstallerVersionList getLiteLoaderInstaller() {
        return org.jackhuang.hellominecraft.launcher.core.install.liteloader.LiteLoaderVersionList.getInstance();
    }

    @Override
    public InstallerVersionList getOptiFineInstaller() {
        return org.jackhuang.hellominecraft.launcher.core.install.optifine.vanilla.OptiFineVersionList.getInstance();
    }

    @Override
    public String getLibraryDownloadURL() {
        return "https://libraries.minecraft.net";
    }

    @Override
    public String getVersionsDownloadURL() {
        return "http://s3.amazonaws.com/Minecraft.Download/versions/";
    }

    @Override
    public String getIndexesDownloadURL() {
        return "http://s3.amazonaws.com/Minecraft.Download/indexes/";
    }

    @Override
    public String getVersionsListDownloadURL() {
        return "https://launchermeta.mojang.com/mc/game/version_manifest.json";
    }

    @Override
    public String getAssetsDownloadURL() {
        return "http://resources.download.minecraft.net/";
    }

    @Override
    public boolean isAllowedToUseSelfURL() {
        return true;
    }

    @Override
    public String getParsedDownloadURL(String str) {
        if (str == null)
            return null;
        else if (str.contains("scala-swing") || str.contains("scala-xml") || str.contains("scala-parser-combinators"))
            return str.replace("http://files.minecraftforge.net/maven", "http://ftb.cursecdn.com/FTB2/maven/");
        else if (str.contains("typesafe") || str.contains("scala"))
            if (SupportedLocales.NOW_LOCALE.self == Locale.CHINA)
                return str.replace("http://files.minecraftforge.net/maven", "http://maven.oschina.net/content/groups/public");
            else
                return str.replace("http://files.minecraftforge.net/maven", "http://repo1.maven.org/maven2");
        else
            return str;
    }
}
