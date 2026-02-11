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