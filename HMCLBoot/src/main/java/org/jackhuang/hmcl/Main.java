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

import javax.swing.*;
import java.net.URISyntaxException;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Paths;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.ResourceBundle;

/**
 * @author Glavo
 */
public final class Main {
    private static final int MINIMUM_JAVA_VERSION = 17;
    private static final String DOWNLOAD_PAGE = "https://hmcl.huangyuhui.net/download/";

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

    static void showErrorAndExit(String[] args) {
        SwingUtils.initLookAndFeel();

        ResourceBundle resourceBundle = BootProperties.getResourceBundle();
        String errorTitle = resourceBundle.getString("boot.message.error");

        if (args.length > 0 && args[0].equals("--apply-to")) {
            String errorMessage = resourceBundle.getString("boot.manual_update");
            System.err.println(errorMessage);
            int result = JOptionPane.showOptionDialog(null, errorMessage, errorTitle, JOptionPane.YES_NO_OPTION,
                    JOptionPane.ERROR_MESSAGE, null, null, null);

            if (result == JOptionPane.YES_OPTION) {
                System.out.println("Open " + DOWNLOAD_PAGE);
                DesktopUtils.openLink(DOWNLOAD_PAGE);
            }
        } else {
            String errorMessage = resourceBundle.getString("boot.unsupported_java_version");
            System.err.println(errorMessage);
            SwingUtils.showErrorDialog(errorMessage, errorTitle);
        }

        System.exit(1);
    }

    private static void checkDirectoryPath() {
        String currentDir = System.getProperty("user.dir", "");
        String jarPath = getThisJarPath();
        if (currentDir.contains("!")) {
            SwingUtils.initLookAndFeel();
            System.err.println("The current working path contains an exclamation mark: " + currentDir);
            // No Chinese translation because both Swing and JavaFX cannot render Chinese character properly when exclamation mark exists in the path.
            SwingUtils.showErrorDialog("Exclamation mark(!) is not allowed in the working path.\n" + "The path is " + currentDir);
            System.exit(1);
        } else if (jarPath != null && jarPath.contains("!")) {
            SwingUtils.initLookAndFeel();
            System.err.println("The jar path contains an exclamation mark: " + jarPath);
            // No Chinese translation because both Swing and JavaFX cannot render Chinese character properly when exclamation mark exists in the path.
            SwingUtils.showErrorDialog("Exclamation mark(!) is not allowed in the path where HMCL is in.\n" + "The path is " + jarPath);
            System.exit(1);
        }
    }

    private static String getThisJarPath() {
        ProtectionDomain protectionDomain = Main.class.getProtectionDomain();
        if (protectionDomain == null)
            return null;

        CodeSource codeSource = protectionDomain.getCodeSource();
        if (codeSource == null || codeSource.getLocation() == null)
            return null;

        try {
            return Paths.get(codeSource.getLocation().toURI()).toAbsolutePath().normalize().toString();
        } catch (FileSystemNotFoundException | IllegalArgumentException | URISyntaxException e) {
            return null;
        }
    }

    public static void main(String[] args) throws Throwable {
        checkDirectoryPath();
        if (getJavaFeatureVersion(System.getProperty("java.version")) >= MINIMUM_JAVA_VERSION) {
            EntryPoint.main(args);
        } else {
            showErrorAndExit(args);
        }
    }
}
