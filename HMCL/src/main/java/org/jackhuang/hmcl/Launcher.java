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

import com.sun.jna.Pointer;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.value.ObservableBooleanValue;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.jackhuang.hmcl.game.HMCLCacheRepository;
import org.jackhuang.hmcl.setting.*;
import org.jackhuang.hmcl.task.AsyncTaskExecutor;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.theme.Themes;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.animation.AnimationUtils;
import org.jackhuang.hmcl.upgrade.UpdateChecker;
import org.jackhuang.hmcl.upgrade.UpdateHandler;
import org.jackhuang.hmcl.util.*;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.io.JarUtils;
import org.jackhuang.hmcl.util.platform.*;
import org.jackhuang.hmcl.util.platform.windows.Gdi32;
import org.jackhuang.hmcl.util.platform.windows.User32;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.jackhuang.hmcl.setting.SettingsManager.settings;
import static org.jackhuang.hmcl.ui.FXUtils.runInFX;
import static org.jackhuang.hmcl.util.DataSizeUnit.MEGABYTES;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class Launcher extends Application {
    public static final CookieManager COOKIE_MANAGER = new CookieManager();

    @Override
    public void start(Stage primaryStage) {
        Thread.currentThread().setUncaughtExceptionHandler(CRASH_REPORTER);

        CookieHandler.setDefault(COOKIE_MANAGER);

        LOG.info("JavaFX Version: " + System.getProperty("javafx.runtime.version"));
        LOG.info("Prism Pipeline: " + FXUtils.GRAPHICS_PIPELINE);
        LOG.info("Dark Mode: " + Optional.ofNullable(FXUtils.DARK_MODE).map(ObservableBooleanValue::get).orElse(false));
        LOG.info("Reduced Motion: " + Objects.requireNonNullElse(FXUtils.REDUCED_MOTION, false));

        if (Screen.getScreens().isEmpty()) {
            LOG.info("No screen");
        } else {
            StringBuilder builder = new StringBuilder("Screens:");
            int count = 0;
            for (Screen screen : Screen.getScreens()) {
                builder.append("\n - Screen ").append(++count).append(": ");
                appendScreen(builder, screen);
            }
            LOG.info(builder.toString());
        }

        try {
            initializeSettingsRuntime();

            // https://lapcatsoftware.com/articles/app-translocation.html
            if (OperatingSystem.CURRENT_OS == OperatingSystem.MACOS
                    && SettingsManager.isNewlyCreated()
                    && System.getProperty("user.dir").startsWith("/private/var/folders/")) {
                if (!confirmWithCountdown(AlertType.WARNING, i18n("fatal.mac_app_translocation"), 5))
                    return;
            } else {
                checkConfigInTempDir();
            }

            if (SettingsManager.isOwnerChanged()) {
                if (showAlert(AlertType.WARNING, i18n("fatal.config_change_owner_root"), ButtonType.YES, ButtonType.NO) == ButtonType.NO)
                    return;
            }

            if (SettingsManager.hasReadOnlyCoreSettings()) {
                showAlert(AlertType.WARNING, i18n("fatal.config_unsupported_version"));
            }

            if (Metadata.HMCL_LOCAL_HOME.toString().indexOf('=') >= 0) {
                showAlert(AlertType.WARNING, i18n("fatal.illegal_char"));
            }

            // runLater to ensure SettingsManager.init() finished initialization
            Platform.runLater(() -> {
                // When launcher visibility is set to "hide and reopen" without Platform.implicitExit = false,
                // Stage.show() cannot work again because JavaFX Toolkit have already shut down.
                Platform.setImplicitExit(false);
                Controllers.initialize(primaryStage);

                if (OperatingSystem.CURRENT_OS == OperatingSystem.MACOS)
                    Themes.applyNativeDarkMode(primaryStage);

                UpdateChecker.init();

                primaryStage.show();
            });
        } catch (Throwable e) {
            CRASH_REPORTER.uncaughtException(Thread.currentThread(), e);
        }
    }

    /// Initializes modules and runtime services that depend on loaded settings.
    private static void initializeSettingsRuntime() {
        DownloadProviders.init();
        ProxyManager.init();
        Accounts.init();
        GameDirectoryManager.init();
        AuthlibInjectorServers.init();
        AnimationUtils.init();

        CacheRepository.setInstance(HMCLCacheRepository.REPOSITORY);
        HMCLCacheRepository.REPOSITORY.directoryProperty().bind(Bindings.createStringBinding(() -> {
            String commonDirectory = settings().getResolvedCommonDirectory();
            if (commonDirectory != null && FileUtils.canCreateDirectory(commonDirectory)) {
                return commonDirectory;
            } else {
                return LauncherSettings.getDefaultCommonDirectory();
            }
        }, settings().commonDirectoryProperty(), settings().commonDirectoryTypeProperty()));
    }

    private static void appendScreen(StringBuilder builder, Screen screen) {
        Rectangle2D bounds = screen.getBounds();
        double scale = screen.getOutputScaleX();

        builder.append(Math.round(bounds.getWidth() * scale));
        builder.append('x');
        builder.append(Math.round(bounds.getHeight() * scale));

        DecimalFormat decimalFormat = new DecimalFormat("#.##");

        if (scale != 1.0) {
            builder.append(" @ ");
            builder.append(decimalFormat.format(scale));
            builder.append('x');
        }

        double dpi = screen.getDpi();
        builder.append(' ');
        builder.append(decimalFormat.format(dpi));
        builder.append("dpi");

        builder.append(" in ")
                .append(Math.round(Math.sqrt(bounds.getWidth() * bounds.getWidth() + bounds.getHeight() * bounds.getHeight()) / dpi))
                .append('"');

        builder.append(" (").append(decimalFormat.format(bounds.getMinX()))
                .append(", ").append(decimalFormat.format(bounds.getMinY()))
                .append(", ").append(decimalFormat.format(bounds.getMaxX()))
                .append(", ").append(decimalFormat.format(bounds.getMaxY()))
                .append(")");
    }

    private static ButtonType showAlert(AlertType alertType, String contentText, ButtonType... buttons) {
        return new Alert(alertType, contentText, buttons).showAndWait().orElse(null);
    }

    private static boolean confirmWithCountdown(Alert.AlertType alertType, String contentText, int seconds) {
        Alert alert = new Alert(alertType, contentText, ButtonType.YES, ButtonType.NO);
        Button okButton = (Button) alert.getDialogPane().lookupButton(ButtonType.YES);

        okButton.setDisable(true);

        KeyFrame[] keyFrames = new KeyFrame[seconds + 1];
        for (int i = 0; i < seconds; i++) {
            keyFrames[i] = new KeyFrame(Duration.seconds(i),
                    new KeyValue(okButton.textProperty(), i18n("button.ok.countdown", seconds - i)));
        }
        keyFrames[seconds] = new KeyFrame(Duration.seconds(seconds),
                new KeyValue(okButton.textProperty(), i18n("button.ok")),
                new KeyValue(okButton.disableProperty(), false));

        Timeline timeline = new Timeline(keyFrames);
        alert.setOnShown(e -> timeline.play());
        alert.setOnCloseRequest(e -> timeline.stop());
        return alert.showAndWait().orElse(null) == ButtonType.YES;
    }

    private static boolean isConfigInTempDir() {
        String configPath = SettingsManager.localConfigDirectory().toString();

        String tmpdir = System.getProperty("java.io.tmpdir");
        if (StringUtils.isNotBlank(tmpdir) && configPath.startsWith(tmpdir))
            return true;

        String[] tempFolderNames = {"Temp", "Cache", "Caches"};
        for (String name : tempFolderNames) {
            if (configPath.contains(File.separator + name + File.separator))
                return true;
        }

        if (OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS) {
            return configPath.contains("\\Temporary Internet Files\\")
                    || configPath.contains("\\INetCache\\")
                    || configPath.contains("\\$Recycle.Bin\\")
                    || configPath.contains("\\recycler\\");
        } else if (OperatingSystem.CURRENT_OS.isLinuxOrBSD()) {
            return configPath.startsWith("/tmp/")
                    || configPath.startsWith("/var/tmp/")
                    || configPath.startsWith("/var/cache/")
                    || configPath.startsWith("/dev/shm/")
                    || configPath.contains("/Trash/");
        } else if (OperatingSystem.CURRENT_OS == OperatingSystem.MACOS) {
            return configPath.startsWith("/var/folders/")
                    || configPath.startsWith("/private/var/folders/")
                    || configPath.startsWith("/tmp/")
                    || configPath.startsWith("/private/tmp/")
                    || configPath.startsWith("/var/tmp/")
                    || configPath.startsWith("/private/var/tmp/")
                    || configPath.contains("/.Trash/");
        } else {
            return false;
        }
    }

    private static void checkConfigInTempDir() {
        if (SettingsManager.isNewlyCreated() && isConfigInTempDir()
                && !confirmWithCountdown(AlertType.WARNING, i18n("fatal.config_in_temp_dir"), 5)) {
            EntryPoint.exit(0);
        }
    }

    @Override
    public void stop() throws Exception {
        Controllers.onApplicationStop();
        FileSaver.shutdown();
        LOG.shutdown();
    }

    public static void main(String[] args) {
        if (UpdateHandler.processArguments(args)) {
            LOG.shutdown();
            return;
        }

        Thread.setDefaultUncaughtExceptionHandler(CRASH_REPORTER);
        AsyncTaskExecutor.setUncaughtExceptionHandler(new CrashReporter(false));

        try {
            LOG.info("*** " + Metadata.TITLE + " ***");
            LOG.info("Operating System: " + (OperatingSystem.OS_RELEASE_PRETTY_NAME == null
                    ? OperatingSystem.SYSTEM_NAME + ' ' + OperatingSystem.SYSTEM_VERSION.getVersion()
                    : OperatingSystem.OS_RELEASE_PRETTY_NAME + " (" + OperatingSystem.SYSTEM_NAME + ' ' + OperatingSystem.SYSTEM_VERSION.getVersion() + ')'));
            if (OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS) {
                LOG.info("Processor Identifier: " + System.getenv("PROCESSOR_IDENTIFIER"));
            }
            LOG.info("System Architecture: " + Architecture.SYSTEM_ARCH.getDisplayName());
            LOG.info("Native Encoding: " + OperatingSystem.NATIVE_CHARSET);
            LOG.info("JNU Encoding: " + System.getProperty("sun.jnu.encoding"));
            if (OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS) {
                LOG.info("Code Page: " + OperatingSystem.CODE_PAGE);
            }
            LOG.info("Java Architecture: " + Architecture.CURRENT_ARCH.getDisplayName());
            LOG.info("Java Version: " + System.getProperty("java.version") + ", " + System.getProperty("java.vendor"));
            LOG.info("Java VM Version: " + System.getProperty("java.vm.name") + " (" + System.getProperty("java.vm.info") + "), " + System.getProperty("java.vm.vendor"));
            LOG.info("Java Home: " + System.getProperty("java.home"));
            LOG.info("Current Directory: " + Metadata.CURRENT_DIRECTORY);
            LOG.info("HMCL User Home: " + Metadata.HMCL_USER_HOME);
            LOG.info("HMCL Local Home: " + Metadata.HMCL_LOCAL_HOME);
            LOG.info("HMCL Jar Path: " + Lang.requireNonNullElse(JarUtils.thisJarPath(), "Not Found"));
            LOG.info("HMCL Log File: " + Lang.requireNonNullElse(LOG.getLogFile(), "In Memory"));
            LOG.info("JVM Max Memory: " + MEGABYTES.formatBytes(Runtime.getRuntime().maxMemory()));
            try {
                for (MemoryPoolMXBean bean : ManagementFactory.getMemoryPoolMXBeans()) {
                    if ("Metaspace".equals(bean.getName())) {
                        long bytes = bean.getUsage().getUsed();
                        LOG.info("Metaspace: " + MEGABYTES.formatBytes(bytes));
                        break;
                    }
                }
            } catch (NoClassDefFoundError ignored) {
            }
            LOG.info("Native Backend: " + (NativeUtils.USE_JNA ? "JNA" : "None"));
            if (OperatingSystem.CURRENT_OS.isLinuxOrBSD()) {
                LOG.info("XDG Session Type: " + System.getenv("XDG_SESSION_TYPE"));
                LOG.info("XDG Current Desktop: " + System.getenv("XDG_CURRENT_DESKTOP"));
            }

            LOG.info("Zlib Compatible: " + ZlibUtils.IS_ZLIB_COMPATIBLE);

            Lang.thread(SystemInfo::initialize, "Detection System Information", true);

            try {
                SettingsManager.init();
            } catch (SambaException e) {
                EntryPoint.showWarning(i18n("fatal.samba"));
            } catch (IOException e) {
                LOG.error("Failed to load config", e);
                checkConfigOwner();
                SwingUtils.showErrorDialog(i18n("fatal.config_loading_failure", SettingsManager.localConfigDirectory()));
                EntryPoint.exit(1);
            }

            setupJavaFXVMOptions();

            launch(Launcher.class, args);
        } catch (Throwable e) { // Fucking JavaFX will suppress the exception and will break our crash reporter.
            CRASH_REPORTER.uncaughtException(Thread.currentThread(), e);
        }
    }

    public static void stopApplication() {
        LOG.info("Stopping application.\n" + StringUtils.getStackTrace(Thread.currentThread().getStackTrace()));

        runInFX(() -> {
            if (Controllers.getStage() == null)
                return;
            Controllers.getStage().close();
            Schedulers.shutdown();
            Controllers.shutdown();
            Platform.exit();
        });
    }

    public static void stopWithoutPlatform() {
        LOG.info("Stopping application without JavaFX Toolkit.\n" + StringUtils.getStackTrace(Thread.currentThread().getStackTrace()));

        runInFX(() -> {
            if (Controllers.getStage() == null)
                return;
            Controllers.getStage().close();
            Schedulers.shutdown();
            Controllers.shutdown();
            Lang.executeDelayed(System::gc, TimeUnit.SECONDS, 5, true);
        });
    }

    private static void setupJavaFXVMOptions() {
        if ("true".equalsIgnoreCase(System.getenv("HMCL_FORCE_GPU"))) {
            LOG.info("HMCL_FORCE_GPU: true");
            System.getProperties().putIfAbsent("prism.forceGPU", "true");
        }

        setUpAnimationFrameRate:
        {
            if (System.getProperty("javafx.animation.pulse") != null) {
                break setUpAnimationFrameRate;
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
                break setUpAnimationFrameRate;
            }

            // To avoid prematurely loading FXUtils, we only check if animationDisabled has been explicitly set to true
            if (!Boolean.TRUE.equals(settings().animationDisabledProperty().get())) {
                if (OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS) {
                    if (NativeUtils.USE_JNA && Gdi32.INSTANCE != null && User32.INSTANCE != null) {
                        Pointer pointer = User32.INSTANCE.GetDC(Pointer.NULL);
                        if (pointer != null && !Pointer.NULL.equals(pointer)) {
                            try {
                                int refreshRate = Gdi32.INSTANCE.GetDeviceCaps(pointer, Gdi32.VREFRESH);

                                if (refreshRate > 0) {
                                    LOG.info("Detected refresh rate: " + refreshRate + "Hz");

                                    if (refreshRate >= 90) {
                                        System.getProperties().putIfAbsent("javafx.animation.pulse", String.valueOf(refreshRate));
                                    }
                                }
                            } finally {
                                User32.INSTANCE.ReleaseDC(Pointer.NULL, pointer);
                            }
                        }
                    }
                }
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

    private static void checkConfigOwner() {
        if (OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS)
            return;

        String userName = System.getProperty("user.name");
        Path configDirectory = SettingsManager.localConfigDirectory();
        if (!Files.exists(configDirectory)) {
            return;
        }

        String owner;
        try {
            owner = Files.getOwner(configDirectory).getName();
        } catch (IOException ioe) {
            LOG.warning("Failed to get file owner", ioe);
            return;
        }

        if (Files.isWritable(configDirectory) || userName.equals("root") || userName.equals(owner))
            return;

        ArrayList<String> files = new ArrayList<>();
        files.add(configDirectory.toString());
        if (Files.exists(Metadata.HMCL_USER_HOME))
            files.add(Metadata.HMCL_USER_HOME.toString());

        Path mcDir = Paths.get(".minecraft").toAbsolutePath().normalize();
        if (Files.exists(mcDir))
            files.add(mcDir.toString());

        String command = new CommandBuilder().addAll("sudo", "chown", "-R", userName).addAll(files).toString();
        SwingUtils.initLookAndFeel();

        Object[] options = {i18n("button.copy_and_exit"), i18n("button.cancel")};
        int result = JOptionPane.showOptionDialog(null,
                i18n("fatal.config_loading_failure.unix", owner, command),
                i18n("message.error"),
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.ERROR_MESSAGE,
                null,
                options,
                options[0]);

        if (result == 0) {
            try {
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(command), null);
            } catch (Throwable e) {
                LOG.warning("Failed to copy command to clipboard", e);
            }
        }
        EntryPoint.exit(1);
    }

    public static final CrashReporter CRASH_REPORTER = new CrashReporter(true);
}
