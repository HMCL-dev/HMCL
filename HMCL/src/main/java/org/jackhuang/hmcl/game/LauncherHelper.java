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
import javafx.stage.Stage;
import org.jackhuang.hmcl.Launcher;
import org.jackhuang.hmcl.auth.*;
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorDownloadException;
import org.jackhuang.hmcl.auth.offline.OfflineAccount;
import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.download.LibraryAnalyzer;
import org.jackhuang.hmcl.download.MaintainTask;
import org.jackhuang.hmcl.download.game.*;
import org.jackhuang.hmcl.java.JavaManager;
import org.jackhuang.hmcl.java.JavaRuntime;
import org.jackhuang.hmcl.launch.*;
import org.jackhuang.hmcl.mod.ModpackCompletionException;
import org.jackhuang.hmcl.mod.ModpackConfiguration;
import org.jackhuang.hmcl.mod.ModpackProvider;
import org.jackhuang.hmcl.setting.*;
import org.jackhuang.hmcl.task.*;
import org.jackhuang.hmcl.ui.*;
import org.jackhuang.hmcl.ui.construct.DialogCloseEvent;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane.MessageType;
import org.jackhuang.hmcl.ui.construct.PromptDialogPane;
import org.jackhuang.hmcl.ui.construct.TaskExecutorDialogPane;
import org.jackhuang.hmcl.util.*;
import org.jackhuang.hmcl.util.i18n.I18n;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.io.ResponseCodeException;
import org.jackhuang.hmcl.util.platform.*;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;
import org.jackhuang.hmcl.util.versioning.VersionNumber;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.file.AccessDeniedException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import static javafx.application.Platform.runLater;
import static javafx.application.Platform.setImplicitExit;
import static org.jackhuang.hmcl.ui.FXUtils.runInFX;
import static org.jackhuang.hmcl.util.DataSizeUnit.MEGABYTES;
import static org.jackhuang.hmcl.util.Lang.resolveException;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;
import static org.jackhuang.hmcl.util.platform.Platform.SYSTEM_PLATFORM;
import static org.jackhuang.hmcl.util.platform.Platform.isCompatibleWithX86Java;

public final class LauncherHelper {

    private final Profile profile;
    private Account account;
    private final String selectedVersion;
    private Path scriptFile;
    private final VersionSetting setting;
    private LauncherVisibility launcherVisibility;
    private boolean showLogs;
    private QuickPlayOption quickPlayOption;
    private boolean disableOfflineSkin = false;

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

    public void setQuickPlayOption(QuickPlayOption quickPlayOption) {
        this.quickPlayOption = quickPlayOption;
    }

    public void setDisableOfflineSkin() {
        disableOfflineSkin = true;
    }

    public void launch() {
        FXUtils.checkFxUserThread();

        LOG.info("Launching game version: " + selectedVersion);

        Controllers.dialog(launchingStepsPane);
        launch0();
    }

    public void makeLaunchScript(Path scriptFile) {
        this.scriptFile = Objects.requireNonNull(scriptFile);
        launch();
    }

