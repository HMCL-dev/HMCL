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

import com.google.gson.annotations.SerializedName
import java.io.File
import java.lang.management.ManagementFactory
import java.nio.charset.Charset
import java.util.*

enum class OS {
    @SerializedName("windows")
    WINDOWS,
    @SerializedName("linux")
    LINUX,
    @SerializedName("osx")
    OSX,
    @SerializedName("unknown")
    UNKNOWN;

    companion object {
        val CURRENT_OS: OS by lazy {
            System.getProperty("os.name").toLowerCase(Locale.US).run {
                when {
                    contains("win") -> WINDOWS
                    contains("mac") -> OSX
                    contains("solaris") || contains("linux") || contains("unix") || contains("sunos") -> LINUX
                    else -> UNKNOWN
                }
            }
        }

        val TOTAL_MEMORY: Long by lazy {
            ReflectionHelper.get<Long>(ManagementFactory.getOperatingSystemMXBean(), "getTotalPhysicalMemorySize") ?: 1024L
        }

        val SUGGESTED_MEMORY: Long by lazy {
            val memory = TOTAL_MEMORY / 1024 / 1024 / 4
            Math.round(1.0 * memory / 128.0) * 128
        }

        val PATH_SEPARATOR: String = File.pathSeparator
        val FILE_SEPARATOR: String = File.separator
        val LINE_SEPARATOR: String by lazy(System::lineSeparator)
        val ENCODING: String by lazy {
            System.getProperty("sun.jnu.encoding", Charset.defaultCharset().name())
        }

        val SYSTEM_VERSION: String by lazy { System.getProperty("os.version") }
        val SYSTEM_ARCH: String by lazy { System.getProperty("os.arch") }
    }
}