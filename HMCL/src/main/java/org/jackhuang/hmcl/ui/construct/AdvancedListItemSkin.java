// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.construct;

import com.jfoenix.utils.JFXNodeUtils;
import javafx.animation.FadeTransition;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.scene.control.SkinBase;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.Region;
import javafx.scene.paint.Paint;
import org.jackhuang.hmcl.ui.animation.AnimationUtils;
import org.jackhuang.hmcl.ui.animation.Motion;
import org.jetbrains.annotations.Nullable;

/// Lays out a two-line item, forwards background actions, and registers optional preparation.
public class AdvancedListItemSkin extends SkinBase<AdvancedListItem> {
    /// The pseudo-class reflecting the item's active state.
    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");

    /// The background node opacity when the item is fully selected.
    private static final double SELECTED_BACKGROUND_OPACITY = 0.5;

    /// The feedback wrapper receiving the item's preparation registration.
    private final RipplerContainer container;

    /// The theme-colored selection background, faded independently of content and feedback.
    private final Region selectionBackground = new Region();

    /// The reusable selection fade, allocated on the first animated state change.
    private @Nullable FadeTransition selectionTransition;

    /// Creates the content bindings and installs preparation only when configured by the item.
    public AdvancedListItemSkin(AdvancedListItem skinnable) {
        super(skinnable);

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

        selectionBackground.setMouseTransparent(true);
        getChildren().setAll(selectionBackground, container);
        updateSelectionFill();
        registerChangeListener(skinnable.selectedBackgroundFillProperty(), observable -> updateSelectionFill());
        updateSelection(false);
        registerChangeListener(skinnable.activeProperty(), observable -> updateSelection(true));
        registerChangeListener(skinnable.sceneProperty(), observable -> updateSelection(false));
        registerChangeListener(JFXNodeUtils.treeVisibleProperty(skinnable), observable -> updateSelection(false));
        setPrepareAction(skinnable.getPrepareAction());
    }

    /// Applies the control's selected background paint without changing animation progress.
    private void updateSelectionFill() {
        @Nullable Paint fill = getSkinnable().getSelectedBackgroundFill();
        selectionBackground.setBackground(fill == null ? null
                : new Background(new BackgroundFill(fill, CornerRadii.EMPTY, Insets.EMPTY)));
    }

    /// Updates selection styling and fades from the displayed opacity when the item is showing.
    /// Initial, detached, hidden, and animation-disabled states are applied immediately.
    private void updateSelection(boolean animate) {
        AdvancedListItem item = getSkinnable();
        boolean active = item.isActive();
        item.pseudoClassStateChanged(SELECTED, active);
        double target = active ? SELECTED_BACKGROUND_OPACITY : 0;
        double current = selectionBackground.getOpacity();
        if (selectionTransition != null)
            selectionTransition.stop();
        if (!animate || !AnimationUtils.isAnimationEnabled() || !JFXNodeUtils.isTreeShowing(item) || current == target) {
            selectionBackground.setOpacity(target);
            return;
        }

        if (selectionTransition == null) {
            selectionTransition = new FadeTransition(Motion.SHORT4, selectionBackground);
            selectionTransition.setInterpolator(Motion.LINEAR);
        }
        // Preserve the displayed opacity when reversing an unfinished fade.
        selectionBackground.setOpacity(current);
        selectionTransition.setFromValue(current);
        selectionTransition.setToValue(target);
        selectionTransition.setOnFinished(event -> selectionBackground.setOpacity(target));
        selectionTransition.playFromStart();
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

    /// Stops the selection fade, removes background activation, and releases graphics.
    /// Unconsumed preparation remains on the control.
    @Override
    public void dispose() {
        if (selectionTransition != null)
            selectionTransition.stop();
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
