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
import org.jackhuang.hmcl.util.i18n.translator.Translator;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.time.temporal.TemporalAccessor;
import java.util.*;

public final class I18n {

    private I18n() {
    }

    private static volatile SupportedLocale locale = SupportedLocale.DEFAULT;

    public static void setLocale(SupportedLocale locale) {
        I18n.locale = locale;
    }

    public static SupportedLocale getLocale() {
        return locale;
    }

    public static boolean isUpsideDown() {
        return LocaleUtils.getScript(locale.getDisplayLocale()).equals("Qabs");
    }

    public static boolean isUseChinese() {
        return LocaleUtils.isChinese(locale.getDisplayLocale());
    }

    public static ResourceBundle getResourceBundle() {
        return locale.getResourceBundle();
    }

    public static Translator getTranslator() {
        return locale.getTranslator();
    }

    public static String i18n(@PropertyKey(resourceBundle = "assets.lang.I18N") String key, Object... formatArgs) {
        return locale.i18n(key, formatArgs);
    }

    public static String i18n(@PropertyKey(resourceBundle = "assets.lang.I18N") String key) {
        return locale.i18n(key);
    }

    public static String formatDateTime(TemporalAccessor time) {
        return getTranslator().formatDateTime(time);
    }

    public static String formatSpeed(long bytes) {
        return getTranslator().formatSpeed(bytes);
    }

    public static String getDisplayVersion(RemoteVersion version) {
        return getTranslator().getDisplayVersion(version);
    }

    public static String getDisplayVersion(GameVersionNumber version) {
        return getTranslator().getDisplayVersion(version);
    }

    /// Find the builtin localized resource with given name and suffix.
    ///
    /// For example, if the current locale is `zh-CN`, when calling `getBuiltinResource("assets.lang.foo", "json")`,
    /// this method will look for the following built-in resources in order:
    ///
    ///  - `assets/lang/foo_zh_Hans_CN.json`
    ///  - `assets/lang/foo_zh_Hans.json`
    ///  - `assets/lang/foo_zh_CN.json`
    ///  - `assets/lang/foo_zh.json`
    ///  - `assets/lang/foo.json`
    ///
    /// This method will return the first found resource;
    /// if none of the above resources exist, it returns `null`.
    public static @Nullable URL getBuiltinResource(String name, String suffix) {
        var control = DefaultResourceBundleControl.INSTANCE;
        var classLoader = I18n.class.getClassLoader();
        for (Locale locale : locale.getCandidateLocales()) {
            String resourceName = control.toResourceName(control.toBundleName(name, locale), suffix);
            URL input = classLoader.getResource(resourceName);
            if (input != null)
                return input;
        }
        return null;
    }

    /// @see [#getBuiltinResource(String, String) ]
    public static @Nullable InputStream getBuiltinResourceAsStream(String name, String suffix) {
        URL resource = getBuiltinResource(name, suffix);
        try {
            return resource != null ? resource.openStream() : null;
        } catch (IOException e) {
            return null;
        }
    }

    public static String getWikiLink(GameRemoteVersion remoteVersion) {
        return MinecraftWiki.getWikiLink(locale, remoteVersion);
    }

    public static boolean hasKey(String key) {
        return getResourceBundle().containsKey(key);
    }
}
