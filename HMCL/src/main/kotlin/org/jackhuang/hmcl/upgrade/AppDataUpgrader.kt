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
package org.jackhuang.hmcl.upgrade

import java.security.PrivilegedActionException
import java.io.IOException
import com.google.gson.JsonSyntaxException
import javafx.scene.control.Alert
import java.io.File
import java.net.URLClassLoader
import java.security.PrivilegedExceptionAction
import java.security.AccessController
import java.util.Arrays
import java.util.ArrayList
import java.util.jar.JarFile
import org.jackhuang.hmcl.Main
import java.util.HashMap
import org.jackhuang.hmcl.task.*
import java.util.logging.Level
import java.util.zip.GZIPInputStream
import java.util.jar.Pack200
import java.util.jar.JarOutputStream
import org.jackhuang.hmcl.util.*
import java.net.URISyntaxException
import org.jackhuang.hmcl.i18n
import org.jackhuang.hmcl.ui.alert
import org.jackhuang.hmcl.util.Constants.GSON
import org.jackhuang.hmcl.util.Logging.LOG
import org.jackhuang.hmcl.util.VersionNumber
import java.net.Proxy
import java.net.URI

class AppDataUpgrader : IUpgrader {

    @Throws(IOException::class, PrivilegedActionException::class)
    private fun launchNewerVersion(args: Array<String>, jar: File): Boolean {
        JarFile(jar).use { jarFile ->
            val mainClass = jarFile.manifest.mainAttributes.getValue("Main-Class")
            if (mainClass != null) {
                val al = ArrayList(Arrays.asList(*args))
                al.add("--noupdate")
                AccessController.doPrivileged(PrivilegedExceptionAction<Void> {
                    URLClassLoader(arrayOf(jar.toURI().toURL()),
                            URLClassLoader.getSystemClassLoader().parent).loadClass(mainClass)
                            .getMethod("main", Array<String>::class.java).invoke(null, *arrayOf<Any>(al.toTypedArray()))
                    null
                })
                return true
            }
        }
        return false
    }

    override fun parseArguments(nowVersion: VersionNumber, args: Array<String>) {
        val f = AppDataUpgraderPackGzTask.HMCL_VER_FILE
        if (!args.contains("--noupdate"))
            try {
                if (f.exists()) {
                    val m = GSON.fromJson(f.readText(), Map::class.java)
                    val s = m["ver"] as? String?
                    if (s != null && VersionNumber.asVersion(s.toString()) > nowVersion) {
                        val j = m["loc"] as? String?
                        if (j != null) {
                            val jar = File(j)
                            if (jar.exists() && launchNewerVersion(args, jar))
                                System.exit(0)
                        }
                    }
                }
            } catch (ex: JsonSyntaxException) {
                f.delete()
            } catch (t: IOException) {
                LOG.log(Level.SEVERE, "Failed to execute newer version application", t)
            } catch (t: PrivilegedActionException) {
                LOG.log(Level.SEVERE, "Failed to execute newer version application", t)
            }

    }

