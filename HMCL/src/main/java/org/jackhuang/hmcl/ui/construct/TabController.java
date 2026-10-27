// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.construct;

import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.control.SingleSelectionModel;
import org.jackhuang.hmcl.ui.animation.ContainerAnimations;
import org.jackhuang.hmcl.ui.animation.Motion;
import org.jackhuang.hmcl.ui.animation.TransitionPane;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

/// Manages tab selection, lazy content initialization, and page lifecycle callbacks.
/// An optional transition pane displays content activated by [#select(TabItem)].
/// The first tab is selected when an unselected list gains tabs, without initializing
/// content. No control or skin is required. Changes must occur on the FX thread.
@NotNullByDefault
public final class TabController implements PageAware {

    /// The optional destination for selected tab content.
    private final @Nullable TransitionPane contentPane;

    /// Creates a controller with the supplied tabs and no content destination.
    ///
    /// @param tabs the initial tabs in display order, or null for an empty controller
    public TabController(TabItem<?> @Nullable ... tabs) {
        this(null, tabs);
    }

    /// Creates a controller with an optional destination for selected content.
    ///
    /// @param contentPane the destination updated by [#select(TabItem)], or null
    /// @param tabs the initial tabs in display order, or null for an empty controller
    public TabController(@Nullable TransitionPane contentPane, TabItem<?> @Nullable ... tabs) {
        this.contentPane = contentPane;

        if (tabs != null) {
            getTabs().addAll(tabs);
        }
    }

    /// The mutable tabs in display order.
    private final ObservableList<TabItem<?>> tabs = FXCollections.observableArrayList();

    /// Returns the mutable, observable list of tabs in display order.
    /// Entries must be non-null and distinct, and must not belong to another controller.
    /// List changes update selection state without initializing or switching content.
    public ObservableList<TabItem<?>> getTabs() {
        return tabs;
    }

    /// The selection model that tracks changes to the tab list.
    private final SingleSelectionModel<@Nullable TabItem<?>> selectionModel = new TabSelectionModel();

    /// Returns the selection model without activating content or invoking page callbacks.
    /// Direct selection changes, clearing, and list-driven selection only update state.
    /// Selecting a non-null item outside the list sets its selected index to -1;
    /// selecting a null item or an out-of-range index leaves selection unchanged.
    /// Use [#select(TabItem)] to initialize and display the selected content.
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
    /// Callbacks apply to nodes implementing [PageAware]. When a content destination
    /// is configured, the tab must provide a non-null node after initialization.
    /// Without a destination, a tab may have no content.
    /// Exceptions from factories, callbacks, or content replacement propagate;
    /// completed callbacks, initialization, and selection changes are not rolled back.
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

    /// Synchronizes the selected index, selected item, and tab selection flags.
    ///
    /// Changes to the tab list update the selection. Selecting through this model
    /// does not initialize content or invoke page lifecycle callbacks.
    @NotNullByDefault
    private final class TabSelectionModel extends SingleSelectionModel<@Nullable TabItem<?>> {
        /// Registers a listener that keeps the selection aligned with this controller's tab list.
        private TabSelectionModel() {
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
                    if (c.wasAdded() || c.wasRemoved() || c.wasPermutated()) {
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

        /// Selects the tab at the given index; ignores indices outside the tab list.
        /// Updates the selection flags of the old and new tabs.
        @Override
        public void select(int index) {
            if (index < 0 || index >= getItemCount() ||
                    (index == getSelectedIndex() && getModelItem(index).isSelected())) {
                return;
            }

            // Use the item rather than its former index, which may have shifted.
            @Nullable TabItem<?> oldTab = getSelectedItem();
            if (oldTab != null)
                oldTab.setSelected(false);

            setSelectedIndex(index);

            @Nullable TabItem<?> tab = getModelItem(index);
            if (tab != null) {
                setSelectedItem(tab);
            }

            // Select the new tab
            if (getSelectedIndex() >= 0 && getSelectedIndex() < getTabs().size()) {
                getTabs().get(getSelectedIndex()).setSelected(true);
            }
        }

        /// Clears the selected item, index, and tab selection flag without switching content.
        @Override
        public void clearSelection() {
            @Nullable TabItem<?> oldTab = getSelectedItem();
            if (oldTab != null)
                oldTab.setSelected(false);
            setSelectedIndex(-1);
            setSelectedItem(null);
        }

        /// Selects a matching tab in the list. If a non-null tab is absent, updates
        /// the selected item with index -1; a null argument leaves the selection unchanged.
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
                @Nullable TabItem<?> oldTab = getSelectedItem();
                if (oldTab != null)
                    oldTab.setSelected(false);
                setSelectedIndex(-1);
                tab.setSelected(true);
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

        /// Returns the current number of tabs in the controller.
        @Override
        protected int getItemCount() {
            return getTabs().size();
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
}
