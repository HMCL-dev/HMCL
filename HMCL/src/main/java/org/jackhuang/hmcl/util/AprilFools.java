/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026 huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.util;

import org.jackhuang.hmcl.util.i18n.LocaleUtils;

import java.time.LocalDate;
import java.time.Month;
import java.util.List;

import static org.jackhuang.hmcl.setting.ConfigHolder.config;

/// April Fools' Day utilities.
///
/// This class provides methods to check if it is April Fools' Day or near April Fools' Day.
/// It also provides a method to check if April Fools is enabled.
///
/// @author Glavo
public final class AprilFools {

    private static final boolean ENABLED;
    private static final boolean SHOW_APRIL_FOOLS_SETTINGS;

    static {
        var date = LocalDate.now();

        // Some countries/regions may oppose April Fools' Day for various reasons.
        // Therefore, we use a regional whitelist to avoid risks.
        // Currently, we have only listed a limited set of countries/regions for testing.
        // We will investigate more countries/regions in the future to expand this list.
        boolean supportedRegion = List.of(
                "CN", "TW", "HK", "MO", "JP", "KR", "VN", "SG", "MY",
                "ES", "DE", "FR", "GB", "RU", "UA", "US"
        ).contains(LocaleUtils.SYSTEM_DEFAULT.getCountry());

        boolean aprilFoolsMode;
        String value = System.getProperty("hmcl.april_fools", System.getenv("HMCL_APRIL_FOOLS"));
        if ("true".equalsIgnoreCase(value))
            aprilFoolsMode = true;
        else if ("false".equalsIgnoreCase(value) || !supportedRegion)
            aprilFoolsMode = false;
        else
            aprilFoolsMode = date.getMonth() == Month.APRIL && date.getDayOfMonth() == 1;

        ENABLED = aprilFoolsMode && !config().isDisableAprilFools();
        SHOW_APRIL_FOOLS_SETTINGS = aprilFoolsMode || supportedRegion && date.getMonth() == Month.MARCH && date.getDayOfMonth() > 30;
    }

    /// Whether April Fools settings should be shown.
    ///
    /// This method returns true if April Fools settings should be shown.
    public static boolean isShowAprilFoolsSettings() {
        return SHOW_APRIL_FOOLS_SETTINGS;
    }

    /// Whether April Fools is enabled.
    ///
    /// This method returns true if April Fools is enabled.
    public static boolean isEnabled() {
        return ENABLED;
    }

    private AprilFools() {
    }
}
