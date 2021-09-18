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
import org.jackhuang.hmcl.download.game.GameAssetDownloadTask;
import org.jackhuang.hmcl.game.GameDirectoryType;
import org.jackhuang.hmcl.game.GameRepository;
import org.jackhuang.hmcl.game.LauncherHelper;
import org.jackhuang.hmcl.mod.DownloadManager;
import org.jackhuang.hmcl.setting.Accounts;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.setting.Profiles;
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
import org.jackhuang.hmcl.ui.download.VanillaInstallWizardProvider;
import org.jackhuang.hmcl.ui.export.ExportWizardProvider;
import org.jackhuang.hmcl.util.Logging;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.platform.OperatingSystem;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Level;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public final class Versions {
    private Versions() {
    }

    public static void addNewGame() {
        Profile profile = Profiles.getSelectedProfile();
        if (profile.getRepository().isLoaded()) {
            Controllers.getDecorator().startWizard(new VanillaInstallWizardProvider(profile), i18n("install.new_game"));
        }
    }

    public static void importModpack() {
        Profile profile = Profiles.getSelectedProfile();
        if (profile.getRepository().isLoaded()) {
            Controllers.getDecorator().startWizard(new ModpackInstallWizardProvider(profile), i18n("install.modpack"));
        }
    }

    public static void downloadModpack() {
        Profile profile = Profiles.getSelectedProfile();
        if (profile.getRepository().isLoaded()) {
            Controllers.getModpackDownloadListPage().loadVersion(profile, null);
            Controllers.navigate(Controllers.getModpackDownloadListPage());
        }
    }

    public static void downloadModpackImpl(Profile profile, String version, DownloadManager.Version file) {
        Path modpack;
        URL downloadURL;
        try {
            modpack = Files.createTempFile("modpack", ".zip");
            downloadURL = new URL(file.getFile().getUrl());
        } catch (IOException e) {
            Controllers.dialog(
                    i18n("install.failed.downloading.detail", file.getFile().getUrl()) + "\n" + StringUtils.getStackTrace(e),
                    i18n("download.failed"), MessageDialogPane.MessageType.ERROR);
            return;
        }
        Controllers.taskDialog(
                new FileDownloadTask(downloadURL, modpack.toFile())
                        .whenComplete(Schedulers.javafx(), e -> {
                            if (e == null) {
                                Controllers.getDecorator().startWizard(new ModpackInstallWizardProvider(Profiles.getSelectedProfile(), modpack.toFile()));
                            } else {
                                Controllers.dialog(
                                        i18n("install.failed.downloading.detail", file.getFile().getUrl()) + "\n" + StringUtils.getStackTrace(e),
                                        i18n("download.failed"), MessageDialogPane.MessageType.ERROR);
                            }
                        }).executor(true),
                i18n("message.downloading")
        );
    }

    public static void deleteVersion(Profile profile, String version) {
        boolean isIndependent = profile.getVersionSetting(version).getGameDirType() == GameDirectoryType.VERSION_FOLDER;
        boolean isMovingToTrashSupported = FileUtils.isMovingToTrashSupported();
        String message = isIndependent ? i18n("version.manage.remove.confirm.independent", version) :
                isMovingToTrashSupported ? i18n("version.manage.remove.confirm.trash", version, version + "_removed") :
                        i18n("version.manage.remove.confirm", version);

        JFXButton deleteButton = new JFXButton(i18n("button.delete"));
        deleteButton.getStyleClass().add("dialog-error");
        deleteButton.setOnAction(e -> profile.getRepository().removeVersionFromDisk(version));

        Controllers.confirmAction(message, i18n("message.warning"), MessageDialogPane.MessageType.WARNING, deleteButton);
    }

    public static CompletableFuture<String> renameVersion(Profile profile, String version) {
        return Controllers.prompt(i18n("version.manage.rename.message"), (newName, resolve, reject) -> {
            if (!OperatingSystem.isNameValid(newName)) {
                reject.accept(i18n("install.new_game.malformed"));
                return;
            }
            if (profile.getRepository().renameVersion(version, newName)) {
                profile.getRepository().refreshVersionsAsync().start();
                resolve.run();
            } else {
                reject.accept(i18n("version.manage.rename.fail"));
            }
        }, version);
    }

    public static void exportVersion(Profile profile, String version) {
        Controllers.getDecorator().startWizard(new ExportWizardProvider(profile, version), i18n("modpack.wizard"));
    }

    public static void openFolder(Profile profile, String version) {
        FXUtils.openFolder(profile.getRepository().getRunDirectory(version));
    }

    public static void duplicateVersion(Profile profile, String version) {
        Controllers.prompt(
                new PromptDialogPane.Builder(i18n("version.manage.duplicate.prompt"), (res, resolve, reject) -> {
                    String newVersionName = ((PromptDialogPane.Builder.StringQuestion) res.get(0)).getValue();
                    boolean copySaves = ((PromptDialogPane.Builder.BooleanQuestion) res.get(1)).getValue();
                    Task.runAsync(() -> profile.getRepository().duplicateVersion(version, newVersionName, copySaves))
                            .thenComposeAsync(profile.getRepository().refreshVersionsAsync())
                            .whenComplete(Schedulers.javafx(), (result, exception) -> {
                                if (exception == null) {
                                    resolve.run();
                                } else {
                                    reject.accept(StringUtils.getStackTrace(exception));
                                    profile.getRepository().removeVersionFromDisk(newVersionName);
                                }
                            }).start();
                })
                        .addQuestion(new PromptDialogPane.Builder.StringQuestion(i18n("version.manage.duplicate.confirm"), version,
                                new Validator(i18n("install.new_game.already_exists"), newVersionName -> !profile.getRepository().hasVersion(newVersionName))))
                        .addQuestion(new PromptDialogPane.Builder.BooleanQuestion(i18n("version.manage.duplicate.duplicate_save"), false)));
    }

    public static void updateVersion(Profile profile, String version) {
        Controllers.getDecorator().startWizard(new ModpackInstallWizardProvider(profile, version));
    }

    public static void updateGameAssets(Profile profile, String version) {
        TaskExecutor executor = new GameAssetDownloadTask(profile.getDependency(), profile.getRepository().getVersion(version), GameAssetDownloadTask.DOWNLOAD_INDEX_FORCIBLY, true)
                .executor();
        Controllers.taskDialog(executor, i18n("version.manage.redownload_assets_index"));
        executor.start();
    }

    public static void cleanVersion(Profile profile, String id) {
        try {
            profile.getRepository().clean(id);
        } catch (IOException e) {
            Logging.LOG.log(Level.WARNING, "Unable to clean game directory", e);
        }
    }

    public static void generateLaunchScript(Profile profile, String id) {
        if (!checkVersionForLaunching(profile, id))
            return;
        ensureSelectedAccount(account -> {
            GameRepository repository = profile.getRepository();
            FileChooser chooser = new FileChooser();
            if (repository.getRunDirectory(id).isDirectory())
                chooser.setInitialDirectory(repository.getRunDirectory(id));
            chooser.setTitle(i18n("version.launch_script.save"));
            chooser.getExtensionFilters().add(OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS
                    ? new FileChooser.ExtensionFilter(i18n("extension.bat"), "*.bat")
                    : new FileChooser.ExtensionFilter(i18n("extension.sh"), "*.sh"));
            File file = chooser.showSaveDialog(Controllers.getStage());
            if (file != null)
                new LauncherHelper(profile, account, id).makeLaunchScript(file);
        });
    }

    public static void launch(Profile profile, String id) {
        if (!checkVersionForLaunching(profile, id))
            return;
        ensureSelectedAccount(account -> {
            new LauncherHelper(profile, account, id).launch();
        });
    }

    public static void testGame(Profile profile, String id) {
        if (!checkVersionForLaunching(profile, id))
            return;
        ensureSelectedAccount(account -> {
            LauncherHelper helper = new LauncherHelper(profile, account, id);
            helper.setTestMode();
            helper.launch();
        });
    }

    private static boolean checkVersionForLaunching(Profile profile, String id) {
        if (id == null || !profile.getRepository().isLoaded() || !profile.getRepository().hasVersion(id)) {
            Controllers.dialog(i18n("version.empty.launch"), i18n("launch.failed"), MessageDialogPane.MessageType.ERROR, () -> {
                Controllers.navigate(Controllers.getDownloadPage());
            });
            return false;
        } else {
            return true;
        }
    }

    private static void ensureSelectedAccount(Consumer<Account> action) {
        Account account = Accounts.getSelectedAccount();
        if (account == null) {
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
//        VersionSettingsPage page = new VersionSettingsPage();
//        page.loadVersion(profile, null);
//        Controllers.navigate(page);
    }

    public static void modifyGameSettings(Profile profile, String version) {
        Controllers.getVersionPage().setVersion(version, profile);
        // VersionPage.loadVersion will be invoked after navigation
        Controllers.navigate(Controllers.getVersionPage());
    }
}
