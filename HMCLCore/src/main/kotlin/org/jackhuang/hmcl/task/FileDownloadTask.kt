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

import org.jackhuang.hmcl.event.EventManager
import org.jackhuang.hmcl.event.FailedEvent
import org.jackhuang.hmcl.util.*
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.math.BigInteger
import java.net.Proxy
import java.net.URL
import java.util.logging.Level

/**
 * A task that can download a file online.
 *
 * @param url the URL of remote file.
 * @param file the location that download to.
 * @param hash the SHA-1 hash code of remote file, null if the hash is unknown or it is no need to check the hash code.
 * @param retry the times for retrying if downloading fails.
 * @param proxy the proxy.
 *
 * @author huangyuhui
 */
class FileDownloadTask @JvmOverloads constructor(val url: URL, val file: File, val hash: String? = null, val retry: Int = 5, val proxy: Proxy = Proxy.NO_PROXY): Task() {
    override val scheduler: Scheduler = Scheduler.IO

    /**
     * Once downloading fails, this event will be fired to gain the substitute URL.
     */
    var onFailed = EventManager<FailedEvent<URL>>()

    private var rFile: RandomAccessFile? = null
    private var stream: InputStream? = null

    private fun closeFiles() {
        rFile?.closeQuietly()
        rFile = null
        stream?.closeQuietly()
        stream = null
    }

    override fun execute() {
        var currentURL = url
        LOG.finer("Downloading: $currentURL, to: $file")
        var exception: Exception? = null
        for (repeat in 0 until retry) {
            if (repeat > 0) {
                val event = FailedEvent(this, repeat, currentURL)
                onFailed(event)
                if (currentURL != event.newResult) {
                    LOG.fine("Switch from: $currentURL to: ${event.newResult}")
                    currentURL = event.newResult
                }
            }
            if (Thread.interrupted()) {
                Thread.currentThread().interrupt()
                break
            }

            var temp: File? = null

            try {
                updateProgress(0.0)

                val conn = url.createConnection(proxy)
                conn.connect()

                if (conn.responseCode / 100 != 2)
                    throw IOException("Server error, response code: ${conn.responseCode}")

                val contentLength = conn.contentLength
                if (contentLength < 1)
                    throw IOException("The content length is invalid")

                if (!file.absoluteFile.parentFile.makeDirectory())
                    throw IOException("Could not make directory: ${file.absoluteFile.parent}")

                temp = createTempFile("HMCLCore")
                rFile = RandomAccessFile(temp, "rw")

                val digest = DigestUtils.sha1Digest

                stream = conn.inputStream
                var lastDownloaded = 0
                var downloaded = 0
                var lastTime = System.currentTimeMillis()
                val buf = ByteArray(4096)
                while (true) {
                    if (Thread.interrupted()) {
                        Thread.currentThread().interrupt()
                        break
                    }

                    val read = stream!!.read(buf)
                    if (read == -1)
                        break

                    if (hash != null)
                        digest.update(buf, 0, read)

                    rFile!!.write(buf, 0, read)
                    downloaded += read

                    updateProgress(downloaded, contentLength)
                    val now = System.currentTimeMillis()
                    if (now - lastTime >= 1000L) {
                        updateMessage(((downloaded - lastDownloaded) / 1024).toString() + "KB/s")
                        lastDownloaded = downloaded
                        lastTime = now
                    }
                }

                closeFiles()

                if (Thread.interrupted()) {
                    temp.delete()
                    Thread.currentThread().interrupt()
                    break
                } else {
                    if (file.exists())
                        file.delete()
                    if (!file.absoluteFile.parentFile.makeDirectory())
                        throw IOException("Cannot make parent directory $file")
                    if (!temp.renameTo(file))
                        throw IOException("Cannot move temp file to $file")
                }

                check(downloaded == contentLength, { "Unexpected file size: $downloaded, expected: $contentLength" })

                if (hash != null) {
                    val hashCode = String.format("%1$040x", BigInteger(1, digest.digest()))
                    check(hash.equals(hashCode, ignoreCase = true), { "Unexpected hash code: $hashCode, expected: $hash" })
                }

                return
            } catch(e: Exception) {
                temp?.delete()
                exception = e
                LOG.log(Level.WARNING, "Unable to download file $currentURL", e)
            } finally {
                closeFiles()
            }
        }

        if (exception != null)
            throw exception
    }
}