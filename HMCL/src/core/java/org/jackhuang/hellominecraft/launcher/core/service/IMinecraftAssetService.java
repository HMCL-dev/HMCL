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
import java.io.IOException;
import org.jackhuang.hellominecraft.launcher.core.GameException;
import org.jackhuang.hellominecraft.launcher.core.version.AssetIndexDownloadInfo;
import org.jackhuang.hellominecraft.util.tasks.Task;

/**
 *
 * @author huangyuhui
 */
public abstract class IMinecraftAssetService extends IMinecraftBasicService {

    public IMinecraftAssetService(IMinecraftService service) {
        super(service);
    }

    public abstract Task downloadAssets(String mcVersion) throws GameException;

    public abstract File getAssets();

    /**
     * Redownload the Asset index json of the given version.
     *
     * @param a the given version name
     *
     * @return Is the action successful?
     */
    public abstract boolean refreshAssetsIndex(String a) throws GameException;

    public abstract boolean downloadMinecraftAssetsIndexAsync(AssetIndexDownloadInfo assetsId);

    public abstract Task downloadMinecraftAssetsIndex(AssetIndexDownloadInfo assetsId);

    public abstract File getAssetObject(String assetVersion, String name) throws IOException;
}
