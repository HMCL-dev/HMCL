/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
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

import com.jfoenix.controls.JFXCheckBox;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.Node;
import javafx.stage.FileChooser;
import org.jackhuang.hmcl.game.World;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.*;
import org.jackhuang.hmcl.util.io.FileUtils;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class WorldListPage extends ListPageBase<WorldListItem> implements VersionPage.VersionLoadable {
    private final BooleanProperty showAll = new SimpleBooleanProperty(this, "showAll", false);

    private Path savesDir;
    private Path backupsDir;
    private List<World> worlds;
    private Profile profile;
    private String id;
    private String gameVersion;

    public WorldListPage() {
        FXUtils.applyDragListener(this, it -> "zip".equals(FileUtils.getExtension(it)), modpacks -> {
            installWorld(modpacks.get(0));
        });

        showAll.addListener(e -> {
            if (worlds != null)
                itemsProperty().setAll(worlds.stream()
                        .filter(world -> isShowAll() || world.getGameVersion() == null || world.getGameVersion().equals(gameVersion))
                        .map(world -> new WorldListItem(this, world, backupsDir)).toList());
        });
    }

    @Override
    protected ToolbarListPageSkin<WorldListPage> createDefaultSkin() {
        return new WorldListPageSkin();
    }

    @Override
    public void loadVersion(Profile profile, String id) {
        this.profile = profile;
        this.id = id;
        this.savesDir = profile.getRepository().getSavesDirectory(id);
        this.backupsDir = profile.getRepository().getBackupsDirectory(id);
        refresh();
    }

    public void remove(WorldListItem item) {
        itemsProperty().remove(item);
    }

    public void refresh() {
        if (profile == null || id == null)
            return;

        setLoading(true);
        Task.runAsync(() -> gameVersion = profile.getRepository().getGameVersion(id).orElse(null))
                .thenApplyAsync(unused -> {
                    try (Stream<World> stream = World.getWorlds(savesDir)) {
                        return stream.parallel().collect(Collectors.toList());
                    }
                })
                .whenComplete(Schedulers.javafx(), (result, exception) -> {
                    worlds = result;
                    setLoading(false);
                    if (exception == null) {
                        itemsProperty().setAll(result.stream()
                                .filter(world -> isShowAll() || world.getGameVersion() == null || world.getGameVersion().equals(gameVersion))
                                .map(world -> new WorldListItem(this, world, backupsDir))
                                .collect(Collectors.toList()));
                    } else {
                        LOG.warning("Failed to load world list page", exception);
                    }
                }).start();
    }

    public void add() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(i18n("world.import.choose"));
        chooser.getExtensionFilters().setAll(new FileChooser.ExtensionFilter(i18n("world.extension"), "*.zip"));
        List<Path> res = FileUtils.toPaths(chooser.showOpenMultipleDialog(Controllers.getStage()));

        if (res == null || res.isEmpty()) return;
        installWorld(res.get(0));
    }

    public void download() {
        Controllers.getDownloadPage().showWorldDownloads();
        Controllers.navigate(Controllers.getDownloadPage());
    }

    private void installWorld(Path zipFile) {
        // Only accept one world file because user is required to confirm the new world name
        // Or too many input dialogs are popped.
        Task.supplyAsync(() -> new World(zipFile))
                .whenComplete(Schedulers.javafx(), world -> {
                    Controllers.prompt(i18n("world.name.enter"), (name, resolve, reject) -> {
                        Task.runAsync(() -> world.install(savesDir, name))
                                .whenComplete(Schedulers.javafx(), () -> {
                                    itemsProperty().add(new WorldListItem(this, new World(savesDir.resolve(name)), backupsDir));
                                    resolve.run();
                                }, e -> {
                                    if (e instanceof FileAlreadyExistsException)
                                        reject.accept(i18n("world.import.failed", i18n("world.import.already_exists")));
                                    else if (e instanceof IOException && e.getCause() instanceof InvalidPathException)
                                        reject.accept(i18n("world.import.failed", i18n("install.new_game.malformed")));
                                    else
                                        reject.accept(i18n("world.import.failed", e.getClass().getName() + ": " + e.getLocalizedMessage()));
                                }).start();
                    }, world.getWorldName());
                }, e -> {
                    LOG.warning("Unable to parse world file " + zipFile, e);
                    Controllers.dialog(i18n("world.import.invalid"));
                }).start();
    }

    public boolean isShowAll() {
        return showAll.get();
    }

    public BooleanProperty showAllProperty() {
        return showAll;
    }

    public void setShowAll(boolean showAll) {
        this.showAll.set(showAll);
    }

    private final class WorldListPageSkin extends ToolbarListPageSkin<WorldListPage> {

        WorldListPageSkin() {
            super(WorldListPage.this);
        }

        @Override
        protected List<Node> initializeToolbar(WorldListPage skinnable) {
            JFXCheckBox chkShowAll = new JFXCheckBox();
            chkShowAll.setText(i18n("world.show_all"));
            chkShowAll.selectedProperty().bindBidirectional(skinnable.showAllProperty());

            return Arrays.asList(chkShowAll,
                    createToolbarButton2(i18n("button.refresh"), SVG.REFRESH, skinnable::refresh),
                    createToolbarButton2(i18n("world.add"), SVG.ADD, skinnable::add),
                    createToolbarButton2(i18n("world.download"), SVG.DOWNLOAD, skinnable::download));
        }
    }
}
