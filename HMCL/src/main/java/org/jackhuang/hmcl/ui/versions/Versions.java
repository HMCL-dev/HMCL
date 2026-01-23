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

import com.jfoenix.controls.JFXButton;
import javafx.application.Platform;
import javafx.stage.FileChooser;
import org.jackhuang.hmcl.auth.Account;
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorAccount;
import org.jackhuang.hmcl.download.game.GameAssetDownloadTask;
import org.jackhuang.hmcl.game.*;
import org.jackhuang.hmcl.mod.RemoteMod;
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
import org.jackhuang.hmcl.util.io.NetworkUtils;
import org.jackhuang.hmcl.util.platform.OperatingSystem;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
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
        Profile profile = Profiles.getSelectedProfile();
        if (profile.getRepository().isLoaded()) {
            Controllers.getDecorator().startWizard(new ModpackInstallWizardProvider(profile), i18n("install.modpack"));
        }
    }

    public static void downloadModpackImpl(Profile profile, String version, RemoteMod mod, RemoteMod.Version file) {
        Path modpack;
        URI downloadURL;
        try {
            downloadURL = NetworkUtils.toURI(file.getFile().getUrl());
            modpack = Files.createTempFile("modpack", ".zip");
        } catch (IOException | IllegalArgumentException e) {
            Controllers.dialog(
                    i18n("install.failed.downloading.detail", file.getFile().getUrl()) + "\n" + StringUtils.getStackTrace(e),
                    i18n("download.failed.no_code"), MessageDialogPane.MessageType.ERROR);
            return;
        }
        Controllers.taskDialog(
                new FileDownloadTask(downloadURL, modpack)
                        .whenComplete(Schedulers.javafx(), e -> {
                            if (e == null) {
                                ModpackInstallWizardProvider installWizardProvider;
                                if (version != null)
                                    installWizardProvider = new ModpackInstallWizardProvider(profile, modpack, version);
                                else
                                    installWizardProvider = new ModpackInstallWizardProvider(profile, modpack);
                                if (StringUtils.isNotBlank(mod.getIconUrl()))
                                    installWizardProvider.setIconUrl(mod.getIconUrl());
                                Controllers.getDecorator().startWizard(installWizardProvider);
                            } else if (e instanceof CancellationException) {
                                Controllers.showToast(i18n("message.cancelled"));
                            } else {
                                Controllers.dialog(
                                        i18n("install.failed.downloading.detail", file.getFile().getUrl()) + "\n" + StringUtils.getStackTrace(e),
                                        i18n("download.failed.no_code"), MessageDialogPane.MessageType.ERROR);
                            }
                        }).executor(true),
                i18n("message.downloading"),
                TaskCancellationAction.NORMAL
        );
    }

    public static void deleteVersion(Profile profile, String version) {
        boolean isIndependent = profile.getVersionSetting(version).getGameDirType() == GameDirectoryType.VERSION_FOLDER;
        String message = isIndependent ? i18n("version.manage.remove.confirm.independent", version) :
                i18n("version.manage.remove.confirm.trash", version, version + "_removed");

        JFXButton deleteButton = new JFXButton(i18n("button.delete"));
        deleteButton.getStyleClass().add("dialog-error");
        deleteButton.setOnAction(e -> {
            Task.supplyAsync(Schedulers.io(), () -> profile.getRepository().removeVersionFromDisk(version))
                    .whenComplete(Schedulers.javafx(), (result, exception) -> {
                        if (exception != null || !Boolean.TRUE.equals(result)) {
                            Controllers.dialog(i18n("version.manage.remove.failed"), i18n("message.error"), MessageDialogPane.MessageType.ERROR);
                        }
                    }).start();
        });

        Controllers.confirmAction(message, i18n("message.warning"), MessageDialogPane.MessageType.WARNING, deleteButton);
    }

    public static CompletableFuture<String> renameVersion(Profile profile, String version) {
        return Controllers.prompt(i18n("version.manage.rename.message"), (newName, handler) -> {
            if (newName.equals(version)) {
                handler.resolve();
                return;
            }
            if (profile.getRepository().renameVersion(version, newName)) {
                handler.resolve();
                profile.getRepository().refreshVersionsAsync()
                        .thenRunAsync(Schedulers.javafx(), () -> {
                            if (profile.getRepository().hasVersion(newName)) {
                                profile.setSelectedVersion(newName);
                            }
                        }).start();
            } else {
                handler.reject(i18n("version.manage.rename.fail"));
            }
        }, version, new Validator(i18n("install.new_game.malformed"), HMCLGameRepository::isValidVersionId),
            new Validator(i18n("install.new_game.already_exists"), newVersionName -> !profile.getRepository().versionIdConflicts(newVersionName) || newVersionName.equals(version)));
    }

    public static void exportVersion(Profile profile, String version) {
        Controllers.getDecorator().startWizard(new ExportWizardProvider(profile, version), i18n("modpack.wizard"));
    }

    public static void openFolder(Profile profile, String version) {
        FXUtils.openFolder(profile.getRepository().getRunDirectory(version));
    }

    public static void duplicateVersion(Profile profile, String version) {
        Controllers.prompt(
                new PromptDialogPane.Builder(i18n("version.manage.duplicate.prompt"), (res, handler) -> {
                    String newVersionName = ((PromptDialogPane.Builder.StringQuestion) res.get(1)).getValue();
                    boolean copySaves = ((PromptDialogPane.Builder.BooleanQuestion) res.get(2)).getValue();
                    Task.runAsync(() -> profile.getRepository().duplicateVersion(version, newVersionName, copySaves))
                            .thenComposeAsync(profile.getRepository().refreshVersionsAsync())
                            .whenComplete(Schedulers.javafx(), (result, exception) -> {
                                if (exception == null) {
                                    handler.resolve();
                                } else {
                                    handler.reject(StringUtils.getStackTrace(exception));
                                    if (!profile.getRepository().versionIdConflicts(newVersionName)) {
                                        profile.getRepository().removeVersionFromDisk(newVersionName);
                                    }
                                }
                            }).start();
                })
                        .addQuestion(new PromptDialogPane.Builder.HintQuestion(i18n("version.manage.duplicate.confirm")))
                        .addQuestion(new PromptDialogPane.Builder.StringQuestion(null, version,
                                new Validator(i18n("install.new_game.malformed"), HMCLGameRepository::isValidVersionId),
                                new Validator(i18n("install.new_game.already_exists"), newVersionName -> !profile.getRepository().versionIdConflicts(newVersionName))))
                        .addQuestion(new PromptDialogPane.Builder.BooleanQuestion(i18n("version.manage.duplicate.duplicate_save"), false)));
    }

    public static void updateVersion(Profile profile, String version) {
        Controllers.getDecorator().startWizard(new ModpackInstallWizardProvider(profile, version));
    }

    public static void updateGameAssets(Profile profile, String version) {
        TaskExecutor executor = new GameAssetDownloadTask(profile.getDependency(), profile.getRepository().getVersion(version), GameAssetDownloadTask.DOWNLOAD_INDEX_FORCIBLY, true)
                .executor();
        Controllers.taskDialog(executor, i18n("version.manage.redownload_assets_index"), TaskCancellationAction.NO_CANCEL);
        executor.start();
    }

    public static void cleanVersion(Profile profile, String id) {
        try {
            profile.getRepository().clean(id);
        } catch (IOException e) {
            LOG.warning("Unable to clean game directory", e);
        }
    }

    @SafeVarargs
    public static void generateLaunchScript(Profile profile, String id, Consumer<LauncherHelper>... injecters) {
        if (!checkVersionForLaunching(profile, id))
            return;
        ensureSelectedAccount(account -> {
            GameRepository repository = profile.getRepository();
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
                LauncherHelper launcherHelper = new LauncherHelper(profile, account, id);
                for (Consumer<LauncherHelper> injecter : injecters) {
                    injecter.accept(launcherHelper);
                }
                launcherHelper.makeLaunchScript(file);
            }
        });
    }

    @SafeVarargs
    public static void launch(Profile profile, String id, Consumer<LauncherHelper>... injecters) {
        if (!checkVersionForLaunching(profile, id))
            return;
        ensureSelectedAccount(account -> {
            LauncherHelper launcherHelper = new LauncherHelper(profile, account, id);
            for (Consumer<LauncherHelper> injecter : injecters) {
                injecter.accept(launcherHelper);
            }
            launcherHelper.launch();
        });
    }

    public static void testGame(Profile profile, String id) {
        launch(profile, id, LauncherHelper::setTestMode);
    }

    public static void launchAndEnterWorld(Profile profile, String id, String worldFolderName) {
        launch(profile, id, launcherHelper ->
                launcherHelper.setQuickPlayOption(new QuickPlayOption.SinglePlayer(worldFolderName)));
    }

    public static void generateLaunchScriptForQuickEnterWorld(Profile profile, String id, String worldFolderName) {
        generateLaunchScript(profile, id, launcherHelper ->
                launcherHelper.setQuickPlayOption(new QuickPlayOption.SinglePlayer(worldFolderName)));
    }

    private static boolean checkVersionForLaunching(Profile profile, String id) {
        if (id == null || !profile.getRepository().isLoaded() || !profile.getRepository().hasVersion(id)) {
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
        if (ConfigHolder.isNewlyCreated() && !AuthlibInjectorServers.getServers().isEmpty() &&
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

    public static void modifyGlobalSettings(Profile profile) {
        Controllers.getSettingsPage().showGameSettings(profile);
        Controllers.navigate(Controllers.getSettingsPage());
    }

    public static void modifyGameSettings(Profile profile, String version) {
        Controllers.getVersionPage().setVersion(version, profile);
        Controllers.getVersionPage().showInstanceSettings();
        // VersionPage.loadVersion will be invoked after navigation
        Controllers.navigate(Controllers.getVersionPage());
    }
}
