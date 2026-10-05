package org.jackhuang.hmcl.util;

import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jackhuang.hmcl.util.platform.SystemUtils;
import org.jackhuang.hmcl.util.platform.windows.WinReg;

import java.util.concurrent.CompletableFuture;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public class AdminChecker {

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
