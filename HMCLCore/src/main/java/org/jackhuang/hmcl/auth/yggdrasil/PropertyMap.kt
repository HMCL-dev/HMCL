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
import com.google.gson.JsonObject
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import kotlin.collections.HashMap
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext



class PropertyMap: HashMap<String, Property>() {

    fun toList(): List<Map<String, String>> {
        val properties = LinkedList<Map<String, String>>()
        values.forEach { (name, value) ->
            properties += mapOf(
                    "name" to name,
                    "value" to value
            )
        }
        return properties
    }

    /**
     * Load property map from list.
     * @param list Right type is List<Map<String, String>>. Using List<*> here because of fault tolerance
     */
    fun fromList(list: List<*>) {
        list.forEach { propertyMap ->
            if (propertyMap is Map<*, *>) {
                val name = propertyMap["name"] as? String
                val value = propertyMap["value"] as? String
                if (name != null && value != null)
                    put(name, Property(name, value))
            }
        }
    }

    companion object Serializer : JsonSerializer<PropertyMap>, JsonDeserializer<PropertyMap> {
        override fun serialize(src: PropertyMap, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement {
            val result = JsonArray()
            for ((name, value) in src.values)
                result.add(JsonObject().apply {
                    addProperty("name", name)
                    addProperty("value", value)
                })

            return result
        }

        override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): PropertyMap {
            val result = PropertyMap()
            if (json is JsonObject) {
                for ((key, value) in json.entrySet())
                    if (value is JsonArray)
                        for (element in value)
                            result.put(key, Property(key, element.asString))
            } else if (json is JsonArray)
                for (element in json)
                    if (element is JsonObject) {
                        val name = element.getAsJsonPrimitive("name").asString
                        val value = element.getAsJsonPrimitive("value").asString
                        result.put(name, Property(name, value))
                    }
            return result
        }

    }
}