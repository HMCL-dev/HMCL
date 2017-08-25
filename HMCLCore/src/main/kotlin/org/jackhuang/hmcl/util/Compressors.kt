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

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.apache.commons.compress.archivers.zip.ZipFile
import java.io.File
import java.io.IOException

/**
 * Compress the given directory to a zip file.
 *
 * @param src the source directory or a file.
 * @param destZip the location of dest zip file.
 * @param pathNameCallback callback(pathName, isDirectory) returns your modified pathName
 * @throws IOException
 */
@JvmOverloads
@Throws(IOException::class)
fun File.zipTo(destZip: File, pathNameCallback: ((String, Boolean) -> String?)? = null) {
    ZipArchiveOutputStream(destZip.outputStream()).use { zos ->
        val basePath: String
        if (this.isDirectory)
            basePath = this.path
        else
            //直接压缩单个文件时，取父目录
            basePath = this.parent
        zipFile(this, basePath, zos, pathNameCallback)
        zos.closeArchiveEntry()
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
@Throws(IOException::class)
private fun zipFile(src: File,
                    basePath: String,
                    zos: ZipArchiveOutputStream,
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
            zos.putArchiveEntry(ZipArchiveEntry(pathName))
            zipFile(file, basePath, zos, pathNameCallback)
        } else {
            pathName = file.path.substring(basePath.length + 1)
            if (pathNameCallback != null)
                pathName = pathNameCallback.invoke(pathName, true)
            if (pathName == null)
                continue
            file.inputStream().use { inputStream ->
                zos.putArchiveEntry(ZipArchiveEntry(pathName))
                inputStream.copyTo(zos, buf)
            }
        }
}

/**
 * Decompress the given zip file to a directory.
 *
 * @param dest the dest directory.
 * @param subDirectory the subdirectory of the zip file to be decompressed.
 * @param callback will be called for every entry in the zip file, returns false if you dont want this file being uncompressed.
 * @param ignoreExistentFile true if skip all existent files.
 * @param allowStoredEntriesWithDataDescriptor whether the zip stream will try to read STORED entries that use a data descriptor
 * @throws IOException
 */
@JvmOverloads
@Throws(IOException::class)
fun File.uncompressTo(dest: File, subDirectory: String = "", callback: ((String) -> Boolean)? = null, ignoreExistentFile: Boolean = true, allowStoredEntriesWithDataDescriptor: Boolean = false) {
    val buf = ByteArray(1024)
    dest.mkdirs()
    ZipArchiveInputStream(this.inputStream(), null, true, allowStoredEntriesWithDataDescriptor).use { zipFile ->
        if (this.exists()) {
            val strPath = dest.absolutePath
            while (true) {
                val zipEnt = zipFile.nextEntry ?: break
                var strtemp: String
                var gbkPath: String
                gbkPath = zipEnt.name
                if (!gbkPath.startsWith(subDirectory))
                    continue
                gbkPath = gbkPath.substring(subDirectory.length)
                if (gbkPath.startsWith("/") || gbkPath.startsWith("\\")) gbkPath = gbkPath.substring(1)
                strtemp = strPath + File.separator + gbkPath

                if (callback != null)
                    if (!callback.invoke(gbkPath))
                        continue

                if (zipEnt.isDirectory) {
                    val dir = File(strtemp)
                    dir.mkdirs()
                } else {
                    //建目录
                    val strsubdir = gbkPath
                    for (i in 0 until strsubdir.length)
                        if (strsubdir.substring(i, i + 1).equals("/", ignoreCase = true)) {
                            val temp = strPath + File.separator + strsubdir.substring(0, i)
                            val subdir = File(temp)
                            if (!subdir.exists())
                                subdir.mkdir()
                        }
                    if (ignoreExistentFile && File(strtemp).exists())
                        continue
                    File(strtemp).outputStream().use({ fos ->
                        zipFile.copyTo(fos, buf)
                    })
                }
            }
        }
    }
}

/**
 * Read the text content of a file in zip.
 *
 * @param f the zip file
 * @param name the location of the text in zip file, something like A/B/C/D.txt
 * @throws IOException if the file is not a valid zip file.
 * @return the content of given file.
 */
@Throws(IOException::class)
fun File.readTextZipEntry(name: String): String {
    ZipFile(this).use { zipFile ->
        val entry = zipFile.getEntry(name) ?: throw IOException("`$name` not found.")
        return zipFile.getInputStream(entry).readFullyAsString()
    }
}

/**
 * Read the text content of a file in zip.
 *
 * @param f the zip file
 * @param location the location of the text in zip file, something like A/B/C/D.txt
 * @return the content of given file.
 */
fun File.readTextZipEntryQuietly(location: String) =
    try {
        readTextZipEntry(location)
    } catch (e: IOException) {
        null
    }