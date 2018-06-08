/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2018  huangyuhui <huanghongxun2008@126.com>
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

import com.jfoenix.concurrency.JFXUtilities;
import com.jfoenix.controls.JFXSpinner;
import com.jfoenix.controls.JFXTabPane;
import javafx.fxml.FXML;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.jackhuang.hmcl.Launcher;
import org.jackhuang.hmcl.mod.ModInfo;
import org.jackhuang.hmcl.mod.ModManager;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.FileUtils;
import org.jackhuang.hmcl.util.Logging;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import java.util.stream.Collectors;
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

    @FXML
    private void initialize() {
        FXUtils.smoothScrolling(scrollPane);

        rootPane.setOnDragOver(event -> {
            if (event.getGestureSource() != rootPane && event.getDragboard().hasFiles())
                event.acceptTransferModes(TransferMode.COPY_OR_MOVE);
            event.consume();
        });

        rootPane.setOnDragDropped(event -> {
            List<File> files = event.getDragboard().getFiles();
            if (files != null) {
                Collection<File> mods = files.stream()
                        .filter(it -> Arrays.asList("jar", "zip", "litemod").contains(FileUtils.getExtension(it)))
                        .collect(Collectors.toList());
                if (!mods.isEmpty()) {
                    mods.forEach(it -> {
                        try {
                            modManager.addMod(versionId, it);
                        } catch (IOException | IllegalArgumentException e) {
                            Logging.LOG.log(Level.WARNING, "Unable to parse mod file " + it, e);
                        }
                    });
                    loadMods(modManager, versionId);
                    event.setDropCompleted(true);
                }
            }
            event.consume();
        });
    }

    public void loadMods(ModManager modManager, String versionId) {
        this.modManager = modManager;
        this.versionId = versionId;
        Task.of(variables -> {
            synchronized (ModController.this) {
                JFXUtilities.runInFX(() -> {
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

                variables.set("list", list);
            }
        }).finalized(Schedulers.javafx(), (variables, isDependentsSucceeded) -> {
            rootPane.getChildren().add(contentPane);
            spinner.setVisible(false);
            if (isDependentsSucceeded)
                FXUtils.onWeakChangeAndOperate(parentTab.getSelectionModel().selectedItemProperty(), newValue -> {
                    if (newValue != null && newValue.getUserData() == ModController.this)
                        modPane.getChildren().setAll(variables.<List<ModItem>>get("list"));
                });
        }).start();
    }

    @FXML
    private void onAdd() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(Launcher.i18n("mods.choose_mod"));
        chooser.getExtensionFilters().setAll(new FileChooser.ExtensionFilter(Launcher.i18n("extension.mod"), "*.jar", "*.zip", "*.litemod"));
        List<File> res = chooser.showOpenMultipleDialog(Controllers.getStage());

        // It's guaranteed that succeeded and failed are thread safe here.
        List<String> succeeded = new LinkedList<>();
        List<String> failed = new LinkedList<>();
        if (res == null) return;
        Task.of(variables -> {
            for (File file : res) {
                try {
                    modManager.addMod(versionId, file);
                    succeeded.add(file.getName());
                } catch (Exception e) {
                    Logging.LOG.log(Level.WARNING, "Unable to add mod " + file, e);
                    failed.add(file.getName());

                    // Actually addMod will not throw exceptions because FileChooser has already filtered files.
                }
            }
        }).with(Task.of(Schedulers.javafx(), variables -> {
            List<String> prompt = new LinkedList<>();
            if (!succeeded.isEmpty())
                prompt.add(Launcher.i18n("mods.add.success", String.join(", ", succeeded)));
            if (!failed.isEmpty())
                prompt.add(Launcher.i18n("mods.add.failed", String.join(", ", failed)));
            Controllers.dialog(String.join("\n", prompt), Launcher.i18n("mods.add"));
            loadMods(modManager, versionId);
        })).start();
    }

    public void setParentTab(JFXTabPane parentTab) {
        this.parentTab = parentTab;
    }
}
