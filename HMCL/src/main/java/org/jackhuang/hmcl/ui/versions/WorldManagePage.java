/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2025 huangyuhui <huanghongxun2008@126.com> and contributors
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
import javafx.beans.property.*;
import javafx.geometry.Insets;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.game.World;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.animation.TransitionPane;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.ui.decorator.DecoratorAnimatedPage;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.util.ChunkBaseApp;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.util.Optional;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/**
 * @author Glavo
 */
public final class WorldManagePage extends DecoratorAnimatedPage implements DecoratorPage {

    private final World world;
    private final Path backupsDir;
    private final Profile profile;
    private final String instanceId;
    private final boolean supportQuickPlay;
    private FileChannel sessionLockChannel;

    private final ObjectProperty<State> state;
    private boolean isFirstNavigation = true;
    private final BooleanProperty refreshable = new SimpleBooleanProperty(true);
    private final BooleanProperty readOnly = new SimpleBooleanProperty(false);

    private final TransitionPane transitionPane = new TransitionPane();
    private final TabHeader header = new TabHeader(transitionPane);
    private final TabHeader.Tab<WorldInfoPage> worldInfoTab = new TabHeader.Tab<>("worldInfoPage");
    private final TabHeader.Tab<WorldBackupsPage> worldBackupsTab = new TabHeader.Tab<>("worldBackupsPage");
    private final TabHeader.Tab<DatapackListPage> datapackTab = new TabHeader.Tab<>("datapackListPage");

    public WorldManagePage(World world, Profile profile, String instanceId) {
        this.world = world;
        this.backupsDir = profile.getRepository().getBackupsDirectory(instanceId);
        this.profile = profile;
        this.instanceId = instanceId;

        updateSessionLockChannel();

        try {
            this.world.reloadLevelDat();
        } catch (IOException e) {
            LOG.warning("Can not load world level.dat of world: " + this.world.getFile(), e);
            this.addEventHandler(Navigator.NavigationEvent.NAVIGATED, event -> closePageForLoadingFail());
        }

        worldInfoTab.setNodeSupplier(() -> new WorldInfoPage(this));
        worldBackupsTab.setNodeSupplier(() -> new WorldBackupsPage(this));
        datapackTab.setNodeSupplier(() -> new DatapackListPage(this));

        this.state = new SimpleObjectProperty<>(new State(i18n("world.manage.title", StringUtils.parseColorEscapes(world.getWorldName())), null, true, true, true));

        Optional<String> gameVersion = profile.getRepository().getGameVersion(instanceId);
        supportQuickPlay = World.supportQuickPlay(GameVersionNumber.asGameVersion(gameVersion));

        this.addEventHandler(Navigator.NavigationEvent.EXITED, this::onExited);
        this.addEventHandler(Navigator.NavigationEvent.NAVIGATED, this::onNavigated);
    }

    @Override
    protected @NotNull Skin createDefaultSkin() {
        return new Skin(this);
    }

