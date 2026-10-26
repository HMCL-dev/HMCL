// SPDX-FileCopyrightText: Copyright (C) 2021 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
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
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;

public class MenuUpDownButton extends Control {

    private final BooleanProperty selected = new SimpleBooleanProperty(this, "selected");
    private final StringProperty text = new SimpleStringProperty(this, "text");

    public MenuUpDownButton() {
        this.getStyleClass().add("menu-up-down-button");
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
            label.textProperty().bind(control.text);

            Node up = SVG.ARROW_DROP_UP.createIcon(16);
            Node down = SVG.ARROW_DROP_DOWN.createIcon(16);

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
