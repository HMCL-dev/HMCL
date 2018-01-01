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
import com.google.gson.annotations.SerializedName
import org.jackhuang.hmcl.util.*
import java.lang.reflect.Type

/**
 * A class that describes a Minecraft dependency.
 */
@Immutable
open class Library @JvmOverloads constructor(
        val groupId: String,
        val artifactId: String,
        val version: String,
        classifier_: String? = null,
        @SerializedName("url")
        private val url: String? = null,
        @SerializedName("downloads")
        private val downloads: LibrariesDownloadInfo? = null,
        @SerializedName("extract")
        val extract: ExtractRules? = null,
        @SerializedName("lateload")
        val lateload: Boolean = false,
        private val natives: Map<OS, String>? = null,
        private val rules: List<CompatibilityRule>? = null
) {

    val appliesToCurrentEnvironment: Boolean = CompatibilityRule.appliesToCurrentEnvironment(rules)
    val isNative: Boolean = natives != null && appliesToCurrentEnvironment
    val download: LibraryDownloadInfo
    val classifier: String? = classifier_ ?: natives?.get(OS.CURRENT_OS)?.replace("\${arch}", Platform.PLATFORM.bit)
    val path: String

    init {

        var temp: LibraryDownloadInfo? = null
        if (downloads != null) {
            if (isNative)
                temp = downloads.classifiers?.get(classifier)
            else
                temp = downloads.artifact
        }
        path = temp?.path ?: "${groupId.replace(".", "/")}/$artifactId/$version/$artifactId-$version" + (if (classifier == null) "" else "-$classifier") + ".jar"
        download = LibraryDownloadInfo(
                url = temp?.url ?: (url ?: DEFAULT_LIBRARY_URL) + path,
                path = path,
                size = temp?.size ?: 0,
                sha1 = temp?.sha1
        )
    }

    override fun toString() = "Library[$groupId:$artifactId:$version]"

    companion object LibrarySerializer : JsonDeserializer<Library>, JsonSerializer<Library> {
        fun fromName(name: String, url: String? = null, downloads: LibrariesDownloadInfo? = null, extract: ExtractRules? = null, natives: Map<OS, String>? = null, rules: List<CompatibilityRule>? = null): Library {
            val arr = name.split(":".toRegex(), 4)
            if (arr.size != 3 && arr.size != 4)
                throw IllegalArgumentException("Library name is malformed. Correct example: group:artifact:version.")
            return Library(
                    groupId = arr[0].replace("\\", "/"),
                    artifactId = arr[1],
                    version = arr[2],
                    classifier_ = arr.getOrNull(3),
                    url = url,
                    downloads = downloads,
                    extract = extract,
                    natives = natives,
                    rules = rules
            )
        }

        override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext): Library? {
            if (json == null || json == JsonNull.INSTANCE)
                return null
            val jsonObject = json.asJsonObject
            if (jsonObject["name"] == null)
                throw JsonParseException("Library name not found.")
            return fromName(jsonObject["name"].asString, jsonObject["url"]?.asString, context.deserialize(jsonObject["downloads"], LibrariesDownloadInfo::class.java),
                    context.deserialize(jsonObject["extract"], ExtractRules::class.java),
                    context.deserialize(jsonObject["natives"], typeOf<Map<OS, String>>()),
                    context.deserialize(jsonObject["rules"], typeOf<List<CompatibilityRule>>()))
        }

        override fun serialize(src: Library?, typeOfSrc: Type?, context: JsonSerializationContext): JsonElement {
            if (src == null) return JsonNull.INSTANCE
            val obj = JsonObject()
            obj.addProperty("name", "${src.groupId}:${src.artifactId}:${src.version}")
            obj.addProperty("url", src.url)
            obj.add("downloads", context.serialize(src.downloads))
            obj.add("extract", context.serialize(src.extract))
            obj.add("natives", context.serialize(src.natives, typeOf<Map<OS, String>>()))
            obj.add("rules", context.serialize(src.rules, typeOf<List<CompatibilityRule>>()))
            return obj
        }
    }
}