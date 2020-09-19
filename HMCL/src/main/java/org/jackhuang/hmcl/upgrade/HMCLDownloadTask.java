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
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.io.NetworkUtils;
import org.tukaani.xz.XZInputStream;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.jar.JarOutputStream;
import java.util.jar.Pack200;

class HMCLDownloadTask extends Task<Void> {

    private final FileDownloadTask task;
    private final RemoteVersion.Type archiveFormat;
    private final Path target;

    public HMCLDownloadTask(RemoteVersion version, Path target) {
        this.task = new FileDownloadTask(NetworkUtils.toURL(version.getUrl()), target.toFile(), version.getIntegrityCheck());
        this.archiveFormat = version.getType();
        this.target = target;
    }

    @Override
    public Collection<Task<?>> getDependents() {
        return Collections.singletonList(this);
    }

    @Override
    public void execute() throws Exception {
        try {
            switch (archiveFormat) {
                case JAR:
                    break;

                case PACK_XZ:
                    byte[] raw = Files.readAllBytes(target);
                    try (InputStream in = new XZInputStream(new ByteArrayInputStream(raw));
                            JarOutputStream out = new JarOutputStream(Files.newOutputStream(target))) {
                        Pack200.newUnpacker().unpack(in, out);
                    }
                    break;

                default:
                    throw new IllegalArgumentException("Unknown format: " + archiveFormat);
            }
        } catch (Throwable e) {
            Files.deleteIfExists(target);
            throw e;
        }
    }

}
