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

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;

/// @author Glavo
public abstract class LineButtonBase extends StackPane implements LineComponent {

    protected final BorderPane root;
    protected final RipplerContainer container;

    private final Label titleLabel;

    public LineButtonBase() {
        this.root = new BorderPane();
        root.setPadding(LineComponent.PADDING);
        root.setMinHeight(LineComponent.MIN_HEIGHT);

        this.container = new RipplerContainer(root);
        this.getChildren().setAll(container);

        this.titleLabel = new Label();
        root.setCenter(titleLabel);
        BorderPane.setAlignment(titleLabel, Pos.CENTER_LEFT);
        titleLabel.textProperty().bind(titleProperty());
        titleLabel.getStyleClass().add("title");
    }

    @Override
    public BorderPane getRoot() {
        return root;
    }

    private final StringProperty title = new SimpleStringProperty(this, "title");

    @Override
    public StringProperty titleProperty() {
        return title;
    }

    @Override
    public String getTitle() {
        return titleProperty().get();
    }

    @Override
    public void setTitle(String title) {
        this.titleProperty().set(title);
    }

    private StringProperty subtitle;

    @Override
    public StringProperty subtitleProperty() {
        if (subtitle == null) {
            subtitle = new LineComponent.SubtitleProperty() {
                @Override
                public LineButtonBase getBean() {
                    return LineButtonBase.this;
                }

                @Override
                public Label getTitleLabel() {
                    return titleLabel;
                }
            };
        }

        return subtitle;
    }

    @Override
    public String getSubtitle() {
        return subtitleProperty().get();
    }

    @Override
    public void setSubtitle(String subtitle) {
        subtitleProperty().set(subtitle);
    }
}
