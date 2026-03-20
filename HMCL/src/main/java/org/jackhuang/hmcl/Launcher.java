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

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.value.ObservableBooleanValue;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import javafx.scene.input.Clipboard;
import javafx.scene.input.DataFormat;
import javafx.stage.Screen;
import javafx.stage.Stage;
import org.jackhuang.hmcl.setting.ConfigHolder;
import org.jackhuang.hmcl.setting.SambaException;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.util.FileSaver;
import org.jackhuang.hmcl.task.AsyncTaskExecutor;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.upgrade.UpdateChecker;
import org.jackhuang.hmcl.upgrade.UpdateHandler;
import org.jackhuang.hmcl.util.CrashReporter;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.io.JarUtils;
import org.jackhuang.hmcl.util.platform.Architecture;
import org.jackhuang.hmcl.util.platform.CommandBuilder;
import org.jackhuang.hmcl.util.platform.NativeUtils;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jackhuang.hmcl.util.platform.SystemInfo;

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
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.jackhuang.hmcl.ui.FXUtils.runInFX;
import static org.jackhuang.hmcl.util.DataSizeUnit.MEGABYTES;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

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
            try {
                ConfigHolder.init();
            } catch (SambaException e) {
                showAlert(AlertType.WARNING, i18n("fatal.samba"));
            } catch (IOException e) {
                LOG.error("Failed to load config", e);
                checkConfigInTempDir();
                checkConfigOwner();
                showAlert(AlertType.ERROR, i18n("fatal.config_loading_failure", ConfigHolder.configLocation().getParent()));
                EntryPoint.exit(1);
            }

            // https://lapcatsoftware.com/articles/app-translocation.html
            if (OperatingSystem.CURRENT_OS == OperatingSystem.MACOS
                    && ConfigHolder.isNewlyCreated()
                    && System.getProperty("user.dir").startsWith("/private/var/folders/")) {
                if (showAlert(AlertType.WARNING, i18n("fatal.mac_app_translocation"), ButtonType.YES, ButtonType.NO) == ButtonType.NO)
                    return;
            } else {
                checkConfigInTempDir();
            }

            if (ConfigHolder.isOwnerChanged()) {
                if (showAlert(AlertType.WARNING, i18n("fatal.config_change_owner_root"), ButtonType.YES, ButtonType.NO) == ButtonType.NO)
                    return;
            }

            if (ConfigHolder.isUnsupportedVersion()) {
                showAlert(AlertType.WARNING, i18n("fatal.config_unsupported_version"));
            }

            if (Metadata.HMCL_CURRENT_DIRECTORY.toString().indexOf('=') >= 0) {
                showAlert(AlertType.WARNING, i18n("fatal.illegal_char"));
            }

            // runLater to ensure ConfigHolder.init() finished initialization
            Platform.runLater(() -> {
                // When launcher visibility is set to "hide and reopen" without Platform.implicitExit = false,
                // Stage.show() cannot work again because JavaFX Toolkit have already shut down.
                Platform.setImplicitExit(false);
                Controllers.initialize(primaryStage);

                UpdateChecker.init();

                primaryStage.show();
            });
        } catch (Throwable e) {
            CRASH_REPORTER.uncaughtException(Thread.currentThread(), e);
        }
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

    private static boolean isConfigInTempDir() {
        String configPath = ConfigHolder.configLocation().toString();

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
        if (ConfigHolder.isNewlyCreated() && isConfigInTempDir()
                && showAlert(AlertType.WARNING, i18n("fatal.config_in_temp_dir"), ButtonType.YES, ButtonType.NO) == ButtonType.NO) {
            EntryPoint.exit(0);
        }
    }

    private static void checkConfigOwner() {
        if (OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS)
            return;

        String userName = System.getProperty("user.name");
        String owner;
        try {
            owner = Files.getOwner(ConfigHolder.configLocation()).getName();
        } catch (IOException ioe) {
            LOG.warning("Failed to get file owner", ioe);
            return;
        }

        if (Files.isWritable(ConfigHolder.configLocation()) || userName.equals("root") || userName.equals(owner))
            return;

        ArrayList<String> files = new ArrayList<>();
        files.add(ConfigHolder.configLocation().toString());
        if (Files.exists(Metadata.HMCL_GLOBAL_DIRECTORY))
            files.add(Metadata.HMCL_GLOBAL_DIRECTORY.toString());
        if (Files.exists(Metadata.HMCL_CURRENT_DIRECTORY))
            files.add(Metadata.HMCL_CURRENT_DIRECTORY.toString());

        Path mcDir = Paths.get(".minecraft").toAbsolutePath().normalize();
        if (Files.exists(mcDir))
            files.add(mcDir.toString());

        String command = new CommandBuilder().addAll("sudo", "chown", "-R", userName).addAll(files).toString();
        ButtonType copyAndExit = new ButtonType(i18n("button.copy_and_exit"));

        if (showAlert(AlertType.ERROR,
                i18n("fatal.config_loading_failure.unix", owner, command),
                copyAndExit, ButtonType.CLOSE) == copyAndExit) {
            Clipboard.getSystemClipboard()
                    .setContent(Collections.singletonMap(DataFormat.PLAIN_TEXT, command));
        }
        EntryPoint.exit(1);
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
            LOG.info("HMCL Global Directory: " + Metadata.HMCL_GLOBAL_DIRECTORY);
            LOG.info("HMCL Current Directory: " + Metadata.HMCL_CURRENT_DIRECTORY);
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

            Lang.thread(SystemInfo::initialize, "Detection System Information", true);

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

    public static final CrashReporter CRASH_REPORTER = new CrashReporter(true);
}
