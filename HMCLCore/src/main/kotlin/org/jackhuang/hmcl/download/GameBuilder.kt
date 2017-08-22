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

import org.jackhuang.hmcl.task.Task

/**
 * The builder which provide a task to build Minecraft environment.
 *
 * @author huangyuhui
 */
abstract class GameBuilder {
    var name: String = ""
    protected var gameVersion: String = ""
    protected var toolVersions = HashMap<String, String>()

    /**
     * The new game version name, for .minecraft/<version name>.
     * @param name the name of new game version.
     */
    fun name(name: String): GameBuilder {
        this.name = name
        return this
    }

    fun gameVersion(version: String): GameBuilder {
        gameVersion = version
        return this
    }

    /**
     * @param id the core library id. i.e. "forge", "liteloader", "optifine"
     * @param version the version of the core library. For documents, you can first try [VersionList.versions]
     */
    fun version(id: String, version: String): GameBuilder {
        toolVersions[id] = version
        return this
    }

    /**
     * @return the task that can build thw whole Minecraft environment
     */
    abstract fun buildAsync(): Task
}