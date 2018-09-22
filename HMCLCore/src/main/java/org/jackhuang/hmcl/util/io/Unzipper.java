/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2018  huangyuhui <huanghongxun2008@126.com>
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
 * along with this program.  If not, see {http://www.gnu.org/licenses/}.
 */
package org.jackhuang.hmcl.util.io;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

public class Unzipper {
    private final Path zipFile, dest;
    private boolean replaceExistentFile = false;
    private boolean terminateIfSubDirectoryNotExists = false;
    private String subDirectory = "/";
    private FileFilter filter = null;
    private String encoding;

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
     * Decompress the given zip file to a directory.
     *
     * @param zipFile the input zip file to be uncompressed
     * @param destDir the dest directory to hold uncompressed files
     */
    public Unzipper(File zipFile, File destDir) {
        this(zipFile.toPath(), destDir.toPath());
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
    public Unzipper setFilter(FileFilter filter) {
        this.filter = filter;
        return this;
    }

    /**
     * Will only uncompress files in the "subDirectory", their path will be also affected.
     *
     * For example, if you set subDirectory to /META-INF, files in /META-INF/ will be
     * uncompressed to the destination directory without creating META-INF folder.
     *
     * Default value: "/"
     */
    public Unzipper setSubDirectory(String subDirectory) {
        this.subDirectory = FileUtils.normalizePath(subDirectory);
        return this;
    }

    public Unzipper setEncoding(String encoding) {
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
        Files.createDirectories(dest);
        try (FileSystem fs = CompressingUtils.createReadOnlyZipFileSystem(zipFile, encoding)) {
            Path root = fs.getPath(subDirectory);
            if (!root.isAbsolute() || (subDirectory.length() > 1 && subDirectory.endsWith("/")))
                throw new IllegalArgumentException("Subdirectory for unzipper must be absolute");

            if (terminateIfSubDirectoryNotExists && Files.notExists(root))
                return;

            Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file,
                                                 BasicFileAttributes attrs) throws IOException {
                    String relativePath = root.relativize(file).toString();
                    Path destFile = dest.resolve(relativePath);
                    if (filter != null && !filter.accept(file, false, destFile, relativePath))
                        return FileVisitResult.CONTINUE;
                    if (replaceExistentFile || Files.notExists(destFile))
                        Files.copy(file, destFile, StandardCopyOption.REPLACE_EXISTING);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult preVisitDirectory(Path dir,
                                                         BasicFileAttributes attrs) throws IOException {
                    String relativePath = root.relativize(dir).toString();
                    Path dirToCreate = dest.resolve(relativePath);
                    if (filter != null && !filter.accept(dir, true, dirToCreate, relativePath))
                        return FileVisitResult.CONTINUE;
                    if (Files.notExists(dirToCreate)) {
                        Files.createDirectory(dirToCreate);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }

    public interface FileFilter {
        boolean accept(Path destPath, boolean isDirectory, Path zipEntry, String entryPath) throws IOException;
    }
}
