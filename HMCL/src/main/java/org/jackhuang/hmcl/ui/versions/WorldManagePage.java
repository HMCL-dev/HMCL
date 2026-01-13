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

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Path;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/**
 * @author Glavo
 */
public final class WorldManagePage extends DecoratorAnimatedPage implements DecoratorPage {

    private final World world;
    private final Path backupsDir;
    private final Profile profile;
    private final String id;
    private FileChannel sessionLockChannel;

    private final ObjectProperty<State> state;
    private boolean isFirstNavigation = true;
    private final BooleanProperty refreshableProperty = new SimpleBooleanProperty(true);

    private final TransitionPane transitionPane = new TransitionPane();
    private final TabHeader header = new TabHeader(transitionPane);
    private final TabHeader.Tab<WorldInfoPage> worldInfoTab = new TabHeader.Tab<>("worldInfoPage");
    private final TabHeader.Tab<WorldBackupsPage> worldBackupsTab = new TabHeader.Tab<>("worldBackupsPage");
    private final TabHeader.Tab<DatapackListPage> datapackTab = new TabHeader.Tab<>("datapackListPage");

    public WorldManagePage(World world, Path backupsDir, Profile profile, String id) {
        this.world = world;
        this.backupsDir = backupsDir;
        this.profile = profile;
        this.id = id;

        sessionLockChannel = WorldManageUIUtils.getSessionLockChannel(world);
        try {
            world.reloadLevelDat();
        } catch (IOException e) {
            LOG.warning("Can not load world level.dat of world: " + world.getFile(), e);
            this.addEventHandler(Navigator.NavigationEvent.NAVIGATED, event -> closePageForLoadingFail());
        }

        this.state = new SimpleObjectProperty<>(new State(i18n("world.manage.title", StringUtils.parseColorEscapes(world.getWorldName())), null, true, true, true));

        setCenter(transitionPane);
        setLeftNode();

        this.addEventHandler(Navigator.NavigationEvent.EXITED, this::onExited);
        this.addEventHandler(Navigator.NavigationEvent.NAVIGATED, this::onNavigated);
    }

