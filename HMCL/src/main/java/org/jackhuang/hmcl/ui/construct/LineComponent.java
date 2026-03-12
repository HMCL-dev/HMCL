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
import javafx.beans.property.StringProperty;
import javafx.beans.property.StringPropertyBase;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import org.jackhuang.hmcl.ui.SVG;

import java.util.Arrays;
import java.util.Objects;

/// @author Glavo
public abstract class LineComponent extends StackPane implements NoPaddingComponent {
    private static final String DEFAULT_STYLE_CLASS = "line-component";
    private static final double MIN_HEIGHT = 48.0;
    private static final PseudoClass PSEUDO_LARGER_TITLE = PseudoClass.getPseudoClass("large-title");

    protected static final int IDX_LEADING = 0;
    protected static final int IDX_TITLE = 1;
    protected static final int IDX_TRAILING = 2;

    public static final double SPACING = 12;
    public static final double DEFAULT_ICON_SIZE = 20;

    public static void setMargin(Node child, Insets value) {
        HBox.setMargin(child, value);
    }

    protected final HBox container;

    private final Label titleLabel;
    private final VBox titleContainer;

    public LineComponent() {
        this.getStyleClass().add(DEFAULT_STYLE_CLASS);

        this.setMinHeight(MIN_HEIGHT);

        this.container = new HBox(SPACING);
        container.getStyleClass().add("line-component-container");
        container.setAlignment(Pos.CENTER_LEFT);

        this.titleLabel = new Label();
        titleLabel.getStyleClass().add("title-label");
        titleLabel.setMinWidth(Region.USE_PREF_SIZE);

        this.titleContainer = new VBox(titleLabel);
        titleContainer.getStyleClass().add("title-container");
        titleContainer.setMouseTransparent(true);
        titleContainer.setAlignment(Pos.CENTER_LEFT);
        titleContainer.minWidthProperty().bind(titleLabel.prefWidthProperty());
        HBox.setHgrow(titleContainer, Priority.ALWAYS);

        this.setNode(IDX_TITLE, titleContainer);

        this.getChildren().setAll(container);
    }

    private Node[] nodes = new Node[2];

    protected void setNode(int idx, Node node) {
        if (nodes.length <= idx)
            nodes = Arrays.copyOf(nodes, idx + 1);

        if (nodes[idx] != node) {
            nodes[idx] = node;
            container.getChildren().setAll(Arrays.stream(nodes).filter(Objects::nonNull).toArray(Node[]::new));
        }
    }

    public void setLargeTitle(boolean largeTitle) {
        pseudoClassStateChanged(PSEUDO_LARGER_TITLE, largeTitle);
    }

    private final StringProperty title = new StringPropertyBase() {
        @Override
        public Object getBean() {
            return LineComponent.this;
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

    public final StringProperty titleProperty() {
        return title;
    }

    public String getTitle() {
        return titleProperty().get();
    }

    public void setTitle(String title) {
        titleProperty().set(title);
    }

    private StringProperty subtitle;

    public final StringProperty subtitleProperty() {
        if (subtitle == null) {
            subtitle = new StringPropertyBase() {
                private Label subtitleLabel;

                @Override
                public String getName() {
                    return "subtitle";
                }

                @Override
                public Object getBean() {
                    return LineComponent.this;
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

    public final String getSubtitle() {
        return subtitle != null ? subtitle.get() : null;
    }

    public final void setSubtitle(String subtitle) {
        subtitleProperty().set(subtitle);
    }

    private ObjectProperty<Node> leading;

    public final ObjectProperty<Node> leadingProperty() {
        if (leading == null) {
            leading = new ObjectPropertyBase<>() {
                @Override
                public Object getBean() {
                    return LineComponent.this;
                }

                @Override
                public String getName() {
                    return "leading";
                }

                @Override
                protected void invalidated() {
                    setNode(IDX_LEADING, get());
                }
            };
        }
        return leading;
    }

    public final Node getLeading() {
        return leadingProperty().get();
    }

    public final void setLeading(Node node) {
        leadingProperty().set(node);
    }

    public void setLeading(Image icon) {
        setLeading(icon, -1);
    }

    public void setLeading(Image icon, double size) {
        var imageView = new ImageView(icon);
        if (size > 0) {
            imageView.setFitWidth(size);
            imageView.setFitHeight(size);
            imageView.setPreserveRatio(true);
            imageView.setSmooth(true);
        }
        imageView.setMouseTransparent(true);

        setNode(IDX_LEADING, imageView);
    }

    public void setLeading(SVG svg) {
        setLeading(svg, DEFAULT_ICON_SIZE);
    }

    public void setLeading(SVG svg, double size) {
        Node node = svg.createIcon(size);
        node.setMouseTransparent(true);
        setNode(IDX_LEADING, node);
    }

}
