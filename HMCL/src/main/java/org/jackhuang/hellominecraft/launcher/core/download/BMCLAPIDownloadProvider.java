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

import org.jackhuang.hellominecraft.launcher.core.install.InstallerVersionList;

/**
 *
 * @author huangyuhui
 */
public class BMCLAPIDownloadProvider extends IDownloadProvider {

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
        return org.jackhuang.hellominecraft.launcher.core.install.optifine.bmcl.OptiFineBMCLVersionList.getInstance();
    }

    @Override
    public String getLibraryDownloadURL() {
        return "http://bmclapi2.bangbang93.com/libraries";
    }

    @Override
    public String getVersionsDownloadURL() {
        return "http://bmclapi2.bangbang93.com/versions/";
    }

    @Override
    public String getIndexesDownloadURL() {
        return "http://bmclapi2.bangbang93.com/indexes/";
    }

    @Override
    public String getVersionsListDownloadURL() {
        return "http://bmclapi2.bangbang93.com/versions/versions.json";
    }

    @Override
    public String getAssetsDownloadURL() {
        return "http://bmclapi2.bangbang93.com/assets/";
    }

    @Override
    public String getParsedLibraryDownloadURL(String str) {
        return str == null ? null : str.replace("http://files.minecraftforge.net/maven", "http://bmclapi2.bangbang93.com/maven");
    }

    @Override
    public boolean isAllowedToUseSelfURL() {
        return false;
    }

}
