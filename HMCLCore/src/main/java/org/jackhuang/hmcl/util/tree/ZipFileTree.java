// SPDX-FileCopyrightText: Copyright (C) 2024 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util.tree;

import kala.compress.archivers.zip.ZipArchiveEntry;
import kala.compress.archivers.zip.ZipArchiveReader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Set;

/**
 * @author Glavo
 */
public final class ZipFileTree extends ArchiveFileTree<ZipArchiveReader, ZipArchiveEntry> {
    private final boolean closeReader;
    private @Nullable Dir<ZipArchiveEntry> root;

    public ZipFileTree(ZipArchiveReader file) {
        this(file, true);
    }

    public ZipFileTree(ZipArchiveReader file, boolean closeReader) {
        super(file);
        this.closeReader = closeReader;
    }

    @Override
    public Dir<ZipArchiveEntry> getRoot() {
        if (root == null) {
            root = new Dir<>("");
            for (ZipArchiveEntry zipArchiveEntry : reader.getEntries()) {
                addEntry(root, zipArchiveEntry);
            }
        }
        return root;
    }

    @Override
    public void close() throws IOException {
        if (closeReader)
            reader.close();
    }

    @Override
    @SuppressWarnings("OctalInteger")
    protected void copyAttributes(@NotNull ZipArchiveEntry source, @NotNull Path targetFile) throws IOException {
        BasicFileAttributeView targetView = Files.getFileAttributeView(targetFile, PosixFileAttributeView.class);

        // target might not support posix even if source does
        if (targetView == null)
            targetView = Files.getFileAttributeView(targetFile, BasicFileAttributeView.class);

        if (targetView == null)
            return;

        targetView.setTimes(
                source.getLastModifiedTime(),
                source.getLastAccessTime(),
                source.getCreationTime()
        );

        int unixMode = source.getUnixMode();
        if (unixMode != 0 && targetView instanceof PosixFileAttributeView posixView) {
            Set<PosixFilePermission> permissions = EnumSet.noneOf(PosixFilePermission.class);

            // Owner permissions
            if ((unixMode & 0400) != 0) permissions.add(PosixFilePermission.OWNER_READ);
            if ((unixMode & 0200) != 0) permissions.add(PosixFilePermission.OWNER_WRITE);
            if ((unixMode & 0100) != 0) permissions.add(PosixFilePermission.OWNER_EXECUTE);

            // Group permissions
            if ((unixMode & 0040) != 0) permissions.add(PosixFilePermission.GROUP_READ);
            if ((unixMode & 0020) != 0) permissions.add(PosixFilePermission.GROUP_WRITE);
            if ((unixMode & 0010) != 0) permissions.add(PosixFilePermission.GROUP_EXECUTE);

            // Others permissions
            if ((unixMode & 0004) != 0) permissions.add(PosixFilePermission.OTHERS_READ);
            if ((unixMode & 0002) != 0) permissions.add(PosixFilePermission.OTHERS_WRITE);
            if ((unixMode & 0001) != 0) permissions.add(PosixFilePermission.OTHERS_EXECUTE);

            posixView.setPermissions(permissions);
        }
    }

    @Override
    public @Nullable ZipArchiveEntry getEntry(@NotNull String entryPath) {
        ZipArchiveEntry entry = reader.getEntry(entryPath);
        if (entry != null)
            return entry;

        return super.getEntry(entryPath);
    }

    @Override
    public InputStream getInputStream(ZipArchiveEntry entry) throws IOException {
        return getReader().getInputStream(entry);
    }

    @Override
    public boolean isLink(ZipArchiveEntry entry) {
        return entry.isUnixSymlink();
    }

    @Override
    public String getLink(ZipArchiveEntry entry) throws IOException {
        return getReader().getUnixSymlink(entry);
    }

    @Override
    public boolean isExecutable(ZipArchiveEntry entry) {
        return !entry.isDirectory() && !entry.isUnixSymlink() && (entry.getUnixMode() & 0b1000000) != 0;
    }
}
