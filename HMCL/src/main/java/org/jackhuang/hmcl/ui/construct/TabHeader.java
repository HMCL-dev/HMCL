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
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.animation.ContainerAnimations;
import org.jackhuang.hmcl.ui.animation.Motion;
import org.jackhuang.hmcl.ui.animation.TransitionPane;
import org.jackhuang.hmcl.util.javafx.MappedObservableList;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnmodifiableView;

import java.util.function.Supplier;

/// Displays selectable tab headers and optionally switches content in a transition pane.
public class TabHeader extends Control implements PageAware {

    private final TransitionPane contentPane;

    public TabHeader(Tab<?>... tabs) {
        this(null, tabs);
    }

    public TabHeader(@Nullable TransitionPane contentPane, Tab<?>... tabs) {
        this.contentPane = contentPane;

        getStyleClass().setAll("tab-header");
        if (tabs != null) {
            getTabs().addAll(tabs);
        }
    }

    private final ObservableList<Tab<?>> tabs = FXCollections.observableArrayList();

    /// Returns the mutable, observable list of tabs in display order.
    public ObservableList<Tab<?>> getTabs() {
        return tabs;
    }

    /// The selection model that tracks changes to the tab list.
    private final SingleSelectionModel<Tab<?>> selectionModel = new TabHeaderSelectionModel();

    public SingleSelectionModel<Tab<?>> getSelectionModel() {
        return selectionModel;
    }

    public void select(Tab<?> tab) {
        select(tab, true);
    }

