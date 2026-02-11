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

public final class WineDetector {
    public static boolean isRunningUnderWine() {
        String osName = System.getProperty("os.name", "");
        if (!osName.toLowerCase(java.util.Locale.ROOT).contains("win")) {
            return false;
        }

        return checkWineRegistryKey() && checkWineEnv();
    }

    private static boolean checkWineRegistryKey() {
        Process process = null;
        try {
            process = Runtime.getRuntime().exec(
                    new String[]{"reg", "query", "HKLM\\Software\\Wine", "/reg:64"}
            );
            if (!process.waitFor(800, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        } finally {
            if (process != null && process.isAlive()) {
                process.destroy();
            }
        }
    }

    private static boolean checkWineEnv() {
        return System.getenv("WINEPREFIX") != null;
    }

    private WineDetector() {
    }
}
