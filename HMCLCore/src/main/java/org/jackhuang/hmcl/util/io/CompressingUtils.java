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
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.nio.file.*;
import java.nio.file.spi.FileSystemProvider;
import java.util.*;
import java.util.zip.ZipException;

/// Provides ZIP archive access and filename encoding detection.
///
/// @author huangyuhui
@NotNullByDefault
public final class CompressingUtils {

    /// ZIP filesystem provider, or `null` when the runtime does not include one.
    private static final @Nullable FileSystemProvider ZIPFS_PROVIDER = FileSystemProvider.installedProviders().stream()
            .filter(it -> "jar".equalsIgnoreCase(it.getScheme()))
            .findFirst()
            .orElse(null);

    /// Prevents instantiation of this utility class.
    private CompressingUtils() {
    }

    /// Guesses a charset for the archive's unmarked entry names, defaulting to UTF-8.
    ///
    /// Names marked as UTF-8 or supplied by a Unicode extra field are excluded.
    /// UTF-8 is preferred when it can decode every remaining name, including when
    /// there are no remaining names. Otherwise, statistical candidates are tried
    /// in descending score order, including low-scoring candidates when necessary.
    /// Candidates must strictly decode every unmarked name. If none qualifies,
    /// UTF-8 is returned even if some names cannot be decoded with it.
    /// Successful decoding does not guarantee that the original charset was identified.
    ///
    /// @param zipFile archive to inspect
    /// @return guessed filename charset, or UTF-8 when no candidate qualifies
    /// @throws IOException if the archive cannot be read or its reader cannot be closed
    static Charset findSuitableEncoding(Path zipFile) throws IOException {
        try (ZipArchiveReader zf = openZipFile(zipFile, StandardCharsets.UTF_8)) {
            return findSuitableEncoding(zf);
        }
    }