    private void launch0() {
        // https://github.com/HMCL-dev/HMCL/pull/4121
        PROCESSES.removeIf(it -> it.get() == null);

        HMCLGameRepository repository = profile.getRepository();
        DefaultDependencyManager dependencyManager = profile.getDependency();
        AtomicReference<Version> version = new AtomicReference<>(MaintainTask.maintain(repository, repository.getResolvedVersion(selectedVersion)));
        Optional<String> gameVersion = repository.getGameVersion(version.get());
        boolean integrityCheck = repository.unmarkVersionLaunchedAbnormally(selectedVersion);
        CountDownLatch launchingLatch = new CountDownLatch(1);
        List<String> javaAgents = new ArrayList<>(0);
        List<String> javaArguments = new ArrayList<>(0);

        AtomicReference<JavaRuntime> javaVersionRef = new AtomicReference<>();

        TaskExecutor executor = checkGameState(profile, setting, version.get())
                .thenComposeAsync(java -> {
                    javaVersionRef.set(Objects.requireNonNull(java));
                    version.set(NativePatcher.patchNative(repository, version.get(), gameVersion.orElse(null), java, setting, javaArguments));
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
                                    Library lib = NativePatcher.getWindowsMesaLoader(java, renderer, OperatingSystem.SYSTEM_VERSION);
                                    if (lib == null)
                                        return null;
                                    Path file = dependencyManager.getGameRepository().getLibraryFile(version.get(), lib);
                                    if (file.toAbsolutePath().toString().indexOf('=') >= 0) {
                                        LOG.warning("Invalid character '=' in the libraries directory path, unable to attach software renderer loader");
                                        return null;
                                    }

                                    String agent = FileUtils.getAbsolutePath(file) + "=" + renderer.name().toLowerCase(Locale.ROOT);

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
                .thenComposeAsync(() -> gameVersion.map(s -> new GameVerificationFixTask(dependencyManager, s, version.get())).orElse(null))
                .thenComposeAsync(() -> logIn(account).withStage("launch.state.logging_in"))
                .thenComposeAsync(authInfo -> Task.supplyAsync(() -> {
                    LaunchOptions.Builder launchOptionsBuilder = repository.getLaunchOptions(
                            selectedVersion, javaVersionRef.get(), profile.getGameDir(), javaAgents, javaArguments, scriptFile != null);
                    if (disableOfflineSkin) {
                        launchOptionsBuilder.setDaemon(false);
                    }
                    if (quickPlayOption != null) {
                        launchOptionsBuilder.setQuickPlayOption(quickPlayOption);
                    }
                    LaunchOptions launchOptions = launchOptionsBuilder.create();

                    LOG.info("Here's the structure of game mod directory:\n" + FileUtils.printFileStructure(repository.getModsDirectory(selectedVersion), 10));

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
                        PROCESSES.add(new WeakReference<>(process));
                        if (launcherVisibility == LauncherVisibility.CLOSE)
                            Launcher.stopApplication();
                        else
                            launchingStepsPane.setCancel(new TaskCancellationAction(it -> {
                                process.stop();
                                it.fireEvent(new DialogCloseEvent());
                            }));
                    } else {
                        runLater(() -> {
                            launchingStepsPane.fireEvent(new DialogCloseEvent());
                            Controllers.dialog(i18n("version.launch_script.success", FileUtils.getAbsolutePath(scriptFile)));
                        });
                    }
                }).withFakeProgress(
                        i18n("message.doing"),
                        () -> launchingLatch.getCount() == 0, 6.95
                ).withStage("launch.state.waiting_launching"))
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
                runLater(() -> {
                    // Check if the application has stopped
                    // because onStop will be invoked if tasks fail when the executor service shut down.
                    if (!Controllers.isStopped()) {
                        launchingStepsPane.fireEvent(new DialogCloseEvent());
                        if (!success) {
                            Exception ex = executor.getException();
                            if (ex != null && !(ex instanceof CancellationException)) {
                                String message;
                                if (ex instanceof ModpackCompletionException) {
                                    if (ex.getCause() instanceof FileNotFoundException)
                                        message = i18n("modpack.type.curse.not_found");
                                    else
                                        message = i18n("modpack.type.curse.error");
                                } else if (ex instanceof PermissionException) {
                                    message = i18n("launch.failed.executable_permission");
                                } else if (ex instanceof ProcessCreationException) {
                                    message = i18n("launch.failed.creating_process") + "\n" + ex.getLocalizedMessage();
                                } else if (ex instanceof NotDecompressingNativesException) {
                                    message = i18n("launch.failed.decompressing_natives") + "\n" + ex.getLocalizedMessage();
                                } else if (ex instanceof LibraryDownloadException) {
                                    message = i18n("launch.failed.download_library", ((LibraryDownloadException) ex).getLibrary().getName()) + "\n";
                                    if (ex.getCause() instanceof ResponseCodeException) {
                                        ResponseCodeException rce = (ResponseCodeException) ex.getCause();
                                        int responseCode = rce.getResponseCode();
                                        String uri = rce.getUri();
                                        if (responseCode == 404)
                                            message += i18n("download.code.404", uri);
                                        else
                                            message += i18n("download.failed", uri, responseCode);
                                    } else {
                                        message += StringUtils.getStackTrace(ex.getCause());
                                    }
                                } else if (ex instanceof DownloadException) {
                                    URI uri = ((DownloadException) ex).getUri();
                                    if (ex.getCause() instanceof SocketTimeoutException) {
                                        message = i18n("install.failed.downloading.timeout", uri);
                                    } else if (ex.getCause() instanceof ResponseCodeException) {
                                        ResponseCodeException responseCodeException = (ResponseCodeException) ex.getCause();
                                        if (I18n.hasKey("download.code." + responseCodeException.getResponseCode())) {
                                            message = i18n("download.code." + responseCodeException.getResponseCode(), uri);
                                        } else {
                                            message = i18n("install.failed.downloading.detail", uri) + "\n" + StringUtils.getStackTrace(ex.getCause());
                                        }
                                    } else {
                                        message = i18n("install.failed.downloading.detail", uri) + "\n" + StringUtils.getStackTrace(ex.getCause());
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
                                    String uri = rce.getUri();
                                    if (responseCode == 404)
                                        message = i18n("download.code.404", uri);
                                    else
                                        message = i18n("download.failed", uri, responseCode);
                                } else if (ex instanceof CommandTooLongException) {
                                    message = i18n("launch.failed.command_too_long");
                                } else if (ex instanceof ExecutionPolicyLimitException) {
                                    Controllers.prompt(new PromptDialogPane.Builder(i18n("launch.failed.execution_policy"),
                                            (result, handler) -> {
                                                if (CommandBuilder.setExecutionPolicy()) {
                                                    LOG.info("Set the ExecutionPolicy for the scope 'CurrentUser' to 'RemoteSigned'");
                                                    handler.resolve();
                                                } else {
                                                    LOG.warning("Failed to set ExecutionPolicy");
                                                    handler.reject(i18n("launch.failed.execution_policy.failed_to_set"));
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

    private static Task<JavaRuntime> checkGameState(Profile profile, VersionSetting setting, Version version) {
        LibraryAnalyzer analyzer = LibraryAnalyzer.analyze(version, profile.getRepository().getGameVersion(version).orElse(null));
        GameVersionNumber gameVersion = GameVersionNumber.asGameVersion(analyzer.getVersion(LibraryAnalyzer.LibraryType.MINECRAFT));

        Task<JavaRuntime> getJavaTask = Task.supplyAsync(() -> {
            try {
                return setting.getJava(gameVersion, version);
            } catch (InterruptedException e) {
                throw new CancellationException();
            }
        });
        Task<JavaRuntime> task;
        if (setting.isNotCheckJVM()) {
            task = getJavaTask.thenApplyAsync(java -> Lang.requireNonNullElse(java, JavaRuntime.getDefault()));
        } else if (setting.getJavaVersionType() == JavaVersionType.AUTO || setting.getJavaVersionType() == JavaVersionType.VERSION) {
            task = getJavaTask.thenComposeAsync(Schedulers.javafx(), java -> {
                if (java != null) {
                    return Task.completed(java);
                }

                // Reset invalid java version
                CompletableFuture<JavaRuntime> future = new CompletableFuture<>();
                Task<JavaRuntime> result = Task.fromCompletableFuture(future);
                Runnable breakAction = () -> future.completeExceptionally(new CancellationException("No accepted java"));
                List<GameJavaVersion> supportedVersions = GameJavaVersion.getSupportedVersions(SYSTEM_PLATFORM);

                GameJavaVersion targetJavaVersion = null;
                if (setting.getJavaVersionType() == JavaVersionType.VERSION) {
                    try {
                        int targetJavaVersionMajor = Integer.parseInt(setting.getJavaVersion());
                        GameJavaVersion minimumJavaVersion = GameJavaVersion.getMinimumJavaVersion(gameVersion);

                        if (minimumJavaVersion != null && targetJavaVersionMajor < minimumJavaVersion.majorVersion()) {
                            Controllers.dialog(
                                    i18n("launch.failed.java_version_too_low"),
                                    i18n("message.error"),
                                    MessageType.ERROR,
                                    breakAction
                            );
                            return result;
                        }

                        targetJavaVersion = GameJavaVersion.get(targetJavaVersionMajor);
                    } catch (NumberFormatException ignored) {
                    }
                } else
                    targetJavaVersion = version.getJavaVersion();

                if (targetJavaVersion != null && supportedVersions.contains(targetJavaVersion)) {
                    downloadJava(targetJavaVersion, profile)
                            .whenCompleteAsync((downloadedJava, exception) -> {
                                if (exception == null) {
                                    future.complete(downloadedJava);
                                } else {
                                    LOG.warning("Failed to download java", exception);
                                    Controllers.confirm(i18n("launch.failed.no_accepted_java"), i18n("message.warning"), MessageType.WARNING,
                                            () -> future.complete(JavaRuntime.getDefault()),
                                            breakAction);
                                }
                            }, Schedulers.javafx());
                } else {
                    Controllers.confirm(i18n("launch.failed.no_accepted_java"), i18n("message.warning"), MessageType.WARNING,
                            () -> future.complete(JavaRuntime.getDefault()),
                            breakAction);
                }

                return result;
            });
        } else {
            task = getJavaTask.thenComposeAsync(java -> {
                Set<JavaVersionConstraint> violatedMandatoryConstraints = EnumSet.noneOf(JavaVersionConstraint.class);
                Set<JavaVersionConstraint> violatedSuggestedConstraints = EnumSet.noneOf(JavaVersionConstraint.class);

                if (java != null) {
                    for (JavaVersionConstraint constraint : JavaVersionConstraint.ALL) {
                        if (constraint.appliesToVersion(gameVersion, version, java, analyzer)) {
                            if (!constraint.checkJava(gameVersion, version, java)) {
                                if (constraint.isMandatory()) {
                                    violatedMandatoryConstraints.add(constraint);
                                } else {
                                    violatedSuggestedConstraints.add(constraint);
                                }
                            }
                        }
                    }
                }

                CompletableFuture<JavaRuntime> future = new CompletableFuture<>();
                Task<JavaRuntime> result = Task.fromCompletableFuture(future);
                Runnable breakAction = () -> future.completeExceptionally(new CancellationException("Launch operation was cancelled by user"));

                if (java == null || !violatedMandatoryConstraints.isEmpty()) {
                    JavaRuntime suggestedJava = JavaManager.findSuitableJava(gameVersion, version);
                    if (suggestedJava != null) {
                        FXUtils.runInFX(() -> {
                            Controllers.confirm(i18n("launch.advice.java.auto"), i18n("message.warning"), () -> {
                                setting.setJavaAutoSelected();
                                future.complete(suggestedJava);
                            }, breakAction);
                        });
                        return result;
                    } else if (java == null) {
                        FXUtils.runInFX(() -> Controllers.dialog(
                                i18n("launch.invalid_java"),
                                i18n("message.error"),
                                MessageType.ERROR,
                                breakAction
                        ));
                        return result;
                    } else {
                        GameJavaVersion gameJavaVersion;
                        if (violatedMandatoryConstraints.contains(JavaVersionConstraint.CLEANROOM_JAVA_21))
                            gameJavaVersion = GameJavaVersion.JAVA_21;
                        else if (violatedMandatoryConstraints.contains(JavaVersionConstraint.GAME_JSON))
                            gameJavaVersion = version.getJavaVersion();
                        else if (violatedMandatoryConstraints.contains(JavaVersionConstraint.VANILLA))
                            gameJavaVersion = GameJavaVersion.getMinimumJavaVersion(gameVersion);
                        else
                            gameJavaVersion = null;

                        if (gameJavaVersion != null) {
                            FXUtils.runInFX(() -> downloadJava(gameJavaVersion, profile).whenCompleteAsync((downloadedJava, throwable) -> {
                                if (throwable == null) {
                                    setting.setJavaAutoSelected();
                                    future.complete(downloadedJava);
                                } else {
                                    LOG.warning("Failed to download java", throwable);
                                    breakAction.run();
                                }
                            }, Schedulers.javafx()));
                            return result;
                        }

                        if (violatedMandatoryConstraints.contains(JavaVersionConstraint.VANILLA_LINUX_JAVA_8)) {
                            if (setting.getNativesDirType() == NativesDirectoryType.VERSION_FOLDER) {
                                FXUtils.runInFX(() -> Controllers.dialog(i18n("launch.advice.vanilla_linux_java_8"), i18n("message.error"), MessageType.ERROR, breakAction));
                                return result;
                            } else {
                                violatedMandatoryConstraints.remove(JavaVersionConstraint.VANILLA_LINUX_JAVA_8);
                            }
                        }

                        if (violatedMandatoryConstraints.contains(JavaVersionConstraint.LAUNCH_WRAPPER)) {
                            FXUtils.runInFX(() -> Controllers.dialog(
                                    i18n("launch.advice.java9") + "\n" + i18n("launch.advice.uncorrected"),
                                    i18n("message.error"),
                                    MessageType.ERROR,
                                    breakAction
                            ));
                            return result;
                        }

                        if (!violatedMandatoryConstraints.isEmpty()) {
                            FXUtils.runInFX(() -> Controllers.dialog(
                                    i18n("launch.advice.unknown") + "\n" + violatedMandatoryConstraints,
                                    i18n("message.error"),
                                    MessageType.ERROR,
                                    breakAction
                            ));
                            return result;
                        }
                    }
                }

                List<String> suggestions = new ArrayList<>();

                if (Architecture.SYSTEM_ARCH == Architecture.X86_64 && java.getPlatform().getArchitecture() == Architecture.X86) {
                    suggestions.add(i18n("launch.advice.different_platform"));
                }

                // 32-bit JVM cannot make use of too much memory.
                if (java.getBits() == Bits.BIT_32 && setting.getMaxMemory() > 1.5 * 1024) {
                    // 1.5 * 1024 is an inaccurate number.
                    // Actual memory limit depends on operating system and memory.
                    suggestions.add(i18n("launch.advice.too_large_memory_for_32bit"));
                }

                for (JavaVersionConstraint violatedSuggestedConstraint : violatedSuggestedConstraints) {
                    switch (violatedSuggestedConstraint) {
                        case MODDED_JAVA_7:
                            suggestions.add(i18n("launch.advice.java.modded_java_7"));
                            break;
                        case MODDED_JAVA_8:
                            // Minecraft>=1.7.10+Forge accepts Java 8
                            if (java.getParsedVersion() < 8)
                                suggestions.add(i18n("launch.advice.newer_java"));
                            else
                                suggestions.add(i18n("launch.advice.modded_java", 8, gameVersion));
                            break;
                        case MODDED_JAVA_16:
                            // Minecraft<=1.17.1+Forge[37.0.0,37.0.60) not compatible with Java 17
                            String forgePatchVersion = analyzer.getVersion(LibraryAnalyzer.LibraryType.FORGE).orElse(null);
                            if (forgePatchVersion != null && VersionNumber.compare(forgePatchVersion, "37.0.60") < 0)
                                suggestions.add(i18n("launch.advice.forge37_0_60"));
                            else
                                suggestions.add(i18n("launch.advice.modded_java", 16, gameVersion));
                            break;
                        case MODDED_JAVA_17:
                            suggestions.add(i18n("launch.advice.modded_java", 17, gameVersion));
                            break;
                        case MODDED_JAVA_21:
                            suggestions.add(i18n("launch.advice.modded_java", 21, gameVersion));
                            break;
                        case CLEANROOM_JAVA_21:
                            suggestions.add(i18n("launch.advice.cleanroom"));
                            break;
                        case VANILLA_JAVA_8_51:
                            suggestions.add(i18n("launch.advice.java8_51_1_13"));
                            break;
                        case MODLAUNCHER_8:
                            suggestions.add(i18n("launch.advice.modlauncher8"));
                            break;
                        case VANILLA_X86:
                            if (setting.getNativesDirType() == NativesDirectoryType.VERSION_FOLDER
                                    && isCompatibleWithX86Java()) {
                                suggestions.add(i18n("launch.advice.vanilla_x86.translation"));
                            }
                            break;
                        default:
                            suggestions.add(violatedSuggestedConstraint.name());
                    }
                }

                // Cannot allocate too much memory exceeding free space.
                long totalMemorySizeMB = (long) MEGABYTES.convertFromBytes(SystemInfo.getTotalMemorySize());
                if (totalMemorySizeMB > 0 && totalMemorySizeMB < setting.getMaxMemory()) {
                    suggestions.add(i18n("launch.advice.not_enough_space", totalMemorySizeMB));
                }

                VersionNumber forgeVersion = analyzer.getVersion(LibraryAnalyzer.LibraryType.FORGE)
                        .map(VersionNumber::asVersion)
                        .orElse(null);

                // Forge 2760~2773 will crash game with LiteLoader.
                boolean hasForge2760 = forgeVersion != null && (forgeVersion.compareTo("1.12.2-14.23.5.2760") >= 0) && (forgeVersion.compareTo("1.12.2-14.23.5.2773") < 0);
                boolean hasLiteLoader = version.getLibraries().stream().anyMatch(it -> it.is("com.mumfrey", "liteloader"));
                if (hasForge2760 && hasLiteLoader && gameVersion.compareTo("1.12.2") == 0) {
                    suggestions.add(i18n("launch.advice.forge2760_liteloader"));
                }

                // OptiFine 1.14.4 is not compatible with Forge 28.2.2 and later versions.
                boolean hasForge28_2_2 = forgeVersion != null && (forgeVersion.compareTo("1.14.4-28.2.2") >= 0);
                boolean hasOptiFine = version.getLibraries().stream().anyMatch(it -> it.is("optifine", "OptiFine"));
                if (hasForge28_2_2 && hasOptiFine && gameVersion.compareTo("1.14.4") == 0) {
                    suggestions.add(i18n("launch.advice.forge28_2_2_optifine"));
                }

                if (suggestions.isEmpty()) {
                    if (!future.isDone()) {
                        future.complete(java);
                    }
                } else {
                    String message;
                    if (suggestions.size() == 1) {
                        message = i18n("launch.advice", suggestions.get(0));
                    } else {
                        message = i18n("launch.advice.multi", suggestions.stream().map(it -> "→ " + it).collect(Collectors.joining("\n")));
                    }

                    FXUtils.runInFX(() -> Controllers.confirm(
                            message,
                            i18n("message.warning"),
                            MessageType.WARNING,
                            () -> future.complete(java),
                            breakAction));
                }

                return result;
            });
        }

        return task.withStage("launch.state.java");
    }

    private static CompletableFuture<JavaRuntime> downloadJava(GameJavaVersion javaVersion, Profile profile) {
        CompletableFuture<JavaRuntime> future = new CompletableFuture<>();
        Controllers.dialog(new MessageDialogPane.Builder(
                i18n("launch.advice.require_newer_java_version", javaVersion.majorVersion()),
                i18n("message.warning"),
                MessageType.QUESTION)
                .yesOrNo(() -> {
                    DownloadProvider downloadProvider = profile.getDependency().getDownloadProvider();
                    Controllers.taskDialog(JavaManager.getDownloadJavaTask(downloadProvider, SYSTEM_PLATFORM, javaVersion)
                            .whenComplete(Schedulers.javafx(), (result, exception) -> {
                                if (exception == null) {
                                    future.complete(result);
                                } else {
                                    Throwable resolvedException = resolveException(exception);
                                    LOG.warning("Failed to download java", exception);
                                    if (!(resolvedException instanceof CancellationException)) {
                                        Controllers.dialog(DownloadProviders.localizeErrorMessage(resolvedException), i18n("install.failed"));
                                    }
                                    future.completeExceptionally(new CancellationException());
                                }
                            }), i18n("download.java"), new TaskCancellationAction(() -> future.completeExceptionally(new CancellationException())));
                }, () -> future.completeExceptionally(new CancellationException())).build());

        return future;
    }

    private Task<AuthInfo> logIn(Account account) {
        return Task.composeAsync(() -> {
            try {
                if (disableOfflineSkin && account instanceof OfflineAccount offlineAccount)
                    return Task.completed(offlineAccount.logInWithoutSkin());
                else
                    return Task.completed(account.logIn());
            } catch (CredentialExpiredException e) {
                LOG.info("Credential has expired", e);

                return Task.completed(DialogController.logIn(account));
            } catch (AuthenticationException e) {
                LOG.warning("Authentication failed, try skipping refresh", e);

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
                runLater(() -> {
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
                runLater(() -> {
                    // Shut down the platform when user closed log window.
                    setImplicitExit(true);
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

        private final ReentrantLock lock = new ReentrantLock();
        private final HMCLGameRepository repository;
        private final Version version;
        private final LaunchOptions launchOptions;
        private ManagedProcess process;
        private volatile boolean lwjgl;
        private LogWindow logWindow;
        private final boolean detectWindow;
        private final CircularArrayList<Log> logs;
        private final CountDownLatch launchingLatch;
        private final String forbiddenAccessToken;
        private Thread submitLogThread;
        private LinkedBlockingQueue<Log> logBuffer;

        public HMCLProcessListener(HMCLGameRepository repository, Version version, AuthInfo authInfo, LaunchOptions launchOptions, CountDownLatch launchingLatch, boolean detectWindow) {
            this.repository = repository;
            this.version = version;
            this.launchOptions = launchOptions;
            this.launchingLatch = launchingLatch;
            this.detectWindow = detectWindow;
            this.forbiddenAccessToken = authInfo != null ? authInfo.getAccessToken() : null;
            this.logs = new CircularArrayList<>(Log.getLogLines() + 1);
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

            if (showLogs) {
                CountDownLatch logWindowLatch = new CountDownLatch(1);
                runLater(() -> {
                    logWindow = new LogWindow(process, logs);
                    logWindow.show();
                    logWindowLatch.countDown();
                });

                logBuffer = new LinkedBlockingQueue<>();
                submitLogThread = Lang.thread(new Runnable() {
                    private final ArrayList<Log> currentLogs = new ArrayList<>();
                    private final Semaphore semaphore = new Semaphore(0);

                    private void submitLogs() {
                        if (currentLogs.size() == 1) {
                            Log log = currentLogs.get(0);
                            runLater(() -> logWindow.logLine(log));
                        } else {
                            runLater(() -> {
                                logWindow.logLines(currentLogs);
                                semaphore.release();
                            });
                            semaphore.acquireUninterruptibly();
                        }
                        currentLogs.clear();
                    }

                    @Override
                    public void run() {
                        while (true) {
                            try {
                                currentLogs.add(logBuffer.take());
                                //noinspection BusyWait
                                Thread.sleep(200); // Wait for more logs
                            } catch (InterruptedException e) {
                                break;
                            }

                            logBuffer.drainTo(currentLogs);
                            submitLogs();
                        }

                        do {
                            submitLogs();
                        } while (logBuffer.drainTo(currentLogs) > 0);
                    }
                }, "Game Log Submitter", true);

                try {
                    logWindowLatch.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        private void finishLaunch() {
            switch (launcherVisibility) {
                case HIDE_AND_REOPEN:
                    runLater(() -> {
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
                    runLater(launchingLatch::countDown);
                    break;
                case HIDE:
                    launchingLatch.countDown();
                    runLater(() -> {
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
            if (isErrorStream)
                System.err.println(log);
            else
                System.out.println(log);

            log = StringUtils.parseEscapeSequence(log);
            if (forbiddenAccessToken != null)
                log = log.replace(forbiddenAccessToken, "<access token>");

            Log4jLevel level = isErrorStream && !log.startsWith("[authlib-injector]") ? Log4jLevel.ERROR : null;
            if (showLogs) {
                if (level == null)
                    level = Lang.requireNonNullElse(Log4jLevel.guessLevel(log), Log4jLevel.INFO);
                logBuffer.add(new Log(log, level));
            } else {
                lock.lock();
                try {
                    logs.addLast(new Log(log, level));
                    if (logs.size() > Log.getLogLines())
                        logs.removeFirst();
                } finally {
                    lock.unlock();
                }
            }

            if (!lwjgl) {
                String lowerCaseLog = log.toLowerCase(Locale.ROOT);
                if (!detectWindow || lowerCaseLog.contains("lwjgl version") || lowerCaseLog.contains("lwjgl openal")) {
                    lock.lock();
                    try {
                        if (!lwjgl) {
                            lwjgl = true;
                            finishLaunch();
                        }
                    } finally {
                        lock.unlock();
                    }
                }
            }
        }

        @Override
        public void onExit(int exitCode, ExitType exitType) {
            if (showLogs) {
                logBuffer.add(new Log(String.format("[HMCL ProcessListener] Minecraft exit with code %d(0x%x), type is %s.", exitCode, exitCode, exitType), Log4jLevel.INFO));
                submitLogThread.interrupt();
                try {
                    submitLogThread.join();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            launchingLatch.countDown();

            if (exitType == ExitType.INTERRUPTED)
                return;

            // Game crashed before opening the game window.
            if (!lwjgl) {
                lock.lock();
                try {
                    if (!lwjgl)
                        finishLaunch();
                } finally {
                    lock.unlock();
                }
            }

            if (exitType != ExitType.NORMAL) {
                repository.markVersionLaunchedAbnormally(version.getId());
                runLater(() -> new GameCrashWindow(process, exitType, repository, version, launchOptions, logs).show());
            }

            checkExit();
        }

    }

    private static final Queue<WeakReference<ManagedProcess>> PROCESSES = new ConcurrentLinkedQueue<>();

    public static int countMangedProcesses() {
        PROCESSES.removeIf(it -> {
            ManagedProcess process = it.get();
            return process == null || !process.isRunning();
        });
        return PROCESSES.size();
    }

    public static void stopManagedProcesses() {
        while (!PROCESSES.isEmpty())
            Optional.ofNullable(PROCESSES.poll()).map(WeakReference::get).ifPresent(ManagedProcess::stop);
    }
}
