/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2017  huangyuhui <huanghongxun2008@126.com>
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


import java.io.*;
import java.util.HashSet;
import java.util.function.BiFunction;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Non thread-safe
 *
 * @author huangyuhui
 */
public class ZipEngine implements Closeable {

    byte[] buf = new byte[IOUtils.DEFAULT_BUFFER_SIZE];
    ZipOutputStream zos;

    public ZipEngine(File f) throws IOException {
        zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(f)));
    }

    @Override
    public void close() throws IOException {
        zos.closeEntry();
        zos.close();
    }

    public void putDirectory(File sourceDir) throws IOException {
        putDirectory(sourceDir, null);
    }

    /**
     * 功能：把 sourceDir 目录下的所有文件进行 zip 格式的压缩，保存为指定 zip 文件
     *
     * @param sourceDir 源文件夹
     * @param pathNameCallback callback(pathName, isDirectory) returns your
     * modified pathName
     *
     * @throws java.io.IOException 压缩失败或无法读取
     */
    public void putDirectory(File sourceDir, BiFunction<String, Boolean, String> pathNameCallback) throws IOException {
        putDirectoryImpl(sourceDir, sourceDir.isDirectory() ? sourceDir.getPath() : sourceDir.getParent(), pathNameCallback);
    }

    /**
     * 将文件压缩成zip文件
     *
     * @param source zip文件路径
     * @param basePath 待压缩文件根目录
     * @param pathNameCallback callback(pathName, isDirectory) returns your
     * modified pathName, null if you dont want this file zipped
     */
    private void putDirectoryImpl(File source, String basePath, BiFunction<String, Boolean, String> pathNameCallback) throws IOException {
        File[] files;
        if (source.isDirectory())
            files = source.listFiles();
        else
            files = new File[] { source };

        if (files == null)
            return;
        String pathName;//存相对路径(相对于待压缩的根目录)
        for (File file : files)
            if (file.isDirectory()) {
                pathName = file.getPath().substring(basePath.length() + 1)
                        + "/";
                pathName = pathName.replace('\\', '/');
                if (pathNameCallback != null)
                    pathName = pathNameCallback.apply(pathName, true);
                if (pathName == null)
                    continue;
                put(new ZipEntry(pathName));
                putDirectoryImpl(file, basePath, pathNameCallback);
            } else {
                if (".DS_Store".equals(file.getName())) // For Mac computers.
                    continue;
                pathName = file.getPath().substring(basePath.length() + 1);
                pathName = pathName.replace('\\', '/');
                if (pathNameCallback != null)
                    pathName = pathNameCallback.apply(pathName, false);
                if (pathName == null)
                    continue;
                putFile(file, pathName);
            }
    }

    public void putFile(File file, String pathName) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            putStream(fis, pathName);
        }
    }

    public void putStream(InputStream is, String pathName) throws IOException {
        put(new ZipEntry(pathName));
        IOUtils.copyTo(is, zos, buf);
    }

    public void putTextFile(String text, String pathName) throws IOException {
        putTextFile(text, "UTF-8", pathName);
    }

    public void putTextFile(String text, String encoding, String pathName) throws IOException {
        putStream(new ByteArrayInputStream(text.getBytes(encoding)), pathName);
    }

    protected HashSet<String> names = new HashSet<>();

    public void put(ZipEntry entry) throws IOException {
        if (names.add(entry.getName()))
            zos.putNextEntry(entry);
    }

}
