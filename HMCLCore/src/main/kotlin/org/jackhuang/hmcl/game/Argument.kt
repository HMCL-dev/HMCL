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

import com.google.gson.*
import java.lang.reflect.Type

interface Argument {

    /**
     * Parse this argument in form: ${key name} or simply a string.
     *
     * @param keys the parse map
     * @param features the map that contains some features such as 'is_demo_user', 'has_custom_resolution'
     * @return parsed argument element, empty if this argument is ignored and will not be added.
     */
    fun toString(keys: Map<String, String>, features: Map<String, Boolean>): List<String>

    companion object Serializer : JsonDeserializer<Argument>, JsonSerializer<Argument> {
        override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): Argument =
                if (json.isJsonPrimitive)
                    StringArgument(json.asString)
                else
                    context.deserialize(json, RuledArgument::class.java)

        override fun serialize(src: Argument, typeOfSrc: Type, context: JsonSerializationContext): JsonElement =
                when (src) {
                    is StringArgument -> JsonPrimitive(src.argument)
                    is RuledArgument -> context.serialize(src, RuledArgument::class.java)
                    else -> throw AssertionError("Unrecognized argument type: $src")
                }
    }
}