/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2018  huangyuhui <huanghongxun2008@126.com>
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
 * along with this program.  If not, see {http://www.gnu.org/licenses/}.
 */
package org.jackhuang.hmcl;

import com.jfoenix.concurrency.JFXUtilities;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import org.jackhuang.hmcl.setting.Settings;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.upgrade.AppDataUpgrader;
import org.jackhuang.hmcl.upgrade.IUpgrader;
import org.jackhuang.hmcl.upgrade.UpdateChecker;
import org.jackhuang.hmcl.util.*;

import java.io.File;
import java.util.Arrays;
import java.util.ResourceBundle;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public final class Main extends Application {

    @Override
    public void start(Stage primaryStage) {
        Thread.currentThread().setUncaughtExceptionHandler(CRASH_REPORTER);

        try {
            // When launcher visibility is set to "hide and reopen" without Platform.implicitExit = false,
            // Stage.show() cannot work again because JavaFX Toolkit have already shut down.
            Platform.setImplicitExit(false);
            Controllers.initialize(primaryStage);
            primaryStage.setResizable(false);
            primaryStage.setScene(Controllers.getScene());
            primaryStage.show();
        } catch (Throwable e) {
            CRASH_REPORTER.uncaughtException(Thread.currentThread(), e);
        }
    }

    public static void main(String[] args) {
        Thread.setDefaultUncaughtExceptionHandler(CRASH_REPORTER);

        try {
            // NetworkUtils.setUserAgentSupplier(() -> "Hello Minecraft! Launcher");
            Constants.UI_THREAD_SCHEDULER = Constants.JAVAFX_UI_THREAD_SCHEDULER;
            UPGRADER.parseArguments(VersionNumber.asVersion(VERSION), Arrays.asList(args));

            Logging.LOG.info("*** " + TITLE + " ***");

            UPDATE_CHECKER.process(false)
                    .then(Task.of(Schedulers.javafx(), () -> {
                        if (UPDATE_CHECKER.isOutOfDate())
                            Controllers.showUpdate();
                    }))
                    .start();

            launch(args);
        } catch (Throwable e) { // Fucking JavaFX will suppress the exception and will break our crash reporter.
            CRASH_REPORTER.uncaughtException(Thread.currentThread(), e);
        }
    }

    public static void stopApplication() {
        JFXUtilities.runInFX(() -> {
            stopWithoutPlatform();
            Platform.exit();
        });
    }

    public static void stopWithoutPlatform() {
        JFXUtilities.runInFX(() -> {
            if (Controllers.getStage() == null)
                return;
            Controllers.getStage().close();

            Logging.LOG.info("Shutting down executor services.");
            Schedulers.shutdown();

            Controllers.shutdown();

            Lang.executeDelayed(OperatingSystem::forceGC, TimeUnit.SECONDS, 5, true);
        });
    }

    public static String i18n(String key) {
        try {
            return RESOURCE_BUNDLE.getString(key);
        } catch (Exception e) {
            Logging.LOG.log(Level.SEVERE, "Cannot find key " + key + " in resource bundle", e);
            return key;
        }
    }

    public static String i18n(String key, Object... formatArgs) {
        return String.format(i18n(key), formatArgs);
    }

    public static File getWorkingDirectory(String folder) {
        String home = System.getProperty("user.home", ".");
        switch (OperatingSystem.CURRENT_OS) {
            case LINUX:
                return new File(home, "." + folder + "/");
            case WINDOWS:
                String appdata = System.getenv("APPDATA");
                return new File(Lang.nonNull(appdata, home), "." + folder + "/");
            case OSX:
                return new File(home, "Library/Application Support/" + folder);
            default:
                return new File(home, folder + "/");
        }
    }

    public static final File MINECRAFT_DIRECTORY = getWorkingDirectory("minecraft");
    public static final File HMCL_DIRECTORY = getWorkingDirectory("hmcl");

    public static final String VERSION = "@HELLO_MINECRAFT_LAUNCHER_VERSION_FOR_GRADLE_REPLACING@";
    public static final String NAME = "HMCL";
    public static final String TITLE = NAME + " " + VERSION;
    public static final ResourceBundle RESOURCE_BUNDLE = Settings.INSTANCE.getLocale().getResourceBundle();
    public static final UpdateChecker UPDATE_CHECKER = new UpdateChecker(VersionNumber.asVersion(VERSION));
    public static final IUpgrader UPGRADER = new AppDataUpgrader();
    public static final CrashReporter CRASH_REPORTER = new CrashReporter();

    public static final String CONTACT = "http://huangyuhui.duapp.com/hmcl.php";
    public static final String PUBLISH = "http://www.mcbbs.net/thread-142335-1-1.html";
}
