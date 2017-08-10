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
package org.jackhuang.hmcl.task

import org.jackhuang.hmcl.util.LOG
import org.jackhuang.hmcl.util.createConnection
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.Proxy
import java.net.URL
import java.nio.charset.Charset

class GetTask @JvmOverloads constructor(val url: URL, val encoding: Charset = Charsets.UTF_8, private val retry: Int = 5, private val proxy: Proxy = Proxy.NO_PROXY): TaskResult<String>() {
    override val scheduler: Scheduler = Scheduler.IO_THREAD

    override fun execute() {
        var exception: IOException? = null
        for (time in 0 until retry) {
            if (time > 0)
                LOG.warning("Unable to finish downloading $url, retrying time: $time")
            try {
                updateProgress(0.0)
                val conn = url.createConnection(proxy)
                val input = conn.inputStream
                val baos = ByteArrayOutputStream()
                val buf = ByteArray(4096)
                val size = conn.contentLength
                var read = 0
                while (true) {
                    val len = input.read(buf)
                    if (len == -1)
                        break
                    read += len

                    baos.write(buf, 0, len)
                    updateProgress(read, size)

                    if (Thread.currentThread().isInterrupted)
                        return
                }

                result = baos.toString(encoding.name())
                return
            } catch (e: IOException) {
                exception = e
            }
        }
        if (exception != null)
            throw exception
    }
}