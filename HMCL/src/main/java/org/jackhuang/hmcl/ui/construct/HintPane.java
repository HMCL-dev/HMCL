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
import javafx.scene.Node;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import org.jackhuang.hmcl.setting.Theme;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;

import java.util.Locale;

public class HintPane extends VBox {
    private final Text label = new Text();
    private final StringProperty text = new SimpleStringProperty(this, "text");
    private final TextFlow flow = new TextFlow();

    public HintPane() {
        this(MessageDialogPane.MessageType.INFO);
    }

    public HintPane(MessageDialogPane.MessageType type) {
        setFillWidth(true);
        getStyleClass().addAll("hint", type.name().toLowerCase(Locale.ROOT));

        SVG svg;
        switch (type) {
            case INFO:
                svg = SVG.INFORMATION_OUTLINE;
                break;
            case ERROR:
                svg = SVG.CLOSE_CIRCLE_OUTLINE;
                break;
            case SUCCESS:
                svg = SVG.CHECK_CIRCLE_OUTLINE;
                break;
            case WARNING:
                svg = SVG.ALERT_OUTLINE;
                break;
            case QUESTION:
                svg = SVG.HELP_CIRCLE_OUTLINE;
                break;
            default:
                throw new IllegalArgumentException("Unrecognized message box message type " + type);
        }

        HBox hbox = new HBox(svg.createIcon(Theme.blackFill(), 16, 16), new Text(type.getDisplayName()));
        hbox.setAlignment(Pos.CENTER_LEFT);
        flow.getChildren().setAll(label);
        getChildren().setAll(hbox, flow);
        label.textProperty().bind(text);
        VBox.setMargin(flow, new Insets(2, 2, 0, 2));
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

    public void setSegment(String segment) {
        flow.getChildren().setAll(FXUtils.parseSegment(segment, Controllers::onHyperlinkAction));
    }

    public void setChildren(Node... children) {
        flow.getChildren().setAll(children);
    }
}
