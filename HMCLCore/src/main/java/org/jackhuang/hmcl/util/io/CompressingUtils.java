// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util.io;

import kala.compress.archivers.zip.ZipArchiveEntry;
import kala.compress.archivers.zip.ZipArchiveReader;
import kala.encdet.EncodingDetector;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.tree.ZipFileTree;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.*;
import java.nio.file.*;
import java.nio.file.spi.FileSystemProvider;
import java.util.*;
import java.util.zip.CRC32;
import java.util.zip.ZipException;

/// Provides ZIP archive access and filename encoding detection.
///
/// @author huangyuhui
@NotNullByDefault
public final class CompressingUtils {

    /// Maximum combined size of complete sampled names and their separators.
    private static final int ENCODING_SAMPLE_BYTES = 64 * 1024;

    /// Maximum number of evenly spaced entries considered for statistical sampling.
    private static final int ENCODING_SAMPLE_NAMES = 256;

    /// Detector restricted to legacy filename encodings with ASCII-compatible syntax.
    private static final EncodingDetector FILENAME_ENCODING_DETECTOR = EncodingDetector.DEFAULT
            .withMaxBytes(ENCODING_SAMPLE_BYTES)
            .withMinimumConfidence(0.0)
            .withEncodings(
                    EncodingDetector.Encoding.GB18030, EncodingDetector.Encoding.BIG5_HKSCS,
                    EncodingDetector.Encoding.CP932, EncodingDetector.Encoding.EUC_JIS_2004,
                    EncodingDetector.Encoding.CP949, EncodingDetector.Encoding.EUC_KR,
                    EncodingDetector.Encoding.CP1250, EncodingDetector.Encoding.CP1251,
                    EncodingDetector.Encoding.CP1252, EncodingDetector.Encoding.CP1253,
                    EncodingDetector.Encoding.CP1254, EncodingDetector.Encoding.CP1255,
                    EncodingDetector.Encoding.CP1256, EncodingDetector.Encoding.CP1257,
                    EncodingDetector.Encoding.CP1258, EncodingDetector.Encoding.KOI8_R,
                    EncodingDetector.Encoding.KOI8_U, EncodingDetector.Encoding.MAC_CYRILLIC,
                    EncodingDetector.Encoding.CP437, EncodingDetector.Encoding.CP850,
                    EncodingDetector.Encoding.CP855, EncodingDetector.Encoding.CP866,
                    EncodingDetector.Encoding.ISO_8859_1, EncodingDetector.Encoding.ISO_8859_5,
                    EncodingDetector.Encoding.ISO_8859_7, EncodingDetector.Encoding.ISO_8859_8);

    /// Deterministic compatibility choices used only after statistical candidates fail.
    private static final @Unmodifiable List<String> FALLBACK_ENCODINGS = List.of(
            "GB18030", "Big5", "Shift_JIS", "EUC-JP", "EUC-KR", "IBM437", "ISO-8859-1");

    /// ZIP filesystem provider, or `null` when the runtime does not include one.
    private static final @Nullable FileSystemProvider ZIPFS_PROVIDER = FileSystemProvider.installedProviders().stream()
            .filter(it -> "jar".equalsIgnoreCase(it.getScheme()))
            .findFirst()
            .orElse(null);

    /// Prevents instantiation of this utility class.
    private CompressingUtils() {
    }

    /// Guesses a filename charset using [#findSuitableEncoding(ZipArchiveReader)].
    ///
    /// Names supplied by Unicode metadata are excluded even if the ZIP filesystem
    /// provider does not support that metadata.
    ///
    /// @param zipFile archive to inspect
    /// @return guessed filename charset
    /// @throws IOException if the archive cannot be read or its reader cannot be closed
    static Charset findSuitableEncoding(Path zipFile) throws IOException {
        try (ZipArchiveReader zf = openZipFile(zipFile, StandardCharsets.UTF_8)) {
            return findSuitableEncoding(zf);
        }
    }

