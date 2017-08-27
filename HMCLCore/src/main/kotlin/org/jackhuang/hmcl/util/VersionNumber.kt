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

import java.util.*

/**
 * The formatted version number represents a version string.
 */
abstract class VersionNumber: Comparable<VersionNumber> {
    companion object {
        @JvmStatic
        fun asIntVersionNumber(version: String): IntVersionNumber {
            if (version.count { it != '.' && (it < '0' || it > '9') } > 0 || version.contains("..") || version.trim().isBlank())
                throw IllegalArgumentException("The version $version is malformed, only dots and digits are allowed.")
            val s = version.split(".")
            var last = s.size - 1
            for (i in s.size - 1 downTo 0)
                if (s[i].toInt() == 0)
                    last = i
            val versions = ArrayList<Int>(last + 1)
            for (i in 0..last)
                versions.add(s[i].toInt())
            return IntVersionNumber(Collections.unmodifiableList(versions))
        }

        @JvmStatic
        fun asVersion(version: String): VersionNumber {
            try {
                return asIntVersionNumber(version)
            } catch (e: IllegalArgumentException) {
                return StringVersionNumber(version)
            }
        }
    }
}

/**
 * If a version string contains alphabets, a [StringVersionNumber] will be constructed.
 */
class StringVersionNumber internal constructor(val version: String): VersionNumber() {
    override fun compareTo(other: VersionNumber): Int {
        if (other !is StringVersionNumber) return 0
        return version.compareTo(other.version)
    }

    override fun hashCode() = version.hashCode()

    override fun toString() = version

    override fun equals(other: Any?): Boolean {
        val other1 = other as? VersionNumber ?: return false
        val other2 = other1 as? StringVersionNumber ?: return true
        return version == other2.version
    }
}

/**
 * If a version string formats x.x.x.x, a [IntVersionNumber] will be generated.
 */
class IntVersionNumber internal constructor(val version: List<Int>): VersionNumber() {

    operator fun get(index: Int) = version[index]

    override fun compareTo(other: VersionNumber): Int {
        if (other !is IntVersionNumber) return 0
        val len = minOf(this.version.size, other.version.size)
        for (i in 0 until len)
            if (version[i] != other.version[i])
                return version[i].compareTo(other.version[i])
        return this.version.size.compareTo(other.version.size)
    }

    override fun hashCode(): Int {
        var hash = 3
        for (v in version) hash = 83 * hash + v
        return hash
    }

    override fun toString(): String {
        val builder = StringBuilder()
        for (i in version.indices) builder.append(version[i]).append('.')
        if (builder.isNotEmpty())
            builder.deleteCharAt(builder.length - 1)
        return builder.toString()
    }

    override fun equals(other: Any?): Boolean {
        val other1 = other as? VersionNumber ?: return false
        val other2 = other1 as? IntVersionNumber ?: return true
        if (version.size != other2.version.size) return false
        for (i in 0 until version.size)
            if (version[i] != other2.version[i])
                return false
        return true
    }

}