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
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jetbrains.annotations.Nullable;

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

    /// The row containing the title and its optional trailing node.
    private final HBox titleLine;

    /// The primary title label.
    private final Label titleLabel;

    /// The container holding the title row and optional subtitle.
    private final VBox titleContainer;

    /// The optional node displayed immediately after the title.
    private @Nullable Node titleTrailing;

    /// The optional subtitle label.
    private @Nullable Label subtitleLabel;

    public LineComponent() {
        this.getStyleClass().add(DEFAULT_STYLE_CLASS);

        this.setMinHeight(MIN_HEIGHT);

        this.container = new HBox(SPACING);
        container.getStyleClass().add("line-component-container");
        container.setAlignment(Pos.CENTER_LEFT);
        container.setPickOnBounds(true);

        this.titleLabel = new Label();
        titleLabel.getStyleClass().add("title-label");
        titleLabel.setContentDisplay(ContentDisplay.RIGHT);
        titleLabel.setGraphicTextGap(4);
        titleLabel.setMinWidth(Region.USE_PREF_SIZE);
        titleLabel.setMouseTransparent(true);
        titleLabel.setPickOnBounds(false);

        this.titleLine = new HBox(titleLabel);
        titleLine.setAlignment(Pos.CENTER_LEFT);
        titleLine.setPickOnBounds(false);

        this.titleContainer = new VBox(titleLine);
        titleContainer.getStyleClass().add("title-container");
        titleContainer.setAlignment(Pos.CENTER_LEFT);
        titleContainer.minWidthProperty().bind(titleLine.prefWidthProperty());
        titleContainer.setMouseTransparent(true);
        titleContainer.setPickOnBounds(false);
        HBox.setHgrow(titleContainer, Priority.ALWAYS);

        this.setNode(IDX_TITLE, titleContainer);

        this.getChildren().setAll(container);
        widthProperty().addListener(observable -> updatePreferredHeight());
    }

    /// Computes wrapped subtitle height from the row width.
    @Override
    public Orientation getContentBias() {
        return Orientation.HORIZONTAL;
    }

    /// Computes preferred row height from the title column width after fixed-width nodes are removed.
    @Override
    protected double computePrefHeight(double width) {
        return computeLineHeight(width);
    }

    /// Keeps wrapped subtitle rows from being compressed below their preferred height.
    @Override
    protected double computeMinHeight(double width) {
        return computeLineHeight(width);
    }

    /// Computes row height with the same width split used by the `HBox` at layout time.
    private double computeLineHeight(double width) {
        double horizontalInsets = container.snappedLeftInset() + container.snappedRightInset();
        double verticalInsets = container.snappedTopInset() + container.snappedBottomInset();
        double contentWidth = width < 0 ? -1 : Math.max(0, width - horizontalInsets);

        int managedCount = 0;
        double fixedWidth = 0;
        double contentHeight = 0;
        for (Node child : container.getChildren()) {
            if (!child.isManaged()) {
                continue;
            }

            managedCount++;
            if (child == titleContainer) {
                continue;
            }

            fixedWidth += child.prefWidth(-1);
            contentHeight = Math.max(contentHeight, child.prefHeight(-1));
        }

        double titleWidth = contentWidth < 0
                ? -1
                : Math.max(0, contentWidth - fixedWidth - container.getSpacing() * Math.max(0, managedCount - 1));
        contentHeight = Math.max(contentHeight, computeTitleHeight(titleWidth));

        return Math.max(MIN_HEIGHT, verticalInsets + contentHeight);
    }

    /// Computes the height of the title column at the given width.
    private double computeTitleHeight(double width) {
        double height = titleLine.prefHeight(width);
        if (subtitleLabel != null && subtitleLabel.getParent() == titleContainer) {
            height += titleContainer.getSpacing() + subtitleLabel.prefHeight(width);
        }
        return height;
    }

    private Node[] nodes = new Node[2];

    protected void setNode(int idx, Node node) {
        if (nodes.length <= idx)
            nodes = Arrays.copyOf(nodes, idx + 1);

        if (nodes[idx] != node) {
            nodes[idx] = node;
            container.getChildren().setAll(Arrays.stream(nodes).filter(Objects::nonNull).toArray(Node[]::new));
            updatePreferredHeight();
        }
    }

    /// Sets the node displayed immediately after the title label.
    public final void setTitleTrailing(@Nullable Node node) {
        if (titleTrailing == node) {
            return;
        }

        titleTrailing = node;
        titleLabel.setGraphic(node);
        titleLabel.setMouseTransparent(node == null);
        titleContainer.setMouseTransparent(node == null);
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
                            subtitleLabel.setMouseTransparent(true);
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
                    updatePreferredHeight();
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

    /// Updates the fixed preferred height after the row width or subtitle content changes.
    private void updatePreferredHeight() {
        if (FXUtils.getLimitHeight(this) > 0) return;

        double width = getWidth();
        if (width <= 0) {
            setMinHeight(MIN_HEIGHT);
            setPrefHeight(Region.USE_COMPUTED_SIZE);
            return;
        }

        applyCss();
        double height = computeLineHeight(width);
        setMinHeight(height);
        setPrefHeight(height);
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
