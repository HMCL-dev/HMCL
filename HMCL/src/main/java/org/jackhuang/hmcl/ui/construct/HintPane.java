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

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import org.jackhuang.hmcl.setting.Theme;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.util.javafx.BindingMapping;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class HintPane extends VBox {
    private final Text label = new Text();
    private final StringProperty text = new SimpleStringProperty(this, "text");

    public HintPane() {
        this(MessageDialogPane.MessageType.INFORMATION);
    }

    public HintPane(MessageDialogPane.MessageType type) {
        setFillWidth(true);
        getStyleClass().add("hint");
        HBox hbox = new HBox();
        hbox.setAlignment(Pos.CENTER_LEFT);
        hbox.getChildren().setAll(
                SVG.informationOutline(Theme.blackFillBinding(), 16, 16),
                new Label(i18n("message.info"))
        );
        TextFlow flow = new TextFlow();
        flow.getChildren().setAll(label);
        getChildren().setAll(hbox, flow);
        label.textProperty().bind(text);
        VBox.setMargin(flow, new Insets(2, 2, 0, 2));

        label.fillProperty().bind(BindingMapping.of(disabledProperty()).map(disabled -> disabled ? new Color(0, 0, 0, 0.5) : Color.BLACK));
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
}
