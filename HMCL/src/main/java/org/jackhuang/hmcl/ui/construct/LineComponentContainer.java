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

import javafx.beans.InvalidationListener;
import javafx.beans.property.StringProperty;
import javafx.beans.property.StringPropertyBase;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import org.jackhuang.hmcl.ui.SVG;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Objects;

/// Helper class to assist in implementing [LineComponent].
///
/// @author Glavo
public abstract class LineComponentContainer extends HBox implements LineComponent {
    protected static final int IDX_LEFT_ICON = 0;
    protected static final int IDX_TITLE = 1;
    protected static final int IDX_RIGHT = 2;

    private static final Insets PADDING = new Insets(8, 8, 8, 16);
    private static final double MIN_HEIGHT = 48.0;

    public static void setMargin(Node child, Insets value) {
        HBox.setMargin(child, value);
    }

    private final Label titleLabel;
    private final VBox titleContainer;

    public LineComponentContainer() {
        setPadding(PADDING);
        setMinHeight(MIN_HEIGHT);
        setAlignment(Pos.CENTER_LEFT);

        this.titleLabel = new Label();
        titleLabel.getStyleClass().add("title-label");
        titleLabel.setMinWidth(Region.USE_PREF_SIZE);

        this.titleContainer = new VBox(titleLabel);
        titleContainer.setMouseTransparent(true);
        titleContainer.setAlignment(Pos.CENTER_LEFT);
        titleContainer.minWidthProperty().bind(titleLabel.prefWidthProperty());
        HBox.setHgrow(titleContainer, Priority.ALWAYS);

        this.setNode(IDX_TITLE, titleContainer);
    }

    private Node[] nodes = new Node[2];

    protected void setNode(int idx, Node node) {
        if (nodes.length <= idx)
            nodes = Arrays.copyOf(nodes, idx + 1);

        if (nodes[idx] != node) {
            nodes[idx] = node;
            this.getChildren().setAll(Arrays.stream(nodes).filter(Objects::nonNull).toArray(Node[]::new));
        }
    }

    protected @Nullable Node getNode(int idx) {
        return idx < nodes.length ? nodes[idx] : null;
    }

    protected abstract LineComponent getBean();

    @Override
    public final LineComponentContainer getRoot() {
        return this;
    }

    private final StringProperty title = new StringPropertyBase() {
        @Override
        public Object getBean() {
            return LineComponentContainer.this.getBean();
        }

        @Override
        public String getName() {
            return "title";
        }

        @Override
        protected void invalidated() {
            titleLabel.setText(get());
        }
    };

    @Override
    public final StringProperty titleProperty() {
        return title;
    }

    private StringProperty subtitle;

    @Override
    public final StringProperty subtitleProperty() {
        if (subtitle == null) {
            subtitle = new StringPropertyBase() {
                private Label subtitleLabel;
                private InvalidationListener maxWidthListener;

                @Override
                public String getName() {
                    return "subtitle";
                }

                @Override
                public Object getBean() {
                    return LineComponentContainer.this.getBean();
                }

                @Override
                protected void invalidated() {
                    String subtitle = get();
                    if (subtitle != null && !subtitle.isEmpty()) {
                        if (subtitleLabel == null) {
                            subtitleLabel = new Label();
                            subtitleLabel.setWrapText(true);
                            subtitleLabel.setMinHeight(Region.USE_PREF_SIZE);
                            subtitleLabel.getStyleClass().add("subtitle-label");
                        }
                        subtitleLabel.setText(subtitle);
                        if (titleContainer.getChildren().size() == 1)
                            titleContainer.getChildren().add(subtitleLabel);
                    } else if (subtitleLabel != null) {
                        subtitleLabel.setText(null);
                        if (titleContainer.getChildren().size() == 2)
                            titleContainer.getChildren().remove(1);
                    }
                }
            };
        }

        return subtitle;
    }

    private static final Insets LEFT_ICON_MARGIN = new Insets(0, 16, 0, 0);

    @Override
    public void setLeftIcon(Image icon, double size) {
        ImageView imageView = new ImageView(icon);
        imageView.getStyleClass().add("left-icon");
        if (size > 0) {
            imageView.setFitWidth(size);
            imageView.setFitHeight(size);
            imageView.setPreserveRatio(true);
            imageView.setSmooth(true);
        }
        imageView.setMouseTransparent(true);
        setMargin(imageView, LEFT_ICON_MARGIN);

        setNode(IDX_LEFT_ICON, imageView);
    }

    @Override
    public void setLeftIcon(SVG svg, double size) {
        Node node = svg.createIcon(size);
        node.getStyleClass().add("left-icon");
        node.setMouseTransparent(true);
        setMargin(node, LEFT_ICON_MARGIN);
        setNode(IDX_LEFT_ICON, node);
    }
}
