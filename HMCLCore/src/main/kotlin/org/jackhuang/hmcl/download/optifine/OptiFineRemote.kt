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
package org.jackhuang.hmcl.download.optifine

import com.google.gson.annotations.SerializedName

data class OptiFineVersion @JvmOverloads constructor(
        @SerializedName("dl")
        val downloadLink: String? = null,
        @SerializedName("ver")
        val version: String? = null,
        @SerializedName("date")
        val date: String? = null,
        @SerializedName("type")
        val type: String? = null,
        @SerializedName("patch")
        val patch: String? = null,
        @SerializedName("mirror")
        val mirror: String? = null,
        @SerializedName("mcversion")
        val gameVersion: String? = null
)