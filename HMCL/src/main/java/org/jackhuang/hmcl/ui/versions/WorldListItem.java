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
import javafx.stage.FileChooser;
import org.jackhuang.hmcl.game.World;
import org.jackhuang.hmcl.game.WorldLockedException;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane.MessageType;
import org.jackhuang.hmcl.ui.wizard.SinglePageWizardProvider;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.io.FileUtils;

import java.nio.file.Path;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

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
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(i18n("world.export.title"));
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(i18n("world"), "*.zip"));
        fileChooser.setInitialFileName(world.getWorldName());
        Path file = FileUtils.toPath(fileChooser.showSaveDialog(Controllers.getStage()));
        if (file == null) {
            return;
        }

        Controllers.getDecorator().startWizard(new SinglePageWizardProvider(controller -> new WorldExportPage(world, file, controller::onFinish)));
    }

    public void delete() {
        Controllers.confirm(
                i18n("button.remove.confirm"),
                i18n("world.delete"),
                () -> Task.runAsync(world::delete)
                        .whenComplete(Schedulers.javafx(), (result, exception) -> {
                            if (exception == null) {
                                parent.remove(this);
                            } else if (exception instanceof WorldLockedException) {
                                Controllers.dialog(i18n("world.locked.failed"), null, MessageType.WARNING);
                            } else {
                                Controllers.dialog(i18n("world.delete.failed", StringUtils.getStackTrace(exception)), null, MessageType.WARNING);
                            }
                        }).start(),
                null
        );
    }

    public void reveal() {
        FXUtils.openFolder(world.getFile());
    }

    public void showManagePage() {
        Controllers.navigate(new WorldManagePage(world, backupsDir, profile, id));
    }

    public void launch() {
        Versions.launchAndEnterWorld(profile, id, world.getFileName());
    }

    public void generateLaunchScript() {
        Versions.generateLaunchScriptForQuickEnterWorld(profile, id, world.getFileName());
    }
}
