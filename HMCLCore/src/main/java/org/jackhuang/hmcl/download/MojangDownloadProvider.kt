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
import java.util.*

object MojangDownloadProvider : DownloadProvider() {
    override val libraryBaseURL: String = "https://libraries.minecraft.net/"
    override val versionBaseURL: String = "http://s3.amazonaws.com/Minecraft.Download/versions/"
    override val versionListURL: String = "https://launchermeta.mojang.com/mc/game/version_manifest.json"
    override val assetIndexBaseURL: String = "http://s3.amazonaws.com/Minecraft.Download/indexes/"
    override val assetBaseURL: String =  "http://resources.download.minecraft.net/"

    override fun getVersionListById(id: String): VersionList<*> {
        return when(id) {
            "game" -> GameVersionList
            "forge" -> ForgeVersionList
            "liteloader" -> LiteLoaderVersionList
            "optifine" -> OptiFineVersionList
            else -> throw IllegalArgumentException("Unrecognized version list id: $id")
        }
    }

    override fun injectURL(baseURL: String): String {
        /**if (baseURL.contains("scala-swing") || baseURL.contains("scala-xml") || baseURL.contains("scala-parser-combinators"))
            return baseURL.replace("http://files.minecraftforge.net/maven", "http://ftb.cursecdn.com/FTB2/maven/");
        else if (baseURL.contains("typesafe") || baseURL.contains("scala"))
            if (Locale.getDefault() == Locale.CHINA)
                return baseURL.replace("http://files.minecraftforge.net/maven", "http://maven.aliyun.com/nexus/content/groups/public");
            else
                return baseURL.replace("http://files.minecraftforge.net/maven", "http://repo1.maven.org/maven2");
        else
            return baseURL; */
        if (baseURL.endsWith("net/minecraftforge/forge/json"))
            return baseURL
        else if (Locale.getDefault() == Locale.CHINA)
            return baseURL.replace("http://files.minecraftforge.net/maven", "http://maven.aliyun.com/nexus/content/groups/public");
        else
            return baseURL.replace("http://files.minecraftforge.net/maven", "http://ftb.cursecdn.com/FTB2/maven")
    }
}