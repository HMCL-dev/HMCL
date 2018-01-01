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
package org.jackhuang.hmcl.download.optifine

import org.jackhuang.hmcl.download.DownloadProvider
import org.jackhuang.hmcl.download.RemoteVersion
import org.jackhuang.hmcl.download.VersionList
import org.jackhuang.hmcl.task.GetTask
import org.jackhuang.hmcl.task.Task
import org.jackhuang.hmcl.util.toURL
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.util.regex.Pattern
import javax.xml.parsers.DocumentBuilderFactory

object OptiFineVersionList : VersionList<Unit>() {
    private val pattern = Pattern.compile("OptiFine (.*?) ")

    override fun refreshAsync(downloadProvider: DownloadProvider): Task {
        return RefreshTask(downloadProvider)
    }

    private class RefreshTask(val downloadProvider: DownloadProvider) : Task() {
        val task = GetTask("http://optifine.net/downloads".toURL())
        override val dependents: Collection<Task> = listOf(task)
        override fun execute() {
            versions.clear()

            val html = task.result!!.replace("&nbsp;", " ").replace("&gt;", ">").replace("&lt;", "<").replace("<br>", "<br />")

            val factory = DocumentBuilderFactory.newInstance()
            val db = factory.newDocumentBuilder()
            val doc = db.parse(ByteArrayInputStream(html.toByteArray()))
            val r = doc.documentElement
            val tables = r.getElementsByTagName("table")
            for (i in 0 until tables.length) {
                val e = tables.item(i) as Element
                if ("downloadTable" == e.getAttribute("class")) {
                    val tr = e.getElementsByTagName("tr")
                    for (k in 0 until tr.length) {
                        val downloadLine = (tr.item(k) as Element).getElementsByTagName("td")
                        var url: String? = null
                        var version: String? = null
                        for (j in 0 until downloadLine.length) {
                            val td = downloadLine.item(j) as Element
                            if (td.getAttribute("class")?.startsWith("downloadLineMirror") ?: false)
                                url = (td.getElementsByTagName("a").item(0) as Element).getAttribute("href")
                            if (td.getAttribute("class")?.startsWith("downloadLineFile") ?: false)
                                version = td.textContent
                        }
                        val matcher = pattern.matcher(version)
                        var gameVersion: String? = null
                        while (matcher.find())
                            gameVersion = matcher.group(1)
                        if (gameVersion == null || version == null || url == null) continue
                        val remoteVersion = RemoteVersion(
                                gameVersion = gameVersion,
                                selfVersion = version,
                                url = url,
                                tag = Unit
                        )
                        versions.put(gameVersion, remoteVersion)
                    }
                }
            }

        }
    }

}