/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.game;

import com.jfoenix.controls.JFXButton;
import javafx.application.Platform;
import javafx.stage.Stage;
import org.jackhuang.hmcl.Launcher;
import org.jackhuang.hmcl.auth.*;
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorDownloadException;
import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.download.MaintainTask;
import org.jackhuang.hmcl.download.game.GameAssetIndexDownloadTask;
import org.jackhuang.hmcl.download.game.GameVerificationFixTask;
import org.jackhuang.hmcl.download.game.LibraryDownloadException;
import org.jackhuang.hmcl.download.java.JavaRepository;
import org.jackhuang.hmcl.launch.NotDecompressingNativesException;
import org.jackhuang.hmcl.launch.PermissionException;
import org.jackhuang.hmcl.launch.ProcessCreationException;
import org.jackhuang.hmcl.launch.ProcessListener;
import org.jackhuang.hmcl.mod.ModpackConfiguration;
import org.jackhuang.hmcl.mod.curse.CurseCompletionException;
import org.jackhuang.hmcl.mod.curse.CurseCompletionTask;
import org.jackhuang.hmcl.mod.curse.CurseInstallTask;
import org.jackhuang.hmcl.mod.mcbbs.McbbsModpackCompletionTask;
import org.jackhuang.hmcl.mod.mcbbs.McbbsModpackLocalInstallTask;
import org.jackhuang.hmcl.mod.server.ServerModpackCompletionTask;
import org.jackhuang.hmcl.mod.server.ServerModpackLocalInstallTask;
import org.jackhuang.hmcl.setting.LauncherVisibility;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.setting.VersionSetting;
import org.jackhuang.hmcl.task.*;
import org.jackhuang.hmcl.ui.*;
import org.jackhuang.hmcl.ui.construct.DialogCloseEvent;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane.MessageType;
import org.jackhuang.hmcl.ui.construct.TaskExecutorDialogPane;
import org.jackhuang.hmcl.util.*;
import org.jackhuang.hmcl.util.i18n.I18n;
import org.jackhuang.hmcl.util.io.ResponseCodeException;
import org.jackhuang.hmcl.util.platform.*;
import org.jackhuang.hmcl.util.versioning.VersionNumber;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;

