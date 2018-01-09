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
package org.jackhuang.hmcl.setting;

import org.jackhuang.hmcl.ui.construct.UTF8Control;
import org.jackhuang.hmcl.util.Lang;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

public final class Locales {

    public static final SupportedLocale DEFAULT = new SupportedLocale(Locale.getDefault(), "lang.default");

    /**
     * English
     */
    public static final SupportedLocale EN = new SupportedLocale(Locale.ENGLISH);

    /**
     * Traditional Chinese
     */
    public static final SupportedLocale ZH = new SupportedLocale(Locale.TRADITIONAL_CHINESE);

    /**
     * Simplified Chinese
     */
    public static final SupportedLocale ZH_CN = new SupportedLocale(Locale.SIMPLIFIED_CHINESE);

    /**
     * Vietnamese
     */
    public static final SupportedLocale VI = new SupportedLocale(new Locale("vi"));

    /**
     * Russian
     */
    public static final SupportedLocale RU = new SupportedLocale(new Locale("ru"));

    public static final List<SupportedLocale> LOCALES = Arrays.asList(DEFAULT, EN, ZH, ZH_CN, VI, RU);

    public static SupportedLocale getLocale(int index) {
        return Lang.get(LOCALES, index).orElse(DEFAULT);
    }

    public static SupportedLocale getLocaleByName(String name) {
        switch (name.toLowerCase()) {
            case "en": return EN;
            case "zh": return ZH;
            case "zh_cn": return ZH_CN;
            case "vi": return VI;
            case "ru": return RU;
            default: return DEFAULT;
        }
    }

    public static String getNameByLocale(SupportedLocale locale) {
        if (locale == EN) return "en";
        else if (locale == ZH) return "zh";
        else if (locale == ZH_CN) return "zh_CN";
        else if (locale == VI) return "vi";
        else if (locale == RU) return "ru";
        else if (locale == DEFAULT) return "def";
        else throw new IllegalArgumentException("Unknown locale: " + locale);
    }

    public static class SupportedLocale {
        private final Locale locale;
        private final String name;
        private final ResourceBundle resourceBundle;

        SupportedLocale(Locale locale) {
            this(locale, null);
        }

        SupportedLocale(Locale locale, String name) {
            this.locale = locale;
            this.name = name;
            resourceBundle = ResourceBundle.getBundle("assets.lang.I18N", locale, UTF8Control.INSTANCE);
        }

        public Locale getLocale() {
            return locale;
        }

        public ResourceBundle getResourceBundle() {
            return resourceBundle;
        }

        public String getName(ResourceBundle newResourceBundle) {
            if (name == null) return resourceBundle.getString("lang");
            else return newResourceBundle.getString(name);
        }
    }
}
