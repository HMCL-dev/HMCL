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

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.jackhuang.hmcl.util.platform.OperatingSystem;

import java.io.File;
import java.io.IOException;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.*;
import java.nio.file.*;
import java.nio.file.spi.FileSystemProvider;
import java.util.*;
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

    private static CharsetDecoder newCharsetDecoder(Charset charset) {
        return charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT);
    }

    public static boolean testEncoding(Path zipFile, Charset encoding) throws IOException {
        try (ZipFile zf = openZipFile(zipFile, encoding)) {
            return testEncoding(zf, encoding);
        }
    }

    public static boolean testEncoding(ZipFile zipFile, Charset encoding) throws IOException {
        Enumeration<ZipArchiveEntry> entries = zipFile.getEntries();
        CharsetDecoder cd = newCharsetDecoder(encoding);
        CharBuffer cb = CharBuffer.allocate(32);

        while (entries.hasMoreElements()) {
            ZipArchiveEntry entry = entries.nextElement();

            if (entry.getGeneralPurposeBit().usesUTF8ForNames()) continue;

            cd.reset();
            byte[] ba = entry.getRawName();
            int clen = (int)(ba.length * cd.maxCharsPerByte());
            if (clen == 0) continue;
            if (clen <= cb.capacity())
                ((Buffer) cb).clear(); // cast to prevent "java.lang.NoSuchMethodError: java.nio.CharBuffer.clear()Ljava/nio/CharBuffer;" when compiling with Java 9+
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
        return findSuitableEncoding(zipFile, Charset.availableCharsets().values());
    }

    public static Charset findSuitableEncoding(Path zipFile, Collection<Charset> candidates) throws IOException {
        try (ZipFile zf = openZipFile(zipFile, StandardCharsets.UTF_8)) {
            return findSuitableEncoding(zf, candidates);
        }
    }

    public static Charset findSuitableEncoding(ZipFile zipFile) throws IOException {
        return findSuitableEncoding(zipFile, Charset.availableCharsets().values());
    }

    public static Charset findSuitableEncoding(ZipFile zipFile, Collection<Charset> candidates) throws IOException {
        if (testEncoding(zipFile, StandardCharsets.UTF_8)) return StandardCharsets.UTF_8;
        if (OperatingSystem.NATIVE_CHARSET != StandardCharsets.UTF_8 && testEncoding(zipFile, OperatingSystem.NATIVE_CHARSET))
            return OperatingSystem.NATIVE_CHARSET;

        for (Charset charset : candidates)
            if (charset != null && testEncoding(zipFile, charset))
                return charset;
        throw new IOException("Cannot find suitable encoding for the zip.");
    }

    public static ZipFile openZipFile(Path zipFile) throws IOException {
        return new ZipFile(Files.newByteChannel(zipFile));
    }

    public static ZipFile openZipFile(Path zipFile, Charset charset) throws IOException {
        return new ZipFile(Files.newByteChannel(zipFile), charset.name());
    }

    public static final class Builder {
        private boolean autoDetectEncoding = false;
        private Collection<Charset> charsetCandidates;
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

        public Builder setCharsetCandidates(Collection<Charset> charsetCandidates) {
            this.charsetCandidates = charsetCandidates;
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
                    if (charsetCandidates == null)
                        charsetCandidates = Charset.availableCharsets().values();
                    encoding = findSuitableEncoding(zip, charsetCandidates);
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
            return ZIPFS_PROVIDER.newFileSystem(zipFile, env);
        } catch (ZipError error) {
            // Since Java 8 throws ZipError stupidly
            throw new ZipException(error.getMessage());
        } catch (UnsupportedOperationException ex) {
            throw new ZipException("Not a zip file");
        } catch (FileSystemNotFoundException ex) {
            throw new ZipException("Java Environment is broken");
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
        try (ZipFile s = new ZipFile(zipFile)) {
            return readTextZipEntry(s, name);
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
    public static String readTextZipEntry(ZipFile zipFile, String name) throws IOException {
        return IOUtils.readFullyAsString(zipFile.getInputStream(zipFile.getEntry(name)), StandardCharsets.UTF_8);
    }

    /**
     * Read the text content of a file in zip.
     *
     * @param zipFile the zip file
     * @param name the location of the text in zip file, something like A/B/C/D.txt
     * @throws IOException if the file is not a valid zip file.
     * @return the plain text content of given file.
     */
    public static String readTextZipEntry(Path zipFile, String name, Charset encoding) throws IOException {
        try (ZipFile s = openZipFile(zipFile, encoding)) {
            return IOUtils.readFullyAsString(s.getInputStream(s.getEntry(name)), StandardCharsets.UTF_8);
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
        } catch (IOException | NullPointerException e) {
            return Optional.empty();
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
