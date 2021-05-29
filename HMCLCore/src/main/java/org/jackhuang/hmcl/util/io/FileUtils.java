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

import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.StringUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 *
 * @author huang
 */
public final class FileUtils {

    private FileUtils() {
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

    public static String getNameWithoutExtension(File file) {
        return StringUtils.substringBeforeLast(file.getName(), '.');
    }

    public static String getNameWithoutExtension(Path file) {
        return StringUtils.substringBeforeLast(getName(file), '.');
    }

    public static String getExtension(File file) {
        return StringUtils.substringAfterLast(file.getName(), '.');
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
        return StringUtils.removeSuffix(path.getFileName().toString(), "/", "\\");
    }

    public static String getName(Path path, String candidate) {
        if (path.getFileName() == null) return candidate;
        else return getName(path);
    }

    public static String readText(File file) throws IOException {
        return readText(file, UTF_8);
    }

    public static String readText(File file, Charset charset) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), charset);
    }

    public static String readText(Path file) throws IOException {
        return readText(file, UTF_8);
    }

    public static String readText(Path file, Charset charset) throws IOException {
        return new String(Files.readAllBytes(file), charset);
    }

    /**
     * Write plain text to file. Characters are encoded into bytes using UTF-8.
     * <p>
     * We don't care about platform difference of line separator. Because readText accept all possibilities of line separator.
     * It will create the file if it does not exist, or truncate the existing file to empty for rewriting.
     * All characters in text will be written into the file in binary format. Existing data will be erased.
     *
     * @param file the path to the file
     * @param text the text being written to file
     * @throws IOException if an I/O error occurs
     */
    public static void writeText(File file, String text) throws IOException {
        writeText(file, text, UTF_8);
    }

    /**
     * Write plain text to file.
     * <p>
     * We don't care about platform difference of line separator. Because readText accept all possibilities of line separator.
     * It will create the file if it does not exist, or truncate the existing file to empty for rewriting.
     * All characters in text will be written into the file in binary format. Existing data will be erased.
     *
     * @param file    the path to the file
     * @param text    the text being written to file
     * @param charset the charset to use for encoding
     * @throws IOException if an I/O error occurs
     */
    public static void writeText(File file, String text, Charset charset) throws IOException {
        writeBytes(file, text.getBytes(charset));
    }

    /**
     * Write byte array to file.
     * It will create the file if it does not exist, or truncate the existing file to empty for rewriting.
     * All bytes in byte array will be written into the file in binary format. Existing data will be erased.
     *
     * @param file  the path to the file
     * @param array the data being written to file
     * @throws IOException if an I/O error occurs
     */
    public static void writeBytes(File file, byte[] array) throws IOException {
        Files.createDirectories(file.toPath().getParent());
        Files.write(file.toPath(), array);
    }

    public static void deleteDirectory(File directory)
            throws IOException {
        if (!directory.exists())
            return;

        if (!isSymlink(directory))
            cleanDirectory(directory);

        if (!directory.delete()) {
            String message = "Unable to delete directory " + directory + ".";

            throw new IOException(message);
        }
    }

    public static boolean deleteDirectoryQuietly(File directory) {
        try {
            deleteDirectory(directory);
            return true;
        } catch (IOException e) {
            return false;
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
        Files.walkFileTree(src, new SimpleFileVisitor<Path>() {
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

    /**
     * Move file to trash.
     * <p>
     * This method is only implemented in Java 9. Please check we are using Java 9 by invoking isMovingToTrashSupported.
     * Example:
     * <pre>{@code
     * if (FileUtils.isMovingToTrashSupported()) {
     *     FileUtils.moveToTrash(file);
     * }
     * }</pre>
     *
     * @param file the file being moved to trash.
     * @return false if moveToTrash does not exist, or platform does not support Desktop.Action.MOVE_TO_TRASH
     * @see FileUtils#isMovingToTrashSupported()
     */
    public static boolean moveToTrash(File file) {
        try {
            java.awt.Desktop desktop = java.awt.Desktop.getDesktop();
            Method moveToTrash = desktop.getClass().getMethod("moveToTrash", File.class);
            moveToTrash.invoke(desktop, file);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Check if {@code java.awt.Desktop.moveToTrash} exists.
     *
     * @return true if the method exists.
     */
    public static boolean isMovingToTrashSupported() {
        try {
            java.awt.Desktop.class.getMethod("moveToTrash", File.class);
            return true;
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    public static void cleanDirectory(File directory)
            throws IOException {
        if (!directory.exists()) {
            if (!makeDirectory(directory))
                throw new IOException("Failed to create directory: " + directory);
            return;
        }

        if (!directory.isDirectory()) {
            String message = directory + " is not a directory";
            throw new IllegalArgumentException(message);
        }

        File[] files = directory.listFiles();
        if (files == null)
            throw new IOException("Failed to list contents of " + directory);

        IOException exception = null;
        for (File file : files)
            try {
                forceDelete(file);
            } catch (IOException ioe) {
                exception = ioe;
            }

        if (null != exception)
            throw exception;
    }

    public static boolean cleanDirectoryQuietly(File directory) {
        try {
            cleanDirectory(directory);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public static void forceDelete(File file)
            throws IOException {
        if (file.isDirectory()) {
            deleteDirectory(file);
        } else {
            boolean filePresent = file.exists();
            if (!file.delete()) {
                if (!filePresent)
                    throw new FileNotFoundException("File does not exist: " + file);
                throw new IOException("Unable to delete file: " + file);
            }
        }
    }

    public static boolean isSymlink(File file)
            throws IOException {
        Objects.requireNonNull(file, "File must not be null");
        if (File.separatorChar == '\\')
            return false;
        File fileInCanonicalDir;
        if (file.getParent() == null)
            fileInCanonicalDir = file;
        else {
            File canonicalDir = file.getParentFile().getCanonicalFile();
            fileInCanonicalDir = new File(canonicalDir, file.getName());
        }

        return !fileInCanonicalDir.getCanonicalFile().equals(fileInCanonicalDir.getAbsoluteFile());
    }

    public static void copyFile(File srcFile, File destFile)
            throws IOException {
        Objects.requireNonNull(srcFile, "Source must not be null");
        Objects.requireNonNull(destFile, "Destination must not be null");
        if (!srcFile.exists())
            throw new FileNotFoundException("Source '" + srcFile + "' does not exist");
        if (srcFile.isDirectory())
            throw new IOException("Source '" + srcFile + "' exists but is a directory");
        if (srcFile.getCanonicalPath().equals(destFile.getCanonicalPath()))
            throw new IOException("Source '" + srcFile + "' and destination '" + destFile + "' are the same");
        File parentFile = destFile.getParentFile();
        if (parentFile != null && !FileUtils.makeDirectory(parentFile))
            throw new IOException("Destination '" + parentFile + "' directory cannot be created");
        if (destFile.exists() && !destFile.canWrite())
            throw new IOException("Destination '" + destFile + "' exists but is read-only");

        Files.copy(srcFile.toPath(), destFile.toPath(), StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING);
    }

    public static void copyFile(Path srcFile, Path destFile)
            throws IOException {
        Objects.requireNonNull(srcFile, "Source must not be null");
        Objects.requireNonNull(destFile, "Destination must not be null");
        if (!Files.exists(srcFile))
            throw new FileNotFoundException("Source '" + srcFile + "' does not exist");
        if (Files.isDirectory(srcFile))
            throw new IOException("Source '" + srcFile + "' exists but is a directory");
        Path parentFile = destFile.getParent();
        Files.createDirectories(parentFile);
        if (Files.exists(destFile) && !Files.isWritable(destFile))
            throw new IOException("Destination '" + destFile + "' exists but is read-only");

        Files.copy(srcFile, destFile, StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING);
    }

    public static void moveFile(File srcFile, File destFile) throws IOException {
        copyFile(srcFile, destFile);
        srcFile.delete();
    }

    public static boolean makeDirectory(File directory) {
        directory.mkdirs();
        return directory.isDirectory();
    }

    public static boolean makeFile(File file) {
        return makeDirectory(file.getAbsoluteFile().getParentFile()) && (file.exists() || Lang.test(file::createNewFile));
    }

    public static List<File> listFilesByExtension(File file, String extension) {
        List<File> result = new LinkedList<>();
        File[] files = file.listFiles();
        if (files != null)
            for (File it : files)
                if (extension.equals(getExtension(it)))
                    result.add(it);
        return result;
    }

    /**
     * Tests whether the file is convertible to [java.nio.file.Path] or not.
     *
     * @param file the file to be tested
     * @return true if the file is convertible to Path.
     */
    public static boolean isValidPath(File file) {
        try {
            file.toPath();
            return true;
        } catch (InvalidPathException ignored) {
            return false;
        }
    }
}