    public void select(Tab<?> tab, boolean playAnimation) {
        Tab<?> oldTab = getSelectionModel().getSelectedItem();
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

    @Override
    public void onPageShown() {
        Tab<?> tab = getSelectionModel().getSelectedItem();
        if (tab != null) {
            if (tab.getNode() instanceof PageAware) {
                ((PageAware) tab.getNode()).onPageShown();
            }
        }
    }

    @Override
    public void onPageHidden() {
        Tab<?> tab = getSelectionModel().getSelectedItem();
        if (tab != null) {
            if (tab.getNode() instanceof PageAware) {
                ((PageAware) tab.getNode()).onPageHidden();
            }
        }
    }

    private final ObjectProperty<Side> side = new SimpleObjectProperty<>(Side.TOP);

    /**
     * The position of the tabs.
     */
    public ObjectProperty<Side> sideProperty() {
        return side;
    }

    /**
     * The position to place the tabs.
     */
    public Side getSide() {
        return side.get();
    }

    /**
     * The position the place the tabs in this TabHeader.
     */
    public void setSide(Side side) {
        this.side.set(side);
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
    private final class TabHeaderSelectionModel extends SingleSelectionModel<@Nullable Tab<?>> {
        /// Registers a listener that keeps the selection aligned with this header's tab list.
        private TabHeaderSelectionModel() {
            // watching for changes to the items list content
            final ListChangeListener<Tab<?>> itemsContentObserver = c -> {
                while (c.next()) {
                    for (@Nullable Tab<?> tab : c.getRemoved()) {
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

            @Nullable Tab<?> tab = getModelItem(index);
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
        public void select(@Nullable Tab<?> tab) {
            final int itemCount = getItemCount();

            for (int i = 0; i < itemCount; i++) {
                final @Nullable Tab<?> value = getModelItem(i);
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
        protected @Nullable Tab<?> getModelItem(int index) {
            final ObservableList<Tab<?>> items = getTabs();
            if (index < 0 || index >= items.size()) return null;
            return items.get(index);
        }

        /// Returns the current number of tabs in the header.
        @Override
        protected int getItemCount() {
            final ObservableList<Tab<?>> items = getTabs();
            return items.size();
        }

        /// Select the tab near a removed position, searching left before right at each distance.
        ///
        /// @param tabIndex the position from which a tab was removed
        private void selectNearestAvailableTab(int tabIndex) {
            // Search outwards from the removed position, preferring the left side.
            final int tabCount = getItemCount();
            int i = 1;
            @Nullable Tab<?> bestTab = null;
            while (true) {
                // look leftwards
                int downPos = tabIndex - i;
                if (downPos >= 0) {
                    @Nullable Tab<?> candidate = getModelItem(downPos);
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
                    @Nullable Tab<?> candidate = getModelItem(upPos);
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

    /// Stores a tab's label, selection state, and optionally lazily created content.
    ///
    /// @param <T> the type of content node
    @NotNullByDefault
    public static final class Tab<T extends Node> {
        /// The nullable identifier of this tab.
        private final StringProperty id = new SimpleStringProperty(this, "id");
        /// The nullable text displayed in the header.
        private final StringProperty text = new SimpleStringProperty(this, "text");
        /// Whether a selection model has marked this tab as selected.
        private final ReadOnlyBooleanWrapper selected = new ReadOnlyBooleanWrapper(this, "selected");
        /// The content node, or null when no content is assigned.
        private final ObjectProperty<@Nullable T> node = new SimpleObjectProperty<>(this, "node");
        /// Optional application data associated with this tab.
        private final ObjectProperty<@Nullable Object> userData = new SimpleObjectProperty<>(this, "userData");
        /// The optional factory used when content has not been assigned.
        private @Nullable Supplier<? extends @Nullable T> nodeSupplier;

        /// Creates an unselected tab with the given nullable identifier and no text or content.
        public Tab(@Nullable String id) {
            setId(id);
        }

        /// Creates an unselected tab with the given nullable identifier and text, and no content.
        public Tab(@Nullable String id, @Nullable String text) {
            setId(id);
            setText(text);
        }

        /// Returns the content factory, or null if none has been set.
        public @Nullable Supplier<? extends @Nullable T> getNodeSupplier() {
            return nodeSupplier;
        }

        /// Sets the content factory, or clears it if null. Existing content is unchanged.
        public void setNodeSupplier(@Nullable Supplier<? extends @Nullable T> nodeSupplier) {
            this.nodeSupplier = nodeSupplier;
        }

        /// Returns the tab identifier, which may be null.
        public @Nullable String getId() {
            return id.get();
        }

        /// Returns the writable identifier property, whose value may be null.
        public StringProperty idProperty() {
            return id;
        }

        /// Sets the tab identifier, which may be null.
        public void setId(@Nullable String id) {
            this.id.set(id);
        }

        /// Returns the header text, which may be null.
        public @Nullable String getText() {
            return text.get();
        }

        /// Returns the writable header text property, whose value may be null.
        public StringProperty textProperty() {
            return text;
        }

        /// Sets the header text, which may be null.
        public void setText(@Nullable String text) {
            this.text.set(text);
        }

        /// Returns whether this tab is marked as selected.
        public boolean isSelected() {
            return selected.get();
        }

        /// Returns a read-only view of the tab's selection state.
        public ReadOnlyBooleanProperty selectedProperty() {
            return selected.getReadOnlyProperty();
        }

        /// Updates the tab's selection flag.
        private void setSelected(boolean selected) {
            this.selected.set(selected);
        }

        /// Returns the content node, or null if none is assigned; does not invoke the factory.
        public @Nullable T getNode() {
            return node.get();
        }

        /// Returns the writable content property. A null value permits lazy initialization.
        public ObjectProperty<@Nullable T> nodeProperty() {
            return node;
        }

        /// Sets the content node, or clears it if null. Does not change the content factory.
        public void setNode(@Nullable T node) {
            this.node.set(node);
        }

        /// Returns the application data, or null if none is assigned.
        public @Nullable Object getUserData() {
            return userData.get();
        }

        /// Returns the property holding nullable application data.
        public ObjectProperty<@Nullable ?> userDataProperty() {
            return userData;
        }

        /// Sets the application data, which may be null.
        public void setUserData(@Nullable Object userData) {
            this.userData.set(userData);
        }

        /// Returns whether a non-null content node is currently assigned.
        public boolean isInitialized() {
            return getNode() != null;
        }

        /// Invokes the content factory and assigns its result if the content is null
        /// and a factory is present. A null result permits another attempt on the next call.
        /// Factory exceptions propagate without assigning a result.
        ///
        /// @return true if the factory returned and its result was assigned, even if null;
        /// false if content was already assigned or no factory was present
        public boolean initializeIfNeeded() {
            if (getNode() == null) {
                if (getNodeSupplier() == null) {
                    return false;
                }
                setNode(getNodeSupplier().get());
                return true;
            }
            return false;
        }
    }

    /// Arranges tab headers and animates the selection indicator along the chosen side.
    @NotNullByDefault
    public static class TabHeaderSkin extends SkinBase<TabHeader> {
        /// Marks the content of the selected tab for CSS styling.
        private static final PseudoClass SELECTED_PSEUDOCLASS_STATE = PseudoClass.getPseudoClass("selected");

        /// The single layout container for tab views and the selection indicator.
        private final HeaderContainer header;

        /// Creates tab views and observes the control's selection and side.
        protected TabHeaderSkin(TabHeader control) {
            super(control);
            header = new HeaderContainer();
            getChildren().setAll(header);

            registerChangeListener(control.getSelectionModel().selectedItemProperty(), observable -> {
                header.animateSelection = true;
                header.requestLayout();
            });
            registerChangeListener(control.sideProperty(), observable -> header.requestLayout());

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
            header.stopAnimation();
            header.headers.removeListener(header.headersListener);
            header.headers.forEach(TabHeaderContainer::dispose);
            getChildren().clear();
            super.dispose();
        }

        /// Lays out tab views and their selection indicator along one edge.
        @NotNullByDefault
        private final class HeaderContainer extends StackPane {
            /// The indicator, excluded from preferred-size calculations and mouse picking.
            private final StackPane selectedTabLine = new StackPane();
            /// The tab views in the same order as the control's tabs.
            private final @UnmodifiableView ObservableList<TabHeaderContainer> headers;
            /// Releases removed views and updates the layout children after list changes.
            private final ListChangeListener<TabHeaderContainer> headersListener = change -> {
                while (change.next())
                    change.getRemoved().forEach(TabHeaderContainer::dispose);
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

            /// Creates tab views and an unmanaged selection indicator.
            private HeaderContainer() {
                getStyleClass().add("tab-header-area");
                setPickOnBounds(false);
                selectedTabLine.getStyleClass().add("tab-selected-line");
                selectedTabLine.setManaged(false);
                selectedTabLine.setMouseTransparent(true);
                selectedTabLine.setVisible(false);
                selectedTabLine.setPrefSize(2, 2);
                lineOffset.addListener(observable -> requestLayout());
                lineLength.addListener(observable -> requestLayout());

                headers = MappedObservableList.create(getSkinnable().getTabs(), TabHeaderContainer::new);
                headers.addListener(headersListener);
                updateChildren();
            }

            /// Keeps the indicator above all tab views in paint order.
            private void updateChildren() {
                getChildren().setAll(headers);
                getChildren().add(selectedTabLine);
                requestLayout();
            }

            /// Returns the sum of tab widths for a row, or the widest tab for a column.
            @Override
            protected double computePrefWidth(double height) {
                boolean horizontal = getSkinnable().getSide().isHorizontal();
                double width = 0;
                for (TabHeaderContainer child : headers) {
                    if (!child.isVisible()) continue;
                    double childWidth = snapSizeX(child.prefWidth(height));
                    width = horizontal ? width + childWidth : Math.max(width, childWidth);
                }
                return width + snappedLeftInset() + snappedRightInset();
            }

            /// Returns the tallest tab for a row, or the sum of tab heights for a column.
            @Override
            protected double computePrefHeight(double width) {
                boolean horizontal = getSkinnable().getSide().isHorizontal();
                double height = 0;
                for (TabHeaderContainer child : headers) {
                    if (!child.isVisible()) continue;
                    double childHeight = snapSizeY(child.prefHeight(width));
                    height = horizontal ? Math.max(height, childHeight) : height + childHeight;
                }
                return height + snappedTopInset() + snappedBottomInset();
            }

            /// Places tab views along the content-facing edge and updates the indicator bounds.
            @Override
            protected void layoutChildren() {
                Side side = getSkinnable().getSide();
                boolean horizontal = side.isHorizontal();
                double offset = 0;
                double selectedOffset = 0;
                double selectedLength = 0;
                @Nullable Tab<?> selected = getSkinnable().getSelectionModel().getSelectedItem();
                for (TabHeaderContainer child : headers) {
                    if (!child.isVisible()) continue;
                    double width = snapSizeX(child.prefWidth(-1));
                    double height = snapSizeY(child.prefHeight(-1));
                    double x = horizontal ? snappedLeftInset() + offset
                            : side == Side.LEFT ? getWidth() - snappedRightInset() - width : snappedLeftInset();
                    double y = !horizontal ? snappedTopInset() + offset
                            : side == Side.TOP ? getHeight() - snappedBottomInset() - height : snappedTopInset();
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
                double x = horizontal ? snappedLeftInset() + lineOffset.get()
                        : side == Side.LEFT ? getWidth() - snappedRightInset() - thickness : snappedLeftInset();
                double y = !horizontal ? snappedTopInset() + lineOffset.get()
                        : side == Side.TOP ? getHeight() - snappedBottomInset() - thickness : snappedTopInset();
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
        }

        /// Displays one tab's label, selection styling, and pointer feedback.
        @NotNullByDefault
        private final class TabHeaderContainer extends StackPane {
            /// The tab represented by this view.
            private final Tab<?> tab;
            /// The label bound to the tab's text.
            private final Label tabText = new Label();
            /// The content node receiving the selection pseudo-class.
            private final BorderPane inner = new BorderPane();
            /// Updates selection styling until this view is removed or the skin is disposed.
            private final InvalidationListener selectionListener = observable -> updateSelection();

            /// Binds the label and selection styling and installs pointer selection.
            private TabHeaderContainer(Tab<?> tab) {
                this.tab = tab;
                tabText.textProperty().bind(tab.textProperty());
                tabText.getStyleClass().add("tab-label");
                inner.setCenter(tabText);
                inner.getStyleClass().add("tab-container");
                inner.setMouseTransparent(true);
                getChildren().setAll(new RipplerContainer(inner));
                tab.selectedProperty().addListener(selectionListener);
                updateSelection();
                FXUtils.onClicked(this, () -> getSkinnable().getSelectionModel().select(tab));
            }

            /// Reflects the tab's selection flag in the content pseudo-class.
            private void updateSelection() {
                inner.pseudoClassStateChanged(SELECTED_PSEUDOCLASS_STATE, tab.isSelected());
            }

            /// Releases the tab's text binding and selection listener.
            private void dispose() {
                tabText.textProperty().unbind();
                tab.selectedProperty().removeListener(selectionListener);
            }
        }
    }
}
