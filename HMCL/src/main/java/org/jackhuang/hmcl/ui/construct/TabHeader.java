// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.construct;

import javafx.animation.*;
import javafx.beans.InvalidationListener;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
import javafx.geometry.Side;
import javafx.scene.AccessibleAttribute;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import org.jackhuang.hmcl.ui.animation.ContainerAnimations;
import org.jackhuang.hmcl.ui.animation.Motion;
import org.jackhuang.hmcl.ui.animation.TransitionPane;
import org.jackhuang.hmcl.util.javafx.MappedObservableList;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnmodifiableView;

/// Displays selectable tab headers and optionally switches content in a transition pane.
public class TabHeader extends Control implements PageAware {

    /// The optional destination for selected tab content.
    private final @Nullable TransitionPane contentPane;

    /// Creates a header with the supplied tabs and no content destination.
    ///
    /// @param tabs the initial tabs in display order, or null for an empty header
    public TabHeader(TabItem<?> @Nullable ... tabs) {
        this(null, tabs);
    }

    /// Creates a header with an optional destination for selected content.
    ///
    /// @param contentPane the destination updated by [#select(TabItem)], or null
    /// @param tabs the initial tabs in display order, or null for an empty header
    public TabHeader(@Nullable TransitionPane contentPane, TabItem<?> @Nullable ... tabs) {
        this.contentPane = contentPane;

        getStyleClass().setAll("tab-header");
        if (tabs != null) {
            getTabs().addAll(tabs);
        }
    }

    /// The mutable tabs in display order.
    private final ObservableList<TabItem<?>> tabs = FXCollections.observableArrayList();

    /// Returns the mutable, observable list of tabs in display order.
    public ObservableList<TabItem<?>> getTabs() {
        return tabs;
    }

    /// The selection model that tracks changes to the tab list.
    private final SingleSelectionModel<@Nullable TabItem<?>> selectionModel = new TabHeaderSelectionModel();

    /// Returns the selection model; selecting through it does not switch content.
    public SingleSelectionModel<@Nullable TabItem<?>> getSelectionModel() {
        return selectionModel;
    }

    /// Selects the tab and switches its content with animation when content is already displayed.
    ///
    /// @see #select(TabItem, boolean)
    public void select(TabItem<?> tab) {
        select(tab, true);
    }

    /// Hides the previous page, initializes and shows the requested page, updates the
    /// selection, and replaces the content of the configured transition pane.
    /// Page callbacks are invoked even when the requested tab is already selected.
    ///
    /// @param tab the tab to select
    /// @param playAnimation whether to animate replacement of existing content
    public void select(TabItem<?> tab, boolean playAnimation) {
        @Nullable TabItem<?> oldTab = getSelectionModel().getSelectedItem();
        if (oldTab != null) {
            if (oldTab.getNode() instanceof PageAware pageAware) {
                pageAware.onPageHidden();
            }
        }

        tab.initializeIfNeeded();
        if (tab.getNode() instanceof PageAware pageAware) {
            pageAware.onPageShown();
        }

        getSelectionModel().select(tab);

        if (contentPane != null) {
            if (playAnimation && contentPane.getCurrentNode() != null) {
                contentPane.setContent(tab.getNode(),
                        ContainerAnimations.SLIDE_UP_FADE_IN,
                        Motion.MEDIUM4,
                        Motion.EASE_IN_OUT_CUBIC_EMPHASIZED
                );
            } else {
                contentPane.setContent(tab.getNode(), ContainerAnimations.NONE);
            }
        }
    }

    /// Forwards the shown callback to the selected content when it implements [PageAware].
    @Override
    public void onPageShown() {
        @Nullable TabItem<?> tab = getSelectionModel().getSelectedItem();
        if (tab != null && tab.getNode() instanceof PageAware pageAware) {
            pageAware.onPageShown();
        }
    }

