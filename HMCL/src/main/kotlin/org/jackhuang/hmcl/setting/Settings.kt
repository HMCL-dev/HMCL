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
import org.jackhuang.hmcl.Main
import org.jackhuang.hmcl.download.BMCLAPIDownloadProvider
import org.jackhuang.hmcl.download.DownloadProvider
import org.jackhuang.hmcl.download.MojangDownloadProvider
import org.jackhuang.hmcl.util.LOG
import java.io.File
import java.util.logging.Level
import org.jackhuang.hmcl.ProfileLoadingEvent
import org.jackhuang.hmcl.ProfileChangedEvent
import org.jackhuang.hmcl.auth.Account
import org.jackhuang.hmcl.util.*
import org.jackhuang.hmcl.event.EVENT_BUS
import org.jackhuang.hmcl.util.property.ImmediateObjectProperty
import java.net.Authenticator
import java.net.InetSocketAddress
import java.net.PasswordAuthentication
import java.net.Proxy
import java.util.*

object Settings {
    val GSON = GsonBuilder()
            .registerTypeAdapter(VersionSetting::class.java, VersionSetting)
            .registerTypeAdapter(Profile::class.java, Profile)
            .registerTypeAdapter(File::class.java, FileTypeAdapter)
            .setPrettyPrinting().create()

    val DEFAULT_PROFILE = "Default"
    val HOME_PROFILE = "Home"

    val SETTINGS_FILE = File("hmcl.json").absoluteFile

    private val SETTINGS: Config

    private val ACCOUNTS = mutableMapOf<String, Account>()

