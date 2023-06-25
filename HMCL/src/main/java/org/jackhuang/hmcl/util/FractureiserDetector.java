package org.jackhuang.hmcl.util;

import org.jackhuang.hmcl.util.platform.OperatingSystem;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;

/**
 * @see <a href="https://github.com/fractureiser-investigation/fractureiser">fractureiser-investigation/fractureiser</a>
 * @see <a href="https://prismlauncher.org/news/cf-compromised-alert/#automated-script">[MALWARE WARNING] "fractureiser" malware in many popular Minecraft mods and modpacks</a>
 */
public final class FractureiserDetector {
    private FractureiserDetector() {
    }

    public static boolean detect() {
        try {
            ArrayList<Path> badPaths = new ArrayList<>();

            if (OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS) {
                Path appdata = Paths.get(System.getProperty("user.home"), "AppData");
                if (Files.isDirectory(appdata)) {
                    badPaths.add(appdata.resolve("Roaming\\Microsoft\\Windows\\Start Menu\\Programs\\Startup\\run.bat"));

                    Path falseEdgePath = appdata.resolve("Local\\Microsoft Edge");
                    if (Files.exists(falseEdgePath)) {
                        badPaths.add(falseEdgePath.resolve(".ref"));
                        badPaths.add(falseEdgePath.resolve("client.jar"));
                        badPaths.add(falseEdgePath.resolve("lib.dll"));
                        badPaths.add(falseEdgePath.resolve("libWebGL64.jar"));
                        badPaths.add(falseEdgePath.resolve("run.bat"));
                    }
                }
            } else if (OperatingSystem.CURRENT_OS == OperatingSystem.LINUX) {
                Path dataDir = Paths.get(System.getProperty("user.home"), ".config", ".data");
                if (Files.exists(dataDir)) {
                    badPaths.add(dataDir.resolve(".ref"));
                    badPaths.add(dataDir.resolve("client.jar"));
                    badPaths.add(dataDir.resolve("lib.jar"));
                }
            }

            for (Path badPath : badPaths) {
                if (Files.exists(badPath)) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }

        return false;
    }
}
