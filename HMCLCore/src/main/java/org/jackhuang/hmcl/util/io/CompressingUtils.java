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
import kala.encdet.EncodingDetector;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.tree.ZipFileTree;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.nio.file.*;
import java.nio.file.spi.FileSystemProvider;
import java.util.*;
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
            .orElse(null);

    private CompressingUtils() {
    }

    public static Charset findSuitableEncoding(Path zipFile) throws IOException {
        try (ZipArchiveReader zf = openZipFile(zipFile, StandardCharsets.UTF_8)) {
            return findSuitableEncoding(zf);
        }
    }

    private static Charset findSuitableEncoding(ZipArchiveReader zipFile) {
        @Nullable ByteBuffer buffer = null;

        for (ZipArchiveEntry entry : zipFile.getEntries()) {
            if (entry.getNameSource() == ZipArchiveEntry.NameSource.NAME_WITH_EFS_FLAG
                    || entry.getNameSource() == ZipArchiveEntry.NameSource.UNICODE_EXTRA_FIELD)
                continue;

            byte[] rawName = entry.getRawName();
            if (rawName == null || StringUtils.isASCII(rawName))
                continue;

            if (buffer == null) {
                int length = Math.max(512, rawName.length + 1);
                buffer = ByteBuffer.allocate(length);
            } else {
                long minCapacity = (long) buffer.position() + rawName.length + 1;
                if (minCapacity > EncodingDetector.DEFAULT_MAX_BYTES)
                    // Too many bytes, just skip other entries.
                    break;

                if (buffer.capacity() < minCapacity) {
                    int newCapacity = (int) Math.max(
                            Math.min(buffer.capacity() * 2L, Integer.MAX_VALUE - 8),
                            minCapacity
                    );

                    ByteBuffer newBuffer = ByteBuffer.allocate(newCapacity);
                    buffer.flip();
                    newBuffer.put(buffer);
                    buffer = newBuffer;
                }
            }

            buffer.put(rawName);
            buffer.put((byte) '\n');
        }

        if (buffer == null)
            return StandardCharsets.UTF_8;

        buffer.flip();

        EncodingDetector.Candidate candidate = EncodingDetector.DEFAULT.detect(buffer).bestCandidate();
        if (candidate == null || candidate.encoding() == null)
            return StandardCharsets.UTF_8;

        Charset approximateCharset = candidate.encoding().approximateCharset();
        return approximateCharset == null || approximateCharset == StandardCharsets.US_ASCII
                ? StandardCharsets.UTF_8
                : approximateCharset;
    }

    public static ZipFileTree openZipTree(Path zipFile) throws IOException {
        return new ZipFileTree(openZipFile(zipFile));
    }

    public static ZipArchiveReader openZipFile(Path zipFile) throws IOException {
        ZipArchiveReader zipReader = openZipFile(zipFile, StandardCharsets.UTF_8);

        Charset suitableEncoding = findSuitableEncoding(zipFile);
        if (suitableEncoding == StandardCharsets.UTF_8)
            return zipReader;

        zipReader.close();
        return openZipFile(zipFile, suitableEncoding);
    }

    private static ZipArchiveReader openZipFile(Path zipFile, Charset charset) throws IOException {
        return new ZipArchiveReader(zipFile, charset, true, true);
    }

    public static final class Builder {
        private boolean useTempFile = false;
        private final boolean create;
        private final Path zip;

        public Builder(Path zip, boolean create) {
            this.zip = zip;
            this.create = create;
        }

        public Builder setUseTempFile(boolean useTempFile) {
            this.useTempFile = useTempFile;
            return this;
        }

        public FileSystem build() throws IOException {
            return createZipFileSystem(zip, create, useTempFile, findSuitableEncoding(zip));
        }
    }

    public static Builder readonly(Path zipFile) {
        return new Builder(zipFile, false);
    }

    public static Builder writable(Path zipFile) {
        return new Builder(zipFile, true).setUseTempFile(true);
    }

    public static FileSystem createReadOnlyZipFileSystem(Path zipFile) throws IOException {
        return createReadOnlyZipFileSystem(zipFile, null);
    }

    public static FileSystem createReadOnlyZipFileSystem(Path zipFile, Charset charset) throws IOException {
        return createZipFileSystem(zipFile, false, false, charset);
    }

    public static FileSystem createWritableZipFileSystem(Path zipFile) throws IOException {
        return createWritableZipFileSystem(zipFile, null);
    }

    public static FileSystem createWritableZipFileSystem(Path zipFile, Charset charset) throws IOException {
        return createZipFileSystem(zipFile, true, true, charset);
    }

    public static FileSystem createZipFileSystem(Path zipFile, boolean create, boolean useTempFile, Charset encoding) throws IOException {
        Map<String, Object> env = new HashMap<>();
        if (create)
            env.put("create", "true");
        if (encoding != null)
            env.put("encoding", encoding.name());
        if (useTempFile)
            env.put("useTempFile", true);
        try {
            if (ZIPFS_PROVIDER == null)
                throw new FileSystemNotFoundException("Module jdk.zipfs does not exist");

            return ZIPFS_PROVIDER.newFileSystem(zipFile, env);
        } catch (UnsupportedOperationException ex) {
            throw new ZipException("Not a zip file");
        } catch (FileSystemNotFoundException ex) {
            throw Lang.apply(new ZipException("Java Environment is broken"), it -> it.initCause(ex));
        }
    }

    /**
     * Read the text content of a file in zip.
     *
     * @param zipFile the zip file
     * @param name    the location of the text in zip file, something like A/B/C/D.txt
     * @return the plain text content of given file.
     * @throws IOException if the file is not a valid zip file.
     */
    public static String readTextZipEntry(Path zipFile, String name) throws IOException {
        try (ZipArchiveReader s = new ZipArchiveReader(zipFile)) {
            return readTextZipEntry(s, name);
        }
    }

    /**
     * Read the text content of a file in zip.
     *
     * @param zipFile the zip file
     * @param name    the location of the text in zip file, something like A/B/C/D.txt
     * @return the plain text content of given file.
     * @throws IOException if the file is not a valid zip file.
     */
    public static String readTextZipEntry(ZipArchiveReader zipFile, String name) throws IOException {
        return IOUtils.readFullyAsString(zipFile.getInputStream(zipFile.getEntry(name)));
    }

}