    init {
        SETTINGS = initSettings();

        loop@for ((name, settings) in SETTINGS.accounts) {
            val factory = Accounts.ACCOUNT_FACTORY[settings["type"] ?: ""]
            if (factory == null) {
                SETTINGS.accounts.remove(name)
                continue@loop
            }

            val account = factory.fromStorage(settings)

            if (account.username != name) {
                SETTINGS.accounts.remove(name)
                continue
            }

            ACCOUNTS[name] = account
        }

        save()

        if (!getProfileMap().containsKey(DEFAULT_PROFILE))
            getProfileMap().put(DEFAULT_PROFILE, Profile());

        for ((name, profile) in getProfileMap().entries) {
            profile.name = name
            profile.addPropertyChangedListener(InvalidationListener { save() })
        }

        ignoreException {
            Runtime.getRuntime().addShutdownHook(Thread(this::save))
        }
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
                c.configurations[HOME_PROFILE] = Profile(HOME_PROFILE, Main.getMinecraftDirectory())
        }
        return c
    }

    fun save() {
        try {
            SETTINGS.accounts.clear()
            for ((name, account) in ACCOUNTS) {
                val storage = account.toStorage()
                storage["type"] = Accounts.getAccountType(account)
                SETTINGS.accounts[name] = storage
            }

            SETTINGS_FILE.writeText(GSON.toJson(SETTINGS))
        } catch (ex: IOException) {
            LOG.log(Level.SEVERE, "Failed to save config", ex)
        }
    }

    var LANG: Locales.SupportedLocale = Locales.getLocaleByName(SETTINGS.localization)
        set(value) {
            field = value
            SETTINGS.localization = Locales.getNameByLocal(value)
        }

    var PROXY: Proxy = Proxy.NO_PROXY
    var PROXY_TYPE: Proxy.Type? = Proxies.getProxyType(SETTINGS.proxyType)
        set(value) {
            field = value
            SETTINGS.proxyType = Proxies.PROXIES.indexOf(value)
            loadProxy()
        }

    var PROXY_HOST: String? get() = SETTINGS.proxyHost; set(value) { SETTINGS.proxyHost = value }
    var PROXY_PORT: String? get() = SETTINGS.proxyPort; set(value) { SETTINGS.proxyPort = value }
    var PROXY_USER: String? get() = SETTINGS.proxyUserName; set(value) { SETTINGS.proxyUserName = value }
    var PROXY_PASS: String? get() = SETTINGS.proxyPassword; set(value) { SETTINGS.proxyPassword = value }

    private fun loadProxy() {
        val host = PROXY_HOST
        val port = PROXY_PORT?.toIntOrNull()
        if (host == null || host.isBlank() || port == null)
            PROXY = Proxy.NO_PROXY
        else {
            System.setProperty("http.proxyHost", PROXY_HOST)
            System.setProperty("http.proxyPort", PROXY_PORT)
            PROXY = Proxy(PROXY_TYPE, InetSocketAddress(host, port))

            val user = PROXY_USER
            val pass = PROXY_PASS
            if (user != null && user.isNotBlank() && pass != null && pass.isNotBlank()) {
                System.setProperty("http.proxyUser", user)
                System.setProperty("http.proxyPassword", pass)

                Authenticator.setDefault(object : Authenticator() {
                    override fun getPasswordAuthentication(): PasswordAuthentication {
                        return PasswordAuthentication(user, pass.toCharArray())
                    }
                })
            }
        }
    }

    init { loadProxy() }

    var DOWNLOAD_PROVIDER: DownloadProvider
        get() = when (SETTINGS.downloadtype) {
            0 -> MojangDownloadProvider
            1 -> BMCLAPIDownloadProvider
            else -> MojangDownloadProvider
        }
        set(value) {
            SETTINGS.downloadtype = when (value) {
                MojangDownloadProvider -> 0
                BMCLAPIDownloadProvider -> 1
                else -> 0
            }
        }

    /****************************************
     *               ACCOUNTS               *
     ****************************************/

    val selectedAccountProperty = object : ImmediateObjectProperty<Account?>(this, "selectedAccount", getAccount(SETTINGS.selectedAccount)) {
        override fun get(): Account? {
            val a = super.get()
            if (a == null || !ACCOUNTS.containsKey(a.username)) {
                val acc = if (ACCOUNTS.isEmpty()) null else ACCOUNTS.values.first()
                set(acc)
                return acc
            } else return a
        }

        override fun set(newValue: Account?) {
            if (newValue == null || ACCOUNTS.containsKey(newValue.username)) {
                super.set(newValue)
            }
        }

        override fun invalidated() {
            super.invalidated()

            SETTINGS.selectedAccount = value?.username ?: ""
        }
    }
    var selectedAccount: Account? by selectedAccountProperty

    fun addAccount(account: Account) {
        ACCOUNTS[account.username] = account
    }

    fun getAccount(name: String): Account? {
        return ACCOUNTS[name]
    }

    fun getAccounts(): Map<String, Account> {
        return Collections.unmodifiableMap(ACCOUNTS)
    }

    fun deleteAccount(name: String) {
        ACCOUNTS.remove(name)

        selectedAccountProperty.get()
    }

    /****************************************
     *               PROFILES               *
     ****************************************/

    val selectedProfile: Profile
        get() {
            if (!hasProfile(SETTINGS.selectedProfile))
                SETTINGS.selectedProfile = DEFAULT_PROFILE
            return getProfile(SETTINGS.selectedProfile)
        }

    fun getProfile(name: String?): Profile {
        var p: Profile? = getProfileMap()[name ?: DEFAULT_PROFILE]
        if (p == null)
            if (getProfileMap().containsKey(DEFAULT_PROFILE))
                p = getProfileMap()[DEFAULT_PROFILE]!!
            else {
                p = Profile()
                getProfileMap().put(DEFAULT_PROFILE, p)
            }
        return p
    }

    fun hasProfile(name: String?): Boolean {
        return getProfileMap().containsKey(name ?: DEFAULT_PROFILE)
    }

    fun getProfileMap(): MutableMap<String, Profile> {
        return SETTINGS.configurations
    }

    fun getProfiles(): Collection<Profile> {
        return getProfileMap().values.filter { t -> t.name.isNotBlank()  }
    }

    fun putProfile(ver: Profile?): Boolean {
        if (ver == null || ver.name.isBlank() || getProfileMap().containsKey(ver.name))
            return false
        getProfileMap().put(ver.name, ver)
        return true
    }

    fun deleteProfile(ver: Profile): Boolean {
        return deleteProfile(ver.name)
    }

    fun deleteProfile(ver: String): Boolean {
        if (DEFAULT_PROFILE == ver) {
            return false
        }
        var notify = false
        if (selectedProfile.name == ver)
            notify = true
        val flag = getProfileMap().remove(ver) != null
        if (notify && flag)
            onProfileChanged()
        return flag
    }

    internal fun onProfileChanged() {
        selectedProfile.repository.refreshVersions()
        EVENT_BUS.fireEvent(ProfileChangedEvent(SETTINGS, selectedProfile))
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