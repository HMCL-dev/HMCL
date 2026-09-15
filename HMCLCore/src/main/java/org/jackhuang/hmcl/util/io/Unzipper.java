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
package org.jackhuang.hmcl.util.io;

import kala.compress.archivers.zip.ZipArchiveEntry;
import kala.compress.archivers.zip.ZipArchiveReader;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.platform.OperatingSystem;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

public final class Unzipper {

    private static final CopyOption[] REPLACE_OPTIONS = {StandardCopyOption.REPLACE_EXISTING};
    private static final CopyOption[] NO_OPTIONS = {};

    private final Path zipFile, dest;
    private boolean replaceExistentFile = false;
    private boolean terminateIfSubDirectoryNotExists = false;
    private String subDirectory = "/";
    private EntryFilter filter;
    private Charset encoding = StandardCharsets.UTF_8;

    /// Decompress the given zip file to a directory.
    ///
    /// @param zipFile the input zip file to be uncompressed
    /// @param destDir the dest directory to hold uncompressed files
    public Unzipper(Path zipFile, Path destDir) {
        this.zipFile = zipFile;
        this.dest = destDir;
    }

    /// True if replace the existent files in destination directory,
    /// otherwise those conflict files will be ignored.
    public Unzipper setReplaceExistentFile(boolean replaceExistentFile) {
        this.replaceExistentFile = replaceExistentFile;
        return this;
    }

    /// Will be called for every entry in the zip file.
    /// Callback returns false if you want leave the specific file uncompressed.
    public Unzipper setFilter(EntryFilter filter) {
        this.filter = filter;
        return this;
    }

    /// Will only uncompress files in the "subDirectory", their path will be also affected.
    ///
    /// For example, if you set subDirectory to /META-INF, files in /META-INF/ will be
    /// uncompressed to the destination directory without creating META-INF folder.
    ///
    /// Default value: "/"
    public Unzipper setSubDirectory(String subDirectory) {
        this.subDirectory = FileUtils.normalizePath(subDirectory);
        return this;
    }

    public Unzipper setEncoding(Charset encoding) {
        this.encoding = encoding;
        return this;
    }

    public Unzipper setTerminateIfSubDirectoryNotExists() {
        this.terminateIfSubDirectoryNotExists = true;
        return this;
    }

    /// Decompress the given zip file to a directory.
    ///
    /// The archive is planned in one pass and then written by several workers, see
    /// [CompressingUtils#forEachParallel(Path, Charset, int, int, String, CompressingUtils.ZipIndexWork)].
    ///
    /// @throws IOException if zip file is malformed or filesystem error.
    public void unzip() throws IOException {
        Path destDir = this.dest.toAbsolutePath().normalize();
        Files.createDirectories(destDir);

        // Resolved up front because every worker opens its own reader over the same archive.
        Charset charset = CompressingUtils.resolveZipEncoding(zipFile, encoding);

        List<PendingFile> pending = new ArrayList<>();
        long entryCount;
        try (ZipArchiveReader reader = CompressingUtils.openZipFile(zipFile, charset)) {
            entryCount = plan(reader, destDir, pending);
        }

        if (entryCount == 0 && !"/".equals(subDirectory) && !terminateIfSubDirectoryNotExists) {
            throw new NoSuchFileException("Subdirectory " + subDirectory + " does not exist in the zip file.");
        }

        int threads = CompressingUtils.readerThreads(pending.size());
        CompressingUtils.forEachParallel(zipFile, charset, threads, pending.size(), "Unzip-",
                (reader, index) -> extractEntry(reader, pending.get(index)));
    }

