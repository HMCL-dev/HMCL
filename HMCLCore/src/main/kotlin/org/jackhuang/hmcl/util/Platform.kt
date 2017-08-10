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
package org.jackhuang.hmcl.util

import com.google.gson.*
import java.lang.reflect.Type

enum class Platform(val bit: String) {
    BIT_32("32"),
    BIT_64("64"),
    UNKNOWN("unknown");

    companion object Serializer: JsonSerializer<Platform>, JsonDeserializer<Platform> {

        override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): Platform? {
            if (json == null) return null
            return when (json.asInt) {
                0 -> BIT_32
                1 -> BIT_64
                else -> UNKNOWN
            }
        }

        override fun serialize(src: Platform?, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement? {
            if (src == null) return null
            return when (src) {
                BIT_32 -> JsonPrimitive(0)
                BIT_64 -> JsonPrimitive(1)
                UNKNOWN -> JsonPrimitive(-1)
            }
        }

        val PLATFORM: Platform by lazy {
            if (IS_64_BIT) BIT_64 else BIT_32
        }

        val IS_64_BIT: Boolean by lazy {
            val arch = System.getProperty("sun.arch.data.model") ?: System.getProperty("os.arch")
            arch.contains("64")
        }
    }
}