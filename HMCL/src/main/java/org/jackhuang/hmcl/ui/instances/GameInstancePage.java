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
package org.jackhuang.hmcl.ui.instances;

import com.jfoenix.controls.JFXPopup;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.beans.value.ChangeListener;
import javafx.event.Event;
import javafx.event.EventType;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.game.GameInstanceID;
import org.jackhuang.hmcl.game.HMCLGameInstance;
import org.jackhuang.hmcl.game.HMCLGameRepository;
import org.jackhuang.hmcl.game.HMCLGameRepositorySnapshot;
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

import static org.jackhuang.hmcl.ui.FXUtils.runInFX;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

@SuppressWarnings("FieldCanBeLocal") // Strong reference
public class GameInstancePage extends DecoratorAnimatedPage implements DecoratorPage {
    private final ReadOnlyObjectWrapper<State> state = new ReadOnlyObjectWrapper<>();
    private final TabHeader tab;
    private final TabHeader.Tab<GameSettingsPage<GameSettings.Instance>> gameSettingsTab = new TabHeader.Tab<>("versionSettingsTab");
    private final TabHeader.Tab<InstallerListPage> installerListTab = new TabHeader.Tab<>("installerListTab");
    private final TabHeader.Tab<ModListPage> modListTab = new TabHeader.Tab<>("modListTab");
    private final TabHeader.Tab<WorldListPage> worldListTab = new TabHeader.Tab<>("worldList");
    private final TabHeader.Tab<SchematicsPage> schematicsTab = new TabHeader.Tab<>("schematicsTab");
    private final TabHeader.Tab<ResourcePackListPage> resourcePackTab = new TabHeader.Tab<>("resourcePackTab");
    private final TransitionPane transitionPane = new TransitionPane();
    private final BooleanProperty currentInstanceUpgradable = new SimpleBooleanProperty();
    private final ObjectProperty<HMCLGameInstance.@Nullable Optional> instance =
            new SimpleObjectProperty<>(this, "instance");
    private final WeakListenerHolder listenerHolder = new WeakListenerHolder();

    /// Re-resolves the page context when its repository publishes a new snapshot.
    private final ChangeListener<? super HMCLGameRepositorySnapshot> repositorySnapshotListener =
            (observable, oldValue, newValue) -> checkSelectedInstance();

    /// Repository currently observed for snapshot publications.
    private @Nullable HMCLGameRepository observedRepository;

    /// Last concrete instance displayed by this page.
    private @Nullable GameInstanceID preferredInstanceId;

    public static class WorkingDirChangedEvent extends Event {
        public static final EventType<WorkingDirChangedEvent> EVENT_TYPE = new EventType<>(Event.ANY, "WORKING_DIR_CHANGED");

        public WorkingDirChangedEvent() {
            super(EVENT_TYPE);
        }
    }

    public GameInstancePage() {
        // Child tabs subscribe to instanceProperty() themselves and reload on change.
        gameSettingsTab.setNodeSupplier(() -> new GameSettingsPage<>(GameSettings.Instance.class, instance));
        installerListTab.setNodeSupplier(() -> new InstallerListPage(instance));
        modListTab.setNodeSupplier(() -> new ModListPage(instance));
        resourcePackTab.setNodeSupplier(() -> new ResourcePackListPage(instance));
        worldListTab.setNodeSupplier(() -> new WorldListPage(instance));
        schematicsTab.setNodeSupplier(() -> new SchematicsPage(instance));

        tab = new TabHeader(transitionPane, gameSettingsTab, installerListTab, modListTab, resourcePackTab, worldListTab, schematicsTab);
        tab.select(gameSettingsTab);

        addEventHandler(Navigator.NavigationEvent.NAVIGATED, this::onNavigated);

        addEventHandler(WorkingDirChangedEvent.EVENT_TYPE, event -> {
            HMCLGameInstance.Optional current = this.instance.get();
            if (current != null) {
                // Re-resolve so subscribed tabs reload from the current snapshot.
                this.instance.set(current.refreshed());
            }
        });

        // Page chrome that depends on the current instance.
        listenerHolder.add(FXUtils.onWeakChange(instance, current -> {
            observeRepository(current);
            if (current == null) {
                return;
            }
            HMCLGameInstance gameInstance = current.instance();
            currentInstanceUpgradable.set(gameInstance != null && gameInstance.isModpack());
            if (gameInstance != null) {
                preferredInstanceId = gameInstance.getId();
            }
        }));
    }

