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
import javafx.scene.paint.Color
import java.util.regex.Pattern

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

/**
 *
 * @author huangyuhui
 */
enum class Log4jLevel constructor(val level: Int, val color: Color) {

    FATAL(1, Color.RED),
    ERROR(2, Color.RED),
    WARN(3, Color.ORANGE),
    INFO(4, Color.BLACK),
    DEBUG(5, Color.BLUE),
    TRACE(6, Color.BLUE),
    ALL(2147483647, Color.BLACK);

    fun lessOrEqual(level: Log4jLevel): Boolean {
        return this.level <= level.level
    }

    companion object {

        val MINECRAFT_LOGGER = Pattern.compile("\\[(?<timestamp>[0-9:]+)\\] \\[[^/]+/(?<level>[^\\]]+)\\]")
        val MINECRAFT_LOGGER_CATEGORY = Pattern.compile("\\[(?<timestamp>[0-9:]+)\\] \\[[^/]+/(?<level>[^\\]]+)\\] \\[(?<category>[^\\]]+)\\]")
        val JAVA_SYMBOL = "([a-zA-Z_$][a-zA-Z\\d_$]*\\.)+[a-zA-Z_$][a-zA-Z\\d_$]*"

        fun guessLevel(line: String): Log4jLevel? {
            var level: Log4jLevel? = null
            val m = MINECRAFT_LOGGER.matcher(line)
            if (m.find()) {
                // New style logs from log4j
                val levelStr = m.group("level")
                if (null != levelStr)
                    when (levelStr) {
                        "INFO" -> level = INFO
                        "WARN" -> level = WARN
                        "ERROR" -> level = ERROR
                        "FATAL" -> level = FATAL
                        "TRACE" -> level = TRACE
                        "DEBUG" -> level = DEBUG
                        else -> {
                        }
                    }
                val m2 = MINECRAFT_LOGGER_CATEGORY.matcher(line)
                if (m2.find()) {
                    val level2Str = m2.group("category")
                    if (null != level2Str)
                        when (level2Str) {
                            "STDOUT" -> level = INFO
                            "STDERR" -> level = ERROR
                        }
                }
            } else {
                if (line.contains("[INFO]") || line.contains("[CONFIG]") || line.contains("[FINE]")
                        || line.contains("[FINER]") || line.contains("[FINEST]"))
                    level = INFO
                if (line.contains("[SEVERE]") || line.contains("[STDERR]"))
                    level = ERROR
                if (line.contains("[WARNING]"))
                    level = WARN
                if (line.contains("[DEBUG]"))
                    level = DEBUG
            }
            return if (line.contains("overwriting existing")) FATAL else level

            /*if (line.contains("Exception in thread")
                || line.matches("\\s+at " + JAVA_SYMBOL)
                || line.matches("Caused by: " + JAVA_SYMBOL)
                || line.matches("([a-zA-Z_$][a-zA-Z\\d_$]*\\.)+[a-zA-Z_$]?[a-zA-Z\\d_$]*(Exception|Error|Throwable)")
                || line.matches("... \\d+ more$"))
            return ERROR;*/
        }

        fun isError(a: Log4jLevel?): Boolean {
            return a?.lessOrEqual(ERROR) ?: false
        }

        fun mergeLevel(a: Log4jLevel?, b: Log4jLevel?): Log4jLevel? {
            return if (a == null) b
                    else if (b == null) a
                    else if (a.level < b.level) a else b
        }
    }

}

fun guessLogLineError(log: String) = Log4jLevel.isError(Log4jLevel.guessLevel(log))