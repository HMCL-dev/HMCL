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

import javafx.application.Platform
import java.awt.EventQueue
import java.nio.charset.Charset

val DEFAULT_LIBRARY_URL = "https://libraries.minecraft.net/"
val DEFAULT_VERSION_DOWNLOAD_URL = "http://s3.amazonaws.com/Minecraft.Download/versions/"
val DEFAULT_INDEX_URL = "http://s3.amazonaws.com/Minecraft.Download/indexes/"

val SWING_UI_THREAD_SCHEDULER = { runnable: () -> Unit ->
    if (EventQueue.isDispatchThread())
        runnable()
    else
        EventQueue.invokeLater(runnable)
}

val JAVAFX_UI_THREAD_SCHEDULER = { runnable: () -> Unit ->
    if (Platform.isFxApplicationThread())
        runnable()
    else
        Platform.runLater(runnable)
}

val UI_THREAD_SCHEDULER: (() -> Unit) -> Unit = { }

val DEFAULT_ENCODING = "UTF-8"

val DEFAULT_CHARSET = Charsets.UTF_8
val SYSTEM_CHARSET: Charset by lazy { charset(OS.ENCODING) }