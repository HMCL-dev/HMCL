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

import org.jackhuang.hmcl.util.ImmediateBooleanProperty
import org.jackhuang.hmcl.util.getValue
import org.jackhuang.hmcl.util.setValue
import java.io.File

class ModInfo (
        var file: File,
        val name: String,
        val description: String = "",
        val authors: String = "unknown",
        val version: String = "unknown",
        val mcversion: String = "unknown",
        val url: String = ""
): Comparable<ModInfo> {
    val activeProperty = object : ImmediateBooleanProperty(this, "active", file.extension != DISABLED_EXTENSION) {
        override fun invalidated() {
            val f = file.absoluteFile
            val newf: File
            if (f.extension == DISABLED_EXTENSION)
                newf = File(f.parentFile, f.nameWithoutExtension)
            else
                newf = File(f.parentFile, f.name + ".disabled")
            if (f.renameTo(newf))
                file = newf
        }
    }
        @JvmName("activeProperty") get

    var isActive: Boolean by activeProperty

    val fileName: String = (if (isActive) file.name else file.nameWithoutExtension).substringBeforeLast(".")

    override fun compareTo(other: ModInfo): Int {
        return fileName.compareTo(other.fileName)
    }

    companion object {
        val DISABLED_EXTENSION = "disabled"

        fun isFileMod(file: File): Boolean {
            var name = file.name
            val disabled = name.endsWith(".disabled")
            if (disabled)
                name = name.substringBeforeLast(".disabled")
            return name.endsWith(".zip") || name.endsWith(".jar") || name.endsWith("litemod")
        }

        fun fromFile(modFile: File): ModInfo {
            val file = if (modFile.extension == DISABLED_EXTENSION)
                            modFile.absoluteFile.parentFile.resolve(modFile.nameWithoutExtension)
                        else modFile
            val description: String
            if (file.extension == "zip" || file.extension == "jar")
                try {
                    return ForgeModMetadata.fromFile(modFile)
                } catch (ignore: Exception) {
                    description = "May be Forge mod"
                }

            else if (file.extension == "litemod")
                try {
                    return LiteModMetadata.fromFile(modFile)
                } catch (ignore: Exception) {
                    description = "May be LiteLoader mod"
                }
            else throw IllegalArgumentException("File $modFile is not mod")

            return ModInfo(file = modFile, name = modFile.nameWithoutExtension, description = description)
        }
    }
}