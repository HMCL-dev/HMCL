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
package org.jackhuang.hellominecraft.launcher.utils.download;

import org.jackhuang.hellominecraft.launcher.utils.installers.InstallerVersionList;

/**
 *
 * @author huangyuhui
 */
public class MojangDownloadProvider implements IDownloadProvider {

    @Override
    public InstallerVersionList getForgeInstaller() {
        return org.jackhuang.hellominecraft.launcher.utils.installers.forge.vanilla.MinecraftForgeVersionList.getInstance();
    }

    @Override
    public InstallerVersionList getLiteLoaderInstaller() {
        return org.jackhuang.hellominecraft.launcher.utils.installers.liteloader.LiteLoaderVersionList.getInstance();
    }

    @Override
    public InstallerVersionList getOptiFineInstaller() {
        return org.jackhuang.hellominecraft.launcher.utils.installers.optifine.vanilla.OptiFineVersionList.getInstance();
    }

    @Override
    public InstallerVersionList getInstallerByType(String type) {
        if (type.equalsIgnoreCase("forge"))
            return getForgeInstaller();
        if (type.equalsIgnoreCase("liteloader"))
            return getLiteLoaderInstaller();
        if (type.equalsIgnoreCase("optifine"))
            return getOptiFineInstaller();
        return null;
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
        return "http://s3.amazonaws.com/Minecraft.Download/versions/versions.json";
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
    public String getParsedLibraryDownloadURL(String str) {
        return str;
    }

}
