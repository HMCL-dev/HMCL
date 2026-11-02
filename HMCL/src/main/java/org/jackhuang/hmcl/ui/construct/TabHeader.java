// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.construct;

import javafx.animation.*;
import javafx.beans.property.*;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
import javafx.geometry.Side;
import javafx.scene.AccessibleAttribute;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import org.jackhuang.hmcl.util.javafx.MappedObservableList;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnmodifiableView;

/// Displays selectable tab headers backed by a [TabController].
/// Activating a header delegates selection and content switching to its controller.
@NotNullByDefault
public class TabHeader extends Control {

    /// The controller supplying the tab list and selection state.
    private final TabController controller;

    /// Creates a header with its own controller and no content destination.
    ///
    /// @param tabs the initial tabs in display order, or null for an empty header
    public TabHeader(TabItem<?>... tabs) {
        this.controller = new TabController(tabs);
        getStyleClass().setAll("tab-header");
    }

    /// Returns the controller used to activate tabs and manage content lifecycle.
    public final TabController getController() {
        return controller;
    }

    /// The lazily allocated tab placement property.
    private @Nullable ObjectProperty<Side> side;

    /// Returns the tab placement property, initially [Side#TOP]. Values must be non-null.
    public ObjectProperty<Side> sideProperty() {
        if (side == null)
            side = new SimpleObjectProperty<>(this, "side", Side.TOP);
        return side;
    }

    /// Returns the side along which the tab headers are arranged.
    public Side getSide() {
        return sideProperty().get();
    }

    /// Sets the side along which the tab headers are arranged.
    ///
    /// @param side the non-null tab placement
    public void setSide(Side side) {
        sideProperty().set(side);
    }

    /// Creates the skin that lays out headers and animates the selection indicator.
    @Override
    protected Skin<?> createDefaultSkin() {
        return new TabHeaderSkin(this);
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

            headers = MappedObservableList.create(getSkinnable().controller.getTabs(), TabHeaderView::new);
            headers.addListener(headersListener);
            updateChildren();

            registerChangeListener(control.getController().selectedTabProperty(), observable -> {
                headers.forEach(TabHeaderView::updateSelection);
                control.notifyAccessibleAttributeChanged(AccessibleAttribute.FOCUS_ITEM);
                animateSelection = true;
                control.requestLayout();
            });
            registerChangeListener(control.sideProperty(), observable -> control.requestLayout());

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
            @Nullable TabItem<?> selected = getSkinnable().getController().getSelectedTab();
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

            /// Binds the label and selection styling and selects the tab when this view fires an action.
            private TabHeaderView(TabItem<?> tab) {
                super(new Label());
                this.tab = tab;
                this.tabText = (Label) getContainer();
                tabText.textProperty().bind(tab.textProperty());
                tabText.getStyleClass().add("tab-label");
                tabText.setMouseTransparent(true);
                updateSelection();
                setOnAction(event -> getSkinnable().getController().selectTab(tab));
            }

            /// Reflects the controller's selected tab in the label's pseudo-class.
            private void updateSelection() {
                tabText.pseudoClassStateChanged(SELECTED_PSEUDOCLASS_STATE,
                        getSkinnable().getController().getSelectedTab() == tab);
            }

            /// Releases the tab's text binding.
            private void dispose() {
                tabText.textProperty().unbind();
            }
        }
    }
}
