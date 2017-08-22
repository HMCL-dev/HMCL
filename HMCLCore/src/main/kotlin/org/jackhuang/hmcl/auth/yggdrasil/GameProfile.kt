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
package org.jackhuang.hmcl.auth.yggdrasil

import com.google.gson.*
import java.lang.reflect.Type
import java.util.*

data class GameProfile(
        val id: UUID? = null,
        val name: String? = null,
        val properties: PropertyMap = PropertyMap(),
        val legacy: Boolean = false
) {
    companion object GameProfileSerializer: JsonSerializer<GameProfile>, JsonDeserializer<GameProfile> {
        override fun serialize(src: GameProfile, typeOfSrc: Type?, context: JsonSerializationContext): JsonElement {
            val result = JsonObject()
            if (src.id != null)
                result.add("id", context.serialize(src.id))
            if (src.name != null)
                result.addProperty("name", src.name)
            return result
        }

        override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext): GameProfile {
            if (json !is JsonObject)
                throw JsonParseException("The json element is not a JsonObject.")
            val id = if (json.has("id")) context.deserialize<UUID>(json.get("id"), UUID::class.java) else null
            val name = if (json.has("name")) json.getAsJsonPrimitive("name").asString else null
            return GameProfile(id, name)
        }

    }
}