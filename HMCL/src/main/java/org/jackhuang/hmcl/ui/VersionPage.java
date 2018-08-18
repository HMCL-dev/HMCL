/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2018  huangyuhui <huanghongxun2008@126.com>
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
package org.jackhuang.hmcl.ui;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXListView;
import com.jfoenix.controls.JFXPopup;
import com.jfoenix.controls.JFXTabPane;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.Tab;
import javafx.scene.layout.StackPane;

import org.jackhuang.hmcl.download.game.GameAssetIndexDownloadTask;
import org.jackhuang.hmcl.setting.EnumGameDirectory;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.ui.export.ExportWizardProvider;
import org.jackhuang.hmcl.ui.wizard.DecoratorPage;
import org.jackhuang.hmcl.util.FileUtils;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

import java.io.File;

public final class VersionPage extends StackPane implements DecoratorPage {
    private final StringProperty title = new SimpleStringProperty(this, "title", null);

    @FXML
    private VersionSettingsController versionSettingsController;
    @FXML
    private Tab modTab;
    @FXML
    private ModController modController;
    @FXML
    private InstallerController installerController;
    @FXML
    private JFXListView<?> browseList;
    @FXML
    private JFXListView<?> managementList;
    @FXML
    private JFXButton btnBrowseMenu;
    @FXML
    private JFXButton btnDelete;
    @FXML
    private JFXButton btnManagementMenu;
    @FXML
    private JFXButton btnExport;
    @FXML
    private StackPane rootPane;
    @FXML
    private StackPane contentPane;
    @FXML
    private JFXTabPane tabPane;

    private final JFXPopup browsePopup;
    private final JFXPopup managementPopup;

    private Profile profile;
    private String version;

    {
        FXUtils.loadFXML(this, "/assets/fxml/version/version.fxml");

        getChildren().removeAll(browseList, managementList);

        browsePopup = new JFXPopup(browseList);
        managementPopup = new JFXPopup(managementList);

        FXUtils.installTooltip(btnDelete, i18n("version.manage.remove"));
        FXUtils.installTooltip(btnBrowseMenu, i18n("settings.game.exploration"));
        FXUtils.installTooltip(btnManagementMenu, i18n("settings.game.management"));
        FXUtils.installTooltip(btnExport, i18n("modpack.export"));
    }

    public void load(String id, Profile profile) {
        this.version = id;
        this.profile = profile;

        title.set(i18n("settings.game") + " - " + id);

        versionSettingsController.loadVersionSetting(profile, id);
        modController.setParentTab(tabPane);
        modTab.setUserData(modController);
        modController.loadMods(profile.getModManager(), id);
        installerController.loadVersion(profile, id);
    }

    @FXML
    private void onBrowseMenu() {
        browseList.getSelectionModel().select(-1);
        browsePopup.show(btnBrowseMenu, JFXPopup.PopupVPosition.TOP, JFXPopup.PopupHPosition.RIGHT, -12, 15);
    }

    @FXML
    private void onManagementMenu() {
        managementList.getSelectionModel().select(-1);
        managementPopup.show(btnManagementMenu, JFXPopup.PopupVPosition.TOP, JFXPopup.PopupHPosition.RIGHT, -12, 15);
    }

    @FXML
    private void onDelete() {
        deleteVersion(profile, version);
    }

    @FXML
    private void onExport() {
        exportVersion(profile, version);
    }

    @FXML
    private void onBrowse() {
        String sub;
        switch (browseList.getSelectionModel().getSelectedIndex()) {
            case 0:
                sub = "";
                break;
            case 1:
                sub = "mods";
                break;
            case 2:
                sub = "coremods";
                break;
            case 3:
                sub = "config";
                break;
            case 4:
                sub = "resourcepacks";
                break;
            case 5:
                sub = "screenshots";
                break;
            case 6:
                sub = "saves";
                break;
            default:
                return;
        }
        FXUtils.openFolder(new File(profile.getRepository().getRunDirectory(version), sub));
    }

    @FXML
    private void onManagement() {
        switch (managementList.getSelectionModel().getSelectedIndex()) {
            case 0: // rename a version
                renameVersion(profile, version);
                break;
            case 1: // remove a version
                deleteVersion(profile, version);
                break;
            case 2: // redownload asset index
                new GameAssetIndexDownloadTask(profile.getDependency(), profile.getRepository().getResolvedVersion(version)).start();
                break;
            case 3: // delete libraries
                FileUtils.deleteDirectoryQuietly(new File(profile.getRepository().getBaseDirectory(), "libraries"));
                break;
            case 4:
                throw new Error();
        }
    }

    public String getTitle() {
        return title.get();
    }

    @Override
    public StringProperty titleProperty() {
        return title;
    }

    public void setTitle(String title) {
        this.title.set(title);
    }

    public static void deleteVersion(Profile profile, String version) {
        boolean isIndependent = profile.getVersionSetting(version).getGameDirType() == EnumGameDirectory.VERSION_FOLDER;
        Controllers.confirmDialog(i18n(isIndependent ? "version.manage.remove.confirm.independent" : "version.manage.remove.confirm", version), i18n("message.confirm"), () -> {
            if (profile.getRepository().removeVersionFromDisk(version)) {
                profile.getRepository().refreshVersionsAsync().start();
                Controllers.navigate(null);
            }
        }, null);
    }

    public static void renameVersion(Profile profile, String version) {
        Controllers.inputDialog(i18n("version.manage.rename.message"), (res, resolve, reject) -> {
            if (profile.getRepository().renameVersion(version, res)) {
                profile.getRepository().refreshVersionsAsync().start();
                Controllers.navigate(null);
                resolve.run();
            } else {
                reject.accept(i18n("version.manage.rename.fail"));
            }
        }).setInitialText(version);
    }

    public static void exportVersion(Profile profile, String version) {
        Controllers.getDecorator().startWizard(new ExportWizardProvider(profile, version), i18n("modpack.wizard"));
    }
}