    /// Guesses a filename charset without closing the supplied reader.
    ///
    /// Only entries whose name source is [ZipArchiveEntry.NameSource#NAME]
    /// participate. UTF-8 is preferred if it strictly decodes all remaining names,
    /// including when there are none. Otherwise, bounded statistical samples
    /// rank candidates, including low-confidence candidates. Exact Java charset
    /// mappings are preferred; EUC-JP is tried for EUC-JIS-2004 evidence when an
    /// exact mapping is unavailable. Candidates must strictly decode every
    /// participating name.
    /// Decoder state is reset between names. Fixed compatibility encodings are
    /// tried if statistical candidates fail; ISO-8859-1 is the final fallback.
    /// Selection does not depend on the system charset. Successful decoding
    /// does not guarantee correct names, particularly for mixed encodings.
    static Charset findSuitableEncoding(ZipArchiveReader zipFile) {
        @Nullable List<byte[]> rawNames = null;

        for (ZipArchiveEntry entry : zipFile.getEntries()) {
            if (entry.getNameSource() != ZipArchiveEntry.NameSource.NAME)
                continue;

            byte @Nullable [] rawName = entry.getRawName();
            if (rawName == null || StringUtils.isASCII(rawName))
                continue;

            if (rawNames == null)
                rawNames = new ArrayList<>();

            rawNames.add(rawName);
        }

        if (rawNames == null || canDecodeNames(StandardCharsets.UTF_8, rawNames))
            return StandardCharsets.UTF_8;

        Set<Charset> attempted = new HashSet<>();
        attempted.add(StandardCharsets.UTF_8);
        for (EncodingDetector.Candidate candidate : FILENAME_ENCODING_DETECTOR.detect(sampleNames(rawNames)).candidates()) {
            EncodingDetector.@Nullable Encoding encoding = candidate.encoding();
            @Nullable Charset charset = encoding == null ? null : encoding.charset();
            // The detector groups ordinary EUC-JP input under its EUC-JIS-2004 target.
            // Treat EUC-JP as another hypothesis, subject to full strict validation.
            if (charset == null && encoding == EncodingDetector.Encoding.EUC_JIS_2004)
                charset = encoding.approximateCharset();
            if (charset != null && attempted.add(charset) && canDecodeNames(charset, rawNames))
                return charset;
        }

        for (String name : FALLBACK_ENCODINGS) {
            try {
                Charset charset = Charset.forName(name);
                if (attempted.add(charset) && canDecodeNames(charset, rawNames))
                    return charset;
            } catch (UnsupportedCharsetException ignored) {
                // Optional charset providers may be absent in a reduced runtime.
            }
        }
        return StandardCharsets.ISO_8859_1;
    }

    /// Samples complete, distinct names across the entry list within a fixed byte budget.
    private static ByteBuffer sampleNames(List<byte[]> rawNames) {
        ByteBuffer sample = ByteBuffer.allocate(ENCODING_SAMPLE_BYTES);
        Set<ByteBuffer> seen = new HashSet<>();
        int count = Math.min(rawNames.size(), ENCODING_SAMPLE_NAMES);
        for (int i = 0; i < count; i++) {
            int index = count == 1 ? 0 : (int) ((long) i * (rawNames.size() - 1) / (count - 1));
            byte[] name = rawNames.get(index);
            if (name.length >= sample.remaining() || !seen.add(ByteBuffer.wrap(name)))
                continue;
            sample.put(name);
            // Separate independent names without creating cross-name multibyte sequences.
            sample.put((byte) '\n');
        }
        return sample.flip();
    }

