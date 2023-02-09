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

import org.jackhuang.hmcl.setting.ConfigHolder;
import org.jackhuang.hmcl.util.i18n.Locales.SupportedLocale;

import java.util.Arrays;
import java.util.IllegalFormatException;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.logging.Level;

import static org.jackhuang.hmcl.util.Logging.LOG;

public final class I18n {

    private I18n() {}

    public static SupportedLocale getCurrentLocale() {
        try {
            return ConfigHolder.config().getLocalization();
        } catch (IllegalStateException e) {
            // e is thrown by ConfigHolder.config(), indicating the config hasn't been loaded
            // fallback to use default locale
            return Locales.DEFAULT;
        }
    }

    public static ResourceBundle getResourceBundle() {
        return getCurrentLocale().getResourceBundle();
    }

    public static String i18n(String key, Object... formatArgs) {
        try {
            return String.format(i18n(key), formatArgs);
        } catch (IllegalFormatException e) {
            LOG.log(Level.SEVERE, "Illegal format string", e);
            return key + Arrays.toString(formatArgs);
        }
    }

    public static String i18n(String key) {
        try {
            return getResourceBundle().getString(key);
        } catch (MissingResourceException e) {
            LOG.log(Level.SEVERE, "Cannot find key " + key + " in resource bundle", e);
            return key;
        }
    }

    public static boolean hasKey(String key) {
        try {
            getResourceBundle().getString(key);
            return true;
        } catch (MissingResourceException e) {
            return false;
        }
    }
}
