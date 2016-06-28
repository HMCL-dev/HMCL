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
package org.jackhuang.hellominecraft.launcher.core.download;

import java.io.File;
import org.jackhuang.hellominecraft.launcher.core.version.IMinecraftLibrary;
import org.jackhuang.hellominecraft.util.system.IOUtils;

/**
 *
 * @author huangyuhui
 */
public class DownloadLibraryJob {

    public IMinecraftLibrary lib;
    public String url;
	public String retryUrl;
    public File path;

    public DownloadLibraryJob(IMinecraftLibrary n, String u, String retry, File p) {
        url = u;
		retryUrl = retry;
        lib = n;
        path = IOUtils.tryGetCanonicalFile(p);
    }
}
