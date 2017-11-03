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
import com.google.gson.reflect.TypeToken
import org.jackhuang.hmcl.util.typeOf
import java.lang.reflect.Type


class RuledArgument @JvmOverloads constructor(
        val rules: List<CompatibilityRule>? = null,
        val value: List<String>? = null) : Argument {

    override fun toString(keys: Map<String, String>, features: Map<String, Boolean>): List<String> =
            if (CompatibilityRule.appliesToCurrentEnvironment(rules))
                value?.map { StringArgument(it).toString(keys, features).single() } ?: emptyList()
            else
                emptyList()

    companion object Serializer : JsonSerializer<RuledArgument>, JsonDeserializer<RuledArgument> {
        override fun serialize(src: RuledArgument, typeOfSrc: Type, context: JsonSerializationContext) =
                JsonObject().apply {
                    add("rules", context.serialize(src.rules))
                    add("value", context.serialize(src.value))
                }

        override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): RuledArgument {
            val obj = json.asJsonObject
            return RuledArgument(
                    rules = context.deserialize(obj["rules"], typeOf<List<CompatibilityRule>>()),
                    value = if (obj["value"].isJsonPrimitive)
                        listOf(obj["value"].asString)
                    else
                        context.deserialize(obj["value"], typeOf<List<String>>())
            )
        }
    }
}