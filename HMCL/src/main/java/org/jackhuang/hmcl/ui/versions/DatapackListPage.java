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

import javafx.collections.ObservableList;
import javafx.scene.control.Skin;
import javafx.stage.FileChooser;
import org.jackhuang.hmcl.mod.Datapack;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.ListPageBase;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.javafx.MappedObservableList;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public final class DatapackListPage extends ListPageBase<DatapackListPageSkin.DatapackInfoObject> {
    private final Path worldDir;
    private final Datapack datapack;

    public DatapackListPage(WorldManagePage worldManagePage) {
        this.worldDir = worldManagePage.getWorld().getFile();

        datapack = new Datapack(worldDir.resolve("datapacks"));
        datapack.loadFromDir();

        setItems(MappedObservableList.create(datapack.getInfo(), DatapackListPageSkin.DatapackInfoObject::new));

        FXUtils.applyDragListener(this, it -> Objects.equals("zip", FileUtils.getExtension(it)),
                mods -> mods.forEach(this::installSingleDatapack), this::refresh);
    }

    private void installSingleDatapack(File datapack) {
        try {
            Datapack zip = new Datapack(datapack.toPath());
            zip.loadFromZip();
            zip.installTo(worldDir);
        } catch (IOException | IllegalArgumentException e) {
            LOG.warning("Unable to parse datapack file " + datapack, e);
        }
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new DatapackListPageSkin(this);
    }

    public void refresh() {
        setLoading(true);
        Task.runAsync(datapack::loadFromDir)
                .withRunAsync(Schedulers.javafx(), () -> {
                    setLoading(false);

                    // https://github.com/HMCL-dev/HMCL/issues/938
                    System.gc();
                })
                .start();
    }

    public void add() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(i18n("datapack.choose_datapack"));
        chooser.getExtensionFilters().setAll(new FileChooser.ExtensionFilter(i18n("datapack.extension"), "*.zip"));
        List<File> res = chooser.showOpenMultipleDialog(Controllers.getStage());

        if (res != null)
            res.forEach(this::installSingleDatapack);

        datapack.loadFromDir();
    }

    void removeSelected(ObservableList<DatapackListPageSkin.DatapackInfoObject> selectedItems) {
        selectedItems.stream()
                .map(DatapackListPageSkin.DatapackInfoObject::getPackInfo)
                .forEach(pack -> {
                    try {
                        datapack.deletePack(pack);
                    } catch (IOException e) {
                        // Fail to remove mods if the game is running or the datapack is absent.
                        LOG.warning("Failed to delete datapack " + pack);
                    }
                });
    }

    void enableSelected(ObservableList<DatapackListPageSkin.DatapackInfoObject> selectedItems) {
        selectedItems.stream()
                .map(DatapackListPageSkin.DatapackInfoObject::getPackInfo)
                .forEach(info -> info.setActive(true));
    }

    void disableSelected(ObservableList<DatapackListPageSkin.DatapackInfoObject> selectedItems) {
        selectedItems.stream()
                .map(DatapackListPageSkin.DatapackInfoObject::getPackInfo)
                .forEach(info -> info.setActive(false));
    }
}
