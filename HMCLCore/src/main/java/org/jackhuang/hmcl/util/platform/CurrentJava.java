package org.jackhuang.hmcl.util.platform;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.logging.Level;

import static org.jackhuang.hmcl.util.Logging.LOG;

public final class CurrentJava {
    private CurrentJava() {
    }

    private static final boolean toolsPackageStatus = tryLoadJDKToolsJar();

    public static boolean checkToolPackageDepdencies() {
        return toolsPackageStatus;
    }

    private static boolean tryLoadJDKToolsJar() {
        if (JavaVersion.CURRENT_JAVA.getParsedVersion() > 8) {
            try (InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream("com/sun/tools/attach/VirtualMachine.class")) {
                return inputStream != null;
            } catch (IOException e) {
                return false;
            }
        }

        // Java 8
        File tools = new File(System.getProperty("java.home"), "../lib/tools.jar");
        if (tools.exists()) {
            LOG.log(Level.INFO, "Try to Load tools.jar on Java 8.");
            try {
                if (CurrentJava.class.getClassLoader() instanceof URLClassLoader) {
                    Method addURLMethod = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
                    addURLMethod.setAccessible(true);
                    addURLMethod.invoke(CurrentJava.class.getClassLoader(), tools.toURI().toURL());
                }
            } catch (Throwable e) {
                LOG.log(Level.WARNING, "Failed to load tools.jar. Maybe the java version is not supported.", e);
            }

            try (InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream("com/sun/tools/attach/VirtualMachine.class")) {
                return inputStream != null;
            } catch (IOException e) {
                return false;
            }
        }
        return false;
    }
}
