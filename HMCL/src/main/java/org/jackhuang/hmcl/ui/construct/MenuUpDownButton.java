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

import com.jfoenix.controls.JFXButton;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.Skin;
import javafx.scene.control.SkinBase;
import javafx.scene.layout.HBox;
import org.jackhuang.hmcl.setting.Theme;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;


public class MenuUpDownButton extends Control {

    private final BooleanProperty selected = new SimpleBooleanProperty(this, "selected");
    private final StringProperty text = new SimpleStringProperty(this, "text");

    public MenuUpDownButton() {
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new MenuUpDownButtonSkin(this);
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

    public String getText() {
        return text.get();
    }

    public StringProperty textProperty() {
        return text;
    }

    public void setText(String text) {
        this.text.set(text);
    }

    private static class MenuUpDownButtonSkin extends SkinBase<MenuUpDownButton> {

        protected MenuUpDownButtonSkin(MenuUpDownButton control) {
            super(control);

            HBox content = new HBox(8);
            content.setAlignment(Pos.CENTER);
            Label label = new Label();
            label.setStyle("-fx-text-fill: black;");
            label.textProperty().bind(control.text);

            Node up = SVG.menuUp(Theme.blackFillBinding(), 16, 16);
            Node down = SVG.menuDown(Theme.blackFillBinding(), 16, 16);

            JFXButton button = new JFXButton();
            button.setGraphic(content);
            button.setOnAction(e -> {
                control.selected.set(!control.isSelected());
            });

            FXUtils.onChangeAndOperate(control.selected, selected -> {
                if (selected) {
                    content.getChildren().setAll(label, up);
                } else {
                    content.getChildren().setAll(label, down);
                }
            });

            getChildren().setAll(button);
        }
    }
}
