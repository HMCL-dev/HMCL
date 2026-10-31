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
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import org.jackhuang.hmcl.ui.SVG;
import org.jetbrains.annotations.Nullable;

/// Displays a title, optional subtitle and tags, and optional leading and trailing nodes.
///
/// @author Glavo
public abstract class LineComponent extends StackPane implements NoPaddingComponent {
    private static final String DEFAULT_STYLE_CLASS = "line-component";
    private static final double MIN_HEIGHT = 48.0;
    private static final PseudoClass PSEUDO_LARGER_TITLE = PseudoClass.getPseudoClass("large-title");

    /// The default gap between text and side nodes, also used within trailing groups.
    public static final double SPACING = 12;

    /// The default margin separating a leading node from the text.
    private static final Insets LEADING_MARGIN = new Insets(0, SPACING, 0, 0);

    /// The default margin separating a trailing node from the text.
    private static final Insets TRAILING_MARGIN = new Insets(0, 0, 0, SPACING);
    public static final double DEFAULT_ICON_SIZE = 20;

    /// Sets the full margin of a leading or trailing node, including its separation from the text.
    /// Null removes the margin; [Insets#EMPTY] explicitly requests no margin.
    public static void setMargin(Node child, @Nullable Insets value) {
        TwoLineListItem.setMargin(child, value);
    }

    /// The pane laying out the entire row, including optional side nodes.
    protected final TwoLineListItem container;

    /// Creates a row with truncation tooltips and independently interactive optional nodes.
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

    /// Sets the trailing node, supplying a 12-pixel left margin only if no margin is assigned.
    /// Null removes the node. Subclasses may supply a container for multiple trailing controls.
    protected final void setTrailing(@Nullable Node node) {
        if (node != null && TwoLineListItem.getMargin(node) == null)
            setMargin(node, TRAILING_MARGIN);
        container.setTrailing(node);
    }

    /// Sets the node displayed immediately after the title label.
    /// Null removes the node; non-null nodes retain their own mouse input handling.
    /// Nodes that are both invisible and unmanaged remain detached until either property becomes true.
    public final void setTitleTrailing(@Nullable Node node) {
        container.setTitleTrailing(node);
    }

    /// Returns the mutable list of tags displayed after the title and its optional trailing node.
    /// Changes to the list are reflected in the row. Tags that do not fit are replaced
    /// by a single `+N` tag whose tooltip lists the hidden values, one per line.
    /// The title may shrink to preserve space for this summary.
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
    /// Assigning a node supplies a 12-pixel right margin only if no margin is assigned.
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

                /// Applies the default margin when absent and replaces the leading node.
                @Override
                protected void invalidated() {
                    @Nullable Node node = get();
                    if (node != null && TwoLineListItem.getMargin(node) == null)
                        setMargin(node, LEADING_MARGIN);
                    container.setLeading(node);
                }
            };
        }
        return leading;
    }

    /// Returns the leading node, or null if none is assigned.
    public final @Nullable Node getLeading() {
        return leading == null ? null : leading.get();
    }

    /// Sets the leading node; null removes it. See [#leadingProperty()] for default margins.
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
