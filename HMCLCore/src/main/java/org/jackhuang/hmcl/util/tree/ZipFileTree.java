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

import kala.compress.archivers.zip.ZipArchiveEntry;
import kala.compress.archivers.zip.ZipArchiveReader;

import java.io.IOException;
import java.io.InputStream;

/**
 * @author Glavo
 */
public final class ZipFileTree extends ArchiveFileTree<ZipArchiveReader, ZipArchiveEntry> {
    public ZipFileTree(ZipArchiveReader file) throws IOException {
        super(file);
        try {
            for (ZipArchiveEntry zipArchiveEntry : file.getEntries()) {
                addEntry(zipArchiveEntry);
            }
        } catch (Throwable e) {
            try {
                file.close();
            } catch (Throwable e2) {
                e.addSuppressed(e2);
            }
            throw e;
        }
    }

    @Override
    public void close() throws IOException {
        file.close();
    }

    @Override
    public InputStream getInputStream(ZipArchiveEntry entry) throws IOException {
        return getFile().getInputStream(entry);
    }

    @Override
    public boolean isLink(ZipArchiveEntry entry) {
        return entry.isUnixSymlink();
    }

    @Override
    public String getLink(ZipArchiveEntry entry) throws IOException {
        return getFile().getUnixSymlink(entry);
    }

    @Override
    public boolean isExecutable(ZipArchiveEntry entry) {
        return !entry.isDirectory() && !entry.isUnixSymlink() && (entry.getUnixMode() & 0b1000000) != 0;
    }
}
