// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.construct;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
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
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Scale;
import javafx.util.Duration;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.animation.ContainerAnimations;
import org.jackhuang.hmcl.ui.animation.Motion;
import org.jackhuang.hmcl.ui.animation.TransitionPane;
import org.jackhuang.hmcl.util.javafx.MappedObservableList;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/// Displays selectable tab headers and optionally switches content in a transition pane.
@SuppressWarnings("deprecation")
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
    private final SingleSelectionModel<Tab<?>> selectionModel = new TabHeaderSelectionModel(this);

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
    public static class TabHeaderSelectionModel extends SingleSelectionModel<Tab<?>> {
        /// The header whose tab list supplies this model's items.
        private final TabHeader tabHeader;

        /// Creates a selection model and registers a listener on the header's tab list.
        ///
        /// @param t the header to observe
        /// @throws NullPointerException if the header is null
        public TabHeaderSelectionModel(final TabHeader t) {
            if (t == null) {
                throw new NullPointerException("TabHeader cannot be null");
            }
            this.tabHeader = t;

            // watching for changes to the items list content
            final ListChangeListener<Tab<?>> itemsContentObserver = c -> {
                while (c.next()) {
                    for (@Nullable Tab<?> tab : c.getRemoved()) {
                        if (tab != null && !tabHeader.getTabs().contains(tab)) {
                            if (tab.isSelected()) {
                                tab.setSelected(false);
                                final int tabIndex = c.getFrom();

                                // Select a nearby tab after removing the selected tab.
                                findNearestAvailableTab(tabIndex, true);
                            }
                        }
                    }
                    if (c.wasAdded() || c.wasRemoved()) {
                        // The selected tab index can be out of sync with the list of tab if
                        // we add or remove tabs before the selected tab.
                        if (getSelectedIndex() != tabHeader.getTabs().indexOf(getSelectedItem())) {
                            clearAndSelect(tabHeader.getTabs().indexOf(getSelectedItem()));
                        }
                    }
                }
                if (getSelectedIndex() == -1 && getSelectedItem() == null && tabHeader.getTabs().size() > 0) {
                    // Select the first available tab when the list gains its first items.
                    findNearestAvailableTab(0, true);
                } else if (tabHeader.getTabs().isEmpty()) {
                    clearSelection();
                }
            };
            this.tabHeader.getTabs().addListener(itemsContentObserver);
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
            if (getSelectedIndex() >= 0 && getSelectedIndex() < tabHeader.getTabs().size()) {
                tabHeader.getTabs().get(getSelectedIndex()).setSelected(false);
            }

            setSelectedIndex(index);

            @Nullable Tab<?> tab = getModelItem(index);
            if (tab != null) {
                setSelectedItem(tab);
            }

            // Select the new tab
            if (getSelectedIndex() >= 0 && getSelectedIndex() < tabHeader.getTabs().size()) {
                tabHeader.getTabs().get(getSelectedIndex()).setSelected(true);
            }

            tabHeader.notifyAccessibleAttributeChanged(AccessibleAttribute.FOCUS_ITEM);
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
            final ObservableList<Tab<?>> items = tabHeader.getTabs();
            if (index < 0 || index >= items.size()) return null;
            return items.get(index);
        }

        /// Returns the current number of tabs in the header.
        @Override
        protected int getItemCount() {
            final ObservableList<Tab<?>> items = tabHeader.getTabs();
            return items.size();
        }

        /// Finds a tab near a removed position, searching left before right at each distance.
        ///
        /// @param tabIndex the position from which a tab was removed
        /// @param doSelect whether to select the tab found
        /// @return the nearest non-null tab, or null if none is available
        private @Nullable Tab<?> findNearestAvailableTab(int tabIndex, boolean doSelect) {
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

            if (doSelect && bestTab != null) {
                select(bestTab);
            }

            return bestTab;
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

    public static class TabHeaderSkin extends SkinBase<TabHeader> {

        private static final PseudoClass SELECTED_PSEUDOCLASS_STATE = PseudoClass.getPseudoClass("selected");

        private final HeaderContainer header;
        private boolean isSelectingTab = false;
        private Tab<?> selectedTab;

        protected TabHeaderSkin(TabHeader control) {
            super(control);

            header = new HeaderContainer();
            getChildren().setAll(header);

            FXUtils.onChangeAndOperate(control.getSelectionModel().selectedItemProperty(), item -> {
                isSelectingTab = true;
                selectedTab = item;
                Platform.runLater(() -> {
                    header.setNeedsLayout2(true);
                    header.layout();
                });
            });

            this.selectedTab = control.getSelectionModel().getSelectedItem();
            if (this.selectedTab == null && control.getSelectionModel().getSelectedIndex() != -1) {
                control.getSelectionModel().select(control.getSelectionModel().getSelectedIndex());
                this.selectedTab = control.getSelectionModel().getSelectedItem();
            }

            if (this.selectedTab == null) {
                control.getSelectionModel().selectFirst();
            }

            this.selectedTab = control.getSelectionModel().getSelectedItem();
        }

        protected final class HeaderContainer extends StackPane {
            private Timeline timeline;
            private final StackPane selectedTabLine;
            private final HeadersRegion headersRegion;
            private final Scale scale = new Scale(1, 1, 0, 0);
            private final Rotate rotate = new Rotate(0, 0, 1);
            @SuppressWarnings({"FieldCanBeLocal", "unused"})
            private final ObservableList<Node> binding;

            public HeaderContainer() {
                getStyleClass().add("tab-header-area");
                setPickOnBounds(false);

                headersRegion = new HeadersRegion();
                headersRegion.sideProperty().bind(getSkinnable().sideProperty());

                selectedTabLine = new StackPane();
                selectedTabLine.setManaged(false);
                selectedTabLine.getTransforms().addAll(scale, rotate);
                selectedTabLine.setCache(true);
                selectedTabLine.getStyleClass().addAll("tab-selected-line");
                selectedTabLine.setPrefHeight(2);
                selectedTabLine.setPrefWidth(2);
                getChildren().setAll(headersRegion, selectedTabLine);
                headersRegion.setPickOnBounds(false);
                headersRegion.prefHeightProperty().bind(heightProperty());
                rotate.pivotXProperty().bind(Bindings.createDoubleBinding(() -> getSkinnable().getSide().isHorizontal() ? 0.0 : 1, getSkinnable().sideProperty()));
                rotate.pivotYProperty().bind(Bindings.createDoubleBinding(() -> getSkinnable().getSide().isHorizontal() ? 1.0 : 0, getSkinnable().sideProperty()));

                Bindings.bindContent(headersRegion.getChildren(), binding = MappedObservableList.create(getSkinnable().getTabs(), tab -> {
                    TabHeaderContainer container = new TabHeaderContainer(tab);
                    container.setVisible(true);
                    return container;
                }));
            }

            public void setNeedsLayout2(boolean value) {
                setNeedsLayout(value);
            }

            private boolean isAnimating() {
                return this.timeline != null && this.timeline.getStatus() == Animation.Status.RUNNING;
            }

            @Override
            protected void layoutChildren() {
                super.layoutChildren();

                if (isSelectingTab) {
                    headersRegion.animateSelectionLine();
                    isSelectingTab = false;
                }
            }

            private final class HeadersRegion extends StackPane {
                private SideAction action;
                private final ObjectProperty<Side> side = new SimpleObjectProperty<Side>() {
                    @Override
                    protected void invalidated() {
                        super.invalidated();

                        action = switch (get()) {
                            case TOP -> new Top();
                            case BOTTOM -> new Bottom();
                            case LEFT -> new Left();
                            case RIGHT -> new Right();
                        };
                    }
                };

                public Side getSide() {
                    return side.get();
                }

                public ObjectProperty<Side> sideProperty() {
                    return side;
                }

                public void setSide(Side side) {
                    this.side.set(side);
                }

                @Override
                protected double computePrefWidth(double height) {
                    return action.computePrefWidth(height);
                }

                @Override
                protected double computePrefHeight(double width) {
                    return action.computePrefHeight(width);
                }

                @Override
                protected void layoutChildren() {
                    action.layoutChildren();
                }

                private void animateSelectionLine() {
                    action.animateSelectionLine();
                }

                private abstract class SideAction {
                    abstract double computePrefWidth(double height);

                    abstract double computePrefHeight(double width);

                    void layoutChildren() {
                        if (isSelectingTab) {
                            animateSelectionLine();
                            isSelectingTab = false;
                        }
                    }

                    abstract void animateSelectionLine();
                }

                private abstract class Horizontal extends SideAction {
                    @Override
                    public double computePrefWidth(double height) {
                        double width = 0;
                        for (Node child : getChildren()) {
                            if (!(child instanceof TabHeaderContainer) || !child.isVisible()) continue;
                            width += child.prefWidth(height);
                        }
                        return snapSize(width) + snappedLeftInset() + snappedRightInset();
                    }

                    @Override
                    public double computePrefHeight(double width) {
                        double height = 0;
                        for (Node child : getChildren()) {
                            if (!(child instanceof TabHeaderContainer) || !child.isVisible()) continue;
                            height = Math.max(height, child.prefHeight(width));
                        }
                        return snapSize(height) + snappedTopInset() + snappedBottomInset();
                    }

                    private void runTimeline(double newTransX, double newWidth) {
                        double lineWidth = selectedTabLine.prefWidth(-1.0D);
                        if (isAnimating()) {
                            timeline.stop();
                            double tempScaleX = scale.getX();
                            if (rotate.getAngle() != 0.0D) {
                                rotate.setAngle(0.0D);
                                double tempWidth = tempScaleX * lineWidth;
                                selectedTabLine.setTranslateX(selectedTabLine.getTranslateX() - tempWidth);
                            }
                        }

                        double oldScaleX = scale.getX();
                        double oldWidth = lineWidth * oldScaleX;
                        double oldTransX = selectedTabLine.getTranslateX();
                        double newScaleX = newWidth * oldScaleX / oldWidth;
                        // newTransX += offsetStart * (double)this.direction;
                        double transDiff = newTransX - oldTransX;
                        if (transDiff < 0.0D) {
                            selectedTabLine.setTranslateX(selectedTabLine.getTranslateX() + oldWidth);
                            newTransX += newWidth;
                            rotate.setAngle(180.0D);
                        }

                        timeline = new Timeline(
                                new KeyFrame(
                                        Duration.ZERO,
                                        new KeyValue(selectedTabLine.translateXProperty(), selectedTabLine.getTranslateX(), Interpolator.EASE_BOTH)
                                ),
                                new KeyFrame(
                                        Duration.seconds(0.24D),
                                        new KeyValue(scale.xProperty(), newScaleX, Interpolator.EASE_BOTH),
                                        new KeyValue(selectedTabLine.translateXProperty(), newTransX, Interpolator.EASE_BOTH)
                                )
                        );
                        timeline.setOnFinished((finish) -> {
                            if (rotate.getAngle() != 0.0D) {
                                rotate.setAngle(0.0D);
                                selectedTabLine.setTranslateX(selectedTabLine.getTranslateX() - newWidth);
                            }

                        });
                        timeline.play();
                    }

                    @Override
                    public void animateSelectionLine() {
                        double offset = 0.0D;
                        double selectedTabOffset = 0.0D;
                        double selectedTabWidth = 0.0D;

                        for (Node node : headersRegion.getChildren()) {
                            if (node instanceof TabHeaderContainer) {
                                TabHeaderContainer tabHeader = (TabHeaderContainer) node;
                                double tabHeaderPrefWidth = snapSize(tabHeader.prefWidth(-1.0D));
                                if (selectedTab != null && selectedTab.equals(tabHeader.tab)) {
                                    selectedTabOffset = offset;
                                    selectedTabWidth = tabHeaderPrefWidth;
                                    break;
                                }

                                offset += tabHeaderPrefWidth;
                            }
                        }

                        this.runTimeline(selectedTabOffset, selectedTabWidth);
                    }
                }

                private final class Top extends Horizontal {
                    @Override
                    public void layoutChildren() {
                        super.layoutChildren();

                        double headerHeight = snapSize(prefHeight(-1));
                        double tabStartX = 0;
                        for (Node node : getChildren()) {
                            if (!(node instanceof TabHeaderContainer)) continue;
                            TabHeaderContainer child = (TabHeaderContainer) node;
                            double w = snapSize(child.prefWidth(-1));
                            double h = snapSize(child.prefHeight(-1));
                            child.resize(w, h);

                            child.relocate(tabStartX, headerHeight - h - snappedBottomInset());
                            tabStartX += w;
                        }

                        selectedTabLine.resizeRelocate(0,
                                headerHeight - selectedTabLine.prefHeight(-1),
                                snapSize(selectedTabLine.prefWidth(-1)),
                                snapSize(selectedTabLine.prefHeight(-1)));
                    }
                }

                private final class Bottom extends Horizontal {
                    @Override
                    public void layoutChildren() {
                        super.layoutChildren();

                        double headerHeight = snapSize(prefHeight(-1));
                        double tabStartX = 0;
                        for (Node node : getChildren()) {
                            if (!(node instanceof TabHeaderContainer)) continue;
                            TabHeaderContainer child = (TabHeaderContainer) node;
                            double w = snapSize(child.prefWidth(-1));
                            double h = snapSize(child.prefHeight(-1));
                            child.resize(w, h);

                            child.relocate(tabStartX, snappedTopInset());
                            tabStartX += w;
                        }

                        selectedTabLine.resizeRelocate(0, 0,
                                snapSize(selectedTabLine.prefWidth(-1)),
                                snapSize(selectedTabLine.prefHeight(-1)));
                    }
                }

                private abstract class Vertical extends SideAction {
                    @Override
                    public double computePrefWidth(double height) {
                        double width = 0;
                        for (Node child : getChildren()) {
                            if (!(child instanceof TabHeaderContainer) || !child.isVisible()) continue;
                            width = Math.max(width, child.prefWidth(height));
                        }
                        return snapSize(width) + snappedLeftInset() + snappedRightInset();
                    }

                    @Override
                    public double computePrefHeight(double width) {
                        double height = 0;
                        for (Node child : getChildren()) {
                            if (!(child instanceof TabHeaderContainer) || !child.isVisible()) continue;
                            height += child.prefHeight(width);
                        }
                        return snapSize(height) + snappedTopInset() + snappedBottomInset();
                    }

                    private void runTimeline(double newTransY, double newHeight) {
                        double lineHeight = selectedTabLine.prefHeight(-1.0D);
                        if (isAnimating()) {
                            timeline.stop();
                            double tempScaleY = scale.getY();
                            if (rotate.getAngle() != 0.0D) {
                                rotate.setAngle(0.0D);
                                double tempHeight = tempScaleY * lineHeight;
                                selectedTabLine.setTranslateY(selectedTabLine.getTranslateY() - tempHeight);
                            }
                        }

                        double oldScaleY = scale.getY();
                        double oldHeight = lineHeight * oldScaleY;
                        double oldTransY = selectedTabLine.getTranslateY();
                        double newScaleY = newHeight * oldScaleY / oldHeight;
                        // newTransY += offsetStart * (double)this.direction;
                        double transDiff = newTransY - oldTransY;
                        if (transDiff < 0.0D) {
                            selectedTabLine.setTranslateY(selectedTabLine.getTranslateY() + oldHeight);
                            newTransY += newHeight;
                            rotate.setAngle(180.0D);
                        }

                        timeline = new Timeline(
                                new KeyFrame(
                                        Duration.ZERO,
                                        new KeyValue(selectedTabLine.translateYProperty(), selectedTabLine.getTranslateY(), Interpolator.EASE_BOTH)
                                ),
                                new KeyFrame(
                                        Duration.seconds(1.24D),
                                        new KeyValue(scale.yProperty(), newScaleY, Interpolator.EASE_BOTH),
                                        new KeyValue(selectedTabLine.translateYProperty(), newTransY, Interpolator.EASE_BOTH)
                                )
                        );
                        timeline.setOnFinished((finish) -> {
                            if (rotate.getAngle() != 0.0D) {
                                rotate.setAngle(0.0D);
                                selectedTabLine.setTranslateY(selectedTabLine.getTranslateY() - newHeight);
                            }

                        });
                        timeline.play();
                    }

                    @Override
                    public void animateSelectionLine() {
                        double offset = 0.0D;
                        double selectedTabOffset = 0.0D;
                        double selectedTabHeight = 0.0D;

                        for (Node node : headersRegion.getChildren()) {
                            if (node instanceof TabHeaderContainer) {
                                TabHeaderContainer tabHeader = (TabHeaderContainer) node;
                                double tabHeaderPrefHeight = snapSize(tabHeader.prefHeight(-1.0D));
                                if (selectedTab != null && selectedTab.equals(tabHeader.tab)) {
                                    selectedTabOffset = offset;
                                    selectedTabHeight = tabHeaderPrefHeight;
                                    break;
                                }

                                offset += tabHeaderPrefHeight;
                            }
                        }

                        this.runTimeline(selectedTabOffset, selectedTabHeight);
                    }
                }

                private final class Left extends Vertical {
                    @Override
                    public void layoutChildren() {
                        super.layoutChildren();

                        double headerWidth = snapSize(prefWidth(-1));
                        double tabStartY = 0;
                        for (Node node : getChildren()) {
                            if (!(node instanceof TabHeaderContainer)) continue;
                            TabHeaderContainer child = (TabHeaderContainer) node;
                            double w = snapSize(child.prefWidth(-1));
                            double h = snapSize(child.prefHeight(-1));
                            child.resize(w, h);

                            child.relocate(headerWidth - w - snappedRightInset(), tabStartY);
                            tabStartY += h;
                        }

                        selectedTabLine.resizeRelocate(headerWidth - selectedTabLine.prefWidth(-1), 0,
                                snapSize(selectedTabLine.prefWidth(-1)),
                                snapSize(selectedTabLine.prefHeight(-1)));
                    }
                }

                private final class Right extends Vertical {
                    @Override
                    public void layoutChildren() {
                        super.layoutChildren();

                        double headerWidth = snapSize(prefWidth(-1));
                        double tabStartY = 0;
                        for (Node node : getChildren()) {
                            if (!(node instanceof TabHeaderContainer)) continue;
                            TabHeaderContainer child = (TabHeaderContainer) node;
                            double w = snapSize(child.prefWidth(-1));
                            double h = snapSize(child.prefHeight(-1));
                            child.resize(w, h);

                            child.relocate(snappedLeftInset(), tabStartY);
                            tabStartY += h;
                        }

                        selectedTabLine.resizeRelocate(0, 0,
                                snapSize(selectedTabLine.prefWidth(-1)),
                                snapSize(selectedTabLine.prefHeight(-1)));
                    }
                }

            }
        }

        protected class TabHeaderContainer extends StackPane {

            private final Tab<?> tab;
            private final Label tabText;
            private final BorderPane inner;

            public TabHeaderContainer(Tab<?> tab) {
                this.tab = tab;

                tabText = new Label();
                tabText.textProperty().bind(tab.textProperty());
                tabText.getStyleClass().add("tab-label");
                inner = new BorderPane();
                inner.setCenter(tabText);
                inner.getStyleClass().add("tab-container");
                inner.setMouseTransparent(true);
                RipplerContainer rippler = new RipplerContainer(inner);
                getChildren().setAll(rippler);

                FXUtils.onChangeAndOperate(tab.selectedProperty(), selected -> inner.pseudoClassStateChanged(SELECTED_PSEUDOCLASS_STATE, selected));

                FXUtils.onClicked(this, () -> {
                    this.setOpacity(1);
                    getSkinnable().getSelectionModel().select(tab);
                });
            }
        }
    }

}
