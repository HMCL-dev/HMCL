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
import org.jackhuang.hmcl.game.Library
import org.jackhuang.hmcl.util.Immutable

@Immutable
internal data class LiteLoaderVersionsMeta (
        @SerializedName("description")
        val description: String = "",
        @SerializedName("authors")
        val authors: String = "",
        @SerializedName("url")
        val url: String = ""
)

@Immutable
internal data class LiteLoaderRepository (
        @SerializedName("stream")
        val stream: String = "",
        @SerializedName("type")
        val type: String = "",
        @SerializedName("url")
        val url: String = "",
        @SerializedName("classifier")
        val classifier: String = ""
)

@Immutable
internal class LiteLoaderVersion (
        @SerializedName("tweakClass")
        val tweakClass: String = "",
        @SerializedName("file")
        val file: String = "",
        @SerializedName("version")
        val version: String = "",
        @SerializedName("md5")
        val md5: String = "",
        @SerializedName("timestamp")
        val timestamp: String = "",
        @SerializedName("lastSuccessfulBuild")
        val lastSuccessfulBuild: Int = 0,
        @SerializedName("libraries")
        val libraries: Collection<Library> = emptyList()
)

@Immutable
internal class LiteLoaderBranch (
        @SerializedName("libraries")
        val libraries: Collection<Library> = emptyList(),
        @SerializedName("com.mumfrey:liteloader")
        val liteLoader: Map<String, LiteLoaderVersion> = emptyMap()
)

@Immutable
internal class LiteLoaderGameVersions (
        @SerializedName("repo")
        val repo: LiteLoaderRepository? = null,
        @SerializedName("artefacts")
        val artifacts: LiteLoaderBranch? = null,
        @SerializedName("snapshots")
        val snapshots: LiteLoaderBranch? = null
)

@Immutable
internal class LiteLoaderVersionsRoot (
        @SerializedName("versions")
        val versions: Map<String, LiteLoaderGameVersions> = emptyMap(),
        @SerializedName("meta")
        val meta: LiteLoaderVersionsMeta? = null
)

@Immutable
data class LiteLoaderRemoteVersionTag (
        val tweakClass: String,
        val libraries: Collection<Library>
)