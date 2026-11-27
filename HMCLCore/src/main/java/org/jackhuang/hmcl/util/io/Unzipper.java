// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util.io;

import kala.compress.archivers.zip.ZipArchiveEntry;
import kala.compress.archivers.zip.ZipArchiveReader;
import org.jackhuang.hmcl.util.DigestUtils;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jackhuang.hmcl.util.platform.SystemInfo;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;

public final class Unzipper {
    private final Path zipFile, dest;
    private boolean replaceExistentFile = false;
    private boolean terminateIfSubDirectoryNotExists = false;
    private String subDirectory = "/";
    private EntryFilter filter;

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

    public Unzipper setTerminateIfSubDirectoryNotExists() {
        this.terminateIfSubDirectoryNotExists = true;
        return this;
    }

    /// Decompress the given zip file to a directory.
    ///
    /// @throws IOException if zip file is malformed or filesystem error.
    public void unzip() throws IOException {
        unzip(null, null);
    }

    /// Updates the supplied mutable map after each file or symbolic link is written. If extraction fails,
    /// completed entries retain their new digests. A null algorithm skips hashing and allows a null map.
    public void unzip(@Nullable String algorithm, @Nullable Map<String, String> hashes) throws IOException {
        if (algorithm != null)
            Objects.requireNonNull(hashes, "hashes");
        @Nullable MessageDigest digest = algorithm == null ? null : DigestUtils.getDigest(algorithm);
        Path destDir = this.dest.toAbsolutePath().normalize();
        Files.createDirectories(destDir);

        long entryCount = 0L;
        try (ZipArchiveReader reader = CompressingUtils.openZipFile(zipFile)) {
            String pathPrefix = StringUtils.addSuffix(subDirectory, "/");

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
                    if (digest != null)
                        digest.reset();
                    if (entry.isUnixSymlink()) {
                        String linkTarget = reader.getUnixSymlink(entry);
                        Path targetPath;
                        try {
                            targetPath = destFile.getFileSystem().getPath(linkTarget);
                        } catch (InvalidPathException e) {
                            throw new IOException("Zip entry has an invalid symlink target: " + entry.getName(), e);
                        }

                        if (!destFile.getParent().resolve(targetPath).toAbsolutePath().normalize().startsWith(destDir)) {
                            throw new IOException("Zip entry is trying to create a symlink outside of the destination directory: " + entry.getName());
                        }

                        if (digest != null) {
                            try (InputStream input = reader.getInputStream(entry)) {
                                DigestUtils.updateDigest(digest, input);
                            }
                        }
                        if (replaceExistentFile) {
                            FileUtils.replaceFile(destFile, temporary -> {
                                Files.delete(temporary);
                                Files.createSymbolicLink(temporary, targetPath);
                            });
                        } else {
                            try {
                                Files.createSymbolicLink(destFile, targetPath);
                            } catch (FileAlreadyExistsException ignored) {
                                continue;
                            }
                        }
                    } else if (replaceExistentFile) {
                        FileUtils.replaceFile(destFile, temporary -> extractFile(reader, entry, temporary, true, digest));
                    } else if (!extractFile(reader, entry, destFile, false, digest)) {
                        continue;
                    }
                    if (digest != null)
                        hashes.put(relativePath, HexFormat.of().formatHex(digest.digest()));
                }
            }

            if (entryCount == 0 && !"/".equals(subDirectory) && !terminateIfSubDirectoryNotExists) {
                throw new NoSuchFileException("Subdirectory " + subDirectory + " does not exist in the zip file.");
            }
        }
    }

    /// Applies the archived permissions and returns whether the file contents were written.
    private static boolean extractFile(ZipArchiveReader reader, ZipArchiveEntry entry, Path file, boolean replace,
                                       @Nullable MessageDigest digest) throws IOException {
        boolean extracted = true;
        InputStream contents = reader.getInputStream(entry);
        try (InputStream input = digest == null ? contents : new DigestInputStream(contents, digest)) {
            if (replace) {
                try (var output = Files.newOutputStream(file)) {
                    input.transferTo(output);
                }
            } else {
                try {
                    Files.copy(input, file);
                } catch (FileAlreadyExistsException ignored) {
                    extracted = false;
                }
            }
        }
        if ((entry.getUnixMode() & 0777) != 0 && SystemInfo.CURRENT_OS != OperatingSystem.WINDOWS)
            Files.setPosixFilePermissions(file, FileUtils.parsePosixFilePermission(entry.getUnixMode()));
        return extracted;
    }

    @FunctionalInterface
    public interface EntryFilter {
        boolean accept(ZipArchiveEntry zipArchiveEntry, Path destFile, String relativePath) throws IOException;
    }
}
