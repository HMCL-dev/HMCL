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
package org.jackhuang.hmcl.ui.export;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXCheckBox;
import com.jfoenix.controls.JFXTreeView;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.CheckBoxTreeItem;
import javafx.scene.control.Label;
import javafx.scene.control.TreeItem;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import org.jackhuang.hmcl.game.GameInstanceID;
import org.jackhuang.hmcl.game.HMCLGameRepository;
import org.jackhuang.hmcl.modpack.ModAdviser;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.construct.NoneMultipleSelectionModel;
import org.jackhuang.hmcl.ui.wizard.WizardController;
import org.jackhuang.hmcl.ui.wizard.WizardPage;
import org.jackhuang.hmcl.util.Pair;
import org.jackhuang.hmcl.util.SettingsMap;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.io.FileUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.jackhuang.hmcl.ui.FXUtils.onEscPressed;
import static org.jackhuang.hmcl.util.Lang.mapOf;
import static org.jackhuang.hmcl.util.Pair.pair;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/**
 * @author huangyuhui
 */
public final class ModpackFileSelectionPage extends BorderPane implements WizardPage {
    private final WizardController controller;
    private final GameInstanceID instanceId;
    private final ModAdviser adviser;
    private final ModpackFileTreeItem rootNode;

    public ModpackFileSelectionPage(WizardController controller, HMCLGameRepository repository, GameInstanceID instanceId, ModAdviser adviser) {
        this.controller = controller;
        this.instanceId = instanceId;
        this.adviser = adviser;

        JFXTreeView<String> treeView = new JFXTreeView<>();
        rootNode = getTreeItem(repository.getRunDirectory(instanceId), "minecraft", 0);
        treeView.setRoot(rootNode);
        treeView.setSelectionModel(new NoneMultipleSelectionModel<>());
        onEscPressed(treeView, () -> controller.onPrev(true));
        setMargin(treeView, new Insets(10, 10, 5, 10));
        this.setCenter(treeView);

        HBox nextPane = new HBox();
        nextPane.setPadding(new Insets(16, 16, 16, 0));
        nextPane.setAlignment(Pos.CENTER_RIGHT);
        {
            JFXButton btnNext = FXUtils.newRaisedButton(i18n("wizard.next"));
            btnNext.setPrefSize(100, 40);
            btnNext.setOnAction(e -> onNext());

            nextPane.getChildren().setAll(btnNext);
        }

        this.setBottom(nextPane);
    }

    private ModpackFileTreeItem getTreeItem(Path file, String basePath, int level) {
        if (Files.notExists(file))
            return null;

        boolean isDirectory = Files.isDirectory(file);

        ModAdviser.ModSuggestion state = ModAdviser.ModSuggestion.SUGGESTED;
        if (basePath.length() > "minecraft/".length()) {
            state = adviser.advise(StringUtils.substringAfter(basePath, "minecraft/") + (isDirectory ? "/" : ""), isDirectory);

            String fileName = FileUtils.getName(file);

            if (!isDirectory) {
                switch (fileName) {
                    case ".DS_Store", // macOS system file
                         "desktop.ini", "Thumbs.db" // Windows system files
                            -> state = ModAdviser.ModSuggestion.HIDDEN;
                }
                if (fileName.startsWith("._")) // macOS system file
                    state = ModAdviser.ModSuggestion.HIDDEN;
                if (FileUtils.getNameWithoutExtension(file).equals(instanceId.toString()))
                    state = ModAdviser.ModSuggestion.HIDDEN;
            }

            if (isDirectory) {
                if (fileName.equals(instanceId + "-natives")) { // Ignore <version>-natives
                    state = ModAdviser.ModSuggestion.HIDDEN;
                }
                if (level == 1 && fileName.startsWith("natives-")) { // Ignore natives-os-arch
                    state = ModAdviser.ModSuggestion.HIDDEN;
                }
            }
            if (state == ModAdviser.ModSuggestion.HIDDEN)
                return null;
        }

        ModpackFileTreeItem node = new ModpackFileTreeItem(level == 0 ? instanceId.toString() : StringUtils.substringAfterLast(basePath, '/'), basePath);
        if (state == ModAdviser.ModSuggestion.SUGGESTED)
            node.setSelected(true);

        if (isDirectory) {
            try (var stream = Files.list(file)) {
                stream.map(path -> Pair.pair(path, Files.isDirectory(path))).sorted((p1, p2) -> {
                    if (p1.value().equals(p2.value())) return FileUtils.getName(p1.key()).compareToIgnoreCase(FileUtils.getName(p2.key()));
                    return p1.value() ? -1 : 1;
                }).map(Pair::key).forEach(it -> {
                    ModpackFileTreeItem subNode = getTreeItem(it, basePath + "/" + FileUtils.getName(it), level + 1);
                    if (subNode != null) {
                        node.setSelected(subNode.isSelected() || node.isSelected());
                        if (!subNode.isSelected()) {
                            node.setIndeterminate(true);
                        }
                        node.getChildren().add(subNode);
                    }
                });
            } catch (IOException e) {
                LOG.warning("Failed to list contents of " + file, e);
            }

            if (!node.isSelected()) node.setIndeterminate(false);

            // Empty folder need not to be displayed.
            if (node.getChildren().isEmpty()) {
                return null;
            }
        }

        return node;
    }

