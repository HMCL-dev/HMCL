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
package org.jackhuang.hmcl.game;

import com.jfoenix.concurrency.JFXUtilities;
import javafx.application.Platform;
import org.jackhuang.hmcl.Main;
import org.jackhuang.hmcl.auth.Account;
import org.jackhuang.hmcl.auth.AuthInfo;
import org.jackhuang.hmcl.auth.AuthenticationException;
import org.jackhuang.hmcl.auth.SpecificCharacterSelector;
import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.launch.*;
import org.jackhuang.hmcl.mod.CurseCompletionTask;
import org.jackhuang.hmcl.setting.*;
import org.jackhuang.hmcl.task.*;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.DialogController;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane;
import org.jackhuang.hmcl.ui.construct.TaskExecutorDialogPane;
import org.jackhuang.hmcl.ui.LogWindow;
import org.jackhuang.hmcl.ui.construct.MessageBox;
import org.jackhuang.hmcl.util.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

public final class LauncherHelper {
    public static final LauncherHelper INSTANCE = new LauncherHelper();
    private LauncherHelper(){}

    private TaskExecutor executor;
    public static final Queue<ManagedProcess> PROCESSES = new ConcurrentLinkedQueue<>();
    private final TaskExecutorDialogPane launchingStepsPane = new TaskExecutorDialogPane(() -> {});

    public void launch(String selectedVersion, File scriptFile) {
        Profile profile = Settings.INSTANCE.getSelectedProfile();
        GameRepository repository = profile.getRepository();
        Account account = Settings.INSTANCE.getSelectedAccount();
        if (account == null)
            throw new IllegalStateException("No account");

        Version version = repository.getVersion(selectedVersion);
        VersionSetting setting = profile.getVersionSetting(selectedVersion);

        Platform.runLater(() -> {
            try {
                checkGameState(profile, setting, version, () -> Schedulers.newThread().schedule(() -> launch0(selectedVersion, scriptFile)));
            } catch (InterruptedException ignore) {
            }
        });
    }

    private void launch0(String selectedVersion, File scriptFile) {
        Profile profile = Settings.INSTANCE.getSelectedProfile();
        GameRepository repository = profile.getRepository();
        DefaultDependencyManager dependencyManager = profile.getDependency();
        Version version = repository.getVersion(selectedVersion);
        Account account = Settings.INSTANCE.getSelectedAccount();
        VersionSetting setting = profile.getVersionSetting(selectedVersion);

        executor = Task.of(Schedulers.javafx(), () -> Controllers.dialog(launchingStepsPane))
                .then(Task.of(Schedulers.javafx(), () -> emitStatus(LoadingState.DEPENDENCIES)))
                .then(variables -> {
                    if (setting.isNotCheckGame())
                        return null;
                    else
                        return dependencyManager.checkGameCompletionAsync(version);
                })
                .then(Task.of(Schedulers.javafx(), () -> emitStatus(LoadingState.MODS)))
                .then(new CurseCompletionTask(dependencyManager, selectedVersion))
                .then(Task.of(Schedulers.javafx(), () -> emitStatus(LoadingState.LOGGING_IN)))
                .then(Task.of(Main.i18n("account.methods"), variables -> {
                    try {
                        variables.set("account", account.logIn(new SpecificCharacterSelector(Accounts.getCurrentCharacter(account)), Settings.INSTANCE.getProxy()));
                    } catch (AuthenticationException e) {
                        variables.set("account", DialogController.logIn(account));
                        JFXUtilities.runInFX(() -> Controllers.dialog(launchingStepsPane));
                    }
                }))
                .then(Task.of(Schedulers.javafx(), () -> emitStatus(LoadingState.LAUNCHING)))
                .then(Task.of(variables -> {
                    variables.set("launcher", new HMCLGameLauncher(
                            repository, selectedVersion, variables.get("account"), setting.toLaunchOptions(profile.getGameDir()), new HMCLProcessListener(variables.get("account"), setting)
                    ));
                }))
                .then(variables -> {
                    DefaultLauncher launcher = variables.<DefaultLauncher>get("launcher");
                    if (scriptFile == null) {
                        return new LaunchTask<>(launcher::launch).setName(Main.i18n("version.launch"));
                    } else {
                        return new LaunchTask<>(() -> {
                            launcher.makeLaunchScript(scriptFile);
                            return null;
                        }).setName(Main.i18n("version.launch_script"));
                    }
                })
                .then(Task.of(variables -> {
                    if (scriptFile == null) {
                        ManagedProcess process = variables.get(LaunchTask.LAUNCH_ID);
                        PROCESSES.add(process);
                        if (setting.getLauncherVisibility() == LauncherVisibility.CLOSE)
                            Main.stopApplication();
                        else
                            launchingStepsPane.setCancel(() -> {
                                process.stop();
                                Controllers.closeDialog();
                            });
                    } else
                        Platform.runLater(() ->
                                Controllers.dialog(Main.i18n("version.launch_script.success", scriptFile.getAbsolutePath())));

                }))
                .executor();

        launchingStepsPane.setExecutor(executor);
        executor.addTaskListener(new TaskListener() {
            AtomicInteger finished = new AtomicInteger(0);

            @Override
            public void onFinished(Task task) {
                finished.incrementAndGet();
                Platform.runLater(() -> {
                    launchingStepsPane.setProgress(1.0 * finished.get() / executor.getRunningTasks());
                });
            }

            @Override
            public void onTerminate() {
                Platform.runLater(() -> {
                    if (executor.getLastException() != null)
                        Controllers.dialog(I18nException.getStackTrace(executor.getLastException()),
                                scriptFile == null ? Main.i18n("launch.failed") : Main.i18n("version.launch_script.failed"),
                                MessageBox.ERROR_MESSAGE, Controllers::closeDialog);
                    else
                        Controllers.closeDialog();
                });
            }
        });

        executor.start();
    }

