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
package org.jackhuang.hmcl.ui;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXListView;
import com.jfoenix.controls.JFXPopup;
import com.jfoenix.controls.JFXTabPane;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Tab;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.StackPane;
import org.jackhuang.hmcl.Main;
import org.jackhuang.hmcl.download.game.GameAssetIndexDownloadTask;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.ui.export.ExportWizardProvider;
import org.jackhuang.hmcl.ui.wizard.DecoratorPage;
import org.jackhuang.hmcl.util.FileUtils;

import java.io.File;
import java.util.Optional;

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
    private JFXButton btnManagementMenu;
    @FXML
    private JFXButton btnExport;
    @FXML
    private StackPane rootPane;
    @FXML
    private StackPane contentPane;
    @FXML
    private JFXTabPane tabPane;

    private JFXPopup browsePopup;
    private JFXPopup managementPopup;

    private Profile profile;
    private String version;

    {
        FXUtils.loadFXML(this, "/assets/fxml/version/version.fxml");

        getChildren().removeAll(browseList, managementList);

        browsePopup = new JFXPopup(browseList);
        managementPopup = new JFXPopup(managementList);

        FXUtils.installTooltip(btnBrowseMenu, 0, 5000, 0, new Tooltip(Main.i18n("game_settings.exploration")));
        FXUtils.installTooltip(btnManagementMenu, 0, 5000, 0, new Tooltip(Main.i18n("game_settings.management")));
        FXUtils.installTooltip(btnExport, 0, 5000, 0, new Tooltip(Main.i18n("modpack.task.save")));
    }

    public void load(String id, Profile profile) {
        this.version = id;
        this.profile = profile;

        title.set(Main.i18n("game_settings") + " - " + id);

        versionSettingsController.loadVersionSetting(profile, id, profile.getVersionSetting(id));
        modController.setParentTab(tabPane);
        modTab.setUserData(modController);
        modController.loadMods(profile.getModManager(), id);
        installerController.loadVersion(profile, id);
    }

    public void onBrowseMenu() {
        browseList.getSelectionModel().select(-1);
        browsePopup.show(btnBrowseMenu, JFXPopup.PopupVPosition.TOP, JFXPopup.PopupHPosition.RIGHT, -12, 15);
    }

    public void onManagementMenu() {
        managementList.getSelectionModel().select(-1);
        managementPopup.show(btnManagementMenu, JFXPopup.PopupVPosition.TOP, JFXPopup.PopupHPosition.RIGHT, -12, 15);
    }

    public void onExport() {
        Controllers.getDecorator().startWizard(new ExportWizardProvider(profile, version), Main.i18n("modpack.wizard"));
    }

    public void onBrowse() {
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
                throw new RuntimeException();
        }
        FXUtils.openFolder(new File(profile.getRepository().getRunDirectory(version), sub));
    }

    public void onManagement() {
        switch (managementList.getSelectionModel().getSelectedIndex()) {
            case 0: // rename a version
                Optional<String> res = FXUtils.inputDialog("Input", Main.i18n("versions.manage.rename.message"), null, version);
                if (res.isPresent()) {
                    if (profile.getRepository().renameVersion(version, res.get())) {
                        profile.getRepository().refreshVersions();
                        Controllers.navigate(null);
                    }
                }
                break;
            case 1: // remove a version
                if (FXUtils.alert(Alert.AlertType.CONFIRMATION, "Confirm", Main.i18n("versions.manage.remove.confirm") + version)) {
                    if (profile.getRepository().removeVersionFromDisk(version)) {
                        profile.getRepository().refreshVersions();
                        Controllers.navigate(null);
                    }
                }
                break;
            case 2: // redownload asset index
                new GameAssetIndexDownloadTask(profile.getDependency(), profile.getRepository().getVersion(version).resolve(profile.getRepository())).start();
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
}
