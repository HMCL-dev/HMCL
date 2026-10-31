// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.construct;

import javafx.beans.InvalidationListener;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ObjectPropertyBase;
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
import javafx.scene.layout.Pane;
import javafx.scene.text.Text;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;

/// Displays a title, tags, and an optional subtitle between optional leading and trailing nodes.
/// Both text rows are left-aligned and vertically centered together within this pane.
/// Side nodes are vertically centered and separated from the text by 12 pixels.
/// They receive their preferred widths, shrinking toward their minimum widths when
/// necessary to accommodate the text area's minimum width. The title-trailing node receives
/// its preferred width, then the title receives up to its preferred width. Tags that do
/// not fit are replaced by a single `+N` tag with a tooltip listing the hidden values.
/// The title may shrink below its minimum width to preserve space for this tag.
/// Without tags, the title's minimum width is respected. Text and expanded tags are
/// mouse-transparent; the collapsed tag and optional nodes receive mouse input.
/// The children list is managed by this component and must not be modified directly.
@NotNullByDefault
public class TwoLineListItem extends Pane {
    /// The spacing between the text area and each managed side node.
    public static final double SIDE_SPACING = 12;

    /// The spacing between adjacent tags.
    private static final double TAG_SPACING = 8;

    /// Selects the warning appearance for rendered tags.
    private static final PseudoClass WARNING_PSEUDO_CLASS = PseudoClass.getPseudoClass("warning");

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
    private final Label lblTitle = new Label();

    /// The lazily created subtitle label, attached only while the subtitle is non-null.
    private @Nullable Label lblSubtitle;

    /// The optional node immediately following the title.
    private @Nullable Node titleTrailing;

    /// The optional leading-node property, allocated when first requested.
    private @Nullable ObjectProperty<@Nullable Node> leading;

    /// The optional trailing-node property, allocated when first requested.
    private @Nullable ObjectProperty<@Nullable Node> trailing;

    /// Observes side-node visibility and layout participation.
    private final InvalidationListener sideNodeListener = observable -> updateSideNodes();

    /// Observes trailing-node visibility and layout participation.
    private final InvalidationListener titleTrailingListener = observable -> updateTitleTrailing();

    /// The gap after the title and its trailing node; tag-to-tag spacing is independent.
    private double titleSpacing;

    /// The mutable tag values, allocated when first requested.
    private @Nullable ObservableList<Tag> tags;

    /// The labels rendering tag values in list order.
    private final List<Label> tagLabels = new ArrayList<>();

    /// The lazily created label summarizing tags that do not fit in the first row.
    private @Nullable Label collapsedTag;

    /// Measures summary text without changing the displayed label during size queries.
    private final Text tagMeasureText = new Text();

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

