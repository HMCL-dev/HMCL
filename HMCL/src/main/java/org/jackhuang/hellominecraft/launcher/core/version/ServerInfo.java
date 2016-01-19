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
import java.util.HashMap;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import org.jackhuang.hellominecraft.tasks.download.FileDownloadTask;
import org.jackhuang.hellominecraft.utils.Utils;

/**
 *
 * @author huangyuhui
 */
public class ServerInfo {

    public String name, addr, picurl, type, info, wfjc, tsjs, version, url, depend;
    public String[] md5;
    public int port;

    public Icon icon;

    public static final HashMap<String, Icon> CACHE = new HashMap<>();

    public void downloadIcon() {
        if (icon == null && Utils.isURL(picurl))
            if (CACHE.containsKey(picurl))
                icon = CACHE.get(picurl);
            else
                try {
                    File tmp = File.createTempFile("HMCLSERVER", ".png");
                    FileDownloadTask.download(picurl, tmp, null);
                    CACHE.put(picurl, icon = new ImageIcon(tmp.getAbsolutePath()));
                } catch (Throwable e) {
                    throw new RuntimeException("Failed to download icon", e);
                }
    }
}
