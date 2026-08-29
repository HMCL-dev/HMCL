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

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import kala.encdet.EncodingDetector;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.function.ExceptionalConsumer;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/**
 * @author huang
 */
public final class FileUtils {

    private FileUtils() {
    }

    public static @Nullable Path toPath(@Nullable File file) {
        try {
            return file != null ? file.toPath() : null;
        } catch (InvalidPathException e) {
            LOG.warning("Invalid path: " + file);
            return null;
        }
    }

    public static @Nullable List<Path> toPaths(@Nullable List<File> files) {
        if (files == null) return null;
        return files.stream().map(FileUtils::toPath).filter(Objects::nonNull).toList();
    }

    public static boolean canCreateDirectory(String path) {
        try {
            return canCreateDirectory(Paths.get(path));
        } catch (InvalidPathException e) {
            return false;
        }
    }

    public static boolean canCreateDirectory(Path path) {
        if (Files.isDirectory(path)) return true;
        else if (Files.exists(path)) return false;
        else {
            Path lastPath = path; // always not exist
            path = path.getParent();
            // find existent ancestor
            while (path != null && !Files.exists(path)) {
                lastPath = path;
                path = path.getParent();
            }
            if (path == null) return false; // all ancestors are nonexistent
            if (!Files.isDirectory(path)) return false; // ancestor is file
            try {
                Files.createDirectory(lastPath); // check permission
                Files.delete(lastPath); // safely delete empty directory
                return true;
            } catch (IOException e) {
                return false;
            }
        }
    }

    public static String getNameWithoutExtension(String fileName) {
        return StringUtils.substringBeforeLast(fileName, '.');
    }

    public static String getNameWithoutExtension(Path file) {
        String name = getName(file);
        if (Files.isDirectory(file)) {
            return name;
        }
        return StringUtils.substringBeforeLast(name, '.');
    }

    public static String getExtension(String fileName) {
        return StringUtils.substringAfterLast(fileName, '.');
    }

    public static String getExtension(Path file) {
        return StringUtils.substringAfterLast(getName(file), '.');
    }

    /**
     * This method is for normalizing ZipPath since Path.normalize of ZipFileSystem does not work properly.
     */
    public static String normalizePath(String path) {
        return StringUtils.addPrefix(StringUtils.removeSuffix(path, "/", "\\"), "/");
    }

    public static String getName(Path path) {
        Path fileName = path.getFileName();
        return fileName != null ? fileName.toString() : "";
    }

    public static Path toAbsolute(Path path) {
        return path.toAbsolutePath().normalize();
    }

    public static String getAbsolutePath(Path path) {
        return path.toAbsolutePath().normalize().toString();
    }

    // https://learn.microsoft.com/biztalk/core/restrictions-when-configuring-the-file-adapter
    private static final Set<String> INVALID_WINDOWS_RESOURCE_BASE_NAMES = Set.of(
            "aux", "con", "nul", "prn", "clock$",
            "com1", "com2", "com3", "com4", "com5", "com6", "com7", "com8", "com9",
            "com¹", "com²", "com³",
            "lpt1", "lpt2", "lpt3", "lpt4", "lpt5", "lpt6", "lpt7", "lpt8", "lpt9",
            "lpt¹", "lpt²", "lpt³"
    );

    /// @see #isNameValid(OperatingSystem, String)
    public static boolean isNameValid(String name) {
        return isNameValid(OperatingSystem.CURRENT_OS, name);
    }

