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
package org.jackhuang.hmcl.core.service;

import java.io.File;
import org.jackhuang.hmcl.core.GameException;
import org.jackhuang.hmcl.api.auth.UserProfileProvider;
import org.jackhuang.hmcl.core.download.DownloadType;
import org.jackhuang.hmcl.api.game.LaunchOptions;

/**
 *
 * @author huangyuhui
 */
public abstract class IMinecraftService {

    public abstract File baseDirectory();

    public DownloadType getDownloadType() {
        return DownloadType.getSuggestedDownloadType();
    }

    public abstract IMinecraftAssetService asset();

    public abstract IMinecraftDownloadService download();

    public abstract IMinecraftModService mod();

    public abstract IMinecraftProvider version();

    public abstract IMinecraftInstallerService install();

    /**
     * Provide the Minecraft Loader to generate the launching command.
     *
     * @see org.jackhuang.hmcl.core.service.IMinecraftLoader
     * @param p player informations, including username & auth_token
     *
     * @return what you want
     *
     * @throws GameException circular denpendency versions
     */
    public abstract IMinecraftLoader launch(LaunchOptions options, UserProfileProvider p) throws GameException;

}
