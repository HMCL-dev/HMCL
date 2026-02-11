/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
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

import org.jackhuang.hmcl.util.FileSaver;
import org.jackhuang.hmcl.util.SelfDependencyPatcher;
import org.jackhuang.hmcl.util.SwingUtils;
import org.jackhuang.hmcl.java.JavaRuntime;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.io.JarUtils;
import org.jackhuang.hmcl.util.platform.OperatingSystem;

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CancellationException;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public final class EntryPoint {

    private EntryPoint() {
    }

    public static void main(String[] args) {
        System.getProperties().putIfAbsent("java.net.useSystemProxies", "true");
        System.getProperties().putIfAbsent("javafx.autoproxy.disable", "true");
        System.getProperties().putIfAbsent("http.agent", "HMCL/" + Metadata.VERSION);

        createHMCLDirectories();
        LOG.start(Metadata.HMCL_CURRENT_DIRECTORY.resolve("logs"));

        setupJavaFXVMOptions();

        if (OperatingSystem.CURRENT_OS == OperatingSystem.MACOS) {
            System.getProperties().putIfAbsent("apple.awt.application.appearance", "system");
            if (!isInsideMacAppBundle())
                initIcon();
        }

        checkJavaFX();
        verifyJavaFX();
        addEnableNativeAccess();
        enableUnsafeMemoryAccess();

        Launcher.main(args);
    }

    public static void exit(int exitCode) {
        FileSaver.shutdown();
        LOG.shutdown();
        System.exit(exitCode);
    }

    private static void setupJavaFXVMOptions() {
        if ("true".equalsIgnoreCase(System.getenv("HMCL_FORCE_GPU"))) {
            LOG.info("HMCL_FORCE_GPU: true");
            System.getProperties().putIfAbsent("prism.forceGPU", "true");
        }

        String animationFrameRate = System.getenv("HMCL_ANIMATION_FRAME_RATE");
        if (animationFrameRate != null) {
            LOG.info("HMCL_ANIMATION_FRAME_RATE: " + animationFrameRate);

            try {
                if (Integer.parseInt(animationFrameRate) <= 0)
                    throw new NumberFormatException(animationFrameRate);

                System.getProperties().putIfAbsent("javafx.animation.pulse", animationFrameRate);
            } catch (NumberFormatException e) {
                LOG.warning("Invalid animation frame rate: " + animationFrameRate);
            }
        }

        String uiScale = System.getProperty("hmcl.uiScale", System.getenv("HMCL_UI_SCALE"));
        if (uiScale != null) {
            uiScale = uiScale.trim();

            LOG.info("HMCL_UI_SCALE: " + uiScale);

            try {
                float scaleValue;
                if (uiScale.endsWith("%")) {
                    scaleValue = Integer.parseInt(uiScale.substring(0, uiScale.length() - 1)) / 100.0f;
                } else if (uiScale.endsWith("dpi") || uiScale.endsWith("DPI")) {
                    scaleValue = Integer.parseInt(uiScale.substring(0, uiScale.length() - 3)) / 96.0f;
                } else {
                    scaleValue = Float.parseFloat(uiScale);
                }

                float lowerBound;
                float upperBound;

                if (OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS) {
                    // JavaFX behavior may be abnormal when the DPI scaling factor is too high
                    lowerBound = 0.25f;
                    upperBound = 4f;
                } else {
                    lowerBound = 0.01f;
                    upperBound = 10f;
                }

                if (scaleValue >= lowerBound && scaleValue <= upperBound) {
                    if (OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS) {
                        System.getProperties().putIfAbsent("glass.win.uiScale", uiScale);
                    } else if (OperatingSystem.CURRENT_OS == OperatingSystem.MACOS) {
                        LOG.warning("macOS does not support setting UI scale, so it will be ignored");
                    } else {
                        System.getProperties().putIfAbsent("glass.gtk.uiScale", uiScale);
                    }
                } else {
                    LOG.warning("UI scale out of range: " + uiScale);
                }
            } catch (Throwable e) {
                LOG.warning("Invalid UI scale: " + uiScale);
            }
        }
    }

    private static void createHMCLDirectories() {
        if (!Files.isDirectory(Metadata.HMCL_CURRENT_DIRECTORY)) {
            try {
                Files.createDirectories(Metadata.HMCL_CURRENT_DIRECTORY);
                if (OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS) {
                    try {
                        Files.setAttribute(Metadata.HMCL_CURRENT_DIRECTORY, "dos:hidden", true);
                    } catch (IOException e) {
                        LOG.warning("Failed to set hidden attribute of " + Metadata.HMCL_CURRENT_DIRECTORY, e);
                    }
                }
            } catch (IOException e) {
                // Logger has not been started yet, so print directly to System.err
                System.err.println("Failed to create HMCL directory: " + Metadata.HMCL_CURRENT_DIRECTORY);
                e.printStackTrace(System.err);
                showErrorAndExit(i18n("fatal.create_hmcl_current_directory_failure", Metadata.HMCL_CURRENT_DIRECTORY));
            }
        }

        if (!Files.isDirectory(Metadata.HMCL_GLOBAL_DIRECTORY)) {
            try {
                Files.createDirectories(Metadata.HMCL_GLOBAL_DIRECTORY);
            } catch (IOException e) {
                LOG.warning("Failed to create HMCL global directory " + Metadata.HMCL_GLOBAL_DIRECTORY, e);
            }
        }
    }

    private static boolean isInsideMacAppBundle() {
        Path thisJar = JarUtils.thisJarPath();
        if (thisJar == null)
            return false;

        for (Path current = thisJar.getParent();
             current != null && current.getParent() != null;
             current = current.getParent()
        ) {
            if ("Contents".equals(FileUtils.getName(current))
                    && FileUtils.getName(current.getParent()).endsWith(".app")
                    && Files.exists(current.resolve("Info.plist"))
            ) {
                return true;
            }
        }
        return false;
    }

    private static void initIcon() {
        try {
            if (java.awt.Taskbar.isTaskbarSupported()) {
                var image = java.awt.Toolkit.getDefaultToolkit().getImage(EntryPoint.class.getResource("/assets/img/icon-mac.png"));
                java.awt.Taskbar.getTaskbar().setIconImage(image);
            }
        } catch (Throwable e) {
            LOG.warning("Failed to set application icon", e);
        }
    }

    private static void checkJavaFX() {
        try {
            SelfDependencyPatcher.patch();
        } catch (SelfDependencyPatcher.PatchException e) {
            LOG.error("Unable to patch JVM", e);
            showErrorAndExit(i18n("fatal.javafx.missing"));
        } catch (SelfDependencyPatcher.IncompatibleVersionException e) {
            LOG.error("Unable to patch JVM", e);
            showErrorAndExit(i18n("fatal.javafx.incompatible"));
        } catch (CancellationException e) {
            LOG.error("User cancels downloading JavaFX", e);
            exit(0);
        }
    }

    /**
     * Check if JavaFX exists but is incomplete
     */
    private static void verifyJavaFX() {
        try {
            Class.forName("javafx.beans.binding.Binding"); // javafx.base
            Class.forName("javafx.stage.Stage");           // javafx.graphics
            Class.forName("javafx.scene.control.Skin");    // javafx.controls
        } catch (Exception e) {
            LOG.warning("JavaFX is incomplete or not found", e);
            showErrorAndExit(i18n("fatal.javafx.incomplete"));
        }
    }

    private static void addEnableNativeAccess() {
        if (JavaRuntime.CURRENT_VERSION > 21) {
            try {
                // javafx.graphics
                Module module = Class.forName("javafx.stage.Stage").getModule();
                if (module.isNamed()) {
                    try {
                        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(Module.class, MethodHandles.lookup());
                        MethodHandle implAddEnableNativeAccess = lookup.findVirtual(Module.class,
                                "implAddEnableNativeAccess", MethodType.methodType(Module.class));
                        Module ignored = (Module) implAddEnableNativeAccess.invokeExact(module);
                    } catch (Throwable e) {
                        e.printStackTrace(System.err);
                    }
                }
            } catch (ClassNotFoundException e) {
                LOG.error("Failed to add enable native access for JavaFX", e);
                showErrorAndExit(i18n("fatal.javafx.incomplete"));
            }
        }
    }

    private static void enableUnsafeMemoryAccess() {
        // https://openjdk.org/jeps/498
        if (JavaRuntime.CURRENT_VERSION == 24 || JavaRuntime.CURRENT_VERSION == 25) {
            try {
                Class<?> clazz = Class.forName("sun.misc.Unsafe");
                boolean ignored = (boolean) MethodHandles.privateLookupIn(clazz, MethodHandles.lookup())
                        .findStatic(clazz, "trySetMemoryAccessWarned", MethodType.methodType(boolean.class))
                        .invokeExact();
            } catch (Throwable e) {
                LOG.warning("Failed to enable unsafe memory access", e);
            }
        }
    }

    /**
     * Indicates that a fatal error has occurred, and that the application cannot start.
     */
    private static void showErrorAndExit(String message) {
        SwingUtils.showErrorDialog(message);
        exit(1);
    }
}
