/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
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
import com.jfoenix.controls.JFXPopup;
import com.jfoenix.controls.JFXTabPane;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.fxml.FXML;
import javafx.scene.control.Tab;
import javafx.scene.layout.StackPane;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.setting.Theme;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.IconedMenuItem;
import org.jackhuang.hmcl.ui.construct.Navigator;
import org.jackhuang.hmcl.ui.construct.PageCloseEvent;
import org.jackhuang.hmcl.ui.construct.PopupMenu;
import org.jackhuang.hmcl.util.io.FileUtils;

import java.io.File;
import java.util.concurrent.CompletableFuture;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public final class VersionPage extends StackPane {
    private final ReadOnlyStringWrapper title = new ReadOnlyStringWrapper(this, "title", null);
    private final ReadOnlyBooleanWrapper loading = new ReadOnlyBooleanWrapper(this, "loading", false);

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
                new IconedMenuItem(null, i18n("version.manage.redownload_assets_index"), FXUtils.withJFXPopupClosing(() -> Versions.updateGameAssets(profile, version), managementPopup)),
                new IconedMenuItem(null, i18n("version.manage.remove_libraries"), FXUtils.withJFXPopupClosing(() -> FileUtils.deleteDirectoryQuietly(new File(profile.getRepository().getBaseDirectory(), "libraries")), managementPopup)),
                new IconedMenuItem(null, i18n("version.manage.clean"), FXUtils.withJFXPopupClosing(() -> Versions.cleanVersion(profile, version), managementPopup)).addTooltip(i18n("version.manage.clean.tooltip"))
        );

        FXUtils.installFastTooltip(btnDelete, i18n("version.manage.remove"));
        FXUtils.installFastTooltip(btnBrowseMenu, i18n("settings.game.exploration"));
        FXUtils.installFastTooltip(btnManagementMenu, i18n("settings.game.management"));
        FXUtils.installFastTooltip(btnExport, i18n("modpack.export"));

        btnTestGame.setGraphic(SVG.launch(Theme.whiteFillBinding(), 20, 20));
        FXUtils.installFastTooltip(btnTestGame, i18n("version.launch.test"));

        addEventHandler(Navigator.NavigationEvent.NAVIGATED, this::onNavigated);
    }

    public void load(String id, Profile profile) {
        this.version = id;
        this.profile = profile;

        title.set(i18n("version.manage.manage") + " - " + id);

        versionSettings.loadVersion(profile, id);
        mod.setParentTab(tabPane);
        modTab.setUserData(mod);
        loading.set(true);

        CompletableFuture.allOf(
                mod.loadVersion(profile, id),
                installer.loadVersion(profile, id),
                world.loadVersion(profile, id))
                .whenCompleteAsync((result, exception) -> loading.set(false), Platform::runLater);
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

    public ReadOnlyStringProperty titleProperty() {
        return title.getReadOnlyProperty();
    }

    public void setTitle(String title) {
        this.title.set(title);
    }

    public boolean isLoading() {
        return loading.get();
    }

    public ReadOnlyBooleanProperty loadingProperty() {
        return loading.getReadOnlyProperty();
    }
}
