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
package org.jackhuang.hmcl;

import org.jackhuang.hmcl.util.SwingUtils;

/**
 * @author Glavo
 */
public final class Main {
    private static final int MINIMUM_JAVA_VERSION = 11;

    private Main() {
    }

    /**
     * Check if the current Java version is compatible with HMCL.
     */
    static boolean checkJavaVersion(String javaVersion) {
        if (javaVersion == null) {
            return false;
        }

        try {
            int major;
            int dot = javaVersion.indexOf('.');
            int dash = javaVersion.indexOf('-');

            if (dot >= 0) {
                major = Integer.parseInt(javaVersion.substring(0, dot));
                if (major == 1 && dot < javaVersion.length() - 1) {
                    int begin = dot + 1;
                    dot = javaVersion.indexOf('.', begin);

                    major = dot > begin
                            ? Integer.parseInt(javaVersion.substring(begin, dot))
                            : Integer.parseInt(javaVersion.substring(begin));
                }
            } else {
                if (dash >= 0) {
                    major = Integer.parseInt(javaVersion.substring(0, dash));
                } else {
                    major = Integer.parseInt(javaVersion);
                }
            }

            return major >= MINIMUM_JAVA_VERSION;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static void main(String[] args) throws Throwable {
        if (checkJavaVersion(System.getProperty("java.version"))) {
            EntryPoint.main(args);
        } else {
            String errorMessage = BootProperties.getResourceBundle().getString("boot.unsupported_java_version");
            System.err.println(errorMessage);
            SwingUtils.showErrorDialog(errorMessage);
            System.exit(1);
        }
    }
}