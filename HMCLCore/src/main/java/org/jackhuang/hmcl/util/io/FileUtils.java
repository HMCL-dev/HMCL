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
import org.glavo.chardet.DetectedCharset;
import org.glavo.chardet.UniversalDetector;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.function.ExceptionalConsumer;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
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
        return StringUtils.substringBeforeLast(getName(file), '.');
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

    public static String readTextMaybeNativeEncoding(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);

        if (OperatingSystem.NATIVE_CHARSET == UTF_8)
            return new String(bytes, UTF_8);

        UniversalDetector detector = new UniversalDetector();
        detector.handleData(bytes);
        detector.dataEnd();

        DetectedCharset detectedCharset = detector.getDetectedCharset();
        if (detectedCharset != null && detectedCharset.isSupported()
                && (detectedCharset == DetectedCharset.UTF_8 || detectedCharset == DetectedCharset.US_ASCII))
            return new String(bytes, UTF_8);
        else
            return new String(bytes, OperatingSystem.NATIVE_CHARSET);
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
        Path tmpFile = tmpSaveFile(file);
        try (BufferedWriter writer = Files.newBufferedWriter(tmpFile, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE)) {
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
