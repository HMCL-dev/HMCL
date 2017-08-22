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

import org.jackhuang.hmcl.util.Immutable
import java.io.File

/**
 * The Minecraft version for 1.5.x and earlier.
 */
@Immutable
class ClassicVersion : Version(
        mainClass = "net.minecraft.client.Minecraft",
        id = "Classic",
        type = ReleaseType.UNKNOWN,
        minecraftArguments = "\${auth_player_name} \${auth_session} --workDir \${game_directory}",
        libraries = listOf(
                ClassicLibrary("lwjgl"),
                ClassicLibrary("jinput"),
                ClassicLibrary("lwjgl_util")
        )
) {
    @Immutable
    private class ClassicLibrary(name: String) :
            Library(groupId = "", artifactId = "", version = "",
                    downloads = LibrariesDownloadInfo(
                            artifact = LibraryDownloadInfo(path = "bin/$name.jar")
                    )
            )

    companion object {
        /**
         * Check if this directory is an old style Minecraft repository.
         */
        fun hasClassicVersion(baseDirectory: File): Boolean {
            val file = File(baseDirectory, "bin")
            if (!file.exists()) return false
            if (!File(file, "lwjgl.jar").exists()) return false
            if (!File(file, "jinput.jar").exists()) return false
            if (!File(file, "lwjgl_util.jar").exists()) return false
            return true
        }
    }
}