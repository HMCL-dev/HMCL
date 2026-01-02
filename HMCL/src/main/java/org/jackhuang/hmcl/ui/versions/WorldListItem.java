/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020 huangyuhui <huanghongxun2008@126.com> and contributors
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

import javafx.scene.control.Control;
import javafx.scene.control.Skin;
import org.jackhuang.hmcl.game.World;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;

import java.io.IOException;
import java.nio.file.Path;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class WorldListItem extends Control {
    private final World world;
    private final Path backupsDir;
    private final WorldListPage parent;
    private final Profile profile;
    private final String id;

    public WorldListItem(WorldListPage parent, World world, Path backupsDir, Profile profile, String id) {
        this.world = world;
        this.backupsDir = backupsDir;
        this.parent = parent;
        this.profile = profile;
        this.id = id;
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new WorldListItemSkin(this);
    }

    public World getWorld() {
        return world;
    }

    public void export() {
        WorldManageUIUtils.export(world);
    }

    public void delete() {
        WorldManageUIUtils.delete(world, () -> parent.remove(this));
    }

    public void copy() {
        WorldManageUIUtils.copyWorld(world, parent::refresh);
    }

    public void reveal() {
        FXUtils.openFolder(world.getFile());
    }

    public void showManagePage() {
        try {
            world.reloadLevelDat();
            Controllers.navigate(new WorldManagePage(world, backupsDir, profile, id));
        } catch (IOException e) {
            LOG.warning("Failed to load level dat of world " + world.getFile(), e);
            Controllers.showToast(i18n("world.load.fail"));
            parent.refresh();
        }
    }

    public void launch() {
        Versions.launchAndEnterWorld(profile, id, world.getFileName());
    }

    public void generateLaunchScript() {
        Versions.generateLaunchScriptForQuickEnterWorld(profile, id, world.getFileName());
    }
}
