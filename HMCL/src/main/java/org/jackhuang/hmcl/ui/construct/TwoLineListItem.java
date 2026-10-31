// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.construct;

import javafx.beans.InvalidationListener;
import javafx.beans.property.StringProperty;
import javafx.beans.property.StringPropertyBase;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Rectangle;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;

/// Displays a title, optional title-trailing node, tags, and an optional subtitle.
/// Both rows are left-aligned and vertically centered together within this pane.
/// All content is laid out directly in this pane. The title-trailing node receives
/// its preferred width, then the title receives up to its preferred width, subject
/// to its minimum width. Tags use the remaining space and are clipped at the right edge.
/// Text is mouse-transparent; an optional title-trailing node retains its own input handling.
/// The children list is managed by this component and must not be modified directly.
@NotNullByDefault
public class TwoLineListItem extends Pane {
    /// The spacing between adjacent tags.
    private static final double TAG_SPACING = 8;

    /// Selects the warning appearance for rendered tags.
    private static final PseudoClass WARNING_PSEUDO_CLASS = PseudoClass.getPseudoClass("warning");

    /// The primary text label, whose minimum width controls whether the title may shrink.
    private final Label lblTitle = new Label();

    /// The lazily created subtitle label, attached only while the subtitle is non-null.
    private @Nullable Label lblSubtitle;

    /// The optional node immediately following the title.
    private @Nullable Node titleTrailing;

    /// Observes trailing-node visibility and layout participation.
    private final InvalidationListener titleTrailingListener = observable -> updateTitleTrailing();

    /// The gap after the title and its trailing node; tag-to-tag spacing is independent.
    private double titleSpacing;

    /// The mutable tag values, allocated when first requested.
    private @Nullable ObservableList<Tag> tags;

    /// The labels rendering tag values in list order, each with its own overflow clip.
    private final List<Label> tagLabels = new ArrayList<>();

    /// The primary title text.
    private final StringProperty title = new StringPropertyBase() {
        /// Returns the component owning this property.
        @Override
        public Object getBean() {
            return TwoLineListItem.this;
        }

        /// Returns the property name.
        @Override
        public String getName() {
            return "title";
        }

        /// Updates the title label.
        @Override
        protected void invalidated() {
            lblTitle.setText(get());
        }
    };

    /// The subtitle text property, allocated when first requested.
    private @Nullable StringProperty subtitle;

    /// Creates an empty item with mouse-transparent text and a shrinkable title.
    public TwoLineListItem() {
        getStyleClass().add("two-line-list-item");
        setMouseTransparent(true);
        setPickOnBounds(false);
        lblTitle.getStyleClass().add("title");
        lblTitle.setTextOverrun(OverrunStyle.ELLIPSIS);
        lblTitle.setMinWidth(0);
        lblTitle.setMouseTransparent(true);
        getChildren().add(lblTitle);
    }

    /// Creates an item with the given title and optional subtitle.
    /// Null title text is empty; a null subtitle omits the second row.
    public TwoLineListItem(@Nullable String titleString, @Nullable String subtitleString) {
        this();
        setTitle(titleString);
        setSubtitle(subtitleString);
    }

    /// Returns the title text property; null text is displayed as empty.
    public StringProperty titleProperty() {
        return title;
    }

    /// Returns the title text, or null if none is assigned.
    public @Nullable String getTitle() {
        return title.get();
    }

    /// Sets the title text; null is displayed as empty.
    public void setTitle(@Nullable String title) {
        this.title.set(title);
    }

    /// Returns the subtitle property. Null removes the second row; an empty string retains it.
    public StringProperty subtitleProperty() {
        if (subtitle == null) {
            subtitle = new StringPropertyBase() {
                /// Returns the component owning this property.
                @Override
                public Object getBean() {
                    return TwoLineListItem.this;
                }

                /// Returns the property name.
                @Override
                public String getName() {
                    return "subtitle";
                }

                /// Updates the subtitle and attaches or removes its label.
                @Override
                protected void invalidated() {
                    @Nullable String text = get();
                    if (text != null) {
                        Label label = getSubtitleLabel();
                        label.setText(text);
                        if (label.getParent() != TwoLineListItem.this)
                            getChildren().add(label);
                    } else if (lblSubtitle != null) {
                        lblSubtitle.setText(null);
                        getChildren().remove(lblSubtitle);
                    }
                    requestLayout();
                }
            };
        }
        return subtitle;
    }

