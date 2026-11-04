// SPDX-FileCopyrightText: Copyright (C) 2025 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.instances;

import com.jfoenix.controls.JFXPopup;
import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.geometry.Insets;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.game.HMCLGameInstance;
import org.jackhuang.hmcl.game.World;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.animation.TransitionPane;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.ui.decorator.DecoratorAnimatedPage;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.util.ChunkBaseApp;
import org.jackhuang.hmcl.util.StringUtils;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Path;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Displays world information, backups, and management actions.
public final class WorldManagePage extends DecoratorAnimatedPage implements DecoratorPage {

    private final World world;
    private final Path backupsDir;
    private final HMCLGameInstance gameInstance;
    private final boolean supportQuickPlay;
    /// The active world lock, or null when the world is read-only.
    private @Nullable FileChannel sessionLockChannel;

    private final ObjectProperty<State> state;
    private boolean isFirstNavigation = true;
    private final BooleanProperty refreshable = new SimpleBooleanProperty(true);
    private final BooleanProperty readOnly = new SimpleBooleanProperty(false);

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
    public WorldManagePage(World world, HMCLGameInstance gameInstance) {
        this.world = world;
        this.gameInstance = gameInstance;
        this.backupsDir = gameInstance.getBackupsDirectory();

        updateSessionLockChannel();

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

        supportQuickPlay = World.supportQuickPlay(gameInstance.getVersion());

        this.addEventHandler(Navigator.NavigationEvent.EXITED, this::onExited);
        this.addEventHandler(Navigator.NavigationEvent.NAVIGATED, this::onNavigated);

        setCenter(transitionPane);
        setLeft(createSidebar());
    }

    /// Reloads world metadata and refreshes only tab pages whose content is already initialized.
    @Override
    public void refresh() {
        updateSessionLockChannel();
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

    private void updateSessionLockChannel() {
        if (sessionLockChannel == null || !sessionLockChannel.isOpen()) {
            sessionLockChannel = WorldManageUIUtils.getSessionLockChannel(world);
            readOnly.set(sessionLockChannel == null);
        }
    }

    private void onNavigated(Navigator.NavigationEvent event) {
        if (isFirstNavigation)
            isFirstNavigation = false;
        else
            refresh();
    }

    public void onExited(Navigator.NavigationEvent event) {
        try {
            WorldManageUIUtils.closeSessionLockChannel(world, sessionLockChannel);
        } catch (IOException ignored) {
        }
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

    /// Places the tab entries above the world management toolbar.
    private BorderPane createSidebar() {
        BorderPane sidebar = new BorderPane();
        {
            FXUtils.setLimitWidth(sidebar, 200);
            VBox.setVgrow(sidebar, Priority.ALWAYS);
        }

        sidebar.setTop(createTabBar());
        sidebar.setBottom(createToolBar());

        return sidebar;
    }

    /// Registers supported world tabs and selects the information tab.
    private AdvancedListBox createTabBar() {
        AdvancedListBox tabBar = new AdvancedListBox();
        {
            tabController.getTabs().addAll(worldInfoTab, worldBackupsTab);
            tabController.selectTab(worldInfoTab);

            tabBar.addNavigationDrawerTab(tabController, worldInfoTab, i18n("world.info"), SVG.INFO, SVG.INFO_FILL)
                    .addNavigationDrawerTab(tabController, worldBackupsTab, i18n("world.backup"), SVG.ARCHIVE, SVG.ARCHIVE_FILL);

            if (world.supportDataPacks()) {
                tabController.getTabs().add(dataPackTab);
                tabBar.addNavigationDrawerTab(tabController, dataPackTab, i18n("world.datapack"), SVG.EXTENSION, SVG.EXTENSION_FILL);
            }
        }

        return tabBar;
    }

    /// Creates world actions and binds write operations to the lock state.
    private AdvancedListBox createToolBar() {
        AdvancedListBox toolbar = new AdvancedListBox();
        BorderPane.setMargin(toolbar, new Insets(0, 0, 12, 0));
        {
            if (supportQuickPlay) {
                toolbar.addNavigationDrawerItem(i18n("instance.launch"), SVG.ROCKET_LAUNCH, () -> launch(), advancedListItem -> advancedListItem.disableProperty().bind(readOnlyProperty()));
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
                            new IconedMenuItem(SVG.ROCKET_LAUNCH, i18n("instance.launch"), () -> launch(), managePopup),
                            new IconedMenuItem(SVG.SCRIPT, i18n("instance.launch_script"), () -> generateLaunchScript(), managePopup),
                            new MenuSeparator()
                    );
                }

                managePopupMenu.getContent().addAll(
                        new IconedMenuItem(SVG.OUTPUT, i18n("world.export"), () -> WorldManageUIUtils.export(world, sessionLockChannel), managePopup),
                        new IconedMenuItem(SVG.DELETE_FOREVER, i18n("world.delete"), () -> WorldManageUIUtils.delete(world, () -> fireEvent(new PageCloseEvent()), sessionLockChannel), managePopup),
                        new IconedMenuItem(SVG.CONTENT_COPY, i18n("world.duplicate"), () -> WorldManageUIUtils.copyWorld(world, null), managePopup)
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
