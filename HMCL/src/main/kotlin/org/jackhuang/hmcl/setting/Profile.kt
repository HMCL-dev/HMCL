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
package org.jackhuang.hmcl.setting

import com.google.gson.*
import javafx.beans.InvalidationListener
import org.jackhuang.hmcl.download.DefaultDependencyManager
import org.jackhuang.hmcl.game.HMCLGameRepository
import org.jackhuang.hmcl.mod.ModManager
import org.jackhuang.hmcl.util.*
import org.jackhuang.hmcl.util.property.ImmediateObjectProperty
import org.jackhuang.hmcl.util.property.ImmediateStringProperty
import java.io.File
import java.lang.reflect.Type

class Profile(var name: String = "Default", initialGameDir: File = File(".minecraft"), initialSelectedVersion: String = "") {
    val globalProperty = ImmediateObjectProperty<VersionSetting>(this, "global", VersionSetting())
    var global: VersionSetting by globalProperty

    val selectedVersionProperty = ImmediateStringProperty(this, "selectedVersion", initialSelectedVersion)
    var selectedVersion: String by selectedVersionProperty

    val gameDirProperty = ImmediateObjectProperty<File>(this, "gameDir", initialGameDir)
    var gameDir: File by gameDirProperty

    var repository = HMCLGameRepository(this, initialGameDir)
    val dependency: DefaultDependencyManager get() = DefaultDependencyManager(repository, Settings.downloadProvider, Settings.proxy)
    var modManager = ModManager(repository)

    init {
        gameDirProperty.addListener { _ ->
            repository.baseDirectory = gameDir
            repository.refreshVersions()
        }

        selectedVersionProperty.addListener { _ ->
            if (selectedVersion.isNotBlank() && !repository.hasVersion(selectedVersion)) {
                val newVersion = repository.getVersions().firstOrNull()
                // will cause anthor change event, we must ensure that there will not be dead recursion.
                selectedVersion = newVersion?.id ?: ""
            }
        }
    }

    /**
     * @return null if the given version id does not exist.
     */
    fun specializeVersionSetting(id: String): VersionSetting? {
        var vs = repository.getVersionSetting(id)
        if (vs == null)
            vs = repository.createVersionSetting(id) ?: return null
        vs.usesGlobal = false
        return vs
    }

    fun globalizeVersionSetting(id: String) {
        repository.getVersionSetting(id)?.usesGlobal = true
    }

    fun isVersionGlobal(id: String): Boolean {
        return repository.getVersionSetting(id)?.usesGlobal ?: true
    }

    fun getVersionSetting(id: String): VersionSetting {
        val vs = repository.getVersionSetting(id)
        if (vs == null || vs.usesGlobal) {
            global.isGlobal = true // always keep global.isGlobal = true
            return global
        } else
            return vs
    }

    fun getSelectedVersionSetting(): VersionSetting =
            getVersionSetting(selectedVersion)

    fun addPropertyChangedListener(listener: InvalidationListener) {
        globalProperty.addListener(listener)
        selectedVersionProperty.addListener(listener)
        gameDirProperty.addListener(listener)
    }

    companion object Serializer: JsonSerializer<Profile>, JsonDeserializer<Profile> {
        override fun serialize(src: Profile?, typeOfSrc: Type?, context: JsonSerializationContext): JsonElement {
            if (src == null) return JsonNull.INSTANCE
            val jsonObject = JsonObject()
            with(jsonObject) {
                add("global", context.serialize(src.global))
                addProperty("selectedVersion", src.selectedVersion)
                addProperty("gameDir", src.gameDir.path)
            }

            return jsonObject
        }

        override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext): Profile? {
            if (json == null || json == JsonNull.INSTANCE || json !is JsonObject) return null

            return Profile(initialGameDir = File(json["gameDir"]?.asString ?: ""), initialSelectedVersion = json["selectedVersion"]?.asString ?: "").apply {
                global = context.deserialize(json["global"], VersionSetting::class.java)
            }
        }

    }
}