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

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * @author Glavo
 */
public class LocaleUtils {

    public static final Locale SYSTEM_DEFAULT = Locale.getDefault();

    public static final Locale LOCALE_ZH_HANS = Locale.forLanguageTag("zh-Hans");
    public static final Locale LOCALE_ZH_HANT = Locale.forLanguageTag("zh-Hant");

    public static boolean isEnglish(Locale locale) {
        return locale.getLanguage().equals("en") || locale.getLanguage().isEmpty();
    }

    static final Set<String> CHINESE_TRADITIONAL_REGIONS = Set.of(
            "TW", "HK", "MO"
    );

    static final Set<String> CHINESE_LATN_VARIANTS = Set.of(
            "pinyin", "wadegile"
    );

    private static final Set<String> CHINESE_LANGUAGES = Set.of(
            "zh",
            "zho", "cmn", "lzh", "cdo", "cjy", "cpx", "czh",
            "gan", "hak", "hsn", "mnp", "nan", "wuu", "yue"
    );

    public static boolean isChinese(Locale locale) {
        return CHINESE_LANGUAGES.contains(locale.getLanguage());
    }

    public static boolean isSimplifiedChinese(Locale locale) {
        if (isChinese(locale)) {
            String script = locale.getScript();
            if (script.isEmpty()) {
                if (locale.getLanguage().equals("lzh") || LocaleUtils.CHINESE_LATN_VARIANTS.contains(locale.getVariant()))
                    return false;
                else
                    return !CHINESE_TRADITIONAL_REGIONS.contains(locale.getCountry());
            } else
                return script.equals("Hans");
        } else {
            return false;
        }
    }

    public static @NotNull List<Locale> getCandidateLocales(Locale locale) {
        return DefaultResourceBundleControl.INSTANCE.getCandidateLocales("", locale);
    }

    private LocaleUtils() {
    }
}
