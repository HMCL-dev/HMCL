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

import java.util.Locale;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Glavo
 */
public final class LocalesTest {

    private static void printCandidateLocales(String languageTag) {
        System.out.println("Bundles for " + languageTag + ": " +
                Locales.Control.INSTANCE.getCandidateLocales("", Locale.forLanguageTag(languageTag))
                        .stream()
                        .map(locale -> Locales.Control.INSTANCE.toBundleName("I18N", locale))
                        .collect(Collectors.toList())
        );
    }

    // Just for Manual Test
    @Test
    public void testGetCandidateLocales() {
        printCandidateLocales("zh");
        printCandidateLocales("zh-CN");
        printCandidateLocales("zh-Hans");
        printCandidateLocales("zh-Hant");
        printCandidateLocales("zh-Hans-JP");
        printCandidateLocales("zh-JP");
        printCandidateLocales("zh-TW");
        printCandidateLocales("zh-SG");
        printCandidateLocales("lzh");
        printCandidateLocales("cmn");
        printCandidateLocales("cmn-Hans");
        printCandidateLocales("und");
        printCandidateLocales("en");
    }

    @Test
    public void testIsChinese() {
        assertTrue(Locales.isChinese(Locale.CHINESE));
        assertTrue(Locales.isChinese(Locale.SIMPLIFIED_CHINESE));
        assertTrue(Locales.isChinese(Locale.TRADITIONAL_CHINESE));
        assertTrue(Locales.isChinese(Locale.forLanguageTag("lzh")));
        assertTrue(Locales.isChinese(Locale.forLanguageTag("cmn")));
        assertTrue(Locales.isChinese(Locale.forLanguageTag("cmn-Hans")));
    }

    @Test
    public void testIsSimplifiedChinese() {
        assertTrue(Locales.isSimplifiedChinese(Locale.CHINESE));
        assertTrue(Locales.isSimplifiedChinese(Locale.forLanguageTag("zh")));
        assertTrue(Locales.isSimplifiedChinese(Locale.forLanguageTag("zh-Hans")));
        assertTrue(Locales.isSimplifiedChinese(Locale.forLanguageTag("zh-Hans-JP")));
        assertTrue(Locales.isSimplifiedChinese(Locale.forLanguageTag("zh-SG")));
        assertTrue(Locales.isSimplifiedChinese(Locale.forLanguageTag("zh-MY")));
        assertTrue(Locales.isSimplifiedChinese(Locale.forLanguageTag("cmn")));
        assertTrue(Locales.isSimplifiedChinese(Locale.forLanguageTag("cmn-Hans")));
        assertTrue(Locales.isSimplifiedChinese(Locale.forLanguageTag("cmn-CN")));

        assertFalse(Locales.isSimplifiedChinese(Locale.forLanguageTag("zh-Hant")));
        assertFalse(Locales.isSimplifiedChinese(Locale.forLanguageTag("zh-TW")));
        assertFalse(Locales.isSimplifiedChinese(Locale.forLanguageTag("zh-HK")));
        assertFalse(Locales.isSimplifiedChinese(Locale.forLanguageTag("zh-MO")));
        assertFalse(Locales.isSimplifiedChinese(Locale.forLanguageTag("cmn-Hant")));
    }
}
