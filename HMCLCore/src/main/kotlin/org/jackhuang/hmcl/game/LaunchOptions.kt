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

import org.jackhuang.hmcl.util.JavaVersion
import java.io.File
import java.io.Serializable

data class LaunchOptions(
        /**
         * The game directory
         */
        val gameDir: File,

        /**
         * The Java Environment that Minecraft runs on.
         */
        val java: JavaVersion = JavaVersion.fromCurrentEnvironment(),

        /**
         * Will shown in the left bottom corner of the main menu of Minecraft.
         * null if use the id of launch version.
         */
        val versionName: String? = null,

        /**
         * Don't know what the hell this is.
         */
        var profileName: String? = null,

        /**
         * User custom additional minecraft command line arguments.
         */
        val minecraftArgs: String? = null,

        /**
         * User custom additional java virtual machine command line arguments.
         */
        val javaArgs: String? = null,

        /**
         * The minimum memory that the JVM can allocate.
         */
        val minMemory: Int? = null,

        /**
         * The maximum memory that the JVM can allocate.
         */
        val maxMemory: Int? = null,

        /**
         * The maximum metaspace memory that the JVM can allocate.
         * For Java 7 -XX:PermSize and Java 8 -XX:MetaspaceSize
         * Containing class instances.
         */
        val metaspace: Int? = null,

        /**
         * The initial game window width
         */
        val width: Int? = null,

        /**
         * The initial game window height
         */
        val height: Int? = null,

        /**
         * Is inital game window fullscreen.
         */
        val fullscreen: Boolean = false,

        /**
         * The server ip that will connect to when enter game main menu.
         */
        val serverIp: String? = null,

        /**
         * i.e. optirun
         */
        val wrapper: String? = null,

        /**
         * The host of the proxy address
         */
        val proxyHost: String? = null,

        /**
         * the port of the proxy address.
         */
        val proxyPort: String? = null,

        /**
         * The user name of the proxy, optional.
         */
        val proxyUser: String? = null,

        /**
         * The password of the proxy, optional
         */
        val proxyPass: String? = null,

        /**
         * Prevent game launcher from generating default JVM arguments like max memory.
         */
        val noGeneratedJVMArgs: Boolean = false,

        /**
         * Called command line before launching the game.
         */
        val precalledCommand: String? = null

) : Serializable