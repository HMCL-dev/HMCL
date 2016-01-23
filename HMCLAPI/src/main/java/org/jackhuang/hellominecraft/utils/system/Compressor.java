/*
 * Hello Minecraft! Launcher.
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
package org.jackhuang.hellominecraft.utils.system;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.jackhuang.hellominecraft.utils.functions.Predicate;
import java.util.zip.ZipInputStream;

/**
 * 文件压缩/解压类
 *
 * @author huangyuhui
 */
public class Compressor {

    public static void zip(String sourceDir, String zipFile) throws IOException {
        zip(new File(sourceDir), new File(zipFile));
    }

    /**
     * 功能：把 sourceDir 目录下的所有文件进行 zip 格式的压缩，保存为指定 zip 文件
     *
     * @param sourceDir 源文件夹
     * @param zipFile   压缩生成的zip文件路径。
     *
     * @throws java.io.IOException 压缩失败或无法读取
     */
    public static void zip(File sourceDir, File zipFile) throws IOException {
        FileOutputStream os;
        os = new FileOutputStream(zipFile);
        BufferedOutputStream bos = new BufferedOutputStream(os);
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            String basePath;
            if (sourceDir.isDirectory())
                basePath = sourceDir.getPath();
            else//直接压缩单个文件时，取父目录
                basePath = sourceDir.getParent();
            zipFile(sourceDir, basePath, zos);
            zos.closeEntry();
        }
    }

    /**
     * 将文件压缩成zip文件
     *
     * @param source   zip文件路径
     * @param basePath 待压缩文件根目录
     * @param zos      zip文件的os
     *
     * @param callback if the file is allowed to be zipped.
     */
    private static void zipFile(File source, String basePath,
                                ZipOutputStream zos) throws IOException {
        File[] files;
        if (source.isDirectory())
            files = source.listFiles();
        else {
            files = new File[1];
            files[0] = source;
        }
        String pathName;//存相对路径(相对于待压缩的根目录)
        byte[] buf = new byte[1024];
        int length;
        for (File file : files)
            if (file.isDirectory()) {
                pathName = file.getPath().substring(basePath.length() + 1)
                           + "/";
                zos.putNextEntry(new ZipEntry(pathName));
                zipFile(file, basePath, zos);
            } else {
                pathName = file.getPath().substring(basePath.length() + 1);
                try (InputStream is = new FileInputStream(file)) {
                    BufferedInputStream bis = new BufferedInputStream(is);
                    zos.putNextEntry(new ZipEntry(pathName));
                    while ((length = bis.read(buf)) > 0)
                        zos.write(buf, 0, length);
                }
            }
    }

    public static void unzip(String zipFileName, String extPlace) throws IOException {
        unzip(new File(zipFileName), new File(extPlace));
    }

    public static void unzip(File zipFileName, File extPlace) throws IOException {
        unzip(zipFileName, extPlace, null);
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
    public static void unzip(File zipFileName, File extPlace, Predicate<String> callback) throws IOException {
        extPlace.mkdirs();
        try (ZipInputStream zipFile = new ZipInputStream(new FileInputStream(zipFileName))) {
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
                        try (FileOutputStream fos = new FileOutputStream(strtemp); BufferedOutputStream bos = new BufferedOutputStream(fos)) {
                            int c;
                            while ((c = zipFile.read()) != -1)
                                bos.write((byte) c);
                        }
                    }
                }
            }
        }
    }

    /**
     * 将zip1合并到zip2里面，即保留zip2
     *
     * @param destFile zip1
     * @param srcFile  zip2
     *
     * @throws java.io.IOException 无法写入或读取
     *//*
     * public static void merge(File destFile, File srcFile) throws IOException
     * {
     * try (ZipOutputStream os = new ZipOutputStream(new
     * FileOutputStream(destFile))) {
     * if (destFile.exists()) {
     * File extPlace = new File(IOUtils.currentDir(), "HMCL-MERGE-TEMP");
     * unzip(srcFile, extPlace);
     * ZipFile zipFile = new ZipFile(srcFile);
     * if (srcFile.exists()) {
     * String gbkPath;//, strtemp, strPath;
     * //strPath = extPlace.getAbsolutePath();
     * java.util.Enumeration e = zipFile.entries();
     * while (e.hasMoreElements()) {
     * ZipEntry zipEnt = (ZipEntry) e.nextElement();
     * //gbkPath = zipEnt.getName();
     * if (zipEnt.isDirectory()) {
     * //strtemp = strPath + File.separator + gbkPath;
     * } else {
     * gbkPath = zipEnt.getName();
     * //strtemp = strPath + File.separator + gbkPath;
     * os.putNextEntry(zipEnt);
     * os.write(gbkPath.getBytes("UTF-8"));
     * }
     * }
     * }
     * }
     * os.closeEntry();
     * }
     * }
     */
}
