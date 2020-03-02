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
import com.jfoenix.controls.JFXListView;
import com.jfoenix.controls.JFXPopup;
import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.geometry.Pos;
import javafx.scene.control.Control;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.SkinBase;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import org.jackhuang.hmcl.game.HMCLGameRepository;
import org.jackhuang.hmcl.game.Version;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.setting.Profiles;
import org.jackhuang.hmcl.setting.Theme;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.animation.ContainerAnimations;
import org.jackhuang.hmcl.ui.animation.TransitionPane;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.versioning.VersionNumber;

import java.io.File;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static org.jackhuang.hmcl.ui.FXUtils.runInFX;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class VersionPage extends Control implements DecoratorPage {
    private final ReadOnlyObjectWrapper<State> state = new ReadOnlyObjectWrapper<>();
    private final BooleanProperty loading = new SimpleBooleanProperty();
    private final JFXListView<String> listView = new JFXListView<>();
    private final TabHeader.Tab versionSettingsTab = new TabHeader.Tab("versionSettingsTab");
    private final VersionSettingsPage versionSettingsPage = new VersionSettingsPage();
    private final TabHeader.Tab modListTab = new TabHeader.Tab("modListTab");
    private final ModListPage modListPage = new ModListPage(modListTab);
    private final TabHeader.Tab installerListTab = new TabHeader.Tab("installerListTab");
    private final InstallerListPage installerListPage = new InstallerListPage();
    private final TabHeader.Tab worldListTab = new TabHeader.Tab("worldList");
    private final WorldListPage worldListPage = new WorldListPage();
    private final TransitionPane transitionPane = new TransitionPane();
    private final ObjectProperty<TabHeader.Tab> selectedTab = new SimpleObjectProperty<>();

    private Profile profile;
    private String version;

    {
        Profiles.registerVersionsListener(this::loadVersions);

        listView.getSelectionModel().selectedItemProperty().addListener((a, b, newValue) -> {
            loadVersion(newValue, profile);
        });

        versionSettingsTab.setNode(versionSettingsPage);
        modListTab.setNode(modListPage);
        installerListTab.setNode(installerListPage);
        worldListTab.setNode(worldListPage);

        addEventHandler(Navigator.NavigationEvent.NAVIGATED, this::onNavigated);
    }

    private void loadVersions(Profile profile) {
        HMCLGameRepository repository = profile.getRepository();
        List<String> children = repository.getVersions().parallelStream()
                .filter(version -> !version.isHidden())
                .sorted(Comparator.comparing((Version version) -> version.getReleaseTime() == null ? new Date(0L) : version.getReleaseTime())
                        .thenComparing(a -> VersionNumber.asVersion(a.getId())))
                .map(Version::getId)
                .collect(Collectors.toList());
        runInFX(() -> {
            if (profile == Profiles.getSelectedProfile()) {
                this.profile = profile;
                loading.set(false);
                listView.getItems().setAll(children);
            }
        });
    }

    public void loadVersion(String version, Profile profile) {
        listView.getSelectionModel().select(version);
        this.version = version;
        this.profile = profile;

        versionSettingsPage.loadVersion(profile, version);
        loading.set(true);

        CompletableFuture.allOf(
                modListPage.loadVersion(profile, version),
                installerListPage.loadVersion(profile, version),
                worldListPage.loadVersion(profile, version))
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

        loadVersion(this.version, this.profile);
    }

    private void onBrowse(String sub) {
        FXUtils.openFolder(new File(profile.getRepository().getRunDirectory(version), sub));
    }

    private void redownloadAssetIndex() {
        Versions.updateGameAssets(profile, version);
    }

    private void clearLibraries() {
        FileUtils.deleteDirectoryQuietly(new File(profile.getRepository().getBaseDirectory(), "libraries"));
    }

    private void clearJunkFiles() {
        Versions.cleanVersion(profile, version);
    }

    private void testGame() {
        Versions.testGame(profile, version);
    }

    @Override
    protected Skin createDefaultSkin() {
        return new Skin(this);
    }

    @Override
    public ReadOnlyObjectProperty<State> stateProperty() {
        return state.getReadOnlyProperty();
    }

    public static class Skin extends SkinBase<VersionPage> {

        /**
         * Constructor for all SkinBase instances.
         *
         * @param control The control for which this Skin should attach to.
         */
        protected Skin(VersionPage control) {
            super(control);

            control.listView.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);

            SpinnerPane spinnerPane = new SpinnerPane();
            spinnerPane.getStyleClass().add("large-spinner-pane");

            // the root page, with the sidebar in left, navigator in center.
            BorderPane root = new BorderPane();
            root.getStyleClass().add("gray-background");

            {
                BorderPane leftRootPane = new BorderPane();
                FXUtils.setLimitWidth(leftRootPane, 200);

                StackPane drawerContainer = new StackPane();
                drawerContainer.getChildren().setAll(control.listView);
                leftRootPane.setCenter(drawerContainer);

                Rectangle separator = new Rectangle();
                separator.heightProperty().bind(root.heightProperty());
                separator.setWidth(1);
                separator.setFill(Color.GRAY);

                leftRootPane.setRight(separator);

                root.setLeft(leftRootPane);
            }

            TabHeader tabPane = new TabHeader();
            tabPane.setPickOnBounds(false);
            tabPane.getStyleClass().add("jfx-decorator-tab");
            control.versionSettingsTab.setText(i18n("settings"));
            control.modListTab.setText(i18n("mods"));
            control.installerListTab.setText(i18n("settings.tabs.installers"));
            control.worldListTab.setText(i18n("world"));
            tabPane.getTabs().setAll(
                    control.versionSettingsTab,
                    control.modListTab,
                    control.installerListTab,
                    control.worldListTab);
            control.selectedTab.bind(tabPane.getSelectionModel().selectedItemProperty());
            FXUtils.onChangeAndOperate(tabPane.getSelectionModel().selectedItemProperty(), newValue -> {
                control.transitionPane.setContent(newValue.getNode(), ContainerAnimations.FADE.getAnimationProducer());
            });

            HBox toolBar = new HBox();
            toolBar.setAlignment(Pos.TOP_RIGHT);
            toolBar.setPickOnBounds(false);
            {
                PopupMenu browseList = new PopupMenu();
                JFXPopup browsePopup = new JFXPopup(browseList);
                browseList.getContent().setAll(
                        new IconedMenuItem(null, i18n("folder.game"), FXUtils.withJFXPopupClosing(() -> control.onBrowse(""), browsePopup)),
                        new IconedMenuItem(null, i18n("folder.mod"), FXUtils.withJFXPopupClosing(() -> control.onBrowse("mods"), browsePopup)),
                        new IconedMenuItem(null, i18n("folder.config"), FXUtils.withJFXPopupClosing(() -> control.onBrowse("config"), browsePopup)),
                        new IconedMenuItem(null, i18n("folder.resourcepacks"), FXUtils.withJFXPopupClosing(() -> control.onBrowse("resourcepacks"), browsePopup)),
                        new IconedMenuItem(null, i18n("folder.screenshots"), FXUtils.withJFXPopupClosing(() -> control.onBrowse("screenshots"), browsePopup)),
                        new IconedMenuItem(null, i18n("folder.saves"), FXUtils.withJFXPopupClosing(() -> control.onBrowse("saves"), browsePopup))
                );

                PopupMenu managementList = new PopupMenu();
                JFXPopup managementPopup = new JFXPopup(managementList);
                managementList.getContent().setAll(
                        new IconedMenuItem(null, i18n("version.manage.redownload_assets_index"), FXUtils.withJFXPopupClosing(control::redownloadAssetIndex, managementPopup)),
                        new IconedMenuItem(null, i18n("version.manage.remove_libraries"), FXUtils.withJFXPopupClosing(control::clearLibraries, managementPopup)),
                        new IconedMenuItem(null, i18n("version.manage.clean"), FXUtils.withJFXPopupClosing(control::clearJunkFiles, managementPopup)).addTooltip(i18n("version.manage.clean.tooltip"))
                );

                JFXButton testGameButton = new JFXButton();
                FXUtils.setLimitWidth(testGameButton, 40);
                FXUtils.setLimitHeight(testGameButton, 40);
                testGameButton.setGraphic(SVG.launch(Theme.whiteFillBinding(), 20, 20));
                testGameButton.getStyleClass().add("jfx-decorator-button");
                testGameButton.ripplerFillProperty().bind(Theme.whiteFillBinding());
                testGameButton.setOnAction(event -> control.testGame());
                FXUtils.installFastTooltip(testGameButton, i18n("version.launch.test"));

                JFXButton browseMenuButton = new JFXButton();
                FXUtils.setLimitWidth(browseMenuButton, 40);
                FXUtils.setLimitHeight(browseMenuButton, 40);
                browseMenuButton.setGraphic(SVG.folderOpen(Theme.whiteFillBinding(), 20, 20));
                browseMenuButton.getStyleClass().add("jfx-decorator-button");
                browseMenuButton.ripplerFillProperty().bind(Theme.whiteFillBinding());
                browseMenuButton.setOnAction(event -> browsePopup.show(browseMenuButton, JFXPopup.PopupVPosition.TOP, JFXPopup.PopupHPosition.RIGHT, 0, browseMenuButton.getHeight()));
                FXUtils.installFastTooltip(browseMenuButton, i18n("settings.game.exploration"));

                JFXButton managementMenuButton = new JFXButton();
                FXUtils.setLimitWidth(managementMenuButton, 40);
                FXUtils.setLimitHeight(managementMenuButton, 40);;
                managementMenuButton.setGraphic(SVG.wrench(Theme.whiteFillBinding(), 20, 20));
                managementMenuButton.getStyleClass().add("jfx-decorator-button");
                managementMenuButton.ripplerFillProperty().bind(Theme.whiteFillBinding());
                managementMenuButton.setOnAction(event -> managementPopup.show(managementMenuButton, JFXPopup.PopupVPosition.TOP, JFXPopup.PopupHPosition.RIGHT, 0, managementMenuButton.getHeight()));
                FXUtils.installFastTooltip(managementMenuButton, i18n("settings.game.management"));

                toolBar.getChildren().setAll(testGameButton, browseMenuButton, managementMenuButton);
            }

            BorderPane titleBar = new BorderPane();
            titleBar.setLeft(tabPane);
            titleBar.setRight(toolBar);
            control.state.set(new State(i18n("version.manage.manage"), titleBar, true, false, true));

            root.setCenter(control.transitionPane);

            spinnerPane.loadingProperty().bind(control.loading);
            spinnerPane.setContent(root);
            getChildren().setAll(spinnerPane);
        }
    }
}
