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

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.Proxy
import java.net.URL
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.*
import java.util.logging.Level
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

private val XTM = object : X509TrustManager {
    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}

    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}

    override fun getAcceptedIssuers() = arrayOfNulls<X509Certificate>(0)
}
private val HNV = HostnameVerifier { _, _ -> true }
@Volatile
private var INIT_HTTPS = false
fun initHttps() {
    if (INIT_HTTPS)
        return
    INIT_HTTPS = true
    System.setProperty("https.protocols", "SSLv3,TLSv1")
    try {
        val c = SSLContext.getInstance("SSL")
        c.init(null, arrayOf(XTM), SecureRandom())
        HttpsURLConnection.setDefaultSSLSocketFactory(c.socketFactory)
    } catch (e: GeneralSecurityException) {
    }
    HttpsURLConnection.setDefaultHostnameVerifier(HNV)
}

var DEFAULT_USER_AGENT: () -> String = { RandomUserAgent.randomUserAgent }

fun String.toURL() = URL(this)

fun URL.createConnection(proxy: Proxy): HttpURLConnection {
    initHttps()
    return openConnection(proxy).apply {
        doInput = true
        useCaches = false
        connectTimeout = 15000
        readTimeout = 15000
        addRequestProperty("User-Agent", DEFAULT_USER_AGENT())
    } as HttpURLConnection
}

fun URL.doGet() = openConnection().getInputStream().readFullyAsString()
fun URL.doGet(proxy: Proxy) = createConnection(proxy).getInputStream().readFullyAsString()

fun URL.doPost(post: Map<String, String>,
               contentType: String = "application/x-www-form-urlencoded",
               proxy: Proxy = Proxy.NO_PROXY): String {
    val sb = StringBuilder()
    post.entries.forEach { (key, value) ->
        sb.append(key).append('=').append(value).append('&')
    }
    if (sb.isNotEmpty())
        sb.deleteCharAt(sb.length - 1)
    return doPost(sb.toString(), contentType, proxy)
}

fun URL.doPost(post: String,
               contentType: String = "application/x-www-form-urlencoded",
               proxy: Proxy = Proxy.NO_PROXY): String {
    val bytes = post.toByteArray(Charsets.UTF_8)

    val con = createConnection(proxy)
    con.requestMethod = "POST"
    con.doOutput = true
    con.setRequestProperty("Content-Type", contentType + "; charset=utf-8")
    con.setRequestProperty("Content-Length", "" + bytes.size)
    var os: OutputStream? = null
    try {
        os = con.outputStream
        os?.write(bytes)
    } finally {
        os?.closeQuietly()
    }
    return con.readData()
}

fun HttpURLConnection.readData(): String {
    var input: InputStream? = null
    try {
        input = inputStream
        return input.readFullyAsString(Charsets.UTF_8)
    } catch (e: IOException) {
        input?.closeQuietly()
        input = errorStream
        return input?.readFullyAsString(Charsets.UTF_8) ?: throw e
    } finally {
        input?.closeQuietly()
    }
}

fun URL.detectFileNameQuietly(proxy: Proxy = Proxy.NO_PROXY): String {
    try {
        val conn = createConnection(proxy)
        conn.connect()
        if (conn.responseCode / 100 != 2)
            throw IOException("Response code ${conn.responseCode}")
        return conn.detectFileName()
    } catch (e: IOException) {
        LOG.log(Level.WARNING, "Cannot detect the file name of URL $this", e)
        return UUIDTypeAdapter.fromUUID(UUID.randomUUID())
    }
}

fun URL.detectFileName(proxy: Proxy = Proxy.NO_PROXY): String {
    val conn = createConnection(proxy)
    conn.connect()
    if (conn.responseCode / 100 != 2)
        throw IOException("Response code ${conn.responseCode}")
    return conn.detectFileName()
}

fun HttpURLConnection.detectFileName(): String {
    val disposition = getHeaderField("Content-Disposition")
    if (disposition == null || !disposition.contains("filename=")) {
        val u = url.toString()
        return u.substringAfterLast('/')
    } else
        return disposition.substringAfter("filename=").removeSurrounding("\"")
}