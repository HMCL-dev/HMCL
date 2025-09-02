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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

/// Overrides the default behavior of [ResourceBundle.Control], optimizing the candidate list generation logic.
///
/// Compared to the default implementation, [DefaultResourceBundleControl] optimizes the following scenarios:
///
/// - If no language is specified (such as [Locale#ROOT]), `en` is used instead.
/// - For all Chinese locales, if no script is specified, the script (`Hans`/`Hant`/`Latn`) is always inferred based on region and variant.
/// - For all Chinese locales, `zh-CN` is always added to the candidate list. If `zh-Hans` already exists in the candidate list,
///   `zh-CN` is inserted before `zh`; otherwise, it is inserted after `zh`.
/// - For all Traditional Chinese locales, `zh-TW` is always added to the candidate list (after `zh-Hant`).
/// - For all Chinese variants (such as `lzh`, `cmn`, `yue`, etc.), a candidate with the language code replaced by `zh`
///   is added to the end of the candidate list.
///
/// @author Glavo
public class DefaultResourceBundleControl extends ResourceBundle.Control {

    public static final DefaultResourceBundleControl INSTANCE = new DefaultResourceBundleControl();

    public DefaultResourceBundleControl() {
    }

    private static List<Locale> ensureEditable(List<Locale> list) {
        return list instanceof ArrayList<?>
                ? list
                : new ArrayList<>(list);
    }

    @Override
    public List<Locale> getCandidateLocales(String baseName, Locale locale) {
        if (locale.getLanguage().isEmpty())
            return super.getCandidateLocales(baseName, Locale.ENGLISH);

        if (LocaleUtils.isChinese(locale)) {
            String language = locale.getLanguage();
            String script = locale.getScript();

            if (script.isEmpty()) {
                if (LocaleUtils.CHINESE_LATN_VARIANTS.contains(locale.getVariant()))
                    script = "Latn";
                else if (LocaleUtils.isSimplifiedChinese(locale))
                    script = "Hans";
                else
                    script = "Hant";

                locale = new Locale.Builder()
                        .setLocale(locale)
                        .setScript(script)
                        .build();
            }

            List<Locale> locales = super.getCandidateLocales("", locale);

            if (!language.equals("zh")) {
                locales = ensureEditable(locales);
                locales.removeIf(it -> !it.getLanguage().equals(language));

                locales.addAll(super.getCandidateLocales("", new Locale.Builder()
                        .setLocale(locale)
                        .setLanguage("zh")
                        .build()));
            }

            if (!locales.contains(Locale.TRADITIONAL_CHINESE)) {
                int hantIdx = locales.indexOf(LocaleUtils.LOCALE_ZH_HANT);
                if (hantIdx >= 0) {
                    locales = ensureEditable(locales);
                    locales.add(hantIdx + 1, Locale.TRADITIONAL_CHINESE);
                }
            }

            if (!locales.contains(Locale.SIMPLIFIED_CHINESE)) {
                int chineseIdx = locales.indexOf(Locale.CHINESE);

                if (chineseIdx >= 0) {
                    locales = ensureEditable(locales);
                    if (locales.contains(LocaleUtils.LOCALE_ZH_HANS))
                        locales.add(chineseIdx, Locale.SIMPLIFIED_CHINESE);
                    else
                        locales.add(chineseIdx + 1, Locale.SIMPLIFIED_CHINESE);
                }
            }

            return locales;
        }

        return super.getCandidateLocales(baseName, locale);
    }
}
