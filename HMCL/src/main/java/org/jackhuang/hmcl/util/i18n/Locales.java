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
import org.jackhuang.hmcl.download.RemoteVersion;
import org.jackhuang.hmcl.download.game.GameRemoteVersion;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;

import java.io.IOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

public final class Locales {
    private Locales() {
    }

    public static final SupportedLocale DEFAULT = new SupportedLocale(Locale.getDefault());

    /**
     * English
     */
    public static final SupportedLocale EN = new SupportedLocale(Locale.ROOT);

    /**
     * Spanish
     */
    public static final SupportedLocale ES = new SupportedLocale(Locale.forLanguageTag("es"));

    /**
     * Russian
     */
    public static final SupportedLocale RU = new SupportedLocale(Locale.forLanguageTag("ru"));

    /**
     * Japanese
     */
    public static final SupportedLocale JA = new SupportedLocale(Locale.JAPANESE);

    /**
     * Traditional Chinese
     */
    public static final SupportedLocale ZH = new SupportedLocale(Locale.TRADITIONAL_CHINESE);

    /**
     * Simplified Chinese
     */
    public static final SupportedLocale ZH_CN = new SupportedLocale(Locale.SIMPLIFIED_CHINESE);

    /**
     * Wenyan (Classical Chinese)
     */
    public static final SupportedLocale WENYAN = new SupportedLocale(Locale.forLanguageTag("lzh")) {
        @Override
        public String formatDateTime(TemporalAccessor time) {
            return WenyanUtils.formatDateTime(time);
        }

        @Override
        public String getDisplayVersion(RemoteVersion version) {
            if (version instanceof GameRemoteVersion)
                return WenyanUtils.translateGameVersion(GameVersionNumber.asGameVersion(version.getSelfVersion()));
            return super.getDisplayVersion(version);
        }
    };

    public static final List<SupportedLocale> LOCALES = List.of(DEFAULT, EN, ES, JA, RU, ZH_CN, ZH, WENYAN);

    public static SupportedLocale getLocaleByName(String name) {
        if (name == null) return DEFAULT;
        switch (name.toLowerCase(Locale.ROOT)) {
            case "en":
                return EN;
            case "es":
                return ES;
            case "ja":
                return JA;
            case "ru":
                return RU;
            case "zh":
                return ZH;
            case "zh_cn":
                return ZH_CN;
            case "lzh":
                return WENYAN;
            default:
                return DEFAULT;
        }
    }

    public static String getNameByLocale(SupportedLocale locale) {
        if (locale == EN) return "en";
        else if (locale == ES) return "es";
        else if (locale == RU) return "ru";
        else if (locale == JA) return "ja";
        else if (locale == ZH) return "zh";
        else if (locale == ZH_CN) return "zh_CN";
        else if (locale == WENYAN) return "lzh";
        else if (locale == DEFAULT) return "def";
        else throw new IllegalArgumentException("Unknown locale: " + locale);
    }

    @JsonAdapter(SupportedLocale.TypeAdapter.class)
    public static class SupportedLocale {
        private final Locale locale;
        private ResourceBundle resourceBundle;
        private DateTimeFormatter dateTimeFormatter;

        SupportedLocale(Locale locale) {
            this.locale = locale;
        }

        public Locale getLocale() {
            return locale;
        }

        public ResourceBundle getResourceBundle() {
            ResourceBundle bundle = resourceBundle;

            if (resourceBundle == null) {
                if (this != DEFAULT && this.locale == DEFAULT.locale) {
                    bundle = DEFAULT.getResourceBundle();
                } else {
                    bundle = ResourceBundle.getBundle("assets.lang.I18N", locale, new ResourceBundle.Control() {
                        @Override
                        public List<Locale> getCandidateLocales(String baseName, Locale locale) {
                            if (locale.getLanguage().equals("zh")) {
                                boolean simplified;

                                String script = locale.getScript();
                                String region = locale.getCountry();
                                if (script.isEmpty())
                                    simplified = region.equals("CN") || region.equals("SG");
                                else
                                    simplified = script.equals("Hans");

                                if (simplified) {
                                    return List.of(
                                            Locale.SIMPLIFIED_CHINESE,
                                            Locale.CHINESE,
                                            Locale.ROOT
                                    );
                                }
                            }

                            if (locale.getLanguage().equals("lzh")) {
                                return List.of(
                                        locale,
                                        Locale.CHINESE,
                                        Locale.ROOT
                                );
                            }
                            return super.getCandidateLocales(baseName, locale);
                        }
                    });
                }
                resourceBundle = bundle;
            }

            return bundle;
        }

        public String formatDateTime(TemporalAccessor time) {
            DateTimeFormatter formatter = dateTimeFormatter;
            if (formatter == null)
                formatter = dateTimeFormatter = DateTimeFormatter.ofPattern(getResourceBundle().getString("datetime.format"))
                        .withZone(ZoneId.systemDefault());
            return formatter.format(time);
        }

        public String getDisplayVersion(RemoteVersion version) {
            return version.getSelfVersion();
        }

        public static final class TypeAdapter extends com.google.gson.TypeAdapter<SupportedLocale> {
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
