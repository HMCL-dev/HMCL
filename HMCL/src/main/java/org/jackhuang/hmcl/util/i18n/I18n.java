/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2018  huangyuhui <huanghongxun2008@126.com>
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
package org.jackhuang.hmcl.util.i18n;

import static org.jackhuang.hmcl.util.Logging.LOG;

import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.logging.Level;

import org.jackhuang.hmcl.setting.ConfigHolder;
import org.jackhuang.hmcl.setting.Settings;

public final class I18n {

    private I18n() {}

    public static ResourceBundle getResourceBundle() {
        if (ConfigHolder.isInitialized()) {
            return Settings.instance().getLocale().getResourceBundle();
        } else {
            return Locales.DEFAULT.getResourceBundle();
        }
    }

    public static String i18n(String key, Object... formatArgs) {
        return String.format(i18n(key), formatArgs);
    }

    public static String i18n(String key) {
        try {
            return getResourceBundle().getString(key);
        } catch (MissingResourceException e) {
            LOG.log(Level.SEVERE, "Cannot find key " + key + " in resource bundle", e);
            return key;
        }
    }

}
