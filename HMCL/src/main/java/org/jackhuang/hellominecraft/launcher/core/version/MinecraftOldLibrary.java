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
package org.jackhuang.hellominecraft.launcher.core.version;

import java.io.File;
import org.jackhuang.hellominecraft.launcher.core.download.DownloadType;

/**
 *
 * @author huangyuhui
 */
public class MinecraftOldLibrary extends MinecraftLibrary {

    public MinecraftOldLibrary(String name) {
        super(name);
    }

    @Override
    public boolean isRequiredToUnzip() {
        return false;
    }

    @Override
    public void init() {
    }

    @Override
    public boolean allow() {
        return true;
    }

    @Override
    public File getFilePath(File gameDir) {
        return new File(gameDir, "bin/" + name + ".jar");
    }

    @Override
    public Object clone() {
        return new MinecraftOldLibrary(name);
    }

    @Override
    public String getDownloadURL(String urlBase, DownloadType downloadType) {
        return null;
    }

}
