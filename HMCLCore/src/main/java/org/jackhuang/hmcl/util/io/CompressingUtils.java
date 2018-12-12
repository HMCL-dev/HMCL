/**
 * Hello Minecraft! Launcher
 * Copyright (C) 2018  huangyuhui <huanghongxun2008@126.com> and contributors
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

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.spi.FileSystemProvider;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
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

    @NotNull
    private static FileVisitResult testZipPath(Path file, Path root, AtomicBoolean result) {
        try {
            root.relativize(file).toString(); // throw IllegalArgumentException for wrong encoding.
            return FileVisitResult.CONTINUE;
        } catch (Exception e) {
            result.set(false);
            return FileVisitResult.TERMINATE;
        }
    }

    public static boolean testEncoding(Path zipFile, Charset encoding) throws IOException {
        AtomicBoolean result = new AtomicBoolean(true);
        try (FileSystem fs = CompressingUtils.createReadOnlyZipFileSystem(zipFile, encoding)) {
            Path root = fs.getPath("/");
            Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file,
                                                 BasicFileAttributes attrs) {
                    return testZipPath(file, root, result);
                }

                @Override
                public FileVisitResult preVisitDirectory(Path dir,
                                                         BasicFileAttributes attrs) {
                    return testZipPath(dir, root, result);
                }
            });
        }
        return result.get();
    }

    public static Charset findSuitableEncoding(Path zipFile) throws IOException {
        return findSuitableEncoding(zipFile, Charset.availableCharsets().values());
    }

    public static Charset findSuitableEncoding(Path zipFile, Collection<Charset> candidates) throws IOException {
        if (testEncoding(zipFile, StandardCharsets.UTF_8)) return StandardCharsets.UTF_8;
        if (testEncoding(zipFile, Charset.defaultCharset())) return Charset.defaultCharset();

        for (Charset charset : candidates)
            if (charset != null && testEncoding(zipFile, charset))
                return charset;
        throw new IOException("Cannot find suitable encoding for the zip.");
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
        return readTextZipEntry(zipFile.toPath(), name, null);
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
        try (FileSystem fs = createReadOnlyZipFileSystem(zipFile, encoding)) {
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
        } catch (IOException e) {
            return Optional.empty();
        }
    }
}
