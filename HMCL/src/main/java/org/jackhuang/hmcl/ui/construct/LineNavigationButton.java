/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026 huangyuhui <huanghongxun2008@126.com> and contributors
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

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ObjectPropertyBase;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;

/// @author Glavo
public final class LineNavigationButton extends LineButtonBase {
    private static final String DEFAULT_STYLE_CLASS = "line-navigation-button";

    public LineNavigationButton() {
        getStyleClass().add(DEFAULT_STYLE_CLASS);

        root.setMouseTransparent(true);

        HBox right = new HBox();
        root.setRight(right);
        {
            right.setAlignment(Pos.CENTER_RIGHT);

            Label valueLabel = new Label();
            valueLabel.getStyleClass().add("subtitle");
            valueLabel.textProperty().bind(messageProperty());

            Node arrowIcon = SVG.ARROW_FORWARD.createIcon(24);
            HBox.setMargin(arrowIcon, new Insets(0, 8, 0, 8));

            disabledProperty().addListener((observable, oldValue, newValue) ->
                    arrowIcon.setOpacity(newValue ? 0.4 : 1.0));

            right.getChildren().setAll(valueLabel, arrowIcon);
        }

        FXUtils.onClicked(container, this::fire);
    }

    public void fire() {
        fireEvent(new ActionEvent());
    }

    private final ObjectProperty<EventHandler<ActionEvent>> onAction = new ObjectPropertyBase<>() {

        @Override
        protected void invalidated() {
            setEventHandler(ActionEvent.ACTION, get());
        }

        @Override
        public Object getBean() {
            return LineNavigationButton.this;
        }

        @Override
        public String getName() {
            return "onAction";
        }
    };

    public ObjectProperty<EventHandler<ActionEvent>> onActionProperty() {
        return onAction;
    }

    public EventHandler<ActionEvent> getOnAction() {
        return onActionProperty().get();
    }

    public void setOnAction(EventHandler<ActionEvent> value) {
        onActionProperty().set(value);
    }

    private final StringProperty message = new SimpleStringProperty(this, "message", "");

    public StringProperty messageProperty() {
        return message;
    }

    public String getMessage() {
        return messageProperty().get();
    }

    public void setMessage(String message) {
        messageProperty().set(message);
    }

}
