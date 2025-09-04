package org.jackhuang.hmcl.util;

import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jackhuang.hmcl.util.platform.windows.WinReg;

public class AdminChecker {

    private AdminChecker() {
    }

    public static boolean isAdmin() {
        if (OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS) {
            return isWindowsAdmin();
        } else if (OperatingSystem.CURRENT_OS.isLinuxOrBSD() && OperatingSystem.CURRENT_OS == OperatingSystem.MACOS) {
            return isUnixRoot();
        } else {
            // 未知系统，保守返回 false
            System.err.println("Unknown OS: " + OperatingSystem.CURRENT_OS);
            return false;
        }
    }

    private static boolean isWindowsAdmin() {
        WinReg reg = WinReg.INSTANCE;
        try {
            return reg.exists(WinReg.HKEY.HKEY_USERS, "S-1-5-19");
        } catch (Throwable t) {
            // 捕获 AccessException、JNA 错误等
            return false;
        }
    }

    private static boolean isUnixRoot() {
        try {
            ProcessBuilder pb = new ProcessBuilder("id", "-u");
            Process process = pb.start();
            java.util.Scanner scanner = new java.util.Scanner(process.getInputStream());
            String uid = scanner.hasNext() ? scanner.next() : "";
            scanner.close();
            process.waitFor();
            return "0".equals(uid.trim());
        } catch (Exception e) {
            return false;
        }
    }
}
