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
package org.jackhuang.hmcl.util

import java.util.zip.ZipEntry
import java.io.File
import java.io.IOException
import java.util.zip.ZipOutputStream
import java.util.zip.ZipInputStream



@Throws(IOException::class)
fun zip(src: String, destZip: String) {
    zip(File(src), File(destZip), null)
}

/**
 * 功能：把 src 目录下的所有文件进行 zip 格式的压缩，保存为指定 zip 文件

 * @param src        源文件夹
 * *
 * @param destZip          压缩生成的zip文件路径。
 * *
 * @param pathNameCallback callback(pathName, isDirectory) returns your
 * *                         modified pathName
 * *
 * *
 * @throws java.io.IOException 压缩失败或无法读取
 */
@Throws(IOException::class)
fun zip(src: File, destZip: File, pathNameCallback: ((String, Boolean) -> String?)?) {
    ZipOutputStream(destZip.outputStream()).use { zos ->
        val basePath: String
        if (src.isDirectory)
            basePath = src.path
        else
        //直接压缩单个文件时，取父目录
            basePath = src.parent
        zipFile(src, basePath, zos, pathNameCallback)
        zos.closeEntry()
    }
}

/**
 * Zip file.

 * @param src           源文件夹
 * @param basePath         file directory that will be compressed
 * *
 * @param zos              zip文件的os
 * *
 * @param pathNameCallback callback(pathName, isDirectory) returns your
 * *                         modified pathName, null if you dont want this file zipped
 */
@Throws(IOException::class)
private fun zipFile(src: File,
                    basePath: String,
                    zos: ZipOutputStream,
                    pathNameCallback: ((String, Boolean) -> String?)?) {
    val files: Array<File>
    if (src.isDirectory)
        files = src.listFiles() ?: emptyArray()
    else {
        files = arrayOf(src)
    }
    var pathName: String? //存相对路径(相对于待压缩的根目录)
    val buf = ByteArray(1024)
    for (file in files)
        if (file.isDirectory) {
            pathName = file.path.substring(basePath.length + 1) + "/"
            if (pathNameCallback != null)
                pathName = pathNameCallback.invoke(pathName, true)
            if (pathName == null)
                continue
            zos.putNextEntry(ZipEntry(pathName))
            zipFile(file, basePath, zos, pathNameCallback)
        } else {
            pathName = file.path.substring(basePath.length + 1)
            if (pathNameCallback != null)
                pathName = pathNameCallback.invoke(pathName, true)
            if (pathName == null)
                continue
            file.inputStream().use { inputStream ->
                zos.putNextEntry(ZipEntry(pathName))
                inputStream.copyTo(zos, buf)
            }
        }
}

@Throws(IOException::class)
fun unzip(zip: File, dest: File) {
    unzip(zip, dest, null, true)
}

/**
 * 将文件压缩成zip文件

 * @param zip zip文件路径
 * *
 * @param dest    待压缩文件根目录
 * *
 * @param callback    will be called for every entry in the zip file,
 * *                    returns false if you dont want this file unzipped.
 * *
 * *
 * @throws java.io.IOException 解压失败或无法写入
 */
@Throws(IOException::class)
fun unzip(zip: File, dest: File, callback: ((String) -> Boolean)?, ignoreExistsFile: Boolean) {
    val buf = ByteArray(1024)
    dest.mkdirs()
    ZipInputStream(zip.inputStream()).use { zipFile ->
        if (zip.exists()) {
            var gbkPath: String
            var strtemp: String
            val strPath = dest.absolutePath
            var zipEnt: ZipEntry?
            while (true) {
                zipEnt = zipFile.nextEntry
                if (zipEnt == null)
                    break
                gbkPath = zipEnt.name
                if (callback != null)
                    if (!callback.invoke(gbkPath))
                        continue
                if (zipEnt.isDirectory) {
                    strtemp = strPath + File.separator + gbkPath
                    val dir = File(strtemp)
                    dir.mkdirs()
                } else {
                    //读写文件
                    gbkPath = zipEnt.name
                    strtemp = strPath + File.separator + gbkPath
                    //建目录
                    val strsubdir = gbkPath
                    for (i in 0..strsubdir.length - 1)
                        if (strsubdir.substring(i, i + 1).equals("/", ignoreCase = true)) {
                            val temp = strPath + File.separator + strsubdir.substring(0, i)
                            val subdir = File(temp)
                            if (!subdir.exists())
                                subdir.mkdir()
                        }
                    if (ignoreExistsFile && File(strtemp).exists())
                        continue
                    File(strtemp).outputStream().use({ fos ->
                        zipFile.copyTo(fos, buf)
                    })
                }
            }
        }
    }
}