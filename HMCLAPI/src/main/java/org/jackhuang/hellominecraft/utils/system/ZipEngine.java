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
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.jackhuang.hellominecraft.utils.functions.BiFunction;

/**
 * Non thread-safe
 *
 * @author huangyuhui
 */
public class ZipEngine {

    byte[] buf = new byte[1024];
    ZipOutputStream zos;

    public ZipEngine(File f) throws IOException {
        FileOutputStream os = new FileOutputStream(f);
        zos = new ZipOutputStream(new BufferedOutputStream(os));
    }

    public void closeFile() throws IOException {
        zos.closeEntry();
        zos.close();
    }

    public void putDirectory(String sourceDir) throws IOException {
        putDirectory(new File(sourceDir), null);
    }

    public void putDirectory(File sourceDir) throws IOException {
        putDirectory(sourceDir, null);
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
    public void putDirectory(File sourceDir, BiFunction<String, Boolean, String> pathNameCallback) throws IOException {
        putDirectoryImpl(sourceDir, sourceDir.isDirectory() ? sourceDir.getPath() : sourceDir.getParent(), pathNameCallback);
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
    private void putDirectoryImpl(File source, String basePath, BiFunction<String, Boolean, String> pathNameCallback) throws IOException {
        File[] files;
        if (source.isDirectory())
            files = source.listFiles();
        else {
            files = new File[1];
            files[0] = source;
        }
        String pathName;//存相对路径(相对于待压缩的根目录)
        for (File file : files)
            if (file.isDirectory()) {
                pathName = file.getPath().substring(basePath.length() + 1)
                           + "/";
                if (pathNameCallback != null)
                    pathName = pathNameCallback.apply(pathName, true);
                if (pathName == null)
                    continue;
                zos.putNextEntry(new ZipEntry(pathName));
                putDirectoryImpl(file, basePath, pathNameCallback);
            } else {
                pathName = file.getPath().substring(basePath.length() + 1);
                if (pathNameCallback != null)
                    pathName = pathNameCallback.apply(pathName, false);
                if (pathName == null)
                    continue;
                putFile(file, pathName);
            }
    }

    public void putFile(File file, String pathName) throws IOException {
        putStream(new FileInputStream(file), pathName);
    }

    public void putStream(InputStream is, String pathName) throws IOException {
        int length;
        BufferedInputStream bis = new BufferedInputStream(is);
        zos.putNextEntry(new ZipEntry(pathName));
        while ((length = bis.read(buf)) > 0)
            zos.write(buf, 0, length);
    }

    public void putTextFile(String text, String pathName) throws IOException {
        putTextFile(text, "UTF-8", pathName);
    }

    public void putTextFile(String text, String encoding, String pathName) throws IOException {
        putStream(new ByteArrayInputStream(text.getBytes(encoding)), pathName);
    }

}
