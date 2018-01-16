/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2017  huangyuhui <huanghongxun2008@126.com>
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
 * along with this program.  If not, see {http://www.gnu.org/licenses/}.
 */
package org.jackhuang.hmcl.ui;

import com.jfoenix.controls.JFXSpinner;
import com.jfoenix.controls.JFXTabPane;
import javafx.application.Platform;
import javafx.beans.value.WeakChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.jackhuang.hmcl.Main;
import org.jackhuang.hmcl.mod.ModInfo;
import org.jackhuang.hmcl.mod.ModManager;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.FileUtils;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.Logging;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.stream.Stream;

public final class ModController {
    @FXML
    private ScrollPane scrollPane;

    @FXML private StackPane rootPane;

    @FXML private VBox modPane;

    @FXML private StackPane contentPane;
    @FXML private JFXSpinner spinner;

    private JFXTabPane parentTab;
    private ModManager modManager;
    private String versionId;

    public void initialize() {
        FXUtils.smoothScrolling(scrollPane);

        rootPane.setOnDragOver(event -> {
            if (event.getGestureSource() != rootPane && event.getDragboard().hasFiles())
                event.acceptTransferModes(TransferMode.COPY_OR_MOVE);
            event.consume();
        });

        rootPane.setOnDragDropped(event -> {
            List<File> mods = event.getDragboard().getFiles();
            Stream<File> stream = null;
            if (mods != null)
                stream = mods.stream()
                        .filter(it -> Arrays.asList("jar", "zip", "litemod").contains(FileUtils.getExtension(it)));
            if (stream != null && stream.findAny().isPresent()) {
                stream.forEach(it -> {
                    try {
                        modManager.addMod(versionId, it);
                    } catch (IOException | IllegalArgumentException e) {
                        Logging.LOG.log(Level.WARNING, "Unable to parse mod file " + it, e);
                    }
                });
                loadMods(modManager, versionId);
                event.setDropCompleted(true);
            }
            event.consume();
        });
    }

    public void loadMods(ModManager modManager, String versionId) {
        this.modManager = modManager;
        this.versionId = versionId;
        Task.of(variables -> {
            synchronized (ModController.this) {
                Platform.runLater(() -> {
                    rootPane.getChildren().remove(contentPane);
                    spinner.setVisible(true);
                });

                modManager.refreshMods(versionId);

                // Surprisingly, if there are a great number of mods, this processing will cause a long UI pause,
                // constructing UI elements.
                // We must do this asynchronously.
                LinkedList<ModItem> list = new LinkedList<>();
                for (ModInfo modInfo : modManager.getMods(versionId)) {
                    ModItem item = new ModItem(modInfo, i -> {
                        modManager.removeMods(versionId, modInfo);
                        loadMods(modManager, versionId);
                    });
                    modInfo.activeProperty().addListener((a, b, newValue) -> {
                        if (newValue)
                            item.getStyleClass().remove("disabled");
                        else
                            item.getStyleClass().add("disabled");
                    });
                    if (!modInfo.isActive())
                        item.getStyleClass().add("disabled");

                    list.add(item);
                }

                Platform.runLater(() -> {
                    rootPane.getChildren().add(contentPane);
                    spinner.setVisible(false);
                });
                variables.set("list", list);
            }
        }).subscribe(Schedulers.javafx(), variables -> {
            FXUtils.onWeakChangeAndOperate(parentTab.getSelectionModel().selectedItemProperty(), newValue -> {
                if (newValue != null && newValue.getUserData() == ModController.this)
                    modPane.getChildren().setAll(variables.<List<ModItem>>get("list"));
            });
        });

    }

    public void onAdd() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(Main.i18n("mods.choose_mod"));
        chooser.getExtensionFilters().setAll(new FileChooser.ExtensionFilter("Mod", "*.jar", "*.zip", "*.litemod"));
        File res = chooser.showOpenDialog(Controllers.getStage());
        if (res == null) return;
        Task.of(() -> modManager.addMod(versionId, res))
                .subscribe(Task.of(Schedulers.javafx(), () -> loadMods(modManager, versionId)));
    }

    public void setParentTab(JFXTabPane parentTab) {
        this.parentTab = parentTab;
    }
}
