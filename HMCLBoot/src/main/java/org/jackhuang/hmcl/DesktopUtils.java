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

import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

/// @author Glavo
public final class DesktopUtils {
    private static final String[] linuxBrowsers = {
            "xdg-open",
            "google-chrome",
            "firefox",
            "microsoft-edge",
            "opera",
            "konqueror",
            "mozilla"
    };

    public static Path which(String command) {
        String path = System.getenv("PATH");
        if (path == null)
            return null;

        try {
            for (String item : path.split(File.pathSeparator)) {
                try {
                    Path program = Paths.get(item, command);
                    if (Files.isExecutable(program))
                        return program.toRealPath();
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }

        return null;
    }

    public static void openLink(String link) {
        String osName = System.getProperty("os.name").toLowerCase(Locale.ROOT);

        Throwable e1 = null;
        try {
            if (osName.startsWith("windows")) {
                Runtime.getRuntime().exec(new String[]{"rundll32.exe", "url.dll,FileProtocolHandler", link});
                return;
            } else if (osName.startsWith("mac") || osName.startsWith("darwin")) {
                Runtime.getRuntime().exec(new String[]{"open", link});
                return;
            } else {
                for (String browser : linuxBrowsers) {
                    Path path = which(browser);
                    if (path != null) {
                        try {
                            Runtime.getRuntime().exec(new String[]{path.toString(), link});
                            return;
                        } catch (Throwable ignored) {
                        }
                    }
                }
                System.err.println("No known browser found");
            }
        } catch (Throwable e) {
            e1 = e;
        }

        try {
            java.awt.Desktop.getDesktop().browse(new URI(link));
        } catch (Throwable e2) {
            if (e1 != null)
                e2.addSuppressed(e1);

            e2.printStackTrace(System.err);
        }
    }

    private DesktopUtils() {
    }
}
