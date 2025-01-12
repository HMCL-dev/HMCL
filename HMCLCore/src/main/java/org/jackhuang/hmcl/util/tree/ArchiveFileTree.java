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

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.*;

/**
 * @author Glavo
 */
public abstract class ArchiveFileTree<F, E extends ArchiveEntry> implements Closeable {

    public static ArchiveFileTree<?, ?> open(Path file) throws IOException {
        Path namePath = file.getFileName();
        if (namePath == null) {
            throw new IOException(file + " is not a valid archive file");
        }

        String name = namePath.toString();
        if (name.endsWith(".jar") || name.endsWith(".zip")) {
            return new ZipFileTree(new ZipFile(file));
        } else if (name.endsWith(".tar") || name.endsWith(".tar.gz") || name.endsWith(".tgz")) {
            return TarFileTree.open(file);
        } else {
            throw new IOException(file + " is not a valid archive file");
        }
    }

    protected final F file;
    protected final Dir<E> root = new Dir<>();

    public ArchiveFileTree(F file) {
        this.file = file;
    }

    public F getFile() {
        return file;
    }

    public Dir<E> getRoot() {
        return root;
    }

    public void addEntry(E entry) throws IOException {
        String[] path = entry.getName().split("/");

        Dir<E> dir = root;

        for (int i = 0, end = entry.isDirectory() ? path.length : path.length - 1; i < end; i++) {
            String item = path[i];
            if (item.equals("."))
                continue;
            if (item.equals("..") || item.isEmpty())
                throw new IOException("Invalid entry: " + entry.getName());

            if (dir.files.containsKey(item)) {
                throw new IOException("A file and a directory have the same name: " + entry.getName());
            }

            dir = dir.subDirs.computeIfAbsent(item, name -> new Dir<>());
        }

        if (entry.isDirectory()) {
            if (dir.entry != null) {
                throw new IOException("Duplicate entry: " + entry.getName());
            }
            dir.entry = entry;
        } else {
            String fileName = path[path.length - 1];

            if (dir.subDirs.containsKey(fileName)) {
                throw new IOException("A file and a directory have the same name: " + entry.getName());
            }

            if (dir.files.containsKey(fileName)) {
                throw new IOException("Duplicate entry: " + entry.getName());
            }

            dir.files.put(fileName, entry);
        }
    }

    public abstract InputStream getInputStream(E entry) throws IOException;

    public abstract boolean isLink(E entry);

    public abstract String getLink(E entry) throws IOException;

    public abstract boolean isExecutable(E entry);

    @Override
    public abstract void close() throws IOException;

    public static final class Dir<E extends ArchiveEntry> {
        E entry;

        final Map<String, Dir<E>> subDirs = new HashMap<>();
        final Map<String, E> files = new HashMap<>();

        public Map<String, Dir<E>> getSubDirs() {
            return subDirs;
        }

        public Map<String, E> getFiles() {
            return files;
        }
    }
}