    override fun download(checker: UpdateChecker, versionNumber: VersionNumber) {
        val version = versionNumber as IntVersionNumber
        checker.requestDownloadLink().then {
            val map: Map<String, String>? = it["update_checker.request_download_link"]
            if (alert(Alert.AlertType.CONFIRMATION,  "Alert", i18n("update.newest_version") + version[0] + "." + version[1] + "." + version[2] + "\n"
                    + i18n("update.should_open_link")))
                if (map != null && map.containsKey("jar") && map["jar"]!!.isNotBlank())
                    try {
                        var hash: String? = null
                        if (map.containsKey("jarsha1"))
                            hash = map.get("jarsha1")
                        if (AppDataUpgraderJarTask(map["jar"]!!, version.toString(), hash!!).test()) {
                            ProcessBuilder(JavaVersion.fromCurrentEnvironment().binary.absolutePath, "-jar", AppDataUpgraderJarTask.getSelf(version.toString()).absolutePath).directory(File("").absoluteFile).start()
                            System.exit(0)
                        }
                    } catch (ex: IOException) {
                        LOG.log(Level.SEVERE, "Failed to create upgrader", ex)
                    }
                else if (map != null && map.containsKey("pack") && map["pack"]!!.isNotBlank())
                    try {
                        var hash: String? = null
                        if (map.containsKey("packsha1"))
                            hash = map["packsha1"]
                        if (AppDataUpgraderPackGzTask(map["pack"]!!, version.toString(), hash!!).test()) {
                            ProcessBuilder(JavaVersion.fromCurrentEnvironment().binary.absolutePath, "-jar", AppDataUpgraderPackGzTask.getSelf(version.toString()).absolutePath).directory(File("").absoluteFile).start()
                            System.exit(0)
                        }
                    } catch (ex: IOException) {
                        LOG.log(Level.SEVERE, "Failed to create upgrader", ex)
                    }
                else {
                    var url = URL_PUBLISH
                    if (map != null)
                        if (map.containsKey(OperatingSystem.CURRENT_OS.checkedName))
                            url = map.get(OperatingSystem.CURRENT_OS.checkedName)!!
                        else if (map.containsKey(OperatingSystem.UNKNOWN.checkedName))
                            url = map.get(OperatingSystem.UNKNOWN.checkedName)!!
                    try {
                        java.awt.Desktop.getDesktop().browse(URI(url))
                    } catch (e: URISyntaxException) {
                        LOG.log(Level.SEVERE, "Failed to browse uri: " + url, e)
                        OperatingSystem.setClipboard(url)
                    } catch (e: IOException) {
                        LOG.log(Level.SEVERE, "Failed to browse uri: " + url, e)
                        OperatingSystem.setClipboard(url)
                    }

                }
            null
        }.execute()
    }

    class AppDataUpgraderPackGzTask(downloadLink: String, private val newestVersion: String, private val expectedHash: String) : Task() {
        private val tempFile: File = File.createTempFile("hmcl", ".pack.gz")
        private val dependents = listOf(FileDownloadTask(downloadLink.toURL(), tempFile, Proxy.NO_PROXY, expectedHash))
        override fun getDependents() = dependents

        init {
            onDone() += { event -> if (event.isFailed) tempFile.delete() }
        }

        override fun execute() {
            val json = HashMap<String, String>()
            var f = getSelf(newestVersion)
            if (!FileUtils.makeDirectory(f.parentFile))
                throw IOException("Failed to make directories: " + f.parent)

            var i = 0
            while (f.exists()) {
                f = File(BASE_FOLDER, "HMCL-" + newestVersion + (if (i > 0) "-" + i else "") + ".jar")
                i++
            }
            if (!f.createNewFile())
                throw IOException("Failed to create new file: " + f)

            JarOutputStream(f.outputStream()).use { jos -> Pack200.newUnpacker().unpack(GZIPInputStream(tempFile.inputStream()), jos) }
            json.put("ver", newestVersion)
            json.put("loc", f.absolutePath)
            val result = GSON.toJson(json)
            HMCL_VER_FILE.writeText(result)
        }

        val info: String
            get() = "Upgrade"

        companion object {

            val BASE_FOLDER = Main.getWorkingDirectory("hmcl")
            val HMCL_VER_FILE = File(BASE_FOLDER, "hmclver.json")

            fun getSelf(ver: String): File {
                return File(BASE_FOLDER, "HMCL-$ver.jar")
            }
        }

    }

    class AppDataUpgraderJarTask(downloadLink: String, private val newestVersion: String, expectedHash: String) : Task() {
        private val tempFile = File.createTempFile("hmcl", ".jar")

        init {
            name = "Upgrade"
            onDone() += { event -> if (event.isFailed) tempFile.delete() }
        }

        private val dependents = listOf(FileDownloadTask(downloadLink.toURL(), tempFile, Proxy.NO_PROXY, expectedHash))
        override fun getDependents() = dependents

        override fun execute() {
            val json = HashMap<String, String>()
            val f = getSelf(newestVersion)
            tempFile.copyTo(f)
            json.put("ver", newestVersion)
            json.put("loc", f.absolutePath)
            val result = GSON.toJson(json)
            HMCL_VER_FILE.writeText(result)
        }

        companion object {
            val BASE_FOLDER = Main.getWorkingDirectory("hmcl")
            val HMCL_VER_FILE = File(BASE_FOLDER, "hmclver.json")

            fun getSelf(ver: String): File {
                return File(BASE_FOLDER, "HMCL-$ver.jar")
            }
        }

    }

    companion object {
        const val URL_PUBLISH = "http://www.mcbbs.net/thread-142335-1-1.html"
    }
}