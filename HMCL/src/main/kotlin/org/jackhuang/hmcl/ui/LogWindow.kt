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

import com.jfoenix.controls.JFXComboBox
import javafx.beans.Observable
import javafx.beans.binding.Bindings
import javafx.beans.property.SimpleIntegerProperty
import javafx.concurrent.Worker
import javafx.fxml.FXML
import javafx.scene.Scene
import javafx.scene.control.ToggleButton
import javafx.scene.image.Image
import javafx.scene.layout.StackPane
import javafx.scene.web.WebEngine
import javafx.scene.web.WebView
import javafx.stage.Stage
import org.jackhuang.hmcl.game.LauncherHelper
import org.jackhuang.hmcl.i18n
import org.jackhuang.hmcl.setting.Settings
import org.jackhuang.hmcl.util.Log4jLevel
import org.jackhuang.hmcl.util.inc
import org.jackhuang.hmcl.util.readFullyAsString
import org.w3c.dom.Document
import org.w3c.dom.Node
import java.util.concurrent.Callable

class LogWindow : Stage() {
    val fatalProperty = SimpleIntegerProperty(0)
    val errorProperty = SimpleIntegerProperty(0)
    val warnProperty = SimpleIntegerProperty(0)
    val infoProperty = SimpleIntegerProperty(0)
    val debugProperty = SimpleIntegerProperty(0)

    val impl = LogWindowImpl()

    init {
        scene = Scene(impl, 800.0, 480.0)
        scene.stylesheets.addAll(*stylesheets)
        title = i18n("logwindow.title")
        icons += Image("/assets/img/icon.png")
    }

    fun logLine(line: String, level: Log4jLevel) {
        impl.body.appendChild(impl.engine.document.createElement("div").apply {
            textContent = line
        })
        impl.engine.executeScript("checkNewLog(\"${level.name.toLowerCase()}\");scrollToBottom();")

        when (level) {
            Log4jLevel.FATAL -> fatalProperty.inc()
            Log4jLevel.ERROR -> errorProperty.inc()
            Log4jLevel.WARN -> warnProperty.inc()
            Log4jLevel.INFO -> infoProperty.inc()
            Log4jLevel.DEBUG -> debugProperty.inc()
            else -> {}
        }
    }

    inner class LogWindowImpl: StackPane() {
        @FXML lateinit var webView: WebView
        @FXML lateinit var btnFatals: ToggleButton
        @FXML lateinit var btnErrors: ToggleButton
        @FXML lateinit var btnWarns: ToggleButton
        @FXML lateinit var btnInfos: ToggleButton
        @FXML lateinit var btnDebugs: ToggleButton

        @FXML lateinit var cboLines: JFXComboBox<String>
        val engine: WebEngine
        lateinit var body: Node
        lateinit var document: Document

        init {
            loadFXML("/assets/fxml/log.fxml")

            engine = webView.engine
            engine.loadContent(javaClass.getResourceAsStream("/assets/log-window-content.html").readFullyAsString().replace("\${FONT}", "${Settings.font.size}px \"${Settings.font.family}\""))
            engine.loadWorker.stateProperty().addListener { _, _, newValue ->
                if (newValue == Worker.State.SUCCEEDED) {
                    document = engine.document
                    body = document.getElementsByTagName("body").item(0)
                    engine.executeScript("limitedLogs=${Settings.logLines};")
                }
            }

            var flag = false
            for (i in cboLines.items) {
                if (i == Settings.logLines.toString()) {
                    cboLines.selectionModel.select(i)
                    flag = true
                }
            }
            cboLines.selectionModel.selectedItemProperty().addListener { _, _, newValue ->
                Settings.logLines = newValue.toInt()
                engine.executeScript("limitedLogs=${Settings.logLines};")
            }
            if (!flag) {
                cboLines.selectionModel.select(0)
            }

            btnFatals.textProperty().bind(Bindings.createStringBinding(Callable { fatalProperty.get().toString() + " fatals" }, fatalProperty))
            btnErrors.textProperty().bind(Bindings.createStringBinding(Callable { errorProperty.get().toString() + " errors" }, errorProperty))
            btnWarns.textProperty().bind(Bindings.createStringBinding(Callable { warnProperty.get().toString() + " warns" }, warnProperty))
            btnInfos.textProperty().bind(Bindings.createStringBinding(Callable { infoProperty.get().toString() + " infos" }, infoProperty))
            btnDebugs.textProperty().bind(Bindings.createStringBinding(Callable { debugProperty.get().toString() + " debugs" }, debugProperty))

            btnFatals.selectedProperty().addListener(this::specificChanged)
            btnErrors.selectedProperty().addListener(this::specificChanged)
            btnWarns.selectedProperty().addListener(this::specificChanged)
            btnInfos.selectedProperty().addListener(this::specificChanged)
            btnDebugs.selectedProperty().addListener(this::specificChanged)
        }

        private fun specificChanged(observable: Observable) {
            var res = ""
            if (btnFatals.isSelected) res += "\"fatal\", "
            if (btnErrors.isSelected) res += "\"error\", "
            if (btnWarns.isSelected) res += "\"warn\", "
            if (btnInfos.isSelected) res += "\"info\", "
            if (btnDebugs.isSelected) res += "\"debug\", "
            if (res.isNotBlank())
                res = res.substringBeforeLast(", ")
            engine.executeScript("specific([$res])")
        }

        fun onTerminateGame() {
            LauncherHelper.stopManagedProcesses()
        }

        fun onClear() {
            engine.executeScript("clear()")
        }
    }
}