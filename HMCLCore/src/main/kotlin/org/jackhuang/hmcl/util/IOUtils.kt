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

import java.io.*
import java.nio.charset.Charset

const val MAX_BUFFER_SIZE = 4096

fun Closeable.closeQuietly() {
    try {
        this.close()
    } catch (ex: IOException) {}
}

fun InputStream.readFully(): ByteArrayOutputStream {
    try {
        val ans = ByteArrayOutputStream()
        copyTo(ans)
        return ans
    } finally {
        this.closeQuietly()
    }
}

fun InputStream.readFullyAsByteArray(): ByteArray =
        readFully().toByteArray()

fun InputStream.readFullyAsString(): String =
        readFully().toString()

fun InputStream.readFullyAsString(charset: Charset): String =
        readFully().toString(charset.name())

fun InputStream.copyTo(dest: OutputStream, buf: ByteArray) {
    while (true) {
        val len = read(buf)
        if (len == -1)
            break
        dest.write(buf, 0, len)
    }
}

fun InputStream.copyToAndClose(dest: OutputStream) {
    this.use { input ->
        dest.use { output ->
            input.copyTo(output)
        }
    }
}

/**
 * @param cmd the command line
 * @return the final command line
 */
fun makeCommand(cmd: List<String>): String {
    val cmdbuf = StringBuilder(120)
    for (i in cmd.indices) {
        if (i > 0)
            cmdbuf.append(' ')
        val s = cmd[i]
        if (s.indexOf(' ') >= 0 || s.indexOf('\t') >= 0)
            if (s[0] != '"') {
                cmdbuf.append('"')
                cmdbuf.append(s)
                if (s.endsWith("\\"))
                    cmdbuf.append("\\")
                cmdbuf.append('"')
            } else if (s.endsWith("\""))
                // The argument has already been quoted.
                cmdbuf.append(s)
            else
                // Unmatched quote for the argument.
                throw IllegalArgumentException()
        else
            cmdbuf.append(s)
    }

    return cmdbuf.toString()
}