    private static void checkGameState(Profile profile, VersionSetting setting, Version version, Runnable onAccept) throws InterruptedException {
        boolean flag = false, suggest = true;

        VersionNumber gameVersion = VersionNumber.asVersion(GameVersion.minecraftVersion(profile.getRepository().getVersionJar(version)).orElse("Unknown"));
        JavaVersion java = setting.getJavaVersion();
        if (java == null) {
            Controllers.dialog(Main.i18n("launch.wrong_javadir"), Main.i18n("message.error"), MessageBox.ERROR_MESSAGE, onAccept);
            setting.setJava(null);
            java = JavaVersion.fromCurrentEnvironment();
            flag = true;
        }

        if (java.getParsedVersion() < JavaVersion.JAVA_8) {
            Controllers.dialog(Main.i18n("launch.advice.newer_java"), Main.i18n("message.error"), MessageBox.ERROR_MESSAGE, onAccept);
            flag = true;
        }

        if (java.getParsedVersion() >= JavaVersion.JAVA_9 && gameVersion.compareTo(VersionNumber.asVersion("1.12.5")) < 0 && version.getMainClass().contains("launchwrapper")) {
            Controllers.dialog(Main.i18n("launch.advice.java9"), Main.i18n("message.error"), MessageBox.ERROR_MESSAGE, null);
            suggest = false;
            flag = true;
        }

        if (java.getPlatform() == org.jackhuang.hmcl.util.Platform.BIT_32 &&
                org.jackhuang.hmcl.util.Platform.IS_64_BIT) {
            Controllers.dialog(Main.i18n("launch.advice.different_platform"), Main.i18n("message.error"), MessageBox.ERROR_MESSAGE, onAccept);
            flag = true;
        }
        if (java.getPlatform() == org.jackhuang.hmcl.util.Platform.BIT_32 &&
                setting.getMaxMemory() > 1.5 * 1024) {
            Controllers.dialog(Main.i18n("launch.advice.too_large_memory_for_32bit"), Main.i18n("message.error"), MessageBox.ERROR_MESSAGE, onAccept);
            flag = true;
        }
        if (OperatingSystem.TOTAL_MEMORY > 0 && OperatingSystem.TOTAL_MEMORY < setting.getMaxMemory()) {
            Controllers.dialog(Main.i18n("launch.advice.not_enough_space", OperatingSystem.TOTAL_MEMORY), Main.i18n("message.error"), MessageBox.ERROR_MESSAGE, onAccept);
            flag = true;
        }

        if (flag) {
            if (suggest && Controllers.getDialogContent() instanceof MessageDialogPane)
                ((MessageDialogPane) Controllers.getDialogContent()).disableClosingDialog();
        } else
            onAccept.run();
    }

