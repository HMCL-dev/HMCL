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

import java.io.File

fun File.makeDirectory(): Boolean = isDirectory || mkdirs()

fun File.makeFile(): Boolean {
    if (!absoluteFile.parentFile.makeDirectory())
        return false
    if (!exists() && !createNewFile())
        return false
    return true
}

fun File.isSymlink(): Boolean {
    if (File.separatorChar == '\\')
        return false
    val fileInCanonicalDir: File =
            if (parent == null) this
            else File(parentFile.canonicalFile, name)
    return fileInCanonicalDir.canonicalFile != fileInCanonicalDir.absoluteFile
}

fun File.listFilesByExtension(ext: String): List<File> {
    val list = mutableListOf<File>()
    this.listFiles()?.filter { it.extension == ext }?.forEach { list.add(it) }
    return list
}