/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2025 huangyuhui <huanghongxun2008@126.com> and contributors
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
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.gson.JsonUtils;

import java.io.IOException;
import java.io.InputStream;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Stream;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

@JsonAdapter(SupportedLocale.TypeAdapter.class)
public final class SupportedLocale {
    public static final SupportedLocale DEFAULT = new SupportedLocale();

    private static final ConcurrentMap<Locale, SupportedLocale> LOCALES = new ConcurrentHashMap<>();

    public static List<SupportedLocale> getSupportedLocales() {
        List<SupportedLocale> list = new ArrayList<>();
        list.add(DEFAULT);

        InputStream locales = SupportedLocale.class.getResourceAsStream("/assets/lang/languages.json");
        if (locales != null) {
            try (locales) {
                list.addAll(JsonUtils.fromNonNullJsonFully(locales, JsonUtils.listTypeOf(SupportedLocale.class)));
            } catch (Throwable e) {
                LOG.warning("Failed to load languages.json", e);
            }
        }
        return List.copyOf(list);
    }

    public static SupportedLocale getLocale(Locale locale) {
        return LOCALES.computeIfAbsent(locale, SupportedLocale::new);
    }

    public static SupportedLocale getLocaleByName(String name) {
        if (name == null || name.isBlank() || "def".equals(name) || "default".equals(name))
            return DEFAULT;

        return getLocale(Locale.forLanguageTag(name.trim().replace('_', '-')));
    }

    private final boolean isDefault;
    private final String name;
    private final Locale locale;
    private ResourceBundle resourceBundle;
    private ResourceBundle localeNamesBundle;
    private DateTimeFormatter dateTimeFormatter;
    private List<Locale> candidateLocales;

    SupportedLocale() {
        this.isDefault = true;
        this.name = "def"; // TODO: Change to "default" after updating the Config format

        String language = System.getenv("HMCL_LANGUAGE");
        this.locale = StringUtils.isBlank(language)
                ? LocaleUtils.SYSTEM_DEFAULT
                : Locale.forLanguageTag(language);
    }

    SupportedLocale(Locale locale) {
        this.isDefault = false;
        this.name = locale.toLanguageTag();
        this.locale = locale;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public String getName() {
        return name;
    }

    public Locale getLocale() {
        return locale;
    }

    public String getDisplayName(SupportedLocale inLocale) {
        if (isDefault()) {
            try {
                return inLocale.getResourceBundle().getString("lang.default");
            } catch (Throwable e) {
                LOG.warning("Failed to get localized name for default locale", e);
                return "Default";
            }
        }

        Locale currentLocale = this.getLocale();

        String language = currentLocale.getLanguage();
        String subLanguage;
        {
            String parentLanguage = LocaleUtils.getParentLanguage(language);
            if (parentLanguage != null) {
                subLanguage = language;
                language = parentLanguage;
            } else {
                subLanguage = "";
            }
        }

        ResourceBundle localeNames = inLocale.getLocaleNamesBundle();

        String languageDisplayName = language;
        try {
            languageDisplayName = localeNames.getString(language);
        } catch (Throwable e) {
            LOG.warning("Failed to get localized name for language " + language, e);
        }

        // Currently, HMCL does not support any locales with regions or variants, so they are not handled for now
        List<String> subTags = Stream.of(subLanguage, currentLocale.getScript())
                .filter(it -> !it.isEmpty())
                .map(it -> {
                    try {
                        return localeNames.getString(it);
                    } catch (Throwable e) {
                        LOG.warning("Failed to get localized name of " + it, e);
                    }
                    return it;
                }).toList();

        return subTags.isEmpty()
                ? languageDisplayName
                : languageDisplayName + " (" + String.join(", ", subTags) + ")";
    }

    public ResourceBundle getResourceBundle() {
        ResourceBundle bundle = resourceBundle;
        if (resourceBundle == null)
            resourceBundle = bundle = ResourceBundle.getBundle("assets.lang.I18N", locale,
                    DefaultResourceBundleControl.INSTANCE);

        return bundle;
    }

    public ResourceBundle getLocaleNamesBundle() {
        ResourceBundle bundle = localeNamesBundle;
        if (localeNamesBundle == null)
            localeNamesBundle = bundle = ResourceBundle.getBundle("assets.lang.LocaleNames", locale,
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
            if (LocaleUtils.isEnglish(locale) && "Qabs".equals(locale.getScript())) {
                return UpsideDownUtils.formatDateTime(time);
            }

            if (locale.getLanguage().equals("lzh")) {
                return WenyanUtils.formatDateTime(time);
            }

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
        return LocaleUtils.getRootLanguage(this.getLocale())
                .equals(LocaleUtils.getRootLanguage(other.getLocale()));
    }

    public static final class TypeAdapter extends com.google.gson.TypeAdapter<SupportedLocale> {
        @Override
        public void write(JsonWriter out, SupportedLocale value) throws IOException {
            out.value(value.getName());
        }

        @Override
        public SupportedLocale read(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL)
                return DEFAULT;

            String language = in.nextString();
            return getLocaleByName(switch (language) {
                // TODO: Remove these compatibility codes after updating the Config format
                case "zh_CN" -> "zh-Hans"; // For compatibility
                case "zh" -> "zh-Hant";    // For compatibility
                default -> language;
            });
        }
    }
}
