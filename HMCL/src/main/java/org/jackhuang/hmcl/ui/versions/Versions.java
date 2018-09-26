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
package org.jackhuang.hmcl.ui.versions;

import javafx.scene.layout.Region;
import javafx.stage.FileChooser;
import org.jackhuang.hmcl.game.GameRepository;
import org.jackhuang.hmcl.game.LauncherHelper;
import org.jackhuang.hmcl.game.ModpackHelper;
import org.jackhuang.hmcl.mod.MismatchedModpackTypeException;
import org.jackhuang.hmcl.mod.UnsupportedModpackException;
import org.jackhuang.hmcl.setting.Accounts;
import org.jackhuang.hmcl.setting.EnumGameDirectory;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.setting.Profiles;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskExecutor;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.construct.DialogCloseEvent;
import org.jackhuang.hmcl.ui.construct.MessageBox;
import org.jackhuang.hmcl.ui.export.ExportWizardProvider;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.platform.OperatingSystem;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class Versions {

    public static void deleteVersion(Profile profile, String version) {
        boolean isIndependent = profile.getVersionSetting(version).getGameDirType() == EnumGameDirectory.VERSION_FOLDER;
        boolean isMovingToTrashSupported = FileUtils.isMovingToTrashSupported();
        String message = isIndependent ? i18n("version.manage.remove.confirm.independent", version) :
                isMovingToTrashSupported ? i18n("version.manage.remove.confirm.trash", version, version + "_removed") :
                        i18n("version.manage.remove.confirm", version);
        Controllers.confirmDialog(message, i18n("message.confirm"), () -> {
            if (profile.getRepository().removeVersionFromDisk(version)) {
                profile.getRepository().refreshVersionsAsync().start();
            }
        }, null);
    }

    public static void renameVersion(Profile profile, String version) {
        Controllers.inputDialog(i18n("version.manage.rename.message"), (res, resolve, reject) -> {
            if (profile.getRepository().renameVersion(version, res)) {
                profile.getRepository().refreshVersionsAsync().start();
                resolve.run();
            } else {
                reject.accept(i18n("version.manage.rename.fail"));
            }
        }).setInitialText(version);
    }

    public static void exportVersion(Profile profile, String version) {
        Controllers.getDecorator().startWizard(new ExportWizardProvider(profile, version), i18n("modpack.wizard"));
    }

    public static void openFolder(Profile profile, String version) {
        FXUtils.openFolder(profile.getRepository().getRunDirectory(version));
    }

    public static void updateVersion(Profile profile, String version) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(i18n("modpack.choose"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(i18n("modpack"), "*.zip"));
        File selectedFile = chooser.showOpenDialog(Controllers.getStage());
        if (selectedFile != null) {
            AtomicReference<Region> region = new AtomicReference<>();
            try {
                TaskExecutor executor = ModpackHelper.getUpdateTask(profile, selectedFile, version, ModpackHelper.readModpackConfiguration(profile.getRepository().getModpackConfiguration(version)))
                        .then(Task.of(Schedulers.javafx(), () -> region.get().fireEvent(new DialogCloseEvent()))).executor();
                region.set(Controllers.taskDialog(executor, i18n("modpack.update"), ""));
                executor.start();
            } catch (UnsupportedModpackException e) {
                Controllers.dialog(i18n("modpack.unsupported"), i18n("message.error"), MessageBox.ERROR_MESSAGE);
            } catch (MismatchedModpackTypeException e) {
                Controllers.dialog(i18n("modpack.mismatched_type"), i18n("message.error"), MessageBox.ERROR_MESSAGE);
            } catch (IOException e) {
                Controllers.dialog(i18n("modpack.invalid"), i18n("message.error"), MessageBox.ERROR_MESSAGE);
            }
        }
    }

    public static void generateLaunchScript(Profile profile, String id) {
        GameRepository repository = profile.getRepository();

        if (Accounts.getSelectedAccount() == null)
            Controllers.dialog(i18n("login.empty_username"));
        else {
            FileChooser chooser = new FileChooser();
            if (repository.getRunDirectory(id).isDirectory())
                chooser.setInitialDirectory(repository.getRunDirectory(id));
            chooser.setTitle(i18n("version.launch_script.save"));
            chooser.getExtensionFilters().add(OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS
                    ? new FileChooser.ExtensionFilter(i18n("extension.bat"), "*.bat")
                    : new FileChooser.ExtensionFilter(i18n("extension.sh"), "*.sh"));
            File file = chooser.showSaveDialog(Controllers.getStage());
            if (file != null)
                LauncherHelper.INSTANCE.launch(profile, Accounts.getSelectedAccount(), id, file);
        }
    }

    public static void launch(Profile profile, String id) {
        if (Accounts.getSelectedAccount() == null)
            Controllers.getLeftPaneController().checkAccount();
        else if (id == null)
            Controllers.dialog(i18n("version.empty.launch"));
        else
            LauncherHelper.INSTANCE.launch(profile, Accounts.getSelectedAccount(), id, null);
    }

    public static void modifyGlobalSettings(Profile profile) {
        VersionSettingsPage page = new VersionSettingsPage();
        page.loadVersionSetting(profile, null);
        Controllers.navigate(page);
    }
}