    /// Observes snapshot publications for the repository associated with the current page context.
    ///
    /// @param current the current page context, or `null` when the page has no context
    private void observeRepository(HMCLGameInstance.@Nullable Optional current) {
        @Nullable HMCLGameRepository repository = current != null ? current.repository() : null;
        if (repository == observedRepository) {
            return;
        }

        if (observedRepository != null) {
            observedRepository.snapshotProperty().removeListener(repositorySnapshotListener);
        }
        observedRepository = repository;
        if (repository != null) {
            repository.snapshotProperty().addListener(repositorySnapshotListener);
        }
    }

    /// Returns the current instance context for this page and its tabs.
    ///
    /// Child tabs subscribe to this property and reload when it changes. The page only publishes
    /// context; it does not push `loadInstance` into children.
    ///
    /// @return the observable instance context
    public ReadOnlyObjectProperty<HMCLGameInstance.@Nullable Optional> instanceProperty() {
        return instance;
    }

    private void checkSelectedInstance() {
        runInFX(() -> {
            HMCLGameInstance.Optional current = this.instance.get();
            if (current == null) return;
            current = current.refreshed();
            this.instance.set(current);
            if (current.isEmpty()) {
                if (preferredInstanceId != null) {
                    loadInstance(preferredInstanceId, current.repository());
                } else {
                    fireEvent(new PageCloseEvent());
                }
            }
        });
    }

    public void showInstanceSettings() {
        tab.select(gameSettingsTab, false);
    }

    public void setInstance(GameInstanceID instanceId, HMCLGameRepository repository) {
        this.instance.set(HMCLGameInstance.Optional.of(repository, instanceId));
    }

    public void loadInstance(GameInstanceID instanceId, HMCLGameRepository repository) {
        // If we jumped to game list page and deleted this version
        // and back to this page, we should return to main page.
        if (this.instance.get() != null && (!getRepository().isLoaded() ||
                !getRepository().hasInstance(instanceId))) {
            Platform.runLater(() -> fireEvent(new PageCloseEvent()));
            return;
        }

        this.instance.set(HMCLGameInstance.Optional.of(repository, instanceId));
    }

    private void onNavigated(Navigator.NavigationEvent event) {
        if (this.instance.get() == null)
            throw new IllegalStateException();

        // If we jumped to game list page and deleted this version
        // and back to this page, we should return to main page.
        if (!getRepository().isLoaded() ||
                !getRepository().hasInstance(getInstanceId())) {
            Platform.runLater(() -> fireEvent(new PageCloseEvent()));
            return;
        }

        loadInstance(getInstanceId(), getRepository());
    }

    private void onBrowse(String sub) {
        HMCLGameInstance gameInstance = requireGameInstance();
        if (gameInstance == null) {
            return;
        }
        FXUtils.openFolder(gameInstance.getRunDirectory().resolve(sub));
    }

