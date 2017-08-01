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

import org.jackhuang.hmcl.game.GameRepository
import org.jackhuang.hmcl.game.Version
import org.jackhuang.hmcl.task.Task

abstract class DependencyManager(open val repository: GameRepository) {

    /**
     * Check if the game is complete.
     * Check libraries, assets, logging files and so on.
     *
     * @return
     */
    abstract fun checkGameCompletionAsync(version: Version): Task

    /**
     * The builder to build a brand new game then libraries such as Forge, LiteLoader and OptiFine.
     */
    abstract fun gameBuilder(): GameBuilder

    abstract fun installLibraryAsync(version: Version, libraryId: String, libraryVersion: String): Task

    /**
     * Get registered version list.
     * @param id the id of version list. i.e. game, forge, liteloader, optifine
     * @throws IllegalArgumentException if the version list of specific id is not found.
     */
    abstract fun getVersionList(id: String): VersionList<*>
}