    /// Returns the subtitle text, or null if the second row is absent.
    public @Nullable String getSubtitle() {
        return subtitle != null ? subtitle.get() : null;
    }

    /// Sets the subtitle text; null removes the second row.
    public void setSubtitle(@Nullable String subtitle) {
        if (this.subtitle == null && subtitle == null)
            return;
        subtitleProperty().set(subtitle);
    }

    /// Returns the title label for text wrapping and sizing configuration.
    public Label getTitleLabel() {
        return lblTitle;
    }

    /// Returns the subtitle label, creating it if necessary without attaching it.
    /// Use [#setSubtitle(String)] to display the second row.
    public Label getSubtitleLabel() {
        if (lblSubtitle == null) {
            lblSubtitle = new Label();
            lblSubtitle.getStyleClass().add("subtitle");
            lblSubtitle.setMouseTransparent(true);
        }
        return lblSubtitle;
    }

    /// Sets the node displayed immediately after the title, or removes it if null.
    /// A node that is both invisible and unmanaged is detached until either property
    /// becomes true. Assigning a visible node enables input for that node while the
    /// text and tags remain mouse-transparent.
    public final void setTitleTrailing(@Nullable Node node) {
        if (titleTrailing == node)
            return;
        if (titleTrailing != null) {
            titleTrailing.visibleProperty().removeListener(titleTrailingListener);
            titleTrailing.managedProperty().removeListener(titleTrailingListener);
            getChildren().remove(titleTrailing);
        }
        titleTrailing = node;
        if (node != null) {
            node.visibleProperty().addListener(titleTrailingListener);
            node.managedProperty().addListener(titleTrailingListener);
        }
        updateTitleTrailing();
    }

    /// Updates trailing-node attachment and mouse input participation.
    private void updateTitleTrailing() {
        if (titleTrailing != null) {
            if (titleTrailing.isVisible() || titleTrailing.isManaged()) {
                if (titleTrailing.getParent() != this)
                    getChildren().add(1, titleTrailing);
            } else {
                getChildren().remove(titleTrailing);
            }
        }
        setMouseTransparent(titleTrailing == null || !titleTrailing.isVisible());
        requestLayout();
    }

    /// Sets the non-negative gap after title-row content, excluding gaps between adjacent tags.
    ///
    /// @throws IllegalArgumentException if spacing is negative or not finite
    final void setTitleSpacing(double spacing) {
        if (!Double.isFinite(spacing) || spacing < 0)
            throw new IllegalArgumentException("Spacing must be finite and non-negative");
        titleSpacing = spacing;
        requestLayout();
    }

    /// Returns the live, mutable list of non-null tag values displayed after the title-trailing node.
    /// Values may be repeated or shared between components. Replace a value to change its
    /// text or type. Changes must be made on the JavaFX application thread once this item
    /// is attached to a showing scene.
    public ObservableList<Tag> getTags() {
        if (tags == null) {
            tags = FXCollections.observableArrayList();
            tags.addListener((ListChangeListener<Tag>) change -> {
                @Unmodifiable List<Label> labels = change.getList().stream().map(TwoLineListItem::createTagLabel).toList();
                getChildren().removeAll(tagLabels);
                tagLabels.clear();
                tagLabels.addAll(labels);
                getChildren().addAll(tagLabels);
                requestLayout();
            });
        }
        return tags;
    }

    /// Creates a mouse-transparent tag label with a clip updated by this pane's layout.
    private static Label createTagLabel(Tag tag) {
        var label = new Label(tag.text());
        label.getStyleClass().add("tag");
        label.setMinWidth(USE_PREF_SIZE);
        label.setMouseTransparent(true);
        label.setClip(new Rectangle());
        label.pseudoClassStateChanged(WARNING_PSEUDO_CLASS, tag.type() == Tag.Type.WARNING);
        return label;
    }

