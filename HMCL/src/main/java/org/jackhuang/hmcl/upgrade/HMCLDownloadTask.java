/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
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
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.jackhuang.hmcl.upgrade;

import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.util.io.NetworkUtils;

import java.nio.file.Path;

class HMCLDownloadTask extends FileDownloadTask {

    private RemoteVersion.Type archiveFormat;

    public HMCLDownloadTask(RemoteVersion version, Path target) {
        super(NetworkUtils.toURL(version.getUrl()), target.toFile(), version.getIntegrityCheck());
        archiveFormat = version.getType();
    }

    @Override
    public void execute() throws Exception {
        super.execute();

        try {
            Path target = getFile().toPath();

            switch (archiveFormat) {
                case JAR:
                    break;
                default:
                    throw new IllegalArgumentException("Unknown format: " + archiveFormat);
            }
        } catch (Throwable e) {
            getFile().delete();
            throw e;
        }
    }

}
