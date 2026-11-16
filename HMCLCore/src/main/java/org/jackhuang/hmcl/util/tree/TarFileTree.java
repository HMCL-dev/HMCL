// SPDX-FileCopyrightText: Copyright (C) 2024 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util.tree;

import kala.compress.archivers.tar.TarArchiveEntry;
import kala.compress.archivers.tar.TarArchiveReader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributeView;
import java.util.zip.GZIPInputStream;

/**
 * @author Glavo
 */
public final class TarFileTree extends ArchiveFileTree<TarArchiveReader, TarArchiveEntry> {

    public static TarFileTree open(Path file) throws IOException {
        String fileName = file.getFileName().toString();

        if (fileName.endsWith(".tar.gz") || fileName.endsWith(".tgz")) {
            Path tempFile = Files.createTempFile("hmcl-", ".tar");
            TarArchiveReader tarFile;
            try (GZIPInputStream input = new GZIPInputStream(Files.newInputStream(file));
                 OutputStream output = Files.newOutputStream(tempFile, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE)
            ) {
                input.transferTo(output);
                tarFile = new TarArchiveReader(tempFile);
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
            return new TarFileTree(new TarArchiveReader(file), null);
        }
    }

    private final Path tempFile;
    private final Thread shutdownHook;
    private @Nullable Dir<TarArchiveEntry> root;

    public TarFileTree(TarArchiveReader file, Path tempFile) {
        super(file);
        this.tempFile = tempFile;

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
    public Dir<TarArchiveEntry> getRoot() {
        if (root == null) {
            root = new Dir<>("");
            for (TarArchiveEntry entry : reader.getEntries()) {
                addEntry(root, entry);
            }
        }

        return root;
    }

    @Override
    protected void copyAttributes(@NotNull TarArchiveEntry source, @NotNull Path targetFile) throws IOException {
        var fileAttributeView = Files.getFileAttributeView(targetFile, BasicFileAttributeView.class);
        if (fileAttributeView == null)
            return;

        fileAttributeView.setTimes(
                source.getLastModifiedTime(),
                source.getLastAccessTime(),
                source.getCreationTime()
        );
    }

    @Override
    public InputStream getInputStream(TarArchiveEntry entry) throws IOException {
        return reader.getInputStream(entry);
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
            reader.close();
        } finally {
            if (tempFile != null) {
                Runtime.getRuntime().removeShutdownHook(shutdownHook);
                Files.deleteIfExists(tempFile);
            }
        }
    }
}
