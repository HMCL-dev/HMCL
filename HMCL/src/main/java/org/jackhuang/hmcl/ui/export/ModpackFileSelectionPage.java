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
import com.jfoenix.controls.JFXCheckTreeCell;
import com.jfoenix.controls.JFXTreeView;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.CheckBoxTreeItem;
import javafx.scene.control.Label;
import javafx.scene.control.TreeItem;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import org.jackhuang.hmcl.game.HMCLGameInstance;
import org.jackhuang.hmcl.modpack.ModAdviser;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.construct.NoneMultipleSelectionModel;
import org.jackhuang.hmcl.ui.construct.SpinnerPane;
import org.jackhuang.hmcl.ui.wizard.WizardController;
import org.jackhuang.hmcl.ui.wizard.WizardPage;
import org.jackhuang.hmcl.util.Pair;
import org.jackhuang.hmcl.util.SettingsMap;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

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
    private final HMCLGameInstance gameInstance;
    private final ModAdviser adviser;
    private @Nullable ModpackFileTreeItem rootNode;

    /// Creates a file-selection page and starts loading the instance's exportable files.
    public ModpackFileSelectionPage(WizardController controller, HMCLGameInstance gameInstance, ModAdviser adviser) {
        this.controller = controller;
        this.gameInstance = gameInstance;
        this.adviser = adviser;

        JFXTreeView<String> treeView = new JFXTreeView<>();
        treeView.setCellFactory(__ -> new ModpackFileTreeCell());
        treeView.setSelectionModel(new NoneMultipleSelectionModel<>());
        onEscPressed(treeView, () -> controller.onPrev(true));

        Label placeholder = new Label(i18n("modpack.files.empty"));
        StackPane placeholderPane = new StackPane(placeholder);
        placeholderPane.getStyleClass().add("notice-pane");
        placeholderPane.setVisible(false);

        SpinnerPane spinnerPane = new SpinnerPane();
        StackPane center = new StackPane(treeView, placeholderPane);
        spinnerPane.setContent(center);

        setMargin(spinnerPane, new Insets(10, 10, 5, 10));
        this.setCenter(spinnerPane);


        HBox nextPane = new HBox();
        nextPane.setPadding(new Insets(16, 16, 16, 0));
        nextPane.setAlignment(Pos.CENTER_RIGHT);

        JFXButton btnNext = FXUtils.newRaisedButton(i18n("wizard.next"));
        btnNext.setPrefSize(100, 40);
        btnNext.setOnAction(e -> onNext());
        nextPane.getChildren().setAll(btnNext);

        loadRoot(treeView, placeholderPane, spinnerPane, btnNext);
        spinnerPane.setOnFailedAction((__) -> loadRoot(treeView, placeholderPane, spinnerPane, btnNext));

        this.setBottom(nextPane);
    }

    private void loadRoot(JFXTreeView<String> treeView, StackPane placeholderPane, SpinnerPane spinnerPane, JFXButton btnNext) {
        spinnerPane.setLoading(true);
        btnNext.setDisable(true);
        CompletableFuture
                .supplyAsync(() -> getTreeItem(gameInstance.getRunDirectory(), "minecraft", 0), Schedulers.io())
                .whenCompleteAsync((root, throwable) -> {
                    if (throwable == null) {
                        if (root != null) {
                            treeView.setRoot(rootNode = root);
                        } else {
                            placeholderPane.setVisible(true);
                        }
                        spinnerPane.setFailedReason(null);
                        btnNext.setDisable(false);
                    } else {
                        LOG.warning("Failed to load modpack file tree", throwable);
                        spinnerPane.setFailedReason(i18n("modpack.files.load_failed"));
                    }
                    spinnerPane.setLoading(false);
                }, Schedulers.javafx());
    }

    private ModpackFileTreeItem getTreeItem(Path file, String basePath, int level) {
        if (Files.notExists(file))
            return null;

        boolean isDirectory = Files.isDirectory(file);

        ModAdviser.ModSuggestion state = ModAdviser.ModSuggestion.SUGGESTED;
        if (level > 0) {
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
                if (FileUtils.getNameWithoutExtension(file).equals(gameInstance.getId().toString()))
                    state = ModAdviser.ModSuggestion.HIDDEN;
            }

            if (isDirectory) {
                if (fileName.equals(gameInstance.getId() + "-natives")) { // Ignore <version>-natives
                    state = ModAdviser.ModSuggestion.HIDDEN;
                }
                if (level == 1 && fileName.startsWith("natives-")) { // Ignore natives-os-arch
                    state = ModAdviser.ModSuggestion.HIDDEN;
                }
            }
            if (state == ModAdviser.ModSuggestion.HIDDEN)
                return null;
        }

        ModpackFileTreeItem node = new ModpackFileTreeItem(level == 0 ? gameInstance.getId().toString() : StringUtils.substringAfterLast(basePath, '/'), basePath);
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

    /// Appends selected paths relative to `minecraft/`, traversing partially selected directories.
    /// A `null` node contributes no paths.
    private void getFilesNeeded(@Nullable ModpackFileTreeItem node, String basePath, List<String> list) {
        if (node == null) return;
        if (node.isSelected() || node.isIndeterminate()) {
            if (basePath.length() > "minecraft/".length())
                list.add(StringUtils.substringAfter(basePath, "minecraft/"));
            for (TreeItem<String> child : node.getChildren()) {
                if (child instanceof ModpackFileTreeItem mChild) {
                    getFilesNeeded(mChild, basePath + "/" + mChild.getValue(), list);
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

    /// Stores a file name, optional explanation, and hierarchical selection state.
    @NotNullByDefault
    private static final class ModpackFileTreeItem extends CheckBoxTreeItem<String> {

        /// The localized explanation for a recognized path, or `null` if none exists.
        private final @Nullable String comment;

        /// Creates an item whose value is the file name; only the `minecraft` root starts expanded.
        public ModpackFileTreeItem(String fileName, String basePath) {
            super(fileName);
            this.comment = TRANSLATION.get(basePath);
            this.setExpanded("minecraft".equals(basePath));
        }
    }

    /// Renders file names and localized explanations alongside the bound checkbox.
    @NotNullByDefault
    private static final class ModpackFileTreeCell extends JFXCheckTreeCell<String> {

        /// Contains the inherited checkbox graphic followed by the file labels.
        private final HBox content = new HBox();

        /// Displays the current file name.
        private final Label fileName = new Label();

        /// Displays the current path's optional explanation.
        private final Label comment = new Label();

        /// Creates reusable labels whose mouse events pass through to the cell.
        private ModpackFileTreeCell() {
            fileName.setMouseTransparent(true);
            comment.setStyle("-fx-text-fill: -monet-on-surface-variant;");
            comment.setMouseTransparent(true);
            content.setAlignment(Pos.CENTER_LEFT);
            content.setPickOnBounds(false);
        }

        /// Refreshes the file labels and removes content when the cell is cleared.
        @Override
        protected void updateDisplay(@Nullable String item, boolean empty) {
            content.getChildren().clear();
            super.updateDisplay(item, empty);

            fileName.setText(null);
            comment.setText(null);
            if (empty || item == null) {
                return;
            }

            // Updating the text makes the skin reattach the current graphic to the cell.
            // Clear it before moving the checkbox graphic into the content container.
            setText(null);
            @Nullable Node graphic = getGraphic();
            if (graphic != null) {
                content.getChildren().add(graphic);
            }
            fileName.setText(item);
            content.getChildren().add(fileName);
            if (getTreeItem() instanceof ModpackFileTreeItem treeItem && treeItem.comment != null) {
                comment.setText(treeItem.comment);
                content.getChildren().add(comment);
            }
            setGraphic(content);
        }
    }
}
