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
package org.jackhuang.hmcl.ui.versions;

import org.jackhuang.hmcl.setting.GameDirectoryManager;

import com.jfoenix.controls.JFXButton;
import javafx.application.Platform;
import javafx.stage.FileChooser;
import org.jackhuang.hmcl.auth.Account;
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorAccount;
import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.download.game.GameAssetDownloadTask;
import org.jackhuang.hmcl.download.game.GameDownloadTask;
import org.jackhuang.hmcl.download.game.GameLibrariesTask;
import org.jackhuang.hmcl.game.*;
import org.jackhuang.hmcl.addon.RemoteAddon;
import org.jackhuang.hmcl.setting.*;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskExecutor;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.account.CreateAccountPane;
import org.jackhuang.hmcl.ui.construct.DialogCloseEvent;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane;
import org.jackhuang.hmcl.ui.construct.PromptDialogPane;
import org.jackhuang.hmcl.ui.construct.Validator;
import org.jackhuang.hmcl.ui.download.ModpackInstallWizardProvider;
import org.jackhuang.hmcl.ui.export.ExportWizardProvider;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.TaskCancellationAction;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.platform.OperatingSystem;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class Versions {
    private Versions() {
    }

    public static void addNewGame() {
        Controllers.getDownloadPage().showGameDownloads();
        Controllers.navigate(Controllers.getDownloadPage());
    }

    public static void importModpack() {
        HMCLGameRepository repository = GameDirectoryManager.getSelectedRepository();
        if (repository.isLoaded()) {
            Controllers.getDecorator().startWizard(new ModpackInstallWizardProvider(repository), i18n("install.modpack"));
        }
    }

    public static void downloadModpackImpl(DownloadProvider downloadProvider, HMCLGameRepository repository, String version, RemoteAddon mod, RemoteAddon.Version file) {
        Path modpack;
        List<URI> downloadURLs;
        try {
            downloadURLs = downloadProvider.injectURLWithCandidates(file.file().url());
            modpack = Files.createTempFile("modpack", ".zip");
        } catch (IOException | IllegalArgumentException e) {
            Controllers.dialog(
                    i18n("install.failed.downloading.detail", file.file().url()) + "\n" + StringUtils.getStackTrace(e),
                    i18n("download.failed.no_code"), MessageDialogPane.MessageType.ERROR);
            return;
        }
        Controllers.taskDialog(
                new FileDownloadTask(downloadURLs, modpack)
                        .whenComplete(Schedulers.javafx(), e -> {
                            if (e == null) {
                                ModpackInstallWizardProvider installWizardProvider;
                                if (version != null)
                                    installWizardProvider = new ModpackInstallWizardProvider(repository, modpack, version);
                                else
                                    installWizardProvider = new ModpackInstallWizardProvider(repository, modpack);
                                if (StringUtils.isNotBlank(mod.iconUrl()))
                                    installWizardProvider.setIconUrl(mod.iconUrl());
                                Controllers.getDecorator().startWizard(installWizardProvider);
                            } else if (e instanceof CancellationException) {
                                Controllers.showToast(i18n("message.cancelled"));
                            } else {
                                Controllers.dialog(
                                        i18n("install.failed.downloading.detail", file.file().url()) + "\n" + StringUtils.getStackTrace(e),
                                        i18n("download.failed.no_code"), MessageDialogPane.MessageType.ERROR);
                            }
                        }),
                i18n("message.downloading"),
                TaskCancellationAction.NORMAL
        );
    }

    public static void deleteVersion(HMCLGameRepository repository, String version) {
        boolean isIndependent = repository.getRunDirectory(version).toAbsolutePath().normalize()
                .equals(repository.getVersionRoot(version).toAbsolutePath().normalize());
        String message = isIndependent ? i18n("version.manage.remove.confirm.independent", version) :
                i18n("version.manage.remove.confirm.trash", version, version + "_removed");

        JFXButton deleteButton = new JFXButton(i18n("button.delete"));
        deleteButton.getStyleClass().add("dialog-error");
        deleteButton.setOnAction(e -> {
            Task.supplyAsync(Schedulers.io(), () -> repository.removeVersionFromDisk(version))
                    .whenComplete(Schedulers.javafx(), (result, exception) -> {
                        if (exception != null || !Boolean.TRUE.equals(result)) {
                            Controllers.dialog(i18n("version.manage.remove.failed"), i18n("message.error"), MessageDialogPane.MessageType.ERROR);
                        }
                    }).start();
        });

        Controllers.confirmAction(message, i18n("message.warning"), MessageDialogPane.MessageType.WARNING, deleteButton);
    }

    public static CompletableFuture<String> renameVersion(HMCLGameRepository repository, String version) {
        return Controllers.prompt(i18n("version.manage.rename.message"), (newName, handler) -> {
            if (newName.equals(version)) {
                handler.resolve();
                return;
            }
            if (repository.renameVersion(version, newName)) {
                handler.resolve();
                repository.refreshVersionsAsync()
                        .thenRunAsync(Schedulers.javafx(), () -> {
                            if (repository.hasVersion(newName)) {
                                repository.setSelectedInstance(newName);
                            }
                        }).start();
            } else {
                handler.reject(i18n("version.manage.rename.fail"));
            }
        }, version,
            new Validator(i18n("install.new_game.malformed"), HMCLGameRepository::isValidVersionId),
            new Validator(i18n("install.new_game.already_exists"), newVersionName -> !repository.versionIdConflicts(newVersionName) || newVersionName.equals(version)));
    }

    public static void exportVersion(HMCLGameRepository repository, String version) {
        Controllers.getDecorator().startWizard(new ExportWizardProvider(repository, version), i18n("modpack.wizard"));
    }

    public static void openFolder(HMCLGameRepository repository, String version) {
        FXUtils.openFolder(repository.getRunDirectory(version));
    }

    public static void installFromJson(HMCLGameRepository repository, Path file) {
        Version version;
        try {
            version = repository.readVersionJson(file);
        } catch (Exception e) {
            Controllers.dialog(i18n("install.new_game.malformed_json"), i18n("message.error"), MessageDialogPane.MessageType.ERROR);
            return;
        }

        Controllers.prompt(i18n("version.manage.duplicate.prompt"), (result, handler) -> {
            handler.resolve();

            DefaultDependencyManager dependencyManager = repository.getDependency();
            Version newVersion = version.setId(result).setJar(result);

            Controllers.taskDialog(
                    Task.allOf(new GameDownloadTask(dependencyManager, null, newVersion),
                                    Task.allOf(
                                            new GameAssetDownloadTask(dependencyManager, newVersion, GameAssetDownloadTask.DOWNLOAD_INDEX_FORCIBLY, true),
                                            new GameLibrariesTask(dependencyManager, newVersion, true)
                                    ).withRunAsync(() -> {
                                        // ignore failure
                                    }))
                            .thenComposeAsync(repository.saveAsync(newVersion))
                            .thenRunAsync(repository::refreshVersions)
                            .whenComplete(Schedulers.javafx(), (exception) -> {
                                if (exception == null) {
                                    repository.setSelectedInstance(result);
                                } else {
                                    Controllers.dialog(
                                            DownloadProviders.localizeErrorMessage(exception), i18n("install.failed"), MessageDialogPane.MessageType.ERROR);
                                }
                            }), i18n("install.new_game"), TaskCancellationAction.NORMAL);
        }, FileUtils.getNameWithoutExtension(file), new Validator(i18n("install.new_game.malformed"), HMCLGameRepository::isValidVersionId), new Validator(i18n("install.new_game.already_exists"), newVersionName -> !repository.versionIdConflicts(newVersionName)));
    }

    public static void duplicateVersion(HMCLGameRepository repository, String version) {
        Controllers.prompt(
                new PromptDialogPane.Builder(i18n("version.manage.duplicate.prompt"), (res, handler) -> {
                    String newVersionName = ((PromptDialogPane.Builder.StringQuestion) res.get(1)).getValue();
                    boolean copySaves = ((PromptDialogPane.Builder.BooleanQuestion) res.get(2)).getValue();
                    Task.runAsync(() -> repository.duplicateVersion(version, newVersionName, copySaves))
                            .thenComposeAsync(repository.refreshVersionsAsync())
                            .whenComplete(Schedulers.javafx(), (result, exception) -> {
                                if (exception == null) {
                                    handler.resolve();
                                } else {
                                    handler.reject(StringUtils.getStackTrace(exception));
                                    if (!repository.versionIdConflicts(newVersionName)) {
                                        repository.removeVersionFromDisk(newVersionName);
                                    }
                                }
                            }).start();
                })
                        .addQuestion(new PromptDialogPane.Builder.HintQuestion(i18n("version.manage.duplicate.confirm")))
                        .addQuestion(new PromptDialogPane.Builder.StringQuestion(null, version,
                                new Validator(i18n("install.new_game.malformed"), HMCLGameRepository::isValidVersionId),
                                new Validator(i18n("install.new_game.already_exists"), newVersionName -> !repository.versionIdConflicts(newVersionName))))
                        .addQuestion(new PromptDialogPane.Builder.BooleanQuestion(i18n("version.manage.duplicate.duplicate_save"), false)));
    }

    public static void updateVersion(HMCLGameRepository repository, String version) {
        Controllers.getDecorator().startWizard(new ModpackInstallWizardProvider(repository, version));
    }

    public static void updateGameAssets(HMCLGameRepository repository, String version) {
        TaskExecutor executor = new GameAssetDownloadTask(repository.getDependency(), repository.getVersion(version), GameAssetDownloadTask.DOWNLOAD_INDEX_FORCIBLY, true)
                .executor();
        Controllers.taskDialog(executor, i18n("version.manage.redownload_assets_index"), TaskCancellationAction.NO_CANCEL);
        executor.start();
    }

    public static void cleanVersion(HMCLGameRepository repository, String id) {
        try {
            repository.clean(id);
        } catch (IOException e) {
            LOG.warning("Unable to clean game directory", e);
        }
    }

    @SafeVarargs
    public static void generateLaunchScript(HMCLGameRepository repository, String id, Consumer<LauncherHelper>... injecters) {
        if (!checkVersionForLaunching(repository, id))
            return;
        ensureSelectedAccount(account -> {
            FileChooser chooser = new FileChooser();
            if (Files.isDirectory(repository.getRunDirectory(id)))
                chooser.setInitialDirectory(repository.getRunDirectory(id).toFile());
            chooser.setTitle(i18n("version.launch_script.save"));
            if (OperatingSystem.CURRENT_OS == OperatingSystem.MACOS) {
                chooser.getExtensionFilters().add(
                        new FileChooser.ExtensionFilter(i18n("extension.command"), "*.command")
                );
            }
            chooser.getExtensionFilters().add(OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS
                    ? new FileChooser.ExtensionFilter(i18n("extension.bat"), "*.bat")
                    : new FileChooser.ExtensionFilter(i18n("extension.sh"), "*.sh"));
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(i18n("extension.ps1"), "*.ps1"));
            Path file = FileUtils.toPath(chooser.showSaveDialog(Controllers.getStage()));
            if (file != null) {
                if (!isValidScriptExtension(FileUtils.getExtension(file))) {
                    String defaultExt = getDefaultScriptExtension();
                    file = file.resolveSibling(file.getFileName().toString() + "." + defaultExt);
                }

                LauncherHelper launcherHelper = new LauncherHelper(repository, account, id);
                for (Consumer<LauncherHelper> injecter : injecters) {
                    injecter.accept(launcherHelper);
                }
                launcherHelper.makeLaunchScript(file);
            }
        });
    }

    private static boolean isValidScriptExtension(String ext) {
        if (OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS) {
            return ext.equalsIgnoreCase("bat") || ext.equalsIgnoreCase("ps1");
        }
        return ext.equalsIgnoreCase("sh") || ext.equalsIgnoreCase("bash") || ext.equalsIgnoreCase("command") || ext.equalsIgnoreCase("ps1");
    }

    private static String getDefaultScriptExtension() {
        return switch (OperatingSystem.CURRENT_OS) {
            case WINDOWS -> "bat";
            case MACOS -> "command";
            default -> "sh";
        };
    }

    @SafeVarargs
    public static void launch(HMCLGameRepository repository, String id, Consumer<LauncherHelper>... injecters) {
        if (!checkVersionForLaunching(repository, id))
            return;
        ensureSelectedAccount(account -> {
            LauncherHelper launcherHelper = new LauncherHelper(repository, account, id);
            for (Consumer<LauncherHelper> injecter : injecters) {
                injecter.accept(launcherHelper);
            }
            launcherHelper.launch();
        });
    }

    public static void testGame(HMCLGameRepository repository, String id) {
        launch(repository, id, LauncherHelper::setTestMode);
    }

    public static void launchAndEnterWorld(HMCLGameRepository repository, String id, String worldFolderName) {
        launch(repository, id, launcherHelper ->
                launcherHelper.setQuickPlayOption(new QuickPlayOption.SinglePlayer(worldFolderName)));
    }

    public static void generateLaunchScriptForQuickEnterWorld(HMCLGameRepository repository, String id, String worldFolderName) {
        generateLaunchScript(repository, id, launcherHelper ->
                launcherHelper.setQuickPlayOption(new QuickPlayOption.SinglePlayer(worldFolderName)));
    }

    private static boolean checkVersionForLaunching(HMCLGameRepository repository, String id) {
        if (id == null || !repository.isLoaded() || !repository.hasVersion(id)) {
            JFXButton gotoDownload = new JFXButton(i18n("version.empty.launch.goto_download"));
            gotoDownload.getStyleClass().add("dialog-accept");
            gotoDownload.setOnAction(e -> Controllers.navigate(Controllers.getDownloadPage()));

            Controllers.confirmAction(i18n("version.empty.launch"), i18n("launch.failed"),
                    MessageDialogPane.MessageType.ERROR,
                    gotoDownload,
                    null);
            return false;
        } else {
            return true;
        }
    }

    private static void ensureSelectedAccount(Consumer<Account> action) {
        Account account = Accounts.getSelectedAccount();
        if (SettingsManager.isNewlyCreated() && !AuthlibInjectorServers.getServers().isEmpty() &&
                !(account instanceof AuthlibInjectorAccount && AuthlibInjectorServers.getServers().contains(((AuthlibInjectorAccount) account).getServer()))) {
            CreateAccountPane dialog = new CreateAccountPane(AuthlibInjectorServers.getServers().iterator().next());
            dialog.addEventHandler(DialogCloseEvent.CLOSE, e -> {
                Account newAccount = Accounts.getSelectedAccount();
                if (newAccount == null) {
                    // user cancelled operation
                } else {
                    Platform.runLater(() -> action.accept(newAccount));
                }
            });
            Controllers.dialog(dialog);
        } else if (account == null) {
            CreateAccountPane dialog = new CreateAccountPane();
            dialog.addEventHandler(DialogCloseEvent.CLOSE, e -> {
                Account newAccount = Accounts.getSelectedAccount();
                if (newAccount == null) {
                    // user cancelled operation
                } else {
                    Platform.runLater(() -> action.accept(newAccount));
                }
            });
            Controllers.dialog(dialog);
        } else {
            action.accept(account);
        }
    }

    public static void modifyGlobalSettings(HMCLGameRepository repository) {
        Controllers.getSettingsPage().showGameSettings(repository);
        Controllers.navigate(Controllers.getSettingsPage());
    }

    public static void modifyGameSettings(HMCLGameRepository repository, String version) {
        Controllers.getVersionPage().setVersion(version, repository);
        Controllers.getVersionPage().showInstanceSettings();
        // VersionPage.loadVersion will be invoked after navigation
        Controllers.navigate(Controllers.getVersionPage());
    }
}