    /// Returns true if the given name is a valid file name on the given operating system,
    /// and `false` otherwise.
    public static boolean isNameValid(OperatingSystem os, String name) {
        // empty filename is not allowed
        if (name.isEmpty())
            return false;
        // '.', '..' and '~' have special meaning on all platforms
        if (name.equals(".") || name.equals("..") || name.equals("~"))
            return false;

        for (int i = 0; i < name.length(); i++) {
            char ch = name.charAt(i);
            int codePoint;

            if (Character.isSurrogate(ch)) {
                if (!Character.isHighSurrogate(ch))
                    return false;

                if (i == name.length() - 1)
                    return false;

                char ch2 = name.charAt(++i);
                if (!Character.isLowSurrogate(ch2))
                    return false;

                codePoint = Character.toCodePoint(ch, ch2);
            } else {
                codePoint = ch;
            }

            if (!Character.isValidCodePoint(codePoint)
                    || Character.isISOControl(codePoint)
                    || codePoint == '/' || codePoint == '\0'
                    || codePoint == ':'
                    // Unicode replacement character
                    || codePoint == 0xfffd
                    // Not Unicode character
                    || codePoint == 0xfffe || codePoint == 0xffff)
                return false;

            // https://learn.microsoft.com/windows/win32/fileio/naming-a-file
            if (os == OperatingSystem.WINDOWS &&
                    (ch == '<' || ch == '>' || ch == '"' || ch == '\\' || ch == '|' || ch == '?' || ch == '*')) {
                return false;
            }
        }

        if (os == OperatingSystem.WINDOWS) { // Windows only
            char lastChar = name.charAt(name.length() - 1);
            // filenames ending in dot are not valid
            if (lastChar == '.')
                return false;
            // file names ending with whitespace are truncated (bug 118997)
            if (Character.isWhitespace(lastChar))
                return false;

            // on windows, filename suffixes are not relevant to name validity
            String basename = StringUtils.substringBeforeLast(name, '.');
            if (INVALID_WINDOWS_RESOURCE_BASE_NAMES.contains(basename.toLowerCase(Locale.ROOT)))
                return false;
        }

        return true;
    }

    /// @see #isNameValidForJar(OperatingSystem, String)
    public static boolean isNameValidForJar(String name) {
        return isNameValidForJar(OperatingSystem.CURRENT_OS, name);
    }

    /// Returns true if the given name is a valid jar file name on the given operating system,
    /// and `false` otherwise.
    public static boolean isNameValidForJar(OperatingSystem os, String name) {
        return !name.contains("!") && isNameValid(os, name);
    }

    /// Safely get the file size. Returns `0` if the file does not exist or the size cannot be obtained.
    public static long size(Path file) {
        try {
            return Files.size(file);
        } catch (NoSuchFileException ignored) {
            return 0L;
        } catch (IOException e) {
            LOG.warning("Failed to get file size of " + file, e);
            return 0L;
        }
    }

    /// How many extra leading bytes to keep when reading only a tail, so the cut can be aligned to a
    /// character boundary (UTF-8 uses at most 4 bytes per character).
    private static final int TAIL_GUARD_BYTES = 16;

    /// How many leading bytes to sample when detecting the charset of a large file.
    private static final int CHARSET_SAMPLE_BYTES = 8192;

    /// Reads the whole file and decodes it with HMCL's charset-detection rules.
    public static String readTextMaybeNativeEncoding(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        return new String(bytes, detectCharset(bytes));
    }

    /// Reads at most the last [maxBytes] bytes of [file], decoded with HMCL's charset-detection rules.
    ///
    /// A file larger than [maxBytes] is never read into memory in full: the charset is detected from a small
    /// head sample, then only the tail is read by seeking straight to its offset. The returned text is
    /// guaranteed to be at most [maxBytes] bytes after re-encoding with the detected charset, never starts in
    /// the middle of a multi-byte character or a line, and always keeps the very end of the file.
    ///
    /// @param file     the file to read from
    /// @param maxBytes the maximum number of bytes to keep from the tail; must be non-negative
    /// @return the decoded tail content, at most [maxBytes] bytes long
    /// @throws IOException if the file cannot be read
    public static String readTextTailMaybeNativeEncoding(Path file, long maxBytes) throws IOException {
        long size = Files.size(file);
        if (size <= maxBytes)
            return readTextMaybeNativeEncoding(file);

        Charset charset = detectCharset(readHead(file));
        byte[] tail = readTailBytes(file, size, maxBytes);

        int start = alignToCharacterBoundary(tail, charset);
        start = alignToLineStart(tail, start);

        String content = new String(tail, start, tail.length - start, charset);
        return trimTailToByteLimit(content, charset, (int) maxBytes);
    }

