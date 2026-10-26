// SPDX-FileCopyrightText: Copyright (C) 2021 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
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
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;

import java.util.Locale;
import java.util.function.Consumer;

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

        HBox hbox = new HBox(type.getIcon().createIcon(16), new Text(type.getDisplayName()));
        hbox.setAlignment(Pos.CENTER_LEFT);
        hbox.setSpacing(2);
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
        this.setSegment(segment, Controllers::onHyperlinkAction);
    }

    public void setSegment(String segment, Consumer<String> hyperlinkAction) {
        flow.getChildren().setAll(FXUtils.parseSegment(segment, hyperlinkAction));
    }

    public void setChildren(Node... children) {
        flow.getChildren().setAll(children);
    }
}
