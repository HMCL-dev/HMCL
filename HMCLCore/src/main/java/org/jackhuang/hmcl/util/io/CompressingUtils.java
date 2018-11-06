/*
 * Hello Minecraft!.
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
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.spi.FileSystemProvider;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipError;
import java.util.zip.ZipException;

/**
 * Utilities of compressing
 *
 * @author huangyuhui
 */
public final class CompressingUtils {

    private static final FileSystemProvider ZIPFS_PROVIDER = FileSystemProvider.installedProviders().stream()
            .filter(it -> "jar".equalsIgnoreCase(it.getScheme()))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("Zipfs not supported"));

    private CompressingUtils() {
    }

    public static FileSystem createReadOnlyZipFileSystem(Path zipFile) throws IOException {
        return createReadOnlyZipFileSystem(zipFile, null);
    }

    public static FileSystem createReadOnlyZipFileSystem(Path zipFile, String encoding) throws IOException {
        return createZipFileSystem(zipFile, false, false, encoding);
    }

    public static FileSystem createWritableZipFileSystem(Path zipFile) throws IOException {
        return createWritableZipFileSystem(zipFile, null);
    }

    public static FileSystem createWritableZipFileSystem(Path zipFile, String encoding) throws IOException {
        return createZipFileSystem(zipFile, true, true, encoding);
    }

    public static FileSystem createZipFileSystem(Path zipFile, boolean create, boolean useTempFile, String encoding) throws IOException {
        Map<String, Object> env = new HashMap<>();
        if (create)
            env.put("create", "true");
        if (encoding != null)
            env.put("encoding", encoding);
        if (useTempFile)
            env.put("useTempFile", true);
        try {
            return ZIPFS_PROVIDER.newFileSystem(zipFile, env);
        } catch (ZipError error) {
            // Since Java 8 throws ZipError stupidly
            throw new ZipException(error.getMessage());
        } catch (UnsupportedOperationException ex) {
            throw new IOException("Not a zip file", ex);
        }
    }

    /**
     * Read the text content of a file in zip.
     *
     * @param zipFile the zip file
     * @param name the location of the text in zip file, something like A/B/C/D.txt
     * @throws IOException if the file is not a valid zip file.
     * @return the plain text content of given file.
     */
    public static String readTextZipEntry(File zipFile, String name) throws IOException {
        try (FileSystem fs = createReadOnlyZipFileSystem(zipFile.toPath())) {
            return FileUtils.readText(fs.getPath(name));
        }
    }

    /**
     * Read the text content of a file in zip.
     *
     * @param file the zip file
     * @param name the location of the text in zip file, something like A/B/C/D.txt
     * @return the plain text content of given file.
     */
    public static Optional<String> readTextZipEntryQuietly(File file, String name) {
        try {
            return Optional.of(readTextZipEntry(file, name));
        } catch (IOException e) {
            return Optional.empty();
        }
    }
}
