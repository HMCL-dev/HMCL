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

import com.google.gson.GsonBuilder
import javafx.beans.InvalidationListener
import java.io.IOException
import org.jackhuang.hmcl.MainApplication
import org.jackhuang.hmcl.download.BMCLAPIDownloadProvider
import org.jackhuang.hmcl.download.DownloadProvider
import org.jackhuang.hmcl.download.MojangDownloadProvider
import org.jackhuang.hmcl.util.GSON
import org.jackhuang.hmcl.util.LOG
import java.io.File
import java.util.logging.Level
import org.jackhuang.hmcl.ProfileLoadingEvent
import org.jackhuang.hmcl.ProfileChangedEvent
import org.jackhuang.hmcl.event.EVENT_BUS
import org.jackhuang.hmcl.util.FileTypeAdapter


object Settings {
    val GSON = GsonBuilder()
            .registerTypeAdapter(VersionSetting::class.java, VersionSetting)
            .registerTypeAdapter(Profile::class.java, Profile)
            .registerTypeAdapter(File::class.java, FileTypeAdapter)
            .setPrettyPrinting().create()

    val DEFAULT_PROFILE = "Default"
    val HOME_PROFILE = "Home"

    val SETTINGS_FILE = File("hmcl.json").absoluteFile

    val SETTINGS: Config

    init {
        SETTINGS = initSettings();
        save()

        if (!getProfiles().containsKey(DEFAULT_PROFILE))
            getProfiles().put(DEFAULT_PROFILE, Profile());

        for ((name, profile) in getProfiles().entries) {
            profile.name = name
            profile.addPropertyChangedListener(InvalidationListener { save() })
        }
    }

    fun getDownloadProvider(): DownloadProvider = when (SETTINGS.downloadtype) {
        0 -> MojangDownloadProvider
        1 -> BMCLAPIDownloadProvider
        else -> MojangDownloadProvider
    }

    private fun initSettings(): Config {
        var c = Config()
        if (SETTINGS_FILE.exists())
            try {
                val str = SETTINGS_FILE.readText()
                if (str.trim() == "")
                    LOG.finer("Settings file is empty, use the default settings.")
                else {
                    val d = GSON.fromJson(str, Config::class.java)
                    if (d != null)
                        c = d
                }
                LOG.finest("Initialized settings.")
            } catch (e: Exception) {
                LOG.log(Level.WARNING, "Something happened wrongly when load settings.", e)
            }
        else {
            LOG.config("No settings file here, may be first loading.")
            if (!c.configurations.containsKey(HOME_PROFILE))
                c.configurations[HOME_PROFILE] = Profile(HOME_PROFILE, MainApplication.getMinecraftDirectory())
        }
        return c
    }

    fun save() {
        try {
            SETTINGS_FILE.writeText(GSON.toJson(SETTINGS))
        } catch (ex: IOException) {
            LOG.log(Level.SEVERE, "Failed to save config", ex)
        }
    }

    fun getLastProfile(): Profile {
        if (!hasProfile(SETTINGS.last))
            SETTINGS.last = DEFAULT_PROFILE
        return getProfile(SETTINGS.last)
    }

    fun getProfile(name: String?): Profile {
        var p: Profile? = getProfiles()[name ?: DEFAULT_PROFILE]
        if (p == null)
            if (getProfiles().containsKey(DEFAULT_PROFILE))
                p = getProfiles()[DEFAULT_PROFILE]!!
            else {
                p = Profile()
                getProfiles().put(DEFAULT_PROFILE, p)
            }
        return p
    }

    fun hasProfile(name: String?): Boolean {
        return getProfiles().containsKey(name ?: DEFAULT_PROFILE)
    }

    fun getProfiles(): MutableMap<String, Profile> {
        return SETTINGS.configurations
    }

    fun getProfilesFiltered(): Collection<Profile> {
        return getProfiles().values.filter { t -> t.name.isNotBlank()  }
    }

    fun putProfile(ver: Profile?): Boolean {
        if (ver == null || ver.name.isBlank() || getProfiles().containsKey(ver.name))
            return false
        getProfiles().put(ver.name, ver)
        return true
    }

    fun delProfile(ver: Profile): Boolean {
        return delProfile(ver.name)
    }

    fun delProfile(ver: String): Boolean {
        if (DEFAULT_PROFILE == ver) {
            return false
        }
        var notify = false
        if (getLastProfile().name == ver)
            notify = true
        val flag = getProfiles().remove(ver) != null
        if (notify && flag)
            onProfileChanged()
        return flag
    }

    internal fun onProfileChanged() {
        val p = getLastProfile()
        EVENT_BUS.fireEvent(ProfileChangedEvent(SETTINGS, p))
        p.repository.refreshVersions()
    }

    /**
     * Start profiles loading process.
     * Invoked by loading GUI phase.
     */
    fun onProfileLoading() {
        EVENT_BUS.fireEvent(ProfileLoadingEvent(SETTINGS))
        onProfileChanged()
    }
}