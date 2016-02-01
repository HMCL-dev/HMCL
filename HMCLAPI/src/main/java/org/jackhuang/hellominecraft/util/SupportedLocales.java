/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2013  huangyuhui <huanghongxun2008@126.com>
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
package org.jackhuang.hellominecraft.util;

import java.util.Locale;
import java.util.ResourceBundle;

/**
 *
 * @author huangyuhui
 */
public enum SupportedLocales {
    def(Locale.getDefault(), "lang.default"), en(Locale.ENGLISH, null), zh_TW(Locale.TRADITIONAL_CHINESE, null), zh_CN(Locale.SIMPLIFIED_CHINESE, null);

    public Locale self;
    private String showString, customized;
    private ResourceBundle bundle;

    private SupportedLocales(Locale self, String customized) {
        this.self = self;

        try {
            bundle = ResourceBundle.getBundle("org/jackhuang/hellominecraft/lang/I18N", self);
            showString = bundle.getString("lang");
            this.customized = customized;
        } catch (Throwable t) {
            showString = self.toString();
            t.printStackTrace();
        }
    }

    public String showString() {
        if (customized == null)
            return showString;
        else
            return NOW_LOCALE.translate(customized);
    }

    public static SupportedLocales NOW_LOCALE = def;

    public String translate(String key, Object... format) {
        try {
            return bundle.getString(key);
        } catch (Exception ex) {
            ex.printStackTrace();
            return key;
        }
    }
}
