// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.construct;

import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.geometry.HPos;
import javafx.geometry.NodeOrientation;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.ScrollPane;
import javafx.stage.Popup;
import javafx.stage.Screen;
import javafx.stage.WindowEvent;
import org.glavo.m3fx.controls.M3Menu;
import org.glavo.m3fx.controls.M3MenuItem;
import org.glavo.m3fx.theme.M3Theme;
import org.glavo.m3fx.theme.M3ThemeManager;
import org.jackhuang.hmcl.ui.SVG;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

/// Presents a Material menu beside an existing control or at a context-menu request.
@NotNullByDefault
public final class PopupMenu extends Popup {
    /// Identifies the open launcher menu in its owner window.
    private static final Object OPEN_MENU = new Object();

    /// The menu surface, which owns its rows and handles keyboard navigation.
    private final M3Menu menu = new M3Menu();
    /// Keeps long menus accessible within the available height.
    private final ScrollPane scroll = new ScrollPane(menu);

    /// Creates an auto-hiding popup with optional initial rows.
    public PopupMenu(Node... items) {
        setAutoHide(true);
        setHideOnEscape(true);
        setConsumeAutoHidingEvents(false);
        setAnchorLocation(AnchorLocation.CONTENT_TOP_LEFT);
        scroll.setFitToWidth(true);
        scroll.setFocusTraversable(false);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("menu-popup-scroll");
        menu.getItems().addAll(items);
        menu.addEventHandler(ActionEvent.ACTION, event -> hide());
        addEventHandler(WindowEvent.WINDOW_HIDDEN, event -> {
            getOwnerWindow().getProperties().remove(OPEN_MENU, this);
            getContent().clear();
        });
    }

    /// Returns the ordered rows of the Material menu.
    public ObservableList<Node> getItems() {
        return menu.getItems();
    }

    /// Creates a menu action with an optional leading icon.
    public static M3MenuItem item(@Nullable SVG icon, String text, Runnable action) {
        var item = new M3MenuItem(text, icon == null ? null : icon.createIcon(20));
        item.setOnAction(event -> action.run());
        return item;
    }

    /// Opens at the trailing edge below the anchor, or above it if there is more room.
    /// The owner receives repeat clicks so that a toggle button can close its menu.
    public void showBelow(Node owner, Node anchor) {
        @Nullable Bounds bounds = anchor.localToScreen(anchor.getLayoutBounds());
        if (bounds != null) {
            open(owner, bounds, null, false);
        }
    }

    /// Opens beside the owner, aligning the requested logical corner with its edge.
    public void showBeside(Node owner, Pos corner) {
        @Nullable Bounds bounds = owner.localToScreen(owner.getLayoutBounds());
        if (bounds != null) {
            open(owner, bounds, corner, false);
        }
    }

    /// Opens at the pointer or keyboard context-menu location in screen coordinates.
    public void showAt(Node owner, double screenX, double screenY) {
        open(owner, new BoundingBox(screenX, screenY, 0, 0), null, true);
    }

    /// Toggles an anchored menu or replaces the previous menu at a new pointer location.
    private void open(Node owner, Bounds anchor, @Nullable Pos side, boolean atPointer) {
        if (owner.getScene() == null || owner.getScene().getWindow() == null || !owner.getScene().getWindow().isShowing())
            return;
        var window = owner.getScene().getWindow();
        if (window.getProperties().get(OPEN_MENU) instanceof PopupMenu previous) {
            boolean toggle = !atPointer && previous.getOwnerNode() == owner;
            previous.hide();
            if (toggle)
                return;
        }
        if (isShowing())
            hide();
        // Popup CSS inherits the owner window only once JavaFX begins showing it.
        EventHandler<WindowEvent> positionOnShowing = event -> position(owner, anchor, side, atPointer);
        addEventHandler(WindowEvent.WINDOW_SHOWING, positionOnShowing);
        try {
            if (atPointer)
                show(window, anchor.getMinX(), anchor.getMinY());
            else
                show(owner, anchor.getMinX(), anchor.getMinY());
        } finally {
            removeEventHandler(WindowEvent.WINDOW_SHOWING, positionOnShowing);
        }
        window.getProperties().put(OPEN_MENU, this);
        menu.getItems().stream().filter(item -> item.isVisible() && !item.isDisabled() && item.isFocusTraversable())
                .findFirst().ifPresent(Node::requestFocus);
    }

