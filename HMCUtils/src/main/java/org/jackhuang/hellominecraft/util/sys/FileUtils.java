/*
 * Hello Minecraft!.
 * Copyright (C) 2013  huangyuhui <huanghongxun2008@126.com>
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
package org.jackhuang.hellominecraft.util.sys;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.jackhuang.hellominecraft.util.log.HMCLog;

/**
 *
 * @author huangyuhui
 */
public final class FileUtils {
    
    private FileUtils() {
    }
    
    public static boolean makeDirectory(File directory) {
        return directory.isDirectory() || directory.mkdirs();
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
        } catch (Exception e) {
            HMCLog.err("Failed to delete directory " + directory, e);
            return false;
        }
    }

    public static boolean cleanDirectoryQuietly(File directory) {
        try {
            cleanDirectory(directory);
            return true;
        } catch (Exception e) {
            HMCLog.err("Failed to clean directory " + directory, e);
            return false;
        }
    }

    public static void cleanDirectory(File directory)
        throws IOException {
        if (!directory.exists()) {
            if (!FileUtils.makeDirectory(directory))
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
        if (file.isDirectory())
            deleteDirectory(file);
        else {
            boolean filePresent = file.exists();
            if (!file.delete()) {
                if (!filePresent)
                    throw new FileNotFoundException("File does not exist: " + file);
                String message = "Unable to delete file: " + file;

                throw new IOException(message);
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

    public static void copyDirectory(File srcDir, File destDir)
        throws IOException {
        copyDirectory(srcDir, destDir, null);
    }

    public static void copyDirectory(File srcDir, File destDir, FileFilter filter)
        throws IOException {
        Objects.requireNonNull(srcDir, "Source must not be null");
        Objects.requireNonNull(destDir, "Destination must not be null");
        if (!srcDir.exists())
            throw new FileNotFoundException("Source '" + srcDir + "' does not exist");
        if (!srcDir.isDirectory())
            throw new IOException("Source '" + srcDir + "' exists but is not a directory");
        if (srcDir.getCanonicalPath().equals(destDir.getCanonicalPath()))
            throw new IOException("Source '" + srcDir + "' and destination '" + destDir + "' are the same");

        List<String> exclusionList = null;
        if (destDir.getCanonicalPath().startsWith(srcDir.getCanonicalPath())) {
            File[] srcFiles = filter == null ? srcDir.listFiles() : srcDir.listFiles(filter);
            if ((srcFiles != null) && (srcFiles.length > 0)) {
                exclusionList = new ArrayList<>(srcFiles.length);
                for (File srcFile : srcFiles) {
                    File copiedFile = new File(destDir, srcFile.getName());
                    exclusionList.add(copiedFile.getCanonicalPath());
                }
            }
        }
        doCopyDirectory(srcDir, destDir, filter, exclusionList);
    }

    private static void doCopyDirectory(File srcDir, File destDir, FileFilter filter, List<String> exclusionList)
        throws IOException {
        File[] srcFiles = filter == null ? srcDir.listFiles() : srcDir.listFiles(filter);
        if (srcFiles == null)
            throw new IOException("Failed to list contents of " + srcDir);
        if (destDir.exists()) {
            if (!destDir.isDirectory())
                throw new IOException("Destination '" + destDir + "' exists but is not a directory");
        } else if (!FileUtils.makeDirectory(destDir))
            throw new IOException("Destination '" + destDir + "' directory cannot be created");

        if (!destDir.canWrite())
            throw new IOException("Destination '" + destDir + "' cannot be written to");
        for (File srcFile : srcFiles) {
            File dstFile = new File(destDir, srcFile.getName());
            if ((exclusionList == null) || (!exclusionList.contains(srcFile.getCanonicalPath())))
                if (srcFile.isDirectory())
                    doCopyDirectory(srcFile, dstFile, filter, exclusionList);
                else
                    doCopyFile(srcFile, dstFile);
        }
        if (!destDir.setLastModified(srcDir.lastModified()))
            HMCLog.warn("Failed to set last modified date of dir: " + destDir);
    }

    public static String read(File file)
        throws IOException {
        return IOUtils.toString(openInputStream(file));
    }

    public static String readQuietly(File file) {
        try {
            return IOUtils.toString(openInputStream(file));
        } catch (IOException ex) {
            HMCLog.err("Failed to read file: " + file, ex);
            return null;
        }
    }

    public static String read(File file, String charset)
        throws IOException {
        return IOUtils.toString(openInputStream(file), charset);
    }

    public static void copyFileQuietly(File srcFile, File destFile) {
        try {
            copyFile(srcFile, destFile);
        } catch (IOException ex) {
            HMCLog.warn("Failed to copy file", ex);
        }
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
        doCopyFile(srcFile, destFile);
    }

    public static void doCopyFile(File srcFile, File destFile)
        throws IOException {
        Files.copy(srcFile.toPath(), destFile.toPath(), StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING);
    }

    public static int indexOfLastSeparator(String filename) {
        if (filename == null)
            return -1;
        int lastUnixPos = filename.lastIndexOf(47);
        int lastWindowsPos = filename.lastIndexOf(92);
        return Math.max(lastUnixPos, lastWindowsPos);
    }

    public static String getName(String filename) {
        if (filename == null)
            return null;
        int index = indexOfLastSeparator(filename);
        return filename.substring(index + 1);
    }

    /**
     * Get the file name without extensions.
     *
     * @param filename
     *
     * @return the file name without extensions
     */
    public static String getBaseName(String filename) {
        return removeExtension(getName(filename));
    }

    public static int indexOfExtension(String filename) {
        if (filename == null)
            return -1;
        int extensionPos = filename.lastIndexOf(46);
        int lastSeparator = indexOfLastSeparator(filename);
        return lastSeparator > extensionPos ? -1 : extensionPos;
    }

    public static String getExtension(String filename) {
        if (filename == null)
            return null;
        int index = indexOfExtension(filename);
        if (index == -1)
            return "";
        return filename.substring(index + 1);
    }

    public static String removeExtension(String filename) {
        if (filename == null)
            return null;
        int index = indexOfExtension(filename);
        if (index == -1)
            return filename;
        return filename.substring(0, index);
    }

    public static boolean writeQuietly(File file, String data) {
        try {
            FileUtils.write(file, data);
            return true;
        } catch (IOException e) {
            HMCLog.warn("Failed to write data to file: " + file, e);
            return false;
        }
    }

    public static void write(File file, String data)
        throws IOException {
        write(file, data, "UTF-8", false);
    }

    public static void write(File file, String data, String encoding)
        throws IOException {
        write(file, data, encoding, false);
    }

    public static void write(File file, String data, String encoding, boolean append)
        throws IOException {
        OutputStream out = null;
        try {
            out = openOutputStream(file, append);
            IOUtils.write(data, out, encoding);
        } finally {
            IOUtils.closeQuietly(out);
        }
    }

    public static FileInputStream openInputStream(File file)
        throws IOException {
        if (file.exists()) {
            if (file.isDirectory())
                throw new IOException("File '" + file + "' exists but is a directory");
            if (!file.canRead())
                throw new IOException("File '" + file + "' cannot be read");
        } else
            throw new FileNotFoundException("File '" + file + "' does not exist");
        return new FileInputStream(file);
    }

    public static FileOutputStream openOutputStream(File file)
        throws IOException {
        return openOutputStream(file, false);
    }

    public static FileOutputStream openOutputStream(File file, boolean append)
        throws IOException {
        if (file.exists()) {
            if (file.isDirectory())
                throw new IOException("File '" + file + "' exists but is a directory");
            if (!file.canWrite())
                throw new IOException("File '" + file + "' cannot be written to");
        } else {
            File parent = file.getParentFile();
            if (parent != null && !FileUtils.makeDirectory(parent))
                throw new IOException("Directory '" + parent + "' could not be created");
            if (!file.createNewFile())
                throw new IOException("File `" + file + "` cannot be created.");
        }

        return new FileOutputStream(file, append);
    }

    public static File[] searchSuffix(File dir, String suffix) {
        ArrayList<File> al = new ArrayList<>();
        File[] files = dir.listFiles();
        if (files == null)
            return new File[0];
        for (File f : files)
            if (f.getName().endsWith(suffix))
                al.add(f);
        return al.toArray(new File[al.size()]);
    }
}