import static org.jackhuang.hmcl.setting.ConfigHolder.config;
import static org.jackhuang.hmcl.util.Lang.mapOf;
import static org.jackhuang.hmcl.util.Logging.LOG;
import static org.jackhuang.hmcl.util.Pair.pair;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public final class LauncherHelper {

    private final Profile profile;
    private final Account account;
    private final String selectedVersion;
    private File scriptFile;
    private final VersionSetting setting;
    private LauncherVisibility launcherVisibility;
    private boolean showLogs;

    public LauncherHelper(Profile profile, Account account, String selectedVersion) {
        this.profile = Objects.requireNonNull(profile);
        this.account = Objects.requireNonNull(account);
        this.selectedVersion = Objects.requireNonNull(selectedVersion);
        this.setting = profile.getVersionSetting(selectedVersion);
        this.launcherVisibility = setting.getLauncherVisibility();
        this.showLogs = setting.isShowLogs();
        this.launchingStepsPane.setTitle(i18n("version.launch"));
    }

    private final TaskExecutorDialogPane launchingStepsPane = new TaskExecutorDialogPane(it -> {});

    public void setTestMode() {
        launcherVisibility = LauncherVisibility.KEEP;
        showLogs = true;
    }

    public void setKeep() {
        launcherVisibility = LauncherVisibility.KEEP;
    }

    public void launch() {
        FXUtils.checkFxUserThread();

        Logging.LOG.info("Launching game version: " + selectedVersion);

        Controllers.dialog(launchingStepsPane);
        launch0();
    }

    public void makeLaunchScript(File scriptFile) {
        this.scriptFile = Objects.requireNonNull(scriptFile);

        launch();
    }

    private void launch0() {
        HMCLGameRepository repository = profile.getRepository();
        DefaultDependencyManager dependencyManager = profile.getDependency();
        Version version = MaintainTask.maintain(repository, repository.getResolvedVersion(selectedVersion));
        Optional<String> gameVersion = repository.getGameVersion(version);
        boolean integrityCheck = repository.unmarkVersionLaunchedAbnormally(selectedVersion);
        CountDownLatch launchingLatch = new CountDownLatch(1);

        AtomicReference<JavaVersion> javaVersionRef = new AtomicReference<>();

        TaskExecutor executor = checkGameState(profile, setting, version)
                .thenComposeAsync(javaVersion -> {
                    javaVersionRef.set(Objects.requireNonNull(javaVersion));
                    return dependencyManager.checkPatchCompletionAsync(repository.getVersion(selectedVersion), integrityCheck);
                })
                .thenComposeAsync(Task.allOf(
                        Task.composeAsync(() -> {
                            if (setting.isNotCheckGame())
                                return null;
                            else
                                return dependencyManager.checkGameCompletionAsync(version, integrityCheck);
                        }), Task.composeAsync(() -> {
                            if (setting.isNotCheckGame()) {
                                return null;
                            } else {
                                try {
                                    ModpackConfiguration<?> configuration = ModpackHelper.readModpackConfiguration(repository.getModpackConfiguration(selectedVersion));
                                    if (CurseInstallTask.MODPACK_TYPE.equals(configuration.getType()))
                                        return new CurseCompletionTask(dependencyManager, selectedVersion);
                                    else if (ServerModpackLocalInstallTask.MODPACK_TYPE.equals(configuration.getType()))
                                        return new ServerModpackCompletionTask(dependencyManager, selectedVersion);
                                    else if (McbbsModpackLocalInstallTask.MODPACK_TYPE.equals(configuration.getType()))
                                        return new McbbsModpackCompletionTask(dependencyManager, selectedVersion);
                                    else
                                        return null;
                                } catch (IOException e) {
                                    return null;
                                }
                            }
                        }))).withStage("launch.state.dependencies")
                .thenComposeAsync(() -> {
                    return gameVersion.map(s -> new GameVerificationFixTask(dependencyManager, s, version)).orElse(null);
                })
                .thenComposeAsync(Task.supplyAsync(() -> {
                    try {
                        return account.logIn();
                    } catch (CredentialExpiredException e) {
                        LOG.info("Credential has expired: " + e);
                        return DialogController.logIn(account);
                    } catch (AuthenticationException e) {
                        LOG.warning("Authentication failed, try playing offline: " + e);
                        return account.playOffline().orElseThrow(() -> e);
                    }
                }).withStage("launch.state.logging_in"))
                .thenComposeAsync(authInfo -> Task.supplyAsync(() -> {
                    LaunchOptions launchOptions = repository.getLaunchOptions(selectedVersion, javaVersionRef.get(), profile.getGameDir());
                    return new HMCLGameLauncher(
                            repository,
                            version,
                            authInfo,
                            launchOptions,
                            launcherVisibility == LauncherVisibility.CLOSE
                                    ? null // Unnecessary to start listening to game process output when close launcher immediately after game launched.
                                    : new HMCLProcessListener(repository, version, authInfo, launchOptions, launchingLatch, gameVersion.isPresent())
                    );
                }).thenComposeAsync(launcher -> { // launcher is prev task's result
                    if (scriptFile == null) {
                        return Task.supplyAsync(launcher::launch);
                    } else {
                        return Task.supplyAsync(() -> {
                            launcher.makeLaunchScript(scriptFile);
                            return null;
                        });
                    }
                }).thenAcceptAsync(process -> { // process is LaunchTask's result
                    if (scriptFile == null) {
                        PROCESSES.add(process);
                        if (launcherVisibility == LauncherVisibility.CLOSE)
                            Launcher.stopApplication();
                        else
                            launchingStepsPane.setCancel(it -> {
                                process.stop();
                                it.fireEvent(new DialogCloseEvent());
                            });
                    } else {
                        Platform.runLater(() -> {
                            launchingStepsPane.fireEvent(new DialogCloseEvent());
                            Controllers.dialog(i18n("version.launch_script.success", scriptFile.getAbsolutePath()));
                        });
                    }
                }).thenRunAsync(() -> {
                    launchingLatch.await();
                }).withStage("launch.state.waiting_launching"))
                .withStagesHint(Lang.immutableListOf(
                        "launch.state.java",
                        "launch.state.dependencies",
                        "launch.state.logging_in",
                        "launch.state.waiting_launching"))
                .executor();
        launchingStepsPane.setExecutor(executor, false);
        executor.addTaskListener(new TaskListener() {

            @Override
            public void onStop(boolean success, TaskExecutor executor) {
                Platform.runLater(() -> {
                    // Check if the application has stopped
                    // because onStop will be invoked if tasks fail when the executor service shut down.
                    if (!Controllers.isStopped()) {
                        launchingStepsPane.fireEvent(new DialogCloseEvent());
                        if (!success) {
                            Exception ex = executor.getException();
                            if (!(ex instanceof CancellationException)) {
                                String message;
                                if (ex instanceof CurseCompletionException) {
                                    if (ex.getCause() instanceof FileNotFoundException)
                                        message = i18n("modpack.type.curse.not_found");
                                    else
                                        message = i18n("modpack.type.curse.error");
                                } else if (ex instanceof PermissionException) {
                                    message = i18n("launch.failed.executable_permission");
                                } else if (ex instanceof ProcessCreationException) {
                                    message = i18n("launch.failed.creating_process") + ex.getLocalizedMessage();
                                } else if (ex instanceof NotDecompressingNativesException) {
                                    message = i18n("launch.failed.decompressing_natives") + ex.getLocalizedMessage();
                                } else if (ex instanceof LibraryDownloadException) {
                                    message = i18n("launch.failed.download_library", ((LibraryDownloadException) ex).getLibrary().getName()) + "\n";
                                    if (ex.getCause() instanceof ResponseCodeException) {
                                        ResponseCodeException rce = (ResponseCodeException) ex.getCause();
                                        int responseCode = rce.getResponseCode();
                                        URL url = rce.getUrl();
                                        if (responseCode == 404)
                                            message += i18n("download.code.404", url);
                                        else
                                            message += i18n("download.failed", url, responseCode);
                                    } else {
                                        message += StringUtils.getStackTrace(ex.getCause());
                                    }
                                } else if (ex instanceof DownloadException) {
                                    URL url = ((DownloadException) ex).getUrl();
                                    if (ex.getCause() instanceof SocketTimeoutException) {
                                        message = i18n("install.failed.downloading.timeout", url);
                                    } else if (ex.getCause() instanceof ResponseCodeException) {
                                        ResponseCodeException responseCodeException = (ResponseCodeException) ex.getCause();
                                        if (I18n.hasKey("download.code." + responseCodeException.getResponseCode())) {
                                            message = i18n("download.code." + responseCodeException.getResponseCode(), url);
                                        } else {
                                            message = i18n("install.failed.downloading.detail", url) + "\n" + StringUtils.getStackTrace(ex.getCause());
                                        }
                                    } else {
                                        message = i18n("install.failed.downloading.detail", url) + "\n" + StringUtils.getStackTrace(ex.getCause());
                                    }
                                } else if (ex instanceof GameAssetIndexDownloadTask.GameAssetIndexMalformedException) {
                                    message = i18n("assets.index.malformed");
                                } else if (ex instanceof AuthlibInjectorDownloadException) {
                                    message = i18n("account.failed.injector_download_failure");
                                } else if (ex instanceof CharacterDeletedException) {
                                    message = i18n("account.failed.character_deleted");
                                } else if (ex instanceof ResponseCodeException) {
                                    ResponseCodeException rce = (ResponseCodeException) ex;
                                    int responseCode = rce.getResponseCode();
                                    URL url = rce.getUrl();
                                    if (responseCode == 404)
                                        message = i18n("download.code.404", url);
                                    else
                                        message = i18n("download.failed", url, responseCode);
                                } else {
                                    message = StringUtils.getStackTrace(ex);
                                }
                                Controllers.dialog(message,
                                        scriptFile == null ? i18n("launch.failed") : i18n("version.launch_script.failed"),
                                        MessageType.ERROR);
                            }
                        }
                    }
                    launchingStepsPane.setExecutor(null);
                });
            }
        });

        executor.start();
    }

    private static Task<JavaVersion> checkGameState(Profile profile, VersionSetting setting, Version version) {
        VersionNumber gameVersion = VersionNumber.asVersion(profile.getRepository().getGameVersion(version).orElse("Unknown"));

        if (setting.isNotCheckJVM()) {
            return Task.composeAsync(() -> setting.getJavaVersion(gameVersion, version))
                    .thenApplyAsync(javaVersion -> Optional.ofNullable(javaVersion).orElseGet(JavaVersion::fromCurrentEnvironment))
                    .withStage("launch.state.java");
        }

        return Task.composeAsync(() -> {
            return setting.getJavaVersion(gameVersion, version);
        }).thenComposeAsync(Schedulers.javafx(), javaVersion -> {
            // Reset invalid java version
            if (javaVersion == null) {
                CompletableFuture<JavaVersion> future = new CompletableFuture<>();
                Runnable continueAction = () -> future.complete(JavaVersion.fromCurrentEnvironment());

                if (setting.isJavaAutoSelected()) {
//                    JavaVersionConstraint.VersionRange range = JavaVersionConstraint.findSuitableJavaVersionRange(gameVersion, version);
                    // TODO: download java 16 if necessary!
                    Controllers.dialog(i18n("launch.failed.no_accepted_java"), i18n("message.warning"), MessageType.WARNING, continueAction);
                } else {
                    Controllers.dialog(i18n("launch.wrong_javadir"), i18n("message.warning"), MessageType.WARNING, continueAction);

                    setting.setJava(null);
                    setting.setDefaultJavaPath(null);
                    setting.setJavaVersion(JavaVersion.fromCurrentEnvironment());
                }

                return Task.fromCompletableFuture(future);
            } else {
                return Task.completed(javaVersion);
            }
        }).thenComposeAsync(javaVersion -> {
            return Task.allOf(Task.completed(javaVersion), Task.supplyAsync(() -> JavaVersionConstraint.findSuitableJavaVersion(gameVersion, version)));
        }).thenComposeAsync(Schedulers.javafx(), javaVersions -> {
            JavaVersion javaVersion = (JavaVersion) javaVersions.get(0);
            JavaVersion suggestedJavaVersion = (JavaVersion) javaVersions.get(1);
            if (setting.isJavaAutoSelected()) return Task.completed(javaVersion);

            JavaVersionConstraint violatedMandatoryConstraint = null;
            JavaVersionConstraint violatedSuggestedConstraint = null;
            for (JavaVersionConstraint constraint : JavaVersionConstraint.values()) {
                if (constraint.getGameVersion().contains(gameVersion) && constraint.appliesToVersion(gameVersion, version)) {
                    if (!constraint.getJavaVersion(version).contains(javaVersion.getVersionNumber())) {
                        if (constraint.getType() == JavaVersionConstraint.RULE_MANDATORY) {
                            violatedMandatoryConstraint = constraint;
                        } else if (constraint.getType() == JavaVersionConstraint.RULE_SUGGESTED) {
                            violatedSuggestedConstraint = constraint;
                        }
                    }

                }
            }

            boolean suggested = false;
            CompletableFuture<JavaVersion> future = new CompletableFuture<>();
            Runnable continueAction = () -> future.complete(javaVersion);
            Runnable breakAction = () -> {
                future.completeExceptionally(new CancellationException("Launch operation was cancelled by user"));
            };

            if (violatedMandatoryConstraint != null) {
                if (suggestedJavaVersion != null) {
                    Controllers.confirm(i18n("launch.advice.java.auto"), i18n("message.warning"), () -> {
                        setting.setJavaAutoSelected();
                        future.complete(suggestedJavaVersion);
                    }, breakAction);
                    return Task.fromCompletableFuture(future);
                } else {
                    switch (violatedMandatoryConstraint) {
                        case GAME_JSON:
                            MessageDialogPane dialog = new MessageDialogPane(
                                    i18n("launch.advice.require_newer_java_version",
                                            gameVersion.toString(),
                                            version.getJavaVersion().getMajorVersion()),
                                    i18n("message.warning"),
                                    MessageType.QUESTION);

                            JFXButton linkButton = new JFXButton(i18n("download.external_link"));
                            linkButton.setOnAction(e -> FXUtils.openLink("https://adoptopenjdk.net/"));
                            linkButton.getStyleClass().add("dialog-accept");
                            dialog.addButton(linkButton);

                            JFXButton yesButton = new JFXButton(i18n("button.ok"));
                            yesButton.setOnAction(event -> {
                                downloadJava(version.getJavaVersion(), profile)
                                        .thenAcceptAsync(x -> {
                                            try {
                                                Optional<JavaVersion> newAcceptableJava = JavaVersion.getJavas().stream()
                                                        .filter(newJava -> newJava.getParsedVersion() >= version.getJavaVersion().getMajorVersion())
                                                        .max(Comparator.comparing(JavaVersion::getVersionNumber));
                                                if (newAcceptableJava.isPresent()) {
                                                    setting.setJavaVersion(newAcceptableJava.get());
                                                    future.complete(newAcceptableJava.get());
                                                    return;
                                                }
                                            } catch (InterruptedException e) {
                                                LOG.log(Level.SEVERE, "Cannot list javas", e);
                                            }
                                            future.complete(javaVersion);
                                        }, Platform::runLater)
                                        .exceptionally(Lang.handleUncaught);
                            });
                            yesButton.getStyleClass().add("dialog-accept");
                            dialog.addButton(yesButton);

                            JFXButton noButton = new JFXButton(i18n("button.cancel"));
                            noButton.getStyleClass().add("dialog-cancel");
                            dialog.addButton(noButton);
                            dialog.setCancelButton(noButton);

                            Controllers.dialog(dialog);
                            return Task.fromCompletableFuture(future);
                        case VANILLA_JAVA_16:
                            Controllers.confirm(i18n("launch.advice.require_newer_java_version", gameVersion.toString(), 16), i18n("message.warning"), () -> {
                                FXUtils.openLink("https://adoptopenjdk.net/");
                            }, breakAction);
                            return null;
                        case VANILLA_JAVA_8:
                            Controllers.dialog(i18n("launch.advice.java8_1_13"), i18n("message.error"), MessageType.ERROR, breakAction);
                            return null;
                        case VANILLA_LINUX_JAVA_8:
                            Controllers.dialog(i18n("launch.advice.vanilla_linux_java_8"), i18n("message.error"), MessageType.ERROR, breakAction);
                            return null;
                        case LAUNCH_WRAPPER:
                            Controllers.dialog(i18n("launch.advice.java9") + "\n" + i18n("launch.advice.uncorrected"), i18n("message.error"), MessageType.ERROR, breakAction);
                            return null;
                    }
                }
            }

            if (Architecture.SYSTEM_ARCH == Architecture.X86_64
                    && javaVersion.getPlatform().getArchitecture() == Architecture.X86) {
                Controllers.dialog(i18n("launch.advice.different_platform"), i18n("message.warning"), MessageType.ERROR, continueAction);
                suggested = true;
            }

            // 32-bit JVM cannot make use of too much memory.
            if (javaVersion.getBits() == Bits.BIT_32 &&
                    setting.getMaxMemory() > 1.5 * 1024) {
                // 1.5 * 1024 is an inaccurate number.
                // Actual memory limit depends on operating system and memory.
                Controllers.confirm(i18n("launch.advice.too_large_memory_for_32bit"), i18n("message.error"), continueAction, breakAction);
                suggested = true;
            }

            if (!suggested && violatedSuggestedConstraint != null) {
                suggested = true;
                switch (violatedSuggestedConstraint) {
                    case MODDED_JAVA_7:
                        Controllers.dialog(i18n("launch.advice.java.modded_java_7"), i18n("message.error"), MessageType.ERROR, continueAction);
                        return null;
                    case MODDED_JAVA_8:
                        Controllers.dialog(i18n("launch.advice.newer_java"), i18n("message.warning"), MessageType.WARNING, continueAction);
                        break;
                    case VANILLA_JAVA_8_51:
                        Controllers.dialog(i18n("launch.advice.java8_51_1_13"), i18n("message.warning"), MessageType.WARNING, continueAction);
                        break;
                }
            }

            // Cannot allocate too much memory exceeding free space.
            if (!suggested && OperatingSystem.TOTAL_MEMORY > 0 && OperatingSystem.TOTAL_MEMORY < setting.getMaxMemory()) {
                Controllers.confirm(i18n("launch.advice.not_enough_space", OperatingSystem.TOTAL_MEMORY), i18n("message.error"), continueAction, null);
                suggested = true;
            }

            // Forge 2760~2773 will crash game with LiteLoader.
            if (!suggested) {
                boolean hasForge2760 = version.getLibraries().stream().filter(it -> it.is("net.minecraftforge", "forge"))
                        .anyMatch(it ->
                                VersionNumber.VERSION_COMPARATOR.compare("1.12.2-14.23.5.2760", it.getVersion()) <= 0 &&
                                        VersionNumber.VERSION_COMPARATOR.compare(it.getVersion(), "1.12.2-14.23.5.2773") < 0);
                boolean hasLiteLoader = version.getLibraries().stream().anyMatch(it -> it.is("com.mumfrey", "liteloader"));
                if (hasForge2760 && hasLiteLoader && gameVersion.compareTo(VersionNumber.asVersion("1.12.2")) == 0) {
                    Controllers.confirm(i18n("launch.advice.forge2760_liteloader"), i18n("message.error"), continueAction, null);
                    suggested = true;
                }
            }

            // OptiFine 1.14.4 is not compatible with Forge 28.2.2 and later versions.
            if (!suggested) {
                boolean hasForge28_2_2 = version.getLibraries().stream().filter(it -> it.is("net.minecraftforge", "forge"))
                        .anyMatch(it ->
                                VersionNumber.VERSION_COMPARATOR.compare("1.14.4-28.2.2", it.getVersion()) <= 0);
                boolean hasOptiFine = version.getLibraries().stream().anyMatch(it -> it.is("optifine", "OptiFine"));
                if (hasForge28_2_2 && hasOptiFine && gameVersion.compareTo(VersionNumber.asVersion("1.14.4")) == 0) {
                    Controllers.confirm(i18n("launch.advice.forge28_2_2_optifine"), i18n("message.error"), continueAction, null);
                    suggested = true;
                }
            }

            if (!suggested) {
                future.complete(javaVersion);
            }

            return Task.fromCompletableFuture(future);
        }).withStage("launch.state.java");
    }

    private static CompletableFuture<Void> downloadJava(GameJavaVersion javaVersion, Profile profile) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        TaskExecutorDialogPane javaDownloadingPane = new TaskExecutorDialogPane(it -> {
        });

        TaskExecutor executor = JavaRepository.downloadJava(javaVersion,
                profile.getDependency().getDownloadProvider()).executor(false);
        executor.addTaskListener(new TaskListener() {
            @Override
            public void onStop(boolean success, TaskExecutor executor) {
                super.onStop(success, executor);
                Platform.runLater(() -> {
                    if (!success) {
                        future.completeExceptionally(Optional.ofNullable(executor.getException()).orElseGet(InterruptedException::new));
                    } else {
                        future.complete(null);
                    }
                });
            }
        });

        javaDownloadingPane.setExecutor(executor, true);
        Controllers.dialog(javaDownloadingPane);
        executor.start();


        return future;
    }

    private void checkExit() {
        switch (launcherVisibility) {
            case HIDE_AND_REOPEN:
                Platform.runLater(() -> {
                    Optional.ofNullable(Controllers.getStage())
                            .ifPresent(Stage::show);
                });
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

    /**
     * The managed process listener.
     * Guarantee that one [JavaProcess], one [HMCLProcessListener].
     * Because every time we launched a game, we generates a new [HMCLProcessListener]
     */
    class HMCLProcessListener implements ProcessListener {

        private final HMCLGameRepository repository;
        private final Version version;
        private final Map<String, String> forbiddenTokens;
        private final LaunchOptions launchOptions;
        private ManagedProcess process;
        private boolean lwjgl;
        private LogWindow logWindow;
        private final boolean detectWindow;
        private final LinkedList<Pair<String, Log4jLevel>> logs;
        private final CountDownLatch logWindowLatch = new CountDownLatch(1);
        private final CountDownLatch launchingLatch;

        public HMCLProcessListener(HMCLGameRepository repository, Version version, AuthInfo authInfo, LaunchOptions launchOptions, CountDownLatch launchingLatch, boolean detectWindow) {
            this.repository = repository;
            this.version = version;
            this.launchOptions = launchOptions;
            this.launchingLatch = launchingLatch;
            this.detectWindow = detectWindow;

            if (authInfo == null)
                forbiddenTokens = Collections.emptyMap();
            else
                forbiddenTokens = mapOf(
                        pair(authInfo.getAccessToken(), "<access token>")
                );

            logs = new LinkedList<>();
        }

        @Override
        public void setProcess(ManagedProcess process) {
            this.process = process;

            String command = new CommandBuilder().addAll(process.getCommands()).toString();
            for (Map.Entry<String, String> entry : forbiddenTokens.entrySet())
                command = command.replace(entry.getKey(), entry.getValue());

            LOG.info("Launched process: " + command);

            if (showLogs)
                Platform.runLater(() -> {
                    logWindow = new LogWindow();
                    logWindow.showNormal();
                    logWindowLatch.countDown();
                });
        }

        private void finishLaunch() {
            switch (launcherVisibility) {
                case HIDE_AND_REOPEN:
                    Platform.runLater(() -> {
                        // If application was stopped and execution services did not finish termination,
                        // these codes will be executed.
                        if (Controllers.getStage() != null) {
                            Controllers.getStage().hide();
                            launchingLatch.countDown();
                        }
                    });
                    break;
                case CLOSE:
                    // Never come to here.
                    break;
                case KEEP:
                    Platform.runLater(launchingLatch::countDown);
                    break;
                case HIDE:
                    launchingLatch.countDown();
                    Platform.runLater(() -> {
                        // If application was stopped and execution services did not finish termination,
                        // these codes will be executed.
                        if (Controllers.getStage() != null) {
                            Controllers.getStage().close();
                            Controllers.shutdown();
                            Schedulers.shutdown();
                        }
                    });
                    break;
            }
        }

        @Override
        public synchronized void onLog(String log, Log4jLevel level) {
            String newLog = log;
            for (Map.Entry<String, String> entry : forbiddenTokens.entrySet())
                newLog = newLog.replace(entry.getKey(), entry.getValue());
            String filteredLog = newLog;

            if (level.lessOrEqual(Log4jLevel.ERROR))
                System.err.println(filteredLog);
            else
                System.out.println(filteredLog);

            logs.add(pair(filteredLog, level));
            if (logs.size() > config().getLogLines())
                logs.removeFirst();

            if (showLogs) {
                try {
                    logWindowLatch.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }

                Platform.runLater(() -> logWindow.logLine(filteredLog, level));
            }

            if (!lwjgl && (filteredLog.toLowerCase().contains("lwjgl version") || filteredLog.toLowerCase().contains("lwjgl openal") || !detectWindow)) {
                lwjgl = true;
                finishLaunch();
            }
        }

        @Override
        public void onExit(int exitCode, ExitType exitType) {
            launchingLatch.countDown();

            if (exitType == ExitType.INTERRUPTED)
                return;

            // Game crashed before opening the game window.
            if (!lwjgl) finishLaunch();

            if (exitType != ExitType.NORMAL) {
                repository.markVersionLaunchedAbnormally(version.getId());
                Platform.runLater(() -> new GameCrashWindow(process, exitType, repository, version, launchOptions, logs).show());
            }

            checkExit();
        }

    }

    public static final Queue<ManagedProcess> PROCESSES = new ConcurrentLinkedQueue<>();
    public static void stopManagedProcesses() {
        while (!PROCESSES.isEmpty())
            Optional.ofNullable(PROCESSES.poll()).ifPresent(ManagedProcess::stop);
    }
}
