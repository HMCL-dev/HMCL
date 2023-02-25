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
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.download.LibraryAnalyzer;
import org.jackhuang.hmcl.download.MaintainTask;
import org.jackhuang.hmcl.download.game.*;
import org.jackhuang.hmcl.download.java.JavaRepository;
import org.jackhuang.hmcl.launch.*;
import org.jackhuang.hmcl.mod.ModpackCompletionException;
import org.jackhuang.hmcl.mod.ModpackConfiguration;
import org.jackhuang.hmcl.mod.ModpackProvider;
import org.jackhuang.hmcl.setting.*;
import org.jackhuang.hmcl.task.*;
import org.jackhuang.hmcl.ui.*;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane.MessageType;
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
import java.nio.file.AccessDeniedException;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.stream.Collectors;

import static org.jackhuang.hmcl.setting.ConfigHolder.config;
import static org.jackhuang.hmcl.ui.FXUtils.runInFX;
import static org.jackhuang.hmcl.util.Lang.resolveException;
import static org.jackhuang.hmcl.util.Logging.LOG;
import static org.jackhuang.hmcl.util.Pair.pair;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public final class LauncherHelper {

    private final Profile profile;
    private Account account;
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

    private final TaskExecutorDialogPane launchingStepsPane = new TaskExecutorDialogPane(TaskCancellationAction.NORMAL);

    public Account getAccount() {
        return account;
    }

    public void setAccount(Account account) {
        this.account = account;
    }

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
        AtomicReference<Version> version = new AtomicReference<>(MaintainTask.maintain(repository, repository.getResolvedVersion(selectedVersion)));
        Optional<String> gameVersion = repository.getGameVersion(version.get());
        boolean integrityCheck = repository.unmarkVersionLaunchedAbnormally(selectedVersion);
        CountDownLatch launchingLatch = new CountDownLatch(1);
        List<String> javaAgents = new ArrayList<>(0);

        AtomicReference<JavaVersion> javaVersionRef = new AtomicReference<>();

        TaskExecutor executor = checkGameState(profile, setting, version.get())
                .thenComposeAsync(javaVersion -> {
                    javaVersionRef.set(Objects.requireNonNull(javaVersion));
                    version.set(NativePatcher.patchNative(version.get(), gameVersion.orElse(null), javaVersion, setting));
                    if (setting.isNotCheckGame())
                        return null;
                    return Task.allOf(
                            dependencyManager.checkGameCompletionAsync(version.get(), integrityCheck),
                            Task.composeAsync(() -> {
                                try {
                                    ModpackConfiguration<?> configuration = ModpackHelper.readModpackConfiguration(repository.getModpackConfiguration(selectedVersion));
                                    ModpackProvider provider = ModpackHelper.getProviderByType(configuration.getType());
                                    if (provider == null) return null;
                                    else return provider.createCompletionTask(dependencyManager, selectedVersion);
                                } catch (IOException e) {
                                    return null;
                                }
                            }),
                            Task.composeAsync(() -> {
                                Renderer renderer = setting.getRenderer();
                                if (renderer != Renderer.DEFAULT && OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS) {
                                    Library lib = NativePatcher.getMesaLoader(javaVersion, renderer);
                                    if (lib == null)
                                        return null;
                                    File file = dependencyManager.getGameRepository().getLibraryFile(version.get(), lib);
                                    if (file.getAbsolutePath().indexOf('=') >= 0) {
                                        LOG.warning("Invalid character '=' in the libraries directory path, unable to attach software renderer loader");
                                        return null;
                                    }

                                    String agent = file.getAbsolutePath() + "=" + renderer.name().toLowerCase(Locale.ROOT);

                                    if (GameLibrariesTask.shouldDownloadLibrary(repository, version.get(), lib, integrityCheck)) {
                                        return new LibraryDownloadTask(dependencyManager, file, lib)
                                                .thenRunAsync(() -> javaAgents.add(agent));
                                    } else {
                                        javaAgents.add(agent);
                                        return null;
                                    }
                                } else {
                                    return null;
                                }
                            })
                    );
                }).withStage("launch.state.dependencies")
                .thenComposeAsync(() -> {
                    return gameVersion.map(s -> new GameVerificationFixTask(dependencyManager, s, version.get())).orElse(null);
                })
                .thenComposeAsync(() -> logIn(account).withStage("launch.state.logging_in"))
                .thenComposeAsync(authInfo -> Task.supplyAsync(() -> {
                    LaunchOptions launchOptions = repository.getLaunchOptions(selectedVersion, javaVersionRef.get(), profile.getGameDir(), javaAgents, scriptFile != null);
                    return new HMCLGameLauncher(
                            repository,
                            version.get(),
                            authInfo,
                            launchOptions,
                            launcherVisibility == LauncherVisibility.CLOSE
                                    ? null // Unnecessary to start listening to game process output when close launcher immediately after game launched.
                                    : new HMCLProcessListener(repository, version.get(), authInfo, launchOptions, launchingLatch, gameVersion.isPresent())
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
                            launchingStepsPane.setCancel(new TaskCancellationAction(it -> {
                                process.stop();
                                it.fireEvent(new DialogCloseEvent());
                            }));
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
                                if (ex instanceof ModpackCompletionException) {
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
                                } else if (ex instanceof CommandTooLongException) {
                                    message = i18n("launch.failed.command_too_long");
                                } else if (ex instanceof ExecutionPolicyLimitException) {
                                    Controllers.prompt(new PromptDialogPane.Builder(i18n("launch.failed.execution_policy"),
                                            (result, resolve, reject) -> {
                                                if (CommandBuilder.setExecutionPolicy()) {
                                                    LOG.info("Set the ExecutionPolicy for the scope 'CurrentUser' to 'RemoteSigned'");
                                                    resolve.run();
                                                } else {
                                                    LOG.warning("Failed to set ExecutionPolicy");
                                                    reject.accept(i18n("launch.failed.execution_policy.failed_to_set"));
                                                }
                                            })
                                            .addQuestion(new PromptDialogPane.Builder.HintQuestion(i18n("launch.failed.execution_policy.hint")))
                                    );

                                    return;
                                } else if (ex instanceof AccessDeniedException) {
                                    message = i18n("exception.access_denied", ((AccessDeniedException) ex).getFile());
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
                    GameJavaVersion targetJavaVersion = null;

                    if (org.jackhuang.hmcl.util.platform.Platform.isCompatibleWithX86Java()) {
                        JavaVersionConstraint.VersionRanges range = JavaVersionConstraint.findSuitableJavaVersionRange(gameVersion, version);
                        if (range.getMandatory().contains(VersionNumber.asVersion("17.0.1"))) {
                            targetJavaVersion = GameJavaVersion.JAVA_17;
                        } else if (range.getMandatory().contains(VersionNumber.asVersion("16.0.1"))) {
                            targetJavaVersion = GameJavaVersion.JAVA_16;
                        } else {
                            String java8Version;

                            if (OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS) {
                                java8Version = "1.8.0_51";
                            } else if (OperatingSystem.CURRENT_OS == OperatingSystem.LINUX) {
                                java8Version = "1.8.0_202";
                            } else if (OperatingSystem.CURRENT_OS == OperatingSystem.OSX) {
                                java8Version = "1.8.0_74";
                            } else {
                                java8Version = null;
                            }

                            if (java8Version != null && range.getMandatory().contains(VersionNumber.asVersion(java8Version)))
                                targetJavaVersion = GameJavaVersion.JAVA_8;
                            else
                                targetJavaVersion = null;
                        }
                    }

                    if (targetJavaVersion == null) {
                        Controllers.confirm(i18n("launch.failed.no_accepted_java"), i18n("message.warning"), MessageType.WARNING, continueAction, () -> {
                            future.completeExceptionally(new CancellationException("No accepted java"));
                        });
                    } else {
                        downloadJava(gameVersion.toString(), targetJavaVersion, profile)
                                .thenAcceptAsync(downloadedJavaVersion -> {
                                    future.complete(downloadedJavaVersion);
                                })
                                .exceptionally(throwable -> {
                                    LOG.log(Level.WARNING, "Failed to download java", throwable);
                                    Controllers.confirm(i18n("launch.failed.no_accepted_java"), i18n("message.warning"), MessageType.WARNING, continueAction, () -> {
                                        future.completeExceptionally(new CancellationException("No accepted java"));
                                    });
                                    return null;
                                });
                    }
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
            List<JavaVersionConstraint> violatedSuggestedConstraints = null;

            LibraryAnalyzer analyzer = LibraryAnalyzer.analyze(version);
            for (JavaVersionConstraint constraint : JavaVersionConstraint.ALL) {
                if (constraint.appliesToVersion(gameVersion, version, javaVersion, analyzer)) {
                    if (!constraint.checkJava(gameVersion, version, javaVersion)) {
                        if (constraint.getType() == JavaVersionConstraint.RULE_MANDATORY) {
                            violatedMandatoryConstraint = constraint;
                        } else if (constraint.getType() == JavaVersionConstraint.RULE_SUGGESTED) {
                            if (violatedSuggestedConstraints == null)
                                violatedSuggestedConstraints = new ArrayList<>(1);
                            violatedSuggestedConstraints.add(constraint);
                        }
                    }

                }
            }

            CompletableFuture<JavaVersion> future = new CompletableFuture<>();
            Runnable breakAction = () -> future.completeExceptionally(new CancellationException("Launch operation was cancelled by user"));

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
                            downloadJava(gameVersion.toString(), version.getJavaVersion(), profile)
                                    .thenAcceptAsync(downloadedJavaVersion -> {
                                        setting.setJavaVersion(downloadedJavaVersion);
                                        future.complete(downloadedJavaVersion);
                                    }, Schedulers.javafx())
                                    .whenCompleteAsync((result, throwable) -> {
                                        LOG.log(Level.WARNING, "Failed to download java", throwable);
                                        breakAction.run();
                                    }, Schedulers.javafx());
                            return Task.fromCompletableFuture(future);
                        case VANILLA_JAVA_16:
                            Controllers.confirm(i18n("launch.advice.require_newer_java_version", gameVersion.toString(), 16), i18n("message.warning"),
                                    () -> FXUtils.openLink(OPENJDK_DOWNLOAD_LINK), null);
                            breakAction.run();
                            return Task.fromCompletableFuture(future);
                        case VANILLA_JAVA_17:
                            Controllers.confirm(i18n("launch.advice.require_newer_java_version", gameVersion.toString(), 17), i18n("message.warning"),
                                    () -> FXUtils.openLink(OPENJDK_DOWNLOAD_LINK), null);
                            breakAction.run();
                            return Task.fromCompletableFuture(future);
                        case VANILLA_JAVA_8:
                            Controllers.dialog(i18n("launch.advice.java8_1_13"), i18n("message.error"), MessageType.ERROR, breakAction);
                            return Task.fromCompletableFuture(future);
                        case VANILLA_LINUX_JAVA_8:
                            if (setting.getNativesDirType() == NativesDirectoryType.VERSION_FOLDER) {
                                Controllers.dialog(i18n("launch.advice.vanilla_linux_java_8"), i18n("message.error"), MessageType.ERROR, breakAction);
                                return Task.fromCompletableFuture(future);
                            } else {
                                break;
                            }
                        case LAUNCH_WRAPPER:
                            Controllers.dialog(i18n("launch.advice.java9") + "\n" + i18n("launch.advice.uncorrected"), i18n("message.error"), MessageType.ERROR, breakAction);
                            return Task.fromCompletableFuture(future);
                    }
                }
            }

            List<String> suggestions = new ArrayList<>(0);

            if (Architecture.SYSTEM_ARCH == Architecture.X86_64 && javaVersion.getPlatform().getArchitecture() == Architecture.X86) {
                suggestions.add(i18n("launch.advice.different_platform"));
            }

            // 32-bit JVM cannot make use of too much memory.
            if (javaVersion.getBits() == Bits.BIT_32 && setting.getMaxMemory() > 1.5 * 1024) {
                // 1.5 * 1024 is an inaccurate number.
                // Actual memory limit depends on operating system and memory.
                suggestions.add(i18n("launch.advice.too_large_memory_for_32bit"));
            }

            if (violatedSuggestedConstraints != null) {
                for (JavaVersionConstraint violatedSuggestedConstraint : violatedSuggestedConstraints) {
                    switch (violatedSuggestedConstraint) {
                        case MODDED_JAVA_7:
                            suggestions.add(i18n("launch.advice.java.modded_java_7"));
                            break;
                        case MODDED_JAVA_8:
                            // Minecraft>=1.7.10+Forge accepts Java 8
                            if (javaVersion.getParsedVersion() < 8)
                                suggestions.add(i18n("launch.advice.newer_java"));
                            else
                                suggestions.add(i18n("launch.advice.modded_java", 8, gameVersion));
                            break;
                        case MODDED_JAVA_16:
                            // Minecraft<=1.17.1+Forge[37.0.0,37.0.60) not compatible with Java 17
                            String forgePatchVersion = analyzer.getVersion(LibraryAnalyzer.LibraryType.FORGE)
                                    .map(LibraryAnalyzer.LibraryType.FORGE::patchVersion)
                                    .orElse(null);
                            if (forgePatchVersion != null && VersionNumber.VERSION_COMPARATOR.compare(forgePatchVersion, "37.0.60") < 0)
                                suggestions.add(i18n("launch.advice.forge37_0_60"));
                            else
                                suggestions.add(i18n("launch.advice.modded_java", 16, gameVersion));
                            break;
                        case MODDED_JAVA_17:
                            suggestions.add(i18n("launch.advice.modded_java", 17, gameVersion));
                            break;
                        case VANILLA_JAVA_8_51:
                            suggestions.add(i18n("launch.advice.java8_51_1_13"));
                            break;
                        case MODLAUNCHER_8:
                            suggestions.add(i18n("launch.advice.modlauncher8"));
                            break;
                        case VANILLA_X86:
                            if (setting.getNativesDirType() == NativesDirectoryType.VERSION_FOLDER
                                    && org.jackhuang.hmcl.util.platform.Platform.isCompatibleWithX86Java()) {
                                suggestions.add(i18n("launch.advice.vanilla_x86.translation"));
                            }
                            break;
                    }
                }
            }

            // Cannot allocate too much memory exceeding free space.
            if (OperatingSystem.TOTAL_MEMORY > 0 && OperatingSystem.TOTAL_MEMORY < setting.getMaxMemory()) {
                suggestions.add(i18n("launch.advice.not_enough_space", OperatingSystem.TOTAL_MEMORY));
            }

            VersionNumber forgeVersion = version.getLibraries().stream()
                    .filter(it -> it.is("net.minecraftforge", "forge"))
                    .findFirst()
                    .map(library -> VersionNumber.asVersion(library.getVersion()))
                    .orElse(null);

            // Forge 2760~2773 will crash game with LiteLoader.
            boolean hasForge2760 = forgeVersion != null && (forgeVersion.compareTo("1.12.2-14.23.5.2760") >= 0) && (forgeVersion.compareTo("1.12.2-14.23.5.2773") < 0);
            boolean hasLiteLoader = version.getLibraries().stream().anyMatch(it -> it.is("com.mumfrey", "liteloader"));
            if (hasForge2760 && hasLiteLoader && gameVersion.compareTo(VersionNumber.asVersion("1.12.2")) == 0) {
                suggestions.add(i18n("launch.advice.forge2760_liteloader"));
            }

            // OptiFine 1.14.4 is not compatible with Forge 28.2.2 and later versions.
            boolean hasForge28_2_2 = forgeVersion != null && (forgeVersion.compareTo("1.14.4-28.2.2") >= 0);
            boolean hasOptiFine = version.getLibraries().stream().anyMatch(it -> it.is("optifine", "OptiFine"));
            if (hasForge28_2_2 && hasOptiFine && gameVersion.compareTo(VersionNumber.asVersion("1.14.4")) == 0) {
                suggestions.add(i18n("launch.advice.forge28_2_2_optifine"));
            }

            if (suggestions.isEmpty()) {
                if (!future.isDone()) {
                    future.complete(javaVersion);
                }
            } else {
                String message;
                if (suggestions.size() == 1) {
                    message = i18n("launch.advice", suggestions.get(0));
                } else {
                    message = i18n("launch.advice.multi", suggestions.stream().map(it -> "→ " + it).collect(Collectors.joining("\n")));
                }

                Controllers.confirm(message, i18n("message.warning"), MessageType.WARNING, () -> future.complete(javaVersion), breakAction);
            }

            return Task.fromCompletableFuture(future);
        }).withStage("launch.state.java");
    }

    private static CompletableFuture<JavaVersion> downloadJava(String gameVersion, GameJavaVersion javaVersion, Profile profile) {
        CompletableFuture<JavaVersion> future = new CompletableFuture<>();

        JFXHyperlink link = new JFXHyperlink(i18n("download.external_link"));
        link.setOnAction(e -> {
            if (javaVersion.getMajorVersion() == JavaVersion.JAVA_8) {
                FXUtils.openLink(ORACLEJDK_DOWNLOAD_LINK);
            } else {
                FXUtils.openLink(OPENJDK_DOWNLOAD_LINK);
            }
            future.completeExceptionally(new CancellationException());
        });

        Controllers.dialog(new MessageDialogPane.Builder(
                i18n("launch.advice.require_newer_java_version",
                        gameVersion,
                        javaVersion.getMajorVersion()),
                i18n("message.warning"),
                MessageType.QUESTION)
                .addAction(link)
                .yesOrNo(() -> {
                    downloadJavaImpl(javaVersion, profile.getDependency().getDownloadProvider())
                            .thenAcceptAsync(future::complete)
                            .exceptionally(throwable -> {
                                Throwable resolvedException = resolveException(throwable);
                                LOG.log(Level.WARNING, "Failed to download java", throwable);
                                if (!(resolvedException instanceof CancellationException)) {
                                    Controllers.dialog(DownloadProviders.localizeErrorMessage(resolvedException), i18n("install.failed"));
                                }
                                future.completeExceptionally(new CancellationException());
                                return null;
                            });
                }, () -> future.completeExceptionally(new CancellationException())).build());

        return future;
    }

    /**
     * Directly start java downloading.
     *
     * @param javaVersion target Java version
     * @param downloadProvider download provider
     * @return JavaVersion, null if we failed to download java, failed if an error occurred when downloading.
     */
    private static CompletableFuture<JavaVersion> downloadJavaImpl(GameJavaVersion javaVersion, DownloadProvider downloadProvider) {
        CompletableFuture<JavaVersion> future = new CompletableFuture<>();

        Controllers.taskDialog(JavaRepository.downloadJava(javaVersion, downloadProvider)
                .whenComplete(Schedulers.javafx(), (downloadedJava, exception) -> {
                    if (exception != null) {
                        future.completeExceptionally(exception);
                    } else {
                        future.complete(downloadedJava);
                    }
                }), i18n("download.java"), TaskCancellationAction.NORMAL);

        return future;
    }

    private static Task<AuthInfo> logIn(Account account) {
        return Task.composeAsync(() -> {
            try {
                return Task.completed(account.logIn());
            } catch (CredentialExpiredException e) {
                LOG.log(Level.INFO, "Credential has expired", e);

                return Task.completed(DialogController.logIn(account));
            } catch (AuthenticationException e) {
                LOG.log(Level.WARNING, "Authentication failed, try skipping refresh", e);

                CompletableFuture<Task<AuthInfo>> future = new CompletableFuture<>();
                runInFX(() -> {
                    JFXButton loginOfflineButton = new JFXButton(i18n("account.login.skip"));
                    loginOfflineButton.setOnAction(event -> {
                        try {
                            future.complete(Task.completed(account.playOffline()));
                        } catch (AuthenticationException e2) {
                            future.completeExceptionally(e2);
                        }
                    });
                    JFXButton retryButton = new JFXButton(i18n("account.login.retry"));
                    retryButton.setOnAction(event -> {
                        future.complete(logIn(account));
                    });
                    Controllers.dialog(new MessageDialogPane.Builder(i18n("account.failed.server_disconnected"), i18n("account.failed"), MessageType.ERROR)
                            .addAction(loginOfflineButton)
                            .addAction(retryButton)
                            .addCancel(() ->
                                    future.completeExceptionally(new CancellationException()))
                            .build());
                });
                return Task.fromCompletableFuture(future).thenComposeAsync(task -> task);
            }
        });
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
    private final class HMCLProcessListener implements ProcessListener {

        private final HMCLGameRepository repository;
        private final Version version;
        private final LaunchOptions launchOptions;
        private ManagedProcess process;
        private boolean lwjgl;
        private LogWindow logWindow;
        private final boolean detectWindow;
        private final ArrayDeque<String> logs;
        private final ArrayDeque</*Log4jLevel*/Object> levels;
        private final CountDownLatch logWindowLatch = new CountDownLatch(1);
        private final CountDownLatch launchingLatch;
        private final String forbiddenAccessToken;

        public HMCLProcessListener(HMCLGameRepository repository, Version version, AuthInfo authInfo, LaunchOptions launchOptions, CountDownLatch launchingLatch, boolean detectWindow) {
            this.repository = repository;
            this.version = version;
            this.launchOptions = launchOptions;
            this.launchingLatch = launchingLatch;
            this.detectWindow = detectWindow;
            this.forbiddenAccessToken = authInfo != null ? authInfo.getAccessToken() : null;

            final int numLogs = config().getLogLines() + 1;
            this.logs = new ArrayDeque<>(numLogs);
            this.levels = new ArrayDeque<>(numLogs);
        }

        @Override
        public void setProcess(ManagedProcess process) {
            this.process = process;

            String command = new CommandBuilder().addAll(process.getCommands()).toString();

            LOG.info("Launched process: " + command);

            String classpath = process.getClasspath();
            if (classpath != null) {
                LOG.info("Process ClassPath: " + classpath);
            }

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
                            System.gc();
                        }
                    });
                    break;
            }
        }

        @Override
        public void onLog(String log, boolean isErrorStream) {
            String filteredLog = forbiddenAccessToken == null ? log : log.replace(forbiddenAccessToken, "<access token>");

            if (isErrorStream)
                System.err.println(filteredLog);
            else
                System.out.println(filteredLog);

            Log4jLevel level;
            if (isErrorStream)
                level = Log4jLevel.ERROR;
            else
                level = showLogs ? Optional.ofNullable(Log4jLevel.guessLevel(filteredLog)).orElse(Log4jLevel.INFO) : null;

            synchronized (this) {
                logs.add(filteredLog);
                levels.add(level != null ? level : Optional.empty()); // Use 'Optional.empty()' as hole
                if (logs.size() > config().getLogLines()) {
                    logs.removeFirst();
                    levels.removeFirst();
                }
            }

            if (showLogs) {
                try {
                    logWindowLatch.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }

                Platform.runLater(() -> logWindow.logLine(filteredLog, level));
            }

            if (!lwjgl) {
                String lowerCaseLog = filteredLog.toLowerCase(Locale.ROOT);
                if (!detectWindow || lowerCaseLog.contains("lwjgl version") || lowerCaseLog.contains("lwjgl openal")) {
                    lwjgl = true;
                    finishLaunch();
                }
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
                ArrayList<Pair<String, Log4jLevel>> pairs = new ArrayList<>(logs.size());
                Lang.forEachZipped(logs, levels,
                        (log, l) -> pairs.add(pair(log, l instanceof Log4jLevel ? ((Log4jLevel) l) : Optional.ofNullable(Log4jLevel.guessLevel(log)).orElse(Log4jLevel.INFO))));
                repository.markVersionLaunchedAbnormally(version.getId());
                Platform.runLater(() -> new GameCrashWindow(process, exitType, repository, version, launchOptions, pairs).show());
            }

            checkExit();
        }

    }

    private static final String ORACLEJDK_DOWNLOAD_LINK = "https://www.java.com/download";
    private static final String OPENJDK_DOWNLOAD_LINK = "https://docs.microsoft.com/java/openjdk/download";

    public static final Queue<ManagedProcess> PROCESSES = new ConcurrentLinkedQueue<>();

    public static void stopManagedProcesses() {
        while (!PROCESSES.isEmpty())
            Optional.ofNullable(PROCESSES.poll()).ifPresent(ManagedProcess::stop);
    }
}
