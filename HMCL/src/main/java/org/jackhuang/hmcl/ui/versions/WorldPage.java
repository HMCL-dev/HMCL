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

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import org.jackhuang.hmcl.game.World;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.animation.ContainerAnimations;
import org.jackhuang.hmcl.ui.animation.TransitionPane;
import org.jackhuang.hmcl.ui.construct.AdvancedListBox;
import org.jackhuang.hmcl.ui.construct.TabHeader;
import org.jackhuang.hmcl.ui.decorator.DecoratorAnimatedPage;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Path;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/**
 * @author Glavo
 */
public final class WorldPage extends DecoratorAnimatedPage implements DecoratorPage {

    private final ObjectProperty<State> state;
    private final World world;
    private final Path backupsDir;

    private final TabHeader header;
    private final TabHeader.Tab<WorldInfoPage> worldInfoTab = new TabHeader.Tab<>("worldInfoPage");
    private final TabHeader.Tab<WorldBackupsPage> worldBackupsTab = new TabHeader.Tab<>("worldBackupsPage");
    private final TabHeader.Tab<DatapackListPage> datapackTab = new TabHeader.Tab<>("datapackListPage");

    private final TransitionPane transitionPane = new TransitionPane();

    private FileChannel sessionLockChannel;

    public WorldPage(World world, Path backupsDir) {
        this.world = world;
        this.backupsDir = backupsDir;

        this.state = new SimpleObjectProperty<>(State.fromTitle(i18n("world.manage.title", world.getWorldName())));
        this.header = new TabHeader(worldInfoTab, worldBackupsTab);

        if (world.getGameVersion() != null && // old game will not write game version to level.dat
                GameVersionNumber.compare(world.getGameVersion(), "1.13") >= 0) {
            header.getTabs().add(datapackTab);
        }

        worldInfoTab.setNodeSupplier(() -> new WorldInfoPage(this));
        worldBackupsTab.setNodeSupplier(() -> new WorldBackupsPage(this));
        datapackTab.setNodeSupplier(() -> new DatapackListPage(this));

        header.select(worldInfoTab);
        transitionPane.setContent(worldInfoTab.getNode(), ContainerAnimations.NONE);
        FXUtils.onChange(header.getSelectionModel().selectedItemProperty(), newValue ->
                transitionPane.setContent(newValue.getNode(), ContainerAnimations.FADE));

        AdvancedListBox sideBar = new AdvancedListBox()
                .addNavigationDrawerTab(header, worldInfoTab, i18n("world.info"), SVG.INFO)
                .addNavigationDrawerTab(header, worldBackupsTab, i18n("world.backup"), SVG.ARCHIVE)
                .addNavigationDrawerTab(header, datapackTab, i18n("world.datapack"), SVG.EXTENSION);
        FXUtils.setLimitWidth(sideBar, 200);

        setLeft(sideBar);
        setCenter(transitionPane);

        // Does it need to be done in the background?
        try {
            sessionLockChannel = world.lock();
            LOG.info("Acquired lock on world " + world.getFileName());
        } catch (IOException ignored) {
        }
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
    public boolean back() {
        closePage();
        return true;
    }

    @Override
    public void closePage() {
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
}
