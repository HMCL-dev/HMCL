/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.ui.construct;

import com.jfoenix.controls.JFXToggleButton;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import org.jackhuang.hmcl.ui.FXUtils;

public class OptionToggleButton extends StackPane {
    private final StringProperty title = new SimpleStringProperty();
    private final BooleanProperty selected = new SimpleBooleanProperty();

    public OptionToggleButton() {
        getProperties().put("ComponentList.noPadding", true);

        BorderPane pane = new BorderPane();
        pane.setPadding(new Insets(8, 8, 8, 16));
        RipplerContainer container = new RipplerContainer(pane);
        getChildren().setAll(container);

        Label label = new Label();
        label.setMouseTransparent(true);
        label.textProperty().bind(title);
        pane.setLeft(label);
        BorderPane.setAlignment(label, Pos.CENTER_LEFT);

        JFXToggleButton toggleButton = new JFXToggleButton();
        pane.setRight(toggleButton);
        toggleButton.selectedProperty().bindBidirectional(selected);
        toggleButton.setSize(8);
        FXUtils.setLimitHeight(toggleButton, 30);

        container.setOnMouseClicked(e -> {
            toggleButton.setSelected(!toggleButton.isSelected());
        });
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

    public boolean isSelected() {
        return selected.get();
    }

    public BooleanProperty selectedProperty() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected.set(selected);
    }
}
