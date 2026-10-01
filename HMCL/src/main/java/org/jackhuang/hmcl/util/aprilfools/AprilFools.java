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
package org.jackhuang.hmcl.util.aprilfools;

import org.jackhuang.hmcl.util.i18n.LocaleUtils;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDate;
import java.time.Month;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

import static org.jackhuang.hmcl.setting.SettingsManager.settings;
import static org.jackhuang.hmcl.setting.SettingsManager.state;

/// April Fools' Day utilities.
///
/// This class provides methods to check if it is April Fools' Day or if April Fools is enabled.
/// It also provides an enum of different April Fools effect types.
///
/// @author Glavo
public final class AprilFools {

    public static final String APRIL_FOOLS_TIP = "aprilFools";

    private static final boolean ENABLED;

    private static final int currentYear;

    static {
        var date = LocalDate.now();
        currentYear = date.getYear();

        // Some countries/regions may oppose April Fools' Day for various reasons.
        // Therefore, we use a regional whitelist to avoid risks.
        // Currently, we have only listed a limited set of countries/regions for testing.
        // We will investigate more countries/regions in the future to expand this list.
        boolean supportedRegion = List.of(
                "CN", "TW", "HK", "MO", "JP", "KR", "VN", "SG", "MY",
                "ES", "DE", "FR", "GB", "RU", "UA", "US"
        ).contains(LocaleUtils.SYSTEM_DEFAULT.getCountry());

        String value = System.getProperty("hmcl.april_fools", System.getenv("HMCL_APRIL_FOOLS"));
        if ("true".equalsIgnoreCase(value)) {
            ENABLED = true;
        } else if ("false".equalsIgnoreCase(value)) {
            ENABLED = false;
        } else {
            ENABLED = supportedRegion && date.getMonth() == Month.APRIL && date.getDayOfMonth() == 1
                    && !settings().disableAprilFoolsProperty().get()
                    && !(state().getShownTips().get(APRIL_FOOLS_TIP) instanceof Number year && year.intValue() >= currentYear);
        }
    }

    /// Whether April Fools is enabled.
    ///
    /// This method returns true if April Fools is enabled.
    public static boolean isEnabled() {
        return ENABLED;
    }

    public static @Nullable Type getRandomTypeToPrompt() {
        if (!isEnabled()) return null;
        Object[] supportedTypes = Arrays.stream(Type.values()).filter(t -> t.shouldPromptSupplier.get()).toArray();
        if (supportedTypes.length == 0) return null;
        return (Type) supportedTypes[new Random().nextInt(supportedTypes.length)];
    }

    public static void updateShownTips() {
        state().getShownTips().put(APRIL_FOOLS_TIP, currentYear);
    }

    private AprilFools() {
    }

    public enum Type {
        LZH(Lzh::shouldPrompt),
        THE_COPPER_AGE(() -> !TheCopperAge.isOxidizing());

        private final Supplier<Boolean> shouldPromptSupplier;

        Type(Supplier<Boolean> supportedLazy) {
            this.shouldPromptSupplier = supportedLazy;
        }
    }
}
