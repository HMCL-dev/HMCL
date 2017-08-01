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

import org.jackhuang.hmcl.game.GameRepository
import org.jackhuang.hmcl.util.SimpleMultimap
import org.jackhuang.hmcl.util.asVersion
import org.jackhuang.hmcl.util.ignoreException
import org.jackhuang.hmcl.util.makeDirectory
import java.io.File
import java.io.IOException
import java.util.*

class ModManager(private val repository: GameRepository) {
    private val modCache = SimpleMultimap<String, ModInfo>(::HashMap, ::TreeSet)

    fun refreshMods(id: String): Collection<ModInfo> {
        val modsDirectory = repository.getRunDirectory(id).resolve("mods")
        val puter = { modFile: File -> ignoreException { modCache.put(id, ModInfo.fromFile(modFile)) } }
        modsDirectory.listFiles()?.forEach { modFile ->
            if (modFile.isDirectory && modFile.name.asVersion() != null)
                modFile.listFiles()?.forEach(puter)
            puter(modFile)
        }
        return modCache[id]
    }

    fun getMods(id: String) : Collection<ModInfo> {
        if (!modCache.containsKey(id))
            refreshMods(id)
        return modCache[id] ?: emptyList()
    }

    fun addMod(id: String, file: File) {
        if (!ModInfo.isFileMod(file))
            throw IllegalArgumentException("File $file is not a valid mod file.")

        if (!modCache.containsKey(id))
            refreshMods(id)

        val modsDirectory = repository.getRunDirectory(id).resolve("mods")
        if (!modsDirectory.makeDirectory())
            throw IOException("Cannot make directory $modsDirectory")

        val newFile = modsDirectory.resolve(file.name)
        file.copyTo(newFile)

        modCache.put(id, ModInfo.fromFile(newFile))
    }

    fun removeMods(id: String, vararg modInfos: ModInfo): Boolean =
        modInfos.fold(true, { acc, modInfo -> acc && modInfo.file.delete() }).apply { refreshMods(id) }
}