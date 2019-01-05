/**
 * Hello Minecraft! Launcher
 * Copyright (C) 2019  huangyuhui <huanghongxun2008@126.com> and contributors
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

import javafx.stage.FileChooser;
import org.jackhuang.hmcl.download.game.GameAssetDownloadTask;
import org.jackhuang.hmcl.download.game.GameAssetIndexDownloadTask;
import org.jackhuang.hmcl.game.GameRepository;
import org.jackhuang.hmcl.game.LauncherHelper;
import org.jackhuang.hmcl.game.Version;
import org.jackhuang.hmcl.setting.Accounts;
import org.jackhuang.hmcl.setting.EnumGameDirectory;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.task.TaskExecutor;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.download.ModpackInstallWizardProvider;
import org.jackhuang.hmcl.ui.export.ExportWizardProvider;
import org.jackhuang.hmcl.util.Logging;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.platform.OperatingSystem;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

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
        Controllers.getDecorator().startWizard(new ModpackInstallWizardProvider(profile, version));
    }

    public static void updateGameAssets(Profile profile, String version) {
        Version resolvedVersion = profile.getRepository().getResolvedVersion(version);
        TaskExecutor executor = new GameAssetIndexDownloadTask(profile.getDependency(), resolvedVersion)
                .then(new GameAssetDownloadTask(profile.getDependency(), resolvedVersion))
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
        if (checkForLaunching(profile, id)) {
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
                new LauncherHelper(profile, Accounts.getSelectedAccount(), id).makeLaunchScript(file);
        }
    }

    public static void launch(Profile profile, String id) {
        if (checkForLaunching(profile, id))
            new LauncherHelper(profile, Accounts.getSelectedAccount(), id).launch();
    }

    public static void testGame(Profile profile, String id) {
        if (checkForLaunching(profile, id)) {
            LauncherHelper helper = new LauncherHelper(profile, Accounts.getSelectedAccount(), id);
            helper.setTestMode();
            helper.launch();
        }
    }

    private static boolean checkForLaunching(Profile profile, String id) {
        if (Accounts.getSelectedAccount() == null)
            Controllers.getLeftPaneController().checkAccount();
        else if (id == null || !profile.getRepository().isLoaded() || !profile.getRepository().hasVersion(id))
            Controllers.dialog(i18n("version.empty.launch"));
        else
            return true;
        return false;
    }

    public static void modifyGlobalSettings(Profile profile) {
        VersionSettingsPage page = new VersionSettingsPage();
        page.loadVersion(profile, null);
        Controllers.navigate(page);
    }

    public static void modifyGameSettings(Profile profile, String version) {
        Controllers.getVersionPage().load(version, profile);
        Controllers.navigate(Controllers.getVersionPage());
    }
}
