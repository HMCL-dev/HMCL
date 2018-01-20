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
package org.jackhuang.hmcl.util;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipFile;

import java.io.*;
import java.util.function.BiFunction;
import java.util.function.Predicate;

/**
 * Utilities of compressing
 *
 * @author huangyuhui
 */
public final class CompressingUtils {

    private CompressingUtils() {
    }

    /**
     * Compress the given directory to a zip file.
     *
     * @param sourceDir the source directory or a file.
     * @param zipFile the location of dest zip file.
     * @param pathNameCallback callback(pathName, isDirectory) returns your modified pathName
     * @throws IOException
     */
    public static void zip(File sourceDir, File zipFile, BiFunction<String, Boolean, String> pathNameCallback) throws IOException {
        try (ZipArchiveOutputStream zos = new ZipArchiveOutputStream(new FileOutputStream(zipFile))) {
            String basePath;
            if (sourceDir.isDirectory())
                basePath = sourceDir.getPath();
            else
                basePath = sourceDir.getParent();
            zipFile(sourceDir, basePath, zos, pathNameCallback);
            zos.closeArchiveEntry();
        }
    }

    /**
     * Zip file.
     *
     * @param src source directory to be compressed.
     * @param basePath the file directory to be compressed, if [src] is a file, this is the parent directory of [src]
     * @param zos the [ZipOutputStream] of dest zip file.
     * @param pathNameCallback callback(pathName, isDirectory) returns your modified pathName, null if you dont want this file zipped
     * @throws IOException
     */
    private static void zipFile(File src, String basePath,
            ZipArchiveOutputStream zos, BiFunction<String, Boolean, String> pathNameCallback) throws IOException {
        File[] files = src.isDirectory() ? src.listFiles() : new File[] { src };
        String pathName;// the relative path (relative to the root directory to be compressed)
        byte[] buf = new byte[IOUtils.DEFAULT_BUFFER_SIZE];
        for (File file : files)
            if (file.isDirectory()) {
                pathName = file.getPath().substring(basePath.length() + 1) + "/";
                if (pathNameCallback != null)
                    pathName = pathNameCallback.apply(pathName, true);
                if (pathName == null)
                    continue;
                zos.putArchiveEntry(new ZipArchiveEntry(pathName));
                zipFile(file, basePath, zos, pathNameCallback);
            } else {
                pathName = file.getPath().substring(basePath.length() + 1);
                if (pathNameCallback != null)
                    pathName = pathNameCallback.apply(pathName, true);
                if (pathName == null)
                    continue;
                try (InputStream is = new FileInputStream(file)) {
                    zos.putArchiveEntry(new ZipArchiveEntry(pathName));
                    IOUtils.copyTo(is, zos, buf);
                }
            }
    }

    /**
     * Decompress the given zip file to a directory.
     *
     * @param src the input zip file.
     * @param dest the dest directory.
     * @throws IOException
     */
    public static void unzip(File src, File dest) throws IOException {
        unzip(src, dest, "");
    }

    /**
     * Decompress the given zip file to a directory.
     *
     * @param src the input zip file.
     * @param dest the dest directory.
     * @param subDirectory the subdirectory of the zip file to be decompressed.
     * @throws IOException
     */
    public static void unzip(File src, File dest, String subDirectory) throws IOException {
        unzip(src, dest, subDirectory, null);
    }

    /**
     * Decompress the given zip file to a directory.
     *
     * @param src the input zip file.
     * @param dest the dest directory.
     * @param subDirectory the subdirectory of the zip file to be decompressed.
     * @param callback will be called for every entry in the zip file, returns false if you dont want this file being uncompressed.
     * @throws IOException
     */
    public static void unzip(File src, File dest, String subDirectory, Predicate<String> callback) throws IOException {
        unzip(src, dest, subDirectory, callback, true);
    }

