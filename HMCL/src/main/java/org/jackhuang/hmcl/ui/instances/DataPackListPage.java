// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.instances;

import javafx.beans.property.BooleanProperty;
import javafx.collections.ObservableList;
import javafx.scene.control.Skin;
import javafx.stage.FileChooser;
import org.jackhuang.hmcl.addon.datapack.DataPackFile;
import org.jackhuang.hmcl.addon.datapack.DataPackManager;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.ListPageBase;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class DataPackListPage extends ListPageBase<DataPackListPageSkin.DataPackInfoObject> implements WorldManagePage.WorldRefreshable {
    private final DataPackManager dataPackManager;
    final BooleanProperty readOnly;
    private final ReentrantLock lock = new ReentrantLock();

    public DataPackListPage(WorldManagePage worldManagePage) {
        dataPackManager = worldManagePage.getWorld().getDataPackManager();
        readOnly = worldManagePage.readOnlyProperty();
        FXUtils.applyDragListener(this, it -> Objects.equals("zip", FileUtils.getExtension(it)), this::addFiles);
        refresh();
    }

    private void addFiles(List<Path> files) {
        List<Path> failures = new ArrayList<>();
        Task.runAsync(() -> {
            for (Path file : files) {
                try {
                    dataPackManager.importDataPack(file);
                } catch (Exception e) {
                    LOG.warning("Failed to add data pack", e);
                    failures.add(file);
                }
            }
        }).thenRunAsync(Schedulers.javafx(), () -> {
            if (!failures.isEmpty()) {
                StringBuilder failure = new StringBuilder(i18n("datapack.add.failed"));
                for (Path file: failures) {
                    failure.append("\n").append(file.toString());
                }
                Controllers.dialog(failure.toString(), i18n("message.error"), MessageDialogPane.MessageType.ERROR);
            }
            if (readOnly.get())
                Controllers.showToast(i18n("datapack.reload.toast"));
            refresh();
        }).start();
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
                dataPackManager.refresh();
                return dataPackManager.getLocalFiles().stream().map(DataPackListPageSkin.DataPackInfoObject::new).toList();
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
            addFiles(res);
        }
    }

    void removeSelected(ObservableList<DataPackListPageSkin.DataPackInfoObject> selectedItems) {
        List<String> failed = new ArrayList<>();
        for (DataPackListPageSkin.DataPackInfoObject item : selectedItems) {
            DataPackFile dataPack = item.getFile();
            try {
                dataPackManager.removePack(dataPack);
            } catch (IOException e) {
                failed.add(dataPack.getFile().toString());
                LOG.warning("Failed to delete datapack \"" + dataPack.getFileName() + "\"", e);
            }
        }
        refresh();
        if (!failed.isEmpty())
            Controllers.dialog(i18n("datapack.delete.failed") + "\n" + String.join(",\n", failed),
                    i18n("message.error"), MessageDialogPane.MessageType.ERROR);
    }

    void setSelectedEnabled(ObservableList<DataPackListPageSkin.DataPackInfoObject> selectedItems, boolean enabled) {
        List<String> failed = new ArrayList<>();
        selectedItems.stream()
                .map(DataPackListPageSkin.DataPackInfoObject::getFile)
                .forEach(dataPack -> {
                    try {
                        dataPack.setActive(enabled);
                    } catch (IOException e) {
                        failed.add(dataPack.getFile().toString());
                        LOG.warning("Failed to enable or disable datapack \"" + dataPack.getFileName() + "\"", e);
                    }
                });
        if (!failed.isEmpty())
            Controllers.dialog(i18n("datapack.toggle.failed") + "\n" + String.join(",\n", failed),
                    i18n("message.error"), MessageDialogPane.MessageType.ERROR);
    }

    void openDataPackFolder() {
        FXUtils.openFolder(dataPackManager.getDirectory());
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
            String id = dataPack.getFile().getFileName();
            String description = dataPack.getFile().getInfo().description().toString();
            return stringPredicate.test(id) || stringPredicate.test(description);
        };
    }
}
