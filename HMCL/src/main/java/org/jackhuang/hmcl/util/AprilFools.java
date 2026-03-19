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

import org.jackhuang.hmcl.setting.ConfigHolder;

import java.time.LocalDate;
import java.time.Month;

public final class AprilFools {

    private static final boolean START_IN_APRIL_FOOLS_DAY;
    private static final boolean START_IN_NEAR_APRIL_FOOLS_DAY;
    private static final boolean ENABLED;

    static {
        var date = LocalDate.now();
        START_IN_APRIL_FOOLS_DAY = date.getMonth() == Month.APRIL && date.getDayOfMonth() == 1;
        START_IN_NEAR_APRIL_FOOLS_DAY = START_IN_APRIL_FOOLS_DAY || date.getMonth() == Month.MARCH && date.getDayOfMonth() > 30;

        String value = System.getProperty("hmcl.april_fools", System.getenv("HMCL_APRIL_FOOLS"));
        if ("true".equalsIgnoreCase(value)) {
            ENABLED = true;
        } else if ("false".equalsIgnoreCase(value) || ConfigHolder.config().isDisableAprilFools()) {
            ENABLED = false;
        } else {
            ENABLED = START_IN_APRIL_FOOLS_DAY;
        }
    }

    /// Whether it is April Fools' Day.
    ///
    /// This method returns true if it is April Fools' Day.
    public static boolean isStartInAprilFoolsDay() {
        return START_IN_APRIL_FOOLS_DAY;
    }

    /// Whether it is near April Fools' Day.
    ///
    /// This method returns true if it is April Fools' Day or the day before or after April Fools' Day.
    /// It is useful for displaying special features or messages related to April Fools' Day.
    public static boolean isStartInNearAprilFoolsDay() {
        return START_IN_NEAR_APRIL_FOOLS_DAY;
    }

    public static boolean isEnabled() {
        return ENABLED;
    }

    private AprilFools() {
    }
}
