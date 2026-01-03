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

import com.google.common.jimfs.Configuration;
import com.google.common.jimfs.Jimfs;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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
                        .toList());
    }

    private static void assertCandidateLocalesEquals(String l1, String l2) {
        assertEquals(
                LocaleUtils.getCandidateLocales(Locale.forLanguageTag(l1)),
                LocaleUtils.getCandidateLocales(Locale.forLanguageTag(l2))
        );
    }

    @Test
    public void testGetCandidateLocales() {
        // English

        assertCandidateLocales("en", List.of("en-Latn", "en", "und"));
        assertCandidateLocales("en-US", List.of("en-Latn-US", "en-Latn", "en-US", "en", "und"));
        assertCandidateLocalesEquals("en", "eng");
        assertCandidateLocalesEquals("en-US", "eng-US");
        assertCandidateLocalesEquals("und", "en");

        // Spanish

        assertCandidateLocales("es", List.of("es-Latn", "es", "und"));
        assertCandidateLocalesEquals("es", "spa");

        // Japanese

        assertCandidateLocales("ja", List.of("ja-Jpan", "ja", "und"));
        assertCandidateLocales("ja-JP", List.of("ja-Jpan-JP", "ja-Jpan", "ja-JP", "ja", "und"));
        assertCandidateLocalesEquals("ja", "jpn");
        assertCandidateLocalesEquals("ja-JP", "jpn-JP");

        // Russian

        assertCandidateLocales("ru", List.of("ru-Cyrl", "ru", "und"));
        assertCandidateLocalesEquals("ru", "rus");

        // Ukrainian

        assertCandidateLocales("uk", List.of("uk-Cyrl", "uk", "und"));
        assertCandidateLocalesEquals("uk", "ukr");

        // Chinese

        assertCandidateLocales("zh", List.of("cmn-Hans", "cmn", "zh-Hans", "zh-CN", "zh", "und"));
        assertCandidateLocales("zh-CN", List.of("cmn-Hans-CN", "cmn-Hans", "cmn-CN", "cmn", "zh-Hans-CN", "zh-Hans", "zh-CN", "zh", "und"));
        assertCandidateLocales("zh-SG", List.of("cmn-Hans-SG", "cmn-Hans", "cmn-SG", "cmn", "zh-Hans-SG", "zh-Hans", "zh-SG", "zh-CN", "zh", "und"));

        assertCandidateLocales("zh-TW", List.of("cmn-Hant-TW", "cmn-Hant", "cmn-TW", "cmn", "zh-Hant-TW", "zh-Hant", "zh-TW", "zh", "zh-CN", "und"));
        assertCandidateLocales("zh-HK", List.of("cmn-Hant-HK", "cmn-Hant", "cmn-HK", "cmn", "zh-Hant-HK", "zh-Hant", "zh-HK", "zh-TW", "zh", "zh-CN", "und"));
        assertCandidateLocales("zh-Hant-CN", List.of("cmn-Hant-CN", "cmn-Hant", "cmn-CN", "cmn", "zh-Hant-CN", "zh-Hant", "zh-CN", "zh-TW", "zh", "und"));

        assertCandidateLocales("zh-pinyin", List.of("cmn-Latn-pinyin", "cmn-Latn", "cmn-pinyin", "cmn", "zh-Latn-pinyin", "zh-Latn", "zh-pinyin", "zh", "zh-CN", "und"));

        assertCandidateLocales("lzh", List.of("lzh-Hant", "lzh", "zh-Hant", "zh-TW", "zh", "zh-CN", "und"));
        assertCandidateLocales("yue", List.of("yue-Hans", "yue", "zh-Hans", "zh-CN", "zh", "und"));

        assertCandidateLocalesEquals("zh", "cmn-Hans");
        assertCandidateLocalesEquals("zh-CN", "cmn-Hans-CN");
        assertCandidateLocalesEquals("zh-SG", "cmn-Hans-SG");
        assertCandidateLocalesEquals("zh-MY", "cmn-Hans-MY");
        assertCandidateLocalesEquals("zh-TW", "cmn-Hant-TW");
        assertCandidateLocalesEquals("zh-HK", "cmn-Hant-HK");
        assertCandidateLocalesEquals("zh-Hans", "cmn-Hans");
        assertCandidateLocalesEquals("zh-Hant", "cmn-Hant");
        assertCandidateLocalesEquals("zh-Hant-CN", "cmn-Hant-CN");
        assertCandidateLocalesEquals("zh-Hant-SG", "cmn-Hant-SG");
        assertCandidateLocalesEquals("zh-Latn", "cmn-Latn");
        assertCandidateLocalesEquals("zh-pinyin", "cmn-Latn-pinyin");
        assertCandidateLocalesEquals("zho", "zh");
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

        assertEquals("Latn", LocaleUtils.getScript(Locale.forLanguageTag("en")));
        assertEquals("Latn", LocaleUtils.getScript(Locale.forLanguageTag("zh-pinyin")));
        assertEquals("Latn", LocaleUtils.getScript(Locale.forLanguageTag("ja-hepburn")));
    }

    @Test
    public void testFindAllLocalizedFiles() throws IOException {
        try (var testFs = Jimfs.newFileSystem(Configuration.unix())) {
            Path testDir = testFs.getPath("/test-dir");
            Files.createDirectories(testDir);

            Files.createFile(testDir.resolve("meow.json"));
            Files.createFile(testDir.resolve("meow_zh.json"));
            Files.createFile(testDir.resolve("meow_zh_CN.json"));
            Files.createFile(testDir.resolve("meow_zh_Hans.json"));
            Files.createFile(testDir.resolve("meow_zh_Hans_CN.json"));
            Files.createFile(testDir.resolve("meow_en.json"));
            Files.createFile(testDir.resolve("meow_en.toml"));

            Files.createFile(testDir.resolve("meow_.json"));
            Files.createFile(testDir.resolve("meowmeow.json"));
            Files.createFile(testDir.resolve("woem.json"));
            Files.createFile(testDir.resolve("meow.txt"));
            Files.createDirectories(testDir.resolve("subdir"));
            Files.createDirectories(testDir.resolve("meow_en_US.json"));

            Path notExistsDir = testFs.getPath("/not-exists");
            Path emptyDir = testFs.getPath("/empty");
            Files.createDirectories(emptyDir);

            assertEquals(Map.of(), LocaleUtils.findAllLocalizedFiles(emptyDir, "meow", "json"));
            assertEquals(Map.of(), LocaleUtils.findAllLocalizedFiles(emptyDir, "meow", Set.of("json", "toml")));
            assertEquals(Map.of(), LocaleUtils.findAllLocalizedFiles(notExistsDir, "meow", "json"));
            assertEquals(Map.of(), LocaleUtils.findAllLocalizedFiles(notExistsDir, "meow", Set.of("json", "toml")));

            assertEquals(Map.of(
                            "default", testDir.resolve("meow.json"),
                            "zh", testDir.resolve("meow_zh.json"),
                            "zh-CN", testDir.resolve("meow_zh_CN.json"),
                            "zh-Hans", testDir.resolve("meow_zh_Hans.json"),
                            "zh-Hans-CN", testDir.resolve("meow_zh_Hans_CN.json"),
                            "en", testDir.resolve("meow_en.json")
                    ),
                    LocaleUtils.findAllLocalizedFiles(testDir, "meow", "json"));
            assertEquals(Map.of(
                            "default", Map.of("json", testDir.resolve("meow.json")),
                            "zh", Map.of("json", testDir.resolve("meow_zh.json")),
                            "zh-CN", Map.of("json", testDir.resolve("meow_zh_CN.json")),
                            "zh-Hans", Map.of("json", testDir.resolve("meow_zh_Hans.json")),
                            "zh-Hans-CN", Map.of("json", testDir.resolve("meow_zh_Hans_CN.json")),
                            "en", Map.of(
                                    "json", testDir.resolve("meow_en.json"),
                                    "toml", testDir.resolve("meow_en.toml")
                            )
                    ),
                    LocaleUtils.findAllLocalizedFiles(testDir, "meow", Set.of("json", "toml")));
        }
    }

    @Test
    public void testNormalizeLanguage() {
        assertEquals("en", LocaleUtils.normalizeLanguage(""));
        assertEquals("en", LocaleUtils.normalizeLanguage("eng"));
        assertEquals("es", LocaleUtils.normalizeLanguage("spa"));
        assertEquals("ja", LocaleUtils.normalizeLanguage("jpn"));
        assertEquals("ru", LocaleUtils.normalizeLanguage("rus"));
        assertEquals("uk", LocaleUtils.normalizeLanguage("ukr"));
        assertEquals("zh", LocaleUtils.normalizeLanguage("zho"));
        assertEquals("zu", LocaleUtils.normalizeLanguage("zul"));
        assertEquals("en", LocaleUtils.normalizeLanguage(""));
        assertEquals("cmn", LocaleUtils.normalizeLanguage("cmn"));
    }

    @Test
    public void testGetParentLanguage() {
        assertEquals("zh", LocaleUtils.getParentLanguage("cmn"));
        assertEquals("zh", LocaleUtils.getParentLanguage("yue"));
        assertEquals("zh", LocaleUtils.getParentLanguage("lzh"));

        assertNull(LocaleUtils.getParentLanguage(""));
        assertNull(LocaleUtils.getParentLanguage("en"));
        assertNull(LocaleUtils.getParentLanguage("eng"));
        assertNull(LocaleUtils.getParentLanguage("zh"));
        assertNull(LocaleUtils.getParentLanguage("zho"));
    }

    @Test
    public void testGetTextDirection() {
        assertEquals(TextDirection.LEFT_TO_RIGHT, LocaleUtils.getTextDirection(Locale.forLanguageTag("en")));
        assertEquals(TextDirection.LEFT_TO_RIGHT, LocaleUtils.getTextDirection(Locale.forLanguageTag("en-US")));
        assertEquals(TextDirection.LEFT_TO_RIGHT, LocaleUtils.getTextDirection(Locale.forLanguageTag("zh")));
        assertEquals(TextDirection.LEFT_TO_RIGHT, LocaleUtils.getTextDirection(Locale.forLanguageTag("zh-Hans")));
        assertEquals(TextDirection.LEFT_TO_RIGHT, LocaleUtils.getTextDirection(Locale.forLanguageTag("zh-CN")));

        assertEquals(TextDirection.RIGHT_TO_LEFT, LocaleUtils.getTextDirection(Locale.forLanguageTag("en-Qabs")));
        assertEquals(TextDirection.RIGHT_TO_LEFT, LocaleUtils.getTextDirection(Locale.forLanguageTag("ar")));
        assertEquals(TextDirection.RIGHT_TO_LEFT, LocaleUtils.getTextDirection(Locale.forLanguageTag("ara")));
        assertEquals(TextDirection.RIGHT_TO_LEFT, LocaleUtils.getTextDirection(Locale.forLanguageTag("he")));
        assertEquals(TextDirection.RIGHT_TO_LEFT, LocaleUtils.getTextDirection(Locale.forLanguageTag("heb")));
    }
}
