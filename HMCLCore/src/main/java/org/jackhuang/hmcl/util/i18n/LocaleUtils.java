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

import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.io.IOUtils;
import org.jackhuang.hmcl.util.platform.NativeUtils;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jackhuang.hmcl.util.platform.windows.Kernel32;
import org.jackhuang.hmcl.util.platform.windows.WinConstants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
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

    private static final Map<String, String> subLanguageToParent = new HashMap<>();
    private static final Map<String, String> iso3To2 = new HashMap<>();
    private static final Set<String> rtl = new HashSet<>();

    static {
        try {
            for (String line : Lang.toIterable(IOUtils.readFullyAsString(LocaleUtils.class.getResourceAsStream("/assets/lang/sublanguages.csv")).lines())) {
                if (line.startsWith("#") || line.isBlank()) {
                    continue;
                }

                String[] languages = line.split(",");
                if (languages.length < 2) {
                    LOG.warning("Invalid line in sublanguages.csv: " + line);
                    continue;
                }

                String parent = languages[0];
                for (int i = 1; i < languages.length; i++) {
                    subLanguageToParent.put(languages[i], parent);
                }
            }
        } catch (Throwable e) {
            LOG.warning("Failed to load sublanguages.csv", e);
        }

        try {
            // Line Format: (?<iso2>[a-z]{2}),(?<iso3>[a-z]{3})
            for (String line : Lang.toIterable(IOUtils.readFullyAsString(LocaleUtils.class.getResourceAsStream("/assets/lang/iso_languages.csv")).lines())) {
                if (line.startsWith("#") || line.isBlank()) {
                    continue;
                }

                String[] parts = line.split(",", 3);
                if (parts.length != 2) {
                    LOG.warning("Invalid line in iso_languages.csv: " + line);
                    continue;
                }

                iso3To2.put(parts[1], parts[0]);
            }
        } catch (Throwable e) {
            LOG.warning("Failed to load iso_languages.csv", e);
        }

        try {
            for (String line : Lang.toIterable(IOUtils.readFullyAsString(LocaleUtils.class.getResourceAsStream("/assets/lang/rtl.txt")).lines())) {
                if (line.startsWith("#") || line.isBlank()) {
                    continue;
                }
                rtl.add(line.trim());
            }
        } catch (Throwable e) {
            LOG.warning("Failed to load rtl.txt", e);
        }
    }

    private static Locale getInstance(String language, String script, String region,
                                      String variant) {
        Locale.Builder builder = new Locale.Builder();
        if (!language.isEmpty()) builder.setLanguage(language);
        if (!script.isEmpty()) builder.setScript(script);
        if (!region.isEmpty()) builder.setRegion(region);
        if (!variant.isEmpty()) builder.setVariant(variant);
        return builder.build();
    }

    /// Convert a locale to the language key.
    ///
    /// The language key is in the format of BCP 47 language tag.
    /// If the locale is the default locale (language is empty), "default" will be returned.
    public static String toLanguageKey(Locale locale) {
        return locale.getLanguage().isEmpty()
                ? DEFAULT_LANGUAGE_KEY
                : locale.stripExtensions().toLanguageTag();
    }

    public static @NotNull String getRootLanguage(Locale locale) {
        return getRootLanguage(locale.getLanguage());
    }

    /// - If `language` is a sublanguage of a [macrolanguage](https://en.wikipedia.org/wiki/ISO_639_macrolanguage),
    /// return the macrolanguage;
    /// - If `language` is an ISO 639 alpha-3 language code and there is a corresponding ISO 639 alpha-2 language code, return the ISO 639 alpha-2 code;
    /// - If `language` is empty, return `en`;
    /// - Otherwise, return the `language`.
    public static @NotNull String getRootLanguage(String language) {
        if (language.isEmpty()) return "en";
        if (language.length() <= 2)
            return language;

        String iso2 = mapToISO2Language(language);
        if (iso2 != null)
            return iso2;

        String parent = getParentLanguage(language);
        return parent != null ? parent : language;
    }

    /// Get the script of the locale. If the script is empty and the language is Chinese,
    /// the script will be inferred based on the language, the region and the variant.
    public static @NotNull String getScript(Locale locale) {
        if (locale.getScript().isEmpty()) {
            if (isEnglish(locale)) {
                if ("UD".equals(locale.getCountry())) {
                    return "Qabs";
                }
            }

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

    public static @NotNull TextDirection getTextDirection(Locale locale) {
        TextDirection direction = rtl.contains(getRootLanguage(locale))
                ? TextDirection.RIGHT_TO_LEFT
                : TextDirection.LEFT_TO_RIGHT;

        if ("Qabs".equals(getScript(locale))) {
            direction = switch (direction) {
                case RIGHT_TO_LEFT -> TextDirection.LEFT_TO_RIGHT;
                case LEFT_TO_RIGHT -> TextDirection.RIGHT_TO_LEFT;
            };
        }

        return direction;
    }

    private static final ConcurrentMap<Locale, List<Locale>> CANDIDATE_LOCALES = new ConcurrentHashMap<>();

    public static @NotNull List<Locale> getCandidateLocales(Locale locale) {
        return CANDIDATE_LOCALES.computeIfAbsent(locale, LocaleUtils::createCandidateLocaleList);
    }

    // -------------

    private static List<Locale> createCandidateLocaleList(Locale locale) {
        String language = locale.getLanguage();
        if (language.isEmpty())
            return List.of(Locale.ENGLISH, Locale.ROOT);

        String script = getScript(locale);
        String region = locale.getCountry();
        List<String> variants = locale.getVariant().isEmpty()
                ? List.of()
                : List.of(locale.getVariant().split("[_\\-]"));

        ArrayList<Locale> result = new ArrayList<>();
        do {
            String currentLanguage;

            if (language.length() <= 2) {
                currentLanguage = language;
            } else {
                String iso2 = mapToISO2Language(language);
                currentLanguage = iso2 != null
                        ? iso2
                        : language;
            }

            addCandidateLocales(result, currentLanguage, script, region, variants);
        } while ((language = getParentLanguage(language)) != null);

        result.add(Locale.ROOT);
        return List.copyOf(result);
    }

    private static void addCandidateLocales(ArrayList<Locale> list,
                                            String language,
                                            String script,
                                            String region,
                                            List<String> variants) {
        if (!variants.isEmpty()) {
            for (String v : variants) {
                list.add(getInstance(language, script, region, v));
            }
        }
        if (!region.isEmpty()) {
            list.add(getInstance(language, script, region, ""));
        }
        if (!script.isEmpty()) {
            list.add(getInstance(language, script, "", ""));
            if (!variants.isEmpty()) {
                for (String v : variants) {
                    list.add(getInstance(language, "", region, v));
                }
            }
            if (!region.isEmpty()) {
                list.add(getInstance(language, "", region, ""));
            }
        }

        list.add(getInstance(language, "", "", ""));

        if (language.equals("zh")) {
            if (list.contains(LocaleUtils.LOCALE_ZH_HANT) && !list.contains(Locale.TRADITIONAL_CHINESE)) {
                int chineseIdx = list.indexOf(Locale.CHINESE);
                if (chineseIdx >= 0)
                    list.add(chineseIdx, Locale.TRADITIONAL_CHINESE);
            }

            if (!list.contains(Locale.SIMPLIFIED_CHINESE)) {
                int chineseIdx = list.indexOf(Locale.CHINESE);

                if (chineseIdx >= 0) {
                    if (list.contains(LocaleUtils.LOCALE_ZH_HANS))
                        list.add(chineseIdx, Locale.SIMPLIFIED_CHINESE);
                    else
                        list.add(chineseIdx + 1, Locale.SIMPLIFIED_CHINESE);
                }
            }
        }
    }

    // -------------

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

    /// Map ISO 639 alpha-3 language codes to ISO 639 alpha-2 language codes.
    /// Returns `null` if there is no corresponding ISO 639 alpha-2 language code.
    public static @Nullable String mapToISO2Language(String iso3Language) {
        return iso3To2.get(iso3Language);
    }

    /// If `language` is a sublanguage of a [macrolanguage](https://en.wikipedia.org/wiki/ISO_639_macrolanguage),
    /// return the macrolanguage; otherwise, return `null`.
    public static @Nullable String getParentLanguage(String language) {
        return subLanguageToParent.get(language);
    }

    public static boolean isEnglish(Locale locale) {
        return "en".equals(getRootLanguage(locale));
    }

    public static final Set<String> CHINESE_TRADITIONAL_REGIONS = Set.of("TW", "HK", "MO");
    public static final Set<String> CHINESE_LATN_VARIANTS = Set.of("pinyin", "wadegile", "tongyong");

    public static boolean isChinese(Locale locale) {
        return "zh".equals(getRootLanguage(locale));
    }

    public static final boolean IS_CHINA_MAINLAND = isChinaMainland();

    private static boolean isChinaMainland() {
        if ("Asia/Shanghai".equals(ZoneId.systemDefault().getId()))
            return true;

        // Check if the time zone is UTC+8
        if (ZonedDateTime.now().getOffset().getTotalSeconds() == Duration.ofHours(8).toSeconds()) {
            if ("CN".equals(LocaleUtils.SYSTEM_DEFAULT.getCountry()))
                return true;

            if (OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS && NativeUtils.USE_JNA) {
                Kernel32 kernel32 = Kernel32.INSTANCE;

                // https://learn.microsoft.com/windows/win32/intl/table-of-geographical-locations
                if (kernel32 != null && kernel32.GetUserGeoID(WinConstants.GEOCLASS_NATION) == 45) // China
                    return true;
            }
        }

        return false;
    }

    private LocaleUtils() {
    }
}
