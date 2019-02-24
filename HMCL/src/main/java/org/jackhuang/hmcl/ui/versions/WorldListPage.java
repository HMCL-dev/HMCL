/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2019  huangyuhui <huanghongxun2008@126.com> and contributors
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

import javafx.stage.FileChooser;
import org.jackhuang.hmcl.game.World;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.ListPage;
import org.jackhuang.hmcl.util.Logging;
import org.jackhuang.hmcl.util.io.FileUtils;

import java.io.File;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Level;
import java.util.stream.Collectors;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class WorldListPage extends ListPage<WorldListItem> {
    private Path savesDir;

    public WorldListPage() {
        FXUtils.applyDragListener(this, it -> "zip".equals(FileUtils.getExtension(it)), modpacks -> {
            installWorld(modpacks.get(0));
        });
    }

    public void loadVersion(Profile profile, String id) {
        this.savesDir = profile.getRepository().getRunDirectory(id).toPath().resolve("saves");

        setLoading(true);
        Task.ofResult(() -> World.getWorlds(savesDir).parallel().collect(Collectors.toList()))
                .whenComplete(Schedulers.javafx(), (result, isDependentsSucceeded, exception) -> {
                    setLoading(false);
                    if (isDependentsSucceeded)
                        itemsProperty().setAll(result.stream().map(WorldListItem::new).collect(Collectors.toList()));
                }).start();
    }

    @Override
    public void add() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(i18n("world.import.choose"));
        chooser.getExtensionFilters().setAll(new FileChooser.ExtensionFilter(i18n("world.extension"), "*.zip"));
        List<File> res = chooser.showOpenMultipleDialog(Controllers.getStage());

        if (res == null || res.isEmpty()) return;
        installWorld(res.get(0));
    }

    private void installWorld(File zipFile) {
        // Only accept one world file because user is required to confirm the new world name
        // Or too many input dialogs are popped.
        Task.ofResult(() -> new World(zipFile.toPath()))
                .whenComplete(Schedulers.javafx(), world -> {
                    Controllers.inputDialog(i18n("world.name.enter"), (name, resolve, reject) -> {
                        Task.of(() -> world.install(savesDir, name))
                                .whenComplete(Schedulers.javafx(), () -> {
                                    itemsProperty().add(new WorldListItem(new World(savesDir.resolve(name))));
                                    resolve.run();
                                }, e -> {
                                    if (e instanceof FileAlreadyExistsException)
                                        reject.accept(i18n("world.import.failed", i18n("world.import.already_exists")));
                                    else
                                        reject.accept(i18n("world.import.failed", e.getClass().getName() + ": " + e.getLocalizedMessage()));
                                }).start();
                    }).setInitialText(world.getWorldName());
                }, e -> {
                    Logging.LOG.log(Level.WARNING, "Unable to parse world file " + zipFile, e);
                    Controllers.dialog(i18n("world.import.invalid"));
                }).start();
    }
}
