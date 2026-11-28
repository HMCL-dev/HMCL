// SPDX-FileCopyrightText: Copyright (C) 2021 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.construct;

import com.jfoenix.controls.JFXButton;
import javafx.event.ActionEvent;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextFlow;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.ScrollUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

import static org.jackhuang.hmcl.ui.FXUtils.onEscPressed;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public final class MessageDialogPane extends HBox {

    public enum MessageType {
        ERROR(SVG.ERROR),
        INFO(SVG.INFO),
        WARNING(SVG.WARNING),
        QUESTION(SVG.HELP),
        SUCCESS(SVG.CHECK_CIRCLE);

        private final SVG icon;

        MessageType(SVG icon) {
            this.icon = icon;
        }

        public SVG getIcon() {
            return icon;
        }

        public String getDisplayName() {
            return i18n("message." + name().toLowerCase(Locale.ROOT));
        }
    }

    private final HBox actions;

    private @Nullable ButtonBase cancelButton;

    public MessageDialogPane(@NotNull String text, @Nullable String title, @NotNull MessageType type) {
        this.setSpacing(16);
        this.getStyleClass().add("jfx-dialog-layout");

        Label graphic = new Label();
        graphic.setTranslateX(10);
        graphic.setTranslateY(10);
        graphic.setMinSize(40, 40);
        graphic.setMaxSize(40, 40);
        graphic.setGraphic(type.getIcon().createIcon(40));

        VBox vbox = new VBox();
        HBox.setHgrow(vbox, Priority.ALWAYS);
        {
            StackPane titlePane = new StackPane();
            titlePane.getStyleClass().addAll("jfx-layout-heading", "title");
            titlePane.getChildren().setAll(new Label(title != null ? title : type.getDisplayName()));

            StackPane content = new StackPane();
            content.getStyleClass().add("jfx-layout-body");
            TextFlow textFlow = new TextFlow();
            textFlow.getChildren().setAll(FXUtils.parseSegment(text, Controllers::onHyperlinkAction));
            textFlow.setStyle("-fx-font-size: 14px;");
            ScrollPane scrollPane = new ScrollPane(textFlow) {
                /// The preferred height depends on the width available for wrapping.
                @Override
                public Orientation getContentBias() {
                    return Orientation.HORIZONTAL;
                }

                /// Includes the control insets around the capped preferred viewport.
                @Override
                protected double computePrefHeight(double width) {
                    double viewportWidth = width < 0 ? -1 : Math.max(1, width - snappedLeftInset() - snappedRightInset());
                    return snappedTopInset() + Math.min(350, Math.ceil(textFlow.prefHeight(viewportWidth))) + snappedBottomInset();
                }
            };
            scrollPane.setFitToWidth(true);
            scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            scrollPane.setMinSize(0, 0);
            ScrollUtils.installSmoothScrolling(scrollPane);
            content.getChildren().setAll(scrollPane);

            actions = new HBox();
            actions.getStyleClass().add("jfx-layout-actions");

            vbox.getChildren().setAll(titlePane, content, actions);
        }

        this.getChildren().setAll(graphic, vbox);

        onEscPressed(this, () -> {
            if (cancelButton != null) {
                cancelButton.fire();
            }
        });
    }

    public void addButton(Node btn) {
        btn.addEventHandler(ActionEvent.ACTION, e -> fireEvent(new DialogCloseEvent()));
        actions.getChildren().add(btn);
    }

    public void setCancelButton(@Nullable ButtonBase btn) {
        cancelButton = btn;
    }

    public ButtonBase getCancelButton() {
        return cancelButton;
    }

    public static class Builder {
        private final MessageDialogPane dialog;

        public Builder(String text, String title, MessageType type) {
            this.dialog = new MessageDialogPane(text, title, type);
        }

        public Builder addHyperLink(String text, String externalLink) {
            JFXHyperlink link = new JFXHyperlink(text);
            link.setExternalLink(externalLink);
            dialog.actions.getChildren().add(link);
            return this;
        }

        public Builder addAction(Node actionNode) {
            dialog.addButton(actionNode);
            actionNode.getStyleClass().add("dialog-accept");
            return this;
        }

        public Builder addAction(String text, @Nullable Runnable action) {
            JFXButton btnAction = new JFXButton(text);
            btnAction.getStyleClass().add("dialog-accept");
            if (action != null) {
                btnAction.setOnAction(e -> action.run());
            }
            dialog.addButton(btnAction);
            return this;
        }

        public Builder ok(@Nullable Runnable ok) {
            JFXButton btnOk = new JFXButton(i18n("button.ok"));
            btnOk.getStyleClass().add("dialog-accept");
            if (ok != null) {
                btnOk.setOnAction(e -> ok.run());
            }
            dialog.addButton(btnOk);
            dialog.setCancelButton(btnOk);
            return this;
        }

        public Builder addCancel(@Nullable Runnable cancel) {
            return addCancel(i18n("button.cancel"), cancel);
        }

        public Builder addCancel(String cancelText, @Nullable Runnable cancel) {
            JFXButton btnCancel = new JFXButton(cancelText);
            btnCancel.setButtonType(JFXButton.ButtonType.FLAT);
            btnCancel.getStyleClass().add("dialog-cancel");
            if (cancel != null) {
                btnCancel.setOnAction(e -> cancel.run());
            }
            dialog.addButton(btnCancel);
            dialog.setCancelButton(btnCancel);
            return this;
        }

        public Builder yesOrNo(@Nullable Runnable yes, @Nullable Runnable no) {
            JFXButton btnYes = new JFXButton(i18n("button.yes"));
            btnYes.getStyleClass().add("dialog-accept");
            if (yes != null) {
                btnYes.setOnAction(e -> yes.run());
            }
            dialog.addButton(btnYes);

            addCancel(i18n("button.no"), no);
            return this;
        }

        public Builder actionOrCancel(ButtonBase actionButton, Runnable cancel) {
            dialog.addButton(actionButton);

            addCancel(cancel);
            return this;
        }

        public MessageDialogPane build() {
            return dialog;
        }
    }
}