    /// Positions the themed, measured menu within the owner's visible window and monitor.
    private void position(Node owner, Bounds anchor, @Nullable Pos side, boolean atPointer) {
        var window = getOwnerWindow();
        Rectangle2D screen = Screen.getScreensForRectangle(anchor.getMinX(), anchor.getMinY(), 1, 1)
                .stream().findFirst().orElse(Screen.getPrimary()).getVisualBounds();
        double top = Math.max(window.getY(), screen.getMinY());
        double bottom = Math.min(window.getY() + window.getHeight(), screen.getMaxY());
        boolean rtl = owner.getEffectiveNodeOrientation() == NodeOrientation.RIGHT_TO_LEFT;
        getScene().setNodeOrientation(owner.getEffectiveNodeOrientation());
        getScene().getRoot().getStylesheets().setAll(owner.getScene().getStylesheets());
        @Nullable M3Theme theme = null;
        for (@Nullable Parent parent = owner instanceof Parent p ? p : owner.getParent();
             parent != null && theme == null; parent = parent.getParent()) {
            theme = M3ThemeManager.getTheme(parent);
        }
        if (theme != null)
            M3ThemeManager.install(scroll, theme);
        else
            M3ThemeManager.uninstall(scroll);
        getContent().setAll(scroll);
        getScene().getRoot().applyCss();

        double width = Math.min(menu.prefWidth(-1), menu.maxWidth(-1));
        double preferredHeight = Math.ceil(menu.prefHeight(width));
        double above = anchor.getMinY() - top;
        double below = bottom - anchor.getMaxY();
        double available = bottom - top;
        boolean opensAbove = false;
        if (side == null) {
            double visibleAbove = above;
            double visibleBelow = below;
            for (@Nullable Parent parent = owner.getParent(); parent != null; parent = parent.getParent()) {
                if (parent instanceof ScrollPane) {
                    @Nullable Bounds clip = parent.localToScreen(parent.getLayoutBounds());
                    if (clip != null) {
                        visibleAbove = Math.min(visibleAbove, anchor.getMinY() - clip.getMinY());
                        visibleBelow = Math.min(visibleBelow, clip.getMaxY() - anchor.getMaxY());
                    }
                }
            }
            opensAbove = visibleBelow < preferredHeight && visibleAbove > visibleBelow;
            available = opensAbove ? above : below;
        }
        double height = Math.clamp(preferredHeight, 1, Math.max(1, available));
        scroll.setPrefHeight(height);
        scroll.setVvalue(0);
        if (height < preferredHeight && scroll.lookup(".scroll-bar:vertical") instanceof ScrollBar bar)
            width += bar.prefWidth(-1);
        width = Math.min(Math.ceil(width), screen.getWidth());
        scroll.setPrefWidth(width);

        double x;
        double y;
        if (side != null) {
            boolean openRight = (side.getHpos() == HPos.RIGHT) != rtl;
            x = openRight ? anchor.getMaxX() : anchor.getMinX() - width;
            y = side.getVpos() == VPos.BOTTOM ? anchor.getMaxY() - height : anchor.getMinY();
        } else {
            boolean alignLeft = atPointer ? !rtl : rtl;
            x = alignLeft ? anchor.getMinX() : anchor.getMaxX() - width;
            y = opensAbove ? anchor.getMinY() - height : anchor.getMaxY();
        }
        setAnchorX(x);
        setAnchorY(Math.clamp(y, top, Math.max(top, bottom - height)));
    }
}
