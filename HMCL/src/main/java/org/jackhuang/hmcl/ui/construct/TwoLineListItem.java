// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.construct;

import javafx.beans.InvalidationListener;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ObjectPropertyBase;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.property.StringPropertyBase;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.text.Text;
import javafx.stage.Window;
import javafx.util.Duration;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/// Displays a title and optional subtitle, tags, and leading or trailing nodes.
/// The first row contains the title, an optional title-trailing node, and tags.
/// The subtitle occupies the full text width below it. Both rows are left-aligned
/// and vertically centered together; side nodes are centered independently.
/// Wrapping can be configured separately through [#getTitleLabel()] and [#getSubtitleLabel()].
///
/// Side nodes receive their preferred widths, shrinking toward their minimum widths
/// when needed to accommodate the text area's minimum width. Side nodes have no default
/// gap from the text; use [#setMargin(Node, Insets)] to specify their separation.
/// The title-trailing node receives its preferred width. Tags that do not fit after
/// the title are folded into a `+N` tag whose tooltip lists the hidden values.
/// The title may shrink below its minimum width to preserve space for this tag.
/// Without tags, the title's minimum width is respected.
///
/// On JavaFX 23 or later, truncated title and subtitle text receives mouse input
/// to show its full value in a tooltip. Other text and expanded tags are mouse-transparent.
/// Primary clicks on tooltip targets act as background clicks in the nearest [RipplerContainer].
///
/// Only managed nodes participate in layout. Leading, trailing, and title-trailing
/// nodes are detached while both invisible and unmanaged. A node must not occupy multiple slots.
/// This component manages its children list; callers must not modify it directly.
@NotNullByDefault
public class TwoLineListItem extends Region {
    /// Sets the margin around a leading or trailing node; null removes the margin.
    /// Uses the same constraint as [HBox#setMargin(Node, Insets)] so a node can retain
    /// its margins when moved into a horizontal group. Title and tag nodes do not use this constraint.
    public static void setMargin(Node child, @Nullable Insets margin) {
        HBox.setMargin(child, margin);
    }

    /// Returns the margin around a side node, or null when none is assigned.
    public static @Nullable Insets getMargin(Node child) {
        return HBox.getMargin(child);
    }

    /// The primary text label, whose minimum width is respected when no tags are present.
    private final Label titleLabel = new Label();

    /// The lazily created subtitle label, attached only while the subtitle is non-null.
    private @Nullable Label subtitleLabel;

    /// The optional node immediately following the title.
    private @Nullable Node titleTrailing;

    /// The optional leading-node property, allocated when first requested.
    private @Nullable ObjectProperty<@Nullable Node> leading;

    /// The optional trailing-node property, allocated when first requested.
    private @Nullable ObjectProperty<@Nullable Node> trailing;

    /// Updates optional-node attachment when visibility or layout participation changes.
    private final InvalidationListener optionalNodeListener = observable -> updateOptionalNodes();

    /// The gap after the title and its trailing node; tag-to-tag spacing is independent.
    private double titleSpacing;

    /// The mutable tag values, allocated when first requested.
    private @Nullable ObservableList<Tag> tags;