    public void setLeftNode() {
        BorderPane left = new BorderPane();
        {
            FXUtils.setLimitWidth(left, 200);
            VBox.setVgrow(left, Priority.ALWAYS);
            setLeft(left);
        }

        {
            this.worldInfoTab.setNodeSupplier(() -> new WorldInfoPage(this));
            this.worldBackupsTab.setNodeSupplier(() -> new WorldBackupsPage(this));
            header.getTabs().addAll(worldInfoTab, worldBackupsTab);
            header.select(worldInfoTab);

            AdvancedListBox tabBar = new AdvancedListBox()
                    .addNavigationDrawerTab(header, worldInfoTab, i18n("world.info"), SVG.INFO, SVG.INFO_FILL)
                    .addNavigationDrawerTab(header, worldBackupsTab, i18n("world.backup"), SVG.ARCHIVE, SVG.ARCHIVE_FILL);

            if (world.getGameVersion() != null && // old game will not write game version to level.dat
                    world.getGameVersion().isAtLeast("1.13", "17w43a")) {
                this.datapackTab.setNodeSupplier(() -> new DatapackListPage(this));
                header.getTabs().add(datapackTab);
                tabBar.addNavigationDrawerTab(header, datapackTab, i18n("world.datapack"), SVG.EXTENSION, SVG.EXTENSION_FILL);
            }

            left.setTop(tabBar);
        }

        AdvancedListBox toolbar = new AdvancedListBox();
        {
            BorderPane.setMargin(toolbar, new Insets(0, 0, 12, 0));
            left.setBottom(toolbar);
        }
        {
            if (world.getGameVersion() != null && world.getGameVersion().isAtLeast("1.20", "23w14a")) {
                toolbar.addNavigationDrawerItem(i18n("version.launch"), SVG.ROCKET_LAUNCH, this::launch, advancedListItem -> advancedListItem.setDisable(isReadOnly()));
            }

            if (ChunkBaseApp.isSupported(world)) {
                PopupMenu chunkBasePopupMenu = new PopupMenu();
                JFXPopup chunkBasePopup = new JFXPopup(chunkBasePopupMenu);

                chunkBasePopupMenu.getContent().addAll(
                        new IconedMenuItem(SVG.EXPLORE, i18n("world.chunkbase.seed_map"), () -> ChunkBaseApp.openSeedMap(world), chunkBasePopup),
                        new IconedMenuItem(SVG.VISIBILITY, i18n("world.chunkbase.stronghold"), () -> ChunkBaseApp.openStrongholdFinder(world), chunkBasePopup),
                        new IconedMenuItem(SVG.FORT, i18n("world.chunkbase.nether_fortress"), () -> ChunkBaseApp.openNetherFortressFinder(world), chunkBasePopup)
                );

                if (world.getGameVersion() != null && world.getGameVersion().compareTo("1.13") >= 0) {
                    chunkBasePopupMenu.getContent().add(
                            new IconedMenuItem(SVG.LOCATION_CITY, i18n("world.chunkbase.end_city"), () -> ChunkBaseApp.openEndCityFinder(world), chunkBasePopup));
                }

                toolbar.addNavigationDrawerItem(i18n("world.chunkbase"), SVG.EXPLORE, null, chunkBaseMenuItem ->
                        chunkBaseMenuItem.setOnAction(e ->
                                chunkBasePopup.show(chunkBaseMenuItem,
                                        JFXPopup.PopupVPosition.BOTTOM, JFXPopup.PopupHPosition.LEFT,
                                        chunkBaseMenuItem.getWidth(), 0)));
            }

            toolbar.addNavigationDrawerItem(i18n("settings.game.exploration"), SVG.FOLDER_OPEN, () -> FXUtils.openFolder(world.getFile()), null);

            {
                PopupMenu managePopupMenu = new PopupMenu();
                JFXPopup managePopup = new JFXPopup(managePopupMenu);

                if (world.getGameVersion() != null && world.getGameVersion().isAtLeast("1.20", "23w14a")) {
                    managePopupMenu.getContent().addAll(
                            new IconedMenuItem(SVG.ROCKET_LAUNCH, i18n("version.launch"), this::launch, managePopup),
                            new IconedMenuItem(SVG.SCRIPT, i18n("version.launch_script"), this::generateLaunchScript, managePopup),
                            new MenuSeparator()
                    );
                }

                managePopupMenu.getContent().addAll(
                        new IconedMenuItem(SVG.OUTPUT, i18n("world.export"), () -> WorldManageUIUtils.export(world, sessionLockChannel), managePopup),
                        new IconedMenuItem(SVG.DELETE, i18n("world.delete"), () -> WorldManageUIUtils.delete(world, () -> fireEvent(new PageCloseEvent()), sessionLockChannel), managePopup),
                        new IconedMenuItem(SVG.CONTENT_COPY, i18n("world.duplicate"), () -> WorldManageUIUtils.copyWorld(world, null), managePopup)
                );

                toolbar.addNavigationDrawerItem(i18n("settings.game.management"), SVG.MENU, null, managePopupMenuItem ->
                {
                    managePopupMenuItem.setOnAction(e ->
                            managePopup.show(managePopupMenuItem,
                                    JFXPopup.PopupVPosition.BOTTOM, JFXPopup.PopupHPosition.LEFT,
                                    managePopupMenuItem.getWidth(), 0));
                    managePopupMenuItem.setDisable(isReadOnly());
                });
            }
        }
    }

    @Override
    public void refresh() {
        if (sessionLockChannel == null || !sessionLockChannel.isOpen()) {
            sessionLockChannel = WorldManageUIUtils.getSessionLockChannel(world);
        }

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

    private void onNavigated(Navigator.NavigationEvent event) {
        if (isFirstNavigation) {
            isFirstNavigation = false;
            return;
        }
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
        Versions.launchAndEnterWorld(profile, id, world.getFileName());
    }

    public void generateLaunchScript() {
        Versions.generateLaunchScriptForQuickEnterWorld(profile, id, world.getFileName());
    }

    @Override
    public ReadOnlyObjectProperty<State> stateProperty() {
        return state;
    }

    public World getWorld() {
        return world;
    }

    public Path getBackupsDir() {
        return backupsDir;
    }

    public boolean isReadOnly() {
        return sessionLockChannel == null;
    }

    @Override
    public BooleanProperty refreshableProperty() {
        return refreshableProperty;
    }

    public interface WorldRefreshable {
        void refresh();
    }
}
