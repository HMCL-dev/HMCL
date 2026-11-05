// SPDX-FileCopyrightText: Copyright (C) 2025 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.instances;

import com.jfoenix.controls.JFXPopup;
import javafx.application.Platform;
import javafx.beans.property.*;
import org.jackhuang.hmcl.game.*;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.animation.TransitionPane;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.ui.decorator.DecoratorAnimatedPage;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.util.ChunkBaseApp;
import org.jackhuang.hmcl.util.StringUtils;

import java.io.IOException;
import java.nio.file.Path;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Displays world information, backups, and management actions.
public final class WorldManagePage extends DecoratorAnimatedPage implements DecoratorPage {

    private final World world;
    private final Path backupsDir;
    private final HMCLGameInstance gameInstance;
    private final WorldManager worldManager;
    private final boolean supportQuickPlay;

    private final ObjectProperty<State> state;
    private boolean isFirstNavigation = true;
    private final BooleanProperty refreshable = new SimpleBooleanProperty(true);
    private final BooleanProperty readOnly = new SimpleBooleanProperty(true);

    private final TransitionPane transitionPane = new TransitionPane();
    /// Manages tab selection and content transitions independently of the sidebar.
    private final TabController tabController = new TabController(transitionPane);
    /// The tab displaying information about this world.
    private final TabItem<WorldInfoPage> worldInfoTab;
    /// The tab for managing backups of this world.
    private final TabItem<WorldBackupsPage> worldBackupsTab;
    /// The tab for managing data packs in this world.
    private final TabItem<DataPackListPage> dataPackTab;

    /// Loads world metadata and creates its tabs and sidebar actions.
    public WorldManagePage(World world, WorldManager manager, HMCLGameInstance gameInstance) {
        this.world = world;
        this.gameInstance = gameInstance;
        this.backupsDir = gameInstance.getBackupsDirectory();
        this.worldManager = manager;

        readOnly.set(world.tryLock() == null);

        try {
            this.world.reloadWorldData();
        } catch (IOException e) {
            LOG.warning("Can not load world data of world: " + this.world.getFile(), e);
            this.addEventHandler(Navigator.NavigationEvent.NAVIGATED, event -> closePageForLoadingFail());
        }

        this.worldInfoTab = new TabItem<>(() -> new WorldInfoPage(this));
        this.worldBackupsTab = new TabItem<>(() -> new WorldBackupsPage(this));
        this.dataPackTab = new TabItem<>(() -> new DataPackListPage(this));

        this.state = new SimpleObjectProperty<>(new State(i18n("world.manage.title", StringUtils.parseColorEscapes(world.getWorldName())), null, true, true, true));

        supportQuickPlay = QuickPlayOption.isSupportedVersion(gameInstance.getVersion());

        this.addEventHandler(Navigator.NavigationEvent.EXITED, event -> world.close());
        this.addEventHandler(Navigator.NavigationEvent.NAVIGATED, this::onNavigated);

        setContent(transitionPane);
        initializeSidebar();
        setSidebarBottom(createToolBar());
    }

    /// Reloads world metadata and refreshes only tab pages whose content is already initialized.
    @Override
    public void refresh() {
        readOnly.set(world.tryLock() == null);
        try {
            world.reloadWorldData();
        } catch (IOException e) {
            LOG.warning("Can not load world data of world: " + world.getFile(), e);
            closePageForLoadingFail();
            return;
        }

        for (var tab : tabController.getTabs()) {
            if (tab.isInitialized() && tab.getNode() instanceof WorldRefreshable r) {
                r.refresh();
            }
        }
    }

    private void closePageForLoadingFail() {
        Platform.runLater(() -> {
            fireEvent(new PageCloseEvent());
            Controllers.dialog(i18n("world.load.fail"), null, MessageDialogPane.MessageType.ERROR);
        });
    }

    private void onNavigated(Navigator.NavigationEvent event) {
        if (isFirstNavigation)
            isFirstNavigation = false;
        else
            refresh();
    }

    public void launch() {
        fireEvent(new PageCloseEvent());
        Instances.launchAndEnterWorld(gameInstance, world.getFileName());
    }

    public void generateLaunchScript() {
        Instances.generateLaunchScriptForQuickEnterWorld(gameInstance, world.getFileName());
    }

    @Override
    public ReadOnlyObjectProperty<State> stateProperty() {
        return state;
    }

    public void setTitle(String title) {
        this.state.set(new DecoratorPage.State(title, null, true, true, true));
    }

    public World getWorld() {
        return world;
    }

    public Path getBackupsDir() {
        return backupsDir;
    }

    public boolean isReadOnly() {
        return readOnly.get();
    }

    public BooleanProperty readOnlyProperty() {
        return readOnly;
    }

    @Override
    public BooleanProperty refreshableProperty() {
        return refreshable;
    }