    public static void stopManagedProcesses() {
        synchronized (PROCESSES) {
            while (!PROCESSES.isEmpty())
                Optional.ofNullable(PROCESSES.poll()).ifPresent(ManagedProcess::stop);
        }
    }

    public void emitStatus(LoadingState state) {
        if (state == LoadingState.DONE)
            Controllers.closeDialog();

        launchingStepsPane.setTitle(state.getLocalizedMessage());
        launchingStepsPane.setSubtitle((state.ordinal() + 1) + " / " + LoadingState.values().length);
        Controllers.dialog(launchingStepsPane);
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
                    // If we use Main.stop(), log window will be halt immediately.
                    Main.stopWithoutPlatform();
                });
                break;
        }
    }

    class LaunchTask<T> extends TaskResult<T> {
        private final ExceptionalSupplier<T, Exception> supplier;

        public LaunchTask(ExceptionalSupplier<T, Exception> supplier) {
            this.supplier = supplier;
        }

        @Override
        public void execute() throws Exception {
            try {
                setResult(supplier.get());
            } catch (PermissionException e) {
                throw new I18nException(Main.i18n("launch.failed.executable_permission"), e);
            } catch (ProcessCreationException e) {
                throw new I18nException(Main.i18n("launch.failed.creating_process") + e.getLocalizedMessage(), e);
            } catch (NotDecompressingNativesException e) {
                throw new I18nException(Main.i18n("launch.failed.decompressing_natives") + e.getLocalizedMessage(), e);
            }
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
        private final LinkedList<Pair<String, Log4jLevel>> logs;

        public HMCLProcessListener(AuthInfo authInfo, VersionSetting setting) {
            this.setting = setting;

            if (authInfo == null)
                forbiddenTokens = Collections.emptyMap();
            else
                forbiddenTokens = Lang.mapOf(
                        new Pair<>(authInfo.getAuthToken(), "<access token>"),
                        new Pair<>(authInfo.getUserId(), "<uuid>"),
                        new Pair<>(authInfo.getUsername(), "<player>")
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
                });
        }

        @Override
        public void onLog(String log, Log4jLevel level) {
            String newLog = log;
            for (Map.Entry<String, String> entry : forbiddenTokens.entrySet())
                newLog = newLog.replace(entry.getKey(), entry.getValue());

            if (level.lessOrEqual(Log4jLevel.ERROR))
                System.err.print(log);
            else
                System.out.print(log);

            Platform.runLater(() -> {
                logs.add(new Pair<>(log, level));
                if (logs.size() > Settings.INSTANCE.getLogLines())
                    logs.removeFirst();
                if (logWindow != null)
                    logWindow.logLine(log, level);
            });

            if (!lwjgl && log.contains("LWJGL Version: ")) {
                lwjgl = true;
                switch (visibility) {
                    case HIDE_AND_REOPEN:
                        Platform.runLater(() -> {
                            Controllers.getStage().hide();
                            emitStatus(LoadingState.DONE);
                        });
                        break;
                    case CLOSE:
                        throw new Error("Never come to here");
                    case KEEP:
                        // No operations here
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
                            logWindow.setTitle(Main.i18n("launch.failed.cannot_create_jvm"));
                            break;
                        case APPLICATION_ERROR:
                            logWindow.setTitle(Main.i18n("launch.failed.exited_abnormally"));
                            break;
                    }

                    logWindow.show();
                    logWindow.onDone.register(() -> {
                        logWindow.logLine("Commands: " + StringUtils.makeCommand(process.getCommands()), Log4jLevel.INFO);
                        for (Map.Entry<String, Log4jLevel> entry : logs)
                            logWindow.logLine(entry.getKey(), entry.getValue());
                    });
                });

            checkExit(visibility);
        }

    }
}
