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
package org.jackhuang.hmcl.mod

import com.google.gson.JsonParseException
import com.google.gson.annotations.SerializedName
import org.jackhuang.hmcl.util.GSON
import org.jackhuang.hmcl.util.parseParams
import org.jackhuang.hmcl.util.readFullyAsString
import org.jackhuang.hmcl.util.typeOf
import java.io.File
import java.util.zip.ZipFile

class ForgeModMetadata(
        @SerializedName("modid")
        val modId: String = "",
        val name: String = "",
        val description: String = "",
        val author: String = "",
        val version: String = "",
        val mcversion: String = "",
        val url: String = "",
        val updateUrl: String = "",
        val credits: String = "",
        val authorList: Array<String> = emptyArray(),
        val authors: Array<String> = emptyArray()
) {

    companion object {
        fun fromFile(modFile: File): ModInfo {
            ZipFile(modFile).use {
                val entry = it.getEntry("mcmod.info") ?: throw JsonParseException("File $modFile is not a Forge mod.")
                val modList: List<ForgeModMetadata>? = GSON.fromJson(it.getInputStream(entry).readFullyAsString(), typeOf<List<ForgeModMetadata>>())
                val metadata = modList?.firstOrNull() ?: throw JsonParseException("Mod $modFile 'mcmod.info' is malformed")
                var authors: String = metadata.author
                if (authors.isBlank() && metadata.authors.isNotEmpty()) {
                    authors = parseParams("", metadata.authors, ", ")
                }
                if (authors.isBlank() && metadata.authorList.isNotEmpty()) {
                    authors = parseParams("", metadata.authorList, ", ")
                }
                if (authors.isBlank())
                    authors = metadata.credits
                return ModInfo(
                        file = modFile,
                        name = metadata.name,
                        description = metadata.description,
                        authors = authors,
                        version = metadata.version,
                        mcversion = metadata.mcversion,
                        url = if (metadata.url.isBlank()) metadata.updateUrl else metadata.url
                )
            }
        }
    }
}