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
import org.jackhuang.hmcl.util.platform.NativeUtils;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jackhuang.hmcl.util.platform.windows.Kernel32;
import org.jackhuang.hmcl.util.platform.windows.WinConstants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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

    public static final Locale LOCALE_ZH_HANS = Locale.forLanguageTag("zh-Hans");
    public static final Locale LOCALE_ZH_HANT = Locale.forLanguageTag("zh-Hant");

    public static final String DEFAULT_LANGUAGE_KEY = "default";

    private static final Map<String, String> PARENT_LANGUAGE = loadCSV("sublanguages.csv");
    private static final Map<String, String> NORMALIZED_TAG = loadCSV("language_aliases.csv");
    private static final Map<String, String> DEFAULT_SCRIPT = loadCSV("default_script.csv");
    private static final Map<String, String> PREFERRED_LANGUAGE = Map.of("zh", "cmn");
    private static final Set<String> RTL_SCRIPTS = Set.of("Qabs", "Arab", "Hebr");
    private static final Set<String> CHINESE_TRADITIONAL_REGIONS = Set.of("TW", "HK", "MO");

    /// Load CSV files located in `/assets/lang/`.
    /// Each line in these files contains at least two elements.
    ///
    /// For example, if a file contains `value0,value1,value2`, the return value will be `{value1=value0, value2=value0}`.
    private static Map<String, String> loadCSV(String fileName) {
        InputStream resource = LocaleUtils.class.getResourceAsStream("/assets/lang/" + fileName);
        if (resource == null) {
            LOG.warning("Can't find file: " + fileName);
            return Map.of();
        }

        HashMap<String, String> result = new HashMap<>();
        try (resource) {
            new String(resource.readAllBytes(), StandardCharsets.UTF_8).lines().forEach(line -> {
                if (line.startsWith("#") || line.isBlank())
                    return;

                String[] items = line.split(",");
                if (items.length < 2) {
                    LOG.warning("Invalid line in " + fileName + ": " + line);
                    return;
                }

                String parent = items[0];
                for (int i = 1; i < items.length; i++) {
                    result.put(items[i], parent);
                }
            });
        } catch (Throwable e) {
            LOG.warning("Failed to load " + fileName, e);
        }

        return Map.copyOf(result);
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

    public static boolean isEnglish(Locale locale) {
        return "en".equals(getRootLanguage(locale));
    }

    public static boolean isChinese(Locale locale) {
        return "zh".equals(getRootLanguage(locale));
    }

    // ---

    /// Normalize the language code to the code in the IANA Language Subtag Registry.
    /// Typically, it normalizes ISO 639 alpha-3 codes to ISO 639 alpha-2 codes.
    public static @NotNull String normalizeLanguage(String language) {
        return language.isEmpty()
                ? "en"
                : NORMALIZED_TAG.getOrDefault(language, language);
    }

    /// If `language` is a sublanguage of a [macrolanguage](https://en.wikipedia.org/wiki/ISO_639_macrolanguage),
    /// return the macrolanguage; otherwise, return `null`.
    public static @Nullable String getParentLanguage(String language) {
        return PARENT_LANGUAGE.get(language);
    }

    /// @see #getRootLanguage(String)
    public static @NotNull String getRootLanguage(Locale locale) {
        return getRootLanguage(locale.getLanguage());
    }

    /// - If `language` is a sublanguage of a [macrolanguage](https://en.wikipedia.org/wiki/ISO_639_macrolanguage),
    /// return the macrolanguage;
    /// - If `language` is an ISO 639 alpha-3 language code and there is a corresponding ISO 639 alpha-2 language code, return the ISO 639 alpha-2 code;
    /// - If `language` is empty, return `en`;
    /// - Otherwise, return the `language`.
    public static @NotNull String getRootLanguage(String language) {
        language = normalizeLanguage(language);

        String parent = getParentLanguage(language);
        return parent != null ? parent : language;
    }

    /// If `language` is a macrolanguage, try to map it to the most commonly used individual language.
    ///
    /// For example, if `language` is `zh`, this method will return `cmn`.
    public static @NotNull String getPreferredLanguage(String language) {
        language = normalizeLanguage(language);
        return PREFERRED_LANGUAGE.getOrDefault(language, language);
    }

    /// Get the script of the locale. If the script is empty and the language is Chinese,
    /// the script will be inferred based on the language, the region and the variant.
    public static @NotNull String getScript(Locale locale) {
        if (locale.getScript().isEmpty()) {
            if (!locale.getVariant().isEmpty()) {
                String script = DEFAULT_SCRIPT.get(locale.getVariant());
                if (script != null)
                    return script;
            }

            if ("UD".equals(locale.getCountry())) {
                return "Qabs";
            }

            String script = DEFAULT_SCRIPT.get(normalizeLanguage(locale.getLanguage()));
            if (script != null)
                return script;

            if (isChinese(locale)) {
                return CHINESE_TRADITIONAL_REGIONS.contains(locale.getCountry())
                        ? "Hant"
                        : "Hans";
            }

            return "";
        }

        return locale.getScript();
    }

    public static @NotNull TextDirection getTextDirection(Locale locale) {
        return RTL_SCRIPTS.contains(getScript(locale))
                ? TextDirection.RIGHT_TO_LEFT
                : TextDirection.LEFT_TO_RIGHT;
    }

    private static final ConcurrentMap<Locale, List<Locale>> CANDIDATE_LOCALES = new ConcurrentHashMap<>();

    public static @NotNull List<Locale> getCandidateLocales(Locale locale) {
        return CANDIDATE_LOCALES.computeIfAbsent(locale, LocaleUtils::createCandidateLocaleList);
    }

    private static List<Locale> createCandidateLocaleList(Locale locale) {
        String language = getPreferredLanguage(locale.getLanguage());
        String script = getScript(locale);
        String region = locale.getCountry();
        List<String> variants = locale.getVariant().isEmpty()
                ? List.of()
                : List.of(locale.getVariant().split("[_\\-]"));

        ArrayList<Locale> result = new ArrayList<>();
        do {
            addCandidateLocales(result, language, script, region, variants);
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

    private LocaleUtils() {
    }
}
