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
package org.jackhuang.hmcl.game

import org.jackhuang.hmcl.util.closeQuietly
import org.jackhuang.hmcl.util.readFullyAsByteArray
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

private fun lessThan32(b: ByteArray, startIndex: Int): Int {
    for (i in startIndex until b.size)
        if (b[i] < 32)
            return i
    return -1
}

fun matchArray(a: ByteArray, b: ByteArray): Int {
    for (i in 0..a.size - b.size - 1) {
        var j = 1
        for (k in b.indices) {
            if (b[k] == a[i + k])
                continue
            j = 0
            break
        }
        if (j != 0)
            return i
    }
    return -1
}

@Throws(IOException::class)
private fun getVersionOfOldMinecraft(file: ZipFile, entry: ZipEntry): String? {
    val tmp = file.getInputStream(entry).readFullyAsByteArray()

    val bytes = "Minecraft Minecraft ".toByteArray(Charsets.US_ASCII)
    var j = matchArray(tmp, bytes)
    if (j < 0) {
        return null
    }
    val i = j + bytes.size
    j = lessThan32(tmp, i)

    if (j < 0) {
        return null
    }
    val ver = String(tmp, i, j - i, Charsets.US_ASCII)
    return ver
}

@Throws(IOException::class)
private fun getVersionOfNewMinecraft(file: ZipFile, entry: ZipEntry): String? {
    val tmp = file.getInputStream(entry).readFullyAsByteArray()

    var str = "-server.txt".toByteArray(charset("ASCII"))
    var j = matchArray(tmp, str)
    if (j < 0) {
        return null
    }
    var i = j + str.size
    i += 11
    j = lessThan32(tmp, i)
    if (j < 0) {
        return null
    }
    val result = String(tmp, i, j - i, Charsets.US_ASCII)

    val ch = result[0]
    // 1.8.1+
    if (ch < '0' || ch > '9') {
        str = "Can't keep up! Did the system time change, or is the server overloaded?".toByteArray(charset("ASCII"))
        j = matchArray(tmp, str)
        if (j < 0) {
            return null
        }
        i = -1
        while (j > 0) {
            if (tmp[j] in 48..57) {
                i = j
                break
            }
            j--
        }
        if (i == -1) {
            return null
        }
        var k = i
        if (tmp[i + 1] >= 'a'.toInt() && tmp[i + 1] <= 'z'.toInt())
            i++
        while (tmp[k] in 48..57 || tmp[k] == '-'.toByte() || tmp[k] == '.'.toByte() || tmp[k] >= 97 && tmp[k] <= 'z'.toByte())
            k--
        k++
        return String(tmp, k, i - k + 1, Charsets.US_ASCII)
    }
    return result
}

fun minecraftVersion(file: File?): String? {
    if (file == null || !file.isFile || !file.canRead()) {
        return null
    }
    var f: ZipFile? = null
    try {
        f = ZipFile(file)
        val minecraft = f
                .getEntry("net/minecraft/client/Minecraft.class")
        if (minecraft != null)
            return getVersionOfOldMinecraft(f, minecraft)
        val main = f.getEntry("net/minecraft/client/main/Main.class")
        val minecraftserver = f.getEntry("net/minecraft/server/MinecraftServer.class")
        if (main != null && minecraftserver != null)
            return getVersionOfNewMinecraft(f, minecraftserver)
        return null
    } catch (e: IOException) {
        return null
    } finally {
        f?.closeQuietly()
    }
}