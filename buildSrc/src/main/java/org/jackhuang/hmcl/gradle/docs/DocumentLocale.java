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
package org.jackhuang.hmcl.gradle.docs;

import java.util.List;
import java.util.Locale;

/// @author Glavo
public enum DocumentLocale {
    ENGLISH(Locale.ENGLISH, "") {
        @Override
        public String getSubLanguageDisplayName() {
            return "Standard";
        }

        @Override
        public List<DocumentLocale> getCandidates() {
            return List.of(ENGLISH);
        }
    },
    ENGLISH_UPSIDE_DOWN(Locale.forLanguageTag("en-Qabs"), "en-Qabs") {
        @Override
        public String getSubLanguageDisplayName() {
            return "uʍoᗡ ǝpᴉsd∩";
        }
    },
    SIMPLIFIED_CHINESE(Locale.forLanguageTag("zh-Hans"), "zh"),
    TRADITIONAL_CHINESE("zh-Hant") {
        @Override
        public List<DocumentLocale> getCandidates() {
            return List.of(TRADITIONAL_CHINESE, SIMPLIFIED_CHINESE, ENGLISH);
        }
    },
    WENYAN("lzh") {
        @Override
        public String getLanguageDisplayName() {
            return TRADITIONAL_CHINESE.getLanguageDisplayName();
        }

        @Override
        public String getSubLanguageDisplayName() {
            return "文言";
        }

        @Override
        public List<DocumentLocale> getCandidates() {
            return List.of(WENYAN, TRADITIONAL_CHINESE, SIMPLIFIED_CHINESE, ENGLISH);
        }
    },
    JAPANESE("ja"),
    SPANISH("es"),
    RUSSIAN("ru"),
    UKRAINIAN("uk"),
    ;

    public record LocaleAndName(DocumentLocale locale, String name) {
    }

    public static LocaleAndName parseFileName(String fileNameWithoutExtension) {
        for (DocumentLocale locale : values()) {
            String suffix = locale.getFileNameSuffix();
            if (suffix.isEmpty())
                continue;

            if (fileNameWithoutExtension.endsWith(suffix))
                return new LocaleAndName(locale, fileNameWithoutExtension.substring(0, fileNameWithoutExtension.length() - locale.getFileNameSuffix().length()));
        }
        return new LocaleAndName(ENGLISH, fileNameWithoutExtension);
    }

    private final Locale locale;
    private final String languageTag;
    private final String fileNameSuffix;

    DocumentLocale(String languageTag) {
        this(Locale.forLanguageTag(languageTag), languageTag);
    }

    DocumentLocale(Locale locale, String languageTag) {
        this.locale = locale;
        this.languageTag = languageTag;
        this.fileNameSuffix = languageTag.isEmpty() ? "" : "_" + languageTag.replace('-', '_');
    }

    public String getLanguageDisplayName() {
        return locale.getDisplayLanguage(locale);
    }

    public String getSubLanguageDisplayName() {
        boolean hasScript = !locale.getScript().isEmpty();
        boolean hasRegion = !locale.getCountry().isEmpty();

        if (hasScript && hasRegion)
            throw new AssertionError("Unsupported locale: " + locale);

        if (hasScript)
            return locale.getDisplayScript(locale);
        if (hasRegion)
            return locale.getDisplayCountry(locale);
        return "";
    }

    public Locale getLocale() {
        return locale;
    }

    public String getFileNameSuffix() {
        return fileNameSuffix;
    }

    public List<DocumentLocale> getCandidates() {
        return List.of(this, ENGLISH);
    }
}
