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
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnmodifiableView;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.*;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// @author Glavo
public abstract class ArchiveFileTree<R, E extends ArchiveEntry> implements Closeable {

    public static ArchiveFileTree<?, ?> open(Path file) throws IOException {
        Path namePath = file.getFileName();
        if (namePath == null) {
            throw new IOException(file + " is not a valid archive file");
        }

        String name = namePath.toString();
        if (name.endsWith(".jar") || name.endsWith(".zip")) {
            return CompressingUtils.openZipTree(file);
        } else if (name.endsWith(".tar") || name.endsWith(".tar.gz") || name.endsWith(".tgz")) {
            return TarFileTree.open(file);
        } else {
            throw new IOException(file + " is not a valid archive file");
        }
    }

    protected final R reader;

    public ArchiveFileTree(R reader) {
        this.reader = reader;
    }

    public R getReader() {
        return reader;
    }

    public abstract Dir<E> getRoot();

    public @Nullable E getEntry(@NotNull String entryPath) {
        Dir<E> root = getRoot();
        Dir<E> dir = root;
        if (entryPath.indexOf('/') < 0) {
            return dir.getFiles().get(entryPath);
        } else {
            String[] path = entryPath.split("/");
            if (path.length == 0)
                return root.getEntry();

            for (int i = 0; i < path.length - 1; i++) {
                String item = path[i];
                if (item.isEmpty())
                    continue;
                dir = dir.getSubDirs().get(item);
                if (dir == null)
                    return null;
            }

            String fileName = path[path.length - 1];
            return dir.getFiles().get(fileName);
        }
    }

    public @Nullable Dir<E> getDirectory(@NotNull String dirPath) {
        Dir<E> dir = getRoot();
        if (dirPath.isEmpty()) {
            return dir;
        }
        String[] path = dirPath.split("/");
        for (String item : path) {
            if (item.isEmpty())
                continue;
            dir = dir.getSubDirs().get(item);
            if (dir == null)
                return null;
        }
        return dir;
    }

    /// Adds an entry, creating missing parent directories.
    /// Empty and `.` directory components are skipped. Entries with `..` directory
    /// components, duplicate files, and file/directory conflicts are skipped with a warning.
    /// Parent directories created before a skipped entry is detected remain in the tree.
    ///
    /// @param entry the archive entry to add
    protected static <E extends ArchiveEntry> void addEntry(Dir<E> root, @NotNull E entry) {
        String name = entry.getName();
        Dir<E> dir = root;

        int start = 0;
        while (start < name.length()) {
            int end = name.indexOf('/', start);
            boolean isLastPart = end < 0 || end == name.length() - 1;
            String item = end >= 0 ? name.substring(start, end) : name.substring(start);

            // A final component may be skipped, so the cursor must still reach the end.
            start = end < 0 ? name.length() : end + 1;

            if (isLastPart && !entry.isDirectory()) {
                if (dir.getSubDirs().containsKey(item)) {
                    LOG.warning("A file and a directory have the same name: " + name);
                    return;
                }

                if (dir.getFiles().containsKey(item)) {
                    LOG.warning("Duplicate entry: " + entry.getName());
                    return;
                }

                if (dir.files.isEmpty())
                    dir.files = new HashMap<>();
                dir.files.put(item, entry);
                break;
            }

            if (item.equals(".") || item.isEmpty())
                continue;
            if (item.equals("..")) {
                LOG.warning("Invalid entry name: " + name);
                return;
            }

            if (dir.getFiles().containsKey(item)) {
                LOG.warning("A file and a directory have the same name: " + name);
                return;
            }

            if (dir.subDirs.isEmpty())
                dir.subDirs = new HashMap<>();

            dir = dir.subDirs.computeIfAbsent(item, Dir::new);

            if (isLastPart) {
                if (dir.entry == null)
                    dir.entry = entry;
                else if (!dir.entry.isDirectory())
                    LOG.warning("A file and a directory have the same name: " + entry.getName());
                break;
            }
        }
    }

    public abstract InputStream getInputStream(E entry) throws IOException;

    public @NotNull InputStream getInputStream(String entryPath) throws IOException {
        E entry = getEntry(entryPath);
        if (entry == null)
            throw new FileNotFoundException("Entry not found: " + entryPath);
        return getInputStream(entry);
    }

    public BufferedReader getBufferedReader(@NotNull E entry) throws IOException {
        return new BufferedReader(new InputStreamReader(getInputStream(entry), StandardCharsets.UTF_8));
    }

    public @NotNull BufferedReader getBufferedReader(String entryPath) throws IOException {
        E entry = getEntry(entryPath);
        if (entry == null)
            throw new FileNotFoundException("Entry not found: " + entryPath);
        return getBufferedReader(entry);
    }

    public byte[] readBinaryEntry(@NotNull E entry) throws IOException {
        try (InputStream input = getInputStream(entry)) {
            return input.readAllBytes();
        }
    }

    public String readTextEntry(@NotNull String entryPath) throws IOException {
        E entry = getEntry(entryPath);
        if (entry == null)
            throw new FileNotFoundException("Entry not found: " + entryPath);
        return readTextEntry(entry);
    }

    public String readTextEntry(@NotNull E entry) throws IOException {
        return new String(readBinaryEntry(entry), StandardCharsets.UTF_8);
    }

    protected void copyAttributes(@NotNull E source, @NotNull Path targetFile) throws IOException {
        FileTime lastModifiedTime = source.getLastModifiedTime();
        if (lastModifiedTime != null)
            Files.setLastModifiedTime(targetFile, lastModifiedTime);
    }

    public void extractTo(@NotNull String entryPath, @NotNull Path targetFile) throws IOException {
        E entry = getEntry(entryPath);
        if (entry == null)
            throw new FileNotFoundException("Entry not found: " + entryPath);

        extractTo(entry, targetFile);
    }

    public void extractTo(@NotNull E entry, @NotNull Path targetFile) throws IOException {
        try (InputStream input = getInputStream(entry)) {
            Files.copy(input, targetFile, StandardCopyOption.REPLACE_EXISTING);
        }
        try {
            copyAttributes(entry, targetFile);
        } catch (Throwable e) {
            LOG.warning("Failed to copy attributes to " + targetFile, e);
        }
    }

    public abstract boolean isLink(E entry);

    public abstract String getLink(E entry) throws IOException;

    public abstract boolean isExecutable(E entry);

    @Override
    public abstract void close() throws IOException;

    public static final class Dir<E extends ArchiveEntry> {
        private final String name;
        private E entry;

        Map<String, Dir<E>> subDirs = Map.of();
        Map<String, E> files = Map.of();

        public Dir(String name) {
            this.name = name;
        }

        public boolean isRoot() {
            return name.isEmpty();
        }

        public @NotNull String getName() {
            return name;
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
