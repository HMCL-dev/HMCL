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
package org.jackhuang.hmcl.core.launch;

import org.jackhuang.hmcl.util.C;
import org.jackhuang.hmcl.api.event.launch.DownloadLibraryJob;
import org.jackhuang.hmcl.util.net.FileDownloadTask;

/**
 *
 * @author huangyuhui
 */
public class LibraryDownloadTask extends FileDownloadTask {

    DownloadLibraryJob job;

    public LibraryDownloadTask(DownloadLibraryJob job) {
        super(job.parse().url, job.path);
        this.job = job;
    }

    @Override
    public String getInfo() {
        return C.i18n("download") + ": " + job.lib.getName();
    }

}