    /// Guesses a filename charset without closing the supplied reader,
    /// defaulting to UTF-8 when no candidate qualifies.
    ///
    /// Statistical sampling is bounded, but charset validation covers every
    /// unmarked entry and resets decoder state between filenames.
    static Charset findSuitableEncoding(ZipArchiveReader zipFile) {
        if (canDecodeNames(zipFile, StandardCharsets.UTF_8))
            return StandardCharsets.UTF_8;

        @Nullable ByteBuffer buffer = null;

        for (ZipArchiveEntry entry : zipFile.getEntries()) {
            if (entry.getNameSource() == ZipArchiveEntry.NameSource.NAME_WITH_EFS_FLAG
                    || entry.getNameSource() == ZipArchiveEntry.NameSource.UNICODE_EXTRA_FIELD)
                continue;

            byte @Nullable [] rawName = entry.getRawName();
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

        @Nullable Charset charset = findDecodableCandidate(zipFile, EncodingDetector.DEFAULT.detect(buffer));
        if (charset != null)
            return charset;

        // Short filenames can have a clear leading candidate below the default threshold.
        charset = findDecodableCandidate(zipFile,
                EncodingDetector.DEFAULT.withMinimumConfidence(0).detect(buffer));
        if (charset != null)
            return charset;

        return StandardCharsets.UTF_8;
    }

    /// Returns the highest-ranked supported charset that decodes every unmarked
    /// name, or `null` if none of the supplied candidates passes validation.
    private static @Nullable Charset findDecodableCandidate(ZipArchiveReader zipFile, EncodingDetector.Result result) {
        for (EncodingDetector.Candidate candidate : result.candidates()) {
            EncodingDetector.@Nullable Encoding encoding = candidate.encoding();
            if (encoding == null)
                continue;

            @Nullable Charset charset = encoding.approximateCharset();
            if (charset != null && canDecodeNames(zipFile, charset))
                return charset;
        }
        return null;
    }

    /// Tests each unmarked raw name independently, rejecting malformed or
    /// unmappable input instead of replacing it with replacement characters.
    private static boolean canDecodeNames(ZipArchiveReader zipFile, Charset charset) {
        CharsetDecoder decoder = charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        for (ZipArchiveEntry entry : zipFile.getEntries()) {
            if (entry.getNameSource() == ZipArchiveEntry.NameSource.NAME_WITH_EFS_FLAG
                    || entry.getNameSource() == ZipArchiveEntry.NameSource.UNICODE_EXTRA_FIELD)
                continue;

            byte @Nullable [] rawName = entry.getRawName();
            if (rawName == null)
                continue;

            try {
                decoder.decode(ByteBuffer.wrap(rawName));
            } catch (CharacterCodingException e) {
                return false;
            }
        }
        return true;
    }

    /// Opens an owning ZIP tree using detected filename encoding.
    ///
    /// @throws IOException if opening or reading the archive fails
    public static ZipFileTree openZipTree(Path zipFile) throws IOException {
        return new ZipFileTree(openZipFile(zipFile));
    }

    /// Opens a ZIP reader using detected filename encoding.
    ///
    /// The caller must close the returned reader. Readers opened during detection
    /// are closed if detection fails.
    ///
    /// @throws IOException if opening, reading, or closing an intermediate reader fails
    public static ZipArchiveReader openZipFile(Path zipFile) throws IOException {
        ZipArchiveReader zipReader = openZipFile(zipFile, StandardCharsets.UTF_8);

        Charset suitableEncoding;
        try {
            suitableEncoding = findSuitableEncoding(zipReader);
        } catch (Throwable e) {
            IOUtils.closeQuietly(zipReader, e);
            throw e;
        }
        if (suitableEncoding == StandardCharsets.UTF_8)
            return zipReader;

        zipReader.close();
        return openZipFile(zipFile, suitableEncoding);
    }

    /// Opens a reader using the supplied charset for names without Unicode metadata.
    private static ZipArchiveReader openZipFile(Path zipFile, Charset charset) throws IOException {
        return new ZipArchiveReader(zipFile, charset, true, true);
    }

    /// Configures a ZIP filesystem whose filename charset is detected when opened.
    public static final class Builder {
        /// Whether the provider should use temporary files for entry updates.
        private boolean useTempFile = false;
        /// Whether to pass the provider's archive-creation option.
        private final boolean create;
        /// Archive whose names are inspected before opening the filesystem.
        private final Path zip;

        /// Creates a builder with the supplied archive path and creation option.
        public Builder(Path zip, boolean create) {
            this.zip = zip;
            this.create = create;
        }

        /// Sets temporary-file usage for entry updates and returns this builder.
        public Builder setUseTempFile(boolean useTempFile) {
            this.useTempFile = useTempFile;
            return this;
        }

        /// Detects names in the existing archive and opens its filesystem.
        ///
        /// The caller must close the returned filesystem. Encoding detection requires
        /// an existing archive even when the creation option is enabled.
        ///
        /// @throws IOException if detection or opening the filesystem fails
        public FileSystem build() throws IOException {
            return createZipFileSystem(zip, create, useTempFile, findSuitableEncoding(zip));
        }
    }

    /// Creates a builder with archive creation disabled.
    public static Builder readonly(Path zipFile) {
        return new Builder(zipFile, false);
    }

    /// Creates a builder with archive creation and temporary-file updates enabled.
    public static Builder writable(Path zipFile) {
        return new Builder(zipFile, true).setUseTempFile(true);
    }

    /// Opens an existing ZIP filesystem using the provider's default filename charset.
    public static FileSystem createReadOnlyZipFileSystem(Path zipFile) throws IOException {
        return createReadOnlyZipFileSystem(zipFile, null);
    }

    /// Opens an existing ZIP filesystem with the supplied filename charset,
    /// or the provider's default when `charset` is `null`.
    public static FileSystem createReadOnlyZipFileSystem(Path zipFile, @Nullable Charset charset) throws IOException {
        return createZipFileSystem(zipFile, false, false, charset);
    }

    /// Opens or creates a ZIP filesystem with temporary-file updates and the
    /// provider's default filename charset.
    public static FileSystem createWritableZipFileSystem(Path zipFile) throws IOException {
        return createWritableZipFileSystem(zipFile, null);
    }

    /// Opens or creates a ZIP filesystem with temporary-file updates and the
    /// supplied filename charset, or the provider's default when `charset` is `null`.
    public static FileSystem createWritableZipFileSystem(Path zipFile, @Nullable Charset charset) throws IOException {
        return createZipFileSystem(zipFile, true, true, charset);
    }

    /// Opens a ZIP filesystem with the supplied provider options.
    ///
    /// The caller must close the returned filesystem. This method does not detect
    /// filename encoding or enforce read-only access.
    ///
    /// @param zipFile archive to open
    /// @param create whether a missing archive may be created
    /// @param useTempFile whether to use temporary files for entry updates
    /// @param encoding filename charset, or `null` for the provider's default
    /// @return newly opened filesystem
    /// @throws IOException if opening fails, including when the ZIP provider is unavailable
    public static FileSystem createZipFileSystem(Path zipFile, boolean create, boolean useTempFile, @Nullable Charset encoding) throws IOException {
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

    /// Reads a ZIP entry as UTF-8 text and closes the archive reader.
    ///
    /// @param zipFile archive to read using the reader's default filename charset
    /// @param name entry path, such as `A/B/C/D.txt`
    /// @return decoded entry contents, replacing malformed UTF-8 sequences
    /// @throws IOException if the archive or entry cannot be read
    /// @throws NullPointerException if the entry does not exist
    public static String readTextZipEntry(Path zipFile, String name) throws IOException {
        try (ZipArchiveReader s = new ZipArchiveReader(zipFile)) {
            return readTextZipEntry(s, name);
        }
    }

    /// Reads a ZIP entry as UTF-8 text, closing its stream but leaving the reader open.
    ///
    /// @param zipFile archive reader
    /// @param name entry path, such as `A/B/C/D.txt`
    /// @return decoded entry contents, replacing malformed UTF-8 sequences
    /// @throws IOException if the entry cannot be read
    /// @throws NullPointerException if the entry does not exist
    public static String readTextZipEntry(ZipArchiveReader zipFile, String name) throws IOException {
        return IOUtils.readFullyAsString(zipFile.getInputStream(zipFile.getEntry(name)));
    }

}
