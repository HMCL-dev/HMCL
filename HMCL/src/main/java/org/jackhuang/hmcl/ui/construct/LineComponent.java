// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.construct;

import javafx.beans.InvalidationListener;
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
import org.jetbrains.annotations.Unmodifiable;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/// Displays a title, optional subtitle and tags, and optional leading and trailing nodes.
///
/// @author Glavo
public abstract class LineComponent extends StackPane implements NoPaddingComponent {
    private static final String DEFAULT_STYLE_CLASS = "line-component";
    private static final double MIN_HEIGHT = 48.0;
    private static final PseudoClass PSEUDO_LARGER_TITLE = PseudoClass.getPseudoClass("large-title");

    protected static final int IDX_LEADING = 0;
    protected static final int IDX_TRAILING = 2;

    /// The gap between the text area and side nodes, and between grouped trailing nodes.
    public static final double SPACING = TwoLineListItem.SIDE_SPACING;
    public static final double DEFAULT_ICON_SIZE = 20;

    /// Sets the margin of a leading or trailing node; null removes it.
    public static void setMargin(Node child, @Nullable Insets value) {
        TwoLineListItem.setMargin(child, value);
    }

    /// The pane laying out the entire row, including optional side nodes.
    protected final TwoLineListItem container;

    /// Creates a row with mouse-transparent title text and independently interactive optional nodes.
    public LineComponent() {
        this.getStyleClass().add(DEFAULT_STYLE_CLASS);

        this.container = new TwoLineListItem();
        container.getStyleClass().setAll("line-component-container");
        container.setTitleSpacing(4);
        container.getTitleLabel().getStyleClass().setAll("label", "title-label");
        container.getTitleLabel().setMinWidth(Region.USE_PREF_SIZE);

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

    /// Stores optional trailing nodes by the slots used by line subclasses.
    private @Nullable Node[] nodes = new Node[0];

    /// Groups multiple trailing nodes into the content pane's single trailing slot.
    private @Nullable HBox trailingGroup;

    /// Collapses the trailing group when its children no longer require rendering or layout.
    private final InvalidationListener trailingGroupListener = observable -> updateTrailingGroup();

    /// Replaces the leading slot or a trailing slot; null removes its content.
    /// Trailing slots are displayed in increasing index order, grouped only when more than one is populated.
    ///
    /// @param idx [#IDX_LEADING] or an index greater than or equal to [#IDX_TRAILING]
    /// @param node the node to display, or null to remove it
    /// @throws IllegalArgumentException if idx does not identify a leading or trailing slot
    protected void setNode(int idx, @Nullable Node node) {
        if (idx == IDX_LEADING) {
            setLeading(node);
            return;
        }
        if (idx < IDX_TRAILING)
            throw new IllegalArgumentException("Invalid row slot: " + idx);
        if (nodes.length <= idx)
            nodes = Arrays.copyOf(nodes, idx + 1);
        if (nodes[idx] == node)
            return;

        @Nullable Node previous = nodes[idx];
        if (previous != null) {
            previous.visibleProperty().removeListener(trailingGroupListener);
            previous.managedProperty().removeListener(trailingGroupListener);
        }
        nodes[idx] = node;
        if (node != null) {
            node.visibleProperty().addListener(trailingGroupListener);
            node.managedProperty().addListener(trailingGroupListener);
        }

        @Unmodifiable List<Node> trailingNodes = Arrays.stream(nodes).filter(Objects::nonNull).toList();
        // Detach before moving nodes between the direct slot and a multi-node group.
        container.setTrailing(null);
        if (trailingGroup != null)
            trailingGroup.getChildren().clear();
        if (trailingNodes.size() == 1) {
            container.setTrailing(trailingNodes.get(0));
        } else if (trailingNodes.size() > 1) {
            if (trailingGroup == null) {
                trailingGroup = new HBox(SPACING);
                trailingGroup.setAlignment(Pos.CENTER_LEFT);
                trailingGroup.setPickOnBounds(false);
            }
            trailingGroup.getChildren().setAll(trailingNodes);
            updateTrailingGroup();
            container.setTrailing(trailingGroup);
        }
    }

    /// Updates the group's own visibility and management from its current children.
    private void updateTrailingGroup() {
        if (trailingGroup != null) {
            trailingGroup.setManaged(trailingGroup.getChildren().stream().anyMatch(Node::isManaged));
            trailingGroup.setVisible(trailingGroup.getChildren().stream().anyMatch(Node::isVisible));
        }
    }

    /// Sets the node displayed immediately after the mouse-transparent title label.
    /// Null removes the node; non-null nodes retain their own mouse input handling.
    /// Nodes that are both invisible and unmanaged remain detached until either property becomes true.
    public final void setTitleTrailing(@Nullable Node node) {
        container.setTitleTrailing(node);
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
        return container.getTags();
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
            container.setTitle(get());
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
                        Label label = container.getSubtitleLabel();
                        label.getStyleClass().setAll("label", "subtitle-label");
                        label.setWrapText(true);
                        container.setSubtitle(subtitle);
                    } else {
                        container.setSubtitle(null);
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

    /// The lazily created leading-node property forwarded to the content pane.
    private @Nullable ObjectProperty<@Nullable Node> leading;

    /// Returns the leading-node property, initially null.
    public final ObjectProperty<@Nullable Node> leadingProperty() {
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
                    container.setLeading(get());
                }
            };
        }
        return leading;
    }

    /// Returns the leading node, or null if none is assigned.
    public final @Nullable Node getLeading() {
        return leading == null ? null : leading.get();
    }

    /// Sets the leading node; null removes it.
    public final void setLeading(@Nullable Node node) {
        leadingProperty().set(node);
    }

    /// Sets a mouse-transparent leading image at its natural dimensions.
    public void setLeading(@Nullable Image icon) {
        setLeading(icon, -1);
    }

    /// Sets a mouse-transparent leading image, fitting within size when positive.
    /// A non-positive size uses the image's natural dimensions; a null image displays no pixels.
    public void setLeading(@Nullable Image icon, double size) {
        var imageView = new ImageView(icon);
        if (size > 0) {
            imageView.setFitWidth(size);
            imageView.setFitHeight(size);
            imageView.setPreserveRatio(true);
            imageView.setSmooth(true);
        }
        imageView.setMouseTransparent(true);

        setLeading(imageView);
    }

    /// Sets a mouse-transparent leading SVG at the default icon size.
    public void setLeading(SVG svg) {
        setLeading(svg, DEFAULT_ICON_SIZE);
    }

    /// Sets a mouse-transparent leading SVG at the specified size.
    public void setLeading(SVG svg, double size) {
        Node node = svg.createIcon(size);
        node.setMouseTransparent(true);
        setLeading(node);
    }

}