    /// Forwards the hidden callback to the selected content when it implements [PageAware].
    @Override
    public void onPageHidden() {
        @Nullable TabItem<?> tab = getSelectionModel().getSelectedItem();
        if (tab != null && tab.getNode() instanceof PageAware pageAware) {
            pageAware.onPageHidden();
        }
    }

    private @Nullable ObjectProperty<Side> side;

    /**
     * The position of the tabs.
     */
    public ObjectProperty<Side> sideProperty() {
        if (side == null)
            side = new SimpleObjectProperty<>(this, "side", Side.TOP);
        return side;
    }

    /**
     * The position to place the tabs.
     */
    public Side getSide() {
        return sideProperty().get();
    }

    /**
     * The position the place the tabs in this TabHeader.
     */
    public void setSide(Side side) {
        this.sideProperty().set(side);
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new TabHeaderSkin(this);
    }

    /// Synchronizes a header's selected index, selected item, and tab selection flags.
    ///
    /// Changes to the tab list update the selection. Selecting through this model
    /// does not initialize content or invoke page lifecycle callbacks.
    @NotNullByDefault
    private final class TabHeaderSelectionModel extends SingleSelectionModel<@Nullable TabItem<?>> {
        /// Registers a listener that keeps the selection aligned with this header's tab list.
        private TabHeaderSelectionModel() {
            // watching for changes to the items list content
            final ListChangeListener<TabItem<?>> itemsContentObserver = c -> {
                while (c.next()) {
                    for (@Nullable TabItem<?> tab : c.getRemoved()) {
                        if (tab != null && !getTabs().contains(tab)) {
                            if (tab.isSelected()) {
                                tab.setSelected(false);
                                final int tabIndex = c.getFrom();

                                // Select a nearby tab after removing the selected tab.
                                selectNearestAvailableTab(tabIndex);
                            }
                        }
                    }
                    if (c.wasAdded() || c.wasRemoved()) {
                        // The selected tab index can be out of sync with the list of tab if
                        // we add or remove tabs before the selected tab.
                        if (getSelectedIndex() != getTabs().indexOf(getSelectedItem())) {
                            clearAndSelect(getTabs().indexOf(getSelectedItem()));
                        }
                    }
                }
                if (getSelectedIndex() == -1 && getSelectedItem() == null && !getTabs().isEmpty()) {
                    // Select the first available tab when the list gains its first items.
                    selectNearestAvailableTab(0);
                } else if (getTabs().isEmpty()) {
                    clearSelection();
                }
            };
            getTabs().addListener(itemsContentObserver);
        }

        /// Selects the tab at the given index and notifies accessibility listeners.
        /// Updates the selection flags of the old and new tabs.
        @Override
        public void select(int index) {
            if (index < 0 || (getItemCount() > 0 && index >= getItemCount()) ||
                    (index == getSelectedIndex() && getModelItem(index).isSelected())) {
                return;
            }

            // Unselect the old tab
            if (getSelectedIndex() >= 0 && getSelectedIndex() < getTabs().size()) {
                getTabs().get(getSelectedIndex()).setSelected(false);
            }

            setSelectedIndex(index);

            @Nullable TabItem<?> tab = getModelItem(index);
            if (tab != null) {
                setSelectedItem(tab);
            }

            // Select the new tab
            if (getSelectedIndex() >= 0 && getSelectedIndex() < getTabs().size()) {
                getTabs().get(getSelectedIndex()).setSelected(true);
            }

            notifyAccessibleAttributeChanged(AccessibleAttribute.FOCUS_ITEM);
        }

        /// Selects a matching tab in the list. If a non-null tab is absent, updates
        /// only the selected item; a null argument leaves the selection unchanged.
        @Override
        public void select(@Nullable TabItem<?> tab) {
            final int itemCount = getItemCount();

            for (int i = 0; i < itemCount; i++) {
                final @Nullable TabItem<?> value = getModelItem(i);
                if (value != null && value.equals(tab)) {
                    select(i);
                    return;
                }
            }
            if (tab != null) {
                setSelectedItem(tab);
            }
        }

