// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.construct;

import javafx.css.PseudoClass;
import javafx.scene.control.SkinBase;
import javafx.scene.layout.BorderPane;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jetbrains.annotations.Nullable;

/// Lays out a two-line item, forwards background actions, and registers optional preparation.
public class AdvancedListItemSkin extends SkinBase<AdvancedListItem> {
    /// The pseudo-class reflecting the item's active state.
    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");

    /// The feedback wrapper receiving the item's preparation registration.
    private final RipplerContainer container;

    /// Creates the content bindings and installs preparation only when configured by the item.
    public AdvancedListItemSkin(AdvancedListItem skinnable) {
        super(skinnable);

        FXUtils.onChangeAndOperate(skinnable.activeProperty(), active -> {
            skinnable.pseudoClassStateChanged(SELECTED, active);
        });

        BorderPane root = new BorderPane();
        root.getStyleClass().add("container");
        root.setPickOnBounds(false);

        container = new RipplerContainer(root);
        container.setOnAction(event -> {
            event.consume();
            skinnable.fire();
        });

        TwoLineListItem item = new TwoLineListItem();
        root.setCenter(item);
        item.setMouseTransparent(true);
        item.titleProperty().bind(skinnable.titleProperty());
        item.subtitleProperty().bind(skinnable.subtitleProperty());

        root.leftProperty().bind(skinnable.leftGraphicProperty());
        root.rightProperty().bind(skinnable.rightGraphicProperty());

        getChildren().setAll(container);
        setPrepareAction(skinnable.getPrepareAction());
    }

    /// Forwards a registration while consuming the control's reference before user code executes.
    void setPrepareAction(@Nullable Runnable action) {
        AdvancedListItem item = getSkinnable();
        container.setPrepareAction(action == null ? null : () -> {
            if (item.getPrepareAction() == action) {
                item.setPrepareAction(null);
                action.run();
            }
        });
    }

    /// Removes background activation, releases graphics, and retains unconsumed preparation on the control.
    @Override
    public void dispose() {
        container.setOnAction(null);
        container.setPrepareAction(null);
        BorderPane root = (BorderPane) container.getContainer();
        root.leftProperty().unbind();
        root.rightProperty().unbind();
        root.setLeft(null);
        root.setRight(null);
        super.dispose();
    }
}
