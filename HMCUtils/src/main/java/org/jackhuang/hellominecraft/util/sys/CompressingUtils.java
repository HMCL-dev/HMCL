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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.jackhuang.hellominecraft.util.func.Predicate;
import java.util.zip.ZipInputStream;
import org.jackhuang.hellominecraft.util.func.BiFunction;

/**
 * 文件压缩/解压类
 *
 * @author huangyuhui
 */
public final class CompressingUtils {
    
    private CompressingUtils() {
    }

    public static void zip(String sourceDir, String zipFile) throws IOException {
        zip(new File(sourceDir), new File(zipFile), null);
    }

    /**
     * 功能：把 sourceDir 目录下的所有文件进行 zip 格式的压缩，保存为指定 zip 文件
     *
     * @param sourceDir        源文件夹
     * @param zipFile          压缩生成的zip文件路径。
     * @param pathNameCallback callback(pathName, isDirectory) returns your
     *                         modified pathName
     *
     * @throws java.io.IOException 压缩失败或无法读取
     */
    public static void zip(File sourceDir, File zipFile, BiFunction<String, Boolean, String> pathNameCallback) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(FileUtils.openOutputStream(zipFile))) {
            String basePath;
            if (sourceDir.isDirectory())
                basePath = sourceDir.getPath();
            else//直接压缩单个文件时，取父目录
                basePath = sourceDir.getParent();
            zipFile(sourceDir, basePath, zos, pathNameCallback);
            zos.closeEntry();
        }
    }

    /**
     * 将文件压缩成zip文件
     *
     * @param source           zip文件路径
     * @param basePath         待压缩文件根目录
     * @param zos              zip文件的os
     * @param pathNameCallback callback(pathName, isDirectory) returns your
     *                         modified pathName, null if you dont want this file zipped
     */
    private static void zipFile(File source, String basePath,
                                ZipOutputStream zos, BiFunction<String, Boolean, String> pathNameCallback) throws IOException {
        File[] files;
        if (source.isDirectory())
            files = source.listFiles();
        else {
            files = new File[1];
            files[0] = source;
        }
        String pathName;//存相对路径(相对于待压缩的根目录)
        byte[] buf = new byte[1024];
        for (File file : files)
            if (file.isDirectory()) {
                pathName = file.getPath().substring(basePath.length() + 1)
                           + "/";
                if (pathNameCallback != null)
                    pathName = pathNameCallback.apply(pathName, true);
                if (pathName == null)
                    continue;
                zos.putNextEntry(new ZipEntry(pathName));
                zipFile(file, basePath, zos, pathNameCallback);
            } else {
                pathName = file.getPath().substring(basePath.length() + 1);
                if (pathNameCallback != null)
                    pathName = pathNameCallback.apply(pathName, true);
                if (pathName == null)
                    continue;
                try (InputStream is = FileUtils.openInputStream(file)) {
                    zos.putNextEntry(new ZipEntry(pathName));
                    IOUtils.copyStream(is, zos, buf);
                }
            }
    }

    public static void unzip(File zipFileName, File extPlace) throws IOException {
        unzip(zipFileName, extPlace, null, true);
    }

    /**
     * 将文件压缩成zip文件
     *
     * @param zipFileName zip文件路径
     * @param extPlace    待压缩文件根目录
     * @param callback    will be called for every entry in the zip file,
     *                    returns false if you dont want this file unzipped.
     *
     * @throws java.io.IOException 解压失败或无法写入
     */
    public static void unzip(File zipFileName, File extPlace, Predicate<String> callback, boolean ignoreExistsFile) throws IOException {
        byte[] buf = new byte[1024];
        extPlace.mkdirs();
        try (ZipInputStream zipFile = new ZipInputStream(FileUtils.openInputStream(zipFileName))) {
            if (zipFileName.exists()) {
                String strPath, gbkPath, strtemp;
                strPath = extPlace.getAbsolutePath();
                ZipEntry zipEnt;
                while ((zipEnt = zipFile.getNextEntry()) != null) {
                    gbkPath = zipEnt.getName();
                    if (callback != null)
                        if (!callback.apply(gbkPath))
                            continue;
                    if (zipEnt.isDirectory()) {
                        strtemp = strPath + File.separator + gbkPath;
                        File dir = new File(strtemp);
                        dir.mkdirs();
                    } else {
                        //读写文件
                        gbkPath = zipEnt.getName();
                        strtemp = strPath + File.separator + gbkPath;
                        //建目录
                        String strsubdir = gbkPath;
                        for (int i = 0; i < strsubdir.length(); i++)
                            if (strsubdir.substring(i, i + 1).equalsIgnoreCase("/")) {
                                String temp = strPath + File.separator + strsubdir.substring(0, i);
                                File subdir = new File(temp);
                                if (!subdir.exists())
                                    subdir.mkdir();
                            }
                        if (ignoreExistsFile && new File(strtemp).exists())
                            continue;
                        try (FileOutputStream fos = FileUtils.openOutputStream(new File(strtemp))) {
                            IOUtils.copyStream(zipFile, fos, buf);
                        }
                    }
                }
            }
        }
    }
}
