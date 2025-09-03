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

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Glavo
 */
public final class LocaleUtilsTest {
    private static void assertCandidateLocales(String languageTag, List<String> candidateLocales) {
        assertEquals(candidateLocales,
                LocaleUtils.getCandidateLocales(Locale.forLanguageTag(languageTag))
                        .stream()
                        .map(Locale::toLanguageTag)
                        .collect(Collectors.toList()));
    }

    @Test
    public void testGetCandidateLocales() {
        assertCandidateLocales("zh", List.of("zh-Hans", "zh-CN", "zh", "und"));
        assertCandidateLocales("zh-CN", List.of("zh-Hans-CN", "zh-Hans", "zh-CN", "zh", "und"));
        assertCandidateLocales("zh-SG", List.of("zh-Hans-SG", "zh-Hans", "zh-SG", "zh-CN", "zh", "und"));
        assertCandidateLocales("zh-MY", List.of("zh-Hans-MY", "zh-Hans", "zh-MY", "zh-CN", "zh", "und"));
        assertCandidateLocales("zh-US", List.of("zh-Hans-US", "zh-Hans", "zh-US", "zh-CN", "zh", "und"));
        assertCandidateLocales("zh-TW", List.of("zh-Hant-TW", "zh-Hant", "zh-TW", "zh", "zh-CN", "und"));
        assertCandidateLocales("zh-HK", List.of("zh-Hant-HK", "zh-Hant", "zh-HK", "zh-TW", "zh", "zh-CN", "und"));
        assertCandidateLocales("zh-MO", List.of("zh-Hant-MO", "zh-Hant", "zh-MO", "zh-TW", "zh", "zh-CN", "und"));
        assertCandidateLocales("zh-Hans", List.of("zh-Hans", "zh-CN", "zh", "und"));
        assertCandidateLocales("zh-Hant", List.of("zh-Hant", "zh-TW", "zh", "zh-CN", "und"));
        assertCandidateLocales("zh-Hans-US", List.of("zh-Hans-US", "zh-Hans", "zh-US", "zh-CN", "zh", "und"));
        assertCandidateLocales("zh-Hant-CN", List.of("zh-Hant-CN", "zh-Hant", "zh-CN", "zh-TW", "zh", "und"));
        assertCandidateLocales("zh-Hans-TW", List.of("zh-Hans-TW", "zh-Hans", "zh-TW", "zh-CN", "zh", "und"));
        assertCandidateLocales("zh-Latn", List.of("zh-Latn", "zh", "zh-CN", "und"));
        assertCandidateLocales("zh-Latn-CN", List.of("zh-Latn-CN", "zh-Latn", "zh-CN", "zh", "und"));
        assertCandidateLocales("zh-pinyin", List.of("zh-Latn-pinyin", "zh-Latn", "zh-pinyin", "zh", "zh-CN", "und"));
        assertCandidateLocales("lzh", List.of("lzh-Hant", "lzh", "zh-Hant", "zh-TW", "zh", "zh-CN", "und"));
        assertCandidateLocales("lzh-Hant", List.of("lzh-Hant", "lzh", "zh-Hant", "zh-TW", "zh", "zh-CN", "und"));
        assertCandidateLocales("lzh-Hans", List.of("lzh-Hans", "lzh", "zh-Hans", "zh-CN", "zh", "und"));
        assertCandidateLocales("cmn", List.of("cmn-Hans", "cmn", "zh-Hans", "zh-CN", "zh", "und"));
        assertCandidateLocales("cmn-Hans", List.of("cmn-Hans", "cmn", "zh-Hans", "zh-CN", "zh", "und"));
        assertCandidateLocales("yue", List.of("yue-Hans", "yue", "zh-Hans", "zh-CN", "zh", "und"));

        assertCandidateLocales("ja", List.of("ja", "und"));
        assertCandidateLocales("ja-JP", List.of("ja-JP", "ja", "und"));

        assertCandidateLocales("en", List.of("en", "und"));
        assertCandidateLocales("und", List.of("en", "und"));
    }

    @Test
    public void testIsChinese() {
        assertTrue(LocaleUtils.isChinese(Locale.CHINESE));
        assertTrue(LocaleUtils.isChinese(Locale.SIMPLIFIED_CHINESE));
        assertTrue(LocaleUtils.isChinese(Locale.TRADITIONAL_CHINESE));
        assertTrue(LocaleUtils.isChinese(LocaleUtils.LOCALE_ZH_HANS));
        assertTrue(LocaleUtils.isChinese(LocaleUtils.LOCALE_ZH_HANT));
        assertTrue(LocaleUtils.isChinese(Locale.forLanguageTag("lzh")));
        assertTrue(LocaleUtils.isChinese(Locale.forLanguageTag("cmn")));
        assertTrue(LocaleUtils.isChinese(Locale.forLanguageTag("cmn-Hans")));
        assertTrue(LocaleUtils.isChinese(Locale.forLanguageTag("yue")));

        assertFalse(LocaleUtils.isChinese(Locale.ROOT));
        assertFalse(LocaleUtils.isChinese(Locale.ENGLISH));
        assertFalse(LocaleUtils.isChinese(Locale.JAPANESE));
        assertFalse(LocaleUtils.isChinese(Locale.forLanguageTag("es")));
        assertFalse(LocaleUtils.isChinese(Locale.forLanguageTag("ru")));
        assertFalse(LocaleUtils.isChinese(Locale.forLanguageTag("uk")));
    }

    @Test
    public void testGetScript() {
        assertEquals("Hans", LocaleUtils.getScript(Locale.CHINESE));
        assertEquals("Hans", LocaleUtils.getScript(Locale.forLanguageTag("zh")));
        assertEquals("Hans", LocaleUtils.getScript(Locale.forLanguageTag("zh-Hans")));
        assertEquals("Hans", LocaleUtils.getScript(Locale.forLanguageTag("zh-Hans-US")));
        assertEquals("Hans", LocaleUtils.getScript(Locale.forLanguageTag("zh-SG")));
        assertEquals("Hans", LocaleUtils.getScript(Locale.forLanguageTag("zh-MY")));
        assertEquals("Hans", LocaleUtils.getScript(Locale.forLanguageTag("cmn")));
        assertEquals("Hans", LocaleUtils.getScript(Locale.forLanguageTag("cmn-Hans")));
        assertEquals("Hans", LocaleUtils.getScript(Locale.forLanguageTag("cmn-CN")));
        assertEquals("Hans", LocaleUtils.getScript(Locale.forLanguageTag("lzh-Hans")));

        assertEquals("Hant", LocaleUtils.getScript(Locale.forLanguageTag("zh-Hant")));
        assertEquals("Hant", LocaleUtils.getScript(Locale.forLanguageTag("zh-TW")));
        assertEquals("Hant", LocaleUtils.getScript(Locale.forLanguageTag("zh-HK")));
        assertEquals("Hant", LocaleUtils.getScript(Locale.forLanguageTag("zh-MO")));
        assertEquals("Hant", LocaleUtils.getScript(Locale.forLanguageTag("cmn-Hant")));
        assertEquals("Hant", LocaleUtils.getScript(Locale.forLanguageTag("lzh")));
        assertEquals("Hant", LocaleUtils.getScript(Locale.forLanguageTag("lzh-Hant")));
        assertEquals("Hant", LocaleUtils.getScript(Locale.forLanguageTag("lzh-CN")));

        assertEquals("Latn", LocaleUtils.getScript(Locale.forLanguageTag("zh-pinyin")));
    }
}
