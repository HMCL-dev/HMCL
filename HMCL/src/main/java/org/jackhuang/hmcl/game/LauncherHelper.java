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
package org.jackhuang.hmcl.game;

import javafx.application.Platform;
import org.jackhuang.hmcl.Launcher;
import org.jackhuang.hmcl.auth.Account;
import org.jackhuang.hmcl.auth.AuthInfo;
import org.jackhuang.hmcl.auth.AuthenticationException;
import org.jackhuang.hmcl.auth.CredentialExpiredException;
import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.download.MaintainTask;
import org.jackhuang.hmcl.download.game.LibraryDownloadException;
import org.jackhuang.hmcl.launch.*;
import org.jackhuang.hmcl.mod.CurseCompletionException;
import org.jackhuang.hmcl.mod.CurseCompletionTask;
import org.jackhuang.hmcl.mod.ModpackConfiguration;
import org.jackhuang.hmcl.setting.LauncherVisibility;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.setting.Settings;
import org.jackhuang.hmcl.setting.VersionSetting;
import org.jackhuang.hmcl.task.*;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.DialogController;
import org.jackhuang.hmcl.ui.LogWindow;
import org.jackhuang.hmcl.ui.construct.DialogCloseEvent;
import org.jackhuang.hmcl.ui.construct.MessageBox;
import org.jackhuang.hmcl.ui.construct.TaskExecutorDialogPane;
import org.jackhuang.hmcl.util.*;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.jackhuang.hmcl.util.Lang.mapOf;
import static org.jackhuang.hmcl.util.Pair.pair;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public final class LauncherHelper {
    public static final LauncherHelper INSTANCE = new LauncherHelper();
    private LauncherHelper(){}

    public static final Queue<ManagedProcess> PROCESSES = new ConcurrentLinkedQueue<>();
    private final TaskExecutorDialogPane launchingStepsPane = new TaskExecutorDialogPane(it -> {});

    public void launch(Profile profile, Account account, String selectedVersion, File scriptFile) {
        if (account == null)
            throw new IllegalArgumentException("No account");

        GameRepository repository = profile.getRepository();

        Version version = repository.getResolvedVersion(selectedVersion);
        VersionSetting setting = profile.getVersionSetting(selectedVersion);

        Platform.runLater(() -> {
            try {
                checkGameState(profile, setting, version, () -> {
                    Controllers.dialog(launchingStepsPane);
                    Schedulers.newThread().schedule(() -> launch0(profile, account, selectedVersion, scriptFile));
                });
            } catch (InterruptedException ignore) {
            }
        });
    }

    private void launch0(Profile profile, Account account, String selectedVersion, File scriptFile) {
        HMCLGameRepository repository = profile.getRepository();
        DefaultDependencyManager dependencyManager = profile.getDependency();
        Version version = MaintainTask.maintain(repository.getResolvedVersion(selectedVersion));
        VersionSetting setting = profile.getVersionSetting(selectedVersion);
        Optional<String> gameVersion = GameVersion.minecraftVersion(repository.getVersionJar(version));

        TaskExecutor executor = Task.of(Schedulers.javafx(), () -> emitStatus(LoadingState.DEPENDENCIES))
                .then(variables -> {
                    if (setting.isNotCheckGame())
                        return null;
                    else
                        return dependencyManager.checkGameCompletionAsync(version);
                })
                .then(Task.of(Schedulers.javafx(), () -> emitStatus(LoadingState.MODS)))
                .then(var -> {
                    try {
                        ModpackConfiguration<?> configuration = ModpackHelper.readModpackConfiguration(repository.getModpackConfiguration(selectedVersion));
                        if ("Curse".equals(configuration.getType()))
                            return new CurseCompletionTask(dependencyManager, selectedVersion);
                        else
                            return null;
                    } catch (IOException e) {
                        return null;
                    }
                })
                .then(Task.of(Schedulers.javafx(), () -> emitStatus(LoadingState.LOGGING_IN)))
                .then(Task.of(i18n("account.methods"), variables -> {
                    try {
                        variables.set("account", account.logIn());
                    } catch (CredentialExpiredException e) {
                        variables.set("account", DialogController.logIn(account));
                    } catch (AuthenticationException e) {
                        variables.set("account",
                                account.playOffline().orElseThrow(() -> e));
                    }
                }))
                .then(Task.of(Schedulers.javafx(), () -> emitStatus(LoadingState.LAUNCHING)))
                .then(Task.of(variables -> {
                    variables.set("launcher", new HMCLGameLauncher(
                            repository,
                            selectedVersion,
                            variables.get("account"),
                            setting.toLaunchOptions(profile.getGameDir()),
                            setting.getLauncherVisibility() == LauncherVisibility.CLOSE
                                    ? null // Unnecessary to start listening to game process output when close launcher immediately after game launched.
                                    : new HMCLProcessListener(variables.get("account"), setting, gameVersion.isPresent())
                    ));
                }))
                .then(variables -> {
                    DefaultLauncher launcher = variables.get("launcher");
                    if (scriptFile == null) {
                        return new LaunchTask<>(launcher::launch).setName(i18n("version.launch"));
                    } else {
                        return new LaunchTask<>(() -> {
                            launcher.makeLaunchScript(scriptFile);
                            return null;
                        }).setName(i18n("version.launch_script"));
                    }
                })
                .then(Task.of(variables -> {
                    if (scriptFile == null) {
                        ManagedProcess process = variables.get(LaunchTask.LAUNCH_ID);
                        PROCESSES.add(process);
                        if (setting.getLauncherVisibility() == LauncherVisibility.CLOSE)
                            Launcher.stopApplication();
                        else
                            launchingStepsPane.setCancel(it -> {
                                process.stop();
                                it.fireEvent(new DialogCloseEvent());
                            });
                    } else
                        Platform.runLater(() -> {
                            launchingStepsPane.fireEvent(new DialogCloseEvent());
                            Controllers.dialog(i18n("version.launch_script.success", scriptFile.getAbsolutePath()));
                        });

                }))
                .executor();

        launchingStepsPane.setExecutor(executor);
        executor.addTaskListener(new TaskListener() {
            final AtomicInteger finished = new AtomicInteger(0);

            @Override
            public void onFinished(Task task) {
                finished.incrementAndGet();
                int runningTasks = executor.getRunningTasks();
                Platform.runLater(() -> launchingStepsPane.setProgress(1.0 * finished.get() / runningTasks));
            }

            @Override
            public void onStop(boolean success, TaskExecutor executor) {
                if (!success && !Controllers.isStopped()) {
                    Platform.runLater(() -> {
                        // Check if the application has stopped
                        // because onStop will be invoked if tasks fail when the executor service shut down.
                        if (!Controllers.isStopped()) {
                            launchingStepsPane.fireEvent(new DialogCloseEvent());
                            Exception ex = executor.getLastException();
                            if (ex != null) {
                                String message;
                                if (ex instanceof CurseCompletionException) {
                                    message = i18n("modpack.type.curse.error");
                                } else if (ex instanceof PermissionException) {
                                    message = i18n("launch.failed.executable_permission");
                                } else if (ex instanceof ProcessCreationException) {
                                    message = i18n("launch.failed.creating_process") + ex.getLocalizedMessage();
                                } else if (ex instanceof NotDecompressingNativesException) {
                                    message = i18n("launch.failed.decompressing_natives") + ex.getLocalizedMessage();
                                } else if (ex instanceof LibraryDownloadException) {
                                    message = i18n("launch.failed.download_library", ((LibraryDownloadException) ex).getLibrary().getName()) + "\n" + StringUtils.getStackTrace(ex.getCause());
                                } else {
                                    message = StringUtils.getStackTrace(ex);
                                }
                                Controllers.dialog(message,
                                        scriptFile == null ? i18n("launch.failed") : i18n("version.launch_script.failed"),
                                        MessageBox.ERROR_MESSAGE);
                            }
                        }
                    });
                }
                launchingStepsPane.setExecutor(null);
            }
        });

        executor.start();
    }

    private static void checkGameState(Profile profile, VersionSetting setting, Version version, Runnable onAccept) throws InterruptedException {
        boolean flag = false;

        // Without onAccept called, the launching operation will be terminated.

        VersionNumber gameVersion = VersionNumber.asVersion(GameVersion.minecraftVersion(profile.getRepository().getVersionJar(version)).orElse("Unknown"));
        JavaVersion java = setting.getJavaVersion();
        if (java == null) {
            Controllers.dialog(i18n("launch.wrong_javadir"), i18n("message.error"), MessageBox.WARNING_MESSAGE, onAccept);
            setting.setJava(null);
            setting.setDefaultJavaPath(null);
            java = JavaVersion.fromCurrentEnvironment();
            flag = true;
        }

        if (!flag && java.getParsedVersion() < JavaVersion.JAVA_8) {
            if (gameVersion.compareTo(VersionNumber.asVersion("1.13")) >= 0) {
                // Minecraft 1.13 and later versions only support Java 8 or later.
                // Terminate launching operation.
                Controllers.dialog(i18n("launch.advice.java8_1_13"), i18n("message.error"), MessageBox.ERROR_MESSAGE, null);
            } else {
                // Most mods require Java 8 or later version.
                Controllers.dialog(i18n("launch.advice.newer_java"), i18n("message.error"), MessageBox.WARNING_MESSAGE, onAccept);
            }
            flag = true;
        }

        // LaunchWrapper will crash because of assuming the system class loader is an instance of URLClassLoader.
        // cpw has claimed that he will make MinecraftForge of 1.13 and later versions able to run on Java 9.
        if (!flag && java.getParsedVersion() >= JavaVersion.JAVA_9 && gameVersion.compareTo(VersionNumber.asVersion("1.12.5")) < 0 && version.getMainClass().contains("launchwrapper")) {
            Controllers.dialog(i18n("launch.advice.java9"), i18n("message.error"), MessageBox.ERROR_MESSAGE, null);
            flag = true;
        }

        if (!flag && java.getPlatform() == org.jackhuang.hmcl.util.Platform.BIT_32 &&
                org.jackhuang.hmcl.util.Platform.IS_64_BIT) {
            Controllers.dialog(i18n("launch.advice.different_platform"), i18n("message.error"), MessageBox.ERROR_MESSAGE, onAccept);
            flag = true;
        }
        if (!flag && java.getPlatform() == org.jackhuang.hmcl.util.Platform.BIT_32 &&
                setting.getMaxMemory() > 1.5 * 1024) {
            // 1.5 * 1024 is an inaccurate number.
            // Actual memory limit depends on operating system and memory.
            Controllers.dialog(i18n("launch.advice.too_large_memory_for_32bit"), i18n("message.error"), MessageBox.ERROR_MESSAGE, onAccept);
            flag = true;
        }
        if (!flag && OperatingSystem.TOTAL_MEMORY > 0 && OperatingSystem.TOTAL_MEMORY < setting.getMaxMemory()) {
            Controllers.dialog(i18n("launch.advice.not_enough_space", OperatingSystem.TOTAL_MEMORY), i18n("message.error"), MessageBox.ERROR_MESSAGE, onAccept);
            flag = true;
        }

        if (!flag)
            onAccept.run();
    }

    public static void stopManagedProcesses() {
        while (!PROCESSES.isEmpty())
            Optional.ofNullable(PROCESSES.poll()).ifPresent(ManagedProcess::stop);
    }

    public void emitStatus(LoadingState state) {
        if (state == LoadingState.DONE) {
            launchingStepsPane.fireEvent(new DialogCloseEvent());
        }

        launchingStepsPane.setTitle(state.getLocalizedMessage());
        launchingStepsPane.setSubtitle((state.ordinal() + 1) + " / " + LoadingState.values().length);
    }

    private void checkExit(LauncherVisibility v) {
        switch (v) {
            case HIDE_AND_REOPEN:
                Platform.runLater(Controllers.getStage()::show);
                break;
            case KEEP:
                // No operations here
                break;
            case CLOSE:
                throw new Error("Never get to here");
            case HIDE:
                Platform.runLater(() -> {
                    // Shut down the platform when user closed log window.
                    Platform.setImplicitExit(true);
                    // If we use Launcher.stop(), log window will be halt immediately.
                    Launcher.stopWithoutPlatform();
                });
                break;
        }
    }

    private static class LaunchTask<T> extends TaskResult<T> {
        private final ExceptionalSupplier<T, Exception> supplier;

        public LaunchTask(ExceptionalSupplier<T, Exception> supplier) {
            this.supplier = supplier;
        }

        @Override
        public void execute() throws Exception {
            setResult(supplier.get());
        }

        @Override
        public String getId() {
            return LAUNCH_ID;
        }

        static final String LAUNCH_ID = "launch";
    }

    /**
     * The managed process listener.
     * Guarantee that one [JavaProcess], one [HMCLProcessListener].
     * Because every time we launched a game, we generates a new [HMCLProcessListener]
     */
    class HMCLProcessListener implements ProcessListener {

        private final VersionSetting setting;
        private final Map<String, String> forbiddenTokens;
        private final LauncherVisibility visibility;
        private ManagedProcess process;
        private boolean lwjgl;
        private LogWindow logWindow;
        private final boolean detectWindow;
        private final LinkedList<Pair<String, Log4jLevel>> logs;
        private final CountDownLatch latch = new CountDownLatch(1);

        public HMCLProcessListener(AuthInfo authInfo, VersionSetting setting, boolean detectWindow) {
            this.setting = setting;
            this.detectWindow = detectWindow;

            if (authInfo == null)
                forbiddenTokens = Collections.emptyMap();
            else
                forbiddenTokens = mapOf(
                        pair(authInfo.getAccessToken(), "<access token>"),
                        pair(UUIDTypeAdapter.fromUUID(authInfo.getUUID()), "<uuid>"),
                        pair(authInfo.getUsername(), "<player>")
                );

            visibility = setting.getLauncherVisibility();
            logs = new LinkedList<>();
        }

        @Override
        public void setProcess(ManagedProcess process) {
            this.process = process;

            if (setting.isShowLogs())
                Platform.runLater(() -> {
                    logWindow = new LogWindow();
                    logWindow.show();
                    latch.countDown();
                });
        }

        @Override
        public synchronized void onLog(String log, Log4jLevel level) {
            String newLog = log;
            for (Map.Entry<String, String> entry : forbiddenTokens.entrySet())
                newLog = newLog.replace(entry.getKey(), entry.getValue());

            if (level.lessOrEqual(Log4jLevel.ERROR))
                System.err.print(log);
            else
                System.out.print(log);

            logs.add(pair(log, level));
            if (logs.size() > Settings.instance().getLogLines())
                logs.removeFirst();

            if (setting.isShowLogs()) {
                Lang.invoke(() -> {
                    latch.await();
                    logWindow.waitForLoaded();
                });

                Platform.runLater(() -> logWindow.logLine(log, level));
            }

            if (!lwjgl && log.contains("LWJGL Version: ") || !detectWindow) {
                lwjgl = true;
                switch (visibility) {
                    case HIDE_AND_REOPEN:
                        Platform.runLater(() -> {
                            Controllers.getStage().hide();
                            emitStatus(LoadingState.DONE);
                        });
                        break;
                    case CLOSE:
                        // Never come to here.
                        break;
                    case KEEP:
                        Platform.runLater(() -> {
                            emitStatus(LoadingState.DONE);
                        });
                        break;
                    case HIDE:
                        Platform.runLater(() -> {
                            Controllers.getStage().close();
                            emitStatus(LoadingState.DONE);
                        });
                        break;
                }
            }
        }

        @Override
        public void onExit(int exitCode, ExitType exitType) {
            if (exitType == ExitType.INTERRUPTED)
                return;

            if (exitType != ExitType.NORMAL && logWindow == null)
                Platform.runLater(() -> {
                    logWindow = new LogWindow();

                    switch (exitType) {
                        case JVM_ERROR:
                            logWindow.setTitle(i18n("launch.failed.cannot_create_jvm"));
                            break;
                        case APPLICATION_ERROR:
                            logWindow.setTitle(i18n("launch.failed.exited_abnormally"));
                            break;
                    }

                    logWindow.show();
                    logWindow.onDone.register(() -> {
                        logWindow.logLine("Command: " + new CommandBuilder().addAll(process.getCommands()).toString(), Log4jLevel.INFO);
                        for (Map.Entry<String, Log4jLevel> entry : logs)
                            logWindow.logLine(entry.getKey(), entry.getValue());
                    });
                });

            checkExit(visibility);
        }

    }
}