    private void redownloadAssetIndex() {
        HMCLGameInstance gameInstance = requireGameInstance();
        if (gameInstance != null) {
            Instances.updateGameAssets(gameInstance);
        }
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

        HMCLGameInstance.Optional current = instance.get();
        Path resourcesDir = current != null && current.isPresent()
                ? current.instance().getRunDirectory().resolve("resources")
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

    private void clearJunkFiles() {
        HMCLGameInstance gameInstance = requireGameInstance();
        if (gameInstance != null) {
            Instances.cleanInstance(gameInstance);
        }
    }

    private void testGame() {
        HMCLGameInstance gameInstance = requireGameInstance();
        if (gameInstance != null) {
            Instances.testGame(gameInstance);
        }
    }

    private void updateGame() {
        HMCLGameInstance gameInstance = requireGameInstance();
        if (gameInstance != null) {
            Instances.updateInstance(gameInstance);
        }
    }

    private void generateLaunchScript() {
        HMCLGameInstance gameInstance = requireGameInstance();
        if (gameInstance != null) {
            Instances.generateLaunchScript(gameInstance);
        }
    }

    private void export() {
        HMCLGameInstance gameInstance = requireGameInstance();
        if (gameInstance != null) {
            Instances.exportInstance(gameInstance);
        }
    }

    private void rename() {
        HMCLGameInstance gameInstance = requireGameInstance();
        if (gameInstance != null) {
            Instances.renameInstance(gameInstance)
                    .thenApply(newInstanceId -> this.preferredInstanceId = new GameInstanceID(newInstanceId));
        }
    }

    private void remove() {
        HMCLGameInstance gameInstance = requireGameInstance();
        if (gameInstance != null) {
            Instances.deleteInstance(gameInstance);
        }
    }

    private void duplicate() {
        HMCLGameInstance gameInstance = requireGameInstance();
        if (gameInstance != null) {
            Instances.duplicateInstance(gameInstance);
        }
    }

    private @Nullable HMCLGameInstance requireGameInstance() {
        HMCLGameInstance.Optional current = instance.get();
        return current != null ? current.instance() : null;
    }

    public HMCLGameRepository getRepository() {
        HMCLGameInstance.Optional current = instance.get();
        return current != null ? current.repository() : null;
    }

    public @Nullable GameInstanceID getInstanceId() {
        HMCLGameInstance.Optional current = instance.get();
        return current != null ? current.instanceId() : null;
    }

    public HMCLGameInstance.Optional getInstance() {
        return instance.get();
    }

    @Override
    protected Skin createDefaultSkin() {
        return new Skin(this);
    }

    @Override
    public ReadOnlyObjectProperty<State> stateProperty() {
        return state.getReadOnlyProperty();
    }

    public static class Skin extends DecoratorAnimatedPageSkin<GameInstancePage> {

        /**
         * Constructor for all SkinBase instances.
         *
         * @param control The control for which this Skin should attach to.
         */
        protected Skin(GameInstancePage control) {
            super(control);

            {
                AdvancedListBox sideBar = new AdvancedListBox()
                        .addNavigationDrawerTab(control.tab, control.gameSettingsTab, i18n("settings.game"), SVG.SETTINGS, SVG.SETTINGS_FILL)
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
                        new IconedMenuItem(SVG.ROCKET_LAUNCH, i18n("instance.launch.test"), control::testGame, managementPopup),
                        new IconedMenuItem(SVG.SCRIPT, i18n("instance.launch_script"), control::generateLaunchScript, managementPopup),
                        new MenuSeparator(),
                        new IconedMenuItem(SVG.EDIT, i18n("instance.manage.rename"), control::rename, managementPopup),
                        new IconedMenuItem(SVG.FOLDER_COPY, i18n("instance.manage.duplicate"), control::duplicate, managementPopup),
                        new IconedMenuItem(SVG.DELETE, i18n("instance.manage.remove"), control::remove, managementPopup),
                        new IconedMenuItem(SVG.OUTPUT, i18n("modpack.export"), control::export, managementPopup),
                        new MenuSeparator(),
                        new IconedMenuItem(null, i18n("instance.manage.redownload_assets_index"), control::redownloadAssetIndex, managementPopup),
                        new IconedMenuItem(null, i18n("instance.manage.remove_assets"), control::clearAssets, managementPopup),
                        new IconedMenuItem(null, i18n("instance.manage.remove_libraries"), control::clearLibraries, managementPopup),
                        new IconedMenuItem(null, i18n("instance.manage.clean"), control::clearJunkFiles, managementPopup).addTooltip(i18n("instance.manage.clean.tooltip"))
                );

                AdvancedListBox toolbar = new AdvancedListBox()
                        .addNavigationDrawerItem(i18n("instance.update"), SVG.UPDATE, control::updateGame, upgradeItem -> {
                            upgradeItem.visibleProperty().bind(control.currentInstanceUpgradable);
                        })
                        .addNavigationDrawerItem(i18n("instance.launch.test"), SVG.ROCKET_LAUNCH, control::testGame)
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
                            State.fromTitle(i18n("instance.manage.manage.title", getSkinnable().getInstanceId()), -1),
                    getSkinnable().instance));

            //control.transitionPane.getStyleClass().add("gray-background");
            //FXUtils.setOverflowHidden(control.transitionPane, 8);
            setCenter(control.transitionPane);
        }
    }

}
