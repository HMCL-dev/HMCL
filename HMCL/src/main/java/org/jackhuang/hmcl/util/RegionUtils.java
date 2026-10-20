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

import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.util.gson.JsonSerializable;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.i18n.LocaleUtils;
import org.jackhuang.hmcl.util.io.NetworkUtils;
import org.jackhuang.hmcl.util.platform.NativeUtils;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jackhuang.hmcl.util.platform.windows.Kernel32;
import org.jackhuang.hmcl.util.platform.windows.WinConstants;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Set;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

@NotNullByDefault
public final class RegionUtils {

    /// Pre-check if the network is in China Mainland to avoid blocking the UI thread.
    public static void init() {
        Schedulers.io().execute(RegionUtils::isIsChinaMainlandNetwork);
    }

    private static final boolean IS_CHINA_MAINLAND_MACHINE = checkMachineIsChinaMainland();

    private static boolean checkMachineIsChinaMainland() {
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

    public static boolean isIsChinaMainlandMachine() {
        return IS_CHINA_MAINLAND_MACHINE;
    }

    private static volatile @Nullable Boolean isChinaMainlandNetwork;

    private static final Set<String> NOT_CHINA_MAINLAND_REGION_TIMEZONES = Set.of(
            "Antarctica/Casey",
            "Asia/Hong_Kong",
            "Asia/Irkutsk",
            "Asia/Kuching",
            "Asia/Macau",
            "Asia/Makassar",
            "Asia/Manila",
            "Asia/Singapore",
            "Asia/Taipei",
            "Asia/Ulaanbaatar",
            "Australia/Perth"
    );

    private static boolean checkChinaMainlandNetwork() {
        if (!IS_CHINA_MAINLAND_MACHINE)
            return false;

        if (ZonedDateTime.now().getOffset().getTotalSeconds() != Duration.ofHours(8).toSeconds())
            return false;

        if (NOT_CHINA_MAINLAND_REGION_TIMEZONES.contains(ZoneId.systemDefault().getId()))
            return false;

        @NotNullByDefault
        @JsonSerializable
        record IpWhoIsResponse(boolean success, String country_code) {
        }

        try {
            LOG.info("Fetching network information");
            IpWhoIsResponse result = JsonUtils.fromNonNullJson(NetworkUtils.doGet("https://ipwho.is"), IpWhoIsResponse.class);
            return !result.success || "CN".equalsIgnoreCase(result.country_code);
        } catch (Throwable e) {
            LOG.warning("Failed to get the network information", e);
            return true;
        }
    }

    public static boolean isIsChinaMainlandNetwork() {
        Boolean value = isChinaMainlandNetwork;
        if (value != null)
            return value;

        synchronized (RegionUtils.class) {
            value = isChinaMainlandNetwork;

            if (value != null)
                return value;

            boolean result = checkChinaMainlandNetwork();
            isChinaMainlandNetwork = result;
            return result;
        }
    }

    private RegionUtils() {
    }
}
