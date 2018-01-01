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
package org.jackhuang.hmcl.download.game

import com.google.gson.annotations.SerializedName
import org.jackhuang.hmcl.game.ReleaseType
import org.jackhuang.hmcl.util.DEFAULT_LIBRARY_URL
import org.jackhuang.hmcl.util.Immutable
import org.jackhuang.hmcl.util.Validation
import java.util.*

@Immutable
internal class GameRemoteLatestVersions(
        @SerializedName("snapshot")
        val snapshot: String,
        @SerializedName("release")
        val release: String
)

@Immutable
internal class GameRemoteVersion @JvmOverloads constructor(
        @SerializedName("id")
        val gameVersion: String = "",
        @SerializedName("time")
        val time: Date = Date(),
        @SerializedName("releaseTime")
        val releaseTime: Date = Date(),
        @SerializedName("type")
        val type: ReleaseType = ReleaseType.UNKNOWN,
        @SerializedName("url")
        val url: String = "$DEFAULT_LIBRARY_URL$gameVersion/$gameVersion.json"
) : Validation {
    override fun validate() {
        if (gameVersion.isBlank())
            throw IllegalArgumentException("GameRemoteVersion id cannot be blank")
        if (url.isBlank())
            throw IllegalArgumentException("GameRemoteVersion url cannot be blank")
    }
}

@Immutable
internal class GameRemoteVersions(
        @SerializedName("versions")
        val versions: List<GameRemoteVersion> = emptyList(),

        @SerializedName("latest")
        val latest: GameRemoteLatestVersions? = null
)

@Immutable
data class GameRemoteVersionTag (
        val type: ReleaseType,
        val time: Date
)