    /// Registers supported world tabs and selects the information tab.
    private void initializeSidebar() {
        tabController.getTabs().addAll(worldInfoTab, worldBackupsTab);
        tabController.selectTab(worldInfoTab);

        getSidebar().addNavigationDrawerTab(tabController, worldInfoTab, i18n("world.info"), SVG.INFO, SVG.INFO_FILL)
                .addNavigationDrawerTab(tabController, worldBackupsTab, i18n("world.backup"), SVG.ARCHIVE, SVG.ARCHIVE_FILL);

        if (world.supportDataPacks()) {
            tabController.getTabs().add(dataPackTab);
            getSidebar().addNavigationDrawerTab(tabController, dataPackTab, i18n("world.datapack"), SVG.EXTENSION, SVG.EXTENSION_FILL);
        }
    }

    /// Creates world actions and binds write operations to the lock state.
    private AdvancedListBox createToolBar() {
        AdvancedListBox toolbar = new AdvancedListBox();
        {
            if (supportQuickPlay) {
                toolbar.addNavigationDrawerItem(i18n("instance.launch"), SVG.ROCKET_LAUNCH, this::launch, advancedListItem -> advancedListItem.disableProperty().bind(readOnlyProperty()));
            }

            if (ChunkBaseApp.isSupported(world)) {
                PopupMenu chunkBasePopupMenu = new PopupMenu();
                JFXPopup chunkBasePopup = new JFXPopup(chunkBasePopupMenu);

                chunkBasePopupMenu.getContent().addAll(
                        new IconedMenuItem(SVG.EXPLORE, i18n("world.chunkbase.seed_map"), () -> ChunkBaseApp.openSeedMap(world), chunkBasePopup),
                        new IconedMenuItem(SVG.VISIBILITY, i18n("world.chunkbase.stronghold"), () -> ChunkBaseApp.openStrongholdFinder(world), chunkBasePopup),
                        new IconedMenuItem(SVG.FORT, i18n("world.chunkbase.nether_fortress"), () -> ChunkBaseApp.openNetherFortressFinder(world), chunkBasePopup)
                );

                if (ChunkBaseApp.supportEndCity(world)) {
                    chunkBasePopupMenu.getContent().add(
                            new IconedMenuItem(SVG.LOCATION_CITY, i18n("world.chunkbase.end_city"), () -> ChunkBaseApp.openEndCityFinder(world), chunkBasePopup));
                }

                toolbar.addNavigationDrawerItem(i18n("world.chunkbase"), SVG.EXPLORE, null, chunkBaseMenuItem ->
                        chunkBaseMenuItem.setOnAction(e ->
                                chunkBasePopup.show(chunkBaseMenuItem,
                                        JFXPopup.PopupVPosition.BOTTOM, JFXPopup.PopupHPosition.LEFT,
                                        chunkBaseMenuItem.getWidth(), 0)));
            }

            toolbar.addNavigationDrawerItem(i18n("settings.game.exploration"), SVG.FOLDER_OPEN, () -> FXUtils.openFolder(world.getFile()));

            {
                PopupMenu managePopupMenu = new PopupMenu();
                JFXPopup managePopup = new JFXPopup(managePopupMenu);

                if (supportQuickPlay) {
                    managePopupMenu.getContent().addAll(
                            new IconedMenuItem(SVG.ROCKET_LAUNCH, i18n("instance.launch"), this::launch, managePopup),
                            new IconedMenuItem(SVG.SCRIPT, i18n("instance.launch_script"), this::generateLaunchScript, managePopup),
                            new MenuSeparator()
                    );
                }

                WorldManageUIToolbox uiToolbox = new WorldManageUIToolbox(world, worldManager);
                managePopupMenu.getContent().addAll(
                        new IconedMenuItem(SVG.OUTPUT, i18n("world.export"), uiToolbox::export, managePopup),
                        new IconedMenuItem(SVG.DELETE_FOREVER, i18n("world.delete"), () -> uiToolbox.delete(() -> fireEvent(new PageCloseEvent())), managePopup),
                        new IconedMenuItem(SVG.CONTENT_COPY, i18n("world.duplicate"), () -> uiToolbox.copyWorld(null), managePopup)
                );

                toolbar.addNavigationDrawerItem(i18n("settings.game.management"), SVG.MENU, null, managePopupMenuItem ->
                {
                    managePopupMenuItem.setOnAction(e ->
                            managePopup.show(managePopupMenuItem,
                                    JFXPopup.PopupVPosition.BOTTOM, JFXPopup.PopupHPosition.LEFT,
                                    managePopupMenuItem.getWidth(), 0));
                    managePopupMenuItem.disableProperty().bind(readOnlyProperty());
                });
            }
        }
        return toolbar;
    }

    public interface WorldRefreshable {
        void refresh();
    }
}
