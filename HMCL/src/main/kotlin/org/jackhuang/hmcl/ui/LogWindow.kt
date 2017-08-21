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
package org.jackhuang.hmcl.ui

import javafx.concurrent.Worker
import javafx.scene.Scene
import javafx.scene.layout.StackPane
import javafx.scene.web.WebEngine
import javafx.scene.web.WebView
import javafx.stage.Stage
import org.jackhuang.hmcl.i18n
import org.jackhuang.hmcl.setting.Settings
import org.jackhuang.hmcl.util.Log4jLevel
import org.jackhuang.hmcl.util.readFullyAsString
import org.w3c.dom.Document
import org.w3c.dom.Node

class LogWindow : Stage() {
    val contentPane = WebView()
    val rootPane = StackPane().apply {
        children.setAll(contentPane)
    }
    val engine: WebEngine
    lateinit var body: Node
    lateinit var document: Document

    init {
        scene = Scene(rootPane, 800.0, 480.0)
        title = i18n("logwindow.title")

        contentPane.onScroll
        engine = contentPane.engine
        engine.loadContent(javaClass.getResourceAsStream("/assets/log-window-content.html").readFullyAsString().replace("\${FONT}", "${Settings.font.size}px \"${Settings.font.family}\""))
        engine.loadWorker.stateProperty().addListener { _, _, newValue ->
            if (newValue == Worker.State.SUCCEEDED) {
                document = engine.document
                body = document.getElementsByTagName("body").item(0)
            }
        }

    }

    fun logLine(line: String, level: Log4jLevel) {
        body.appendChild(contentPane.engine.document.createElement("div").apply {
            setAttribute("style", "background-color: #${level.color.toString().substring(2)};")
            textContent = line
        })
        engine.executeScript("scrollToBottom()")
    }
}