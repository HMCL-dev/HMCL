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

import org.jackhuang.hellominecraft.launcher.core.installers.InstallerType;
import org.jackhuang.hellominecraft.launcher.core.installers.InstallerVersionList;

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
        case Optifine:
            return getOptiFineInstaller();
        default:
            return null;
        }
    }

    public abstract InstallerVersionList getForgeInstaller();

    public abstract InstallerVersionList getLiteLoaderInstaller();

    public abstract InstallerVersionList getOptiFineInstaller();

    public abstract String getLibraryDownloadURL();

    public abstract String getVersionsDownloadURL();

    public abstract String getIndexesDownloadURL();

    public abstract String getVersionsListDownloadURL();

    public abstract String getAssetsDownloadURL();

    public abstract String getParsedLibraryDownloadURL(String str);

    public abstract boolean isAllowedToUseSelfURL();
}
