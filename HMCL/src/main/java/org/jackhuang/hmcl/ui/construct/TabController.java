// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.construct;

import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import org.jackhuang.hmcl.ui.animation.ContainerAnimations;
import org.jackhuang.hmcl.ui.animation.Motion;
import org.jackhuang.hmcl.ui.animation.TransitionPane;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/// Manages tab selection, lazy content initialization, and page lifecycle callbacks.
/// An optional transition pane displays content activated by [#select(TabItem)].
/// The first tab is selected when an unselected list gains tabs, without initializing
/// content. No control or skin is required. Changes must occur on the FX thread.
@NotNullByDefault
public final class TabController implements PageAware {
    /// The optional destination for selected tab content.
    private final @Nullable TransitionPane contentPane;
    /// The mutable tabs in display order.
    private final ObservableList<TabItem<?>> tabs = FXCollections.observableArrayList();
    /// The selected tab, or null when no tab is selected.
    private final ReadOnlyObjectWrapper<@Nullable TabItem<?>> selectedTab = new ReadOnlyObjectWrapper<>(this, "selectedTab");

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
        this.tabs.addListener(this::tabsChanged);
        if (tabs != null)
            this.tabs.addAll(tabs);
    }

    /// Returns the mutable, observable list of tabs in display order.
    /// Entries must be non-null and distinct. Inserting or reordering tabs preserves
    /// an existing selection. If no tab is selected, the first tab is selected
    /// without initializing content.
    ///
    /// Removing the selected tab hides its page and clears the content destination.
    /// If tabs remain, the tab preceding the removed range is activated without
    /// animation, or the first tab if there is no predecessor. If replacement
    /// initialization fails, selection and content remain empty. Clearing the list also clears both.
    public ObservableList<TabItem<?>> getTabs() {
        return tabs;
    }

    /// Returns the selected tab, or null when no tab is selected.
    /// A default selection does not imply that its content has been initialized.
    public @Nullable TabItem<?> getSelectedTab() {
        return selectedTab.get();
    }

    /// Returns the read-only selected tab property; use [#select(TabItem)] to activate a tab.
    public ReadOnlyObjectProperty<@Nullable TabItem<?>> selectedTabProperty() {
        return selectedTab.getReadOnlyProperty();
    }

    /// Selects the tab and animates content replacement when content is already displayed.
    ///
    /// @see #select(TabItem, boolean)
    public void select(TabItem<?> tab) {
        select(tab, true);
    }

    /// Initializes the tab's content, hides the previous page, replaces the configured
    /// destination, updates selection, and shows the requested page, in that order.
    /// Callbacks apply to nodes implementing [PageAware] and run even when the
    /// requested tab is already selected. Without a destination, content may be null.
    ///
    /// Argument validation and initialization precede changes to the previous page.
    /// Initialization failure leaves the previous selection and content unchanged.
    /// Exceptions from subsequent callbacks or content replacement propagate without
    /// rolling back completed effects. A shown callback runs after selection is updated.
    ///
    /// @param tab a tab belonging to [#getTabs()]
    /// @param playAnimation whether to animate replacement of existing content
    /// @throws NullPointerException if the tab is null
    /// @throws IllegalArgumentException if the tab does not belong to this controller
    /// @throws IllegalStateException if a content destination is configured but the
    /// tab has no content after initialization
    public void select(TabItem<?> tab, boolean playAnimation) {
        Objects.requireNonNull(tab, "tab");
        if (!tabs.contains(tab))
            throw new IllegalArgumentException("Tab does not belong to this controller");

        @Nullable TabItem<?> previous = getSelectedTab();
        @Nullable Node previousContent = previous == null ? null : previous.getNode();
        tab.initializeIfNeeded();
        @Nullable Node content = tab.getNode();
        if (contentPane != null && content == null)
            throw new IllegalStateException("Tab has no content");

        if (previousContent instanceof PageAware pageAware)
            pageAware.onPageHidden();
        if (contentPane != null) {
            if (playAnimation && contentPane.getCurrentNode() != null) {
                contentPane.setContent(content, ContainerAnimations.SLIDE_UP_FADE_IN,
                        Motion.MEDIUM4, Motion.EASE_IN_OUT_CUBIC_EMPHASIZED);
            } else {
                contentPane.setContent(content, ContainerAnimations.NONE);
            }
        }
        selectedTab.set(tab);
        if (content instanceof PageAware pageAware)
            pageAware.onPageShown();
    }

    /// Updates selection after list changes, activating a replacement only when selection was removed.
    private void tabsChanged(ListChangeListener.Change<? extends TabItem<?>> change) {
        @Nullable TabItem<?> selected = getSelectedTab();
        int removedIndex = 0;
        while (change.next()) {
            if (selected != null && change.getRemoved().contains(selected))
                removedIndex = change.getFrom();
        }

        if (selected == null) {
            if (!tabs.isEmpty())
                selectedTab.set(tabs.get(0));
        } else if (!tabs.contains(selected)) {
            // Clear the removed page first so a failing replacement cannot leave it selected.
            clearSelection();
            if (!tabs.isEmpty())
                select(tabs.get(Math.max(0, Math.min(removedIndex - 1, tabs.size() - 1))), false);
        }
    }

    /// Hides the selected page and clears selection and content, including when its hidden callback fails.
    private void clearSelection() {
        try {
            onPageHidden();
        } finally {
            try {
                if (contentPane != null)
                    contentPane.clearContent();
            } finally {
                selectedTab.set(null);
            }
        }
    }

    /// Forwards the shown callback to the selected content when it implements [PageAware].
    @Override
    public void onPageShown() {
        @Nullable TabItem<?> tab = getSelectedTab();
        if (tab != null && tab.getNode() instanceof PageAware pageAware)
            pageAware.onPageShown();
    }

    /// Forwards the hidden callback to the selected content when it implements [PageAware].
    @Override
    public void onPageHidden() {
        @Nullable TabItem<?> tab = getSelectedTab();
        if (tab != null && tab.getNode() instanceof PageAware pageAware)
            pageAware.onPageHidden();
    }
}
