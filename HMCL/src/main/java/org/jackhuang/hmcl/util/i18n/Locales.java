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
import org.jackhuang.hmcl.util.StringUtils;

import java.io.IOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.*;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class Locales {
    private Locales() {
    }

    private static Locale getDefaultLocale() {
        String language = System.getenv("HMCL_LANGUAGE");
        if (StringUtils.isNotBlank(language))
            return Locale.forLanguageTag(language);
        else
            return LocaleUtils.SYSTEM_DEFAULT;
    }

    public static final SupportedLocale DEFAULT = new SupportedLocale("def", getDefaultLocale()) {
        @Override
        public String getDisplayName(SupportedLocale inLocale) {
            try {
                return inLocale.getResourceBundle().getString("lang.default");
            } catch (Throwable e) {
                LOG.warning("Failed to get localized name for default locale", e);
                return "Default";
            }
        }
    };

    /**
     * English
     */
    public static final SupportedLocale EN = new SupportedLocale("en", Locale.ENGLISH);

    /**
     * Spanish
     */
    public static final SupportedLocale ES = new SupportedLocale("es", Locale.forLanguageTag("es"));

    /**
     * Russian
     */
    public static final SupportedLocale RU = new SupportedLocale("ru", Locale.forLanguageTag("ru"));

    /**
     * Ukrainian
     */
    public static final SupportedLocale UK = new SupportedLocale("uk", Locale.forLanguageTag("uk"));

    /**
     * Japanese
     */
    public static final SupportedLocale JA = new SupportedLocale("ja", Locale.JAPANESE);

    /**
     * Chinese (Simplified)
     */
    public static final SupportedLocale ZH_HANS = new SupportedLocale("zh_CN", LocaleUtils.LOCALE_ZH_HANS);

    /**
     * Chinese (Traditional)
     */
    public static final SupportedLocale ZH_HANT = new SupportedLocale("zh", LocaleUtils.LOCALE_ZH_HANT);

    /**
     * Wenyan (Classical Chinese)
     */
    public static final SupportedLocale WENYAN = new SupportedLocale("lzh", Locale.forLanguageTag("lzh")) {

        @Override
        public String getDisplayName(SupportedLocale inLocale) {
            if (LocaleUtils.isChinese(inLocale.locale))
                return "文言";

            String name = super.getDisplayName(inLocale);
            return name.equals("lzh") || name.equals("Literary Chinese")
                    ? "Chinese (Classical)"
                    : name;
        }
    };

    public static final List<SupportedLocale> LOCALES = List.of(DEFAULT, EN, ES, JA, RU, UK, ZH_HANS, ZH_HANT, WENYAN);

    public static SupportedLocale getLocaleByName(String name) {
        if (name == null) return DEFAULT;

        for (SupportedLocale locale : LOCALES) {
            if (locale.getName().equalsIgnoreCase(name))
                return locale;
        }

        return DEFAULT;
    }

    @JsonAdapter(SupportedLocale.TypeAdapter.class)
    public static class SupportedLocale {
        private final String name;
        private final Locale locale;
        private ResourceBundle resourceBundle;
        private DateTimeFormatter dateTimeFormatter;
        private List<Locale> candidateLocales;

        SupportedLocale(String name, Locale locale) {
            this.name = name;
            this.locale = locale;
        }

        public String getName() {
            return name;
        }

        public Locale getLocale() {
            return locale;
        }

        public String getDisplayName(SupportedLocale inLocale) {
            if (inLocale.locale.getLanguage().equals("lzh"))
                inLocale = ZH_HANT;
            return locale.getDisplayName(inLocale.getLocale());
        }

        public ResourceBundle getResourceBundle() {
            ResourceBundle bundle = resourceBundle;
            if (resourceBundle == null)
                resourceBundle = bundle = ResourceBundle.getBundle("assets.lang.I18N", locale,
                        DefaultResourceBundleControl.INSTANCE);

            return bundle;
        }

        public List<Locale> getCandidateLocales() {
            if (candidateLocales == null)
                candidateLocales = List.copyOf(LocaleUtils.getCandidateLocales(locale));
            return candidateLocales;
        }

        public String i18n(String key, Object... formatArgs) {
            try {
                return String.format(getResourceBundle().getString(key), formatArgs);
            } catch (MissingResourceException e) {
                LOG.error("Cannot find key " + key + " in resource bundle", e);
            } catch (IllegalFormatException e) {
                LOG.error("Illegal format string, key=" + key + ", args=" + Arrays.toString(formatArgs), e);
            }

            return key + Arrays.toString(formatArgs);
        }

        public String i18n(String key) {
            try {
                return getResourceBundle().getString(key);
            } catch (MissingResourceException e) {
                LOG.error("Cannot find key " + key + " in resource bundle", e);
                return key;
            }
        }

        public String formatDateTime(TemporalAccessor time) {
            DateTimeFormatter formatter = dateTimeFormatter;
            if (formatter == null) {
                if (locale.getLanguage().equals("lzh"))
                    return WenyanUtils.formatDateTime(time);

                formatter = dateTimeFormatter = DateTimeFormatter.ofPattern(getResourceBundle().getString("datetime.format"))
                        .withZone(ZoneId.systemDefault());
            }
            return formatter.format(time);
        }

        public String getFcMatchPattern() {
            String language = locale.getLanguage();
            String region = locale.getCountry();

            if (LocaleUtils.isEnglish(locale))
                return "";

            if (LocaleUtils.isChinese(locale)) {
                String lang;
                String charset;

                String script = LocaleUtils.getScript(locale);
                switch (script) {
                    case "Hans":
                        lang = region.equals("SG") || region.equals("MY")
                                ? "zh-" + region
                                : "zh-CN";
                        charset = "0x6e38,0x620f";
                        break;
                    case "Hant":
                        lang = region.equals("HK") || region.equals("MO")
                                ? "zh-" + region
                                : "zh-TW";
                        charset = "0x904a,0x6232";
                        break;
                    default:
                        return "";
                }

                return ":lang=" + lang + ":charset=" + charset;
            }

            return region.isEmpty() ? language : language + "-" + region;
        }

        public boolean isSameLanguage(SupportedLocale other) {
            return (this.getLocale().getLanguage().equals(other.getLocale().getLanguage()))
                    || (LocaleUtils.isChinese(this.getLocale()) && LocaleUtils.isChinese(other.getLocale()));
        }

        public static final class TypeAdapter extends com.google.gson.TypeAdapter<SupportedLocale> {
            @Override
            public void write(JsonWriter out, SupportedLocale value) throws IOException {
                out.value(value.getName());
            }

            @Override
            public SupportedLocale read(JsonReader in) throws IOException {
                return getLocaleByName(in.nextString());
            }
        }
    }

}
