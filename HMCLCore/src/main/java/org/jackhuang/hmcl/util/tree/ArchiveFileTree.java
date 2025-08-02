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

import kala.compress.archivers.ArchiveEntry;
import kala.compress.archivers.zip.ZipArchiveReader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnmodifiableView;

import java.io.Closeable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
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
            return new ZipFileTree(new ZipArchiveReader(file));
        } else if (name.endsWith(".tar") || name.endsWith(".tar.gz") || name.endsWith(".tgz")) {
            return TarFileTree.open(file);
        } else {
            throw new IOException(file + " is not a valid archive file");
        }
    }

    protected final F reader;
    protected final Dir<E> root = new Dir<>("", "");

    public ArchiveFileTree(F reader) {
        this.reader = reader;
    }

    public F getReader() {
        return reader;
    }

    public abstract @Nullable E getEntry(String name) throws IOException;

    public Dir<E> getRoot() {
        return root;
    }

    protected void addEntry(E entry) throws IOException {
        String[] path = entry.getName().split("/");
        List<String> pathList = Arrays.asList(path);

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

            final int nameEnd = i + 1;
            dir = dir.subDirs.computeIfAbsent(item, name ->
                    new Dir<>(name,
                            String.join("/", pathList.subList(0, nameEnd))));
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

    public String readTextEntry(String entryPath) throws IOException {
        return readTextEntry(entryPath, StandardCharsets.UTF_8);
    }

    public String readTextEntry(String entryPath, Charset encoding) throws IOException {
        E entry = getEntry(entryPath);
        if (entry == null)
            throw new FileNotFoundException("Entry not found: " + entryPath);
        return new String(getInputStream(entry).readAllBytes(), encoding);
    }

    public abstract boolean isLink(E entry);

    public abstract String getLink(E entry) throws IOException;

    public abstract boolean isExecutable(E entry);

    @Override
    public abstract void close() throws IOException;

    public static final class Dir<E extends ArchiveEntry> {
        private final String name;
        private final String fullName;
        private E entry;

        final Map<String, Dir<E>> subDirs = new HashMap<>();
        final Map<String, E> files = new HashMap<>();

        public Dir(String name, String fullName) {
            this.name = name;
            this.fullName = fullName;
        }

        public boolean isRoot() {
            return name.isEmpty();
        }

        public @NotNull String getName() {
            return name;
        }

        public @NotNull String getFullName() {
            return fullName;
        }

        public @Nullable E getEntry() {
            return entry;
        }

        public @NotNull @UnmodifiableView Map<String, Dir<E>> getSubDirs() {
            return subDirs;
        }

        public @NotNull @UnmodifiableView Map<String, E> getFiles() {
            return files;
        }
    }
}
