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

import org.jackhuang.hmcl.auth.Account
import org.jackhuang.hmcl.auth.AccountFactory
import org.jackhuang.hmcl.auth.OfflineAccount
import org.jackhuang.hmcl.auth.yggdrasil.YggdrasilAccount
import org.jackhuang.hmcl.download.BMCLAPIDownloadProvider
import org.jackhuang.hmcl.download.MojangDownloadProvider
import org.jackhuang.hmcl.ui.UTF8Control
import java.net.Proxy
import java.util.*

object Proxies {
    val PROXIES = listOf(null, Proxy.Type.DIRECT, Proxy.Type.HTTP, Proxy.Type.SOCKS)

    fun getProxyType(index: Int): Proxy.Type? = PROXIES.getOrNull(index)
}

object DownloadProviders {
    val DOWNLOAD_PROVIDERS = listOf(MojangDownloadProvider, BMCLAPIDownloadProvider)

    fun getDownloadProvider(index: Int) = DOWNLOAD_PROVIDERS.getOrElse(index, { MojangDownloadProvider })
}

object Accounts {
    val OFFLINE_ACCOUNT_KEY = "offline"
    val YGGDRASIL_ACCOUNT_KEY = "yggdrasil"

    val ACCOUNTS = listOf(OfflineAccount, YggdrasilAccount)
    val ACCOUNT_FACTORY = mapOf<String, AccountFactory<*>>(
            OFFLINE_ACCOUNT_KEY to OfflineAccount,
            YGGDRASIL_ACCOUNT_KEY to YggdrasilAccount
    )

    fun getAccountType(account: Account): String {
        return when (account) {
            is OfflineAccount -> OFFLINE_ACCOUNT_KEY
            is YggdrasilAccount -> YGGDRASIL_ACCOUNT_KEY
            else -> YGGDRASIL_ACCOUNT_KEY
        }
    }
}

object Locales {
    class SupportedLocale internal constructor(
            val locale: Locale,
            private val nameImpl: String? = null
    ) {
        val resourceBundle: ResourceBundle = ResourceBundle.getBundle("assets.lang.I18N", locale, UTF8Control)

        fun getName(nowResourceBundle: ResourceBundle): String {
            if (nameImpl == null)
                return resourceBundle.getString("lang")
            else
                return nowResourceBundle.getString(nameImpl)
        }
    }

    val DEFAULT = SupportedLocale(Locale.getDefault(), "lang.default")
    val EN = SupportedLocale(Locale.ENGLISH)
    val ZH = SupportedLocale(Locale.TRADITIONAL_CHINESE)
    val ZH_CN = SupportedLocale(Locale.SIMPLIFIED_CHINESE)
    val VI = SupportedLocale(Locale("vi"))
    val RU = SupportedLocale(Locale("ru"))

    val LOCALES = listOf(DEFAULT, EN, ZH, ZH_CN, VI, RU)

    fun getLocale(index: Int) = LOCALES.getOrElse(index, { DEFAULT })
    fun getLocaleByName(name: String?) = when(name) {
        "en" -> EN
        "zh" -> ZH
        "zh_CN" -> ZH_CN
        "vi" -> VI
        "ru" -> RU
        else -> DEFAULT
    }

    fun getNameByLocal(supportedLocale: SupportedLocale) = when(supportedLocale) {
        EN -> "en"
        ZH -> "zh"
        ZH_CN -> "zh_CN"
        VI -> "vi"
        RU -> "ru"
        DEFAULT -> "def"
        else -> throw IllegalArgumentException("Unknown argument: " + supportedLocale)
    }
}