    /// Resolves what every accepted entry should become, without writing file contents yet.
    ///
    /// The `EntryFilter` runs here rather than on a worker so callers keep the existing contract: it
    /// is still called once per entry, in archive order, on one thread. `ModpackInstallTask` relies
    /// on that to accumulate the set of extracted paths in a plain non-thread-safe collection.
    ///
    /// Directories and symlinks are materialized immediately because they are rare and cheap; only
    /// regular files are deferred to the workers.
    ///
    /// @param reader  the archive, positioned at its central directory
    /// @param destDir the normalized destination root, used as the containment boundary
    /// @param pending collects the regular files left to write
    /// @return the number of accepted entries
    /// @throws IOException if an entry escapes the destination or a symlink cannot be created
    private long plan(ZipArchiveReader reader, Path destDir, List<PendingFile> pending) throws IOException {
        String pathPrefix = StringUtils.addSuffix(subDirectory, "/");
        long entryCount = 0L;

        for (ZipArchiveEntry entry : reader.getEntries()) {
            String normalizedPath = FileUtils.normalizePath(entry.getName());
            if (!normalizedPath.startsWith(pathPrefix)) {
                continue;
            }

            String relativePath = normalizedPath.substring(pathPrefix.length());
            Path destFile = destDir.resolve(relativePath).toAbsolutePath().normalize();
            if (!destFile.startsWith(destDir)) {
                throw new IOException("Zip entry is trying to write outside of the destination directory: " + entry.getName());
            }

            if (filter != null && !filter.accept(entry, destFile, relativePath)) {
                continue;
            }

            entryCount++;

            if (entry.isDirectory()) {
                Files.createDirectories(destFile);
            } else {
                Files.createDirectories(destFile.getParent());
                if (entry.isUnixSymlink()) {
                    createSymlink(reader, entry, destFile, destDir);
                } else {
                    pending.add(new PendingFile(entry, destFile));
                }
            }
        }

        return entryCount;
    }

    /// Writes one planned file, keeping the attribute handling the sequential implementation had.
    ///
    /// @param reader the calling worker's own reader
    /// @param file   the planned entry and its destination
    /// @throws IOException if the entry cannot be read or written
    private void extractEntry(ZipArchiveReader reader, PendingFile file) throws IOException {
        ZipArchiveEntry entry = file.entry();
        Path destFile = file.dest();

        try (InputStream input = reader.getInputStream(entry)) {
            Files.copy(input, destFile, replaceExistentFile ? REPLACE_OPTIONS : NO_OPTIONS);
        } catch (FileAlreadyExistsException e) {
            if (replaceExistentFile)
                throw e;
        }

        if (entry.getUnixMode() != 0 && OperatingSystem.CURRENT_OS != OperatingSystem.WINDOWS) {
            Files.setPosixFilePermissions(destFile, FileUtils.parsePosixFilePermission(entry.getUnixMode()));
        }
    }

    /// Recreates a symlink entry, refusing targets that escape the destination.
    ///
    /// @param reader   the archive being planned
    /// @param entry    the symlink entry
    /// @param destFile the link to create
    /// @param destDir  the normalized destination root
    /// @throws IOException if the target is unrepresentable or escapes the destination
    private void createSymlink(ZipArchiveReader reader, ZipArchiveEntry entry, Path destFile, Path destDir) throws IOException {
        String linkTarget = reader.getUnixSymlink(entry);
        if (replaceExistentFile)
            Files.deleteIfExists(destFile);

        Path targetPath;
        try {
            targetPath = Path.of(linkTarget);
        } catch (InvalidPathException e) {
            throw new IOException("Zip entry has an invalid symlink target: " + entry.getName(), e);
        }

        if (!destFile.getParent().resolve(targetPath).toAbsolutePath().normalize().startsWith(destDir)) {
            throw new IOException("Zip entry is trying to create a symlink outside of the destination directory: " + entry.getName());
        }

        try {
            Files.createSymbolicLink(destFile, targetPath);
        } catch (FileAlreadyExistsException ignored) {
        }
    }

    /// A regular file accepted by the filter whose contents still have to be written.
    ///
    /// @param entry the archive entry holding the compressed bytes
    /// @param dest  the absolute normalized path to write
    private record PendingFile(ZipArchiveEntry entry, Path dest) {
    }

    @FunctionalInterface
    public interface EntryFilter {
        boolean accept(ZipArchiveEntry zipArchiveEntry, Path destFile, String relativePath) throws IOException;
    }
}
