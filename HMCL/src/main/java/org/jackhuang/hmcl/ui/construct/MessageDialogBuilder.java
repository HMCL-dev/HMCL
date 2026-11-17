// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.construct;

import javafx.scene.control.ScrollPane;
import javafx.scene.text.TextFlow;
import org.glavo.m3fx.controls.M3Button;
import org.glavo.m3fx.controls.M3ButtonVariant;
import org.glavo.m3fx.controls.M3Dialog;
import org.glavo.m3fx.controls.M3DialogPane;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.ScrollUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// Configures native M3 dialogs with launcher message conventions and callbacks.
@NotNullByDefault
public final class MessageDialogBuilder {
    /// The Material dialog being configured.
    private final M3Dialog dialog = new M3Dialog();

    /// Creates a message using the type's default title when `title` is null.
    /// Supports launcher text markup and scrolls long messages within the content area.
    public MessageDialogBuilder(String text, @Nullable String title, MessageType type) {
        dialog.setDismissOnScrimClick(false);
        M3DialogPane pane = dialog.getDialogPane();
        pane.setHeaderText(title != null ? title : type.getDisplayName());
        pane.setGraphic(type.getIcon().createIcon(24));
        TextFlow flow = new TextFlow();
        flow.getStyleClass().add("message-dialog-body");
        flow.getChildren().setAll(FXUtils.parseSegment(text, Controllers::onHyperlinkAction));
        if (flow.prefHeight(400) > 350) {
            ScrollPane scroll = new ScrollPane(flow);
            ScrollUtils.installSmoothScrolling(scroll);
            scroll.setFitToWidth(true);
            scroll.setPrefHeight(350);
            pane.setContent(scroll);
        } else if (text.indexOf('<') >= 0) {
            pane.setContent(flow);
        } else {
            pane.setContentText(text);
        }
    }

    /// Adds a text action that opens a link without closing the dialog.
    public MessageDialogBuilder addHyperLink(String text, String externalLink) {
        M3Button button = new M3Button(text, M3ButtonVariant.TEXT);
        button.setOnAction(event -> {
            event.consume();
            FXUtils.openLink(externalLink);
        });
        dialog.getDialogPane().getActions().add(button);
        return this;
    }

    /// Adds an action button; the first accepting action receives M3FX's preferred initial focus.
    /// Its handler may consume the action event to prevent native dialog closure.
    public MessageDialogBuilder addAction(M3Button button) {
        if (dialog.getDialogPane().getActions().stream().noneMatch(M3Button::isDefaultButton))
            button.setDefaultButton(true);
        dialog.getDialogPane().getActions().add(button);
        return this;
    }

    /// Adds a text action that invokes an optional callback and closes the dialog.
    public MessageDialogBuilder addAction(String text, @Nullable Runnable action) {
        return addAction(newAction(text, action));
    }

    /// Adds OK as both the accepting and Escape action.
    public MessageDialogBuilder ok(@Nullable Runnable action) {
        M3Button button = newAction(i18n("button.ok"), action);
        setCancelButton(button);
        return addAction(button);
    }

    /// Adds a localized Cancel action, invoked by Escape as well.
    public MessageDialogBuilder addCancel(@Nullable Runnable action) {
        return addCancel(i18n("button.cancel"), action);
    }

    /// Adds a named cancel action, replacing the previous Escape action.
    public MessageDialogBuilder addCancel(String text, @Nullable Runnable action) {
        M3Button button = newAction(text, action);
        setCancelButton(button);
        dialog.getDialogPane().getActions().add(button);
        return this;
    }

    /// Adds Yes as the accepting action and No as the Escape action.
    public MessageDialogBuilder yesOrNo(@Nullable Runnable yes, @Nullable Runnable no) {
        return addAction(i18n("button.yes"), yes).addCancel(i18n("button.no"), no);
    }

    /// Adds a supplied action button and a localized Cancel action.
    public MessageDialogBuilder actionOrCancel(M3Button actionButton, @Nullable Runnable cancel) {
        return addAction(actionButton).addCancel(cancel);
    }

    /// Returns the configured Material dialog, ready for an overlay or dialog-window host.
    public M3Dialog build() {
        return dialog;
    }

    /// Assigns the sole Escape action without changing existing callbacks.
    private void setCancelButton(M3Button button) {
        dialog.getDialogPane().getActions().forEach(action -> action.setCancelButton(false));
        button.setCancelButton(true);
    }

    /// Creates a native text action with an optional application callback.
    private static M3Button newAction(String text, @Nullable Runnable action) {
        M3Button button = new M3Button(text, M3ButtonVariant.TEXT);
        if (action != null)
            button.setOnAction(event -> action.run());
        return button;
    }
}
