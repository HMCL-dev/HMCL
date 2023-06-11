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
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.input.Clipboard;
import javafx.scene.input.DataFormat;
import javafx.stage.Stage;
import org.jackhuang.hmcl.setting.ConfigHolder;
import org.jackhuang.hmcl.setting.SambaException;
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
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import net.burningtnt.webp.jfx.WEBPImageLoaderFactory;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import static org.jackhuang.hmcl.ui.FXUtils.runInFX;
import static org.jackhuang.hmcl.util.Logging.LOG;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public final class Launcher extends Application {
    public static final CookieManager COOKIE_MANAGER = new CookieManager();

    @Override
    public void start(Stage primaryStage) {
        Thread.currentThread().setUncaughtExceptionHandler(CRASH_REPORTER);

        CookieHandler.setDefault(COOKIE_MANAGER);

        WEBPImageLoaderFactory.setupListener();

        LOG.info("JavaFX Version: " + System.getProperty("javafx.runtime.version"));
        try {
            Object pipeline = Class.forName("com.sun.prism.GraphicsPipeline").getMethod("getPipeline").invoke(null);
            LOG.info("Prism pipeline: " + (pipeline == null ? "null" : pipeline.getClass().getName()));
        } catch (Throwable e) {
            LOG.log(Level.WARNING, "Failed to get prism pipeline", e);
        }

        try {
            try {
                ConfigHolder.init();
            } catch (SambaException ignored) {
                Main.showWarningAndContinue(i18n("fatal.samba"));
            } catch (IOException e) {
                LOG.log(Level.SEVERE, "Failed to load config", e);
                checkConfigInTempDir();
                checkConfigOwner();
                Main.showErrorAndExit(i18n("fatal.config_loading_failure", ConfigHolder.configLocation().getParent()));
            }

            checkConfigInTempDir();
            if (ConfigHolder.isOwnerChanged()) {
                ButtonType res = new Alert(Alert.AlertType.WARNING, i18n("fatal.config_change_owner_root"), ButtonType.YES, ButtonType.NO)
                        .showAndWait()
                        .orElse(null);
                if (res == ButtonType.NO)
                    return;
            }

            if (Metadata.HMCL_DIRECTORY.toString().indexOf('=') >= 0) {
                Main.showWarningAndContinue(i18n("fatal.illegal_char"));
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
        } else if (OperatingSystem.CURRENT_OS == OperatingSystem.LINUX) {
            return configPath.startsWith("/tmp/")
                    || configPath.startsWith("/var/tmp/")
                    || configPath.startsWith("/var/cache/")
                    || configPath.startsWith("/dev/shm/")
                    || configPath.contains("/Trash/");
        } else if (OperatingSystem.CURRENT_OS == OperatingSystem.OSX) {
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
        if (ConfigHolder.isNewlyCreated() && isConfigInTempDir()) {
            ButtonType res = new Alert(Alert.AlertType.WARNING, i18n("fatal.config_in_temp_dir"), ButtonType.YES, ButtonType.NO)
                    .showAndWait()
                    .orElse(null);
            if (res == ButtonType.NO) {
                System.exit(0);
            }
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
            LOG.log(Level.WARNING, "Failed to get file owner", ioe);
            return;
        }

        if (Files.isWritable(ConfigHolder.configLocation()) || userName.equals("root") || userName.equals(owner))
            return;

        ArrayList<String> files = new ArrayList<>();
        files.add(ConfigHolder.configLocation().toString());
        if (Files.exists(Metadata.HMCL_DIRECTORY))
            files.add(Metadata.HMCL_DIRECTORY.toString());

        Path mcDir = Paths.get(".minecraft").toAbsolutePath().normalize();
        if (Files.exists(mcDir))
            files.add(mcDir.toString());

        String command = new CommandBuilder().add("sudo", "chown", "-R", userName).addAll(files).toString();
        ButtonType copyAndExit = new ButtonType(i18n("button.copy_and_exit"));

        ButtonType res = new Alert(Alert.AlertType.ERROR, i18n("fatal.config_loading_failure.unix", owner, command), copyAndExit, ButtonType.CLOSE)
                .showAndWait()
                .orElse(null);
        if (res == copyAndExit) {
            Clipboard.getSystemClipboard()
                    .setContent(Collections.singletonMap(DataFormat.PLAIN_TEXT, command));
        }
        System.exit(1);
    }

    @Override
    public void stop() throws Exception {
        super.stop();
        Controllers.onApplicationStop();
    }

    public static void main(String[] args) {
        if (UpdateHandler.processArguments(args)) {
            return;
        }

        Thread.setDefaultUncaughtExceptionHandler(CRASH_REPORTER);
        AsyncTaskExecutor.setUncaughtExceptionHandler(new CrashReporter(false));

        try {
            LOG.info("*** " + Metadata.TITLE + " ***");
            LOG.info("Operating System: " + OperatingSystem.SYSTEM_NAME + ' ' + OperatingSystem.SYSTEM_VERSION);
            LOG.info("System Architecture: " + Architecture.SYSTEM_ARCH_NAME);
            LOG.info("Java Architecture: " + Architecture.CURRENT_ARCH_NAME);
            LOG.info("Java Version: " + System.getProperty("java.version") + ", " + System.getProperty("java.vendor"));
            LOG.info("Java VM Version: " + System.getProperty("java.vm.name") + " (" + System.getProperty("java.vm.info") + "), " + System.getProperty("java.vm.vendor"));
            LOG.info("Java Home: " + System.getProperty("java.home"));
            LOG.info("Current Directory: " + System.getProperty("user.dir"));
            LOG.info("HMCL Directory: " + Metadata.HMCL_DIRECTORY);
            LOG.info("HMCL Jar Path: " + JarUtils.thisJar().map(it -> it.toAbsolutePath().toString()).orElse("Not Found"));
            LOG.info("Memory: " + Runtime.getRuntime().maxMemory() / 1024 / 1024 + "MB");
            LOG.info("Metaspace: " + ManagementFactory.getMemoryPoolMXBeans().stream()
                    .filter(bean -> bean.getName().equals("Metaspace"))
                    .findAny()
                    .map(bean -> bean.getUsage().getUsed() / 1024 / 1024 + "MB")
                    .orElse("Unknown"));
            if (OperatingSystem.CURRENT_OS == OperatingSystem.LINUX)
                LOG.info("XDG Session Type: " + System.getenv("XDG_SESSION_TYPE"));

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
            Lang.executeDelayed(OperatingSystem::forceGC, TimeUnit.SECONDS, 5, true);
        });
    }

    public static final CrashReporter CRASH_REPORTER = new CrashReporter(true);
}
