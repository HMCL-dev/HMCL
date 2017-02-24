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
package org.jackhuang.hmcl.core.download;

import java.io.File;
import org.jackhuang.hmcl.api.game.IMinecraftLibrary;

/**
 *
 * @author huangyuhui
 */
public class DownloadLibraryJob {

    public IMinecraftLibrary lib;
    public String url;
    public File path;

    public DownloadLibraryJob(IMinecraftLibrary n, String u, File p) {
        url = u;
        lib = n;
        path = p;
    }

    public DownloadLibraryJob parse(DownloadType type) {
        String name = lib.getName();
        if (name.startsWith("net.minecraftforge:forge:") && url == null) {
            String[] s = name.split(":");
            if (s.length == 3)
                url = type.getProvider().getParsedDownloadURL("http://files.minecraftforge.net/maven/net/minecraftforge/forge/" + s[2] + "/forge-" + s[2] + "-universal.jar");
        }
        if (name.startsWith("com.mumfrey:liteloader:") && url == null) {
            String[] s = name.split(":");
            if (s.length == 3 && s[2].length() > 3)
                url = type.getProvider().getParsedDownloadURL("http://dl.liteloader.com/versions/com/mumfrey/liteloader/" + s[2].substring(0, s[2].length() - 3) + "/liteloader-" + s[2] + ".jar");
        }
        return this;
    }
}
