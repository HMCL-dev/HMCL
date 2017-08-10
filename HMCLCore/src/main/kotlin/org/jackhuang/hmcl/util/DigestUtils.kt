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
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

/**
 * @author huangyuhui
 */
object DigestUtils {

    private val STREAM_BUFFER_LENGTH = 1024

    @Throws(IOException::class)
    private fun digest(digest: MessageDigest, data: InputStream): ByteArray {
        return updateDigest(digest, data).digest()
    }

    fun getDigest(algorithm: String): MessageDigest {
        try {
            return MessageDigest.getInstance(algorithm)
        } catch (e: NoSuchAlgorithmException) {
            throw IllegalArgumentException(e)
        }

    }

    val md2Digest: MessageDigest
        get() = getDigest("MD2")

    val md5Digest: MessageDigest
        get() = getDigest("MD5")

    val sha1Digest: MessageDigest
        get() = getDigest("SHA-1")

    val sha256Digest: MessageDigest
        get() = getDigest("SHA-256")

    val sha384Digest: MessageDigest
        get() = getDigest("SHA-384")

    val sha512Digest: MessageDigest
        get() = getDigest("SHA-512")

    fun md2(data: ByteArray): ByteArray {
        return md2Digest.digest(data)
    }

    @Throws(IOException::class)
    fun md2(data: InputStream): ByteArray {
        return digest(md2Digest, data)
    }

    fun md2(data: String): ByteArray {
        return md2(data.toByteArray(Charsets.UTF_8))
    }

    fun md2Hex(data: ByteArray): String {
        return Hex.encodeHexString(md2(data))
    }

    @Throws(IOException::class)
    fun md2Hex(data: InputStream): String {
        return Hex.encodeHexString(md2(data))
    }

    fun md2Hex(data: String): String {
        return Hex.encodeHexString(md2(data))
    }

    fun md5(data: ByteArray): ByteArray {
        return md5Digest.digest(data)
    }

    @Throws(IOException::class)
    fun md5(data: InputStream): ByteArray {
        return digest(md5Digest, data)
    }

    fun md5(data: String): ByteArray {
        return md5(data.toByteArray(Charsets.UTF_8))
    }

    fun md5Hex(data: ByteArray): String {
        return Hex.encodeHexString(md5(data))
    }

    @Throws(IOException::class)
    fun md5Hex(data: InputStream): String {
        return Hex.encodeHexString(md5(data))
    }

    fun md5Hex(data: String): String {
        return Hex.encodeHexString(md5(data))
    }

    fun sha1(data: ByteArray): ByteArray {
        return sha1Digest.digest(data)
    }

    @Throws(IOException::class)
    fun sha1(data: InputStream): ByteArray {
        return digest(sha1Digest, data)
    }

    fun sha1(data: String): ByteArray {
        return sha1(data.toByteArray(Charsets.UTF_8))
    }

    fun sha1Hex(data: ByteArray): String {
        return Hex.encodeHexString(sha1(data))
    }

    @Throws(IOException::class)
    fun sha1Hex(data: InputStream): String {
        return Hex.encodeHexString(sha1(data))
    }

    fun sha1Hex(data: String): String {
        return Hex.encodeHexString(sha1(data))
    }

    fun sha256(data: ByteArray): ByteArray {
        return sha256Digest.digest(data)
    }

    @Throws(IOException::class)
    fun sha256(data: InputStream): ByteArray {
        return digest(sha256Digest, data)
    }

    fun sha256(data: String): ByteArray {
        return sha256(data.toByteArray(Charsets.UTF_8))
    }

    fun sha256Hex(data: ByteArray): String {
        return Hex.encodeHexString(sha256(data))
    }

    @Throws(IOException::class)
    fun sha256Hex(data: InputStream): String {
        return Hex.encodeHexString(sha256(data))
    }

    fun sha256Hex(data: String): String {
        return Hex.encodeHexString(sha256(data))
    }

    fun sha384(data: ByteArray): ByteArray {
        return sha384Digest.digest(data)
    }

    @Throws(IOException::class)
    fun sha384(data: InputStream): ByteArray {
        return digest(sha384Digest, data)
    }

    fun sha384(data: String): ByteArray {
        return sha384(data.toByteArray(Charsets.UTF_8))
    }

    fun sha384Hex(data: ByteArray): String {
        return Hex.encodeHexString(sha384(data))
    }

    @Throws(IOException::class)
    fun sha384Hex(data: InputStream): String {
        return Hex.encodeHexString(sha384(data))
    }

    fun sha384Hex(data: String): String {
        return Hex.encodeHexString(sha384(data))
    }

    fun sha512(data: ByteArray): ByteArray {
        return sha512Digest.digest(data)
    }

    @Throws(IOException::class)
    fun sha512(data: InputStream): ByteArray {
        return digest(sha512Digest, data)
    }

    fun sha512(data: String): ByteArray {
        return sha512(data.toByteArray(Charsets.UTF_8))
    }

    fun sha512Hex(data: ByteArray): String {
        return Hex.encodeHexString(sha512(data))
    }

    @Throws(IOException::class)
    fun sha512Hex(data: InputStream): String {
        return Hex.encodeHexString(sha512(data))
    }

    fun sha512Hex(data: String): String {
        return Hex.encodeHexString(sha512(data))
    }

    fun updateDigest(messageDigest: MessageDigest, valueToDigest: ByteArray): MessageDigest {
        messageDigest.update(valueToDigest)
        return messageDigest
    }

    @Throws(IOException::class)
    fun updateDigest(digest: MessageDigest, data: InputStream): MessageDigest {
        val buffer = ByteArray(STREAM_BUFFER_LENGTH)
        var read = data.read(buffer, 0, STREAM_BUFFER_LENGTH)

        while (read > -1) {
            digest.update(buffer, 0, read)
            read = data.read(buffer, 0, STREAM_BUFFER_LENGTH)
        }

        return digest
    }

    fun updateDigest(messageDigest: MessageDigest, valueToDigest: String): MessageDigest {
        messageDigest.update(valueToDigest.toByteArray(Charsets.UTF_8))
        return messageDigest
    }
}
