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

import java.util.ResourceBundle;
import java.util.logging.Level;

import org.jackhuang.hmcl.setting.Settings;
import org.jackhuang.hmcl.util.Logging;

public final class I18n {

    public static final ResourceBundle RESOURCE_BUNDLE = Settings.INSTANCE.getLocale().getResourceBundle();

    private I18n() {}

    public static String i18n(String key, Object... formatArgs) {
        return String.format(I18n.i18n(key), formatArgs);
    }

    public static String i18n(String key) {
        try {
            return RESOURCE_BUNDLE.getString(key);
        } catch (Exception e) {
            Logging.LOG.log(Level.SEVERE, "Cannot find key " + key + " in resource bundle", e);
            return key;
        }
    }

}