    @Override
    public void refresh() {
        updateSessionLockChannel();
        try {
            world.reloadLevelDat();
        } catch (IOException e) {
            LOG.warning("Can not load world level.dat of world: " + world.getFile(), e);
            closePageForLoadingFail();
            return;
        }

        for (var tab : header.getTabs()) {
            if (tab.getNode() instanceof WorldRefreshable r) {
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
        Versions.launchAndEnterWorld(profile, instanceId, world.getFileName());
    }

    public void generateLaunchScript() {
        Versions.generateLaunchScriptForQuickEnterWorld(profile, instanceId, world.getFileName());
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

    public static class Skin extends DecoratorAnimatedPageSkin<WorldManagePage> {

        protected Skin(WorldManagePage control) {
            super(control);

            setCenter(control.transitionPane);
            setLeft(getSidebar());
        }

        private BorderPane getSidebar() {
            BorderPane sidebar = new BorderPane();
            {
                FXUtils.setLimitWidth(sidebar, 200);
                VBox.setVgrow(sidebar, Priority.ALWAYS);
            }

            sidebar.setTop(getTabBar());
            sidebar.setBottom(getToolBar());

            return sidebar;
        }

        private AdvancedListBox getTabBar() {
            AdvancedListBox tabBar = new AdvancedListBox();
            {
                getSkinnable().header.getTabs().addAll(getSkinnable().worldInfoTab, getSkinnable().worldBackupsTab);
                getSkinnable().header.select(getSkinnable().worldInfoTab);

                tabBar.addNavigationDrawerTab(getSkinnable().header, getSkinnable().worldInfoTab, i18n("world.info"), SVG.INFO, SVG.INFO_FILL)
                        .addNavigationDrawerTab(getSkinnable().header, getSkinnable().worldBackupsTab, i18n("world.backup"), SVG.ARCHIVE, SVG.ARCHIVE_FILL);

                if (getSkinnable().world.supportDatapacks()) {
                    getSkinnable().header.getTabs().add(getSkinnable().datapackTab);
                    tabBar.addNavigationDrawerTab(getSkinnable().header, getSkinnable().datapackTab, i18n("world.datapack"), SVG.EXTENSION, SVG.EXTENSION_FILL);
                }
            }

            return tabBar;
        }

        private AdvancedListBox getToolBar() {
            AdvancedListBox toolbar = new AdvancedListBox();
            BorderPane.setMargin(toolbar, new Insets(0, 0, 12, 0));
            {
                if (getSkinnable().supportQuickPlay) {
                    toolbar.addNavigationDrawerItem(i18n("version.launch"), SVG.ROCKET_LAUNCH, () -> getSkinnable().launch(), advancedListItem -> advancedListItem.disableProperty().bind(getSkinnable().readOnlyProperty()));
                }

                if (ChunkBaseApp.isSupported(getSkinnable().world)) {
                    PopupMenu chunkBasePopupMenu = new PopupMenu();
                    JFXPopup chunkBasePopup = new JFXPopup(chunkBasePopupMenu);

                    chunkBasePopupMenu.getContent().addAll(
                            new IconedMenuItem(SVG.EXPLORE, i18n("world.chunkbase.seed_map"), () -> ChunkBaseApp.openSeedMap(getSkinnable().world), chunkBasePopup),
                            new IconedMenuItem(SVG.VISIBILITY, i18n("world.chunkbase.stronghold"), () -> ChunkBaseApp.openStrongholdFinder(getSkinnable().world), chunkBasePopup),
                            new IconedMenuItem(SVG.FORT, i18n("world.chunkbase.nether_fortress"), () -> ChunkBaseApp.openNetherFortressFinder(getSkinnable().world), chunkBasePopup)
                    );

                    if (ChunkBaseApp.supportEndCity(getSkinnable().world)) {
                        chunkBasePopupMenu.getContent().add(
                                new IconedMenuItem(SVG.LOCATION_CITY, i18n("world.chunkbase.end_city"), () -> ChunkBaseApp.openEndCityFinder(getSkinnable().world), chunkBasePopup));
                    }

                    toolbar.addNavigationDrawerItem(i18n("world.chunkbase"), SVG.EXPLORE, null, chunkBaseMenuItem ->
                            chunkBaseMenuItem.setOnAction(e ->
                                    chunkBasePopup.show(chunkBaseMenuItem,
                                            JFXPopup.PopupVPosition.BOTTOM, JFXPopup.PopupHPosition.LEFT,
                                            chunkBaseMenuItem.getWidth(), 0)));
                }

                toolbar.addNavigationDrawerItem(i18n("settings.game.exploration"), SVG.FOLDER_OPEN, () -> FXUtils.openFolder(getSkinnable().world.getFile()));

                {
                    PopupMenu managePopupMenu = new PopupMenu();
                    JFXPopup managePopup = new JFXPopup(managePopupMenu);

                    if (getSkinnable().supportQuickPlay) {
                        managePopupMenu.getContent().addAll(
                                new IconedMenuItem(SVG.ROCKET_LAUNCH, i18n("version.launch"), () -> getSkinnable().launch(), managePopup),
                                new IconedMenuItem(SVG.SCRIPT, i18n("version.launch_script"), () -> getSkinnable().generateLaunchScript(), managePopup),
                                new MenuSeparator()
                        );
                    }

                    managePopupMenu.getContent().addAll(
                            new IconedMenuItem(SVG.OUTPUT, i18n("world.export"), () -> WorldManageUIUtils.export(getSkinnable().world, getSkinnable().sessionLockChannel), managePopup),
                            new IconedMenuItem(SVG.DELETE, i18n("world.delete"), () -> WorldManageUIUtils.delete(getSkinnable().world, () -> getSkinnable().fireEvent(new PageCloseEvent()), getSkinnable().sessionLockChannel), managePopup),
                            new IconedMenuItem(SVG.CONTENT_COPY, i18n("world.duplicate"), () -> WorldManageUIUtils.copyWorld(getSkinnable().world, null), managePopup)
                    );

                    toolbar.addNavigationDrawerItem(i18n("settings.game.management"), SVG.MENU, null, managePopupMenuItem ->
                    {
                        managePopupMenuItem.setOnAction(e ->
                                managePopup.show(managePopupMenuItem,
                                        JFXPopup.PopupVPosition.BOTTOM, JFXPopup.PopupHPosition.LEFT,
                                        managePopupMenuItem.getWidth(), 0));
                        managePopupMenuItem.disableProperty().bind(getSkinnable().readOnlyProperty());
                    });
                }
            }
            return toolbar;
        }
    }

    public interface WorldRefreshable {
        void refresh();
    }
}
