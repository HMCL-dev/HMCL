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
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Insets;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.game.World;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.animation.TransitionPane;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.ui.decorator.DecoratorAnimatedPage;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.util.ChunkBaseApp;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Path;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/**
 * @author Glavo
 */
public final class WorldManagePage extends DecoratorAnimatedPage implements DecoratorPage {

    private final ObjectProperty<State> state;
    private final World world;
    private final Path backupsDir;
    private final Profile profile;
    private final String id;

    private final TabHeader header;
    private final TabHeader.Tab<WorldInfoPage> worldInfoTab = new TabHeader.Tab<>("worldInfoPage");
    private final TabHeader.Tab<WorldBackupsPage> worldBackupsTab = new TabHeader.Tab<>("worldBackupsPage");
    private final TabHeader.Tab<DatapackListPage> datapackTab = new TabHeader.Tab<>("datapackListPage");

    private final TransitionPane transitionPane = new TransitionPane();

    private FileChannel sessionLockChannel;

    public WorldManagePage(World world, Path backupsDir, Profile profile, String id) {
        this.world = world;
        this.backupsDir = backupsDir;

        this.profile = profile;
        this.id = id;

        this.worldInfoTab.setNodeSupplier(() -> new WorldInfoPage(this));
        this.worldBackupsTab.setNodeSupplier(() -> new WorldBackupsPage(this));
        this.datapackTab.setNodeSupplier(() -> new DatapackListPage(this));

        this.state = new SimpleObjectProperty<>(State.fromTitle(i18n("world.manage.title", world.getWorldName())));
        this.header = new TabHeader(transitionPane, worldInfoTab, worldBackupsTab);
        header.select(worldInfoTab);

        // Does it need to be done in the background?
        try {
            sessionLockChannel = world.lock();
            LOG.info("Acquired lock on world " + world.getFileName());
        } catch (IOException ignored) {
        }

        setCenter(transitionPane);

        BorderPane left = new BorderPane();
        FXUtils.setLimitWidth(left, 200);
        VBox.setVgrow(left, Priority.ALWAYS);
        setLeft(left);

        AdvancedListBox sideBar = new AdvancedListBox()
                .addNavigationDrawerTab(header, worldInfoTab, i18n("world.info"), SVG.INFO, SVG.INFO_FILL)
                .addNavigationDrawerTab(header, worldBackupsTab, i18n("world.backup"), SVG.ARCHIVE, SVG.ARCHIVE_FILL);

        if (world.getGameVersion() != null && // old game will not write game version to level.dat
                world.getGameVersion().isAtLeast("1.13", "17w43a")) {
            header.getTabs().add(datapackTab);
            sideBar.addNavigationDrawerTab(header, datapackTab, i18n("world.datapack"), SVG.EXTENSION, SVG.EXTENSION_FILL);
        }

        left.setTop(sideBar);

        AdvancedListBox toolbar = new AdvancedListBox();

        if (world.getGameVersion() != null && world.getGameVersion().isAtLeast("1.20", "23w14a")) {
            toolbar.addNavigationDrawerItem(i18n("version.launch"), SVG.ROCKET_LAUNCH, this::launch, null);
            toolbar.addNavigationDrawerItem(i18n("version.launch_script"), SVG.SCRIPT, this::generateLaunchScript, null);
        }

        if (ChunkBaseApp.isSupported(world)) {
            PopupMenu popupMenu = new PopupMenu();
            JFXPopup popup = new JFXPopup(popupMenu);

            popupMenu.getContent().addAll(
                    new IconedMenuItem(SVG.EXPLORE, i18n("world.chunkbase.seed_map"), () -> ChunkBaseApp.openSeedMap(world), popup),
                    new IconedMenuItem(SVG.VISIBILITY, i18n("world.chunkbase.stronghold"), () -> ChunkBaseApp.openStrongholdFinder(world), popup),
                    new IconedMenuItem(SVG.FORT, i18n("world.chunkbase.nether_fortress"), () -> ChunkBaseApp.openNetherFortressFinder(world), popup)
            );

            if (world.getGameVersion() != null && world.getGameVersion().compareTo("1.13") >= 0) {
                popupMenu.getContent().add(
                        new IconedMenuItem(SVG.LOCATION_CITY, i18n("world.chunkbase.end_city"), () -> ChunkBaseApp.openEndCityFinder(world), popup));
            }

            toolbar.addNavigationDrawerItem(i18n("world.chunkbase"), SVG.EXPLORE, null, chunkBaseMenuItem ->
                    chunkBaseMenuItem.setOnAction(e ->
                            popup.show(chunkBaseMenuItem,
                                    JFXPopup.PopupVPosition.BOTTOM, JFXPopup.PopupHPosition.LEFT,
                                    chunkBaseMenuItem.getWidth(), 0)));
        }
        toolbar.addNavigationDrawerItem(i18n("settings.game.exploration"), SVG.FOLDER_OPEN, () -> FXUtils.openFolder(world.getFile()), null);

        BorderPane.setMargin(toolbar, new Insets(0, 0, 12, 0));
        left.setBottom(toolbar);

        this.addEventHandler(Navigator.NavigationEvent.EXITED, this::onExited);
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

    public void onExited(Navigator.NavigationEvent event) {
        if (sessionLockChannel != null) {
            try {
                sessionLockChannel.close();
                LOG.info("Releases the lock on world " + world.getFileName());
            } catch (IOException e) {
                LOG.warning("Failed to close session lock channel", e);
            }

            sessionLockChannel = null;
        }
    }

    public void launch() {
        fireEvent(new PageCloseEvent());
        Versions.launchAndEnterWorld(profile, id, world.getFileName());
    }

    public void generateLaunchScript() {
        Versions.generateLaunchScriptForQuickEnterWorld(profile, id, world.getFileName());
    }
}
