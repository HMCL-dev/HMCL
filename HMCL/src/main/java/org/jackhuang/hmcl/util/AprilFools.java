// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util;

import org.jackhuang.hmcl.util.i18n.LocaleUtils;

import java.time.LocalDate;
import java.time.Month;
import java.util.List;

import static org.jackhuang.hmcl.setting.SettingsManager.settings;

/// April Fools' Day utilities.
///
/// This class provides methods to check if it is April Fools' Day or near April Fools' Day.
/// It also provides a method to check if April Fools is enabled.
///
/// @author Glavo
public final class AprilFools {

    private static final boolean ENABLED;

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

        ENABLED = aprilFoolsMode && !settings().disableAprilFoolsProperty().get();
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
