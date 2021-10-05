/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
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

import com.jfoenix.controls.*;
import com.jfoenix.controls.datamodels.treetable.RecursiveTreeObject;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import org.jackhuang.hmcl.mod.LocalMod;
import org.jackhuang.hmcl.mod.curse.CurseAddon;
import org.jackhuang.hmcl.mod.modrinth.ModrinthRemoteModRepository;
import org.jackhuang.hmcl.ui.construct.MDListCell;
import org.jackhuang.hmcl.ui.construct.PageCloseEvent;
import org.jackhuang.hmcl.ui.construct.TwoLineListItem;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;

import java.util.List;
import java.util.stream.Collectors;

import static org.jackhuang.hmcl.ui.FXUtils.onEscPressed;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class ModUpdatesPane extends BorderPane implements DecoratorPage {
    private final ReadOnlyObjectWrapper<State> state = new ReadOnlyObjectWrapper<>(DecoratorPage.State.fromTitle(i18n("download"), -1));

    public ModUpdatesPane(List<LocalMod.ModUpdate> updates) {

        JFXTreeTableColumn<ModUpdateObject, String> fileNameColumn = new JFXTreeTableColumn<>(i18n("mods.check_updates.file"));
        fileNameColumn.setCellValueFactory(data -> {
            if (fileNameColumn.validateValue(data)) {
                return data.getValue().getValue().fileName;
            } else {
                return fileNameColumn.getComputedValue(data);
            }
        });

        JFXTreeTableColumn<ModUpdateObject, String> currentVersionColumn = new JFXTreeTableColumn<>(i18n("mods.check_updates.current_version"));
        currentVersionColumn.setCellValueFactory(data -> {
            if (currentVersionColumn.validateValue(data)) {
                return data.getValue().getValue().currentVersion;
            } else {
                return currentVersionColumn.getComputedValue(data);
            }
        });

        JFXTreeTableColumn<ModUpdateObject, String> targetVersionColumn = new JFXTreeTableColumn<>(i18n("mods.check_updates.target_version"));
        targetVersionColumn.setCellValueFactory(data -> {
            if (targetVersionColumn.validateValue(data)) {
                return data.getValue().getValue().targetVersion;
            } else {
                return targetVersionColumn.getComputedValue(data);
            }
        });

        ObservableList<ModUpdateObject> objects = FXCollections.observableList(updates.stream().map(ModUpdateObject::new).collect(Collectors.toList()));

        RecursiveTreeItem<ModUpdateObject> root = new RecursiveTreeItem<>(
                objects,
                RecursiveTreeObject::getChildren);

        JFXTreeTableView<ModUpdateObject> table = new JFXTreeTableView<>(root);
        table.setShowRoot(false);
        table.setEditable(true);
        table.getColumns().setAll(fileNameColumn, currentVersionColumn, targetVersionColumn);

        setCenter(table);

        HBox actions = new HBox(8);
        actions.setAlignment(Pos.CENTER_RIGHT);

        JFXButton nextButton = new JFXButton();
        nextButton.getStyleClass().add("jfx-button-raised");
        nextButton.setButtonType(JFXButton.ButtonType.RAISED);
        nextButton.setOnAction(e -> updateMods());

        JFXButton cancelButton = new JFXButton();
        cancelButton.getStyleClass().add("jfx-button-raised");
        cancelButton.setButtonType(JFXButton.ButtonType.RAISED);
        cancelButton.setOnAction(e -> fireEvent(new PageCloseEvent()));
        onEscPressed(this, cancelButton::fire);

        actions.getChildren().setAll(nextButton, cancelButton);
        setBottom(actions);
    }

    private void updateMods() {

    }

    @Override
    public ReadOnlyObjectWrapper<State> stateProperty() {
        return state;
    }

    public static class ModUpdateCell extends MDListCell<LocalMod.ModUpdate> {
        TwoLineListItem content = new TwoLineListItem();

        public ModUpdateCell(JFXListView<LocalMod.ModUpdate> listView) {
            super(listView);

            getContainer().getChildren().setAll(content);
        }

        @Override
        protected void updateControl(LocalMod.ModUpdate item, boolean empty) {
            if (empty) return;
            ModTranslations.Mod mod = ModTranslations.getModById(item.getLocalMod().getId());
            content.setTitle(mod != null ? mod.getDisplayName() : item.getCurrentVersion().getName());
            content.setSubtitle(item.getLocalMod().getFileName());
            content.getTags().setAll();

            if (item.getCurrentVersion().getSelf() instanceof CurseAddon.LatestFile) {
                content.getTags().add("Curseforge");
            } else if (item.getCurrentVersion().getSelf() instanceof ModrinthRemoteModRepository.ModVersion) {
                content.getTags().add("Modrinth");
            }
        }
    }

    private static class ModUpdateObject extends RecursiveTreeObject<ModUpdateObject> {
        final LocalMod.ModUpdate data;
        final StringProperty fileName = new SimpleStringProperty();
        final StringProperty currentVersion = new SimpleStringProperty();
        final StringProperty targetVersion = new SimpleStringProperty();

        public ModUpdateObject(LocalMod.ModUpdate data) {
            this.data = data;

            fileName.set(data.getLocalMod().getFileName());
            currentVersion.set(data.getCurrentVersion().getName());
            targetVersion.set(data.getCandidates().get(0).getName());
        }
    }
}
