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
import org.jackhuang.hmcl.download.LibraryAnalyzer;
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

    public void launch() {
        Logging.LOG.info("Launching game version: " + selectedVersion);

        GameRepository repository = profile.getRepository();
        Version version = repository.getResolvedVersion(selectedVersion);

        Platform.runLater(() -> {
            try {
                checkGameState(profile, setting, version, () -> {
                    Controllers.dialog(launchingStepsPane);
                    Schedulers.defaultScheduler().execute(this::launch0);
                });
            } catch (InterruptedException | RejectedExecutionException ignore) {
            }
        });
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

        TaskExecutor executor = dependencyManager.checkPatchCompletionAsync(repository.getVersion(selectedVersion), integrityCheck)
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
                    LaunchOptions launchOptions = repository.getLaunchOptions(selectedVersion, profile.getGameDir(), !setting.isNotCheckJVM());
                    return new HMCLGameLauncher(
                            repository,
                            version,
                            authInfo,
                            launchOptions,
                            launcherVisibility == LauncherVisibility.CLOSE
                                    ? null // Unnecessary to start listening to game process output when close launcher immediately after game launched.
                                    : new HMCLProcessListener(repository, selectedVersion, authInfo, launchOptions, launchingLatch, gameVersion.isPresent())
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

    private static void checkGameState(Profile profile, VersionSetting setting, Version version, Runnable onAccept) throws InterruptedException {
        if (setting.isNotCheckJVM()) {
            onAccept.run();
            return;
        }

        boolean javaChanged = false;
        boolean mayContinueAfterJavaChanged = false;
        boolean java8required = false;
        boolean newJavaRequired = false;

        // Without onAccept called, the launching operation will be terminated.

        VersionNumber gameVersion = VersionNumber.asVersion(profile.getRepository().getGameVersion(version).orElse("Unknown"));
        if (setting.getJavaVersion() == null) {
            Controllers.dialog(i18n("launch.wrong_javadir"), i18n("message.warning"), MessageType.WARNING, onAccept);
            setting.setJava(null);
            setting.setDefaultJavaPath(null);
            setting.setJavaVersion(JavaVersion.fromCurrentEnvironment());
            // continue java version checking
        }

        // Check java version recorded in game json
        // We only checks for 1.7.10 and above, since 1.7.2 with Forge can only run on Java 7, but it is recorded Java 8 in game json, which is not correct.
        if (!javaChanged && version.getJavaVersion() != null && gameVersion.compareTo(VersionNumber.asVersion("1.7.10")) >= 0) {
            if (setting.getJavaVersion().getParsedVersion() < version.getJavaVersion().getMajorVersion()) {
                Optional<JavaVersion> acceptableJava = JavaVersion.getJavas().stream()
                        .filter(javaVersion -> javaVersion.getParsedVersion() >= version.getJavaVersion().getMajorVersion())
                        .max(Comparator.comparing(JavaVersion::getVersionNumber));
                if (acceptableJava.isPresent()) {
                    setting.setJavaVersion(acceptableJava.get());
                    mayContinueAfterJavaChanged = true;
                } else {
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
                                                .filter(javaVersion -> javaVersion.getParsedVersion() >= version.getJavaVersion().getMajorVersion())
                                                .max(Comparator.comparing(JavaVersion::getVersionNumber));
                                        newAcceptableJava.ifPresent(setting::setJavaVersion);
                                    } catch (InterruptedException e) {
                                        LOG.log(Level.SEVERE, "Cannot list javas", e);
                                    }
                                }, Platform::runLater).thenAccept(x -> onAccept.run());
                    });
                    yesButton.getStyleClass().add("dialog-accept");
                    dialog.addButton(yesButton);

                    JFXButton noButton = new JFXButton(i18n("button.cancel"));
                    noButton.getStyleClass().add("dialog-cancel");
                    dialog.addButton(noButton);
                    dialog.setCancelButton(noButton);

                    Controllers.dialog(dialog);
                    return;
                }
            }
        }

        // Game later than 1.17 requires Java 16.
        if (!javaChanged && setting.getJavaVersion().getParsedVersion() < JavaVersion.JAVA_16 && gameVersion.compareTo(VersionNumber.asVersion("1.17")) >= 0) {
            Optional<JavaVersion> acceptableJava = JavaVersion.getJavas().stream()
                    .filter(javaVersion -> javaVersion.getParsedVersion() >= JavaVersion.JAVA_16)
                    .max(Comparator.comparing(JavaVersion::getVersionNumber));
            if (acceptableJava.isPresent()) {
                setting.setJavaVersion(acceptableJava.get());
                mayContinueAfterJavaChanged = true;
            } else {
                Controllers.confirm(i18n("launch.advice.require_newer_java_version", gameVersion.toString(), 16), i18n("message.warning"), () -> {
                    FXUtils.openLink("https://adoptopenjdk.net/");
                }, null);
            }
            javaChanged = true;
        }

        // Game later than 1.7.2 accepts Java 8.
        if (!javaChanged && setting.getJavaVersion().getParsedVersion() < JavaVersion.JAVA_8 && gameVersion.compareTo(VersionNumber.asVersion("1.7.2")) > 0) {
            Optional<JavaVersion> java8 = JavaVersion.getJavas().stream()
                    .filter(javaVersion -> javaVersion.getParsedVersion() >= JavaVersion.JAVA_8)
                    .max(Comparator.comparing(JavaVersion::getVersionNumber));
            if (java8.isPresent()) {
                newJavaRequired = true;
                setting.setJavaVersion(java8.get());
            } else {
                if (gameVersion.compareTo(VersionNumber.asVersion("1.13")) >= 0) {
                    // Minecraft 1.13 and later versions only support Java 8 or later.
                    // Terminate launching operation.
                    Controllers.dialog(i18n("launch.advice.java8_1_13"), i18n("message.error"), MessageType.ERROR, null);
                } else {
                    // Most mods require Java 8 or later version.
                    Controllers.dialog(i18n("launch.advice.newer_java"), i18n("message.warning"), MessageType.WARNING, onAccept);
                }
                javaChanged = true;
            }
        }

        // LaunchWrapper 1.12 will crash because of assuming the system class loader is an instance of URLClassLoader.
        if (!javaChanged && setting.getJavaVersion().getParsedVersion() >= JavaVersion.JAVA_9
                && gameVersion.compareTo(VersionNumber.asVersion("1.13")) < 0
                && LibraryAnalyzer.LAUNCH_WRAPPER_MAIN.equals(version.getMainClass())
                && version.getLibraries().stream()
                .filter(library -> "launchwrapper".equals(library.getArtifactId()))
                .anyMatch(library -> VersionNumber.asVersion(library.getVersion()).compareTo(VersionNumber.asVersion("1.13")) < 0)) {
            Optional<JavaVersion> java8 = JavaVersion.getJavas().stream().filter(javaVersion -> javaVersion.getParsedVersion() == JavaVersion.JAVA_8).findAny();
            if (java8.isPresent()) {
                java8required = true;
                setting.setJavaVersion(java8.get());
                Controllers.dialog(i18n("launch.advice.java9") + "\n" + i18n("launch.advice.corrected"), i18n("message.info"), MessageType.INFORMATION, onAccept);
                javaChanged = true;
            } else {
                Controllers.dialog(i18n("launch.advice.java9") + "\n" + i18n("launch.advice.uncorrected"), i18n("message.error"), MessageType.ERROR, null);
                javaChanged = true;
            }
        }

        // Minecraft 1.13 may crash when generating world on Java 8 earlier than 1.8.0_51
        VersionNumber JAVA_8 = VersionNumber.asVersion("1.8.0_51");
        if (!javaChanged && gameVersion.compareTo(VersionNumber.asVersion("1.13")) >= 0 && setting.getJavaVersion().getParsedVersion() == JavaVersion.JAVA_8 && setting.getJavaVersion().getVersionNumber().compareTo(JAVA_8) < 0) {
            Optional<JavaVersion> java8 = JavaVersion.getJavas().stream()
                    .filter(javaVersion -> javaVersion.getVersionNumber().compareTo(JAVA_8) >= 0)
                    .max(Comparator.comparing(JavaVersion::getVersionNumber));
            if (java8.isPresent()) {
                newJavaRequired = true;
                setting.setJavaVersion(java8.get());
            } else {
                Controllers.dialog(i18n("launch.advice.java8_51_1_13"), i18n("message.warning"), MessageType.WARNING, onAccept);
                javaChanged = true;
            }
        }

        if (!javaChanged && setting.getJavaVersion().getPlatform() == org.jackhuang.hmcl.util.platform.Platform.BIT_32 &&
                Architecture.CURRENT.getPlatform() == org.jackhuang.hmcl.util.platform.Platform.BIT_64) {
            final JavaVersion java32 = setting.getJavaVersion();

            // First find if same java version but whose platform is 64-bit installed.
            Optional<JavaVersion> java64 = JavaVersion.getJavas().stream()
                    .filter(javaVersion -> javaVersion.getPlatform() == org.jackhuang.hmcl.util.platform.Platform.getPlatform())
                    .filter(javaVersion -> javaVersion.getParsedVersion() == java32.getParsedVersion())
                    .max(Comparator.comparing(JavaVersion::getVersionNumber));

            if (!java64.isPresent()) {
                final boolean java8requiredFinal = java8required, newJavaRequiredFinal = newJavaRequired;

                // Then find if other java version which satisfies requirements installed.
                java64 = JavaVersion.getJavas().stream()
                        .filter(javaVersion -> javaVersion.getPlatform() == org.jackhuang.hmcl.util.platform.Platform.getPlatform())
                        .filter(javaVersion -> {
                            if (java8requiredFinal) return javaVersion.getParsedVersion() == JavaVersion.JAVA_8;
                            if (newJavaRequiredFinal) return javaVersion.getParsedVersion() >= JavaVersion.JAVA_8;
                            return true;
                        })
                        .max(Comparator.comparing(JavaVersion::getVersionNumber));
            }

            if (java64.isPresent()) {
                setting.setJavaVersion(java64.get());
            } else {
                Controllers.dialog(i18n("launch.advice.different_platform"), i18n("message.error"), MessageType.ERROR, onAccept);
                javaChanged = true;
            }
        }

        // 32-bit JVM cannot make use of too much memory.
        if (!javaChanged && setting.getJavaVersion().getPlatform() == org.jackhuang.hmcl.util.platform.Platform.BIT_32 &&
                setting.getMaxMemory() > 1.5 * 1024) {
            // 1.5 * 1024 is an inaccurate number.
            // Actual memory limit depends on operating system and memory.
            Controllers.confirm(i18n("launch.advice.too_large_memory_for_32bit"), i18n("message.error"), onAccept, null);
            javaChanged = true;
        }

        // Cannot allocate too much memory exceeding free space.
        if (!javaChanged && OperatingSystem.TOTAL_MEMORY > 0 && OperatingSystem.TOTAL_MEMORY < setting.getMaxMemory()) {
            Controllers.confirm(i18n("launch.advice.not_enough_space", OperatingSystem.TOTAL_MEMORY), i18n("message.error"), onAccept, null);
            javaChanged = true;
        }

        // Forge 2760~2773 will crash game with LiteLoader.
        if (!javaChanged) {
            boolean hasForge2760 = version.getLibraries().stream().filter(it -> it.is("net.minecraftforge", "forge"))
                    .anyMatch(it ->
                            VersionNumber.VERSION_COMPARATOR.compare("1.12.2-14.23.5.2760", it.getVersion()) <= 0 &&
                                    VersionNumber.VERSION_COMPARATOR.compare(it.getVersion(), "1.12.2-14.23.5.2773") < 0);
            boolean hasLiteLoader = version.getLibraries().stream().anyMatch(it -> it.is("com.mumfrey", "liteloader"));
            if (hasForge2760 && hasLiteLoader && gameVersion.compareTo(VersionNumber.asVersion("1.12.2")) == 0) {
                Controllers.confirm(i18n("launch.advice.forge2760_liteloader"), i18n("message.error"), onAccept, null);
                javaChanged = true;
            }
        }

        // OptiFine 1.14.4 is not compatible with Forge 28.2.2 and later versions.
        if (!javaChanged) {
            boolean hasForge28_2_2 = version.getLibraries().stream().filter(it -> it.is("net.minecraftforge", "forge"))
                    .anyMatch(it ->
                            VersionNumber.VERSION_COMPARATOR.compare("1.14.4-28.2.2", it.getVersion()) <= 0);
            boolean hasOptiFine = version.getLibraries().stream().anyMatch(it -> it.is("optifine", "OptiFine"));
            if (hasForge28_2_2 && hasOptiFine && gameVersion.compareTo(VersionNumber.asVersion("1.14.4")) == 0) {
                Controllers.confirm(i18n("launch.advice.forge28_2_2_optifine"), i18n("message.error"), onAccept, null);
                javaChanged = true;
            }
        }

        if (!javaChanged || mayContinueAfterJavaChanged)
            onAccept.run();
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
        private final String version;
        private final Map<String, String> forbiddenTokens;
        private final LaunchOptions launchOptions;
        private ManagedProcess process;
        private boolean lwjgl;
        private LogWindow logWindow;
        private final boolean detectWindow;
        private final LinkedList<Pair<String, Log4jLevel>> logs;
        private final CountDownLatch logWindowLatch = new CountDownLatch(1);
        private final CountDownLatch launchingLatch;

        public HMCLProcessListener(HMCLGameRepository repository, String version, AuthInfo authInfo, LaunchOptions launchOptions, CountDownLatch launchingLatch, boolean detectWindow) {
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
                repository.markVersionLaunchedAbnormally(version);
                Platform.runLater(() -> new GameCrashWindow(process, exitType, repository, launchOptions, logs).show());
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
