/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
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

import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import org.jackhuang.hmcl.util.Lang;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

public final class Locales {

    public static final SupportedLocale DEFAULT = new SupportedLocale(Locale.getDefault(), "lang.default");

    /**
     * English
     */
    public static final SupportedLocale EN = new SupportedLocale(Locale.ROOT);

    /**
     * Traditional Chinese
     */
    public static final SupportedLocale ZH = new SupportedLocale(Locale.TRADITIONAL_CHINESE);

    /**
     * Simplified Chinese
     */
    public static final SupportedLocale ZH_CN = new SupportedLocale(Locale.SIMPLIFIED_CHINESE);

    /**
     * Spanish
     */
    public static final SupportedLocale ES = new SupportedLocale(new Locale("es"));

    /**
     * Russian
     */
    public static final SupportedLocale RU = new SupportedLocale(new Locale("ru"));

    public static final List<SupportedLocale> LOCALES = Lang.immutableListOf(DEFAULT, EN, ZH_CN, ZH, ES, RU);

    public static SupportedLocale getLocaleByName(String name) {
        if (name == null) return DEFAULT;
        switch (name.toLowerCase()) {
            case "en": return EN;
            case "zh": return ZH;
            case "zh_cn": return ZH_CN;
            case "es": return ES;
            case "ru": return RU;
            default: return DEFAULT;
        }
    }

    public static String getNameByLocale(SupportedLocale locale) {
        if (locale == EN) return "en";
        else if (locale == ZH) return "zh";
        else if (locale == ZH_CN) return "zh_CN";
        else if (locale == ES) return "es";
        else if (locale == RU) return "ru";
        else if (locale == DEFAULT) return "def";
        else throw new IllegalArgumentException("Unknown locale: " + locale);
    }

    @JsonAdapter(SupportedLocale.TypeAdapter.class)
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

        public static class TypeAdapter extends com.google.gson.TypeAdapter<SupportedLocale> {
            @Override
            public void write(JsonWriter out, SupportedLocale value) throws IOException {
                out.value(getNameByLocale(value));
            }

            @Override
            public SupportedLocale read(JsonReader in) throws IOException {
                return getLocaleByName(in.nextString());
            }
        }
    }
}
