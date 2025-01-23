/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2024 huangyuhui <huanghongxun2008@126.com> and contributors
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

package org.jackhuang.hmcl.util.tree;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarFile;
import org.jackhuang.hmcl.util.io.IOUtils;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.zip.GZIPInputStream;

/**
 * @author Glavo
 */
public final class TarFileTree extends ArchiveFileTree<TarFile, TarArchiveEntry> {

    public static TarFileTree open(Path file) throws IOException {
        String fileName = file.getFileName().toString();

        if (fileName.endsWith(".tar.gz") || fileName.endsWith(".tgz")) {
            Path tempFile = Files.createTempFile("hmcl-", ".tar");
            TarFile tarFile;
            try (GZIPInputStream input = new GZIPInputStream(Files.newInputStream(file));
                 OutputStream output = Files.newOutputStream(tempFile, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE)
            ) {
                IOUtils.copyTo(input, output);
                tarFile = new TarFile(tempFile.toFile());
            } catch (Throwable e) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (Throwable e2) {
                    e.addSuppressed(e2);
                }
                throw e;
            }

            return new TarFileTree(tarFile, tempFile);
        } else {
            return new TarFileTree(new TarFile(file), null);
        }
    }

    private final Path tempFile;
    private final Thread shutdownHook;

    public TarFileTree(TarFile file, Path tempFile) throws IOException {
        super(file);
        this.tempFile = tempFile;
        try {
            for (TarArchiveEntry entry : file.getEntries()) {
                addEntry(entry);
            }
        } catch (Throwable e) {
            try {
                file.close();
            } catch (Throwable e2) {
                e.addSuppressed(e2);
            }

            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (Throwable e2) {
                    e.addSuppressed(e2);
                }
            }

            throw e;
        }

        if (tempFile != null) {
            this.shutdownHook = new Thread(() -> {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (Throwable ignored) {
                }
            });
            Runtime.getRuntime().addShutdownHook(shutdownHook);
        } else
            this.shutdownHook = null;
    }

    @Override
    public InputStream getInputStream(TarArchiveEntry entry) throws IOException {
        return file.getInputStream(entry);
    }

    @Override
    public boolean isLink(TarArchiveEntry entry) {
        return entry.isSymbolicLink();
    }

    @Override
    public String getLink(TarArchiveEntry entry) throws IOException {
        return entry.getLinkName();
    }

    @Override
    public boolean isExecutable(TarArchiveEntry entry) {
        return entry.isFile() && (entry.getMode() & 0b1000000) != 0;
    }

    @Override
    public void close() throws IOException {
        try {
            file.close();
        } finally {
            if (tempFile != null) {
                Runtime.getRuntime().removeShutdownHook(shutdownHook);
                Files.deleteIfExists(tempFile);
            }
        }
    }
}
