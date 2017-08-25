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

import org.jackhuang.hmcl.download.forge.ForgeVersionList
import org.jackhuang.hmcl.download.game.GameVersionList
import org.jackhuang.hmcl.download.liteloader.LiteLoaderVersionList
import org.jackhuang.hmcl.download.optifine.OptiFineBMCLVersionList

/**
 * @see {@link http://bmclapi2.bangbang93.com}
 */
object BMCLAPIDownloadProvider : DownloadProvider() {
    override val libraryBaseURL: String = "http://bmclapi2.bangbang93.com/libraries/"
    override val versionListURL: String = "http://bmclapi2.bangbang93.com/mc/game/version_manifest.json"
    override val versionBaseURL: String = "http://bmclapi2.bangbang93.com/versions/"
    override val assetIndexBaseURL: String = "http://bmclapi2.bangbang93.com/indexes/"
    override val assetBaseURL: String = "http://bmclapi2.bangbang93.com/assets/"

    override fun getVersionListById(id: String): VersionList<*> {
        return when(id) {
            "game" -> GameVersionList
            "forge" -> ForgeVersionList
            "liteloader" -> LiteLoaderVersionList
            "optifine" -> OptiFineBMCLVersionList
            else -> throw IllegalArgumentException("Unrecognized version list id: $id")
        }
    }

    override fun injectURL(baseURL: String): String = baseURL
            .replace("https://launchermeta.mojang.com", "https://bmclapi2.bangbang93.com")
            .replace("https://launcher.mojang.com", "https://bmclapi2.bangbang93.com")
            .replace("https://libraries.minecraft.net", "https://bmclapi2.bangbang93.com/libraries")
            .replace("http://files.minecraftforge.net/maven", "https://bmclapi2.bangbang93.com/maven")
            .replace("http://dl.liteloader.com/versions/versions.json", "httsp://bmclapi2.bangbang93.com/maven/com/mumfrey/liteloader/versions.json")
            .replace("http://dl.liteloader.com/versions", "https://bmclapi2.bangbang93.com/maven")
}