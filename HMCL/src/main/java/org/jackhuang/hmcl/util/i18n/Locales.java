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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class Locales {
    private Locales() {
    }

    public static final SupportedLocale DEFAULT = new SupportedLocale("def", Locale.getDefault()) {
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
    public static final SupportedLocale ZH_HANS = new SupportedLocale("zh_CN", Locale.forLanguageTag("zh-Hans"));

    /**
     * Chinese (Traditional)
     */
    public static final SupportedLocale ZH_HANT = new SupportedLocale("zh", Locale.forLanguageTag("zh-Hant"));

    /**
     * Wenyan (Classical Chinese)
     */
    public static final SupportedLocale WENYAN = new SupportedLocale("lzh", Locale.forLanguageTag("lzh")) {

        @Override
        public String getDisplayName(SupportedLocale inLocale) {
            if (isChinese(inLocale.locale))
                return "文言";

            String name = super.getDisplayName(inLocale);
            return name.equals("lzh") || name.equals("Literary Chinese")
                    ? "Classical Chinese"
                    : name;
        }

        @Override
        public String formatDateTime(TemporalAccessor time) {
            return WenyanUtils.formatDateTime(time);
        }

        @Override
        public String getDisplaySelfVersion(RemoteVersion version) {
            if (version instanceof GameRemoteVersion)
                return WenyanUtils.translateGameVersion(GameVersionNumber.asGameVersion(version.getSelfVersion()));
            else
                return WenyanUtils.translateGenericVersion(version.getSelfVersion());
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

    public static boolean isEnglish(Locale locale) {
        return locale.getLanguage().equals("en") || locale.getLanguage().isEmpty();
    }

    public static boolean isChinese(Locale locale) {
        switch (locale.getLanguage()) {
            case "zh":
            case "lzh":
            case "cmn":
                return true;
            default:
                return false;
        }
    }

    public static boolean isSimplifiedChinese(Locale locale) {
        if (locale.getLanguage().equals("zh") || locale.getLanguage().equals("cmn")) {
            String script = locale.getScript();
            if (script.isEmpty()) {
                String region = locale.getCountry();
                return region.isEmpty() || region.equals("CN") || region.equals("SG") || region.equals("MY");
            } else
                return script.equals("Hans");
        } else {
            return false;
        }
    }

    @JsonAdapter(SupportedLocale.TypeAdapter.class)
    public static class SupportedLocale {
        private final String name;
        private final Locale locale;
        private ResourceBundle resourceBundle;
        private DateTimeFormatter dateTimeFormatter;

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
                resourceBundle = bundle = ResourceBundle.getBundle("assets.lang.I18N", locale, Control.INSTANCE);

            return bundle;
        }

        public String formatDateTime(TemporalAccessor time) {
            DateTimeFormatter formatter = dateTimeFormatter;
            if (formatter == null)
                formatter = dateTimeFormatter = DateTimeFormatter.ofPattern(getResourceBundle().getString("datetime.format"))
                        .withZone(ZoneId.systemDefault());
            return formatter.format(time);
        }

        public String getDisplaySelfVersion(RemoteVersion version) {
            return version.getSelfVersion();
        }

        public boolean isSameLanguage(SupportedLocale other) {
            return this.getLocale().getLanguage().equals(other.getLocale().getLanguage())
                    || isChinese(this.getLocale()) && isChinese(other.getLocale());
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

    public static final class Control extends ResourceBundle.Control {
        public static final Control INSTANCE = new Control();

        @Override
        public List<Locale> getCandidateLocales(String baseName, Locale locale) {
            List<Locale> candidateLocales = super.getCandidateLocales(baseName, locale);
            if (isChinese(locale)) {
                int idx = candidateLocales.indexOf(Locale.CHINESE);

                // For "lzh" and "cmn"
                if (idx < 0) {
                    if (!(candidateLocales instanceof ArrayList))
                        candidateLocales = new ArrayList<>(candidateLocales);

                    int i = candidateLocales.size() - 1;
                    while (i >= 0) {
                        Locale l = candidateLocales.get(i);
                        if (!isEnglish(l))
                            break;
                        i--;
                    }
                    candidateLocales.add(i + 1, Locale.CHINESE);
                }
            }

            if (isSimplifiedChinese(locale)) {
                int idx = candidateLocales.indexOf(Locale.SIMPLIFIED_CHINESE);
                if (idx < 0) {
                    if (!(candidateLocales instanceof ArrayList))
                        candidateLocales = new ArrayList<>(candidateLocales);
                    candidateLocales.add(0, Locale.SIMPLIFIED_CHINESE);
                }
            }

            return candidateLocales;
        }

    }
}
