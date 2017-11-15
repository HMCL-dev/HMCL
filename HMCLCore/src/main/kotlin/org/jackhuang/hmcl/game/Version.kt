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

import com.google.gson.JsonParseException
import com.google.gson.annotations.SerializedName
import org.jackhuang.hmcl.util.*
import java.util.*

@Immutable
open class Version(
        @SerializedName("minecraftArguments")
        val minecraftArguments: String? = null,
        @SerializedName("arguments")
        val arguments: Arguments? = null,
        @SerializedName("mainClass")
        val mainClass: String? = null,
        @SerializedName("time")
        val time: Date = Date(),
        @SerializedName("id")
        val id: String = "",
        @SerializedName("type")
        val type: ReleaseType = ReleaseType.UNKNOWN,
        @SerializedName("releaseTime")
        val releaseTime: Date = Date(),
        @SerializedName("jar")
        val jar: String? = null,
        @SerializedName("inheritsFrom")
        val inheritsFrom: String? = null,
        @SerializedName("assets")
        private val assets: String? = null,
        @SerializedName("minimumLauncherVersion")
        val minimumLauncherVersion: Int = 0,
        @SerializedName("assetIndex")
        private val assetIndex: AssetIndexInfo? = null,

        libraries: List<Library> = emptyList(),
        compatibilityRules: List<CompatibilityRule>? = null,
        downloads: Map<DownloadType, DownloadInfo>? = null,
        logging: Map<DownloadType, LoggingInfo>? = null
): Comparable<Version>, Validation {
    val downloads: Map<DownloadType, DownloadInfo>? get() = unmodifiableMap(downloadsImpl)

    @SerializedName("downloads")
    private val downloadsImpl: MutableMap<DownloadType, DownloadInfo>? = copyMap(downloads)

    val logging: Map<DownloadType, LoggingInfo>? get() = unmodifiableMap(loggingImpl)

    @SerializedName("logging")
    private val loggingImpl: MutableMap<DownloadType, LoggingInfo>? = copyMap(logging)

    val libraries: List<Library> get() = Collections.unmodifiableList(librariesImpl)

    @SerializedName("libraries")
    private val librariesImpl: MutableList<Library> = LinkedList(libraries)

    val compatibilityRules: List<CompatibilityRule>? get() = unmodifiableList(compatibilityRulesImpl)

    @SerializedName("compatibilityRules")
    private val compatibilityRulesImpl: MutableList<CompatibilityRule>? = copyList(compatibilityRules)

    val download: DownloadInfo
        get() {
            val client = downloads?.get(DownloadType.CLIENT)
            val jar = this.jar ?: this.id
            if (client == null) {
                return DownloadInfo("$DEFAULT_VERSION_DOWNLOAD_URL$jar/$jar.jar")
            } else {
                return client
            }
        }

    val actualAssetIndex: AssetIndexInfo
        get() {
            val id = assets ?: "legacy"
            return assetIndex ?: AssetIndexInfo(id = id, url = "$DEFAULT_VERSION_DOWNLOAD_URL$id.json")
        }

    fun appliesToCurrentEnvironment() = CompatibilityRule.appliesToCurrentEnvironment(compatibilityRules)

    override fun hashCode(): Int = id.hashCode()
    override fun equals(other: Any?): Boolean =
            if (other is Version) Objects.equals(this.id, other.id)
            else false
    override fun compareTo(other: Version) = id.compareTo(other.id)
    open fun install(id: String): Boolean {
        return false
    }

    /**
     * Resolve given version
     *
     * @throws CircleDependencyException
     */
    fun resolve(provider: VersionProvider): Version =
            resolve(provider, HashSet<String>())

    protected open fun resolve(provider: VersionProvider, resolvedSoFar: MutableSet<String>): Version {
        if (this.inheritsFrom == null)
            return this
        if (!resolvedSoFar.add(this.id)) {
            LOG.warning("Found circular dependency versions: $resolvedSoFar")
            return this
        }

        // It is supposed to auto install an version in getVersion.
        val parent = provider.getVersion(this.inheritsFrom).resolve(provider, resolvedSoFar)
        return Version(
                id = this.id,
                jar = this.jar ?: parent.jar,
                time = this.time,
                type = this.type,
                assets = this.assets ?: parent.assets,
                logging = this.logging ?: parent.logging,
                mainClass = this.mainClass ?: parent.mainClass,
                libraries = merge(this.libraries, parent.libraries),
                downloads = this.downloads ?: parent.downloads,
                assetIndex = this.assetIndex ?: parent.assetIndex,
                arguments = Arguments.mergeArguments(parent.arguments, this.arguments),
                releaseTime = this.releaseTime,
                inheritsFrom = null,
                minecraftArguments = this.minecraftArguments ?: parent.minecraftArguments,
                minimumLauncherVersion = parent.minimumLauncherVersion
        )
    }

    fun copy(
            minecraftArguments: String? = this.minecraftArguments,
            arguments: Arguments? = this.arguments,
            mainClass: String? = this.mainClass,
            time: Date = this.time,
            releaseTime: Date = this.releaseTime,
            id: String = this.id,
            type: ReleaseType = this.type,
            jar: String? = this.jar,
            inheritsFrom: String? = this.inheritsFrom,
            assets: String? = this.assets,
            minimumLauncherVersion: Int = this.minimumLauncherVersion,
            assetIndex: AssetIndexInfo? = this.assetIndex,
            libraries: List<Library> = this.librariesImpl,
            compatibilityRules: List<CompatibilityRule>? = this.compatibilityRulesImpl,
            downloads: Map<DownloadType, DownloadInfo>? = this.downloads,
            logging: Map<DownloadType, LoggingInfo>? = this.logging) =
            Version(minecraftArguments,
                    arguments,
                    mainClass,
                    time,
                    id,
                    type,
                    releaseTime,
                    jar,
                    inheritsFrom,
                    assets,
                    minimumLauncherVersion,
                    assetIndex,
                    libraries,
                    compatibilityRules,
                    downloads,
                    logging
            )

    override fun validate() {
        if (id.isBlank())
            throw JsonParseException("Version ID cannot be blank")
        (downloadsImpl as Map<*, *>?)?.forEach { (key, value) ->
            if (key !is DownloadType)
                throw JsonParseException("Version downloads key must be DownloadType")
            if (value !is DownloadInfo)
                throw JsonParseException("Version downloads value must be DownloadInfo")
        }
        (loggingImpl as Map<*, *>?)?.forEach { (key, value) ->
            if (key !is DownloadType)
                throw JsonParseException("Version logging key must be DownloadType")
            if (value !is LoggingInfo)
                throw JsonParseException("Version logging value must be DownloadInfo")
        }
    }
}