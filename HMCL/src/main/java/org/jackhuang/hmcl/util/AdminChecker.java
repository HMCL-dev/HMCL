/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026  huangyuhui <huanghongxun2008@126.com> and contributors
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
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jackhuang.hmcl.util.platform.SystemUtils;
import org.jackhuang.hmcl.util.platform.windows.WinReg;

import java.util.concurrent.CompletableFuture;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class AdminChecker {

    private AdminChecker() {
    }

    public static CompletableFuture<Boolean> isAdmin() {
        return CompletableFuture.supplyAsync(AdminChecker::checkAdmin, Schedulers.io());
    }

    private static boolean checkAdmin() {
        return switch (OperatingSystem.CURRENT_OS) {
            case WINDOWS -> isWindowsAdmin();
            case LINUX, FREEBSD, MACOS -> isUnixRoot();
            default -> {
                LOG.warning("Unknown OS: " + OperatingSystem.CURRENT_OS);
                yield false;
            }
        };
    }

    private static boolean isWindowsAdmin() {
        WinReg reg = WinReg.INSTANCE;
        if (reg == null)
            return false;
        try {
            return reg.exists(WinReg.HKEY.HKEY_USERS, "S-1-5-19");
        } catch (Exception e) {
            LOG.warning("Failed to check Windows administrator privileges", e);
            return false;
        }
    }

    private static boolean isUnixRoot() {
        try {
            return "0".equals(SystemUtils.run("id", "-u").trim());
        } catch (Exception e) {
            LOG.warning("Failed to check root", e);
            return false;
        }
    }
}
