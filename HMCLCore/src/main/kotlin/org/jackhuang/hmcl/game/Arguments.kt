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

import com.google.gson.annotations.SerializedName
import org.jackhuang.hmcl.util.OS
import org.jackhuang.hmcl.game.CompatibilityRule.*
import org.jackhuang.hmcl.util.merge

class Arguments @JvmOverloads constructor(
        @SerializedName("game")
        val game: List<Argument>? = null,
        @SerializedName("jvm")
        val jvm: List<Argument>? = null
) {
    companion object {
        fun mergeArguments(a: Arguments?, b: Arguments?): Arguments? {
            if (a == null && b == null) return null
            else if (a == null) return b
            else if (b == null) return a
            else return Arguments(
                    game = merge(a.game, b.game),
                    jvm = merge(a.jvm, b.jvm)
            )
        }

        fun parseStringArguments(arguments: List<String>, keys: Map<String, String>, features: Map<String, Boolean> = emptyMap()): List<String> {
            return arguments.flatMap { StringArgument(it).toString(keys, features) }
        }

        fun parseArguments(arguments: List<Argument>, keys: Map<String, String>, features: Map<String, Boolean> = emptyMap()): List<String> {
            return arguments.flatMap { it.toString(keys, features) }
        }

        val DEFAULT_JVM_ARGUMENTS = listOf(
                RuledArgument(listOf(CompatibilityRule(Action.ALLOW, OSRestriction(OS.OSX))), listOf("-XstartOnFirstThread")),
                RuledArgument(listOf(CompatibilityRule(Action.ALLOW, OSRestriction(OS.WINDOWS))), listOf("-XX:HeapDumpPath=MojangTricksIntelDriversForPerformance_javaw.exe_minecraft.exe.heapdump")),
                RuledArgument(listOf(CompatibilityRule(Action.ALLOW, OSRestriction(OS.WINDOWS, "^10\\."))), listOf("-Dos.name=Windows 10", "-Dos.version=10.0")),
                StringArgument("-Djava.library.path=\${natives_directory}"),
                StringArgument("-Dminecraft.launcher.brand=\${launcher_name}"),
                StringArgument("-Dminecraft.launcher.version=\${launcher_version}"),
                StringArgument("-cp"),
                StringArgument("\${classpath}")
        )

        val DEFAULT_GAME_ARGUMENTS = listOf(
                RuledArgument(listOf(CompatibilityRule(Action.ALLOW, features = mapOf("has_custom_resolution" to true))), listOf("--width", "\${resolution_width}", "--height", "\${resolution_height}"))
        )
    }
}