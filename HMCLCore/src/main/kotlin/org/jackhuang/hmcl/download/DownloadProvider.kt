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

/**
 * The service provider that provides Minecraft online file downloads.
 */
interface DownloadProvider {
    val libraryBaseURL: String
    val versionListURL: String
    val versionBaseURL: String
    val assetIndexBaseURL: String
    val assetBaseURL: String

    /**
     * Inject into original URL provided by Mojang and Forge.
     *
     * Since there are many provided URLs that are written in JSONs and are unmodifiable,
     * this method provides a way to change them.
     *
     * @param baseURL original URL provided by Mojang and Forge.
     * @return the URL that is equivalent to [baseURL], but belongs to your own service provider.
     */
    fun injectURL(baseURL: String): String

    /**
     * the specific version list that this download provider provides. i.e. "forge", "liteloader", "game", "optifine"
     * @param id the id of specific version list that this download provider provides. i.e. "forge", "liteloader", "game", "optifine"
     * @return the version list
     */
    fun getVersionListById(id: String): VersionList<*>
}