/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
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
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.jackhuang.hmcl.util.i18n;

import org.jackhuang.hmcl.download.RemoteVersion;
import org.jackhuang.hmcl.download.game.GameRemoteVersion;
import org.jackhuang.hmcl.util.i18n.Locales.SupportedLocale;

import java.time.temporal.TemporalAccessor;
import java.util.*;

public final class I18n {

    private I18n() {
    }

    private static volatile SupportedLocale locale = Locales.DEFAULT;

    public static void setLocale(SupportedLocale locale) {
        I18n.locale = locale;
    }

    public static SupportedLocale getLocale() {
        return locale;
    }

    public static boolean isUseChinese() {
        return LocaleUtils.isChinese(locale.getLocale());
    }

    public static ResourceBundle getResourceBundle() {
        return locale.getResourceBundle();
    }

    public static String i18n(String key, Object... formatArgs) {
        return locale.i18n(key, formatArgs);
    }

    public static String i18n(String key) {
        return locale.i18n(key);
    }

    public static String formatDateTime(TemporalAccessor time) {
        return locale.formatDateTime(time);
    }

    public static String getDisplaySelfVersion(RemoteVersion version) {
        return locale.getDisplaySelfVersion(version);
    }

    public static String getWikiLink(GameRemoteVersion remoteVersion) {
        return MinecraftWiki.getWikiLink(locale, remoteVersion);
    }

    public static boolean hasKey(String key) {
        return getResourceBundle().containsKey(key);
    }
}
