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
package org.jackhuang.hmcl.ui.export;

import com.jfoenix.controls.JFXTreeView;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.CheckBoxTreeItem;
import javafx.scene.control.Label;
import javafx.scene.control.TreeItem;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import org.jackhuang.hmcl.Launcher;
import org.jackhuang.hmcl.game.ModAdviser;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.construct.NoneMultipleSelectionModel;
import org.jackhuang.hmcl.ui.wizard.WizardController;
import org.jackhuang.hmcl.ui.wizard.WizardPage;
import org.jackhuang.hmcl.util.FileUtils;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.Pair;
import org.jackhuang.hmcl.util.StringUtils;

import java.io.File;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * @author huangyuhui
 */
public final class ModpackFileSelectionPage extends StackPane implements WizardPage {
    private final WizardController controller;
    private final String version;
    private final ModAdviser adviser;
    @FXML
    private JFXTreeView<String> treeView;
    private final CheckBoxTreeItem<String> rootNode;

    public ModpackFileSelectionPage(WizardController controller, Profile profile, String version, ModAdviser adviser) {
        this.controller = controller;
        this.version = version;
        this.adviser = adviser;

        FXUtils.loadFXML(this, "/assets/fxml/modpack/selection.fxml");
        rootNode = getTreeItem(profile.getRepository().getRunDirectory(version), "minecraft");
        treeView.setRoot(rootNode);
        treeView.setSelectionModel(new NoneMultipleSelectionModel<>());
    }

    private CheckBoxTreeItem<String> getTreeItem(File file, String basePath) {
        if (!file.exists())
            return null;

        ModAdviser.ModSuggestion state = ModAdviser.ModSuggestion.SUGGESTED;
        if (basePath.length() > "minecraft/".length()) {
            state = adviser.advise(StringUtils.substringAfter(basePath, "minecraft/") + (file.isDirectory() ? "/" : ""), file.isDirectory());
            if (file.isFile() && Objects.equals(FileUtils.getNameWithoutExtension(file), version))
                state = ModAdviser.ModSuggestion.HIDDEN;
            if (file.isDirectory() && Objects.equals(file.getName(), version + "-natives"))
                state = ModAdviser.ModSuggestion.HIDDEN;
            if (state == ModAdviser.ModSuggestion.HIDDEN)
                return null;
        }

        CheckBoxTreeItem<String> node = new CheckBoxTreeItem<>(StringUtils.substringAfterLast(basePath, "/"));
        if (state == ModAdviser.ModSuggestion.SUGGESTED)
            node.setSelected(true);

        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null)
                for (File it : files) {
                    CheckBoxTreeItem<String> subNode = getTreeItem(it, basePath + "/" + it.getName());
                if (subNode != null) {
                    node.setSelected(subNode.isSelected() || node.isSelected());
                    if (!subNode.isSelected())
                        node.setIndeterminate(true);
                    node.getChildren().add(subNode);
                }
            }
            if (!node.isSelected()) node.setIndeterminate(false);

            // Empty folder need not to be displayed.
            if (node.getChildren().isEmpty())
                return null;
        }

        HBox graphic = new HBox();
        CheckBox checkBox = new CheckBox();
        checkBox.selectedProperty().bindBidirectional(node.selectedProperty());
        checkBox.indeterminateProperty().bindBidirectional(node.indeterminateProperty());
        graphic.getChildren().add(checkBox);

        if (TRANSLATION.containsKey(basePath)) {
            Label comment = new Label();
            comment.setText(TRANSLATION.get(basePath));
            comment.setStyle("-fx-text-fill: gray;");
            comment.setMouseTransparent(true);
            graphic.getChildren().add(comment);
        }
        graphic.setPickOnBounds(false);
        node.setExpanded("minecraft".equals(basePath));
        node.setGraphic(graphic);

        return node;
    }

    private void getFilesNeeded(CheckBoxTreeItem<String> node, String basePath, List<String> list) {
        if (node == null) return;
        if (node.isSelected()) {
            if (basePath.length() > "minecraft/".length())
                list.add(StringUtils.substringAfter(basePath, "minecraft/"));
            for (TreeItem<String> child : node.getChildren()) {
                if (child instanceof CheckBoxTreeItem)
                    getFilesNeeded(((CheckBoxTreeItem<String>) child), basePath + "/" + child.getValue(), list);
            }
        }
    }

    @Override
    public void cleanup(Map<String, Object> settings) {
        controller.getSettings().remove(MODPACK_FILE_SELECTION);
    }

    @FXML
    private void onNext() {
        LinkedList<String> list = new LinkedList<>();
        getFilesNeeded(rootNode, "minecraft", list);
        controller.getSettings().put(MODPACK_FILE_SELECTION, list);
        controller.onFinish();
    }

    @Override
    public String getTitle() {
        return Launcher.i18n("modpack.wizard.step.2.title");
    }

    public static final String MODPACK_FILE_SELECTION = "modpack.accepted";
    private static final Map<String, String> TRANSLATION = Lang.mapOf(
            new Pair<>("minecraft/servers.dat", Launcher.i18n("modpack.files.servers_dat")),
            new Pair<>("minecraft/saves", Launcher.i18n("modpack.files.saves")),
            new Pair<>("minecraft/mods", Launcher.i18n("modpack.files.mods")),
            new Pair<>("minecraft/config", Launcher.i18n("modpack.files.config")),
            new Pair<>("minecraft/liteconfig", Launcher.i18n("modpack.files.liteconfig")),
            new Pair<>("minecraft/resourcepacks", Launcher.i18n("modpack.files.resourcepacks")),
            new Pair<>("minecraft/resources", Launcher.i18n("modpack.files.resourcepacks")),
            new Pair<>("minecraft/options.txt", Launcher.i18n("modpack.files.options_txt")),
            new Pair<>("minecraft/optionsshaders.txt", Launcher.i18n("modpack.files.optionsshaders_txt")),
            new Pair<>("minecraft/mods/VoxelMods", Launcher.i18n("modpack.files.mods.voxelmods")),
            new Pair<>("minecraft/dumps", Launcher.i18n("modpack.files.dumps")),
            new Pair<>("minecraft/blueprints", Launcher.i18n("modpack.files.blueprints")),
            new Pair<>("minecraft/scripts", Launcher.i18n("modpack.files.scripts"))
    );
}