    /// Appends a tag with the given text and type.
    ///
    /// @throws NullPointerException if tag or type is null
    public void addTag(String tag, Tag.Type type) {
        getTags().add(new Tag(tag, type));
    }

    /// Appends a normal tag with the given text.
    public void addTag(String tag) {
        addTag(tag, Tag.Type.NORMAL);
    }

    /// Appends normal tags in iteration order, retaining duplicate text.
    /// The collection and its elements must be non-null.
    public void addTags(Collection<String> tags) {
        getTags().addAll(tags.stream().map(tag -> new Tag(tag, Tag.Type.NORMAL)).toList());
    }

    /// Appends each distinct text not already present, in iteration order.
    /// Text is compared using [String#equals(Object)] regardless of type.
    /// The collection and its elements must be non-null.
    public void addTagsIfNotExist(Collection<String> tags) {
        var current = getTags().stream().map(Tag::text).collect(Collectors.toSet());
        var target = new LinkedHashSet<>(tags);
        target.removeAll(current);
        addTags(target);
    }

    /// Appends a warning tag with the given text.
    public void addTagWarning(String tag) {
        getTags().add(Tag.warning(tag));
    }

    /// Indicates that wrapped text height depends on the allocated width.
    @Override
    public Orientation getContentBias() {
        return Orientation.HORIZONTAL;
    }

    /// Returns whether the trailing node participates in layout.
    private boolean hasManagedTrailing() {
        return titleTrailing != null && titleTrailing.isManaged();
    }

    /// Returns whether the subtitle participates in layout.
    private boolean hasSubtitle() {
        return lblSubtitle != null && lblSubtitle.getParent() == this && lblSubtitle.isManaged();
    }

    /// Returns a node's snapped preferred width, bounded by its minimum and maximum widths.
    private double preferredWidth(Node node) {
        return snapSizeX(Math.max(node.minWidth(-1), Math.min(node.prefWidth(-1), node.maxWidth(-1))));
    }

    /// Returns the gap between the title and its managed trailing node.
    private double trailingGap() {
        return lblTitle.isManaged() && hasManagedTrailing() ? snapSpaceX(titleSpacing) : 0;
    }

    /// Returns the gap before the first tag, if other first-row content precedes it.
    private double tagsGap() {
        return !tagLabels.isEmpty() && (lblTitle.isManaged() || hasManagedTrailing()) ? snapSpaceX(titleSpacing) : 0;
    }

    /// Computes natural width, including every tag even when some would be clipped at layout time.
    @Override
    protected double computePrefWidth(double height) {
        double width = lblTitle.isManaged() ? preferredWidth(lblTitle) : 0;
        if (hasManagedTrailing())
            width += trailingGap() + preferredWidth(titleTrailing);
        width += tagsGap();
        for (Label label : tagLabels)
            width += preferredWidth(label);
        width += snapSpaceX(TAG_SPACING) * Math.max(0, tagLabels.size() - 1);
        if (hasSubtitle())
            width = Math.max(width, preferredWidth(lblSubtitle));
        return snappedLeftInset() + width + snappedRightInset();
    }

    /// Computes minimum width from text and the title-trailing node; tags may be fully clipped.
    @Override
    protected double computeMinWidth(double height) {
        double width = lblTitle.isManaged() ? snapSizeX(lblTitle.minWidth(-1)) : 0;
        if (hasManagedTrailing())
            width += trailingGap() + preferredWidth(titleTrailing);
        if (hasSubtitle())
            width = Math.max(width, snapSizeX(lblSubtitle.minWidth(-1)));
        return snappedLeftInset() + width + snappedRightInset();
    }

    /// Describes the first-row dimensions at a particular available width.
    ///
    /// @param titleWidth the title label's allocated width
    /// @param trailingWidth the managed trailing node's allocated width, or zero
    /// @param height the maximum preferred height of the first-row children
    @NotNullByDefault
    private record FirstLineSize(double titleWidth, double trailingWidth, double height) {
    }

