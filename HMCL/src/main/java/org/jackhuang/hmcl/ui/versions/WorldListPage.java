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
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.Node;
import javafx.stage.FileChooser;
import org.jackhuang.hmcl.game.GameVersion;
import org.jackhuang.hmcl.game.World;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.setting.Theme;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.*;
import org.jackhuang.hmcl.util.Logging;
import org.jackhuang.hmcl.util.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class WorldListPage extends ListPageBase<WorldListItem> implements VersionPage.VersionLoadable {
    private final BooleanProperty showAll = new SimpleBooleanProperty(this, "showAll", false);

    private Path savesDir;
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
                        .map(WorldListItem::new).collect(Collectors.toList()));
        });
    }

    @Override
    protected ToolbarListPageSkin<WorldListPage> createDefaultSkin() {
        return new WorldListPageSkin();
    }

    public void loadVersion(Profile profile, String id) {
        this.profile = profile;
        this.id = id;
        this.savesDir = profile.getRepository().getRunDirectory(id).toPath().resolve("saves");
        refresh();
    }

    public CompletableFuture<?> refresh() {
        if (profile == null || id == null)
            return CompletableFuture.completedFuture(null);

        setLoading(true);
        return CompletableFuture
                .runAsync(() -> gameVersion = GameVersion.minecraftVersion(profile.getRepository().getVersionJar(id)).orElse(null))
                .thenApplyAsync(unused -> {
                    try (Stream<World> stream = World.getWorlds(savesDir)) {
                        return stream.parallel().collect(Collectors.toList());
                    }
                })
                .whenCompleteAsync((result, exception) -> {
                    worlds = result;
                    setLoading(false);
                    if (exception == null)
                        itemsProperty().setAll(result.stream()
                                .filter(world -> isShowAll() || world.getGameVersion() == null || world.getGameVersion().equals(gameVersion))
                                .map(WorldListItem::new).collect(Collectors.toList()));

                    // https://github.com/huanghongxun/HMCL/issues/938
                    System.gc();
                }, Platform::runLater);
    }

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
        Task.supplyAsync(() -> new World(zipFile.toPath()))
                .whenComplete(Schedulers.javafx(), world -> {
                    Controllers.prompt(i18n("world.name.enter"), (name, resolve, reject) -> {
                        Task.runAsync(() -> world.install(savesDir, name))
                                .whenComplete(Schedulers.javafx(), () -> {
                                    itemsProperty().add(new WorldListItem(new World(savesDir.resolve(name))));
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
                    Logging.LOG.log(Level.WARNING, "Unable to parse world file " + zipFile, e);
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

    private class WorldListPageSkin extends ToolbarListPageSkin<WorldListPage> {

        WorldListPageSkin() {
            super(WorldListPage.this);
        }

        @Override
        protected List<Node> initializeToolbar(WorldListPage skinnable) {
            JFXCheckBox chkShowAll = new JFXCheckBox();
            chkShowAll.getStyleClass().add("jfx-tool-bar-checkbox");
            chkShowAll.textFillProperty().bind(Theme.foregroundFillBinding());
            chkShowAll.setText(i18n("world.show_all"));
            chkShowAll.selectedProperty().bindBidirectional(skinnable.showAllProperty());

            return Arrays.asList(chkShowAll,
                    createToolbarButton(i18n("button.refresh"), SVG::refresh, skinnable::refresh),
                    createToolbarButton(i18n("world.add"), SVG::plus, skinnable::add));
        }
    }
}
