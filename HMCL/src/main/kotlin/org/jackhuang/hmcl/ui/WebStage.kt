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

import javafx.scene.Scene
import javafx.scene.image.Image
import javafx.scene.web.WebView
import javafx.stage.Stage

class WebStage: Stage() {
    val webView = WebView()
    init {
        scene = Scene(webView, 800.0, 480.0)
        scene.stylesheets.addAll(*stylesheets)
        icons += Image("/assets/img/icon.png")
    }
}