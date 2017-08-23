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

import org.jackhuang.hmcl.mod.Modpack
import org.jackhuang.hmcl.mod.readCurseForgeModpackManifest
import org.jackhuang.hmcl.util.readTextFromZipFileQuietly
import java.io.File

fun readModpackManifest(f: File): Modpack {
    try {
        val manifest = readCurseForgeModpackManifest(f)
        return Modpack(
                name = manifest.name,
                version = manifest.version,
                author = manifest.author,
                gameVersion = manifest.minecraft.gameVersion,
                description = readTextFromZipFileQuietly(f, "modlist.html") ?: "No description",
                manifest = manifest)
    } catch (e: Exception) {
        // ignore it, not a CurseForge modpack.
    }

    try {
        val manifest = readHMCLModpackManifest(f)
        return manifest
    } catch (e: Exception) {
        // ignore it, not a HMCL modpack.
    }

    throw IllegalArgumentException("Modpack file $f is not supported.")
}