    /// Rendering state, allocated only when the first tag is added.
    private @Nullable TagState tagState;

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
            titleLabel.setText(get());
        }
    };

    /// The subtitle text property, allocated when first requested.
    private @Nullable StringProperty subtitle;

    /// Creates an empty item with truncation tooltips and a shrinkable title.
    public TwoLineListItem() {
        getStyleClass().add("two-line-list-item");
        setMouseTransparent(true);
        setPickOnBounds(false);
        titleLabel.getStyleClass().add("title");
        titleLabel.setTextOverrun(OverrunStyle.ELLIPSIS);
        titleLabel.setMinWidth(0);
        initializeTextLabel(titleLabel);
        getChildren().add(titleLabel);
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
                    } else if (subtitleLabel != null) {
                        subtitleLabel.setText(null);
                        getChildren().remove(subtitleLabel);
                    }
                    updateMouseTransparency();
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

    /// Returns the title label for wrapping, styling, and sizing configuration.
    /// Use [#setTitle(String)] to update the title property and its displayed text together.
    public Label getTitleLabel() {
        return titleLabel;
    }

    /// Returns the subtitle label for wrapping, styling, and sizing configuration.
    /// Creates the label if necessary without attaching it.
    /// Use [#setSubtitle(String)] to display the second row.
    public Label getSubtitleLabel() {
        if (subtitleLabel == null) {
            subtitleLabel = new Label();
            subtitleLabel.getStyleClass().add("subtitle");
            initializeTextLabel(subtitleLabel);
        }
        return subtitleLabel;
    }

    /// Returns the leading-node property, initially null.
    /// Its value appears before the text area; null removes it.
    public final ObjectProperty<@Nullable Node> leadingProperty() {
        if (leading == null)
            leading = createSideProperty("leading");
        return leading;
    }

    /// Returns the leading node, or null if none is assigned.
    public final @Nullable Node getLeading() {
        return leading == null ? null : leading.get();
    }

    /// Sets the node before the text area; null removes it.
    public final void setLeading(@Nullable Node node) {
        leadingProperty().set(node);
    }

    /// Returns the trailing-node property, initially null.
    /// Its value appears after the text area; null removes it.
    public final ObjectProperty<@Nullable Node> trailingProperty() {
        if (trailing == null)
            trailing = createSideProperty("trailing");
        return trailing;
    }

    /// Returns the node after the text area, or null if none is assigned.
    public final @Nullable Node getTrailing() {
        return trailing == null ? null : trailing.get();
    }

    /// Sets the node after the text area; null removes it.
    public final void setTrailing(@Nullable Node node) {
        trailingProperty().set(node);
    }

    /// Sets the node displayed immediately after the title, or removes it if null.
    /// The node precedes the tags and receives its preferred width when managed.
    public final void setTitleTrailing(@Nullable Node node) {
        if (titleTrailing == node)
            return;
        @Nullable Node previous = titleTrailing;
        titleTrailing = node;
        replaceOptionalNode(previous, node);
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

    /// Returns the live, mutable tag list in display order.
    /// Values must be non-null and may be repeated or shared between components.
    /// Replace a value to change its text or type. Changes must be made on the JavaFX application thread once this item
    /// is attached to a showing scene. Tags are displayed in order; a suffix that does not
    /// fit is replaced by a `+N` tag whose tooltip lists the hidden values, one per line.
    ///
    /// @apiNote Use [ObservableList#setAll(Collection)] to replace tags while reusing existing labels.
    public ObservableList<Tag> getTags() {
        if (tags == null) {
            tags = FXCollections.observableArrayList();
            tags.addListener(this::updateTags);
        }
        return tags;
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

    /// Returns horizontal bias when folding tags or measuring width-dependent children.
    @Override
    public @Nullable Orientation getContentBias() {
        return hasTags() || hasHorizontalBias(titleLabel) || hasSubtitle() && hasHorizontalBias(subtitleLabel)
                || hasHorizontalBias(titleTrailing) || hasHorizontalBias(getLeading()) || hasHorizontalBias(getTrailing())
                ? Orientation.HORIZONTAL : null;
    }

    /// Returns the current text and tag values for diagnostics.
    @Override
    public String toString() {
        return "TwoLineListItem[title=%s, subtitle=%s, tags=%s]".formatted(getTitle(), getSubtitle(), tags);
    }

    /// Enables lazy truncation tooltips while preserving background clicks in surrounding ripplers.
    private void initializeTextLabel(Label label) {
        label.setMouseTransparent(true);
        FXUtils.showTooltipWhenTruncated(label);
        @Nullable ReadOnlyBooleanProperty truncated = FXUtils.textTruncatedProperty(label);
        if (truncated != null) {
            RipplerContainer.enableBackgroundMouseEvents(label);
            label.setMouseTransparent(!truncated.get());
            truncated.addListener((observable, oldValue, newValue) -> {
                label.setMouseTransparent(!newValue);
                updateMouseTransparency();
            });
        }
    }

    /// Creates a property that transfers attachment and listeners when its node changes.
    private ObjectProperty<@Nullable Node> createSideProperty(String name) {
        return new ObjectPropertyBase<>() {
            /// The node whose listeners are currently installed.
            private @Nullable Node previous;

            /// Returns the item owning this property.
            @Override
            public Object getBean() {
                return TwoLineListItem.this;
            }

            /// Returns the side represented by this property.
            @Override
            public String getName() {
                return name;
            }

            /// Removes the previous node and starts observing the replacement.
            @Override
            protected void invalidated() {
                @Nullable Node oldNode = previous;
                previous = get();
                replaceOptionalNode(oldNode, previous);
            }
        };
    }

    /// Transfers visibility and managed-state listeners from the old node to its replacement.
    private void replaceOptionalNode(@Nullable Node previous, @Nullable Node node) {
        if (previous != null) {
            previous.visibleProperty().removeListener(optionalNodeListener);
            previous.managedProperty().removeListener(optionalNodeListener);
            getChildren().remove(previous);
        }
        if (node != null) {
            node.visibleProperty().addListener(optionalNodeListener);
            node.managedProperty().addListener(optionalNodeListener);
        }
        updateOptionalNodes();
    }

    /// Updates optional-node attachment, input participation, and layout.
    private void updateOptionalNodes() {
        updateOptionalNode(getLeading(), getChildren().size());
        updateOptionalNode(getTrailing(), getChildren().size());
        updateOptionalNode(titleTrailing, 1);
        updateMouseTransparency();
        requestLayout();
    }

    /// Attaches a visible or managed node at the given index if it is not already a child.
    private void updateOptionalNode(@Nullable Node node, int index) {
        if (node == null)
            return;
        if (node.isVisible() || node.isManaged()) {
            if (node.getParent() != this)
                getChildren().add(index, node);
        } else {
            getChildren().remove(node);
        }
    }

    /// Enables input for truncated text, optional nodes, and the collapsed tag.
    private void updateMouseTransparency() {
        setMouseTransparent(titleLabel.isMouseTransparent()
                && (subtitleLabel == null || subtitleLabel.getParent() != this || subtitleLabel.isMouseTransparent())
                && !isVisible(getLeading()) && !isVisible(getTrailing())
                && !isVisible(titleTrailing) && (tagState == null || !tagState.collapsedTag.isVisible()));
    }

    /// Returns whether an optional node is visible.
    private static boolean isVisible(@Nullable Node node) {
        return node != null && node.isVisible();
    }

    /// Updates the changed suffix, allocating rendering state only for a nonempty tag list.
    private void updateTags(ListChangeListener.Change<? extends Tag> change) {
        if (tagState == null) {
            if (change.getList().isEmpty())
                return;
            tagState = new TagState(getTags());
        }
        int from = change.getList().size();
        while (change.next())
            from = Math.min(from, change.getFrom());
        tagState.updateTags(from);
    }

    /// Returns whether the subtitle participates in layout.
    private boolean hasSubtitle() {
        return subtitleLabel != null && subtitleLabel.getParent() == this && subtitleLabel.isManaged();
    }

    /// Returns a node's snapped preferred width, bounded by its minimum and maximum widths.
    private double preferredWidth(Node node) {
        return snapSizeX(Math.max(node.minWidth(-1), Math.min(node.prefWidth(-1), node.maxWidth(-1))));
    }

    /// Returns the preferred height, using JavaFX's cached measurement when width is irrelevant.
    private double preferredHeight(Node node, double width) {
        return snapSizeY(node.prefHeight(node.getContentBias() == Orientation.HORIZONTAL ? width : -1));
    }

    /// Returns whether a participating child's height depends on its width.
    private static boolean hasHorizontalBias(@Nullable Node node) {
        return isManaged(node) && node.getContentBias() == Orientation.HORIZONTAL;
    }

    /// Returns whether an optional node participates in layout.
    private static boolean isManaged(@Nullable Node node) {
        return node != null && node.isManaged();
    }

    /// Returns a node's margin, using zero insets when none is assigned.
    private static Insets margin(Node node) {
        @Nullable Insets value = getMargin(node);
        return value == null ? Insets.EMPTY : value;
    }

    /// Returns the horizontal margins occupied by a managed side node.
    private double sideMarginWidth(@Nullable Node node) {
        if (!isManaged(node))
            return 0;
        Insets margin = margin(node);
        return snapSpaceX(margin.getLeft()) + snapSpaceX(margin.getRight());
    }

    /// Returns a managed side node's snapped preferred width, or zero when absent from layout.
    private double sidePreferredWidth(@Nullable Node node) {
        return isManaged(node) ? preferredWidth(node) : 0;
    }

    /// Returns a managed side node's snapped minimum width, or zero when absent from layout.
    private double sideMinWidth(@Nullable Node node) {
        return isManaged(node) ? snapSizeX(node.minWidth(-1)) : 0;
    }

    /// Returns a side node's bounded preferred height at its allocated width.
    private double sideHeight(Node node, double width) {
        if (node.getContentBias() != Orientation.HORIZONTAL)
            width = -1;
        return snapSizeY(Math.max(node.minHeight(width), Math.min(node.prefHeight(width), node.maxHeight(width))));
    }

    /// Returns a managed side node's preferred height including its vertical margins.
    private double sideAreaHeight(@Nullable Node node, double width) {
        if (!isManaged(node))
            return 0;
        Insets margin = margin(node);
        return snapSpaceY(margin.getTop()) + sideHeight(node, width) + snapSpaceY(margin.getBottom());
    }

    /// Returns the gap between the title and its managed trailing node.
    private double trailingGap() {
        return titleLabel.isManaged() && isManaged(titleTrailing) ? snapSpaceX(titleSpacing) : 0;
    }

    /// Returns whether there are tag values to display.
    private boolean hasTags() {
        return tags != null && !tags.isEmpty();
    }

    /// Returns the gap before the first tag, if other first-row content precedes it.
    private double tagsGap() {
        return hasTags() && (titleLabel.isManaged() || isManaged(titleTrailing)) ? snapSpaceX(titleSpacing) : 0;
    }

    /// Computes the text area's natural width with every tag expanded.
    private double textPrefWidth() {
        double width = titleLabel.isManaged() ? preferredWidth(titleLabel) : 0;
        if (isManaged(titleTrailing))
            width += trailingGap() + preferredWidth(titleTrailing);
        width += tagsGap();
        if (hasTags())
            width += tagState.tagsPrefWidth();
        if (hasSubtitle())
            width = Math.max(width, preferredWidth(subtitleLabel));
        return width;
    }

    /// Computes the text area's minimum width, preserving tags before the title.
    private double textMinWidth() {
        double width = !hasTags() && titleLabel.isManaged() ? snapSizeX(titleLabel.minWidth(-1)) : 0;
        if (isManaged(titleTrailing))
            width += trailingGap() + preferredWidth(titleTrailing);
        if (hasTags())
            width += tagsGap() + tagState.tagsMinWidth();
        if (hasSubtitle())
            width = Math.max(width, snapSizeX(subtitleLabel.minWidth(-1)));
        return width;
    }

    /// Computes natural width from both side nodes, their margins, and the full text area.
    @Override
    protected double computePrefWidth(double height) {
        return snappedLeftInset() + sidePreferredWidth(getLeading()) + sideMarginWidth(getLeading())
                + textPrefWidth() + sidePreferredWidth(getTrailing()) + sideMarginWidth(getTrailing()) + snappedRightInset();
    }

    /// Computes the minimum row width from side-node and text constraints, including margins.
    @Override
    protected double computeMinWidth(double height) {
        return snappedLeftInset() + sideMinWidth(getLeading()) + sideMarginWidth(getLeading())
                + textMinWidth() + sideMinWidth(getTrailing()) + sideMarginWidth(getTrailing()) + snappedRightInset();
    }

    /// Describes horizontal allocation within the pane's insets.
    ///
    /// @param leadingWidth the leading node's width, excluding margins
    /// @param textWidth the text area's width, or -1 when unconstrained
    /// @param trailingWidth the trailing node's width, excluding margins
    @NotNullByDefault
    private record RowSize(double leadingWidth, double textWidth, double trailingWidth) {
    }

    /// Allocates side widths first, shrinking them as needed to preserve the text area's minimum width.
    private RowSize measureRow(double width) {
        double leadingWidth = sidePreferredWidth(getLeading());
        double trailingWidth = sidePreferredWidth(getTrailing());
        if (width < 0)
            return new RowSize(leadingWidth, -1, trailingWidth);

        double available = Math.max(0, width - sideMarginWidth(getLeading()) - sideMarginWidth(getTrailing()));
        double deficit = Math.max(0, leadingWidth + trailingWidth + textMinWidth() - available);
        double leadingCapacity = Math.max(0, leadingWidth - sideMinWidth(getLeading()));
        double trailingCapacity = Math.max(0, trailingWidth - sideMinWidth(getTrailing()));
        // Share the deficit, then give any unfulfilled share to the side that can still shrink.
        double leadingShrink = Math.min(leadingCapacity, deficit / 2);
        double trailingShrink = Math.min(trailingCapacity, deficit - leadingShrink);
        leadingShrink = Math.min(leadingCapacity, deficit - trailingShrink);
        leadingWidth -= leadingShrink;
        trailingWidth -= trailingShrink;
        return new RowSize(leadingWidth, Math.max(0, available - leadingWidth - trailingWidth), trailingWidth);
    }

    /// Describes the first-row dimensions at a particular available width.
    ///
    /// @param titleWidth the title label's allocated width
    /// @param trailingWidth the managed trailing node's allocated width, or zero
    /// @param visibleTags the number of expanded tags preceding the summary
    /// @param collapsedWidth the summary tag's width, or zero when every tag fits
    /// @param height the maximum preferred height of the first-row children
    @NotNullByDefault
    private record FirstLineSize(double titleWidth, double trailingWidth, int visibleTags, double collapsedWidth, double height) {
    }

    /// Reserves the trailing node and minimum tag area, then gives remaining width to the title.
    private FirstLineSize measureFirstLine(double width) {
        boolean tagged = hasTags();
        boolean hasTrailing = isManaged(titleTrailing);
        double trailingWidth = hasTrailing ? preferredWidth(titleTrailing) : 0;
        double titleWidth = titleLabel.isManaged() ? preferredWidth(titleLabel) : 0;
        double gaps = trailingGap() + tagsGap();
        if (width >= 0 && titleLabel.isManaged()) {
            double minimum = tagged ? 0 : snapSizeX(titleLabel.minWidth(-1));
            double available = Math.max(0, width - trailingWidth - gaps - (tagged ? tagState.tagsMinWidth() : 0));
            titleWidth = Math.max(minimum, Math.min(titleWidth, available));
        }
        double height = titleLabel.isManaged() && titleWidth > 0 ? preferredHeight(titleLabel, titleWidth) : 0;
        if (hasTrailing)
            height = Math.max(height, preferredHeight(titleTrailing, trailingWidth));
        if (!tagged)
            return new FirstLineSize(titleWidth, trailingWidth, 0, 0, height);

        double available = width < 0 ? Double.POSITIVE_INFINITY : width - titleWidth - trailingWidth - gaps;
        TagState.TagLayout tags = tagState.measureTags(available);
        height = Math.max(height, tagState.tagMetrics().heights()[tags.visibleTags()]);
        if (tags.collapsedWidth() > 0)
            height = Math.max(height, preferredHeight(tagState.collapsedTag, tags.collapsedWidth()));
        return new FirstLineSize(titleWidth, trailingWidth, tags.visibleTags(), tags.collapsedWidth(), height);
    }

    /// Computes preferred height using the same width allocation as layout.
    @Override
    protected double computePrefHeight(double width) {
        double contentWidth = width < 0 ? -1 : Math.max(0, width - snappedLeftInset() - snappedRightInset());
        RowSize row = measureRow(contentWidth);
        double height = measureFirstLine(row.textWidth()).height();
        if (hasSubtitle())
            height += preferredHeight(subtitleLabel, row.textWidth());
        height = Math.max(height, sideAreaHeight(getLeading(), row.leadingWidth()));
        height = Math.max(height, sideAreaHeight(getTrailing(), row.trailingWidth()));
        return snappedTopInset() + height + snappedBottomInset();
    }

    /// Keeps wrapped text from being compressed below its computed height.
    @Override
    protected double computeMinHeight(double width) {
        return computePrefHeight(width);
    }

    /// Positions side nodes around the text rows and replaces overflowing tags with a summary.
    @Override
    protected void layoutChildren() {
        double left = snappedLeftInset();
        double width = Math.max(0, getWidth() - left - snappedRightInset());
        double height = Math.max(0, getHeight() - snappedTopInset() - snappedBottomInset());
        RowSize row = measureRow(width);
        double textLeft = left + row.leadingWidth() + sideMarginWidth(getLeading());
        double x = textLeft;
        FirstLineSize first = measureFirstLine(row.textWidth());
        double subtitleHeight = hasSubtitle() ? preferredHeight(subtitleLabel, row.textWidth()) : 0;
        double extraHeight = Math.max(0, height - first.height() - subtitleHeight);
        double y = snappedTopInset() + snapPositionY(extraHeight / 2);

        if (isManaged(getLeading()))
            layoutSideNode(getLeading(), left, row.leadingWidth(), height);
        if (isManaged(getTrailing())) {
            Insets margin = margin(getTrailing());
            double trailingX = getWidth() - snappedRightInset() - row.trailingWidth()
                    - snapSpaceX(margin.getLeft()) - snapSpaceX(margin.getRight());
            layoutSideNode(getTrailing(), trailingX, row.trailingWidth(), height);
        }

        if (titleLabel.isManaged()) {
            layoutFirstLineNode(titleLabel, x, y, first.titleWidth(), first.height());
            x += first.titleWidth();
        }
        if (isManaged(titleTrailing)) {
            x += trailingGap();
            layoutFirstLineNode(titleTrailing, x, y, first.trailingWidth(), first.height());
            x += first.trailingWidth();
        }
        if (tagState != null)
            tagState.layoutTags(x + tagsGap(), y, first);
        updateMouseTransparency();
        if (hasSubtitle())
            subtitleLabel.resizeRelocate(textLeft, y + first.height(), row.textWidth(), subtitleHeight);
    }

    /// Centers a side node vertically within its margin-adjusted row area.
    private void layoutSideNode(Node node, double x, double width, double rowHeight) {
        Insets margin = margin(node);
        double height = sideHeight(node, width);
        double top = snapSpaceY(margin.getTop());
        double bottom = snapSpaceY(margin.getBottom());
        double y = snappedTopInset() + top + snapPositionY(Math.max(0, rowHeight - top - bottom - height) / 2);
        node.resizeRelocate(x + snapSpaceX(margin.getLeft()), y, width, height);
    }

    /// Vertically centers a child within the first row at its preferred height.
    private void layoutFirstLineNode(Node node, double x, double y, double width, double rowHeight) {
        double height = width > 0 ? preferredHeight(node, width) : 0;
        node.resizeRelocate(x, y + snapPositionY((rowHeight - height) / 2), width, height);
    }

    /// Labels, tooltip, and measurement caches created only for items that have used tags.
    @NotNullByDefault
    private final class TagState {

        /// The spacing between adjacent tags.
        private static final double TAG_SPACING = 4;

        /// The hover delay used by the application's fast tooltips.
        private static final Duration TAG_TOOLTIP_SHOW_DELAY = Duration.millis(50);

        /// Selects the warning appearance for rendered tags.
        private static final PseudoClass WARNING_PSEUDO_CLASS = PseudoClass.getPseudoClass("warning");

        /// The live tag values rendered by this state.
        private final ObservableList<Tag> values;

        /// The labels rendering tag values in list order.
        private final List<TagLabel> tagLabels = new ArrayList<>();

        /// The label summarizing tags that do not fit in the first row.
        private final TagLabel collapsedTag = new TagLabel("", Tag.Type.NORMAL, null);

        /// Measures summary text without changing the displayed label during size queries.
        private final Text tagMeasureText = new Text();

        /// The aggregate tag dimensions, invalidated by tag changes and label layout requests.
        private @Nullable TagMetrics tagMetrics;

        /// Summary widths measured with the current font, padding, and pixel scale.
        private final Map<Integer, Double> collapsedWidths = new HashMap<>();

        /// Invalidates summary measurements when CSS changes their geometry.
        private final InvalidationListener collapsedMetricsListener = observable -> {
            collapsedWidths.clear();
            tagLayout = null;
            requestLayout();
        };

        /// The horizontal pixel scale used by cached measurements, or zero when snapping is disabled.
        private double tagScaleX = Double.NaN;

        /// The vertical pixel scale used by cached measurements, or zero when snapping is disabled.
        private double tagScaleY = Double.NaN;

        /// The most recently computed tag allocation.
        private @Nullable TagLayout tagLayout;

        /// The available width used by the cached tag allocation.
        private double tagLayoutWidth;

        /// The number of visible tag labels in the current prefix.
        private int visibleTagCount;

        /// The number of hidden values represented by the summary text.
        private int collapsedTagCount;

        /// Whether the tooltip text must be rebuilt before its next display.
        private boolean collapsedTooltipDirty = true;

        /// Creates tag rendering state and defers tooltip allocation until the summary is hovered.
        private TagState(ObservableList<Tag> values) {
            this.values = values;
            collapsedTagCount = values.size();
            collapsedTag.setText("+" + collapsedTagCount);
            collapsedTag.setMouseTransparent(false);
            collapsedTag.getStyleClass().add("collapsed-tag");
            collapsedTag.setOnMouseEntered(event -> initializeCollapsedTooltip());
            collapsedTag.fontProperty().addListener(collapsedMetricsListener);
            collapsedTag.insetsProperty().addListener(collapsedMetricsListener);
            collapsedTag.labelPaddingProperty().addListener(collapsedMetricsListener);
            RipplerContainer.enableBackgroundMouseEvents(collapsedTag);
            getChildren().add(collapsedTag);
        }

        /// Installs the hover tooltip once, before the first mouse-move event reaches the summary.
        private void initializeCollapsedTooltip() {
            if (collapsedTag.getTooltip() != null)
                return;
            Tooltip tooltip = new Tooltip();
            tooltip.setShowDelay(TAG_TOOLTIP_SHOW_DELAY);
            tooltip.setHideDelay(Duration.ZERO);
            tooltip.setOnShowing(event -> updateCollapsedTooltip(tooltip));
            collapsedTag.setTooltip(tooltip);
        }

        /// Rebuilds the hidden-tag description only after its values or visible prefix change.
        private void updateCollapsedTooltip(Tooltip tooltip) {
            if (collapsedTooltipDirty) {
                tooltip.setText(values.subList(visibleTagCount, values.size()).stream()
                        .map(tag -> tag.text().replace('\n', ' ').replace('\r', ' '))
                        .collect(Collectors.joining("\n")));
                collapsedTooltipDirty = false;
            }
        }

        /// A tag renderer that measures CSS normally but skips skin layout while hidden.
        @NotNullByDefault
        private static final class TagLabel extends Label {
            /// The rendering state, cleared on removal and unset during superclass construction.
            private @Nullable TagState owner;

            /// Creates a hidden renderer; a null owner excludes the summary from tag-metric invalidation.
            private TagLabel(String text, Tag.Type type, @Nullable TagState owner) {
                super(text);
                getStyleClass().add("tag");
                setMinWidth(USE_PREF_SIZE);
                pseudoClassStateChanged(WARNING_PSEUDO_CLASS, type == Tag.Type.WARNING);
                setMouseTransparent(true);
                setVisible(false);
                this.owner = owner;
            }

            /// Resumes skin layout on expansion, including after text or CSS changed while hidden.
            private void setTagVisible(boolean visible) {
                if (isVisible() == visible)
                    return;
                setVisible(visible);
                if (visible)
                    super.requestLayout();
            }

            /// Avoids creating text clips for hidden, zero-width labels during the parent's layout pass.
            @Override
            protected void layoutChildren() {
                if (isVisible())
                    super.layoutChildren();
            }

            /// Invalidates aggregate dimensions even when this label was already awaiting layout.
            @Override
            public void requestLayout() {
                if (owner != null)
                    owner.invalidateTagMetrics();
                super.requestLayout();
            }
        }

        /// Invalidates tag dimensions and the allocation derived from them.
        private void invalidateTagMetrics() {
            tagMetrics = null;
            tagLayout = null;
        }

        /// Reuses labels by position, updating text and type from the first changed index onward.
        private void updateTags(int from) {
            int size = values.size();
            if (tagLabels.size() > size) {
                List<TagLabel> removed = tagLabels.subList(size, tagLabels.size());
                for (TagLabel label : removed)
                    label.owner = null;
                getChildren().removeAll(removed);
                removed.clear();
            }
            for (int i = from; i < size; i++) {
                Tag tag = values.get(i);
                if (i < tagLabels.size()) {
                    Label label = tagLabels.get(i);
                    label.setText(tag.text());
                    label.pseudoClassStateChanged(WARNING_PSEUDO_CLASS, tag.type() == Tag.Type.WARNING);
                } else {
                    TagLabel label = new TagLabel(tag.text(), tag.type(), this);
                    tagLabels.add(label);
                    getChildren().add(label);
                }
            }
            invalidateTagMetrics();
            collapsedWidths.clear();
            visibleTagCount = Math.min(visibleTagCount, size);
            collapsedTooltipDirty = true;
            collapsedTag.setTagVisible(false);
            @Nullable Tooltip tooltip = collapsedTag.getTooltip();
            if (tooltip != null) {
                tooltip.hide();
                tooltip.setText("");
            }
            updateMouseTransparency();
            requestLayout();
        }

        /// Immutable measurements for all tags with the current CSS and pixel scale.
        ///
        /// @param widths the individual preferred widths, in tag order
        /// @param heights the maximum preferred height for each prefix, starting with the empty prefix
        /// @param width the total width including spacing between tags
        @NotNullByDefault
        private record TagMetrics(double @Unmodifiable [] widths, double @Unmodifiable [] heights, double width) {
        }

        /// Invalidates snapped dimensions when pixel snapping or the window's rendering scale changes.
        private void updateTagScale() {
            @Nullable Window window = getScene() == null ? null : getScene().getWindow();
            double scaleX = isSnapToPixel() ? (window == null ? 1 : window.getRenderScaleX()) : 0;
            double scaleY = isSnapToPixel() ? (window == null ? 1 : window.getRenderScaleY()) : 0;
            if (scaleX != tagScaleX || scaleY != tagScaleY) {
                tagScaleX = scaleX;
                tagScaleY = scaleY;
                invalidateTagMetrics();
                collapsedWidths.clear();
            }
        }

        /// Measures tags once after their values, geometry, or pixel scale change.
        private TagMetrics tagMetrics() {
            updateTagScale();
            if (tagMetrics == null) {
                double[] widths = new double[tagLabels.size()];
                double[] heights = new double[tagLabels.size() + 1];
                double width = snapSpaceX(TAG_SPACING) * Math.max(0, tagLabels.size() - 1);
                for (int i = 0; i < tagLabels.size(); i++) {
                    Label label = tagLabels.get(i);
                    widths[i] = preferredWidth(label);
                    width += widths[i];
                    heights[i + 1] = Math.max(heights[i], snapSizeY(label.prefHeight(-1)));
                }
                tagMetrics = new TagMetrics(widths, heights, width);
            }
            return tagMetrics;
        }

        /// Returns the cached natural width of all expanded tags and their intervening gaps.
        private double tagsPrefWidth() {
            return tagMetrics().width();
        }

        /// Returns a cached summary width for the current font, padding, and pixel scale.
        private double collapsedTagWidth(int count) {
            updateTagScale();
            return collapsedWidths.computeIfAbsent(count, this::measureCollapsedTagWidth);
        }

        /// Measures summary text without changing the displayed label.
        private double measureCollapsedTagWidth(int count) {
            tagMeasureText.setFont(collapsedTag.getFont());
            tagMeasureText.setText("+" + count);
            Insets insets = collapsedTag.getInsets();
            Insets padding = collapsedTag.getLabelPadding();
            return snapSizeX(tagMeasureText.getLayoutBounds().getWidth())
                    + snapSpaceX(insets.getLeft()) + snapSpaceX(insets.getRight())
                    + snapSpaceX(padding.getLeft()) + snapSpaceX(padding.getRight());
        }

        /// Describes which prefix fits alongside the summary in the available tag area.
        ///
        /// @param visibleTags the number of expanded tags
        /// @param collapsedWidth the summary width, or zero when every tag fits
        @NotNullByDefault
        private record TagLayout(int visibleTags, double collapsedWidth) {
        }

        /// Allocates cached tag widths, stopping once the prefix alone exceeds the available space.
        private TagLayout measureTags(double available) {
            TagMetrics metrics = tagMetrics();
            if (tagLayout != null && available == tagLayoutWidth)
                return tagLayout;
            int visible = tagLabels.size();
            double collapsedWidth = 0;
            if (!tagLabels.isEmpty() && metrics.width() > available) {
                visible = 0;
                collapsedWidth = collapsedTagWidth(tagLabels.size());
                double prefixWidth = 0;
                for (int i = 1; i < tagLabels.size(); i++) {
                    prefixWidth += metrics.widths()[i - 1] + snapSpaceX(TAG_SPACING);
                    // Summary widths can decrease at digit boundaries; the prefix itself only grows.
                    if (prefixWidth > available)
                        break;
                    double summaryWidth = collapsedTagWidth(tagLabels.size() - i);
                    if (prefixWidth + summaryWidth <= available) {
                        visible = i;
                        collapsedWidth = summaryWidth;
                    }
                }
            }
            tagLayoutWidth = available;
            tagLayout = new TagLayout(visible, collapsedWidth);
            return tagLayout;
        }

        /// Reserves enough width for either all tags or a summary of all tags, whichever is smaller.
        private double tagsMinWidth() {
            return tagLabels.isEmpty() ? 0 : Math.min(tagsPrefWidth(), collapsedTagWidth(tagLabels.size()));
        }

        /// Positions only the visible prefix and updates the summary when its contents change.
        private void layoutTags(double x, double y, FirstLineSize first) {
            if (tagLabels.isEmpty())
                return;
            TagMetrics metrics = tagMetrics();
            if (visibleTagCount != first.visibleTags()) {
                int from = Math.min(visibleTagCount, first.visibleTags());
                int to = Math.max(visibleTagCount, first.visibleTags());
                for (int i = from; i < to; i++)
                    tagLabels.get(i).setTagVisible(i < first.visibleTags());
                visibleTagCount = first.visibleTags();
                collapsedTooltipDirty = true;
            }
            for (int i = 0; i < visibleTagCount; i++) {
                double tagWidth = metrics.widths()[i];
                layoutFirstLineNode(tagLabels.get(i), x, y, tagWidth, first.height());
                x += tagWidth + snapSpaceX(TAG_SPACING);
            }
            int hidden = tagLabels.size() - visibleTagCount;
            collapsedTag.setTagVisible(hidden > 0);
            @Nullable Tooltip tooltip = collapsedTag.getTooltip();
            if (hidden > 0) {
                if (collapsedTagCount != hidden) {
                    collapsedTagCount = hidden;
                    collapsedTag.setText("+" + hidden);
                }
                if (tooltip != null && tooltip.isShowing())
                    updateCollapsedTooltip(tooltip);
                layoutFirstLineNode(collapsedTag, x, y, first.collapsedWidth(), first.height());
            } else if (tooltip != null) {
                tooltip.hide();
            }
        }
    }
}
