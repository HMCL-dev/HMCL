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

    static int findFirstNotNumber(String str, int start) {
        if (start >= str.length())
            return -1;

        char ch = str.charAt(start);
        if (ch < '0' || ch > '9')
            return -1;

        for (int i = start + 1; i < str.length(); i++) {
            ch = str.charAt(i);
            if (ch < '0' || ch > '9')
                return i;
        }
        return str.length();
    }

    static int getJavaFeatureVersion(String javaVersion) {
        if (javaVersion == null)
            return -1;

        try {
            int end = findFirstNotNumber(javaVersion, 0);
            if (end < 0)
                return -1; // No valid version number found

            int major = Integer.parseInt(javaVersion.substring(0, end));
            if (major > 1)
                return major;

            if (major < 1)
                return -1; // Invalid major version

            // Java 1.x versions
            int start = end + 1;
            end = findFirstNotNumber(javaVersion, start);

            if (end < 0)
                return -1; // No valid minor version found

            return Integer.parseInt(javaVersion.substring(start, end));
        } catch (NumberFormatException e) {
            return -1; // version number is too long
        }
    }

    public static void main(String[] args) throws Throwable {
        if (getJavaFeatureVersion(System.getProperty("java.version")) >= MINIMUM_JAVA_VERSION) {
            EntryPoint.main(args);
        } else {
            String errorMessage = BootProperties.getResourceBundle().getString("boot.unsupported_java_version");
            System.err.println(errorMessage);
            SwingUtils.showErrorDialog(errorMessage);
            System.exit(1);
        }
    }
}
