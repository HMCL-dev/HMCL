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
package org.jackhuang.hmcl.launch

import org.jackhuang.hmcl.task.Scheduler
import org.jackhuang.hmcl.util.Log4jLevel
import org.jackhuang.hmcl.util.OS
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler
import org.xml.sax.helpers.XMLReaderFactory
import java.io.InterruptedIOException
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

/**
 * This class is to parse log4j classic XML layout logging, since only vanilla Minecraft will enable this layout.
 * Also supports plain logs.
 */
internal class Log4jHandler(private val callback: (String, Log4jLevel) -> Unit) : Thread() {
    val reader = XMLReaderFactory.createXMLReader().apply {
        contentHandler = Log4jHandlerImpl()
    }
    private val outputStream = PipedOutputStream()
    private val inputStream = PipedInputStream(outputStream)
    private val interrupted = AtomicBoolean(false)

    override fun run() {
        name = "log4j-handler"
        newLine("<output>")
        try {
            reader.parse(InputSource(inputStream))
        } catch (e: InterruptedIOException) {
            // Game has been interrupted.
            interrupted.set(true)
        }
    }

    /**
     * Should be called to stop [Log4jHandler] manually.
     */
    fun onStopped() {
        if (interrupted.get())
            return
        Scheduler.NEW_THREAD.schedule {
            if (!interrupted.get()) {
                newLine("</output>")?.get()
                outputStream.close()
                join()
            }
        }!!.get()
    }

    /**
     * New XML line.
     */
    fun newLine(content: String) =
        Scheduler.COMPUTATION.schedule {
            outputStream.write((content + OS.LINE_SEPARATOR).replace("log4j:Event", "log4j_Event").replace("log4j:Message", "log4j_Message").replace("log4j:Throwable", "log4j_Throwable").toByteArray())
            outputStream.flush()
        }

    inner class Log4jHandlerImpl : DefaultHandler() {
        private val df = SimpleDateFormat("HH:mm:ss")

        var date = ""
        var thread = ""
        var logger = ""
        var message: StringBuilder? = null
        var l: Log4jLevel? = null
        var readingMessage = false

        override fun startElement(uri: String, localName: String, qName: String, attributes: Attributes) {
            when (localName) {
                "log4j_Event" -> {
                    message = StringBuilder()
                    val d = Date(attributes.getValue("timestamp").toLong())
                    date = df.format(d)
                    l = try {
                        Log4jLevel.valueOf(attributes.getValue("level"))
                    } catch (e: IllegalArgumentException) {
                        Log4jLevel.INFO
                    }

                    thread = attributes.getValue("thread")
                    logger = attributes.getValue("logger")
                    if ("STDERR" == logger)
                        l = Log4jLevel.ERROR
                }
                "log4j_Message" -> readingMessage = true
                "log4j_Throwable" -> {}
            }
        }

        override fun endElement(uri: String?, localName: String?, qName: String?) {
            when (localName) {
                "log4j_Event" -> callback("[" + date + "] [" + thread + "/" + l!!.name + "] [" + logger + "] " + message.toString(), l!!)
                "log4j_Message" -> readingMessage = false
            }
        }

        override fun characters(ch: CharArray?, start: Int, length: Int) {
            val line = String(ch!!, start, length)
            if (line.trim { it <= ' ' }.isEmpty()) return
            if (readingMessage)
                message!!.append(line).append(OS.LINE_SEPARATOR)
            else
                callback(line, Log4jLevel.guessLevel(line) ?: Log4jLevel.INFO)
        }
    }
}