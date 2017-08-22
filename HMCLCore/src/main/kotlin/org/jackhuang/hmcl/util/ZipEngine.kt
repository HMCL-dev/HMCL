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
import java.util.HashSet
import java.io.*
import java.util.zip.ZipOutputStream


/**
 * Non thread-safe
 *
 * @author huangyuhui
 */
class ZipEngine
    @Throws(IOException::class)
    constructor(f: File) : Closeable {

    val buf = ByteArray(1024)
    val zos: ZipOutputStream = ZipOutputStream(BufferedOutputStream(f.outputStream()))
    private val names = HashSet<String>()

    @Throws(IOException::class)
    override fun close() {
        zos.closeEntry()
        zos.close()
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
    @Throws(IOException::class)
    @JvmOverloads
    fun putDirectory(sourceDir: File, pathNameCallback: ((String, Boolean) -> String?)? = null) {
        putDirectoryImpl(sourceDir, if (sourceDir.isDirectory) sourceDir.path else sourceDir.parent, pathNameCallback)
    }

    /**
     * 将文件压缩成zip文件
     *
     * @param source zip文件路径
     * @param basePath 待压缩文件根目录
     * @param zos zip文件的os
     * @param pathNameCallback callback(pathName, isDirectory) returns your
     * modified pathName, null if you dont want this file zipped
     */
    @Throws(IOException::class)
    private fun putDirectoryImpl(source: File, basePath: String, pathNameCallback: ((String, Boolean) -> String?)?) {
        val files: Array<File>?
        if (source.isDirectory)
            files = source.listFiles()
        else
            files = arrayOf(source)
        if (files == null)
            return
        var pathName: String? //存相对路径(相对于待压缩的根目录)
        for (file in files)
            if (file.isDirectory) {
                pathName = file.path.substring(basePath.length + 1) + "/"
                pathName = pathName.replace('\\', '/')
                if (pathNameCallback != null)
                    pathName = pathNameCallback(pathName, true)
                if (pathName == null)
                    continue
                put(ZipEntry(pathName))
                putDirectoryImpl(file, basePath, pathNameCallback)
            } else {
                if (".DS_Store" == file.name) // For Mac computers.
                    continue
                pathName = file.path.substring(basePath.length + 1)
                pathName = pathName.replace('\\', '/')
                if (pathNameCallback != null)
                    pathName = pathNameCallback(pathName, false)
                if (pathName == null)
                    continue
                putFile(file, pathName)
            }
    }

    @Throws(IOException::class)
    fun putFile(file: File, pathName: String) =
        file.inputStream().use { putStream(it, pathName) }

    @Throws(IOException::class)
    fun putStream(inputStream: InputStream, pathName: String) {
        put(ZipEntry(pathName))
        inputStream.copyTo(zos, buf)
    }

    @Throws(IOException::class)
    fun putTextFile(text: String, pathName: String) =
        putTextFile(text, "UTF-8", pathName)

    @Throws(IOException::class)
    fun putTextFile(text: String, encoding: String, pathName: String) =
        putStream(ByteArrayInputStream(text.toByteArray(charset(encoding))), pathName)

    @Throws(IOException::class)
    fun put(entry: ZipEntry) {
        if (names.add(entry.name))
            zos.putNextEntry(entry)
    }

}
