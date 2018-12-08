/**
 * Hello Minecraft! Launcher
 * Copyright (C) 2018  huangyuhui <huanghongxun2008@126.com> and contributors
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
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.SkinBase;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.construct.ComponentList;
import org.jackhuang.hmcl.ui.construct.FileItem;

import java.nio.file.Files;
import java.nio.file.Paths;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class WorldExportPageSkin extends SkinBase<WorldExportPage> {

    public WorldExportPageSkin(WorldExportPage skinnable) {
        super(skinnable);

        Insets insets = new Insets(0, 0, 12, 0);
        VBox container = new VBox();
        container.setSpacing(16);
        container.setAlignment(Pos.CENTER);
        FXUtils.setLimitWidth(container, 500);
        {
            HBox labelContainer = new HBox();
            labelContainer.setPadding(new Insets(0, 0, 0, 5));
            Label label = new Label(i18n("world.export"));
            labelContainer.getChildren().setAll(label);
            container.getChildren().add(labelContainer);
        }

        ComponentList list = new ComponentList();

        FileItem fileItem = new FileItem();
        fileItem.setName(i18n("world.export.location"));
        fileItem.pathProperty().bindBidirectional(skinnable.pathProperty());
        list.getContent().add(fileItem);

        JFXTextField txtWorldName = new JFXTextField();
        txtWorldName.textProperty().bindBidirectional(skinnable.worldNameProperty());
        txtWorldName.setLabelFloat(true);
        txtWorldName.setPromptText(i18n("world.name"));
        StackPane.setMargin(txtWorldName, insets);
        list.getContent().add(txtWorldName);

        Label lblGameVersionTitle = new Label(i18n("world.game_version"));
        Label lblGameVersion = new Label();
        lblGameVersion.textProperty().bind(skinnable.gameVersionProperty());
        BorderPane gameVersionPane = new BorderPane();
        gameVersionPane.setPadding(new Insets(4, 0, 4, 0));
        gameVersionPane.setLeft(lblGameVersionTitle);
        gameVersionPane.setRight(lblGameVersion);
        list.getContent().add(gameVersionPane);

        container.getChildren().add(list);

        JFXButton btnExport = new JFXButton(i18n("button.export"));
        btnExport.disableProperty().bind(Bindings.createBooleanBinding(() -> txtWorldName.getText().isEmpty() || Files.exists(Paths.get(fileItem.getPath())),
                txtWorldName.textProperty().isEmpty(), fileItem.pathProperty()));
        btnExport.setButtonType(JFXButton.ButtonType.RAISED);
        btnExport.getStyleClass().add("jfx-button-raised");
        btnExport.setOnMouseClicked(e -> skinnable.export());
        HBox bottom = new HBox();
        bottom.setAlignment(Pos.CENTER_RIGHT);
        bottom.getChildren().setAll(btnExport);
        container.getChildren().add(bottom);

        getChildren().setAll(container);
    }


}
