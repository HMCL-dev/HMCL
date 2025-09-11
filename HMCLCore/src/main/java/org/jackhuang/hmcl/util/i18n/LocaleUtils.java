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

import org.jackhuang.hmcl.util.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/**
 * @author Glavo
 */
public final class LocaleUtils {

    public static final Locale SYSTEM_DEFAULT = Locale.getDefault();

    public static final Locale LOCALE_ZH_HANS = Locale.forLanguageTag("zh-Hans");
    public static final Locale LOCALE_ZH_HANT = Locale.forLanguageTag("zh-Hant");

    public static final String DEFAULT_LANGUAGE_KEY = "default";

    /// Convert a locale to the language key.
    ///
    /// The language key is in the format of BCP 47 language tag.
    /// If the locale is the default locale (language is empty), "default" will be returned.
    public static String toLanguageKey(Locale locale) {
        return locale.getLanguage().isEmpty()
                ? DEFAULT_LANGUAGE_KEY
                : locale.stripExtensions().toLanguageTag();
    }

    public static boolean isISO1Language(String language) {
        return language.length() == 2;
    }

    public static boolean isISO3Language(String language) {
        return language.length() == 3;
    }

    public static @NotNull String getISO1Language(Locale locale) {
        String language = locale.getLanguage();
        if (language.isEmpty()) return "en";
        return isISO3Language(language) ? toISO1Language(language) : language;
    }

    /// Get the script of the locale. If the script is empty and the language is Chinese,
    /// the script will be inferred based on the language, the region and the variant.
    public static @NotNull String getScript(Locale locale) {
        if (locale.getScript().isEmpty()) {
            if (isChinese(locale)) {
                if (CHINESE_LATN_VARIANTS.contains(locale.getVariant()))
                    return "Latn";
                if (locale.getLanguage().equals("lzh") || CHINESE_TRADITIONAL_REGIONS.contains(locale.getCountry()))
                    return "Hant";
                else
                    return "Hans";
            }
        }

        return locale.getScript();
    }

    public static @NotNull List<Locale> getCandidateLocales(Locale locale) {
        return DefaultResourceBundleControl.INSTANCE.getCandidateLocales("", locale);
    }

    public static <T> @Nullable T getByCandidateLocales(Map<String, T> map, List<Locale> candidateLocales) {
        for (Locale locale : candidateLocales) {
            String key = toLanguageKey(locale);
            if (map.containsKey(key))
                return map.get(key);
        }
        return null;
    }

    /// Find all localized files in the given directory with the given base name and extension.
    /// The file name should be in the format of `baseName[_languageTag].ext`.
    ///
    /// @return A map of language key to file path.
    public static @NotNull @Unmodifiable Map<String, Path> findAllLocalizedFiles(Path dir, String baseName, String ext) {
        if (Files.isDirectory(dir)) {
            String suffix = "." + ext;
            String defaultName = baseName + suffix;
            String noDefaultPrefix = baseName + "_";

            try (Stream<Path> list = Files.list(dir)) {
                var result = new LinkedHashMap<String, Path>();

                list.forEach(file -> {
                    if (Files.isRegularFile(file)) {
                        String fileName = file.getFileName().toString();
                        if (fileName.equals(defaultName)) {
                            result.put(DEFAULT_LANGUAGE_KEY, file);
                        } else if (fileName.startsWith(noDefaultPrefix) && fileName.endsWith(suffix)) {
                            String languageKey = fileName.substring(noDefaultPrefix.length(), fileName.length() - suffix.length())
                                    .replace('_', '-');

                            if (!languageKey.isEmpty())
                                result.put(languageKey, file);
                        }
                    }
                });

                return result;
            } catch (IOException e) {
                LOG.warning("Failed to list files in directory " + dir, e);
            }
        }

        return Map.of();
    }

    /// Find all localized files in the given directory with the given base name and extensions.
    /// The file name should be in the format of `baseName[_languageTag].ext`.
    ///
    /// @return A map of language key to a map of extension to file path.
    public static @NotNull @Unmodifiable Map<String, Map<String, Path>> findAllLocalizedFiles(Path dir, String baseName, Collection<String> exts) {
        if (Files.isDirectory(dir)) {
            try (Stream<Path> list = Files.list(dir)) {
                var result = new LinkedHashMap<String, Map<String, Path>>();

                list.forEach(file -> {
                    if (Files.isRegularFile(file)) {
                        String fileName = file.getFileName().toString();
                        if (!fileName.startsWith(baseName))
                            return;

                        String ext = StringUtils.substringAfterLast(fileName, '.');
                        if (!exts.contains(ext))
                            return;

                        String languageKey;
                        int defaultFileNameLength = baseName.length() + ext.length() + 1;
                        if (fileName.length() == defaultFileNameLength)
                            languageKey = DEFAULT_LANGUAGE_KEY;
                        else if (fileName.length() > defaultFileNameLength + 1 && fileName.charAt(baseName.length()) == '_')
                            languageKey = fileName.substring(baseName.length() + 1, fileName.length() - ext.length() - 1)
                                    .replace('_', '-');
                        else
                            return;

                        result.computeIfAbsent(languageKey, key -> new HashMap<>())
                                .put(ext, file);
                    }
                });

                return result;
            } catch (IOException e) {
                LOG.warning("Failed to list files in directory " + dir, e);
            }
        }

        return Map.of();
    }

    // ---

    /// Try to convert ISO 639-3 language codes to ISO 639-1 language codes.
    public static String toISO1Language(String languageTag) {
        return switch (languageTag) {
            case "eng" -> "en";
            case "spa" -> "es";
            case "jpa" -> "ja";
            case "rus" -> "ru";
            case "ukr" -> "uk";
            case "zho", "cmn", "lzh", "cdo", "cjy", "cpx", "czh",
                 "gan", "hak", "hsn", "mnp", "nan", "wuu", "yue" -> "zh";
            default -> languageTag;
        };
    }

    public static boolean isEnglish(Locale locale) {
        return "en".equals(getISO1Language(locale));
    }

    public static final Set<String> CHINESE_TRADITIONAL_REGIONS = Set.of("TW", "HK", "MO");
    public static final Set<String> CHINESE_LATN_VARIANTS = Set.of("pinyin", "wadegile", "tongyong");

    public static boolean isChinese(Locale locale) {
        return "zh".equals(getISO1Language(locale));
    }

    private LocaleUtils() {
    }
}
