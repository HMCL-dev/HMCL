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

import com.google.gson.annotations.SerializedName
import org.jackhuang.hmcl.MainApplication
import org.jackhuang.hmcl.util.JavaVersion
import java.io.File
import java.util.TreeMap

class Config {
    @SerializedName("last")
    var selectedProfile: String = ""
        set(value) {
            field = value
            Settings.save()
        }
    @SerializedName("bgpath")
    var bgpath: String? = null
        set(value) {
            field = value
            Settings.save()
        }
    @SerializedName("commonpath")
    var commonpath: File = MainApplication.getMinecraftDirectory()
        set(value) {
            field = value
            Settings.save()
        }
    @SerializedName("proxyHost")
    var proxyHost: String? = null
        set(value) {
            field = value
            Settings.save()
        }
    @SerializedName("proxyPort")
    var proxyPort: String? = null
        set(value) {
            field = value
            Settings.save()
        }
    @SerializedName("proxyUserName")
    var proxyUserName: String? = null
        set(value) {
            field = value
            Settings.save()
        }
    @SerializedName("proxyPassword")
    var proxyPassword: String? = null
        set(value) {
            field = value
            Settings.save()
        }
    @SerializedName("theme")
    var theme: String? = null
        set(value) {
            field = value
            Settings.save()
        }
    @SerializedName("java")
    var java: List<JavaVersion>? = null
        set(value) {
            field = value
            Settings.save()
        }
    @SerializedName("localization")
    var localization: String? = null
        set(value) {
            field = value
            Settings.save()
        }
    @SerializedName("downloadtype")
    var downloadtype: Int = 0
        set(value) {
            field = value
            Settings.save()
        }
    @SerializedName("configurations")
    var configurations: MutableMap<String, Profile> = TreeMap()
        set(value) {
            field = value
            Settings.save()
        }
    @SerializedName("accounts")
    var accounts: MutableMap<String, MutableMap<Any, Any>> = TreeMap()
        set(value) {
            field = value
            Settings.save()
        }
    @SerializedName("selectedAccount")
    var selectedAccount: String = ""
        set(value) {
            field = value
            Settings.save()
        }
    @SerializedName("fontFamily")
    var fontFamily: String? = null
        set(value) {
            field = value
            Settings.save()
        }
    @SerializedName("fontSize")
    var fontSize: Int = 12
        set(value) {
            field = value
            Settings.save()
        }
    @SerializedName("logLines")
    var logLines: Int = 100
        set(value) {
            field = value
            Settings.save()
        }
}