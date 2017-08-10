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

import com.google.gson.annotations.SerializedName
import org.jackhuang.hmcl.game.Version
import org.jackhuang.hmcl.util.Immutable
import org.jackhuang.hmcl.util.Validation

@Immutable
internal class ForgeVersion (
        val branch: String? = null,
        val mcversion: String? = null,
        val jobver: String? = null,
        val version: String? = null,
        val build: Int = 0,
        val modified: Double = 0.0,
        val files: Array<Array<String>>? = null
) : Validation {
    override fun validate() {
        check(files != null, { "ForgeVersion files cannot be null" })
        check(version != null, { "ForgeVersion version cannot be null" })
        check(mcversion != null, { "ForgeVersion mcversion cannot be null" })
    }
}

@Immutable
internal class ForgeVersionRoot (
        val artifact: String? = null,
        val webpath: String? = null,
        val adfly: String? = null,
        val homepage: String? = null,
        val name: String? = null,
        val branches: Map<String, Array<Int>>? = null,
        val mcversion: Map<String, Array<Int>>? = null,
        val promos: Map<String, Int>? = null,
        val number: Map<Int, ForgeVersion>? = null
) : Validation {
    override fun validate() {
        check(number != null, { "ForgeVersionRoot number cannot be null" })
        check(mcversion != null, { "ForgeVersionRoot mcversion cannot be null" })
    }
}

@Immutable
internal data class Install (
        val profileName: String? = null,
        val target: String? = null,
        val path: String? = null,
        val version: String? = null,
        val filePath: String? = null,
        val welcome: String? = null,
        val minecraft: String? = null,
        val mirrorList: String? = null,
        val logo: String? = null
)

@Immutable
internal data class InstallProfile (
        @SerializedName("install")
        val install: Install? = null,
        @SerializedName("versionInfo")
        val versionInfo: Version? = null
) : Validation {
    override fun validate() {
        check(install != null, { "InstallProfile install cannot be null" })
        check(versionInfo != null, { "InstallProfile versionInfo cannot be null" })
    }
}