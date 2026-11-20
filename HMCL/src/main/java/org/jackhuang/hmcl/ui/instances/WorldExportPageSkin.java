// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.instances;

import javafx.beans.binding.Bindings;
import javafx.geometry.Pos;
import javafx.scene.control.SkinBase;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.glavo.m3fx.controls.M3Button;
import org.glavo.m3fx.controls.M3ButtonVariant;
import org.glavo.m3fx.controls.M3FormRow;
import org.glavo.m3fx.controls.M3ListSectionHeader;
import org.glavo.m3fx.controls.M3SettingItem;
import org.glavo.m3fx.controls.M3TextField;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.util.i18n.I18n;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// Presents the destination, world name, and version for a world export.
public class WorldExportPageSkin extends SkinBase<WorldExportPage> {

    /// Binds the export action and form rows to the wizard state.
    public WorldExportPageSkin(WorldExportPage skinnable) {
        super(skinnable);

        VBox container = new VBox();
        container.setSpacing(16);
        container.setAlignment(Pos.CENTER);
        FXUtils.setLimitWidth(container, 500);

        var list = FXUtils.newSegmentedList();
        var chooseFileButton = new M3SettingItem(i18n("world.export.location"));
        chooseFileButton.setTrailing(SVG.EDIT.createIcon(20));
        chooseFileButton.trailingSupportingTextProperty().bind(skinnable.pathProperty());
        chooseFileButton.setOnAction(event -> {
            var chooser = new FileChooser();
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(i18n("world"), "*.zip"));
            chooser.setInitialDirectory(FXUtils.chooserDirectory(skinnable.pathProperty().get()));
            @Nullable File file = chooser.showSaveDialog(chooseFileButton.getScene().getWindow());
            if (file != null) skinnable.pathProperty().set(file.toPath().normalize().toString());
        });

        M3TextField txtWorldName = FXUtils.newTextField();
        txtWorldName.textProperty().bindBidirectional(skinnable.worldNameProperty());
        txtWorldName.setPrefWidth(300);
        var worldNamePane = new M3FormRow(i18n("world.name"), txtWorldName);

        var version = FXUtils.newCopyableLabel(null);
        version.textProperty().bind(skinnable.gameVersionProperty());
        var gameVersionPane = new M3FormRow(i18n("world.game_version"), version);

        list.getItems().setAll(chooseFileButton, worldNamePane, gameVersionPane);

        container.getChildren().setAll(
                new M3ListSectionHeader(i18n("world.export")),
                list
        );

        M3Button btnExport = new M3Button(I18n.i18n("button.export"), M3ButtonVariant.FILLED);
        btnExport.disableProperty().bind(Bindings.createBooleanBinding(() -> txtWorldName.getText().isEmpty() || Files.exists(Paths.get(skinnable.pathProperty().get())),
                txtWorldName.textProperty().isEmpty(), skinnable.pathProperty()));
        btnExport.setOnAction(e -> skinnable.export());
        HBox bottom = new HBox();
        bottom.setAlignment(Pos.CENTER_RIGHT);
        bottom.getChildren().setAll(btnExport);
        container.getChildren().add(bottom);

        getChildren().setAll(container);
    }

}
