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
import javafx.beans.property.StringPropertyBase;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.ui.SVG;

/// @author Glavo
public class LinePane extends BorderPane implements NoPaddingComponent {

    static final Insets PADDING = new Insets(8, 8, 8, 16);
    static final double MIN_HEIGHT = 48.0;

    private final Label titleLabel;

    public LinePane() {
        this.setPadding(PADDING);
        this.setMinHeight(MIN_HEIGHT);

        this.titleLabel = new Label();
        this.setCenter(titleLabel);
        BorderPane.setAlignment(titleLabel, Pos.CENTER_LEFT);
        titleLabel.textProperty().bind(titleProperty());
        titleLabel.getStyleClass().add("title");
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

    abstract static class SubtitleProperty extends StringPropertyBase {
        private VBox left;
        private Label subtitleLabel;

        public abstract BorderPane getRootPane();

        public abstract Label getTitleLabel();

        @Override
        public String getName() {
            return "subtitle";
        }

        @Override
        protected void invalidated() {
            String subtitle = get();
            if (subtitle != null && !subtitle.isEmpty()) {
                if (left == null) {
                    left = new VBox();
                    left.setMouseTransparent(true);
                    left.setAlignment(Pos.CENTER_LEFT);

                    subtitleLabel = new Label();
                    subtitleLabel.setWrapText(true);
                    subtitleLabel.setMinHeight(Region.USE_PREF_SIZE);
                    subtitleLabel.getStyleClass().add("subtitle");
                }
                subtitleLabel.setText(subtitle);
                left.getChildren().setAll(getTitleLabel(), subtitleLabel);
                getRootPane().setCenter(left);
            } else if (left != null) {
                subtitleLabel.setText(null);
                getRootPane().setCenter(getTitleLabel());
            }
        }
    }

    private StringProperty subtitle;

    public StringProperty subtitleProperty() {
        if (subtitle == null) {
            subtitle = new SubtitleProperty() {
                @Override
                public Object getBean() {
                    return LinePane.this;
                }

                @Override
                public BorderPane getRootPane() {
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

    static void setLeftIcon(BorderPane root, Image icon) {
        ImageView imageView = new ImageView(icon);
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
        setLeftIcon(this, icon);
    }

    public void setLeftIcon(SVG svg) {
        setLeftIcon(this, svg, 24);
    }

    public void setLeftIcon(SVG svg, double size) {
        setLeftIcon(this, svg, size);
    }
}
