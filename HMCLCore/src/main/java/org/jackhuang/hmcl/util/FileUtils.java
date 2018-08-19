/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2018  huangyuhui <huanghongxun2008@126.com>
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
 * along with this program.  If not, see {http://www.gnu.org/licenses/}.
 */
package org.jackhuang.hmcl.util;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 *
 * @author huang
 */
public final class FileUtils {

    private FileUtils() {
    }

    public static String getNameWithoutExtension(File file) {
        return StringUtils.substringBeforeLast(file.getName(), '.');
    }

    public static String getExtension(File file) {
        return StringUtils.substringAfterLast(file.getName(), '.');
    }

    /**
     * This method is for normalizing ZipPath since Path.normalize of ZipFileSystem does not work properly.
     */
    public static String normalizePath(String path) {
        return StringUtils.addPrefix(StringUtils.removeSuffix(path, "/", "\\"), "/");
    }

    public static String readText(File file) throws IOException {
        return readText(file, UTF_8);
    }

    public static String readText(File file, Charset charset) throws IOException {
        return new String(readBytes(file), charset);
    }

    public static byte[] readBytes(File file) throws IOException {
        return Files.readAllBytes(file.toPath());
    }

    public static void writeText(File file, String text) throws IOException {
        writeText(file, text, UTF_8);
    }

    public static void writeText(File file, String text, Charset charset) throws IOException {
        writeBytes(file, text.getBytes(charset));
    }

    public static void writeBytes(File file, byte[] array) throws IOException {
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
        return Lang.test(() -> deleteDirectory(directory));
    }

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

    public static void moveFile(File srcFile, File destFile) throws IOException {
        copyFile(srcFile, destFile);
        srcFile.delete();
    }

    public static boolean makeDirectory(File directory) {
        return directory.isDirectory() || directory.mkdirs();
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

    public static File createTempFile() throws IOException {
        return createTempFile("tmp");
    }

    public static File createTempFile(String prefix) throws IOException {
        return createTempFile(prefix, null);
    }

    public static File createTempFile(String prefix, String suffix) throws IOException {
        return createTempFile(prefix, suffix, null);
    }

    public static File createTempFile(String prefix, String suffix, File directory) throws IOException {
        return File.createTempFile(prefix, suffix, directory);
    }
}
