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
package org.jackhuang.hellominecraft.launcher.core.service;

import java.io.File;
import org.jackhuang.hellominecraft.launcher.core.Profile;
import org.jackhuang.hellominecraft.launcher.settings.Settings;
import org.jackhuang.hellominecraft.launcher.core.download.DownloadType;

/**
 *
 * @author huangyuhui
 */
public abstract class IMinecraftService {

    public final Profile profile;
    public final File baseFolder;

    public IMinecraftService(Profile profile) {
        this.profile = profile;
        this.baseFolder = profile.getCanonicalGameDirFile();
    }

    public DownloadType getDownloadType() {
        return DownloadType.getSuggestedDownloadType();
    }

    public abstract IMinecraftAssetService asset();

    public abstract IMinecraftDownloadService download();

    public abstract IMinecraftModService mod();

    public abstract IMinecraftProvider version();

    public abstract IMinecraftInstallerService install();

}
