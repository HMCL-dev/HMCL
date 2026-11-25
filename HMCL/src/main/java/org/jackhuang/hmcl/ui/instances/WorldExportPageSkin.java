// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.instances;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXTextField;
import javafx.beans.binding.Bindings;
import javafx.geometry.Pos;
import javafx.scene.control.SkinBase;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.PathChooser;
import org.jackhuang.hmcl.ui.construct.ComponentList;
import org.jackhuang.hmcl.ui.construct.FileSelector;
import org.jackhuang.hmcl.ui.construct.LinePane;
import org.jackhuang.hmcl.ui.construct.LineTextPane;

import java.nio.file.Files;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class WorldExportPageSkin extends SkinBase<WorldExportPage> {

    public WorldExportPageSkin(WorldExportPage skinnable) {
        super(skinnable);

        VBox container = new VBox();
        container.setSpacing(16);
        container.setAlignment(Pos.CENTER);
        FXUtils.setLimitWidth(container, 500);

        ComponentList list = new ComponentList();

        LinePane exportPathSelectPane = new LinePane();
        exportPathSelectPane.setTitle(i18n("world.export.location"));
        var exportPath = new FileSelector(FileSelector.SelectionMode.SAVE_FILE);
        exportPath.getChooser().setChooserContext(PathChooser.ChooserContext.EXPORT)
                .setExtensionFilters(new FileChooser.ExtensionFilter(i18n("world"), "*.zip"));
        exportPath.setPrefWidth(400);
        exportPathSelectPane.setRight(exportPath);
        exportPath.setText(skinnable.pathProperty().getValue().toString());
        skinnable.pathProperty().bind(exportPath.pathProperty());

        var worldNamePane = new LinePane();
        worldNamePane.setTitle(i18n("world.name"));
        JFXTextField txtWorldName = new JFXTextField();
        txtWorldName.textProperty().bindBidirectional(skinnable.worldNameProperty());
        txtWorldName.setPrefWidth(300);
        worldNamePane.setRight(txtWorldName);

        LineTextPane gameVersionPane = new LineTextPane();
        gameVersionPane.setTitle(i18n("world.game_version"));
        gameVersionPane.textProperty().bind(skinnable.gameVersionProperty());

        list.getContent().setAll(exportPathSelectPane, worldNamePane, gameVersionPane);

        container.getChildren().setAll(
                ComponentList.createComponentListTitle(i18n("world.export")),
                list
        );

        JFXButton btnExport = FXUtils.newRaisedButton(i18n("button.export"));
        btnExport.disableProperty().bind(Bindings.createBooleanBinding(() ->
                        txtWorldName.getText().isEmpty()
                                || exportPath.getPath() == null
                                || Files.exists(exportPath.getPath())
                                || (exportPath.getPath().getParent() != null && !Files.isDirectory(exportPath.getPath().getParent()))
                                || exportPath.getPath().startsWith(skinnable.getWorld().getFile()),
                txtWorldName.textProperty().isEmpty(), exportPath.pathProperty()));
        btnExport.setOnAction(e -> skinnable.export());
        HBox bottom = new HBox();
        bottom.setAlignment(Pos.CENTER_RIGHT);
        bottom.getChildren().setAll(btnExport);
        container.getChildren().add(bottom);

        getChildren().setAll(container);
    }

}
