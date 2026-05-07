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
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jackhuang.hmcl.util.tree.ZipFileTree;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
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

    private static CharsetDecoder newCharsetDecoder(Charset charset) {
        return charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT);
    }

    public static boolean testEncoding(Path zipFile, Charset encoding) throws IOException {
        try (ZipArchiveReader zf = openZipFile(zipFile, encoding)) {
            return testEncoding(zf, encoding);
        }
    }

    public static boolean testEncoding(ZipArchiveReader zipFile, Charset encoding) {
        CharsetDecoder cd = newCharsetDecoder(encoding);
        CharBuffer cb = CharBuffer.allocate(32);

        for (ZipArchiveEntry entry : zipFile.getEntries()) {
            if (entry.getGeneralPurposeBit().usesUTF8ForNames()) continue;

            cd.reset();
            byte[] ba = entry.getRawName();
            int clen = (int) (ba.length * cd.maxCharsPerByte());
            if (clen == 0) continue;
            if (clen <= cb.capacity())
                cb.clear();
            else
                cb = CharBuffer.allocate(clen);

            ByteBuffer bb = ByteBuffer.wrap(ba, 0, ba.length);
            CoderResult cr = cd.decode(bb, cb, true);
            if (!cr.isUnderflow()) return false;
            cr = cd.flush(cb);
            if (!cr.isUnderflow()) return false;
        }
        return true;
    }

    public static Charset findSuitableEncoding(Path zipFile) throws IOException {
        try (ZipArchiveReader zf = openZipFile(zipFile, StandardCharsets.UTF_8)) {
            return findSuitableEncoding(zf);
        }
    }

    public static Charset findSuitableEncoding(ZipArchiveReader zipFile) throws IOException {
        if (testEncoding(zipFile, StandardCharsets.UTF_8)) return StandardCharsets.UTF_8;
        if (OperatingSystem.NATIVE_CHARSET != StandardCharsets.UTF_8 && testEncoding(zipFile, OperatingSystem.NATIVE_CHARSET))
            return OperatingSystem.NATIVE_CHARSET;

        String[] candidates = {
                "GB18030",
                "Big5",
                "Shift_JIS",
                "EUC-JP",
                "ISO-2022-JP",
                "EUC-KR",
                "ISO-2022-KR",
                "KOI8-R",
                "windows-1251",
                "x-MacCyrillic",
                "IBM855",
                "IBM866",
                "windows-1252",
                "ISO-8859-1",
                "ISO-8859-5",
                "ISO-8859-7",
                "ISO-8859-8",
                "UTF-16LE", "UTF-16BE",
                "UTF-32LE", "UTF-32BE"
        };

        for (String candidate : candidates) {
            try {
                Charset charset = Charset.forName(candidate);
                if (!charset.equals(OperatingSystem.NATIVE_CHARSET) && testEncoding(zipFile, charset)) {
                    return charset;
                }
            } catch (IllegalArgumentException ignored) {
            }
        }

        throw new IOException("Cannot find suitable encoding for the zip.");
    }

    public static ZipFileTree openZipTree(Path zipFile) throws IOException {
        return new ZipFileTree(openZipFile(zipFile));
    }

    public static ZipArchiveReader openZipFile(Path zipFile) throws IOException {
        return openZipFileWithPossibleEncoding(zipFile, StandardCharsets.UTF_8);
    }

    public static ZipArchiveReader openZipFile(Path zipFile, Charset charset) throws IOException {
        return new ZipArchiveReader(zipFile, charset);
    }

    public static ZipArchiveReader openZipFileWithPossibleEncoding(Path zipFile, Charset possibleEncoding) throws IOException {
        if (possibleEncoding == null)
            possibleEncoding = StandardCharsets.UTF_8;

        ZipArchiveReader zipReader = new ZipArchiveReader(Files.newByteChannel(zipFile));

        Charset suitableEncoding;
        try {
            if (possibleEncoding != StandardCharsets.UTF_8 && CompressingUtils.testEncoding(zipReader, possibleEncoding)) {
                suitableEncoding = possibleEncoding;
            } else {
                suitableEncoding = CompressingUtils.findSuitableEncoding(zipReader);
                if (suitableEncoding == StandardCharsets.UTF_8)
                    return zipReader;
            }
        } catch (Throwable e) {
            IOUtils.closeQuietly(zipReader, e);
            throw e;
        }

        zipReader.close();
        return new ZipArchiveReader(Files.newByteChannel(zipFile), suitableEncoding);
    }

    public static final class Builder {
        private boolean autoDetectEncoding = false;
        private Charset encoding = StandardCharsets.UTF_8;
        private boolean useTempFile = false;
        private final boolean create;
        private final Path zip;

        public Builder(Path zip, boolean create) {
            this.zip = zip;
            this.create = create;
        }

        public Builder setAutoDetectEncoding(boolean autoDetectEncoding) {
            this.autoDetectEncoding = autoDetectEncoding;
            return this;
        }

        public Builder setEncoding(Charset encoding) {
            this.encoding = encoding;
            return this;
        }

        public Builder setUseTempFile(boolean useTempFile) {
            this.useTempFile = useTempFile;
            return this;
        }

        public FileSystem build() throws IOException {
            if (autoDetectEncoding) {
                if (!testEncoding(zip, encoding)) {
                    encoding = findSuitableEncoding(zip);
                }
            }
            return createZipFileSystem(zip, create, useTempFile, encoding);
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

    /**
     * Read the text content of a file in zip.
     *
     * @param zipFile the zip file
     * @param name    the location of the text in zip file, something like A/B/C/D.txt
     * @return the plain text content of given file.
     * @throws IOException if the file is not a valid zip file.
     */
    public static String readTextZipEntry(Path zipFile, String name, Charset encoding) throws IOException {
        try (ZipArchiveReader s = openZipFile(zipFile, encoding)) {
            return IOUtils.readFullyAsString(s.getInputStream(s.getEntry(name)));
        }
    }

    /**
     * Read the text content of a file in zip.
     *
     * @param file the zip file
     * @param name the location of the text in zip file, something like A/B/C/D.txt
     * @return the plain text content of given file.
     */
    public static Optional<String> readTextZipEntryQuietly(Path file, String name, Charset encoding) {
        try {
            return Optional.of(readTextZipEntry(file, name, encoding));
        } catch (IOException | NullPointerException e) {
            return Optional.empty();
        }
    }
}
