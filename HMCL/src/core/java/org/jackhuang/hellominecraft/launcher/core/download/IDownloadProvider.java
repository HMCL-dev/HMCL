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

import org.jackhuang.hellominecraft.launcher.core.install.InstallerType;
import org.jackhuang.hellominecraft.launcher.core.install.InstallerVersionList;

/**
 *
 * @author huangyuhui
 */
public abstract class IDownloadProvider {

    public InstallerVersionList getInstallerByType(InstallerType type) {
        switch (type) {
        case Forge:
            return getForgeInstaller();
        case LiteLoader:
            return getLiteLoaderInstaller();
        case OptiFine:
            return getOptiFineInstaller();
        default:
            return null;
        }
    }
	
    public abstract InstallerVersionList getForgeInstaller();

    public abstract InstallerVersionList getLiteLoaderInstaller();

    public abstract InstallerVersionList getOptiFineInstaller();

    public abstract String getLibraryDownloadURL();
	
	public String getRetryLibraryDownloadURL() {
		return "";
	}

    public abstract String getVersionsDownloadURL();

    public abstract String getIndexesDownloadURL();

    public abstract String getVersionsListDownloadURL();

    public abstract String getAssetsDownloadURL();

	public String getRetryAssetsDownloadURL() {
		return "";
	}
	
    /**
     * For example, minecraft.json/assetIndex/url or
     * minecraft.json/downloads/client/url
     *
     * @param str baseURL
     *
     * @return parsedURL
     */
    public abstract String getParsedDownloadURL(String str);

    public abstract boolean isAllowedToUseSelfURL();
}
