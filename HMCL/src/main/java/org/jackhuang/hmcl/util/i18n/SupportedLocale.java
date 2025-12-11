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
import org.jackhuang.hmcl.util.i18n.translator.Translator;
import org.jetbrains.annotations.PropertyKey;

import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Stream;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

@JsonAdapter(SupportedLocale.TypeAdapter.class)
public final class SupportedLocale {
    public static final SupportedLocale DEFAULT = new SupportedLocale();

    public static boolean isExperimentalSupported(Locale locale) {
        return "ar".equals(locale.getLanguage());
    }

    private static final ConcurrentMap<Locale, SupportedLocale> LOCALES = new ConcurrentHashMap<>();

    public static List<SupportedLocale> getSupportedLocales() {
        List<SupportedLocale> list = new ArrayList<>();
        list.add(DEFAULT);

        InputStream locales = SupportedLocale.class.getResourceAsStream("/assets/lang/languages.json");
        if (locales != null) {
            try (locales) {
                for (SupportedLocale locale : JsonUtils.fromNonNullJsonFully(locales, JsonUtils.listTypeOf(SupportedLocale.class))) {
                    if (!isExperimentalSupported(locale.getLocale())) {
                        list.add(locale);
                    }
                }
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
    private final Locale displayLocale;
    private final TextDirection textDirection;

    private ResourceBundle resourceBundle;
    private ResourceBundle localeNamesBundle;
    private List<Locale> candidateLocales;
    private Translator translator;

    SupportedLocale() {
        this.isDefault = true;
        this.name = "def"; // TODO: Change to "default" after updating the Config format

        String language = System.getenv("HMCL_LANGUAGE");
        if (StringUtils.isBlank(language)) {
            this.locale = LocaleUtils.SYSTEM_DEFAULT;
            this.displayLocale = isExperimentalSupported(this.locale)
                    ? Locale.ENGLISH
                    : this.locale;
        } else {
            this.locale = Locale.forLanguageTag(language);
            this.displayLocale = this.locale;
        }
        this.textDirection = LocaleUtils.getTextDirection(locale);
    }

    SupportedLocale(Locale locale) {
        this.isDefault = false;
        this.name = locale.toLanguageTag();
        this.locale = locale;
        this.displayLocale = locale;
        this.textDirection = LocaleUtils.getTextDirection(locale);
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

    /// Used to represent the text display language of HMCL.
    ///
    /// Usually equivalent to [#getLocale()],
    /// but for [experimentally supported languages][#isExperimentalSupported(Locale)],
    /// it falls back to ENGLISH by default.
    public Locale getDisplayLocale() {
        return displayLocale;
    }

    public TextDirection getTextDirection() {
        return textDirection;
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
            resourceBundle = bundle = ResourceBundle.getBundle("assets.lang.I18N", displayLocale,
                    DefaultResourceBundleControl.INSTANCE);

        return bundle;
    }

    public ResourceBundle getLocaleNamesBundle() {
        ResourceBundle bundle = localeNamesBundle;
        if (localeNamesBundle == null)
            localeNamesBundle = bundle = ResourceBundle.getBundle("assets.lang.LocaleNames", displayLocale,
                    DefaultResourceBundleControl.INSTANCE);

        return bundle;
    }

    public List<Locale> getCandidateLocales() {
        if (candidateLocales == null)
            candidateLocales = List.copyOf(LocaleUtils.getCandidateLocales(displayLocale));
        return candidateLocales;
    }

    public String i18n(@PropertyKey(resourceBundle = "assets.lang.I18N") String key, Object... formatArgs) {
        try {
            return String.format(getResourceBundle().getString(key), formatArgs);
        } catch (MissingResourceException e) {
            LOG.error("Cannot find key " + key + " in resource bundle", e);
        } catch (IllegalFormatException e) {
            LOG.error("Illegal format string, key=" + key + ", args=" + Arrays.toString(formatArgs), e);
        }

        return key + Arrays.toString(formatArgs);
    }

    public String i18n(@PropertyKey(resourceBundle = "assets.lang.I18N") String key) {
        try {
            return getResourceBundle().getString(key);
        } catch (MissingResourceException e) {
            LOG.error("Cannot find key " + key + " in resource bundle", e);
            return key;
        }
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

    public Translator getTranslator() {
        Translator translator = this.translator;
        if (translator != null)
            return translator;

        List<Locale> candidateLocales = getCandidateLocales();

        for (Locale candidateLocale : candidateLocales) {
            String className = DefaultResourceBundleControl.INSTANCE.toBundleName(Translator.class.getSimpleName(), candidateLocale);
            if (Translator.class.getResource(className + ".class") != null) {
                try {
                    Class<?> clazz = Class.forName(Translator.class.getPackageName() + "." + className);

                    MethodHandle constructor = MethodHandles.publicLookup()
                            .findConstructor(clazz, MethodType.methodType(void.class, SupportedLocale.class));

                    return this.translator = (Translator) constructor.invoke(this);
                } catch (Throwable e) {
                    LOG.warning("Failed to create instance for " + className, e);
                }
            }
        }
        return this.translator = new Translator(this);
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
