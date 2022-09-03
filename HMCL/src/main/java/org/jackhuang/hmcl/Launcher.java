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
import javafx.stage.Stage;
import org.jackhuang.hmcl.setting.ConfigHolder;
import org.jackhuang.hmcl.setting.SambaException;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.AsyncTaskExecutor;
import org.jackhuang.hmcl.ui.AwtUtils;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.upgrade.UpdateChecker;
import org.jackhuang.hmcl.upgrade.UpdateHandler;
import org.jackhuang.hmcl.util.CrashReporter;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.platform.Architecture;
import org.jackhuang.hmcl.util.platform.OperatingSystem;

import java.awt.*;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.*;
import java.nio.file.Paths;
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

        try {
            try {
                ConfigHolder.init();
            } catch (SambaException ignored) {
                Main.showWarningAndContinue(i18n("fatal.samba"));
            } catch (IOException e) {
                LOG.log(Level.SEVERE, "Failed to load config", e);
                Main.showErrorAndExit(i18n("fatal.config_loading_failure", Paths.get("").toAbsolutePath().normalize()));
            }

            if (Metadata.HMCL_DIRECTORY.toAbsolutePath().toString().indexOf('=') >= 0) {
                Main.showWarningAndContinue(i18n("fatal.illegal_char"));
            }

            // runLater to ensure ConfigHolder.init() finished initialization
            Platform.runLater(() -> {
                // When launcher visibility is set to "hide and reopen" without Platform.implicitExit = false,
                // Stage.show() cannot work again because JavaFX Toolkit have already shut down.
                Platform.setImplicitExit(false);
                Controllers.initialize(primaryStage);

                initIcon();

                UpdateChecker.init();

                primaryStage.show();
            });
        } catch (Throwable e) {
            CRASH_REPORTER.uncaughtException(Thread.currentThread(), e);
        }
    }

    @Override
    public void stop() throws Exception {
        super.stop();
        Controllers.onApplicationStop();
    }

    private void initIcon() {
        Toolkit toolkit = Toolkit.getDefaultToolkit();
        Image image = toolkit.getImage(Launcher.class.getResource("/assets/img/icon.png"));
        AwtUtils.setAppleIcon(image);
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
            LOG.info("Current Directory: " + Paths.get("").toAbsolutePath());
            LOG.info("HMCL Directory: " + Metadata.HMCL_DIRECTORY);
            LOG.info("Memory: " + Runtime.getRuntime().maxMemory() / 1024 / 1024 + "MB");
            ManagementFactory.getMemoryPoolMXBeans().stream().filter(bean -> bean.getName().equals("Metaspace")).findAny()
                    .ifPresent(bean -> LOG.info("Metaspace: " + bean.getUsage().getUsed() / 1024 / 1024 + "MB"));

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
