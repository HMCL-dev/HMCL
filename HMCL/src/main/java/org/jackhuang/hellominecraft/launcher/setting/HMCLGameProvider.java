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
package org.jackhuang.hellominecraft.launcher.setting;

import java.io.File;
import org.jackhuang.hellominecraft.launcher.core.version.GameDirType;
import org.jackhuang.hellominecraft.launcher.core.version.MinecraftVersionManager;

/**
 *
 * @author huangyuhui
 */
public class HMCLGameProvider extends MinecraftVersionManager {

    public HMCLGameProvider(DefaultMinecraftService p) {
        super(p);
    }

    @Override
    public File getRunDirectory(String id) {
        return ((DefaultMinecraftService) service).getVersionSetting(id).getGameDirType() == GameDirType.VERSION_FOLDER
               ? service.version().versionRoot(id)
               : super.getRunDirectory(id);
    }
}
