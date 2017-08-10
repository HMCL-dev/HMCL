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
package org.jackhuang.hmcl.download

import org.jackhuang.hmcl.util.VersionNumber
import java.util.*
import kotlin.Comparator

data class RemoteVersion<T> (
        val gameVersion: String,
        val selfVersion: String,
        /**
         * The file of remote version, may be an installer or an universal jar.
         */
        val url: String,
        val tag: T
): Comparable<RemoteVersion<T>> {
    override fun hashCode(): Int {
        return selfVersion.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        return other is RemoteVersion<*> && Objects.equals(this.selfVersion, other.selfVersion)
    }

    override fun compareTo(other: RemoteVersion<T>): Int {
        return -selfVersion.compareTo(other.selfVersion)
    }

    companion object RemoteVersionComparator: Comparator<RemoteVersion<*>> {
        override fun compare(o1: RemoteVersion<*>, o2: RemoteVersion<*>): Int {
            return -VersionNumber.asVersion(o1.selfVersion).compareTo(VersionNumber.asVersion(o2.selfVersion))
        }
    }
}
