// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXListView;
import javafx.beans.property.*;
import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import org.jackhuang.hmcl.ui.construct.ComponentList;
import org.jackhuang.hmcl.ui.construct.RipplerContainer;
import org.jackhuang.hmcl.ui.construct.SpinnerPane;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/// Displays a list with an optional toolbar and loading and failure indicators.
///
/// @param <T> the type of item in the list
@NotNullByDefault
public abstract class ListPaneBase<T> extends StackPane {
    /// Orders the toolbar, list and any footer.
    private final ComponentList content = new ComponentList();
    /// Selects the visible list, progress indicator or failure message.
    private final SpinnerPane spinner = new SpinnerPane();
    /// Displays the source items unless the page installs a filtered view.
    private final JFXListView<T> listView = new JFXListView<>();
    /// The node currently installed above the list.
    private @Nullable Node toolbar;

    /// Creates an empty list that obtains cells from [#createItemCell(ListView)].
    protected ListPaneBase() {
        content.getStyleClass().add("no-padding");
        StackPane.setMargin(content, new Insets(10));
        getChildren().add(content);
        listView.getStyleClass().add("no-horizontal-scrollbar");
        listView.setCellFactory(this::createItemCell);
        FXUtils.ignoreEvent(listView, KeyEvent.KEY_PRESSED, e -> e.getCode() == KeyCode.ESCAPE);
        spinner.setContent(listView);
        ComponentList.setVgrow(spinner, Priority.ALWAYS);
        content.getContent().add(spinner);
    }

    /// Creates a new reusable cell for the supplied list.
    protected abstract ItemCell<T> createItemCell(ListView<T> listView);

    /// Owns the graphic and ripple feedback shared by successive items in one virtual cell.
    @NotNullByDefault
    public abstract static class ItemCell<T> extends ListCell<@Nullable T> {
        /// Marks selected rows on pages that enable selection feedback.
        private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");

        /// Holds the page-specific row content.
        private final StackPane contentPane = new StackPane();
        /// Carries row styles and interaction feedback across item changes.
        private final RipplerContainer feedback = new RipplerContainer(contentPane);

        /// Creates a reusable cell sized to the list viewport.
        protected ItemCell(ListView<T> listView) {
            setText(null);
            feedback.getStyleClass().add("md-list-cell");
            FXUtils.limitCellWidth(listView, this, 1);
        }

        /// Creates a cell that displays each node item directly as its content.
        public static <N extends Node> ItemCell<N> forNode(ListView<N> listView) {
            return new ItemCell<>(listView) {
                @Override
                protected void updateListItem(@Nullable N item) {
                    setContent(item);
                }
            };
        }

        /// Replaces the row content while retaining the cell's feedback container.
        protected final void setContent(@Nullable Node content) {
            if (content == null)
                contentPane.getChildren().clear();
            else
                contentPane.getChildren().setAll(content);
        }

        /// Returns the reusable feedback container for this row.
        public final RipplerContainer getFeedbackContainer() {
            return feedback;
        }

        /// Mirrors the cell's selection state on the styled row.
        protected final void setSelectable() {
            FXUtils.onChangeAndOperate(selectedProperty(), selected -> feedback.pseudoClassStateChanged(SELECTED, selected));
        }

        /// Resets feedback and updates the graphic when the item or empty state changes.
        @Override
        protected final void updateItem(@Nullable T item, boolean empty) {
            boolean changed = item != getItem() || empty != isEmpty();
            super.updateItem(item, empty);

            if (changed) {
                boolean hasItem = !empty && item != null;
                feedback.resetFeedback();
                updateListItem(hasItem ? item : null);
                setGraphic(hasItem ? feedback : null);
            }
        }

        /// Updates the row for the current item, or releases item-specific bindings when cleared.
        /// The cell has already received its new item. The same cell may be reused after clearing.
        protected abstract void updateListItem(@Nullable T item);
    }

    /// Returns the list view configured by this page.
    protected final JFXListView<T> getListView() {
        return listView;
    }

    /// Replaces the toolbar, or removes it if the argument is null.
    protected final void setToolbar(@Nullable Node toolbar) {
        if (this.toolbar != null)
            content.getContent().remove(this.toolbar);
        this.toolbar = toolbar;
        if (toolbar != null)
            content.getContent().add(0, toolbar);
    }

    /// Installs a left-aligned toolbar containing the supplied controls in order.
    protected final void setToolbarButtons(List<Node> buttons) {
        HBox bar = new HBox();
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPickOnBounds(false);
        bar.getChildren().setAll(buttons);
        FXUtils.setOverflowHidden(bar, 8);
        setToolbar(bar);
    }

    /// Adds a node below the list and its status indicator.
    protected final void addFooter(Node footer) {
        content.getContent().add(footer);
    }

    /// Returns the items currently displayed by the list view, including any active filtering.
    public final ObservableList<T> getItems() {
        return listView.getItems();
    }

    /// Returns the property controlling the loading indicator.
    public final BooleanProperty loadingProperty() {
        return spinner.loadingProperty();
    }

    /// Returns whether the loading indicator is active.
    public final boolean isLoading() {
        return spinner.isLoading();
    }

    /// Shows or hides the loading indicator.
    public final void setLoading(boolean loading) {
        spinner.setLoading(loading);
    }

    /// Returns the failure message property.
    public final StringProperty failedReasonProperty() {
        return spinner.failedReasonProperty();
    }

    /// Returns the failure message, or null when no failure is displayed or pending.
    public final @Nullable String getFailedReason() {
        return spinner.getFailedReason();
    }

    /// Sets the message displayed when loading ends, or clears it with null.
    public final void setFailedReason(@Nullable String reason) {
        spinner.setFailedReason(reason);
    }

    /// Handles failure actions that reach this page, including clicks from its spinner.
    private final ObjectProperty<@Nullable EventHandler<Event>> onFailedAction =
            new SimpleObjectProperty<>(this, "onFailedAction") {
                @Override
                protected void invalidated() {
                    setEventHandler(SpinnerPane.FAILED_ACTION, get());
                }
            };

    /// Returns the handler property for clicks on the failure message.
    public final ObjectProperty<@Nullable EventHandler<Event>> onFailedActionProperty() {
        return onFailedAction;
    }

    /// Returns the handler for clicks on the failure message, or null if absent.
    public final @Nullable EventHandler<Event> getOnFailedAction() {
        return onFailedAction.get();
    }

    /// Sets the handler for clicks on the failure message, or removes it with null.
    public final void setOnFailedAction(@Nullable EventHandler<Event> handler) {
        onFailedAction.set(handler);
    }

    /// Creates a toolbar action with a 20-pixel icon and optional label.
    protected static JFXButton createToolbarButton(@Nullable String text, SVG icon, Runnable action) {
        JFXButton button = new JFXButton();
        button.getStyleClass().add("jfx-tool-bar-button");
        button.setGraphic(icon.createIcon(20));
        button.setText(text);
        button.setOnAction(e -> action.run());
        return button;
    }
}
