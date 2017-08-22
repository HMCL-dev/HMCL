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
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import org.jackhuang.hmcl.game.Library
import java.io.File
import java.io.IOException
import java.lang.reflect.Type
import java.text.DateFormat
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*
import java.util.UUID

val GSON: Gson = GsonBuilder()
        .enableComplexMapKeySerialization()
        .setPrettyPrinting()
        .registerTypeAdapter(Library::class.java, Library)
        .registerTypeAdapter(Date::class.java, DateTypeAdapter)
        .registerTypeAdapter(UUID::class.java, UUIDTypeAdapter)
        .registerTypeAdapter(Platform::class.java, Platform)
        .registerTypeAdapter(File::class.java, FileTypeAdapter)
        .registerTypeAdapterFactory(ValidationTypeAdapterFactory)
        .registerTypeAdapterFactory(LowerCaseEnumTypeAdapterFactory)
        .create()

inline fun <reified T> typeOf(): Type = object : TypeToken<T>() {}.type

inline fun <reified T> Gson.fromJson(json: String): T? = fromJson<T>(json, T::class.java)

inline fun <reified T> Gson.fromJsonQuietly(json: String): T? {
    try {
        return fromJson<T>(json)
    } catch (json: JsonParseException) {
        return null
    }
}

/**
 * Check if the json object's fields automatically filled by Gson are in right format.
 */
interface Validation {
    /**
     * 1. Check some non-null fields and;
     * 2. Check strings and;
     * 3. Check generic type of lists <T> and maps <K, V> are correct.
     *
     * Will be called immediately after initialization.
     * Throw an exception when values are malformed.
     * @throws JsonParseException if fields are filled in wrong format or wrong type.
     */
    fun validate()
}

object ValidationTypeAdapterFactory : TypeAdapterFactory {
    override fun <T : Any?> create(gson: Gson, type: TypeToken<T?>?): TypeAdapter<T?> {
        val delgate = gson.getDelegateAdapter(this, type)
        return object : TypeAdapter<T?>() {
            override fun write(out: JsonWriter?, value: T?) {
                if (value is Validation)
                    value.validate()
                delgate.write(out, value)
            }

            override fun read(reader: JsonReader?): T? {
                val value = delgate.read(reader)
                if (value is Validation)
                    value.validate()
                return value
            }
        }
    }
}

object LowerCaseEnumTypeAdapterFactory : TypeAdapterFactory {
    override fun <T> create(gson: Gson, type: TypeToken<T?>): TypeAdapter<T?>? {
        val rawType = type.rawType
        if (!rawType.isEnum) {
            return null
        }
        val lowercaseToConstant = HashMap<String, T>()
        for (constant in rawType.enumConstants) {
            @Suppress("UNCHECKED_CAST")
            lowercaseToConstant.put(toLowercase(constant!!), constant as T)
        }
        return object : TypeAdapter<T?>() {
            @Throws(IOException::class)
            override fun write(out: JsonWriter, value: T?) {
                if (value == null) {
                    out.nullValue()
                } else {
                    out.value(toLowercase(value))
                }
            }

            @Throws(IOException::class)
            override fun read(reader: JsonReader): T? {
                if (reader.peek() == JsonToken.NULL) {
                    reader.nextNull()
                    return null
                }
                return lowercaseToConstant[reader.nextString().toLowerCase()]
            }
        }
    }

    private fun toLowercase(o: Any): String {
        return o.toString().toLowerCase(Locale.US)
    }
}

object UUIDTypeAdapter : TypeAdapter<UUID>() {
    override fun read(reader: JsonReader): UUID {
        return fromString(reader.nextString())
    }

    override fun write(writer: JsonWriter, value: UUID?) {
        writer.value(if (value == null) null else fromUUID(value))
    }

    fun fromUUID(value: UUID): String {
        return value.toString().replace("-", "")
    }

    fun fromString(input: String): UUID {
        return UUID.fromString(input.replaceFirst("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})".toRegex(), "$1-$2-$3-$4-$5"))
    }

}

object DateTypeAdapter : JsonSerializer<Date>, JsonDeserializer<Date> {
    private val enUsFormat: DateFormat = DateFormat.getDateTimeInstance(2, 2, Locale.US)
    private val iso8601Format: DateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ")

    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): Date {
        if (json !is JsonPrimitive) {
            throw JsonParseException("The date should be a string value")
        } else {
            val date = this.deserializeToDate(json.getAsString())
            if (typeOfT === Date::class.java) {
                return date
            } else {
                throw IllegalArgumentException(this.javaClass.toString() + " cannot deserialize to " + typeOfT)
            }
        }
    }

    override fun serialize(src: Date, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
        synchronized(this.enUsFormat) {
            return JsonPrimitive(this.serializeToString(src))
        }
    }

    fun deserializeToDate(string: String): Date {
        synchronized(this.enUsFormat) {
            try {
                return this.enUsFormat.parse(string)
            } catch (ex1: ParseException) {
                try {
                    return this.iso8601Format.parse(string)
                } catch (ex2: ParseException) {
                    try {
                        var cleaned = string.replace("Z", "+00:00")
                        cleaned = cleaned.substring(0, 22) + cleaned.substring(23)
                        return this.iso8601Format.parse(cleaned)
                    } catch (e: Exception) {
                        throw JsonSyntaxException("Invalid date: " + string, e)
                    }
                }
            }
        }
    }

    fun serializeToString(date: Date): String {
        synchronized(this.enUsFormat) {
            val result = this.iso8601Format.format(date)
            return result.substring(0, 22) + ":" + result.substring(22)
        }
    }
}

object FileTypeAdapter : JsonSerializer<File>, JsonDeserializer<File> {
    override fun serialize(src: File?, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement {
        if (src == null) return JsonNull.INSTANCE
        else return JsonPrimitive(src.path)
    }

    override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): File? {
        if (json == null) return null
        else return File(json.asString)
    }

}