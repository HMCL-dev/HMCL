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
package org.jackhuang.hmcl.ui.construct;

import com.jfoenix.controls.JFXButton;
import javafx.beans.property.Property;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.SVG;

import java.io.File;

public class FileItem extends BorderPane {
    private Property<String> property;
    private final Label x = new Label();

    private final SimpleStringProperty name = new SimpleStringProperty(this, "name");
    private final SimpleStringProperty title = new SimpleStringProperty(this, "title");
    private final SimpleStringProperty tooltip = new SimpleStringProperty(this, "tooltip");

    public FileItem() {
        VBox left = new VBox();
        Label name = new Label();
        name.textProperty().bind(nameProperty());
        x.getStyleClass().addAll("subtitle-label");
        left.getChildren().addAll(name, x);
        setLeft(left);

        JFXButton right = new JFXButton();
        right.setGraphic(SVG.pencil("black", 15, 15));
        right.getStyleClass().add("toggle-icon4");
        right.setOnMouseClicked(e -> onExplore());
        setRight(right);

        Tooltip tip = new Tooltip();
        tip.textProperty().bind(tooltipProperty());
        Tooltip.install(this, tip);
    }

    public void onExplore() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.titleProperty().bind(titleProperty());
        File selectedDir = chooser.showDialog(Controllers.getStage());
        if (selectedDir != null)
            property.setValue(selectedDir.getAbsolutePath());
        chooser.titleProperty().unbind();
    }

    public void setProperty(Property<String> property) {
        this.property = property;
        x.textProperty().bind(property);
    }

    public String getName() {
        return name.get();
    }

    public StringProperty nameProperty() {
        return name;
    }

    public void setName(String name) {
        this.name.set(name);
    }

    public String getTitle() {
        return title.get();
    }

    public StringProperty titleProperty() {
        return title;
    }

    public void setTitle(String title) {
        this.title.set(title);
    }

    public String getTooltip() {
        return tooltip.get();
    }

    public StringProperty tooltipProperty() {
        return tooltip;
    }

    public void setTooltip(String tooltip) {
        this.tooltip.set(tooltip);
    }
}
