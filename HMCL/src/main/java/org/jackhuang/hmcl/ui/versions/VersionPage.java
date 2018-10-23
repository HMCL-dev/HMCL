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
package org.jackhuang.hmcl.ui.versions;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXPopup;
import com.jfoenix.controls.JFXTabPane;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.Tab;
import javafx.scene.layout.StackPane;
import org.jackhuang.hmcl.download.game.GameAssetIndexDownloadTask;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.setting.Theme;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.IconedMenuItem;
import org.jackhuang.hmcl.ui.construct.Navigator;
import org.jackhuang.hmcl.ui.construct.PageCloseEvent;
import org.jackhuang.hmcl.ui.construct.PopupMenu;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.util.io.FileUtils;

import java.io.File;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public final class VersionPage extends StackPane implements DecoratorPage {
    private final ReadOnlyStringWrapper title = new ReadOnlyStringWrapper(this, "title", null);

    @FXML
    private VersionSettingsPage versionSettings;
    @FXML
    private Tab modTab;
    @FXML
    private ModListPage mod;
    @FXML
    private InstallerListPage installer;
    @FXML
    private WorldListPage world;
    @FXML
    private JFXButton btnBrowseMenu;
    @FXML
    private JFXButton btnDelete;
    @FXML
    private JFXButton btnManagementMenu;
    @FXML
    private JFXButton btnExport;
    @FXML
    private JFXButton btnTestGame;
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

        PopupMenu browseList = new PopupMenu();
        browsePopup = new JFXPopup(browseList);
        browseList.getContent().setAll(
                new IconedMenuItem(null, i18n("folder.game"), FXUtils.withJFXPopupClosing(() -> onBrowse(""), browsePopup)),
                new IconedMenuItem(null, i18n("folder.mod"), FXUtils.withJFXPopupClosing(() -> onBrowse("mods"), browsePopup)),
                new IconedMenuItem(null, i18n("folder.config"), FXUtils.withJFXPopupClosing(() -> onBrowse("config"), browsePopup)),
                new IconedMenuItem(null, i18n("folder.resourcepacks"), FXUtils.withJFXPopupClosing(() -> onBrowse("resourcepacks"), browsePopup)),
                new IconedMenuItem(null, i18n("folder.screenshots"), FXUtils.withJFXPopupClosing(() -> onBrowse("screenshots"), browsePopup)),
                new IconedMenuItem(null, i18n("folder.saves"), FXUtils.withJFXPopupClosing(() -> onBrowse("saves"), browsePopup))
        );

        PopupMenu managementList = new PopupMenu();
        managementPopup = new JFXPopup(managementList);
        managementList.getContent().setAll(
                new IconedMenuItem(null, i18n("version.manage.rename"), FXUtils.withJFXPopupClosing(() -> Versions.renameVersion(profile, version), managementPopup)),
                new IconedMenuItem(null, i18n("version.manage.remove"), FXUtils.withJFXPopupClosing(() -> Versions.deleteVersion(profile, version), managementPopup)),
                new IconedMenuItem(null, i18n("version.manage.redownload_assets_index"), FXUtils.withJFXPopupClosing(() -> new GameAssetIndexDownloadTask(profile.getDependency(), profile.getRepository().getResolvedVersion(version)).start(), managementPopup)),
                new IconedMenuItem(null, i18n("version.manage.remove_libraries"), FXUtils.withJFXPopupClosing(() -> FileUtils.deleteDirectoryQuietly(new File(profile.getRepository().getBaseDirectory(), "libraries")), managementPopup)),
                new IconedMenuItem(null, i18n("version.manage.clean"), FXUtils.withJFXPopupClosing(() -> Versions.cleanVersion(profile, version), managementPopup))
        );

        FXUtils.installTooltip(btnDelete, i18n("version.manage.remove"));
        FXUtils.installTooltip(btnBrowseMenu, i18n("settings.game.exploration"));
        FXUtils.installTooltip(btnManagementMenu, i18n("settings.game.management"));
        FXUtils.installTooltip(btnExport, i18n("modpack.export"));

        btnTestGame.setGraphic(SVG.launch(Theme.whiteFillBinding(), 20, 20));
        FXUtils.installTooltip(btnTestGame, i18n("version.launch.test"));

        setEventHandler(Navigator.NavigationEvent.NAVIGATED, this::onNavigated);
    }

    public void load(String id, Profile profile) {
        this.version = id;
        this.profile = profile;

        title.set(i18n("version.manage.manage") + " - " + id);

        versionSettings.loadVersion(profile, id);
        mod.setParentTab(tabPane);
        modTab.setUserData(mod);
        mod.loadVersion(profile, id);
        installer.loadVersion(profile, id);
        world.loadVersion(profile, id);
    }

    private void onNavigated(Navigator.NavigationEvent event) {
        if (this.version == null || this.profile == null)
            throw new IllegalStateException();

        // If we jumped to game list page and deleted this version
        // and back to this page, we should return to main page.
        if (!this.profile.getRepository().isLoaded() ||
                !this.profile.getRepository().hasVersion(version)) {
            Platform.runLater(() -> fireEvent(new PageCloseEvent()));
            return;
        }

        load(this.version, this.profile);
    }

    @FXML
    private void onTestGame() {
        Versions.testGame(profile, version);
    }

    @FXML
    private void onBrowseMenu() {
        browsePopup.show(btnBrowseMenu, JFXPopup.PopupVPosition.TOP, JFXPopup.PopupHPosition.RIGHT, 0, btnBrowseMenu.getHeight());
    }

    @FXML
    private void onManagementMenu() {
        managementPopup.show(btnManagementMenu, JFXPopup.PopupVPosition.TOP, JFXPopup.PopupHPosition.RIGHT, 0, btnManagementMenu.getHeight());
    }

    private void onBrowse(String sub) {
        FXUtils.openFolder(new File(profile.getRepository().getRunDirectory(version), sub));
    }

    public String getTitle() {
        return title.get();
    }

    @Override
    public ReadOnlyStringProperty titleProperty() {
        return title.getReadOnlyProperty();
    }

    public void setTitle(String title) {
        this.title.set(title);
    }
}
