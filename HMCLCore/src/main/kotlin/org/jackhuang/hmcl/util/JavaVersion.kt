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
import java.util.*
import java.util.regex.Pattern

/**
 * Represents a Java installation.
 */
data class JavaVersion internal constructor(
        @SerializedName("location")
        val binary: File,
        val longVersion: String,
        val platform: Platform) : Serializable
{
    /**
     * The major version of Java installation.
     *
     * @see JAVA_X
     * @see JAVA_9
     * @see JAVA_8
     * @see JAVA_7
     * @see JAVA_6
     * @see JAVA_5
     * @see UNKNOWN
     */
    val version = parseVersion(longVersion)

    companion object {
        private val regex = Pattern.compile("java version \"(?<version>[1-9]*\\.[1-9]*\\.[0-9]*(.*?))\"")

        val JAVAS: Map<String, JavaVersion>

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

        @Throws(IOException::class)
        fun fromExecutable(file: File): JavaVersion {
            var actualFile = file
            var platform = Platform.BIT_32
            var version: String? = null
            if (actualFile.nameWithoutExtension == "javaw") // javaw will not output version information
                actualFile = actualFile.absoluteFile.parentFile.resolve("java")
            try {
                val process = ProcessBuilder(actualFile.absolutePath, "-version").start()
                process.waitFor()
                process.errorStream.bufferedReader().forEachLine { line ->
                    val m = regex.matcher(line)
                    if (m.find())
                        version = m.group("version")
                    if (line.contains("64-Bit"))
                        platform = Platform.BIT_64
                }
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw IOException("Java process is interrupted", e)
            }
            val thisVersion = version ?: throw IOException("Java version not matched")
            val parsedVersion = parseVersion(thisVersion)
            if (parsedVersion == UNKNOWN)
                throw IOException("Java version '$thisVersion' can not be recognized")
            return JavaVersion(file, thisVersion, platform)
        }

        private fun fromExecutable(file: File, version: String) =
                JavaVersion (
                        binary = file,
                        longVersion = version,
                        platform = Platform.UNKNOWN
                )

        @Throws(IOException::class)
        fun fromJavaHome(home: File): JavaVersion {
            return fromExecutable(getJavaFile(home))
        }

        private fun fromJavaHome(home: File, version: String): JavaVersion {
            return fromExecutable(getJavaFile(home), version)
        }

        private fun getJavaFile(home: File): File {
            val path = home.resolve("bin")
            val javaw = path.resolve("javaw.exe")
            if (OS.CURRENT_OS === OS.WINDOWS && javaw.isFile)
                return javaw
            else
                return path.resolve("java")
        }

        private val currentJava: JavaVersion by lazy {
            JavaVersion(
                    binary = getJavaFile(File(System.getProperty("java.home"))),
                    longVersion = System.getProperty("java.version"),
                    platform = Platform.PLATFORM)
        }
        fun fromCurrentEnvironment() = currentJava

        init {
            val temp = mutableMapOf<String, JavaVersion>()
            (when (OS.CURRENT_OS) {
                OS.WINDOWS -> queryWindows()
                OS.OSX -> queryMacintosh()
                else -> emptyList<JavaVersion>() /* Cannot detect Java in linux. */
            }).forEach { javaVersion ->
                temp.put(javaVersion.longVersion, javaVersion)
            }
            JAVAS = temp
        }

        private fun queryMacintosh() = LinkedList<JavaVersion>().apply {
            val currentJRE = File("/Library/Internet Plug-Ins/JavaAppletPlugin.plugin/Contents/Home")
            if (currentJRE.exists())
                this += fromJavaHome(currentJRE)
            File("/Library/Java/JavaVirtualMachines/").listFiles()?.forEach { file ->
                this += fromJavaHome(file.resolve("Contents/Home"))
            }
        }

        private fun queryWindows() = LinkedList<JavaVersion>().apply {
            ignoreException { this += queryRegisterKey("HKEY_LOCAL_MACHINE\\SOFTWARE\\JavaSoft\\Java Runtime Environment\\") }
            ignoreException { this += queryRegisterKey("HKEY_LOCAL_MACHINE\\SOFTWARE\\JavaSoft\\Java Development Kit\\") }
        }

        private fun queryRegisterKey(location: String) = LinkedList<JavaVersion>().apply {
            querySubFolders(location).forEach { java ->
                val s = java.count { it == '.' }
                if (s > 1) {
                    val home = queryRegisterValue(java, "JavaHome")
                    if (home != null)
                        this += fromJavaHome(File(home))
                }
            }
        }

        private fun querySubFolders(location: String) = LinkedList<String>().apply {
            val cmd = arrayOf("cmd", "/c", "reg", "query", location)
            val process = Runtime.getRuntime().exec(cmd)
            process.waitFor()
            process.inputStream.bufferedReader().readLines().forEach { s ->
                if (s.startsWith(location) && s != location)
                    this += s
            }
        }

        private fun queryRegisterValue(location: String, name: String): String? {
            val cmd = arrayOf("cmd", "/c", "reg", "query", location, "/v", name)
            var last = false
            val process = Runtime.getRuntime().exec(cmd)
            process.waitFor()
            process.inputStream.bufferedReader().readLines().forEach { s ->
                if (s.isNotBlank()) {
                    if (last && s.trim().startsWith(name)) {
                        var begins = s.indexOf(name)
                        if (begins > 0) {
                            val s2 = s.substring(begins + name.length)
                            begins = s2.indexOf("REG_SZ")
                            if (begins > 0)
                                return s2.substring(begins + "REG_SZ".length).trim()
                        }
                    }
                    if (s.trim() == location)
                        last = true
                }
            }
            return null
        }
    }
}