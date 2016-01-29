/*
 * Hello Minecraft! Launcher.
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
package org.jackhuang.hellominecraft.launcher.core.launch;

import java.io.File;
import java.net.URL;
import org.jackhuang.hellominecraft.utils.C;
import org.jackhuang.hellominecraft.launcher.core.download.DownloadLibraryJob;
import org.jackhuang.hellominecraft.utils.tasks.download.FileDownloadTask;

/**
 *
 * @author huangyuhui
 */
public class LibraryDownloadTask extends FileDownloadTask {

    DownloadLibraryJob job;

    public LibraryDownloadTask(DownloadLibraryJob job) {
        super();
        this.job = job;
    }

    @Override
    public void executeTask() throws Throwable {
        if (job.name.startsWith("net.minecraftforge:forge:")) {
            String[] s = job.name.split(":");
            if (s.length == 3)
                job.url = "http://files.minecraftforge.net/maven/net/minecraftforge/forge/" + s[2] + "/forge-" + s[2] + "-universal.jar";
        }
        if (job.name.startsWith("com.mumfrey:liteloader:")) {
            String[] s = job.name.split(":");
            if (s.length == 3 && s[2].length() > 3)
                job.url = "http://dl.liteloader.com/versions/com/mumfrey/liteloader/" + s[2].substring(0, s[2].length() - 3) + "/liteloader-" + s[2] + ".jar";
        }
        download(new URL(job.url), job.path);
    }

    void download(URL url, File filePath) throws Throwable {
        this.url = url;
        this.filePath = filePath;
        super.executeTask();
    }

    @Override
    public String getInfo() {
        return C.i18n("download") + ": " + job.name;
    }

}