        /// Returns the tab at the given index, or null if the index is out of range.
        @Override
        protected @Nullable TabItem<?> getModelItem(int index) {
            final ObservableList<TabItem<?>> items = getTabs();
            if (index < 0 || index >= items.size()) return null;
            return items.get(index);
        }

        /// Returns the current number of tabs in the header.
        @Override
        protected int getItemCount() {
            final ObservableList<TabItem<?>> items = getTabs();
            return items.size();
        }

        /// Select the tab near a removed position, searching left before right at each distance.
        ///
        /// @param tabIndex the position from which a tab was removed
        private void selectNearestAvailableTab(int tabIndex) {
            // Search outwards from the removed position, preferring the left side.
            final int tabCount = getItemCount();
            int i = 1;
            @Nullable TabItem<?> bestTab = null;
            while (true) {
                // look leftwards
                int downPos = tabIndex - i;
                if (downPos >= 0) {
                    @Nullable TabItem<?> candidate = getModelItem(downPos);
                    if (candidate != null) {
                        bestTab = candidate;
                        break;
                    }
                }

                // look rightwards. We subtract one as we need
                // to take into account that a tab has been removed
                // and if we don't do this we'll miss the tab
                // to the right of the tab (as it has moved into
                // the removed tabs position).
                int upPos = tabIndex + i - 1;
                if (upPos < tabCount) {
                    @Nullable TabItem<?> candidate = getModelItem(upPos);
                    if (candidate != null) {
                        bestTab = candidate;
                        break;
                    }
                }

                if (downPos < 0 && upPos >= tabCount) {
                    break;
                }
                i++;
            }

            if (bestTab != null) {
                select(bestTab);
            }
        }
    }

    /// Arranges tab headers and animates the selection indicator along the chosen side.
    @NotNullByDefault
    public static class TabHeaderSkin extends SkinBase<TabHeader> {
        /// Marks the content of the selected tab for CSS styling.
        private static final PseudoClass SELECTED_PSEUDOCLASS_STATE = PseudoClass.getPseudoClass("selected");

        /// The indicator, excluded from preferred-size calculations and mouse picking.
        private final StackPane selectedTabLine = new StackPane();
        /// The tab views in the same order as the control's tabs.
        private final @UnmodifiableView ObservableList<TabHeaderView> headers;
        /// Releases removed views and updates the layout children after list changes.
        private final ListChangeListener<TabHeaderView> headersListener = change -> {
            while (change.next())
                change.getRemoved().forEach(TabHeaderView::dispose);
            updateChildren();
        };
        /// The animated distance from the leading inset along the row or column.
        private final DoubleProperty lineOffset = new SimpleDoubleProperty();
        /// The animated width or height of the indicator.
        private final DoubleProperty lineLength = new SimpleDoubleProperty();
        /// The animation currently moving the indicator, or null when stationary.
        private @Nullable Timeline timeline;
        /// Whether the next layout should animate a selection change.
        private boolean animateSelection;
        /// The side used for the preceding layout, or null before the first layout.
        private @Nullable Side previousSide;
        /// The destination offset of the current indicator animation.
        private double targetOffset;
        /// The destination length of the current indicator animation.
        private double targetLength;

