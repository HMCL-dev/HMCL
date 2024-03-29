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

import org.jackhuang.hmcl.util.i18n.Locales.SupportedLocale;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class I18n {

    private I18n() {
    }

    private static volatile SupportedLocale locale = Locales.DEFAULT;
    private static volatile ResourceBundle resourceBundle = locale.getResourceBundle();
    private static volatile DateTimeFormatter dateTimeFormatter;

    public static void setLocale(SupportedLocale locale) {
        I18n.locale = locale;
        resourceBundle = locale.getResourceBundle();
        dateTimeFormatter = null;
    }

    public static boolean isUseChinese() {
        return locale.getLocale() == Locale.CHINA;
    }

    public static ResourceBundle getResourceBundle() {
        return resourceBundle;
    }

    public static String getName(SupportedLocale locale) {
        return locale == Locales.DEFAULT ? resourceBundle.getString("lang.default") : locale.getResourceBundle().getString("lang");
    }

    public static String i18n(String key, Object... formatArgs) {
        try {
            return String.format(getResourceBundle().getString(key), formatArgs);
        } catch (MissingResourceException e) {
            LOG.error("Cannot find key " + key + " in resource bundle", e);
        } catch (IllegalFormatException e) {
            LOG.error("Illegal format string, key=" + key + ", args=" + Arrays.toString(formatArgs), e);
        }

        return key + Arrays.toString(formatArgs);
    }

    public static String i18n(String key) {
        try {
            return getResourceBundle().getString(key);
        } catch (MissingResourceException e) {
            LOG.error("Cannot find key " + key + " in resource bundle", e);
            return key;
        }
    }

    public static String formatDateTime(Instant instant) {
        DateTimeFormatter formatter = dateTimeFormatter;
        if (formatter == null) {
            formatter = dateTimeFormatter = DateTimeFormatter.ofPattern(getResourceBundle().getString("world.time")).withZone(ZoneId.systemDefault());
        }

        return formatter.format(instant);
    }

    public static boolean hasKey(String key) {
        return getResourceBundle().containsKey(key);
    }
}
