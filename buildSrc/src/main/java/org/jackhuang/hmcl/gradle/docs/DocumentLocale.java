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
import java.util.Map;

/// @author Glavo
public enum DocumentLocale {
    ENGLISH(Locale.ENGLISH, "") {
        @Override
        public List<DocumentLocale> getCandidates() {
            return List.of(ENGLISH);
        }
    },
    SIMPLIFIED_CHINESE(Locale.forLanguageTag("zh-Hans"), "zh") {
        @Override
        public String getLanguageDisplayName() {
            return "中文";
        }

        @Override
        public String getSubLanguageDisplayName() {
            return "简体";
        }
    },
    TRADITIONAL_CHINESE("zh-Hant") {
        @Override
        public String getLanguageDisplayName() {
            return "中文";
        }

        @Override
        public String getSubLanguageDisplayName() {
            return "繁體";
        }

        @Override
        public List<DocumentLocale> getCandidates() {
            return List.of(TRADITIONAL_CHINESE, SIMPLIFIED_CHINESE, ENGLISH);
        }
    },
    JAPANESE("ja"),
    WENYAN("lzh") {
        @Override
        public String getLanguageDisplayName() {
            return "中文";
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
    SPANISH("es"),
    RUSSIAN("ru"),
    UKRAINIAN("uk"),
    ;

    public static Map.Entry<DocumentLocale, String> parseFileName(String fileNameWithoutExtension) {
        for (DocumentLocale locale : values()) {
            String suffix = locale.getFileNameSuffix();
            if (suffix.isEmpty())
                continue;

            if (fileNameWithoutExtension.endsWith(suffix))
                return Map.entry(locale, fileNameWithoutExtension.substring(0, fileNameWithoutExtension.length() - locale.getFileNameSuffix().length()));
        }
        return Map.entry(ENGLISH, fileNameWithoutExtension);
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
