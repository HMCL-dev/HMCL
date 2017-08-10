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
@file:JvmName("HMCLog")
package org.jackhuang.hmcl.util

import java.io.ByteArrayOutputStream
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.*
import java.util.logging.*
import java.util.logging.Formatter

val LOG = Logger.getLogger("HMCL").apply {
    level = Level.FINER
    useParentHandlers = false
    addHandler(FileHandler("hmcl.log").apply {
        level = Level.FINER
        formatter = DefaultFormatter
    })
    addHandler(ConsoleHandler().apply {
        level = Level.FINER
        formatter = DefaultFormatter
    })
}

val DEFAULT_DATE_FORMAT = SimpleDateFormat("HH:mm:ss")

internal object DefaultFormatter : Formatter() {
    override fun format(record: LogRecord): String {
        var s: String = "[${DEFAULT_DATE_FORMAT.format(Date(record.millis))}] [${record.sourceClassName}.${record.sourceMethodName}/${record.level.name}] ${record.message}\n"
        val builder = ByteArrayOutputStream()
        if (record.thrown != null)
            PrintWriter(builder).use(record.thrown::printStackTrace)
        s += builder.toString()
        return s
    }

}