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
package org.jackhuang.hellominecraft.launcher.util;

import java.io.File;
import org.jackhuang.hellominecraft.launcher.core.version.GameDirType;
import org.jackhuang.hellominecraft.launcher.core.version.MinecraftVersionManager;
import org.jackhuang.hellominecraft.launcher.setting.VersionSetting;

/**
 *
 * @author huangyuhui
 */
public class HMCLGameProvider extends MinecraftVersionManager {

    public HMCLGameProvider(HMCLMinecraftService p) {
        super(p);
    }

    @Override
    public File getRunDirectory(String id) {
        VersionSetting vs = ((HMCLMinecraftService) service).getVersionSetting(id);
        if (vs == null)
            return super.getRunDirectory(id);
        else
            return ((HMCLMinecraftService) service).getVersionSetting(id).getGameDirType() == GameDirType.VERSION_FOLDER
                   ? service.version().versionRoot(id)
                   : super.getRunDirectory(id);
    }
}