    /**
     * Decompress the given zip file to a directory.
     *
     * @param src the input zip file.
     * @param dest the dest directory.
     * @param subDirectory the subdirectory of the zip file to be decompressed.
     * @param callback will be called for every entry in the zip file, returns false if you dont want this file being uncompressed.
     * @param ignoreExistentFile true if skip all existent files.
     * @throws IOException
     */
    public static void unzip(File src, File dest, String subDirectory, Predicate<String> callback, boolean ignoreExistentFile) throws IOException {
        unzip(src, dest, subDirectory, callback, ignoreExistentFile, false);
    }

    /**
     * Decompress the given zip file to a directory.
     *
     * @param src the input zip file.
     * @param dest the dest directory.
     * @param subDirectory the subdirectory of the zip file to be decompressed.
     * @param callback will be called for every entry in the zip file, returns false if you dont want this file being uncompressed.
     * @param ignoreExistentFile true if skip all existent files.
     * @param allowStoredEntriesWithDataDescriptor whether the zip stream will try to read STORED entries that use a data descriptor
     * @throws IOException
     */
    public static void unzip(File src, File dest, String subDirectory, Predicate<String> callback, boolean ignoreExistentFile, boolean allowStoredEntriesWithDataDescriptor) throws IOException {
        byte[] buf = new byte[IOUtils.DEFAULT_BUFFER_SIZE];
        dest.mkdirs();
        try (ZipArchiveInputStream zipFile = new ZipArchiveInputStream(new FileInputStream(src), null, true, allowStoredEntriesWithDataDescriptor)) {
            if (src.exists()) {
                String strPath, gbkPath, strtemp;
                strPath = dest.getAbsolutePath();
                ArchiveEntry zipEnt;
                while ((zipEnt = zipFile.getNextEntry()) != null) {
                    gbkPath = zipEnt.getName();

                    if (!gbkPath.startsWith(subDirectory))
                        continue;
                    gbkPath = gbkPath.substring(subDirectory.length());
                    if (gbkPath.startsWith("/") || gbkPath.startsWith("\\"))
                        gbkPath = gbkPath.substring(1);
                    strtemp = strPath + File.separator + gbkPath;

                    if (callback != null)
                        if (!callback.test(gbkPath))
                            continue;

                    if (zipEnt.isDirectory()) {
                        File dir = new File(strtemp);
                        dir.mkdirs();
                    } else {
                        // create directories
                        String strsubdir = gbkPath;
                        for (int i = 0; i < strsubdir.length(); i++)
                            if (strsubdir.substring(i, i + 1).equalsIgnoreCase("/")) {
                                String temp = strPath + File.separator + strsubdir.substring(0, i);
                                File subdir = new File(temp);
                                if (!subdir.exists())
                                    subdir.mkdir();
                            }
                        if (ignoreExistentFile && new File(strtemp).exists())
                            continue;
                        try (FileOutputStream fos = new FileOutputStream(new File(strtemp))) {
                            IOUtils.copyTo(zipFile, fos, buf);
                        }
                    }
                }
            }
        }
    }

    /**
     * Read the text content of a file in zip.
     *
     * @param file the zip file
     * @param name the location of the text in zip file, something like A/B/C/D.txt
     * @throws IOException if the file is not a valid zip file.
     * @return the content of given file.
     */
    public static String readTextZipEntry(File file, String name) throws IOException {
        try (ZipFile zipFile = new ZipFile(file)) {
            ZipArchiveEntry entry = zipFile.getEntry(name);
            if (entry == null)
                throw new IOException("ZipEntry `" + name + "` not found in " + file);
            return IOUtils.readFullyAsString(zipFile.getInputStream(entry));
        }
    }

    /**
     * Read the text content of a file in zip.
     *
     * @param file the zip file
     * @param name the location of the text in zip file, something like A/B/C/D.txt
     * @return the content of given file.
     */
    public static String readTextZipEntryQuietly(File file, String name) {
        try {
            return readTextZipEntry(file, name);
        } catch (IOException e) {
            return null;
        }
    }
}
