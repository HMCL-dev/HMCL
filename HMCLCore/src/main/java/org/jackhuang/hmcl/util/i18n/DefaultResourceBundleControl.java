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
/// - For all Traditional Chinese locales, `zh-TW` is always added to the candidate list (before `zh`).
/// - For all supported ISO 639-3 language code (such as `eng`, `zho`, `lzh`, etc.),
///  a candidate list with the language code replaced by the ISO 639-1 (Macro)language code is added to the end of the candidate list.
///
/// @author Glavo
public class DefaultResourceBundleControl extends ResourceBundle.Control {

    public static final DefaultResourceBundleControl INSTANCE = new DefaultResourceBundleControl();

    public DefaultResourceBundleControl() {
    }

    @Override
    public List<Locale> getCandidateLocales(String baseName, Locale locale) {
        return LocaleUtils.getCandidateLocales(locale);
    }

    @Override
    public Locale getFallbackLocale(String baseName, Locale locale) {
        // By default, when only the base bundle is found, it will attempt to fall back to Locale.getDefault() for further lookup.
        // Since we always use the base bundle as the English resource file, we want to suppress this behavior.
        return null;
    }
}