        /// Creates tab views and an unmanaged indicator and observes selection and side changes.
        protected TabHeaderSkin(TabHeader control) {
            super(control);
            selectedTabLine.getStyleClass().add("tab-selected-line");
            selectedTabLine.setManaged(false);
            selectedTabLine.setMouseTransparent(true);
            selectedTabLine.setVisible(false);
            selectedTabLine.setPrefSize(2, 2);
            lineOffset.addListener(observable -> control.requestLayout());
            lineLength.addListener(observable -> control.requestLayout());

            headers = MappedObservableList.create(getSkinnable().getTabs(), TabHeaderView::new);
            headers.addListener(headersListener);
            updateChildren();

            registerChangeListener(control.getSelectionModel().selectedItemProperty(), observable -> {
                animateSelection = true;
                control.requestLayout();
            });
            registerChangeListener(control.sideProperty(), observable -> control.requestLayout());

            if (control.getSelectionModel().getSelectedItem() == null) {
                if (control.getSelectionModel().getSelectedIndex() != -1)
                    control.getSelectionModel().select(control.getSelectionModel().getSelectedIndex());
                if (control.getSelectionModel().getSelectedItem() == null)
                    control.getSelectionModel().selectFirst();
            }
        }

        /// Stops the indicator animation and releases tab bindings and listeners.
        @Override
        public void dispose() {
            stopAnimation();
            headers.removeListener(headersListener);
            headers.forEach(TabHeaderView::dispose);
            getChildren().clear();
            super.dispose();
        }

        /// Keeps the indicator above all tab views in paint order.
        private void updateChildren() {
            getChildren().setAll(headers);
            getChildren().add(selectedTabLine);
            getSkinnable().requestLayout();
        }

        /// Returns the widest tab's minimum width plus the control's horizontal insets.
        @Override
        protected double computeMinWidth(double height, double topInset, double rightInset, double bottomInset, double leftInset) {
            double width = 0;
            for (TabHeaderView child : headers) {
                if (!child.isManaged()) continue;
                width = Math.max(width, snapSizeX(child.minWidth(-1)));
            }
            return leftInset + width + rightInset;
        }

        /// Returns the tallest tab's minimum height plus the control's vertical insets.
        @Override
        protected double computeMinHeight(double width, double topInset, double rightInset, double bottomInset, double leftInset) {
            double height = 0;
            for (TabHeaderView child : headers) {
                if (!child.isManaged()) continue;
                height = Math.max(height, snapSizeY(child.minHeight(-1)));
            }
            return topInset + height + bottomInset;
        }

        /// Returns the sum of tab widths for a row, or the widest tab for a column.
        @Override
        protected double computePrefWidth(double height, double topInset, double rightInset, double bottomInset, double leftInset) {
            boolean horizontal = getSkinnable().getSide().isHorizontal();
            double width = 0;
            for (TabHeaderView child : headers) {
                if (!child.isVisible()) continue;
                double childWidth = snapSizeX(child.prefWidth(height));
                width = horizontal ? width + childWidth : Math.max(width, childWidth);
            }
            return leftInset + width + rightInset;
        }

        /// Returns the tallest tab for a row, or the sum of tab heights for a column.
        @Override
        protected double computePrefHeight(double width, double topInset, double rightInset, double bottomInset, double leftInset) {
            boolean horizontal = getSkinnable().getSide().isHorizontal();
            double height = 0;
            for (TabHeaderView child : headers) {
                if (!child.isVisible()) continue;
                double childHeight = snapSizeY(child.prefHeight(width));
                height = horizontal ? Math.max(height, childHeight) : height + childHeight;
            }
            return topInset + height + bottomInset;
        }

