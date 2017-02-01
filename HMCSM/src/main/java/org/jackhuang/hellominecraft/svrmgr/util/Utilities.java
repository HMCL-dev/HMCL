/*
 * Hello Minecraft! Server Manager.
 * Copyright (C) 2013  huangyuhui
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
package org.jackhuang.hellominecraft.svrmgr.util;

import java.io.File;
import org.jackhuang.hellominecraft.svrmgr.setting.SettingsManager;
import org.jackhuang.hellominecraft.util.sys.IOUtils;

/**
 *
 * @author huangyuhui
 */
public class Utilities {

    public static String getGameDir() {
        return IOUtils.addSeparator(new File(SettingsManager.settings.mainjar).getParent());
    }

    public static String getPath(String lastFolder) {
        File file = new File(try2GetPath(lastFolder));
        return file.exists() ? file.getPath() : null;
    }

    public static String try2GetPath(String lastFolder) {
        return getGameDir() + lastFolder;
    }

}
