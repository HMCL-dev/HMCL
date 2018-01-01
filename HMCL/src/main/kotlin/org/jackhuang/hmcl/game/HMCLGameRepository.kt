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

import com.google.gson.GsonBuilder
import javafx.beans.InvalidationListener
import org.jackhuang.hmcl.setting.EnumGameDirectory
import org.jackhuang.hmcl.setting.Profile
import org.jackhuang.hmcl.setting.Settings
import org.jackhuang.hmcl.setting.VersionSetting
import org.jackhuang.hmcl.task.Schedulers
import org.jackhuang.hmcl.util.Logging.LOG
import org.jackhuang.hmcl.util.fromJson
import java.io.File
import java.io.IOException
import java.util.logging.Level

class HMCLGameRepository(val profile: Profile, baseDirectory: File)
    : DefaultGameRepository(baseDirectory) {

    private val versionSettings = mutableMapOf<String, VersionSetting>()
    private val beingModpackVersions = mutableSetOf<String>()

    private fun useSelf(version: String, assetId: String): Boolean {
        val vs = profile.getVersionSetting(version)
        return File(baseDirectory, "assets/indexes/$assetId.json").exists() || vs.noCommon
    }

    override fun getAssetDirectory(version: String, assetId: String): File {
        if (useSelf(version, assetId))
            return super.getAssetDirectory(version, assetId)
        else
            return File(Settings.commonPath).resolve("assets")
    }

    override fun getRunDirectory(id: String): File {
        if (beingModpackVersions.contains(id))
            return getVersionRoot(id)
        else {
            val vs = profile.getVersionSetting(id)
            return when (vs.gameDirType) {
                EnumGameDirectory.VERSION_FOLDER -> getVersionRoot(id)
                EnumGameDirectory.ROOT_FOLDER -> super.getRunDirectory(id)
                EnumGameDirectory.CUSTOM -> File(vs.gameDir)
            }
        }
    }

    override fun getLibraryFile(version: Version, lib: Library): File {
        val vs = profile.getVersionSetting(version.id)
        val self = super.getLibraryFile(version, lib);
        if (self.exists() || vs.noCommon)
            return self;
        else
            return File(Settings.commonPath).resolve("libraries/${lib.path}")
    }

    @Synchronized
    override fun refreshVersionsImpl() {
        Schedulers.newThread().schedule {
            versionSettings.clear()

            super.refreshVersionsImpl()

            versions.keys.forEach(this::loadVersionSetting)

            checkModpack()

            try {
                val file = baseDirectory.resolve("launcher_profiles.json")
                if (!file.exists() && versions.isNotEmpty())
                    file.writeText(PROFILE)
            } catch (ex: IOException) {
                LOG.log(Level.WARNING, "Unable to create launcher_profiles.json, Forge/LiteLoader installer will not work.", ex)
            }
        }
    }

    fun changeDirectory(newDir: File) {
        baseDirectory = newDir
        refreshVersions()
    }

    private fun checkModpack() {}

    private fun getVersionSettingFile(id: String) = getVersionRoot(id).resolve("hmclversion.cfg")

    private fun loadVersionSetting(id: String) {
        val file = getVersionSettingFile(id)
        if (file.exists()) {
            try {
                val versionSetting = GSON.fromJson<VersionSetting>(file.readText())!!
                initVersionSetting(id, versionSetting)
            } catch (ignore: Exception) {
                // If [JsonParseException], [IOException] or [NullPointerException] happens, the json file is malformed and needed to be recreated.
            }
        }
    }

    private fun saveVersionSetting(id: String) {
        if (!versionSettings.containsKey(id))
            return

        getVersionSettingFile(id).writeText(GSON.toJson(versionSettings[id]))
    }

    private fun initVersionSetting(id: String, vs: VersionSetting): VersionSetting {
        vs.addPropertyChangedListener(InvalidationListener { saveVersionSetting(id) })
        versionSettings[id] = vs
        return vs
    }

    internal fun createVersionSetting(id: String): VersionSetting? {
        if (!hasVersion(id)) return null
        return versionSettings[id] ?: initVersionSetting(id, VersionSetting())
    }

    fun getVersionSetting(id: String): VersionSetting? {
        if (!versionSettings.containsKey(id))
            loadVersionSetting(id)
        return versionSettings[id]
    }

    fun getVersionIcon(id: String): File =
            getVersionRoot(id).resolve("icon.png")

    fun markVersionAsModpack(id: String) {
        beingModpackVersions += id
    }

    fun undoMark(id: String) {
        beingModpackVersions -= id
    }

    companion object {
        val PROFILE = "{\"selectedProfile\": \"(Default)\",\"profiles\": {\"(Default)\": {\"name\": \"(Default)\"}},\"clientToken\": \"88888888-8888-8888-8888-888888888888\"}"
        val GSON = GsonBuilder().registerTypeAdapter(VersionSetting::class.java, VersionSetting).setPrettyPrinting().create()
    }
}