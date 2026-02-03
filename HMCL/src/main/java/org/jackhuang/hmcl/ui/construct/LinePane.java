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
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import org.jackhuang.hmcl.ui.SVG;

/// @author Glavo
public class LinePane extends BorderPane implements LineComponent {

    private final Label titleLabel;

    public LinePane() {
        this.setPadding(LineComponent.PADDING);
        this.setMinHeight(LineComponent.MIN_HEIGHT);

        this.titleLabel = new Label();
        this.setCenter(titleLabel);
        BorderPane.setAlignment(titleLabel, Pos.CENTER_LEFT);
        titleLabel.textProperty().bind(titleProperty());
        titleLabel.getStyleClass().add("title");
    }

    @Override
    public BorderPane getRoot() {
        return this;
    }

    private final StringProperty title = new SimpleStringProperty(this, "title");

    public StringProperty titleProperty() {
        return title;
    }

    public String getTitle() {
        return titleProperty().get();
    }

    public void setTitle(String title) {
        this.titleProperty().set(title);
    }

    private StringProperty subtitle;

    public StringProperty subtitleProperty() {
        if (subtitle == null) {
            subtitle = new LineComponent.SubtitleProperty() {
                @Override
                public LinePane getBean() {
                    return LinePane.this;
                }

                @Override
                public Label getTitleLabel() {
                    return titleLabel;
                }
            };
        }

        return subtitle;
    }

    public String getSubtitle() {
        return subtitleProperty().get();
    }

    public void setSubtitle(String subtitle) {
        subtitleProperty().set(subtitle);
    }

    private static final Insets ICON_MARGIN = new Insets(0, 16, 0, 0);

    static void setLeftIcon(BorderPane root, Image icon, double size) {
        ImageView imageView = new ImageView(icon);
        if (size > 0) {
            imageView.setFitWidth(size);
            imageView.setFitHeight(size);
            imageView.setPreserveRatio(true);
            imageView.setSmooth(true);
        }
        imageView.setMouseTransparent(true);
        BorderPane.setAlignment(imageView, Pos.CENTER);
        BorderPane.setMargin(imageView, ICON_MARGIN);
        root.setLeft(imageView);
    }

    static void setLeftIcon(BorderPane root, SVG icon, double size) {
        Node node = icon.createIcon(size);
        node.setMouseTransparent(true);
        BorderPane.setAlignment(node, Pos.CENTER);
        BorderPane.setMargin(node, ICON_MARGIN);
        root.setLeft(node);
    }

    public void setLeftIcon(Image icon) {
        setLeftIcon(this, icon, -1.0);
    }

    public void setLeftIcon(Image icon, double size) {
        setLeftIcon(this, icon, size);
    }

    public void setLeftIcon(SVG svg) {
        setLeftIcon(this, svg, SVG.DEFAULT_SIZE);
    }

    public void setLeftIcon(SVG svg, double size) {
        setLeftIcon(this, svg, size);
    }
}
