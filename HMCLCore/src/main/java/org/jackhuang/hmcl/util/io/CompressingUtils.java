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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
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
        return new ZipArchiveReader(zipFile, charset, true, true);
    }

    public static ZipArchiveReader openZipFileWithPossibleEncoding(Path zipFile, Charset possibleEncoding) throws IOException {
        if (possibleEncoding == null)
            possibleEncoding = StandardCharsets.UTF_8;

        ZipArchiveReader zipReader = new ZipArchiveReader(zipFile, possibleEncoding, true, true);

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
        return new ZipArchiveReader(zipFile, suitableEncoding, true, true);
    }

    /// Resolves the charset [#openZipFileWithPossibleEncoding] would decode `zipFile` with,
    /// without leaving a reader open.
    ///
    /// Callers that decompress one archive from several threads need the charset before they open a
    /// reader per thread, so it has to be settled up front, and a throw-away reader is cheaper than
    /// re-running the detection once per thread.
    ///
    /// The result reproduces [#openZipFileWithPossibleEncoding] exactly, including the branch where
    /// the detection settles on UTF-8: that method returns the reader it had already opened with
    /// `possibleEncoding` instead of reopening with UTF-8, so an archive whose names are not valid
    /// `possibleEncoding` but are valid UTF-8 is decoded with `possibleEncoding` all the same. That
    /// is reported here rather than "fixed" so that a parallel extraction writes the same file names
    /// the sequential one did.
    ///
    /// @param zipFile          the archive
    /// @param possibleEncoding the charset the caller expects, or `null` for UTF-8
    /// @return the charset [#openZipFileWithPossibleEncoding] would end up decoding the archive with
    /// @throws IOException if detection found no charset that decodes the entry names
    public static Charset resolveZipEncoding(Path zipFile, Charset possibleEncoding) throws IOException {
        if (possibleEncoding == null)
            possibleEncoding = StandardCharsets.UTF_8;

        try (ZipArchiveReader zipReader = new ZipArchiveReader(zipFile, possibleEncoding, true, true)) {
            return resolveZipEncoding(zipReader, possibleEncoding);
        }
    }

    private static Charset resolveZipEncoding(ZipArchiveReader zipReader, Charset possibleEncoding) throws IOException {
        if (possibleEncoding != StandardCharsets.UTF_8 && CompressingUtils.testEncoding(zipReader, possibleEncoding)) {
            return possibleEncoding;
        }

        Charset suitableEncoding = CompressingUtils.findSuitableEncoding(zipReader);

        // Mirrors the early return in [#openZipFileWithPossibleEncoding]: on this path that method
        // hands back the reader it opened with `possibleEncoding` rather than reopening the archive
        // with the detected UTF-8, so `possibleEncoding` is what the archive is actually read with.
        if (suitableEncoding == StandardCharsets.UTF_8)
            return possibleEncoding;

        return suitableEncoding;
    }

    /// Upper bound on the readers opened over one archive when its entries are processed in parallel.
    public static final int MAX_PARALLEL_READERS = 8;

    /// Entry counts below this stay on the calling thread, where starting a worker costs more than
    /// the entries it would pick up.
    public static final int PARALLEL_READER_THRESHOLD = 32;

    /// Picks how many readers should share the work of one archive.
    ///
    /// @param itemCount the number of entries to process
    /// @return `1` to stay on the calling thread, otherwise the worker count
    public static int readerThreads(int itemCount) {
        if (itemCount < PARALLEL_READER_THRESHOLD) {
            return 1;
        }
        return Math.max(1, Math.min(Runtime.getRuntime().availableProcessors(), MAX_PARALLEL_READERS));
    }

    /// Runs `work` for every index below `itemCount`, spreading them over readers opened on `zipFile`.
    ///
    /// [ZipArchiveReader] seeks through a single channel, so one reader cannot serve two threads.
    /// Each worker opens its own reader instead; the central directory is parsed once per worker,
    /// which is negligible next to the entry data the workers read.
    ///
    /// Workers stop picking up new indices as soon as one of them fails, and the first failure is
    /// the one rethrown, so a caller sees the same exception a sequential loop would have raised.
    ///
    /// @param zipFile          the archive to read
    /// @param charset          the charset every reader decodes entry names with
    /// @param threads          the worker count; `1` runs everything on the calling thread
    /// @param itemCount        the number of indices `work` accepts
    /// @param threadNamePrefix prefix for the worker threads, for stack traces
    /// @param work             the action to run for one index
    /// @throws IOException the first failure a worker reported
    public static void forEachParallel(Path zipFile, Charset charset, int threads, int itemCount,
                                       String threadNamePrefix, ZipIndexWork work) throws IOException {
        if (itemCount <= 0) {
            return;
        }

        if (threads <= 1) {
            try (ZipArchiveReader reader = openZipFile(zipFile, charset)) {
                for (int index = 0; index < itemCount; index++) {
                    work.run(reader, index);
                }
            }
            return;
        }

        AtomicInteger next = new AtomicInteger();
        AtomicReference<IOException> failure = new AtomicReference<>();

        Thread[] workers = new Thread[threads];
        for (int i = 0; i < threads; i++) {
            workers[i] = new Thread(() -> {
                try (ZipArchiveReader reader = openZipFile(zipFile, charset)) {
                    for (int index = next.getAndIncrement(); index < itemCount; index = next.getAndIncrement()) {
                        if (failure.get() != null) {
                            return;
                        }
                        work.run(reader, index);
                    }
                } catch (IOException e) {
                    failure.compareAndSet(null, e);
                } catch (Throwable e) {
                    failure.compareAndSet(null, new IOException("Failed to read " + zipFile, e));
                }
            }, threadNamePrefix + i);
            workers[i].start();
        }

        for (Thread worker : workers) {
            try {
                worker.join();
            } catch (InterruptedException e) {
                failure.compareAndSet(null, new IOException("Interrupted while reading " + zipFile, e));
                Thread.currentThread().interrupt();
            }
        }

        IOException error = failure.get();
        if (error != null) {
            throw error;
        }
    }

    /// The unit of work [#forEachParallel] runs.
    @FunctionalInterface
    public interface ZipIndexWork {
        /// @param reader the calling worker's own reader over the archive
        /// @param index  the index to process
        /// @throws IOException if the entry cannot be read or written
        void run(ZipArchiveReader reader, int index) throws IOException;
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
