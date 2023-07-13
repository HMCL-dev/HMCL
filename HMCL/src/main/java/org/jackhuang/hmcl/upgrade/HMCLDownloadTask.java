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
import org.jackhuang.hmcl.util.Pack200Utils;
import org.jackhuang.hmcl.util.io.IOUtils;
import org.jackhuang.hmcl.util.io.JarUtils;
import org.jackhuang.hmcl.util.io.NetworkUtils;
import org.tukaani.xz.XZInputStream;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

class HMCLDownloadTask extends FileDownloadTask {
    private static final String GITHUB_TOKEN = "Bearer " + JarUtils.getManifestAttribute("GitHub-Api-Token", "");

    private RemoteVersion.Type archiveFormat;

    public HMCLDownloadTask(RemoteVersion version, Path target) {
        super(NetworkUtils.toURL(version.getUrl()), target.toFile(), version.getIntegrityCheck());
        if (version.getType() == RemoteVersion.Type.GitHub_ARTIFACT) {
            this.tweaker = httpURLConnection -> {
                httpURLConnection.setRequestProperty("Accept", "application/vnd.github+json");
                httpURLConnection.setRequestProperty("Authorization", GITHUB_TOKEN);
                httpURLConnection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
            };
        }
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

                case PACK_XZ:
                    byte[] raw = Files.readAllBytes(target);
                    try (InputStream in = new XZInputStream(new ByteArrayInputStream(raw));
                            JarOutputStream out = new JarOutputStream(Files.newOutputStream(target))) {
                        Pack200Utils.unpack(in, out);
                    }
                    break;

                case GitHub_ARTIFACT:
                    byte[] rawData = null;
                    try (JarFile jarFile = new JarFile(target.toFile())) {
                        Enumeration<JarEntry> enumeration = jarFile.entries();
                        while (enumeration.hasMoreElements()) {
                            JarEntry jarEntry = enumeration.nextElement();
                            if (jarEntry.getName().endsWith(".jar")) {
                                rawData = IOUtils.readFullyAsByteArray(jarFile.getInputStream(jarEntry));
                                break;
                            }
                        }
                    }
                    if (rawData == null) {
                        throw new IOException("Broken artifact from github.");
                    }
                    Files.write(target, rawData);
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
