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

import com.jfoenix.controls.JFXPopup;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.SkinBase;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import org.jackhuang.hmcl.mod.curse.CurseAddon;
import org.jackhuang.hmcl.mod.curse.CurseModManager;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.setting.Theme;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskExecutor;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.animation.ContainerAnimations;
import org.jackhuang.hmcl.ui.animation.TransitionPane;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.io.NetworkUtils;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.file.Path;
import java.util.Optional;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class VersionPage extends Control implements DecoratorPage, ModDownloadPage.DownloadCallback {
    private final ReadOnlyObjectWrapper<State> state = new ReadOnlyObjectWrapper<>();
    private final BooleanProperty loading = new SimpleBooleanProperty();
    private final TabHeader tab;
    private final TabHeader.Tab<VersionSettingsPage> versionSettingsTab = new TabHeader.Tab<>("versionSettingsTab");
    private final TabHeader.Tab<ModListPage> modListTab = new TabHeader.Tab<>("modListTab");
    private final TabHeader.Tab<ModDownloadListPage> curseModListTab = new TabHeader.Tab<>("modListTab");
    private final TabHeader.Tab<InstallerListPage> installerListTab = new TabHeader.Tab<>("installerListTab");
    private final TabHeader.Tab<WorldListPage> worldListTab = new TabHeader.Tab<>("worldList");
    private final TransitionPane transitionPane = new TransitionPane();
    private final BooleanProperty currentVersionUpgradable = new SimpleBooleanProperty();
    private final ObjectProperty<Profile.ProfileVersion> version = new SimpleObjectProperty<>();

    private String preferredVersionName = null;

    {
        versionSettingsTab.setNodeSupplier(VersionSettingsPage::new);
        modListTab.setNodeSupplier(ModListPage::new);
        curseModListTab.setNodeSupplier(() -> new ModDownloadListPage(CurseModManager.SECTION_MOD, this));
        installerListTab.setNodeSupplier(InstallerListPage::new);
        worldListTab.setNodeSupplier(WorldListPage::new);

        tab = new TabHeader(versionSettingsTab, modListTab, curseModListTab, installerListTab, worldListTab);

        addEventHandler(Navigator.NavigationEvent.NAVIGATED, this::onNavigated);

        tab.getSelectionModel().select(versionSettingsTab);
        FXUtils.onChangeAndOperate(tab.getSelectionModel().selectedItemProperty(), newValue -> {
            if (newValue.initializeIfNeeded()) {
                if (this.version.get() != null) {
                    if (newValue.getNode() instanceof VersionLoadable) {
                        ((VersionLoadable) newValue.getNode()).loadVersion(version.get().getProfile(), version.get().getVersion());
                    }
                }
            }

            transitionPane.setContent(newValue.getNode(), ContainerAnimations.FADE.getAnimationProducer());
        });
    }

    public void setVersion(String version, Profile profile) {
        this.version.set(new Profile.ProfileVersion(profile, version));
    }

    public void loadVersion(String version, Profile profile) {
        // If we jumped to game list page and deleted this version
        // and back to this page, we should return to main page.
        if (this.version.get() != null && (!getProfile().getRepository().isLoaded() ||
                !getProfile().getRepository().hasVersion(version))) {
            Platform.runLater(() -> fireEvent(new PageCloseEvent()));
            return;
        }

        setVersion(version, profile);
        preferredVersionName = version;

        if (versionSettingsTab.isInitialized())
            versionSettingsTab.getNode().loadVersion(profile, version);
        if (modListTab.isInitialized())
            modListTab.getNode().loadVersion(profile, version);
        if (curseModListTab.isInitialized())
            curseModListTab.getNode().loadVersion(profile, version);
        if (installerListTab.isInitialized())
            installerListTab.getNode().loadVersion(profile, version);
        if (worldListTab.isInitialized())
            worldListTab.getNode().loadVersion(profile, version);
        currentVersionUpgradable.set(profile.getRepository().isModpack(version));
    }

    private void onNavigated(Navigator.NavigationEvent event) {
        if (this.version.get() == null)
            throw new IllegalStateException();

        // If we jumped to game list page and deleted this version
        // and back to this page, we should return to main page.
        if (!getProfile().getRepository().isLoaded() ||
                !getProfile().getRepository().hasVersion(getVersion())) {
            Platform.runLater(() -> fireEvent(new PageCloseEvent()));
            return;
        }

        loadVersion(getVersion(), getProfile());
    }

    private void onBrowse(String sub) {
        FXUtils.openFolder(new File(getProfile().getRepository().getRunDirectory(getVersion()), sub));
    }

    private void redownloadAssetIndex() {
        Versions.updateGameAssets(getProfile(), getVersion());
    }

    private void clearLibraries() {
        FileUtils.deleteDirectoryQuietly(new File(getProfile().getRepository().getBaseDirectory(), "libraries"));
    }

    private void clearJunkFiles() {
        Versions.cleanVersion(getProfile(), getVersion());
    }

    private void testGame() {
        Versions.testGame(getProfile(), getVersion());
    }

    private void updateGame() {
        Versions.updateVersion(getProfile(), getVersion());
    }

    private void generateLaunchScript() {
        Versions.generateLaunchScript(getProfile(), getVersion());
    }

    private void export() {
        Versions.exportVersion(getProfile(), getVersion());
    }

    private void rename() {
        Versions.renameVersion(getProfile(), getVersion())
                .thenApply(newVersionName -> this.preferredVersionName = newVersionName);
    }

    private void remove() {
        Versions.deleteVersion(getProfile(), getVersion());
    }

    private void duplicate() {
        Versions.duplicateVersion(getProfile(), getVersion());
    }

    public Profile getProfile() {
        return Optional.ofNullable(version.get()).map(Profile.ProfileVersion::getProfile).orElse(null);
    }

    public String getVersion() {
        return Optional.ofNullable(version.get()).map(Profile.ProfileVersion::getVersion).orElse(null);
    }

    @Override
    protected Skin createDefaultSkin() {
        return new Skin(this);
    }

    @Override
    public ReadOnlyObjectProperty<State> stateProperty() {
        return state.getReadOnlyProperty();
    }

    @Override
    public void download(Profile profile, @Nullable String version, CurseAddon.LatestFile file) {
        if (version == null) {
            throw new InternalError();
        }

        Path dest = profile.getRepository().getRunDirectory(version).toPath().resolve("mods").resolve(file.getFileName());

        TaskExecutorDialogPane downloadingPane = new TaskExecutorDialogPane(it -> {
        });

        TaskExecutor executor = Task.composeAsync(() -> {
            FileDownloadTask task = new FileDownloadTask(NetworkUtils.toURL(file.getDownloadUrl()), dest.toFile());
            task.setName(file.getDisplayName());
            return task;
        }).executor(false);

        downloadingPane.setExecutor(executor, true);
        Controllers.dialog(downloadingPane);
        executor.start();
    }

    public static class Skin extends SkinBase<VersionPage> {

        /**
         * Constructor for all SkinBase instances.
         *
         * @param control The control for which this Skin should attach to.
         */
        protected Skin(VersionPage control) {
            super(control);

            SpinnerPane spinnerPane = new SpinnerPane();
            spinnerPane.getStyleClass().add("large-spinner-pane");

            // the root page, with the sidebar in left, navigator in center.
            BorderPane root = new BorderPane();

            {
                BorderPane left = new BorderPane();
                FXUtils.setLimitWidth(left, 200);
                root.setLeft(left);

                AdvancedListItem versionSettingsItem = new AdvancedListItem();
                versionSettingsItem.getStyleClass().add("navigation-drawer-item");
                versionSettingsItem.setTitle(i18n("settings.game"));
                versionSettingsItem.setLeftGraphic(wrap(SVG.gearOutline(null, 20, 20)));
                versionSettingsItem.setActionButtonVisible(false);
                versionSettingsItem.activeProperty().bind(control.tab.getSelectionModel().selectedItemProperty().isEqualTo(control.versionSettingsTab));
                versionSettingsItem.setOnAction(e -> control.tab.getSelectionModel().select(control.versionSettingsTab));

                AdvancedListItem modListItem = new AdvancedListItem();
                modListItem.getStyleClass().add("navigation-drawer-item");
                modListItem.setTitle(i18n("mods.manage"));
                modListItem.setLeftGraphic(wrap(SVG.puzzle(null, 20, 20)));
                modListItem.setActionButtonVisible(false);
                modListItem.activeProperty().bind(control.tab.getSelectionModel().selectedItemProperty().isEqualTo(control.modListTab));
                modListItem.setOnAction(e -> control.tab.getSelectionModel().select(control.modListTab));

                AdvancedListItem curseModListItem = new AdvancedListItem();
                curseModListItem.getStyleClass().add("navigation-drawer-item");
                curseModListItem.setTitle(i18n("mods.download"));
                curseModListItem.setLeftGraphic(wrap(SVG.fire(null, 20, 20)));
                curseModListItem.setActionButtonVisible(false);
                curseModListItem.activeProperty().bind(control.tab.getSelectionModel().selectedItemProperty().isEqualTo(control.curseModListTab));
                curseModListItem.setOnAction(e -> control.tab.getSelectionModel().select(control.curseModListTab));

                AdvancedListItem installerListItem = new AdvancedListItem();
                installerListItem.getStyleClass().add("navigation-drawer-item");
                installerListItem.setTitle(i18n("settings.tabs.installers"));
                installerListItem.setLeftGraphic(wrap(SVG.cube(null, 20, 20)));
                installerListItem.setActionButtonVisible(false);
                installerListItem.activeProperty().bind(control.tab.getSelectionModel().selectedItemProperty().isEqualTo(control.installerListTab));
                installerListItem.setOnAction(e -> control.tab.getSelectionModel().select(control.installerListTab));

                AdvancedListItem worldListItem = new AdvancedListItem();
                worldListItem.getStyleClass().add("navigation-drawer-item");
                worldListItem.setTitle(i18n("world.manage"));
                worldListItem.setLeftGraphic(wrap(SVG.earth(null, 20, 20)));
                worldListItem.setActionButtonVisible(false);
                worldListItem.activeProperty().bind(control.tab.getSelectionModel().selectedItemProperty().isEqualTo(control.worldListTab));
                worldListItem.setOnAction(e -> control.tab.getSelectionModel().select(control.worldListTab));

                AdvancedListBox sideBar = new AdvancedListBox()
                        .add(versionSettingsItem)
                        .add(modListItem)
                        .add(curseModListItem)
                        .add(installerListItem)
                        .add(worldListItem);
                left.setCenter(sideBar);


                PopupMenu browseList = new PopupMenu();
                JFXPopup browsePopup = new JFXPopup(browseList);
                browseList.getContent().setAll(
                        new IconedMenuItem(FXUtils.limitingSize(SVG.gamepad(Theme.blackFillBinding(), 14, 14), 14, 14), i18n("folder.game"), FXUtils.withJFXPopupClosing(() -> control.onBrowse(""), browsePopup)),
                        new IconedMenuItem(FXUtils.limitingSize(SVG.puzzle(Theme.blackFillBinding(), 14, 14), 14, 14), i18n("folder.mod"), FXUtils.withJFXPopupClosing(() -> control.onBrowse("mods"), browsePopup)),
                        new IconedMenuItem(FXUtils.limitingSize(SVG.gearOutline(Theme.blackFillBinding(), 14, 14), 14, 14), i18n("folder.config"), FXUtils.withJFXPopupClosing(() -> control.onBrowse("config"), browsePopup)),
                        new IconedMenuItem(FXUtils.limitingSize(SVG.texture(Theme.blackFillBinding(), 14, 14), 14, 14), i18n("folder.resourcepacks"), FXUtils.withJFXPopupClosing(() -> control.onBrowse("resourcepacks"), browsePopup)),
                        new IconedMenuItem(FXUtils.limitingSize(SVG.monitorScreenshot(Theme.blackFillBinding(), 14, 14), 14, 14), i18n("folder.screenshots"), FXUtils.withJFXPopupClosing(() -> control.onBrowse("screenshots"), browsePopup)),
                        new IconedMenuItem(FXUtils.limitingSize(SVG.earth(Theme.blackFillBinding(), 14, 14), 14, 14), i18n("folder.saves"), FXUtils.withJFXPopupClosing(() -> control.onBrowse("saves"), browsePopup))
                );

                PopupMenu managementList = new PopupMenu();
                JFXPopup managementPopup = new JFXPopup(managementList);
                managementList.getContent().setAll(
                        new IconedMenuItem(FXUtils.limitingSize(SVG.rocketLaunchOutline(Theme.blackFillBinding(), 14, 14), 14, 14), i18n("version.launch.test"), FXUtils.withJFXPopupClosing(control::testGame, managementPopup)),
                        new IconedMenuItem(FXUtils.limitingSize(SVG.script(Theme.blackFillBinding(), 14, 14), 14, 14), i18n("version.launch_script"), FXUtils.withJFXPopupClosing(control::generateLaunchScript, managementPopup)),
                        new MenuSeparator(),
                        new IconedMenuItem(FXUtils.limitingSize(SVG.pencil(Theme.blackFillBinding(), 14, 14), 14, 14), i18n("version.manage.rename"), FXUtils.withJFXPopupClosing(control::rename, managementPopup)),
                        new IconedMenuItem(FXUtils.limitingSize(SVG.copy(Theme.blackFillBinding(), 14, 14), 14, 14), i18n("version.manage.duplicate"), FXUtils.withJFXPopupClosing(control::duplicate, managementPopup)),
                        new IconedMenuItem(FXUtils.limitingSize(SVG.deleteOutline(Theme.blackFillBinding(), 14, 14), 14, 14), i18n("version.manage.remove"), FXUtils.withJFXPopupClosing(control::remove, managementPopup)),
                        new IconedMenuItem(FXUtils.limitingSize(SVG.export(Theme.blackFillBinding(), 14, 14), 14, 14), i18n("modpack.export"), FXUtils.withJFXPopupClosing(control::export, managementPopup)),
                        new MenuSeparator(),
                        new IconedMenuItem(null, i18n("version.manage.redownload_assets_index"), FXUtils.withJFXPopupClosing(control::redownloadAssetIndex, managementPopup)),
                        new IconedMenuItem(null, i18n("version.manage.remove_libraries"), FXUtils.withJFXPopupClosing(control::clearLibraries, managementPopup)),
                        new IconedMenuItem(null, i18n("version.manage.clean"), FXUtils.withJFXPopupClosing(control::clearJunkFiles, managementPopup)).addTooltip(i18n("version.manage.clean.tooltip"))
                );

                AdvancedListItem upgradeItem = new AdvancedListItem();
                upgradeItem.getStyleClass().add("navigation-drawer-item");
                upgradeItem.setTitle(i18n("version.update"));
                upgradeItem.setLeftGraphic(wrap(SVG.update(Theme.blackFillBinding(), 20, 20)));
                upgradeItem.setActionButtonVisible(false);
                upgradeItem.visibleProperty().bind(control.currentVersionUpgradable);
                upgradeItem.setOnAction(e -> control.updateGame());

                AdvancedListItem testGameItem = new AdvancedListItem();
                testGameItem.getStyleClass().add("navigation-drawer-item");
                testGameItem.setTitle(i18n("version.launch.test"));
                testGameItem.setLeftGraphic(wrap(SVG.rocketLaunchOutline(Theme.blackFillBinding(), 20, 20)));
                testGameItem.setActionButtonVisible(false);
                testGameItem.setOnAction(e -> control.testGame());

                AdvancedListItem browseMenuItem = new AdvancedListItem();
                browseMenuItem.getStyleClass().add("navigation-drawer-item");
                browseMenuItem.setTitle(i18n("settings.game.exploration"));
                browseMenuItem.setLeftGraphic(wrap(SVG.folderOutline(Theme.blackFillBinding(), 20, 20)));
                browseMenuItem.setActionButtonVisible(false);
                browseMenuItem.setOnAction(e -> browsePopup.show(browseMenuItem, JFXPopup.PopupVPosition.BOTTOM, JFXPopup.PopupHPosition.LEFT, browseMenuItem.getWidth(), 0));

                AdvancedListItem managementItem = new AdvancedListItem();
                managementItem.getStyleClass().add("navigation-drawer-item");
                managementItem.setTitle(i18n("settings.game.management"));
                managementItem.setLeftGraphic(wrap(SVG.wrenchOutline(Theme.blackFillBinding(), 20, 20)));
                managementItem.setActionButtonVisible(false);
                managementItem.setOnAction(e -> managementPopup.show(managementItem, JFXPopup.PopupVPosition.BOTTOM, JFXPopup.PopupHPosition.LEFT, managementItem.getWidth(), 0));


                AdvancedListBox toolbar = new AdvancedListBox()
                        .add(upgradeItem)
                        .add(testGameItem)
                        .add(browseMenuItem)
                        .add(managementItem);
                toolbar.getStyleClass().add("advanced-list-box-clear-padding");
                FXUtils.setLimitHeight(toolbar, 40 * 4 + 12 * 2);
                left.setBottom(toolbar);
            }

            control.state.bind(Bindings.createObjectBinding(() ->
                            State.fromTitle(i18n("version.manage.manage.title", getSkinnable().getVersion()), -1),
                    getSkinnable().version));

            //control.transitionPane.getStyleClass().add("gray-background");
            //FXUtils.setOverflowHidden(control.transitionPane, 8);
            root.setCenter(control.transitionPane);

            spinnerPane.loadingProperty().bind(control.loading);
            spinnerPane.setContent(root);
            getChildren().setAll(spinnerPane);
        }
    }

    public static Node wrap(Node node) {
        StackPane stackPane = new StackPane();
        FXUtils.setLimitWidth(stackPane, 30);
        FXUtils.setLimitHeight(stackPane, 20);
        stackPane.setPadding(new Insets(0, 10, 0, 0));
        stackPane.getChildren().setAll(node);
        return stackPane;
    }

    public interface VersionLoadable {
        void loadVersion(Profile profile, String version);
    }
}