        /// Places tab views along the content-facing edge and updates the indicator bounds.
        @Override
        protected void layoutChildren(double contentX, double contentY, double contentWidth, double contentHeight) {
            Side side = getSkinnable().getSide();
            boolean horizontal = side.isHorizontal();
            double offset = 0;
            double selectedOffset = 0;
            double selectedLength = 0;
            @Nullable TabItem<?> selected = getSkinnable().getSelectionModel().getSelectedItem();
            for (TabHeaderView child : headers) {
                if (!child.isVisible()) continue;
                double width = snapSizeX(child.prefWidth(-1));
                double height = snapSizeY(child.prefHeight(-1));
                double x = horizontal ? contentX + offset
                        : side == Side.LEFT ? contentX + contentWidth - width : contentX;
                double y = !horizontal ? contentY + offset
                        : side == Side.TOP ? contentY + contentHeight - height : contentY;
                child.resizeRelocate(x, y, width, height);
                double length = horizontal ? width : height;
                if (child.tab == selected) {
                    selectedOffset = offset;
                    selectedLength = length;
                }
                offset += length;
            }

            updateSelectionLine(side, selectedOffset, selectedLength);
            double thickness = horizontal ? snapSizeY(selectedTabLine.prefHeight(-1))
                    : snapSizeX(selectedTabLine.prefWidth(-1));
            double x = horizontal ? contentX + lineOffset.get()
                    : side == Side.LEFT ? contentX + contentWidth - thickness : contentX;
            double y = !horizontal ? contentY + lineOffset.get()
                    : side == Side.TOP ? contentY + contentHeight - thickness : contentY;
            selectedTabLine.resizeRelocate(x, y,
                    horizontal ? lineLength.get() : thickness,
                    horizontal ? thickness : lineLength.get());
        }

        /// Animates selection changes; applies initial, resized, and reoriented bounds immediately.
        private void updateSelectionLine(Side side, double offset, double length) {
            if (animateSelection || previousSide != side || targetOffset != offset || targetLength != length) {
                boolean animate = animateSelection && previousSide == side
                        && selectedTabLine.isVisible() && length > 0;
                // Capture the displayed bounds before stopping an interrupted animation.
                double currentOffset = lineOffset.get();
                double currentLength = lineLength.get();
                stopAnimation();
                targetOffset = offset;
                targetLength = length;
                if (animate) {
                    lineOffset.set(currentOffset);
                    lineLength.set(currentLength);
                    timeline = new Timeline(
                            new KeyFrame(Duration.ZERO,
                                    new KeyValue(lineOffset, currentOffset),
                                    new KeyValue(lineLength, currentLength)),
                            new KeyFrame(Duration.seconds(side.isHorizontal() ? 0.24 : 1.24),
                                    new KeyValue(lineOffset, offset, Interpolator.EASE_BOTH),
                                    new KeyValue(lineLength, length, Interpolator.EASE_BOTH))
                    );
                    timeline.setOnFinished(event -> timeline = null);
                    timeline.play();
                } else {
                    lineOffset.set(offset);
                    lineLength.set(length);
                }
            }
            selectedTabLine.setVisible(length > 0);
            previousSide = side;
            animateSelection = false;
        }

        /// Stops and releases the active indicator animation, if any.
        private void stopAnimation() {
            if (timeline != null) {
                timeline.stop();
                timeline = null;
            }
        }

        /// Displays one tab's label, selection styling, and pointer feedback.
        @NotNullByDefault
        private final class TabHeaderView extends RipplerContainer {
            /// The tab represented by this view.
            private final TabItem<?> tab;
            /// The label bound to the tab's text.
            private final Label tabText;
            /// Updates selection styling until this view is removed or the skin is disposed.
            private final InvalidationListener selectionListener = observable -> updateSelection();

            /// Binds the label and selection styling and selects the tab when this view fires an action.
            private TabHeaderView(TabItem<?> tab) {
                super(new Label());
                this.tab = tab;
                this.tabText = (Label) getContainer();
                tabText.textProperty().bind(tab.textProperty());
                tabText.getStyleClass().add("tab-label");
                tabText.setMouseTransparent(true);
                tab.selectedProperty().addListener(selectionListener);
                updateSelection();
                setOnAction(event -> getSkinnable().getSelectionModel().select(tab));
            }

            /// Reflects the tab's selection flag in the label's pseudo-class.
            private void updateSelection() {
                tabText.pseudoClassStateChanged(SELECTED_PSEUDOCLASS_STATE, tab.isSelected());
            }

            /// Releases the tab's text binding and selection listener.
            private void dispose() {
                tabText.textProperty().unbind();
                tab.selectedProperty().removeListener(selectionListener);
            }
        }
    }
}
