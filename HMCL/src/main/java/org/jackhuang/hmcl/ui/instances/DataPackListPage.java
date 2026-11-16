// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.instances;

import javafx.beans.property.BooleanProperty;
import javafx.collections.ObservableList;
import javafx.scene.control.Skin;
import javafx.stage.FileChooser;
import org.jackhuang.hmcl.game.World;
import org.jackhuang.hmcl.addon.pack.datapack.DataPack;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.ListPageBase;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.javafx.MappedObservableList;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class DataPackListPage extends ListPageBase<DataPackListPageSkin.DataPackInfoObject> implements WorldManagePage.WorldRefreshable {
    private final World world;
    private final DataPack dataPack;
    final BooleanProperty readOnly;

    public DataPackListPage(WorldManagePage worldManagePage) {
        world = worldManagePage.getWorld();
        dataPack = new DataPack(world.getFile().resolve("datapacks"));
        setItems(MappedObservableList.create(dataPack.getPacks(), DataPackListPageSkin.DataPackInfoObject::new));
        readOnly = worldManagePage.readOnlyProperty();
        FXUtils.applyDragListener(this, it -> Objects.equals("zip", FileUtils.getExtension(it)),
                this::installMultiDataPack, this::refresh);

        refresh();
    }

    private void installMultiDataPack(List<Path> dataPackPath) {
        dataPackPath.forEach(this::installSingleDataPack);
        if (readOnly.get()) {
            Controllers.showToast(i18n("datapack.reload.toast"));
        }
    }

    private void installSingleDataPack(Path dataPack) {
        try {
            this.dataPack.installPack(dataPack, world.getGameVersion());
        } catch (IOException | IllegalArgumentException e) {
            LOG.warning("Unable to parse datapack file " + dataPack, e);
        }
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new DataPackListPageSkin(this);
    }

    public void refresh() {
        setLoading(true);
        Task.runAsync(dataPack::loadFromDir)
                .withRunAsync(Schedulers.javafx(), () -> setLoading(false))
                .start();
    }

    public void add() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(i18n("datapack.add.title"));
        chooser.getExtensionFilters().setAll(new FileChooser.ExtensionFilter(i18n("extension.datapack"), "*.zip"));
        List<Path> res = Controllers.showOpenMultipleDialog(chooser);
        if (res != null) {
            installMultiDataPack(res);
        }

        dataPack.loadFromDir();
    }

    void removeSelected(ObservableList<DataPackListPageSkin.DataPackInfoObject> selectedItems) {
        List<String> failed = new ArrayList<>();
        selectedItems.stream()
                .map(DataPackListPageSkin.DataPackInfoObject::getPackInfo)
                .forEach(pack -> {
                    try {
                        dataPack.deletePack(pack);
                    } catch (IOException e) {
                        failed.add(pack.getPath().toString());
                        LOG.warning("Failed to delete datapack \"" + pack.getId() + "\"", e);
                    }
                });
        if (!failed.isEmpty())
            Controllers.dialog(i18n("datapack.delete.failed") + "\n" + String.join(",\n", failed),
                    i18n("message.error"), MessageDialogPane.MessageType.ERROR);
    }

    void setSelectedEnabled(ObservableList<DataPackListPageSkin.DataPackInfoObject> selectedItems, boolean enabled) {
        List<String> failed = new ArrayList<>();
        selectedItems.stream()
                .map(DataPackListPageSkin.DataPackInfoObject::getPackInfo)
                .forEach(pack -> {
                    try {
                        pack.setActive(enabled);
                    } catch (IOException e) {
                        failed.add(pack.getPath().toString());
                        LOG.warning("Failed to enable or disable datapack \"" + pack.getId() + "\"", e);
                    }
                });
        if (!failed.isEmpty())
            Controllers.dialog(i18n("datapack.toggle.failed") + "\n" + String.join(",\n", failed),
                    i18n("message.error"), MessageDialogPane.MessageType.ERROR);
    }

    void openDataPackFolder() {
        FXUtils.openFolder(dataPack.getPath());
    }
}
