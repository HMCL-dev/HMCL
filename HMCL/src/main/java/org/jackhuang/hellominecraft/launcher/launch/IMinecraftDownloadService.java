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
package org.jackhuang.hellominecraft.launcher.launch;

import java.util.List;
import org.jackhuang.hellominecraft.launcher.utils.download.DownloadType;
import org.jackhuang.hellominecraft.launcher.version.MinecraftVersion;
import org.jackhuang.hellominecraft.version.MinecraftRemoteVersion;
import rx.Observable;

/**
 *
 * @author huangyuhui
 */
public abstract class IMinecraftDownloadService extends IMinecraftBasicService {

    public IMinecraftDownloadService(IMinecraftService profile) {
        super(profile);
    }

    public abstract MinecraftVersion downloadMinecraft(String id);

    public abstract boolean downloadMinecraftJar(String id);

    public abstract boolean downloadMinecraftVersionJson(String id);

    /**
     * Get the libraries that need to download.
     *
     * @return the library collection
     */
    public abstract List<GameLauncher.DownloadLibraryJob> getDownloadLibraries(MinecraftVersion mv) throws GameException;

    public abstract Observable<MinecraftRemoteVersion> getRemoteVersions();

}
