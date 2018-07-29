/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2017  huangyuhui <huanghongxun2008@126.com>
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

import static org.jackhuang.hmcl.util.Logging.LOG;

import com.jfoenix.concurrency.JFXUtilities;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;

import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.upgrade.AppDataUpgrader;
import org.jackhuang.hmcl.upgrade.IUpgrader;
import org.jackhuang.hmcl.upgrade.UpdateChecker;
import org.jackhuang.hmcl.util.*;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class Launcher extends Application {

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

            UPDATE_CHECKER.process(false)
                    .then(Task.of(Schedulers.javafx(), () -> {
                        if (UPDATE_CHECKER.isOutOfDate())
                            Controllers.showUpdate();
                    }))
                    .start();

            primaryStage.show();
        } catch (Throwable e) {
            CRASH_REPORTER.uncaughtException(Thread.currentThread(), e);
        }
    }

    public static void main(String[] args) {
        Thread.setDefaultUncaughtExceptionHandler(CRASH_REPORTER);

        if (!FileUtils.makeDirectory(LOG_DIRECTORY))
            System.out.println("Unable to create log directory " + LOG_DIRECTORY + ", log files cannot be generated.");

        try {
            Logging.start(LOG_DIRECTORY);

            // NetworkUtils.setUserAgentSupplier(() -> "Hello Minecraft! Launcher");
            Constants.UI_THREAD_SCHEDULER = Constants.JAVAFX_UI_THREAD_SCHEDULER;
            UPGRADER.parseArguments(VersionNumber.asVersion(VERSION), Arrays.asList(args));

            LOG.info("*** " + TITLE + " ***");
            LOG.info("Operating System: " + System.getProperty("os.name") + ' ' + OperatingSystem.SYSTEM_VERSION);
            LOG.info("Java Version: " + System.getProperty("java.version") + ", " + System.getProperty("java.vendor"));
            LOG.info("Java VM Version: " + System.getProperty("java.vm.name") + " (" + System.getProperty("java.vm.info") + "), " + System.getProperty("java.vm.vendor"));
            LOG.info("Java Home: " + System.getProperty("java.home"));
            LOG.info("Current Directory: " + Paths.get("").toAbsolutePath());
            LOG.info("HMCL Directory: " + HMCL_DIRECTORY);

            launch(args);
        } catch (Throwable e) { // Fucking JavaFX will suppress the exception and will break our crash reporter.
            CRASH_REPORTER.uncaughtException(Thread.currentThread(), e);
        }
    }

    public static void stopApplication() {
        LOG.info("Stopping application.\n" + StringUtils.getStackTrace(Thread.currentThread().getStackTrace()));

        JFXUtilities.runInFX(() -> {
            if (Controllers.getStage() == null)
                return;
            Controllers.getStage().close();
            Schedulers.shutdown();
            Controllers.shutdown();
            Platform.exit();
            Lang.executeDelayed(OperatingSystem::forceGC, TimeUnit.SECONDS, 5, true);
        });
    }

    public static void stopWithoutPlatform() {
        LOG.info("Stopping application without JavaFX Toolkit.\n" + StringUtils.getStackTrace(Thread.currentThread().getStackTrace()));

        JFXUtilities.runInFX(() -> {
            if (Controllers.getStage() == null)
                return;
            Controllers.getStage().close();
            Schedulers.shutdown();
            Controllers.shutdown();
            Lang.executeDelayed(OperatingSystem::forceGC, TimeUnit.SECONDS, 5, true);
        });
    }

    public static List<File> getCurrentJarFiles() {
        List<File> result = new LinkedList<>();
        if (Launcher.class.getClassLoader() instanceof URLClassLoader) {
            URL[] urls = ((URLClassLoader) Launcher.class.getClassLoader()).getURLs();
            for (URL u : urls)
                try {
                    File f = new File(u.toURI());
                    if (f.isFile() && (f.getName().endsWith(".exe") || f.getName().endsWith(".jar")))
                        result.add(f);
                } catch (URISyntaxException e) {
                    return null;
                }
        }
        if (result.isEmpty())
            return null;
        else
            return result;
    }

    public static final File MINECRAFT_DIRECTORY = OperatingSystem.getWorkingDirectory("minecraft");
    public static final File HMCL_DIRECTORY = OperatingSystem.getWorkingDirectory("hmcl");
    public static final File LOG_DIRECTORY = new File(Launcher.HMCL_DIRECTORY, "logs");

    public static final String VERSION = System.getProperty("hmcl.version.override", "@HELLO_MINECRAFT_LAUNCHER_VERSION_FOR_GRADLE_REPLACING@");
    public static final String NAME = "HMCL";
    public static final String TITLE = NAME + " " + VERSION;
    public static final UpdateChecker UPDATE_CHECKER = new UpdateChecker(VersionNumber.asVersion(VERSION));
    public static final IUpgrader UPGRADER = new AppDataUpgrader();
    public static final CrashReporter CRASH_REPORTER = new CrashReporter();

    public static final String UPDATE_SERVER = "https://www.huangyuhui.net";
    public static final String CONTACT = UPDATE_SERVER + "/hmcl.php";
    public static final String PUBLISH = "http://www.mcbbs.net/thread-142335-1-1.html";
}
