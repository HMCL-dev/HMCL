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

import com.jfoenix.controls.JFXPopup;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Paint;
import org.jackhuang.hmcl.event.EventBus;
import org.jackhuang.hmcl.event.EventPriority;
import org.jackhuang.hmcl.event.RefreshedVersionsEvent;
import org.jackhuang.hmcl.game.GameRepository;
import org.jackhuang.hmcl.game.HMCLGameRepository;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.WeakListenerHolder;
import org.jackhuang.hmcl.ui.animation.ContainerAnimations;
import org.jackhuang.hmcl.ui.animation.TransitionPane;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.ui.decorator.DecoratorAnimatedPage;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.util.io.FileUtils;

import java.io.File;
import java.util.Optional;
import java.util.function.Supplier;

import static org.jackhuang.hmcl.ui.FXUtils.runInFX;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class VersionPage extends DecoratorAnimatedPage implements DecoratorPage {
    private final ReadOnlyObjectWrapper<State> state = new ReadOnlyObjectWrapper<>();
    private final TabHeader tab;
    private final TabHeader.Tab<VersionSettingsPage> versionSettingsTab = new TabHeader.Tab<>("versionSettingsTab");
    private final TabHeader.Tab<InstallerListPage> installerListTab = new TabHeader.Tab<>("installerListTab");
    private final TabHeader.Tab<ModListPage> modListTab = new TabHeader.Tab<>("modListTab");
    private final TabHeader.Tab<WorldListPage> worldListTab = new TabHeader.Tab<>("worldList");
    private final TabHeader.Tab<SchematicsPage> schematicsTab = new TabHeader.Tab<>("schematicsTab");
    private final TransitionPane transitionPane = new TransitionPane();
    private final BooleanProperty currentVersionUpgradable = new SimpleBooleanProperty();
    private final ObjectProperty<Profile.ProfileVersion> version = new SimpleObjectProperty<>();
    private final WeakListenerHolder listenerHolder = new WeakListenerHolder();

    private String preferredVersionName = null;

    public VersionPage() {
        versionSettingsTab.setNodeSupplier(loadVersionFor(() -> new VersionSettingsPage(false)));
        installerListTab.setNodeSupplier(loadVersionFor(InstallerListPage::new));
        modListTab.setNodeSupplier(loadVersionFor(ModListPage::new));
        worldListTab.setNodeSupplier(loadVersionFor(WorldListPage::new));
        schematicsTab.setNodeSupplier(loadVersionFor(SchematicsPage::new));

        tab = new TabHeader(versionSettingsTab, installerListTab, modListTab, worldListTab, schematicsTab);

        addEventHandler(Navigator.NavigationEvent.NAVIGATED, this::onNavigated);

        tab.select(versionSettingsTab);
        transitionPane.setContent(versionSettingsTab.getNode(), ContainerAnimations.NONE);
        FXUtils.onChange(tab.getSelectionModel().selectedItemProperty(), newValue -> {
            transitionPane.setContent(newValue.getNode(), ContainerAnimations.FADE);
        });

        listenerHolder.add(EventBus.EVENT_BUS.channel(RefreshedVersionsEvent.class).registerWeak(event -> checkSelectedVersion(), EventPriority.HIGHEST));
    }

    private void checkSelectedVersion() {
        runInFX(() -> {
            if (this.version.get() == null) return;
            GameRepository repository = this.version.get().getProfile().getRepository();
            if (!repository.hasVersion(this.version.get().getVersion())) {
                if (preferredVersionName != null) {
                    loadVersion(preferredVersionName, this.version.get().getProfile());
                } else {
                    fireEvent(new PageCloseEvent());
                }
            }
        });
    }

    private <T extends Node> Supplier<T> loadVersionFor(Supplier<T> nodeSupplier) {
        return () -> {
            T node = nodeSupplier.get();
            if (version.get() != null) {
                if (node instanceof VersionPage.VersionLoadable) {
                    ((VersionLoadable) node).loadVersion(version.get().getProfile(), version.get().getVersion());
                }
            }
            return node;
        };
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
        if (installerListTab.isInitialized())
            installerListTab.getNode().loadVersion(profile, version);
        if (modListTab.isInitialized())
            modListTab.getNode().loadVersion(profile, version);
        if (worldListTab.isInitialized())
            worldListTab.getNode().loadVersion(profile, version);
        if (schematicsTab.isInitialized())
            schematicsTab.getNode().loadVersion(profile, version);
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

    private void clearAssets() {
        HMCLGameRepository baseDirectory = getProfile().getRepository();
        FileUtils.deleteDirectoryQuietly(new File(baseDirectory.getBaseDirectory(), "assets"));
        if (version.get() != null) {
            FileUtils.deleteDirectoryQuietly(new File(baseDirectory.getRunDirectory(version.get().getVersion()), "resources"));
        }
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

    public static class Skin extends DecoratorAnimatedPageSkin<VersionPage> {

        /**
         * Constructor for all SkinBase instances.
         *
         * @param control The control for which this Skin should attach to.
         */
        protected Skin(VersionPage control) {
            super(control);

            {
                BorderPane left = new BorderPane();
                FXUtils.setLimitWidth(left, 200);
                setLeft(left);

                AdvancedListItem versionSettingsItem = new AdvancedListItem();
                versionSettingsItem.getStyleClass().add("navigation-drawer-item");
                versionSettingsItem.setTitle(i18n("settings.game"));
                versionSettingsItem.setLeftGraphic(wrap(SVG.SETTINGS));
                versionSettingsItem.setActionButtonVisible(false);
                versionSettingsItem.activeProperty().bind(control.tab.getSelectionModel().selectedItemProperty().isEqualTo(control.versionSettingsTab));
                versionSettingsItem.setOnAction(e -> control.tab.select(control.versionSettingsTab));

                AdvancedListItem installerListItem = new AdvancedListItem();
                installerListItem.getStyleClass().add("navigation-drawer-item");
                installerListItem.setTitle(i18n("settings.tabs.installers"));
                installerListItem.setLeftGraphic(wrap(SVG.DEPLOYED_CODE));
                installerListItem.setActionButtonVisible(false);
                installerListItem.activeProperty().bind(control.tab.getSelectionModel().selectedItemProperty().isEqualTo(control.installerListTab));
                installerListItem.setOnAction(e -> control.tab.select(control.installerListTab));

                AdvancedListItem modListItem = new AdvancedListItem();
                modListItem.getStyleClass().add("navigation-drawer-item");
                modListItem.setTitle(i18n("mods.manage"));
                modListItem.setLeftGraphic(wrap(SVG.EXTENSION));
                modListItem.setActionButtonVisible(false);
                modListItem.activeProperty().bind(control.tab.getSelectionModel().selectedItemProperty().isEqualTo(control.modListTab));
                modListItem.setOnAction(e -> control.tab.select(control.modListTab));

                AdvancedListItem worldListItem = new AdvancedListItem();
                worldListItem.getStyleClass().add("navigation-drawer-item");
                worldListItem.setTitle(i18n("world.manage"));
                worldListItem.setLeftGraphic(wrap(SVG.PUBLIC));
                worldListItem.setActionButtonVisible(false);
                worldListItem.activeProperty().bind(control.tab.getSelectionModel().selectedItemProperty().isEqualTo(control.worldListTab));
                worldListItem.setOnAction(e -> control.tab.select(control.worldListTab));

                AdvancedListItem schematicsListItem = new AdvancedListItem();
                schematicsListItem.getStyleClass().add("navigation-drawer-item");
                schematicsListItem.setTitle(i18n("schematics.manage"));
                schematicsListItem.setLeftGraphic(wrap(SVG.SCHEMA));
                schematicsListItem.setActionButtonVisible(false);
                schematicsListItem.activeProperty().bind(control.tab.getSelectionModel().selectedItemProperty().isEqualTo(control.schematicsTab));
                schematicsListItem.setOnAction(e -> control.tab.select(control.schematicsTab));

                AdvancedListBox sideBar = new AdvancedListBox()
                        .add(versionSettingsItem)
                        .add(installerListItem)
                        .add(modListItem)
                        .add(worldListItem)
                        .add(schematicsListItem);
                VBox.setVgrow(sideBar, Priority.ALWAYS);

                PopupMenu browseList = new PopupMenu();
                JFXPopup browsePopup = new JFXPopup(browseList);
                browseList.getContent().setAll(
                        new IconedMenuItem(SVG.STADIA_CONTROLLER, i18n("folder.game"), () -> control.onBrowse(""), browsePopup),
                        new IconedMenuItem(SVG.EXTENSION, i18n("folder.mod"), () -> control.onBrowse("mods"), browsePopup),
                        new IconedMenuItem(SVG.SETTINGS, i18n("folder.config"), () -> control.onBrowse("config"), browsePopup),
                        new IconedMenuItem(SVG.TEXTURE, i18n("folder.resourcepacks"), () -> control.onBrowse("resourcepacks"), browsePopup),
                        new IconedMenuItem(SVG.WB_SUNNY, i18n("folder.shaderpacks"), () -> control.onBrowse("shaderpacks"), browsePopup),
                        new IconedMenuItem(SVG.SCHEMA, i18n("folder.schematics"), () -> control.onBrowse("schematics"), browsePopup),
                        new IconedMenuItem(SVG.SCREENSHOT_MONITOR, i18n("folder.screenshots"), () -> control.onBrowse("screenshots"), browsePopup),
                        new IconedMenuItem(SVG.PUBLIC, i18n("folder.saves"), () -> control.onBrowse("saves"), browsePopup),
                        new IconedMenuItem(SVG.SCRIPT, i18n("folder.logs"), () -> control.onBrowse("logs"), browsePopup)
                );

                PopupMenu managementList = new PopupMenu();
                JFXPopup managementPopup = new JFXPopup(managementList);
                managementList.getContent().setAll(
                        new IconedMenuItem(SVG.ROCKET_LAUNCH, i18n("version.launch.test"), control::testGame, managementPopup),
                        new IconedMenuItem(SVG.SCRIPT, i18n("version.launch_script"), control::generateLaunchScript, managementPopup),
                        new MenuSeparator(),
                        new IconedMenuItem(SVG.EDIT, i18n("version.manage.rename"), control::rename, managementPopup),
                        new IconedMenuItem(SVG.FOLDER_COPY, i18n("version.manage.duplicate"), control::duplicate, managementPopup),
                        new IconedMenuItem(SVG.DELETE, i18n("version.manage.remove"), control::remove, managementPopup),
                        new IconedMenuItem(SVG.OUTPUT, i18n("modpack.export"), control::export, managementPopup),
                        new MenuSeparator(),
                        new IconedMenuItem(null, i18n("version.manage.redownload_assets_index"), control::redownloadAssetIndex, managementPopup),
                        new IconedMenuItem(null, i18n("version.manage.remove_assets"), control::clearAssets, managementPopup),
                        new IconedMenuItem(null, i18n("version.manage.remove_libraries"), control::clearLibraries, managementPopup),
                        new IconedMenuItem(null, i18n("version.manage.clean"), control::clearJunkFiles, managementPopup).addTooltip(i18n("version.manage.clean.tooltip"))
                );

                AdvancedListBox toolbar = new AdvancedListBox()
                        .addNavigationDrawerItem(i18n("version.update"), SVG.UPDATE, control::updateGame, upgradeItem -> {
                            upgradeItem.visibleProperty().bind(control.currentVersionUpgradable);
                        })
                        .addNavigationDrawerItem(i18n("version.launch.test"), SVG.ROCKET_LAUNCH, control::testGame)
                        .addNavigationDrawerItem(i18n("settings.game.exploration"), SVG.FOLDER_OPEN, null, browseMenuItem -> {
                            browseMenuItem.setOnAction(e -> browsePopup.show(browseMenuItem, JFXPopup.PopupVPosition.BOTTOM, JFXPopup.PopupHPosition.LEFT, browseMenuItem.getWidth(), 0));
                        })
                        .addNavigationDrawerItem(i18n("settings.game.management"), SVG.MENU, null, managementItem -> {
                            managementItem.setOnAction(e -> managementPopup.show(managementItem, JFXPopup.PopupVPosition.BOTTOM, JFXPopup.PopupHPosition.LEFT, managementItem.getWidth(), 0));
                        });
                toolbar.getStyleClass().add("advanced-list-box-clear-padding");
                FXUtils.setLimitHeight(toolbar, 40 * 4 + 12 * 2);

                setLeft(sideBar, toolbar);
            }

            control.state.bind(Bindings.createObjectBinding(() ->
                            State.fromTitle(i18n("version.manage.manage.title", getSkinnable().getVersion()), -1),
                    getSkinnable().version));

            //control.transitionPane.getStyleClass().add("gray-background");
            //FXUtils.setOverflowHidden(control.transitionPane, 8);
            setCenter(control.transitionPane);
        }
    }

    public static Node wrap(Node node) {
        StackPane stackPane = new StackPane();
        stackPane.setAlignment(Pos.CENTER);
        FXUtils.setLimitWidth(stackPane, 30);
        FXUtils.setLimitHeight(stackPane, 20);
        stackPane.setPadding(new Insets(0, 0, 0, 0));
        stackPane.getChildren().setAll(node);
        return stackPane;
    }

    public static Node wrap(SVG svg) {
        return wrap(svg.createIcon((Paint) null, 20));
    }

    public interface VersionLoadable {
        void loadVersion(Profile profile, String version);
    }
}