    /// Truncates [content] so its UTF-8 encoding fits within [maxBytes], keeping the tail and never splitting
    /// a multi-byte character.
    ///
    /// @param content  the text to truncate
    /// @param maxBytes the UTF-8 byte limit
    /// @return [content], truncated to at most [maxBytes] UTF-8 bytes
    public static String truncateUtf8ToByteLimit(String content, int maxBytes) {
        return trimTailToByteLimit(content, UTF_8, maxBytes);
    }

    /// Detects the charset of [bytes] using the same heuristics used by the text readers.
    ///
    /// @param bytes the bytes whose charset should be detected
    /// @return the charset to decode [bytes] with
    private static Charset detectCharset(byte[] bytes) {
        if (OperatingSystem.NATIVE_CHARSET == UTF_8)
            return UTF_8;

        EncodingDetector detector = EncodingDetector.MODERN_WEB;
        EncodingDetector.@Nullable Encoding bestEncoding = detector.detect(bytes).bestEncoding();
        @Nullable Charset detectedCharset = bestEncoding != null ? bestEncoding.approximateCharset() : null;

        if (detectedCharset != null && (detectedCharset == UTF_8 || detectedCharset == US_ASCII))
            return UTF_8;
        else
            return OperatingSystem.NATIVE_CHARSET;
    }

    /// Reads a small head sample of [file] for charset detection.
    ///
    /// @param file the file to sample
    /// @return the leading bytes of [file]
    /// @throws IOException if the file cannot be read
    private static byte[] readHead(Path file) throws IOException {
        try (InputStream input = Files.newInputStream(file)) {
            return input.readNBytes(CHARSET_SAMPLE_BYTES);
        }
    }

