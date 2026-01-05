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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

public final class Unzipper {
    private final Path zipFile, dest;
    private boolean replaceExistentFile = false;
    private boolean terminateIfSubDirectoryNotExists = false;
    private String subDirectory = "/";
    private EntryFilter filter;

    // We now automatically detect encoding, so is it still necessary to keep this option?
    private Charset encoding = StandardCharsets.UTF_8;

    /**
     * Decompress the given zip file to a directory.
     *
     * @param zipFile the input zip file to be uncompressed
     * @param destDir the dest directory to hold uncompressed files
     */
    public Unzipper(Path zipFile, Path destDir) {
        this.zipFile = zipFile;
        this.dest = destDir;
    }

    /**
     * True if replace the existent files in destination directory,
     * otherwise those conflict files will be ignored.
     */
    public Unzipper setReplaceExistentFile(boolean replaceExistentFile) {
        this.replaceExistentFile = replaceExistentFile;
        return this;
    }

    /**
     * Will be called for every entry in the zip file.
     * Callback returns false if you want leave the specific file uncompressed.
     */
    public Unzipper setFilter(EntryFilter filter) {
        this.filter = filter;
        return this;
    }

    /**
     * Will only uncompress files in the "subDirectory", their path will be also affected.
     * <p>
     * For example, if you set subDirectory to /META-INF, files in /META-INF/ will be
     * uncompressed to the destination directory without creating META-INF folder.
     * <p>
     * Default value: "/"
     */
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

    /**
     * Decompress the given zip file to a directory.
     *
     * @throws IOException if zip file is malformed or filesystem error.
     */
    public void unzip() throws IOException {
        Path destDir = this.dest.toAbsolutePath().normalize();
        Files.createDirectories(destDir);

        CopyOption[] copyOptions = replaceExistentFile
                ? new CopyOption[]{StandardCopyOption.REPLACE_EXISTING}
                : new CopyOption[]{};

        long countEntry = 0L;
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

                countEntry++;

                if (entry.isDirectory()) {
                    Files.createDirectories(destFile);
                } else {
                    Files.createDirectories(destFile.getParent());
                    try (InputStream input = reader.getInputStream(entry)) {
                        Files.copy(input, destFile, copyOptions);
                    } catch (FileAlreadyExistsException e) {
                        if (replaceExistentFile)
                            throw e;
                    }
                }
            }

            if (countEntry == 0 && !terminateIfSubDirectoryNotExists) {
                throw new IOException("Subdirectory " + subDirectory + " does not exist in the zip file.");
            }
        }
    }

    public interface FileFilter {
        boolean accept(Path zipEntry, boolean isDirectory, Path destFile, String entryPath) throws IOException;
    }

    public interface EntryFilter {
        boolean accept(ZipArchiveEntry zipArchiveEntry, Path destFile, String relativePath) throws IOException;
    }
}
