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

import java.util.*
import java.util.regex.Pattern

class StringArgument(var argument: String) : Argument {

    override fun toString(keys: Map<String, String>, features: Map<String, Boolean>): List<String> {
        var res = argument
        val pattern = Pattern.compile("\\$\\{(.*?)\\}")
        val m = pattern.matcher(argument)
        while (m.find()) {
            val entry = m.group()
            res = res.replace(entry, keys.getOrDefault(entry, entry))
        }
        return Collections.singletonList(res)
    }

}