    /// Measures the first row, giving title text priority over tags after reserving the trailing node.
    private FirstLineSize measureFirstLine(double width) {
        double trailingWidth = hasManagedTrailing() ? preferredWidth(titleTrailing) : 0;
        double titleWidth = lblTitle.isManaged() ? preferredWidth(lblTitle) : 0;
        if (width >= 0 && lblTitle.isManaged()) {
            titleWidth = Math.max(snapSizeX(lblTitle.minWidth(-1)),
                    Math.min(titleWidth, Math.max(0, width - trailingWidth - trailingGap())));
        }
        double height = lblTitle.isManaged() ? snapSizeY(lblTitle.prefHeight(titleWidth)) : 0;
        if (hasManagedTrailing())
            height = Math.max(height, snapSizeY(titleTrailing.prefHeight(trailingWidth)));
        for (Label label : tagLabels)
            height = Math.max(height, snapSizeY(label.prefHeight(-1)));
        return new FirstLineSize(titleWidth, trailingWidth, height);
    }

    /// Computes preferred height using the same width allocation as layout.
    @Override
    protected double computePrefHeight(double width) {
        double contentWidth = width < 0 ? -1 : Math.max(0, width - snappedLeftInset() - snappedRightInset());
        double height = measureFirstLine(contentWidth).height();
        if (hasSubtitle())
            height += snapSizeY(lblSubtitle.prefHeight(contentWidth));
        return snappedTopInset() + height + snappedBottomInset();
    }

    /// Keeps wrapped text from being compressed below its computed height.
    @Override
    protected double computeMinHeight(double width) {
        return computePrefHeight(width);
    }

    /// Positions both rows and clips each tag independently to the remaining horizontal space.
    @Override
    protected void layoutChildren() {
        double x = snappedLeftInset();
        double width = Math.max(0, getWidth() - x - snappedRightInset());
        double height = Math.max(0, getHeight() - snappedTopInset() - snappedBottomInset());
        FirstLineSize first = measureFirstLine(width);
        double subtitleHeight = hasSubtitle() ? snapSizeY(lblSubtitle.prefHeight(width)) : 0;
        double extraHeight = Math.max(0, height - first.height() - subtitleHeight);
        double y = snappedTopInset() + snapPositionY(extraHeight / 2);

        if (lblTitle.isManaged()) {
            layoutFirstLineNode(lblTitle, x, y, first.titleWidth(), first.height());
            x += first.titleWidth();
        }
        if (hasManagedTrailing()) {
            x += trailingGap();
            layoutFirstLineNode(titleTrailing, x, y, first.trailingWidth(), first.height());
            x += first.trailingWidth();
        }
        x += tagsGap();
        double right = getWidth() - snappedRightInset();
        for (Label label : tagLabels) {
            double tagWidth = preferredWidth(label);
            layoutFirstLineNode(label, x, y, tagWidth, first.height());
            Rectangle clip = (Rectangle) label.getClip();
            clip.setWidth(Math.max(0, Math.min(tagWidth, right - x)));
            clip.setHeight(label.getHeight());
            // Fully clipped tags must not extend the pane's visual bounds at their off-screen positions.
            label.setVisible(clip.getWidth() > 0);
            x += tagWidth + snapSpaceX(TAG_SPACING);
        }
        if (hasSubtitle())
            lblSubtitle.resizeRelocate(snappedLeftInset(), y + first.height(), width, subtitleHeight);
    }

    /// Vertically centers a child within the first row at its preferred height.
    private void layoutFirstLineNode(Node node, double x, double y, double width, double rowHeight) {
        double height = snapSizeY(node.prefHeight(width));
        node.resizeRelocate(x, y + snapPositionY((rowHeight - height) / 2), width, height);
    }

    /// Returns the current text and tag values for diagnostics.
    @Override
    public String toString() {
        return "TwoLineListItem[title=%s, subtitle=%s, tags=%s]".formatted(getTitle(), getSubtitle(), tags);
    }
}
