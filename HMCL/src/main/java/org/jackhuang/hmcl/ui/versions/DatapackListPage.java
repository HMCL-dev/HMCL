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

import javafx.beans.property.BooleanProperty;
import javafx.collections.ObservableList;
import javafx.scene.control.Skin;
import javafx.stage.FileChooser;
import org.jackhuang.hmcl.mod.Datapack;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.ListPageBase;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.javafx.MappedObservableList;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class DatapackListPage extends ListPageBase<DatapackListPageSkin.DatapackInfoObject> implements WorldManagePage.WorldRefreshable {
    private final Path worldDir;
    private final Datapack datapack;
    final BooleanProperty readOnly;

    public DatapackListPage(WorldManagePage worldManagePage) {
        this.worldDir = worldManagePage.getWorld().getFile();
        datapack = new Datapack(worldDir.resolve("datapacks"));
        setItems(MappedObservableList.create(datapack.getPacks(), DatapackListPageSkin.DatapackInfoObject::new));
        readOnly = worldManagePage.readOnlyProperty();
        FXUtils.applyDragListener(this, it -> Objects.equals("zip", FileUtils.getExtension(it)),
                this::installMultiDatapack, this::refresh);

        refresh();
    }

    private void installMultiDatapack(List<Path> datapackPath) {
        datapackPath.forEach(this::installSingleDatapack);
        if (readOnly.get()) {
            Controllers.showToast(i18n("datapack.reload.toast"));
        }
    }

    private void installSingleDatapack(Path datapack) {
        try {
            this.datapack.installPack(datapack);
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
                .withRunAsync(Schedulers.javafx(), () -> setLoading(false))
                .start();
    }

    public void add() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(i18n("datapack.add.title"));
        chooser.getExtensionFilters().setAll(new FileChooser.ExtensionFilter(i18n("extension.datapack"), "*.zip"));
        List<Path> res = FileUtils.toPaths(chooser.showOpenMultipleDialog(Controllers.getStage()));

        if (res != null) {
            installMultiDatapack(res);
        }

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
                        LOG.warning("Failed to delete datapack \"" + pack.getId() + "\"", e);
                    }
                });
    }

    void enableSelected(ObservableList<DatapackListPageSkin.DatapackInfoObject> selectedItems) {
        selectedItems.stream()
                .map(DatapackListPageSkin.DatapackInfoObject::getPackInfo)
                .forEach(pack -> pack.setActive(true));
    }

    void disableSelected(ObservableList<DatapackListPageSkin.DatapackInfoObject> selectedItems) {
        selectedItems.stream()
                .map(DatapackListPageSkin.DatapackInfoObject::getPackInfo)
                .forEach(pack -> pack.setActive(false));
    }

    void openDataPackFolder() {
        FXUtils.openFolder(datapack.getPath());
    }

    @NotNull Predicate<DatapackListPageSkin.DatapackInfoObject> updateSearchPredicate(String queryString) {
        if (queryString.isBlank()) {
            return dataPack -> true;
        }

        final Predicate<String> stringPredicate;
        if (queryString.startsWith("regex:")) {
            try {
                Pattern pattern = Pattern.compile(StringUtils.substringAfter(queryString, "regex:"));
                stringPredicate = s -> s != null && pattern.matcher(s).find();
            } catch (Exception e) {
                return dataPack -> false;
            }
        } else {
            String lowerCaseFilter = queryString.toLowerCase(Locale.ROOT);
            stringPredicate = s -> s != null && s.toLowerCase(Locale.ROOT).contains(lowerCaseFilter);
        }

        return dataPack -> {
            String id = dataPack.getPackInfo().getId();
            String description = dataPack.getPackInfo().getDescription().toString();
            return stringPredicate.test(id) || stringPredicate.test(description);
        };
    }
}
