package org.jackhuang.hmcl.util;

import org.jackhuang.hmcl.util.platform.OperatingSystem;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * @see <a href="https://github.com/fractureiser-investigation/fractureiser">fractureiser-investigation/fractureiser</a>
 * @see <a href="https://prismlauncher.org/news/cf-compromised-alert/#automated-script">[MALWARE WARNING] "fractureiser" malware in many popular Minecraft mods and modpacks</a>
 */
public final class FractureiserDetector {
    private FractureiserDetector() {
    }

    private static final class FractureiserException extends Exception {
    }

    public static boolean detect() {
        try {
            if (OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS) {
                Path appdata = Paths.get(System.getProperty("user.home"), "AppData");
                if (Files.isDirectory(appdata)) {
                    check(appdata.resolve("Roaming\\Microsoft\\Windows\\Start Menu\\Programs\\Startup\\run.bat"));

                    Path falseEdgePath = appdata.resolve("Local\\Microsoft Edge");
                    if (Files.exists(falseEdgePath)) {
                        check(falseEdgePath.resolve(".ref"));
                        check(falseEdgePath.resolve("client.jar"));
                        check(falseEdgePath.resolve("lib.dll"));
                        check(falseEdgePath.resolve("libWebGL64.jar"));
                        check(falseEdgePath.resolve("run.bat"));
                    }
                }
            } else if (OperatingSystem.CURRENT_OS == OperatingSystem.LINUX) {
                Path dataDir = Paths.get(System.getProperty("user.home"), ".config", ".data");
                if (Files.exists(dataDir)) {
                    check(dataDir.resolve(".ref"));
                    check(dataDir.resolve("client.jar"));
                    check(dataDir.resolve("lib.jar"));
                }
            }
        } catch (FractureiserException e) {
            return true;
        } catch (Throwable ignored) {
        }

        return false;
    }

    private static void check(Path path) throws FractureiserException {
        if (Files.isRegularFile(path)) {
            throw new FractureiserException();
        }
    }
}
