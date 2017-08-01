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
import java.io.IOException
import java.io.Serializable
import java.util.regex.Pattern

data class JavaVersion internal constructor(
        @SerializedName("location")
        val binary: File,
        val version: Int,
        val platform: Platform) : Serializable
{
    companion object {
        private val regex = Pattern.compile("java version \"(?<version>[1-9]*\\.[1-9]*\\.[0-9]*(.*?))\"")

        val UNKNOWN: Int = -1
        val JAVA_5: Int = 50
        val JAVA_6: Int = 60
        val JAVA_7: Int = 70
        val JAVA_8: Int = 80
        val JAVA_9: Int = 90
        val JAVA_X: Int = 100

        private fun parseVersion(version: String): Int {
            with(version) {
                if (startsWith("10") || startsWith("X")) return JAVA_X
                else if (contains("1.9.") || startsWith("9")) return JAVA_9
                else if (contains("1.8.")) return JAVA_8
                else if (contains("1.7.")) return JAVA_7
                else if (contains("1.6.")) return JAVA_6
                else if (contains("1.5.")) return JAVA_5
                else return UNKNOWN
            }
        }

        fun fromExecutable(file: File): JavaVersion {
            var platform = Platform.BIT_32
            var version: String? = null
            try {
                val process = ProcessBuilder(file.absolutePath, "-version").start()
                process.waitFor()
                process.inputStream.bufferedReader().forEachLine { line ->
                    val m = regex.matcher(line)
                    if (m.find())
                        version = m.group("version")
                    if (line.contains("64-Bit"))
                        platform = Platform.BIT_64
                }
            } catch (e: InterruptedException) {
                throw IOException("Java process is interrupted", e)
            }
            val thisVersion = version ?: throw IOException("Java version not matched")
            val parsedVersion = parseVersion(thisVersion)
            if (parsedVersion == UNKNOWN)
                throw IOException("Java version '$thisVersion' can not be recognized")
            return JavaVersion(file.parentFile, parsedVersion, platform)
        }

        fun getJavaFile(home: File): File {
            val path = home.resolve("bin")
            val javaw = path.resolve("javaw.exe")
            if (OS.CURRENT_OS === OS.WINDOWS && javaw.isFile)
                return javaw
            else
                return path.resolve("java")
        }

        fun fromCurrentEnvironment(): JavaVersion {
            return JavaVersion(
                    binary = getJavaFile(File(System.getProperty("java.home"))),
                    version = parseVersion(System.getProperty("java.version")),
                    platform = Platform.PLATFORM)
        }
    }
}