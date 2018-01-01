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

import com.google.gson.JsonSyntaxException
import com.google.gson.annotations.SerializedName
import org.jackhuang.hmcl.util.Immutable
import org.jackhuang.hmcl.util.Validation

@Immutable
open class DownloadInfo @JvmOverloads constructor(
        @SerializedName("url")
        val url: String = "",
        @SerializedName("sha1")
        val sha1: String? = null,
        @SerializedName("size")
        val size: Int = 0
): Validation {
    override fun validate() {
        if (url.isBlank())
            throw JsonSyntaxException("DownloadInfo url can not be null")
    }
}

@Immutable
open class IdDownloadInfo @JvmOverloads constructor(
        url: String = "",
        sha1: String? = null,
        size: Int = 0,
        @SerializedName("id")
        val id: String = ""
): DownloadInfo(url, sha1, size) {
    override fun validate() {
        super.validate()

        if (id.isBlank())
            throw JsonSyntaxException("IdDownloadInfo id can not be null")
    }
}