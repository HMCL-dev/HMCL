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

import java.nio.charset.Charset

class Hex @JvmOverloads constructor(val charset: Charset = DEFAULT_CHARSET) {

    @Throws(Exception::class)
    fun decode(array: ByteArray): ByteArray {
        return decodeHex(String(array, charset).toCharArray())
    }

    @Throws(Exception::class)
    fun decode(`object`: Any): Any {
        try {
            val charArray = (`object` as? String)?.toCharArray() ?: `object` as CharArray
            return decodeHex(charArray)
        } catch (e: ClassCastException) {
            throw Exception(e.message, e)
        }

    }

    fun encode(array: ByteArray): ByteArray {
        return encodeHexString(array).toByteArray(charset)
    }

    @Throws(Exception::class)
    fun encode(`object`: Any): Any {
        try {
            val byteArray = (`object` as? String)?.toByteArray(charset) ?: `object` as ByteArray

            return encodeHex(byteArray)
        } catch (e: ClassCastException) {
            throw Exception(e.message, e)
        }

    }

    val charsetName: String
        get() = this.charset.name()

    override fun toString(): String {
        return super.toString() + "[charsetName=$charset]"
    }

    companion object {

        private val DIGITS_LOWER = charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f')

        private val DIGITS_UPPER = charArrayOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F')

        @Throws(Exception::class)
        fun decodeHex(data: CharArray): ByteArray {
            val len = data.size

            if (len and 0x1 != 0)
                throw Exception("Odd number of characters.")

            val out = ByteArray(len shr 1)

            var i = 0
            var j = 0
            while (j < len) {
                var f = toDigit(data[j], j) shl 4
                j++
                f = f or toDigit(data[j], j)
                j++
                out[i] = (f and 0xFF).toByte()
                i++
            }

            return out
        }

        @JvmOverloads fun encodeHex(data: ByteArray, toLowerCase: Boolean = true): CharArray {
            return encodeHex(data, if (toLowerCase) DIGITS_LOWER else DIGITS_UPPER)
        }

        protected fun encodeHex(data: ByteArray, toDigits: CharArray): CharArray {
            val l = data.size
            val out = CharArray(l shl 1)

            var i = 0
            var j = 0
            while (i < l) {
                out[j++] = toDigits[(0xF0 and data[i].toInt()).ushr(4)]
                out[j++] = toDigits[0xF and data[i].toInt()]
                i++
            }
            return out
        }

        fun encodeHexString(data: ByteArray): String {
            return String(encodeHex(data))
        }

        protected fun toDigit(ch: Char, index: Int): Int {
            val digit = Character.digit(ch, 16)
            if (digit == -1)
                throw IllegalArgumentException("Illegal hexadecimal character $ch at index $index")
            return digit
        }
    }
}