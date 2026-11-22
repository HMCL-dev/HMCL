// SPDX-FileCopyrightText: Copyright (C) 2025 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
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
    SIMPLIFIED_CHINESE("zh-Hans"),
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