    private void getFilesNeeded(ModpackFileTreeItem node, String basePath, List<String> list) {
        if (node == null) return;
        if (node.isSelected() || node.isIndeterminate()) {
            if (basePath.length() > "minecraft/".length())
                list.add(StringUtils.substringAfter(basePath, "minecraft/"));
            for (TreeItem<String> child : node.getChildren()) {
                if (child instanceof ModpackFileTreeItem mChild) {
                    getFilesNeeded(mChild, basePath + "/" + mChild.getFileName(), list);
                }
            }
        }
    }

    @Override
    public void cleanup(SettingsMap settings) {
        controller.getSettings().remove(MODPACK_FILE_SELECTION);
    }

    private void onNext() {
        ArrayList<String> list = new ArrayList<>();
        getFilesNeeded(rootNode, "minecraft", list);
        controller.getSettings().put(MODPACK_FILE_SELECTION, list);
        controller.onFinish();
    }

    @Override
    public String getTitle() {
        return i18n("modpack.wizard.step.2.title");
    }

    public static final SettingsMap.Key<List<String>> MODPACK_FILE_SELECTION = new SettingsMap.Key<>("modpack.accepted");
    private static final Map<String, String> TRANSLATION = mapOf(
            pair("minecraft/hmclversion.cfg", i18n("modpack.files.hmclversion_cfg")),
            pair("minecraft/servers.dat", i18n("modpack.files.servers_dat")),
            pair("minecraft/saves", i18n("modpack.files.saves")),
            pair("minecraft/mods", i18n("modpack.files.mods")),
            pair("minecraft/config", i18n("modpack.files.config")),
            pair("minecraft/liteconfig", i18n("modpack.files.liteconfig")),
            pair("minecraft/resourcepacks", i18n("modpack.files.resourcepacks")),
            pair("minecraft/resources", i18n("modpack.files.resourcepacks")),
            pair("minecraft/options.txt", i18n("modpack.files.options_txt")),
            pair("minecraft/optionsshaders.txt", i18n("modpack.files.optionsshaders_txt")),
            pair("minecraft/mods/VoxelMods", i18n("modpack.files.mods.voxelmods")),
            pair("minecraft/dumps", i18n("modpack.files.dumps")),
            pair("minecraft/blueprints", i18n("modpack.files.blueprints")),
            pair("minecraft/scripts", i18n("modpack.files.scripts"))
    );

    private static final class ModpackFileTreeItem extends CheckBoxTreeItem<String> {

        private final String fileName;

        public ModpackFileTreeItem(String fileName, String basePath) {
            this.fileName = fileName;

            HBox graphic = new HBox();
            JFXCheckBox checkBox = new JFXCheckBox();
            checkBox.selectedProperty().bindBidirectional(this.selectedProperty());
            checkBox.indeterminateProperty().bindBidirectional(this.indeterminateProperty());
            graphic.getChildren().add(checkBox);

            {
                Label text = new Label(fileName);
                text.setMouseTransparent(true);
                graphic.getChildren().add(text);
            }
            if (TRANSLATION.containsKey(basePath)) {
                Label comment = new Label(TRANSLATION.get(basePath));
                comment.setStyle("-fx-text-fill: -monet-on-surface-variant;");
                comment.setMouseTransparent(true);
                graphic.getChildren().add(comment);
            }
            graphic.setPickOnBounds(false);
            this.setExpanded("minecraft".equals(basePath));
            this.setValue(""); // To disable the default display of text
            this.setGraphic(graphic);
        }

        public String getFileName() {
            return fileName;
        }

    }
}
