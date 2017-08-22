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
import org.jackhuang.hmcl.util.GSON
import org.jackhuang.hmcl.util.fromJson
import org.jackhuang.hmcl.util.readFullyAsString
import java.io.File
import java.util.zip.ZipFile

class LiteModMetadata @JvmOverloads internal constructor(
        val name: String = "",
        val version: String = "",
        val mcversion: String = "",
        val revision: String = "",
        val author: String = "",
        val classTransformerClasses: String = "",
        val description: String = "",
        val modpackName: String = "",
        val modpackVersion: String = "",
        val checkUpdateUrl: String = "",
        val updateURI: String = ""
) {

    companion object {
        /**
         * Read LiteLoader mod ModInfo.
         */
        fun fromFile(modFile: File): ModInfo {
            ZipFile(modFile).use {
                val entry = it.getEntry("litemod.json")
                requireNotNull(entry, { "File $modFile is not a LiteLoader mod." })
                val modList: LiteModMetadata? = GSON.fromJson<LiteModMetadata>(it.getInputStream(entry).readFullyAsString())
                val metadata = modList ?: throw JsonParseException("Mod $modFile 'litemod.json' is malformed")
                return ModInfo(
                        file = modFile,
                        name = metadata.name,
                        description = metadata.description,
                        authors = metadata.author,
                        version = metadata.version,
                        mcversion = metadata.mcversion,
                        url = metadata.updateURI
                )
            }
        }
    }
}