    /// Returns the leading-node property, initially null.
    /// Null removes the node. Nodes that are both invisible and unmanaged are detached
    /// until either property becomes true. Only managed nodes occupy layout space.
    /// A node must not simultaneously occupy another slot in this item.
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
    /// Null removes the node. Nodes that are both invisible and unmanaged are detached
    /// until either property becomes true. Only managed nodes occupy layout space.
    /// A node must not simultaneously occupy another slot in this item.
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
                if (previous != null) {
                    previous.visibleProperty().removeListener(sideNodeListener);
                    previous.managedProperty().removeListener(sideNodeListener);
                    getChildren().remove(previous);
                }
                previous = get();
                if (previous != null) {
                    previous.visibleProperty().addListener(sideNodeListener);
                    previous.managedProperty().addListener(sideNodeListener);
                }
                updateSideNodes();
            }
        };
    }

    /// Attaches side nodes that need rendering or layout and updates input participation.
    private void updateSideNodes() {
        updateSideNode(getLeading());
        updateSideNode(getTrailing());
        updateMouseTransparency();
        requestLayout();
    }

    /// Attaches a side node unless it is both invisible and unmanaged.
    private void updateSideNode(@Nullable Node node) {
        if (node == null)
            return;
        if (node.isVisible() || node.isManaged()) {
            if (node.getParent() != this)
                getChildren().add(node);
        } else {
            getChildren().remove(node);
        }
    }

    /// Allows optional nodes and the collapsed tag to receive input while text remains mouse-transparent.
    private void updateMouseTransparency() {
        setMouseTransparent(!isVisible(getLeading()) && !isVisible(getTrailing())
                && !isVisible(titleTrailing) && !isVisible(collapsedTag));
    }

    /// Returns whether an optional node is visible.
    private static boolean isVisible(@Nullable Node node) {
        return node != null && node.isVisible();
    }

    /// Sets the node displayed immediately after the title, or removes it if null.
    /// A node that is both invisible and unmanaged is detached until either property
    /// becomes true. Assigning a visible node enables input for that node while the
    /// text and expanded tags remain mouse-transparent.
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
        updateMouseTransparency();
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
    /// is attached to a showing scene. Tags are displayed in order; a suffix that does not
    /// fit is replaced by a `+N` tag whose tooltip lists the hidden values, one per line.
    public ObservableList<Tag> getTags() {
        if (tags == null) {
            tags = FXCollections.observableArrayList();
            tags.addListener((ListChangeListener<Tag>) change -> {
                @Unmodifiable List<Label> labels = change.getList().stream().map(TwoLineListItem::createTagLabel).toList();
                getChildren().removeAll(tagLabels);
                tagLabels.clear();
                tagLabels.addAll(labels);
                getChildren().addAll(tagLabels);
                if (collapsedTag == null && !labels.isEmpty()) {
                    collapsedTag = createTagLabel(new Tag("+" + labels.size(), Tag.Type.NORMAL));
                    collapsedTag.getStyleClass().add("collapsed-tag");
                    collapsedTag.setMouseTransparent(false);
                    collapsedTag.setTooltip(new Tooltip());
                    RipplerContainer.enableBackgroundMouseEvents(collapsedTag);
                    getChildren().add(collapsedTag);
                }
                if (collapsedTag != null) {
                    collapsedTag.setVisible(false);
                    collapsedTag.getTooltip().hide();
                }
                updateMouseTransparency();
                requestLayout();
            });
        }
        return tags;
    }

    /// Creates a mouse-transparent tag label displayed at its full preferred width.
    private static Label createTagLabel(Tag tag) {
        var label = new Label(tag.text());
        label.getStyleClass().add("tag");
        label.setMinWidth(USE_PREF_SIZE);
        label.setMouseTransparent(true);
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

    /// Returns whether an optional node participates in layout.
    private static boolean isManaged(@Nullable Node node) {
        return node != null && node.isManaged();
    }

    /// Returns a node's margin, using zero insets when none is assigned.
    private static Insets margin(Node node) {
        @Nullable Insets value = getMargin(node);
        return value == null ? Insets.EMPTY : value;
    }

    /// Returns the horizontal margins and text gap occupied by a managed side node.
    private double sideSpacing(@Nullable Node node) {
        if (!isManaged(node))
            return 0;
        Insets margin = margin(node);
        return snapSpaceX(margin.getLeft()) + snapSpaceX(margin.getRight()) + snapSpaceX(SIDE_SPACING);
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
        return lblTitle.isManaged() && hasManagedTrailing() ? snapSpaceX(titleSpacing) : 0;
    }

    /// Returns the gap before the first tag, if other first-row content precedes it.
    private double tagsGap() {
        return !tagLabels.isEmpty() && (lblTitle.isManaged() || hasManagedTrailing()) ? snapSpaceX(titleSpacing) : 0;
    }

    /// Computes the text area's natural width with every tag expanded.
    private double textPrefWidth() {
        double width = lblTitle.isManaged() ? preferredWidth(lblTitle) : 0;
        if (hasManagedTrailing())
            width += trailingGap() + preferredWidth(titleTrailing);
        width += tagsGap();
        width += tagsPrefWidth();
        if (hasSubtitle())
            width = Math.max(width, preferredWidth(lblSubtitle));
        return width;
    }

    /// Returns the natural width of all expanded tags and their intervening gaps.
    private double tagsPrefWidth() {
        double width = snapSpaceX(TAG_SPACING) * Math.max(0, tagLabels.size() - 1);
        for (Label label : tagLabels)
            width += preferredWidth(label);
        return width;
    }

    /// Measures a summary using its current font, label padding, and region insets.
    private double collapsedTagWidth(int count) {
        if (collapsedTag == null)
            return 0;
        tagMeasureText.setFont(collapsedTag.getFont());
        tagMeasureText.setText("+" + count);
        Insets insets = collapsedTag.getInsets();
        Insets padding = collapsedTag.getLabelPadding();
        return snapSizeX(tagMeasureText.getLayoutBounds().getWidth())
                + snapSpaceX(insets.getLeft()) + snapSpaceX(insets.getRight())
                + snapSpaceX(padding.getLeft()) + snapSpaceX(padding.getRight());
    }

    /// Reserves enough width for either all tags or a summary of all tags, whichever is smaller.
    private double tagsMinWidth() {
        return tagLabels.isEmpty() ? 0 : Math.min(tagsPrefWidth(), collapsedTagWidth(tagLabels.size()));
    }

    /// Computes the text area's minimum width, preserving tags before the title.
    private double textMinWidth() {
        double width = tagLabels.isEmpty() && lblTitle.isManaged() ? snapSizeX(lblTitle.minWidth(-1)) : 0;
        if (hasManagedTrailing())
            width += trailingGap() + preferredWidth(titleTrailing);
        width += tagsGap() + tagsMinWidth();
        if (hasSubtitle())
            width = Math.max(width, snapSizeX(lblSubtitle.minWidth(-1)));
        return width;
    }

    /// Computes natural width from both side nodes, their margins, and the full text area.
    @Override
    protected double computePrefWidth(double height) {
        return snappedLeftInset() + sidePreferredWidth(getLeading()) + sideSpacing(getLeading())
                + textPrefWidth() + sidePreferredWidth(getTrailing()) + sideSpacing(getTrailing()) + snappedRightInset();
    }

    /// Computes the minimum row width from side-node and text constraints, including margins.
    @Override
    protected double computeMinWidth(double height) {
        return snappedLeftInset() + sideMinWidth(getLeading()) + sideSpacing(getLeading())
                + textMinWidth() + sideMinWidth(getTrailing()) + sideSpacing(getTrailing()) + snappedRightInset();
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

        double available = Math.max(0, width - sideSpacing(getLeading()) - sideSpacing(getTrailing()));
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
        double trailingWidth = hasManagedTrailing() ? preferredWidth(titleTrailing) : 0;
        double titleWidth = lblTitle.isManaged() ? preferredWidth(lblTitle) : 0;
        if (width >= 0 && lblTitle.isManaged()) {
            double minimum = tagLabels.isEmpty() ? snapSizeX(lblTitle.minWidth(-1)) : 0;
            titleWidth = Math.max(minimum, Math.min(titleWidth,
                    Math.max(0, width - trailingWidth - trailingGap() - tagsGap() - tagsMinWidth())));
        }
        int visibleTags = tagLabels.size();
        double collapsedWidth = 0;
        double available = width - titleWidth - trailingWidth - trailingGap() - tagsGap();
        if (width >= 0 && !tagLabels.isEmpty() && tagsPrefWidth() > available) {
            visibleTags = 0;
            collapsedWidth = collapsedTagWidth(tagLabels.size());
            double prefixWidth = 0;
            for (int i = 1; i < tagLabels.size(); i++) {
                prefixWidth += preferredWidth(tagLabels.get(i - 1)) + snapSpaceX(TAG_SPACING);
                double summaryWidth = collapsedTagWidth(tagLabels.size() - i);
                if (prefixWidth + summaryWidth <= available) {
                    visibleTags = i;
                    collapsedWidth = summaryWidth;
                }
            }
        }
        double height = lblTitle.isManaged() && titleWidth > 0 ? snapSizeY(lblTitle.prefHeight(titleWidth)) : 0;
        if (hasManagedTrailing())
            height = Math.max(height, snapSizeY(titleTrailing.prefHeight(trailingWidth)));
        for (int i = 0; i < visibleTags; i++)
            height = Math.max(height, snapSizeY(tagLabels.get(i).prefHeight(-1)));
        if (collapsedWidth > 0)
            height = Math.max(height, snapSizeY(collapsedTag.prefHeight(collapsedWidth)));
        return new FirstLineSize(titleWidth, trailingWidth, visibleTags, collapsedWidth, height);
    }

    /// Computes preferred height using the same width allocation as layout.
    @Override
    protected double computePrefHeight(double width) {
        double contentWidth = width < 0 ? -1 : Math.max(0, width - snappedLeftInset() - snappedRightInset());
        RowSize row = measureRow(contentWidth);
        double height = measureFirstLine(row.textWidth()).height();
        if (hasSubtitle())
            height += snapSizeY(lblSubtitle.prefHeight(row.textWidth()));
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
        double textLeft = left + row.leadingWidth() + sideSpacing(getLeading());
        double x = textLeft;
        FirstLineSize first = measureFirstLine(row.textWidth());
        double subtitleHeight = hasSubtitle() ? snapSizeY(lblSubtitle.prefHeight(row.textWidth())) : 0;
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
        for (int i = 0; i < tagLabels.size(); i++) {
            Label label = tagLabels.get(i);
            label.setVisible(i < first.visibleTags());
            if (label.isVisible()) {
                double tagWidth = preferredWidth(label);
                layoutFirstLineNode(label, x, y, tagWidth, first.height());
                x += tagWidth + snapSpaceX(TAG_SPACING);
            }
        }
        if (collapsedTag != null) {
            collapsedTag.setVisible(first.visibleTags() < tagLabels.size());
            if (collapsedTag.isVisible()) {
                collapsedTag.setText("+" + (tagLabels.size() - first.visibleTags()));
                collapsedTag.getTooltip().setText(tagLabels.subList(first.visibleTags(), tagLabels.size()).stream()
                        .map(label -> "- " + label.getText().replace('\n', ' ').replace('\r', ' '))
                        .collect(Collectors.joining("\n")));
                layoutFirstLineNode(collapsedTag, x, y, first.collapsedWidth(), first.height());
            } else {
                collapsedTag.getTooltip().hide();
            }
        }
        updateMouseTransparency();
        if (hasSubtitle())
            lblSubtitle.resizeRelocate(textLeft, y + first.height(), row.textWidth(), subtitleHeight);
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
        double height = width > 0 ? snapSizeY(node.prefHeight(width)) : 0;
        node.resizeRelocate(x, y + snapPositionY((rowHeight - height) / 2), width, height);
    }

    /// Returns the current text and tag values for diagnostics.
    @Override
    public String toString() {
        return "TwoLineListItem[title=%s, subtitle=%s, tags=%s]".formatted(getTitle(), getSubtitle(), tags);
    }
}
