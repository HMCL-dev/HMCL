// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.construct;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ObjectPropertyBase;
import javafx.beans.property.StringProperty;
import javafx.beans.property.StringPropertyBase;
import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
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

/// Displays a title, optional subtitle and tags, and optional leading and trailing nodes.
///
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

    /// The pane laying out the title, optional trailing node, tags, and subtitle.
    private final TwoLineListItem titleContainer;

    /// Creates a row with mouse-transparent title text and independently interactive optional nodes.
    public LineComponent() {
        this.getStyleClass().add(DEFAULT_STYLE_CLASS);

        this.container = new HBox(SPACING);
        container.getStyleClass().add("line-component-container");
        container.setAlignment(Pos.CENTER_LEFT);
        container.setPickOnBounds(true);

        this.titleContainer = new TwoLineListItem();
        titleContainer.getStyleClass().setAll("title-container");
        titleContainer.setTitleSpacing(4);
        titleContainer.getTitleLabel().getStyleClass().setAll("label", "title-label");
        titleContainer.getTitleLabel().setMinWidth(Region.USE_PREF_SIZE);
        HBox.setHgrow(titleContainer, Priority.ALWAYS);

        this.setNode(IDX_TITLE, titleContainer);

        this.getChildren().setAll(container);
    }

    /// Computes wrapped subtitle height from the row width.
    @Override
    public Orientation getContentBias() {
        return Orientation.HORIZONTAL;
    }

    /// Computes preferred row height from the horizontal layout, including child margins and wrapped text.
    @Override
    protected double computePrefHeight(double width) {
        return Math.max(MIN_HEIGHT, super.computePrefHeight(width));
    }

    /// Keeps wrapped subtitle rows from being compressed below their preferred height.
    @Override
    protected double computeMinHeight(double width) {
        return computePrefHeight(width);
    }

    /// Stores optional row nodes by their layout slot.
    private @Nullable Node[] nodes = new Node[2];

    /// Replaces a row slot, removing the slot's content when node is null.
    ///
    /// @param idx the non-negative slot index
    /// @param node the node to display, or null to remove it
    protected void setNode(int idx, @Nullable Node node) {
        if (nodes.length <= idx)
            nodes = Arrays.copyOf(nodes, idx + 1);

        if (nodes[idx] != node) {
            nodes[idx] = node;
            container.getChildren().setAll(Arrays.stream(nodes).filter(Objects::nonNull).toArray(Node[]::new));
        }
    }

    /// Sets the node displayed immediately after the mouse-transparent title label.
    /// Null removes the node; non-null nodes retain their own mouse input handling.
    /// Nodes that are both invisible and unmanaged remain detached until either property becomes true.
    public final void setTitleTrailing(@Nullable Node node) {
        titleContainer.setTitleTrailing(node);
    }

    /// Returns the mutable list of tags displayed after the title and its optional trailing node.
    /// Changes to the list are reflected in the row. Tags do not receive mouse input;
    /// content that exceeds the space left by the title and other nodes is clipped.
    /// An empty list occupies no space. Tag values may be shared or repeated in the list.
    /// Replace a value in the list to change its text or type. Changes must be made on
    /// the JavaFX application thread once this component is attached to a showing scene.
    ///
    /// @return the live list of tag values; elements must be non-null
    public final ObservableList<Tag> getTags() {
        return titleContainer.getTags();
    }

    public void setLargeTitle(boolean largeTitle) {
        pseudoClassStateChanged(PSEUDO_LARGER_TITLE, largeTitle);
    }

    /// The title text forwarded to the content pane.
    private final StringProperty title = new StringPropertyBase() {
        @Override
        public Object getBean() {
            return LineComponent.this;
        }

        @Override
        public String getName() {
            return "title";
        }

        /// Updates the title displayed by the content pane.
        @Override
        protected void invalidated() {
            titleContainer.setTitle(get());
        }
    };

    /// Returns the title property; null text is displayed as empty.
    public final StringProperty titleProperty() {
        return title;
    }

    /// Returns the title text, or null if none is assigned.
    public @Nullable String getTitle() {
        return titleProperty().get();
    }

    /// Sets the title text; null is displayed as empty.
    public void setTitle(@Nullable String title) {
        titleProperty().set(title);
    }

    /// The lazily created subtitle property; null and empty values omit the second row.
    private @Nullable StringProperty subtitle;

    /// Returns the subtitle property; null and empty values omit the second row.
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

                /// Updates the wrapping subtitle in the content pane.
                @Override
                protected void invalidated() {
                    @Nullable String subtitle = get();
                    if (subtitle != null && !subtitle.isEmpty()) {
                        Label label = titleContainer.getSubtitleLabel();
                        label.getStyleClass().setAll("label", "subtitle-label");
                        label.setWrapText(true);
                        titleContainer.setSubtitle(subtitle);
                    } else {
                        titleContainer.setSubtitle(null);
                    }
                }
            };
        }

        return subtitle;
    }

    /// Returns the assigned subtitle text, or null if none is assigned.
    public final @Nullable String getSubtitle() {
        return subtitle != null ? subtitle.get() : null;
    }

    /// Sets the subtitle text; null and empty values omit the second row.
    public final void setSubtitle(@Nullable String subtitle) {
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
