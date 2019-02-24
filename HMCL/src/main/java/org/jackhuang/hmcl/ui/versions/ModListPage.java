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

import com.jfoenix.utils.JFXUtilities;
import com.jfoenix.controls.JFXTabPane;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ListProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleListProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Control;
import javafx.scene.control.Skin;
import javafx.scene.control.TreeItem;
import javafx.stage.FileChooser;
import org.jackhuang.hmcl.mod.ModInfo;
import org.jackhuang.hmcl.mod.ModManager;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.util.Logging;
import org.jackhuang.hmcl.util.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.stream.Collectors;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public final class ModListPage extends Control {
    private final ListProperty<ModListPageSkin.ModInfoObject> items = new SimpleListProperty<>(this, "items", FXCollections.observableArrayList());
    private final BooleanProperty loading = new SimpleBooleanProperty(this, "loading", false);

    private JFXTabPane parentTab;
    private ModManager modManager;

    public ModListPage() {

        FXUtils.applyDragListener(this, it -> Arrays.asList("jar", "zip", "litemod").contains(FileUtils.getExtension(it)), mods -> {
            mods.forEach(it -> {
                try {
                    modManager.addMod(it);
                } catch (IOException | IllegalArgumentException e) {
                    Logging.LOG.log(Level.WARNING, "Unable to parse mod file " + it, e);
                }
            });
            loadMods(modManager);
        });
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new ModListPageSkin(this);
    }

    public void refresh() {
        loadMods(modManager);
    }

    public void loadVersion(Profile profile, String id) {
        loadMods(profile.getRepository().getModManager(id));
    }

    public void loadMods(ModManager modManager) {
        this.modManager = modManager;
        Task.ofResult(() -> {
            synchronized (ModListPage.this) {
                JFXUtilities.runInFX(() -> loadingProperty().set(true));
                modManager.refreshMods();
                return new LinkedList<>(modManager.getMods());
            }
        }).whenComplete(Schedulers.javafx(), (list, isDependentsSucceeded, exception) -> {
            loadingProperty().set(false);
            if (isDependentsSucceeded)
                FXUtils.onWeakChangeAndOperate(parentTab.getSelectionModel().selectedItemProperty(), newValue -> {
                    if (newValue != null && newValue.getUserData() == ModListPage.this)
                        itemsProperty().setAll(list.stream().map(ModListPageSkin.ModInfoObject::new).collect(Collectors.toList()));
                });
        }).start();
    }

    public void add() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(i18n("mods.choose_mod"));
        chooser.getExtensionFilters().setAll(new FileChooser.ExtensionFilter(i18n("extension.mod"), "*.jar", "*.zip", "*.litemod"));
        List<File> res = chooser.showOpenMultipleDialog(Controllers.getStage());

        // It's guaranteed that succeeded and failed are thread safe here.
        List<String> succeeded = new LinkedList<>();
        List<String> failed = new LinkedList<>();
        if (res == null) return;
        Task.of(() -> {
            for (File file : res) {
                try {
                    modManager.addMod(file);
                    succeeded.add(file.getName());
                } catch (Exception e) {
                    Logging.LOG.log(Level.WARNING, "Unable to add mod " + file, e);
                    failed.add(file.getName());

                    // Actually addMod will not throw exceptions because FileChooser has already filtered files.
                }
            }
        }).with(Task.of(Schedulers.javafx(), () -> {
            List<String> prompt = new LinkedList<>();
            if (!succeeded.isEmpty())
                prompt.add(i18n("mods.add.success", String.join(", ", succeeded)));
            if (!failed.isEmpty())
                prompt.add(i18n("mods.add.failed", String.join(", ", failed)));
            Controllers.dialog(String.join("\n", prompt), i18n("mods.add"));
            loadMods(modManager);
        })).start();
    }

    public void setParentTab(JFXTabPane parentTab) {
        this.parentTab = parentTab;
    }

    public void removeSelectedMods(ObservableList<TreeItem<ModListPageSkin.ModInfoObject>> selectedItems) {
        try {
            modManager.removeMods(selectedItems.stream()
                    .map(TreeItem::getValue)
                    .map(ModListPageSkin.ModInfoObject::getModInfo)
                    .toArray(ModInfo[]::new));
            loadMods(modManager);
        } catch (IOException ignore) {
            // Fail to remove mods if the game is running or the mod is absent.
        }
    }

    public void enableSelectedMods(ObservableList<TreeItem<ModListPageSkin.ModInfoObject>> selectedItems) {
        selectedItems.stream()
                .map(TreeItem::getValue)
                .map(ModListPageSkin.ModInfoObject::getModInfo)
                .forEach(info -> info.setActive(true));
    }

    public void disableSelectedMods(ObservableList<TreeItem<ModListPageSkin.ModInfoObject>> selectedItems) {
        selectedItems.stream()
                .map(TreeItem::getValue)
                .map(ModListPageSkin.ModInfoObject::getModInfo)
                .forEach(info -> info.setActive(false));
    }

    public ObservableList<ModListPageSkin.ModInfoObject> getItems() {
        return items.get();
    }

    public void setItems(ObservableList<ModListPageSkin.ModInfoObject> items) {
        this.items.set(items);
    }

    public ListProperty<ModListPageSkin.ModInfoObject> itemsProperty() {
        return items;
    }

    public boolean isLoading() {
        return loading.get();
    }

    public void setLoading(boolean loading) {
        this.loading.set(loading);
    }

    public BooleanProperty loadingProperty() {
        return loading;
    }
}