    /// Reads the last bytes of a file larger than [maxBytes] via random access, keeping a small guard so a
    /// UTF-8 cut can later be aligned to a character boundary.
    ///
    /// @param file     the file to read from
    /// @param size     the size of the file, obtained upfront
    /// @param maxBytes the maximum number of bytes to keep from the tail
    /// @return the raw tail bytes
    /// @throws IOException if the file cannot be read
    private static byte[] readTailBytes(Path file, long size, long maxBytes) throws IOException {
        long offset = Math.max(0, size - maxBytes - TAIL_GUARD_BYTES);
        int length = (int) Math.min(size - offset, maxBytes + TAIL_GUARD_BYTES);
        byte[] tail = new byte[length];

        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            channel.position(offset);
            ByteBuffer buffer = ByteBuffer.wrap(tail);
            while (buffer.hasRemaining()) {
                if (channel.read(buffer) < 0)
                    break;
            }
            int read = buffer.position();
            return read == length ? tail : Arrays.copyOf(tail, read);
        }
    }

    /// Returns the smallest index within the guard window that starts on a character boundary.
    ///
    /// UTF-8 boundary bytes never start with `10xxxxxx`, so continuation bytes are skipped. Other charsets
    /// are probed with a strict decoder over the leading window.
    ///
    /// @param bytes   the bytes to align
    /// @param charset the charset to decode with
    /// @return the leading offset that keeps the first character intact
    private static int alignToCharacterBoundary(byte[] bytes, Charset charset) {
        int limit = Math.min(TAIL_GUARD_BYTES, bytes.length);
        if (charset == UTF_8) {
            int index = 0;
            while (index < limit && (bytes[index] & 0xC0) == 0x80)
                index++;
            return index;
        }

        CharsetDecoder decoder = charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        for (int index = 0; index <= limit; index++) {
            try {
                decoder.reset();
                decoder.decode(ByteBuffer.wrap(bytes, index, bytes.length - index));
                return index;
            } catch (CharacterCodingException ignored) {
                // The byte at `index` is still a tail byte of a multi-byte character; try the next one.
            }
        }
        return 0;
    }

    /// Skips to the start of the next line so the returned tail never opens with a truncated first line.
    ///
    /// @param bytes the tail bytes
    /// @param start the first character boundary
    /// @return the offset of the first byte of a complete line
    private static int alignToLineStart(byte[] bytes, int start) {
        for (int i = start; i < bytes.length; i++) {
            if (bytes[i] == '\n')
                return i + 1;
        }
        return start;
    }

    /// Truncates [content] so its [charset] encoding fits within [maxBytes], keeping the tail and never
    /// splitting a multi-byte character.
    ///
    /// @param content  the decoded content
    /// @param charset  the charset [content] was decoded with
    /// @param maxBytes the byte limit
    /// @return [content], truncated to at most [maxBytes] bytes
    private static String trimTailToByteLimit(String content, Charset charset, int maxBytes) {
        byte[] bytes = content.getBytes(charset);
        if (bytes.length <= maxBytes) {
            return content;
        }

        int start = bytes.length - maxBytes;
        if (charset == UTF_8) {
            while (start < bytes.length && (bytes[start] & 0xC0) == 0x80)
                start++;
            return new String(bytes, start, bytes.length - start, UTF_8);
        }

        // Non-UTF-8 native encodings are rare; align with a strict decoder over the small over-run window so
        // a multi-byte character is never split in two.
        CharsetDecoder decoder = charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        int limit = Math.min(bytes.length, start + TAIL_GUARD_BYTES);
        for (int index = start; index <= limit; index++) {
            try {
                decoder.reset();
                decoder.decode(ByteBuffer.wrap(bytes, index, bytes.length - index));
                return new String(bytes, index, bytes.length - index, charset);
            } catch (CharacterCodingException ignored) {
                // The byte at `index` is still a tail byte of a multi-byte character; try the next one.
            }
        }
        return new String(bytes, start, bytes.length - start, charset);
    }

    public static void deleteDirectory(Path directory) throws IOException {
        if (!Files.exists(directory))
            return;

        if (!Files.isSymbolicLink(directory))
            cleanDirectory(directory);

        Files.deleteIfExists(directory);
    }

    public static boolean deleteDirectoryQuietly(Path directory) {
        try {
            deleteDirectory(directory);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public static void setExecutable(Path path) {
        PosixFileAttributeView view = Files.getFileAttributeView(path, PosixFileAttributeView.class);
        if (view != null) {
            try {
                Set<PosixFilePermission> oldPermissions = view.readAttributes().permissions();
                if (oldPermissions.contains(PosixFilePermission.OWNER_EXECUTE))
                    return;

                EnumSet<PosixFilePermission> permissions = EnumSet.noneOf(PosixFilePermission.class);
                permissions.addAll(oldPermissions);
                permissions.add(PosixFilePermission.OWNER_EXECUTE);
                view.setPermissions(permissions);
            } catch (IOException e) {
                LOG.warning("Failed to set permissions for " + path, e);
            }
        }
    }

    /**
     * Copy directory.
     * Paths of all files relative to source directory will be the same as the ones relative to destination directory.
     *
     * @param src  the source directory.
     * @param dest the destination directory, which will be created if not existing.
     * @throws IOException if an I/O error occurs.
     */
    public static void copyDirectory(Path src, Path dest) throws IOException {
        copyDirectory(src, dest, path -> true);
    }

    public static void copyDirectory(Path src, Path dest, Predicate<String> filePredicate) throws IOException {
        Files.walkFileTree(src, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (!filePredicate.test(src.relativize(file).toString())) {
                    return FileVisitResult.SKIP_SUBTREE;
                }

                Path destFile = dest.resolve(src.relativize(file).toString());
                Files.copy(file, destFile, StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (!filePredicate.test(src.relativize(dir).toString())) {
                    return FileVisitResult.SKIP_SUBTREE;
                }

                Path destDir = dest.resolve(src.relativize(dir).toString());
                Files.createDirectories(destDir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    public static boolean hasKnownDesktop() {
        if (!OperatingSystem.CURRENT_OS.isLinuxOrBSD())
            return true;

        String desktops = System.getenv("XDG_CURRENT_DESKTOP");
        if (desktops == null) {
            desktops = System.getenv("XDG_SESSION_DESKTOP");
        }

        if (desktops == null) {
            return false;
        }
        for (String desktop : desktops.split(":")) {
            switch (desktop.toLowerCase(Locale.ROOT)) {
                case "gnome":
                case "xfce":
                case "kde":
                case "mate":
                case "deepin":
                case "x-cinnamon":
                    return true;
            }
        }

        return false;
    }

    /**
     * Move file to trash.
     *
     * @param file the file being moved to trash.
     * @return false if moveToTrash does not exist, or platform does not support Desktop.Action.MOVE_TO_TRASH
     */
    public static boolean moveToTrash(Path file) {
        if (OperatingSystem.CURRENT_OS.isLinuxOrBSD() && hasKnownDesktop()) {
            if (!Files.exists(file)) {
                return false;
            }

            String xdgData = System.getenv("XDG_DATA_HOME");

            Path trashDir;
            if (StringUtils.isNotBlank(xdgData)) {
                trashDir = Paths.get(xdgData, "Trash");
            } else {
                trashDir = Paths.get(System.getProperty("user.home"), ".local/share/Trash");
            }

            Path infoDir = trashDir.resolve("info");
            Path filesDir = trashDir.resolve("files");

            try {
                Files.createDirectories(infoDir);
                Files.createDirectories(filesDir);

                String name = getName(file);

                Path infoFile = infoDir.resolve(name + ".trashinfo");
                Path targetFile = filesDir.resolve(name);

                int n = 0;
                while (Files.exists(infoFile) || Files.exists(targetFile)) {
                    n++;
                    infoFile = infoDir.resolve(name + "." + n + ".trashinfo");
                    targetFile = filesDir.resolve(name + "." + n);
                }

                String time = DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(ZonedDateTime.now().truncatedTo(ChronoUnit.SECONDS));
                if (Files.isDirectory(file)) {
                    FileUtils.copyDirectory(file, targetFile);
                } else {
                    FileUtils.copyFile(file, targetFile);
                }

                Files.createDirectories(infoDir);
                Files.writeString(infoFile, "[Trash Info]\nPath=" + FileUtils.getAbsolutePath(file) + "\nDeletionDate=" + time + "\n");
                FileUtils.forceDelete(file);
            } catch (IOException e) {
                LOG.warning("Failed to move " + file + " to trash", e);
                return false;
            }

            return true;
        }

        try {
            return java.awt.Desktop.getDesktop().moveToTrash(file.toFile());
        } catch (Exception e) {
            return false;
        }
    }

    public static void cleanDirectory(Path directory)
            throws IOException {
        if (!Files.exists(directory)) {
            Files.createDirectories(directory);
            return;
        }

        if (!Files.isDirectory(directory)) {
            String message = directory + " is not a directory";
            throw new IllegalArgumentException(message);
        }

        Files.walkFileTree(directory, new SimpleFileVisitor<>() {
            @Override
            public @NotNull FileVisitResult visitFile(@NotNull Path file, @NotNull BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public @NotNull FileVisitResult postVisitDirectory(@NotNull Path dir, @Nullable IOException exc) throws IOException {
                if (!dir.equals(directory)) {
                    Files.delete(dir);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    @CanIgnoreReturnValue
    public static boolean cleanDirectoryQuietly(Path directory) {
        try {
            cleanDirectory(directory);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public static void forceDelete(Path file)
            throws IOException {
        if (Files.isDirectory(file))
            deleteDirectory(file);
        else
            Files.delete(file);
    }

    public static void copyFile(Path srcFile, Path destFile)
            throws IOException {
        Objects.requireNonNull(srcFile, "Source must not be null");
        Objects.requireNonNull(destFile, "Destination must not be null");
        if (!Files.exists(srcFile))
            throw new FileNotFoundException("Source '" + srcFile + "' does not exist");
        if (Files.isDirectory(srcFile))
            throw new IOException("Source '" + srcFile + "' exists but is a directory");
        Files.createDirectories(destFile.getParent());
        if (Files.exists(destFile) && !Files.isWritable(destFile))
            throw new IOException("Destination '" + destFile + "' exists but is read-only");

        Files.copy(srcFile, destFile, StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING);
    }

    public static List<Path> listFilesByExtension(Path file, String extension) {
        try (Stream<Path> list = Files.list(file)) {
            return list.filter(it -> Files.isRegularFile(it) && extension.equals(getExtension(it)))
                    .toList();
        } catch (IOException e) {
            LOG.warning("Failed to list files by extension " + extension, e);
            return List.of();
        }
    }

    public static Optional<Path> tryGetPath(String first, String... more) {
        if (first == null) return Optional.empty();
        try {
            return Optional.of(Paths.get(first, more));
        } catch (InvalidPathException e) {
            return Optional.empty();
        }
    }

    public static Path tmpSaveFile(Path file) {
        return file.toAbsolutePath().resolveSibling("." + file.getFileName().toString() + ".tmp");
    }

    public static void saveSafely(Path file, String content) throws IOException {
        saveSafely(file, content, StandardCharsets.UTF_8);
    }

    public static void saveSafely(Path file, String content, @Nullable Charset charset) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        Path tmpFile = tmpSaveFile(file);
        try (BufferedWriter writer = Files.newBufferedWriter(tmpFile, charset != null ? charset : StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE)) {
            writer.write(content);
        }

        try {
            if (Files.exists(file) && Files.getAttribute(file, "dos:hidden") == Boolean.TRUE) {
                Files.setAttribute(tmpFile, "dos:hidden", true);
            }
        } catch (Throwable ignored) {
        }

        Files.move(tmpFile, file, StandardCopyOption.REPLACE_EXISTING);
    }

    public static void saveSafely(Path file, ExceptionalConsumer<? super OutputStream, IOException> action) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        Path tmpFile = tmpSaveFile(file);

        try (OutputStream os = Files.newOutputStream(tmpFile, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE)) {
            action.accept(os);
        }

        try {
            if (Files.exists(file) && Files.getAttribute(file, "dos:hidden") == Boolean.TRUE) {
                Files.setAttribute(tmpFile, "dos:hidden", true);
            }
        } catch (Throwable ignored) {
        }

        Files.move(tmpFile, file, StandardCopyOption.REPLACE_EXISTING);
    }

    public static String printFileStructure(Path path, int maxDepth) throws IOException {
        return DirectoryStructurePrinter.list(path, maxDepth);
    }

    public static EnumSet<PosixFilePermission> parsePosixFilePermission(int unixMode) {
        EnumSet<PosixFilePermission> permissions = EnumSet.noneOf(PosixFilePermission.class);

        // Owner permissions
        if ((unixMode & 0400) != 0) permissions.add(PosixFilePermission.OWNER_READ);
        if ((unixMode & 0200) != 0) permissions.add(PosixFilePermission.OWNER_WRITE);
        if ((unixMode & 0100) != 0) permissions.add(PosixFilePermission.OWNER_EXECUTE);

        // Group permissions
        if ((unixMode & 0040) != 0) permissions.add(PosixFilePermission.GROUP_READ);
        if ((unixMode & 0020) != 0) permissions.add(PosixFilePermission.GROUP_WRITE);
        if ((unixMode & 0010) != 0) permissions.add(PosixFilePermission.GROUP_EXECUTE);

        // Others permissions
        if ((unixMode & 0004) != 0) permissions.add(PosixFilePermission.OTHERS_READ);
        if ((unixMode & 0002) != 0) permissions.add(PosixFilePermission.OTHERS_WRITE);
        if ((unixMode & 0001) != 0) permissions.add(PosixFilePermission.OTHERS_EXECUTE);

        return permissions;
    }
}
