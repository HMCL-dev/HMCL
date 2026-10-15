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
package org.jackhuang.hmcl.ui.instances;

import javafx.beans.property.BooleanProperty;
import javafx.collections.ObservableList;
import javafx.scene.control.Skin;
import javafx.stage.FileChooser;
import org.jackhuang.hmcl.game.World;
import org.jackhuang.hmcl.addon.datapack.DataPack;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.ListPageBase;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class DataPackListPage extends ListPageBase<DataPackListPageSkin.DataPackInfoObject> implements WorldManagePage.WorldRefreshable {
    private final World world;
    private final DataPack dataPack;
    final BooleanProperty readOnly;
    private final ReentrantLock lock = new ReentrantLock();

    public DataPackListPage(WorldManagePage worldManagePage) {
        world = worldManagePage.getWorld();
        dataPack = new DataPack(world);
        readOnly = worldManagePage.readOnlyProperty();
        FXUtils.applyDragListener(this, it -> Objects.equals("zip", FileUtils.getExtension(it)),
                this::installMultiDataPack);
        refresh();
    }

    private void installMultiDataPack(List<Path> dataPackPath) {
        dataPackPath.forEach(pack -> {
            try {
                dataPack.installPack(pack, world.getGameVersion());
            } catch (IOException e) {
                LOG.warning("Unable to parse datapack file " + pack, e);
            }
        });
        if (readOnly.get())
            Controllers.showToast(i18n("datapack.reload.toast"));
        refresh();
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new DataPackListPageSkin(this);
    }

    public void refresh() {
        setLoading(true);
        Task.supplyAsync(Schedulers.io(), () -> {
            lock.lock();
            try {
                dataPack.refresh();
                return dataPack.getLocalFiles().stream().map(DataPackListPageSkin.DataPackInfoObject::new).toList();
            } finally {
                lock.unlock();
            }
        }).whenComplete(Schedulers.javafx(), (result, exception) -> {
            if (exception == null) {
                getItems().setAll(result);
            } else {
                LOG.warning("Error refreshing data pack list page", exception);
                getItems().clear();
            }
            setLoading(false);
        }).start();
    }

    public void add() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(i18n("datapack.add.title"));
        chooser.getExtensionFilters().setAll(new FileChooser.ExtensionFilter(i18n("extension.datapack"), "*.zip"));
        List<Path> res = Controllers.showOpenMultipleDialog(chooser);
        if (res != null) {
            installMultiDataPack(res);
        }
    }

    void removeSelected(ObservableList<DataPackListPageSkin.DataPackInfoObject> selectedItems) {
        try {
            if (dataPack.removePacks(selectedItems.stream().map(DataPackListPageSkin.DataPackInfoObject::getPackInfo).toList()))
                refresh();
        } catch (IOException e) {
            // Fail to remove mods if the game is running or the datapack is absent.
            LOG.warning("Failed to delete datapacks", e);
            refresh();
        }
    }

    void enableSelected(ObservableList<DataPackListPageSkin.DataPackInfoObject> selectedItems) {
        selectedItems.stream()
                .map(DataPackListPageSkin.DataPackInfoObject::getPackInfo)
                .forEach(pack -> pack.setActive(true));
    }

    void disableSelected(ObservableList<DataPackListPageSkin.DataPackInfoObject> selectedItems) {
        selectedItems.stream()
                .map(DataPackListPageSkin.DataPackInfoObject::getPackInfo)
                .forEach(pack -> pack.setActive(false));
    }

    void openDataPackFolder() {
        FXUtils.openFolder(dataPack.getDirectory());
    }

    @NotNull Predicate<DataPackListPageSkin.DataPackInfoObject> updateSearchPredicate(String queryString) {
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
            String id = dataPack.getPackInfo().getFileName();
            String description = dataPack.getPackInfo().getInfo().description().toString();
            return stringPredicate.test(id) || stringPredicate.test(description);
        };
    }
}
