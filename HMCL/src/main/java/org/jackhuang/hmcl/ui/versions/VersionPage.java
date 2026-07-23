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
import javafx.event.Event;
import javafx.event.EventType;
import javafx.scene.Node;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.event.EventBus;
import org.jackhuang.hmcl.event.EventPriority;
import org.jackhuang.hmcl.event.RefreshedVersionsEvent;
import org.jackhuang.hmcl.game.HMCLGameRepository;
import org.jackhuang.hmcl.setting.GameSettings;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.WeakListenerHolder;
import org.jackhuang.hmcl.ui.animation.TransitionPane;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.ui.decorator.DecoratorAnimatedPage;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.ui.game.GameSettingsPage;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Supplier;

import static org.jackhuang.hmcl.ui.FXUtils.runInFX;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class VersionPage extends DecoratorAnimatedPage implements DecoratorPage {
    private final ReadOnlyObjectWrapper<State> state = new ReadOnlyObjectWrapper<>();
    private final TabHeader tab;
    private final TabHeader.Tab<GameSettingsPage<GameSettings.Instance>> versionSettingsTab = new TabHeader.Tab<>("versionSettingsTab");
    private final TabHeader.Tab<InstallerListPage> installerListTab = new TabHeader.Tab<>("installerListTab");
    private final TabHeader.Tab<ModListPage> modListTab = new TabHeader.Tab<>("modListTab");
    private final TabHeader.Tab<WorldListPage> worldListTab = new TabHeader.Tab<>("worldList");
    private final TabHeader.Tab<SchematicsPage> schematicsTab = new TabHeader.Tab<>("schematicsTab");
    private final TabHeader.Tab<ResourcePackListPage> resourcePackTab = new TabHeader.Tab<>("resourcePackTab");
    private final TransitionPane transitionPane = new TransitionPane();
    private final BooleanProperty currentVersionUpgradable = new SimpleBooleanProperty();
    private final ObjectProperty<HMCLGameRepository.InstanceReference> instanceReference = new SimpleObjectProperty<>();
    private final WeakListenerHolder listenerHolder = new WeakListenerHolder();

    private String preferredVersionName = null;

    public static class WorkingDirChangedEvent extends Event {
        public static final EventType<WorkingDirChangedEvent> EVENT_TYPE = new EventType<>(Event.ANY, "WORKING_DIR_CHANGED");

        public WorkingDirChangedEvent() {
            super(EVENT_TYPE);
        }
    }

    public VersionPage() {
        versionSettingsTab.setNodeSupplier(loadVersionFor(() -> new GameSettingsPage<>(GameSettings.Instance.class)));
        installerListTab.setNodeSupplier(loadVersionFor(InstallerListPage::new));
        modListTab.setNodeSupplier(loadVersionFor(ModListPage::new));
        resourcePackTab.setNodeSupplier(loadVersionFor(ResourcePackListPage::new));
        worldListTab.setNodeSupplier(loadVersionFor(WorldListPage::new));
        schematicsTab.setNodeSupplier(loadVersionFor(SchematicsPage::new));

        tab = new TabHeader(transitionPane, versionSettingsTab, installerListTab, modListTab, resourcePackTab, worldListTab, schematicsTab);
        tab.select(versionSettingsTab);

        addEventHandler(Navigator.NavigationEvent.NAVIGATED, this::onNavigated);

        addEventHandler(WorkingDirChangedEvent.EVENT_TYPE, event -> {
            if (this.instanceReference.get() != null) {
                if (installerListTab.isInitialized())
                    installerListTab.getNode().loadInstance(getRepository(), getVersion());
                if (modListTab.isInitialized())
                    modListTab.getNode().loadInstance(getRepository(), getVersion());
                if (resourcePackTab.isInitialized())
                    resourcePackTab.getNode().loadInstance(getRepository(), getVersion());
                if (worldListTab.isInitialized())
                    worldListTab.getNode().loadInstance(getRepository(), getVersion());
                if (schematicsTab.isInitialized())
                    schematicsTab.getNode().loadInstance(getRepository(), getVersion());
            }
        });

        listenerHolder.add(EventBus.EVENT_BUS.channel(RefreshedVersionsEvent.class).registerWeak(event -> checkSelectedVersion(), EventPriority.HIGHEST));
    }

    private void checkSelectedVersion() {
        runInFX(() -> {
            if (this.instanceReference.get() == null) return;
            HMCLGameRepository repository = this.instanceReference.get().repository();
            if (!repository.hasVersion(this.instanceReference.get().instanceId())) {
                if (preferredVersionName != null) {
                    loadVersion(preferredVersionName, repository);
                } else {
                    fireEvent(new PageCloseEvent());
                }
            }
        });
    }

    private <T extends Node> Supplier<T> loadVersionFor(Supplier<T> nodeSupplier) {
        return () -> {
            T node = nodeSupplier.get();
            if (instanceReference.get() != null) {
                if (node instanceof VersionPage.GameInstanceLoadable) {
                    ((GameInstanceLoadable) node).loadInstance(instanceReference.get().repository(), instanceReference.get().instanceId());
                }
            }
            return node;
        };
    }

    public void showInstanceSettings() {
        tab.select(versionSettingsTab, false);
    }

    public void setVersion(String version, HMCLGameRepository repository) {
        this.instanceReference.set(new HMCLGameRepository.InstanceReference(repository, version));
    }

    public void loadVersion(String version, HMCLGameRepository repository) {
        // If we jumped to game list page and deleted this version
        // and back to this page, we should return to main page.
        if (this.instanceReference.get() != null && (!getRepository().isLoaded() ||
                !getRepository().hasVersion(version))) {
            Platform.runLater(() -> fireEvent(new PageCloseEvent()));
            return;
        }

        setVersion(version, repository);
        preferredVersionName = version;

        if (versionSettingsTab.isInitialized())
            versionSettingsTab.getNode().loadInstance(repository, version);
        if (installerListTab.isInitialized())
            installerListTab.getNode().loadInstance(repository, version);
        if (modListTab.isInitialized())
            modListTab.getNode().loadInstance(repository, version);
        if (resourcePackTab.isInitialized())
            resourcePackTab.getNode().loadInstance(repository, version);
        if (worldListTab.isInitialized())
            worldListTab.getNode().loadInstance(repository, version);
        if (schematicsTab.isInitialized())
            schematicsTab.getNode().loadInstance(repository, version);
        currentVersionUpgradable.set(repository.isModpack(version));
    }

    private void onNavigated(Navigator.NavigationEvent event) {
        if (this.instanceReference.get() == null)
            throw new IllegalStateException();

        // If we jumped to game list page and deleted this version
        // and back to this page, we should return to main page.
        if (!getRepository().isLoaded() ||
                !getRepository().hasVersion(getVersion())) {
            Platform.runLater(() -> fireEvent(new PageCloseEvent()));
            return;
        }

        loadVersion(getVersion(), getRepository());
    }

    private void onBrowse(String sub) {
        FXUtils.openFolder(getRepository().getRunDirectory(getVersion()).resolve(sub));
    }

    private void redownloadAssetIndex() {
        Versions.updateGameAssets(getRepository(), getVersion());
    }

    private void clearLibraries() {
        var libraries = getRepository().getBaseDirectory().resolve("libraries");
        Task.runAsync(Schedulers.io(), () -> {
            FileUtils.deleteDirectoryQuietly(libraries);
        }).whenComplete(Schedulers.javafx(), (exception) -> {
            if (exception != null) {
                Controllers.dialog(i18n("message.failed") + "\n" + StringUtils.getStackTrace(exception), i18n("message.error"), MessageDialogPane.MessageType.ERROR);
            }
        }).start();
    }

    private void clearAssets() {
        Path assetsDir = getRepository().getBaseDirectory().resolve("assets");

        HMCLGameRepository.InstanceReference currentInstanceReference = instanceReference.get();
        Path resourcesDir = currentInstanceReference != null
                ? getRepository().getRunDirectory(currentInstanceReference.instanceId()).resolve("resources")
                : null;

        Task.runAsync(Schedulers.io(), () -> {
            FileUtils.deleteDirectoryQuietly(assetsDir);
            if (resourcesDir != null) {
                FileUtils.deleteDirectoryQuietly(resourcesDir);
            }
        }).whenComplete(Schedulers.javafx(), (exception) -> {
            if (exception != null) {
                Controllers.dialog(i18n("message.failed") + "\n" + StringUtils.getStackTrace(exception), i18n("message.error"), MessageDialogPane.MessageType.ERROR);
            }
        }).start();
    }

    private void resetInstance() {
        Controllers.confirm(i18n("version.manage.reset.confirm"), i18n("message.confirm"), () -> {
            Versions.resetVersion(getProfile(), getVersion());
        }, null);
    }

    private void clearJunkFiles() {
        Versions.cleanVersion(getRepository(), getVersion());
    }

    private void testGame() {
        Versions.testGame(getRepository(), getVersion());
    }

    private void updateGame() {
        Versions.updateVersion(getRepository(), getVersion());
    }

    private void generateLaunchScript() {
        Versions.generateLaunchScript(getRepository(), getVersion());
    }

    private void export() {
        Versions.exportVersion(getRepository(), getVersion());
    }

    private void rename() {
        Versions.renameVersion(getRepository(), getVersion())
                .thenApply(newVersionName -> this.preferredVersionName = newVersionName);
    }

    private void remove() {
        Versions.deleteVersion(getRepository(), getVersion());
    }

    private void duplicate() {
        Versions.duplicateVersion(getRepository(), getVersion());
    }

    public HMCLGameRepository getRepository() {
        return Optional.ofNullable(instanceReference.get()).map(HMCLGameRepository.InstanceReference::repository).orElse(null);
    }

    public String getVersion() {
        return Optional.ofNullable(instanceReference.get()).map(HMCLGameRepository.InstanceReference::instanceId).orElse(null);
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
                AdvancedListBox sideBar = new AdvancedListBox()
                        .addNavigationDrawerTab(control.tab, control.versionSettingsTab, i18n("settings.game"), SVG.SETTINGS, SVG.SETTINGS_FILL)
                        .addNavigationDrawerTab(control.tab, control.installerListTab, i18n("settings.tabs.installers"), SVG.DEPLOYED_CODE, SVG.DEPLOYED_CODE_FILL)
                        .addNavigationDrawerTab(control.tab, control.modListTab, i18n("mods.manage"), SVG.EXTENSION, SVG.EXTENSION_FILL)
                        .addNavigationDrawerTab(control.tab, control.resourcePackTab, i18n("resourcepack.manage"), SVG.TEXTURE)
                        .addNavigationDrawerTab(control.tab, control.worldListTab, i18n("world.manage"), SVG.PUBLIC)
                        .addNavigationDrawerTab(control.tab, control.schematicsTab, i18n("schematics.manage"), SVG.SCHEMA, SVG.SCHEMA_FILL);
                VBox.setVgrow(sideBar, Priority.ALWAYS);

                PopupMenu browseList = new PopupMenu();
                JFXPopup browsePopup = new JFXPopup(browseList);
                browseList.getContent().setAll(
                        new IconedMenuItem(SVG.STADIA_CONTROLLER, i18n("folder.game"), () -> control.onBrowse(""), browsePopup),
                        new IconedMenuItem(SVG.EXTENSION, i18n("folder.mod"), () -> control.onBrowse("mods"), browsePopup),
                        new IconedMenuItem(SVG.TEXTURE, i18n("folder.resourcepacks"), () -> control.onBrowse("resourcepacks"), browsePopup),
                        new IconedMenuItem(SVG.PUBLIC, i18n("folder.saves"), () -> control.onBrowse("saves"), browsePopup),
                        new IconedMenuItem(SVG.SCHEMA, i18n("folder.schematics"), () -> control.onBrowse("schematics"), browsePopup),
                        new IconedMenuItem(SVG.WB_SUNNY, i18n("folder.shaderpacks"), () -> control.onBrowse("shaderpacks"), browsePopup),
                        new IconedMenuItem(SVG.SCREENSHOT_MONITOR, i18n("folder.screenshots"), () -> control.onBrowse("screenshots"), browsePopup),
                        new IconedMenuItem(SVG.SETTINGS, i18n("folder.config"), () -> control.onBrowse("config"), browsePopup),
                        new IconedMenuItem(SVG.SCRIPT, i18n("folder.logs"), () -> control.onBrowse("logs"), browsePopup),
                        new IconedMenuItem(SVG.FRAME_BUG, i18n("folder.crash-reports"), () -> control.onBrowse("crash-reports"), browsePopup)
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
                        new IconedMenuItem(null, i18n("version.manage.clean"), control::clearJunkFiles, managementPopup).addTooltip(i18n("version.manage.clean.tooltip")),
                        new IconedMenuItem(null, i18n("version.manage.reset"), control::resetInstance, managementPopup)
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
                    getSkinnable().instanceReference));

            //control.transitionPane.getStyleClass().add("gray-background");
            //FXUtils.setOverflowHidden(control.transitionPane, 8);
            setCenter(control.transitionPane);
        }
    }

    /// Loads page content for a game instance in a repository.
    public interface GameInstanceLoadable {
        /// Loads page content for the given repository and game instance.
        ///
        /// @param repository the repository containing the game instance
        /// @param instanceId the game instance ID, or `null` when only repository context is available
        void loadInstance(HMCLGameRepository repository, @Nullable String instanceId);
    }
}
