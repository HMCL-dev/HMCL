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
package org.jackhuang.hmcl.ui.versions;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXTextField;
import javafx.beans.binding.Bindings;
import javafx.geometry.Pos;
import javafx.scene.control.SkinBase;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.construct.ComponentList;
import org.jackhuang.hmcl.ui.construct.LineFileChooserButton;
import org.jackhuang.hmcl.ui.construct.LinePane;
import org.jackhuang.hmcl.ui.construct.LineTextPane;

import java.nio.file.Files;
import java.nio.file.Paths;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class WorldExportPageSkin extends SkinBase<WorldExportPage> {

    public WorldExportPageSkin(WorldExportPage skinnable) {
        super(skinnable);

        VBox container = new VBox();
        container.setSpacing(16);
        container.setAlignment(Pos.CENTER);
        FXUtils.setLimitWidth(container, 500);

        ComponentList list = new ComponentList();

        var chooseFileButton = new LineFileChooserButton();
        chooseFileButton.setTitle(i18n("world.export.location"));
        chooseFileButton.setType(LineFileChooserButton.Type.SAVE_FILE);
        chooseFileButton.getExtensionFilters().add(new FileChooser.ExtensionFilter(i18n("world"), "*.zip"));
        chooseFileButton.locationProperty().bindBidirectional(skinnable.pathProperty());

        var worldNamePane = new LinePane();
        worldNamePane.setTitle(i18n("world.name"));
        JFXTextField txtWorldName = new JFXTextField();
        txtWorldName.textProperty().bindBidirectional(skinnable.worldNameProperty());
        txtWorldName.setPrefWidth(300);
        worldNamePane.setRight(txtWorldName);

        LineTextPane gameVersionPane = new LineTextPane();
        gameVersionPane.setTitle(i18n("world.game_version"));
        gameVersionPane.textProperty().bind(skinnable.gameVersionProperty());

        list.getContent().setAll(chooseFileButton, worldNamePane, gameVersionPane);

        container.getChildren().setAll(
                ComponentList.createComponentListTitle(i18n("world.export")),
                list
        );

        JFXButton btnExport = FXUtils.newRaisedButton(i18n("button.export"));
        btnExport.disableProperty().bind(Bindings.createBooleanBinding(() -> txtWorldName.getText().isEmpty() || Files.exists(Paths.get(chooseFileButton.getLocation())),
                txtWorldName.textProperty().isEmpty(), chooseFileButton.locationProperty()));
        btnExport.setOnAction(e -> skinnable.export());
        HBox bottom = new HBox();
        bottom.setAlignment(Pos.CENTER_RIGHT);
        bottom.getChildren().setAll(btnExport);
        container.getChildren().add(bottom);

        getChildren().setAll(container);
    }

}
