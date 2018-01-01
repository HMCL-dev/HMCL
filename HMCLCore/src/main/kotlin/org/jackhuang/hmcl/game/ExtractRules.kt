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
package org.jackhuang.hmcl.game

import com.google.gson.annotations.SerializedName
import java.util.*

class ExtractRules @JvmOverloads constructor(exclude: List<String> = emptyList()) {
    val exclude: List<String> get() = Collections.unmodifiableList(excludeImpl)

    @SerializedName("exclude")
    private val excludeImpl: MutableList<String> = LinkedList(exclude)

    fun shouldExtract(path: String): Boolean =
        exclude.none { path.startsWith(it) }

}