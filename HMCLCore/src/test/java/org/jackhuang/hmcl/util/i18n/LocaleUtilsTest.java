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
        assertCandidateLocales("zho", List.of("zh-Hans", "zh-CN", "zh", "und"));
        assertCandidateLocales("lzh", List.of("lzh-Hant", "lzh", "zh-Hant", "zh-TW", "zh", "zh-CN", "und"));
        assertCandidateLocales("lzh-Hant", List.of("lzh-Hant", "lzh", "zh-Hant", "zh-TW", "zh", "zh-CN", "und"));
        assertCandidateLocales("lzh-Hans", List.of("lzh-Hans", "lzh", "zh-Hans", "zh-CN", "zh", "und"));
        assertCandidateLocales("cmn", List.of("cmn-Hans", "cmn", "zh-Hans", "zh-CN", "zh", "und"));
        assertCandidateLocales("cmn-Hans", List.of("cmn-Hans", "cmn", "zh-Hans", "zh-CN", "zh", "und"));
        assertCandidateLocales("yue", List.of("yue-Hans", "yue", "zh-Hans", "zh-CN", "zh", "und"));

        assertCandidateLocales("ja", List.of("ja", "und"));
        assertCandidateLocales("jpn", List.of("ja", "und"));
        assertCandidateLocales("ja-JP", List.of("ja-JP", "ja", "und"));
        assertCandidateLocales("jpn-JP", List.of("ja-JP", "ja", "und"));

        assertCandidateLocales("en", List.of("en", "und"));
        assertCandidateLocales("eng", List.of("en", "und"));
        assertCandidateLocales("en-US", List.of("en-US", "en", "und"));
        assertCandidateLocales("eng-US", List.of("en-US", "en", "und"));

        assertCandidateLocales("es", List.of("es", "und"));
        assertCandidateLocales("spa", List.of("es", "und"));

        assertCandidateLocales("ru", List.of("ru", "und"));
        assertCandidateLocales("rus", List.of("ru", "und"));

        assertCandidateLocales("uk", List.of("uk", "und"));
        assertCandidateLocales("ukr", List.of("uk", "und"));

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
    public void testMapToISO2Language() {
        assertEquals("en", LocaleUtils.mapToISO2Language("eng"));
        assertEquals("es", LocaleUtils.mapToISO2Language("spa"));
        assertEquals("ja", LocaleUtils.mapToISO2Language("jpn"));
        assertEquals("ru", LocaleUtils.mapToISO2Language("rus"));
        assertEquals("uk", LocaleUtils.mapToISO2Language("ukr"));
        assertEquals("zh", LocaleUtils.mapToISO2Language("zho"));
        assertEquals("zu", LocaleUtils.mapToISO2Language("zul"));

        assertNull(LocaleUtils.mapToISO2Language(null));
        assertNull(LocaleUtils.mapToISO2Language(""));
        assertNull(LocaleUtils.mapToISO2Language("cmn"));
        assertNull(LocaleUtils.mapToISO2Language("lzh"));
        assertNull(LocaleUtils.mapToISO2Language("tlh"));
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
}