    /// Tests all names with a strict decoder, discarding output in bounded chunks.
    private static boolean canDecodeNames(Charset charset, List<byte[]> rawNames) {
        CharsetDecoder decoder = charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        CharBuffer output = CharBuffer.allocate(1024);
        for (byte[] name : rawNames) {
            decoder.reset();
            ByteBuffer input = ByteBuffer.wrap(name);
            CoderResult result;
            do {
                output.clear();
                result = decoder.decode(input, output, true);
            } while (result.isOverflow());
            if (result.isError())
                return false;
            do {
                output.clear();
                result = decoder.flush(output);
            } while (result.isOverflow());
            if (result.isError())
                return false;
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

    /// Opens an owning reader using the supplied charset for names without Unicode metadata.
    ///
    /// No encoding detection is performed. UTF-8 flags and valid Unicode extra
    /// fields take precedence over the supplied charset. With a non-UTF-8
    /// charset, raw names and comments must be decodable even when a Unicode
    /// extra field supplies the final value. The caller must close the reader.
    ///
    /// @param zipFile archive to open
    /// @param charset encoding for names without Unicode metadata
    /// @return newly opened reader
    /// @throws IOException if opening or reading the archive fails
    public static ZipArchiveReader openZipFile(Path zipFile, Charset charset) throws IOException {
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

    /// Buffer size for reading ZIP entry data.
    private static final int BUFFER_SIZE = 64 * 1024;

    /// Maximum number of bytes to decompress across all entries in strict mode.
    ///
    /// Strict mode must decompress entries to verify their checksums, so a highly compressible
    /// archive (a decompression bomb) would otherwise consume unbounded CPU and time. Genuine
    /// mods stay far below this bound; exceeding it yields `false` because the check
    /// could not be completed, not because the archive is known to be damaged.
    private static final long MAX_TOTAL_DECOMPRESSED_BYTES = 2L * 1024 * 1024 * 1024;

    /// Maximum number of entries to inspect.
    ///
    /// Bounds the loop itself: an archive made of millions of tiny entries would stay under the
    /// byte limit while still consuming unbounded CPU. Exceeding it yields `false`.
    private static final int MAX_ENTRIES = 100_000;

    /// Checks the integrity of the given archive file.
    ///
    /// This method decompresses entries up to the inspection limits
    /// ([MAX_ENTRIES] and [MAX_TOTAL_DECOMPRESSED_BYTES]) and verifies their CRC32 checksum
    /// and uncompressed size. This detects damaged entry payloads that a structurally valid
    /// archive can still contain, but such mods often still launch, so the result is advisory.
    /// Entries that record no usable CRC32 in the central directory are skipped rather than
    /// failing the whole archive, so a single such entry cannot make an otherwise-verifiable
    /// mod permanently unverifiable. In practice [java.util.zip.ZipFile] always resolves the
    /// checksum from the central directory, so this path is defensive.
    ///
    /// @param reader reader of the archive file to check
    /// @return true when damage was found, or false otherwise
    public static boolean isCorrupt(ZipArchiveReader reader) {
        try {
            Iterable<? extends ZipArchiveEntry> entries = reader.getEntries();
            byte[] buffer = new byte[BUFFER_SIZE];
            CRC32 crc = new CRC32();
            long totalDecompressed = 0;
            int entryCount = 0;

            for (ZipArchiveEntry entry : entries) {
                if (++entryCount > MAX_ENTRIES) return false;

                // Skip directories - they have no data to validate
                if (entry.isDirectory()) continue;

                crc.reset();
                long actualSize = 0;
                try (InputStream is = reader.getInputStream(entry)) {
                    int n;
                    while ((n = is.read(buffer)) != -1) {
                        crc.update(buffer, 0, n);
                        actualSize += n;
                        totalDecompressed += n;
                        if (totalDecompressed > MAX_TOTAL_DECOMPRESSED_BYTES) {
                            return false;
                        }
                    }
                }
                // An entry that records no usable CRC32 in the central directory cannot be
                // verified. Skip it rather than failing the whole archive: a single such
                // entry would otherwise make an otherwise-verifiable mod permanently
                // unverifiable. In practice ZipFile always resolves the checksum from the
                // central directory, so this path is defensive.
                if (entry.getCrc() < 0) continue;

                if (entry.getCrc() != crc.getValue()) return true;

                // Verify size if available
                if (entry.getSize() >= 0 && entry.getSize() != actualSize) return true;
            }
            // If nothing could be verified, the archive's integrity is genuinely unknown
            return false;
        } catch (IOException e) {
            // The file is missing, locked by another process, inaccessible, or hit a transient I/O
            // error. The check could not be performed, so the result is unknown rather
            // than "sound" — and must not be reported as damage.
            return false;
        }
    }

}
