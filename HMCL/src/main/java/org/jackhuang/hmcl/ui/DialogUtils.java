// SPDX-FileCopyrightText: Copyright (C) 2025 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui;

import com.jfoenix.controls.JFXDialog;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import org.jackhuang.hmcl.ui.animation.AnimationUtils;
import org.jackhuang.hmcl.ui.construct.DialogAware;
import org.jackhuang.hmcl.ui.construct.DialogCloseEvent;
import org.jackhuang.hmcl.ui.construct.JFXDialogPane;
import org.jackhuang.hmcl.ui.decorator.Decorator;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedList;
import java.util.Optional;
import java.util.Queue;
import java.util.function.Consumer;

public final class DialogUtils {
    private DialogUtils() {
    }

    public static final String PROPERTY_DIALOG_INSTANCE = DialogUtils.class.getName() + ".dialog.instance";
    public static final String PROPERTY_DIALOG_PANE_INSTANCE = DialogUtils.class.getName() + ".dialog.pane.instance";
    public static final String PROPERTY_DIALOG_CLOSE_HANDLER = DialogUtils.class.getName() + ".dialog.closeListener";

    public static final String PROPERTY_PARENT_PANE_REF = DialogUtils.class.getName() + ".dialog.parentPaneRef";
    public static final String PROPERTY_PARENT_DIALOG_REF = DialogUtils.class.getName() + ".dialog.parentDialogRef";

    public static final String PROPERTY_DIALOG_SHOW_LATER = DialogUtils.class.getName() + ".dialog.showLater";

    /// Shows content in the main-window dialog stack owned by `decorator`.
    ///
    /// @param decorator the main-window decorator
    /// @param content   the dialog content
    public static void show(Decorator decorator, Node content) {
        StackPane dialogContainer = decorator.getDialogContainer();
        if (decorator.getRoot().getScene() == null) {
            Platform.runLater(() -> showInDecorator(decorator, dialogContainer, content));
            return;
        }

        showInDecorator(decorator, dialogContainer, content);
    }

    /// Shows content in a decorator's resolved dialog container.
    ///
    /// @param decorator     the main-window decorator
    /// @param dialogContainer the container resolved before any deferred execution
    /// @param content       the dialog content
    private static void showInDecorator(Decorator decorator, StackPane dialogContainer, Node content) {
        show(dialogContainer, content, dialog -> {
            JFXDialogPane pane = (JFXDialogPane) dialog.getContent();
            decorator.capableDraggingOverlay(dialog);
            decorator.forbidDraggingWindow(pane);
            dialog.setDialogContainer(dialogContainer);
        });
    }

    public static void show(StackPane container, Node content) {
        show(container, content, null);
    }

    /// Adds content to the container's dialog stack on the JavaFX application thread.
    ///
    /// Content already in a dialog stack is left unchanged. Content still attached
    /// to a closing dialog is detached before being shown again.
    ///
    /// @param container the container that hosts the dialog
    /// @param content the content to display
    /// @param onDialogCreated an optional callback that configures a newly created dialog
    public static void show(StackPane container, Node content, @Nullable Consumer<JFXDialog> onDialogCreated) {
        FXUtils.checkFxUserThread();
        if (isShowing(content))
            return;

        // A closing dialog may retain its content until its exit animation finishes.
        if (content.getParent() instanceof JFXDialogPane previousPane)
            previousPane.pop(content);

        @Nullable JFXDialog dialog = (JFXDialog) container.getProperties().get(PROPERTY_DIALOG_INSTANCE);
        @Nullable JFXDialogPane dialogPane = (JFXDialogPane) container.getProperties().get(PROPERTY_DIALOG_PANE_INSTANCE);

        if (dialog == null) {
            dialog = new JFXDialog(AnimationUtils.isAnimationEnabled()
                    ? JFXDialog.DialogTransition.CENTER
                    : JFXDialog.DialogTransition.NONE);
            dialogPane = new JFXDialogPane();

            dialog.setContent(dialogPane);
            dialog.setDialogContainer(container);
            dialog.setOverlayClose(false);

            container.getProperties().put(PROPERTY_DIALOG_INSTANCE, dialog);
            container.getProperties().put(PROPERTY_DIALOG_PANE_INSTANCE, dialogPane);

            if (onDialogCreated != null) {
                onDialogCreated.accept(dialog);
            }

            dialog.show();
        }

        content.getProperties().put(PROPERTY_PARENT_PANE_REF, dialogPane);
        content.getProperties().put(PROPERTY_PARENT_DIALOG_REF, dialog);

        dialogPane.push(content);

        EventHandler<DialogCloseEvent> handler = event -> close(content);
        content.getProperties().put(PROPERTY_DIALOG_CLOSE_HANDLER, handler);
        content.addEventHandler(DialogCloseEvent.CLOSE, handler);

        handleDialogShown(dialog, content);
    }

    private static void handleDialogShown(JFXDialog dialog, Node node) {
        if (dialog.isVisible()) {
            dialog.requestFocus();
            if (node instanceof DialogAware dialogAware)
                dialogAware.onDialogShown();
        } else {
            dialog.visibleProperty().addListener(new ChangeListener<>() {
                @Override
                public void changed(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) {
                    if (newValue) {
                        dialog.requestFocus();
                        if (node instanceof DialogAware dialogAware)
                            dialogAware.onDialogShown();
                        observable.removeListener(this);
                    }
                }
            });
        }
    }

    /// Queues content in the main-window dialog stack owned by `decorator`.
    ///
    /// @param decorator the main-window decorator
    /// @param content   the dialog content
    public static void showLater(Decorator decorator, Node content) {
        StackPane dialogContainer = decorator.getDialogContainer();
        Runnable showDialogAction = () -> showInDecorator(decorator, dialogContainer, content);
        if (decorator.getRoot().getScene() == null) {
            Platform.runLater(() -> showLater(dialogContainer, showDialogAction));
            return;
        }
        showLater(dialogContainer, showDialogAction);
    }

    @SuppressWarnings("unchecked")
    public static void showLater(StackPane container, Runnable showDialogAction) {
        if (showDialogAction == null) return;
        FXUtils.checkFxUserThread();

        if (container.getProperties().get(PROPERTY_DIALOG_INSTANCE) == null) {
            showDialogAction.run();
            return;
        }
        Queue<Runnable> queue = (Queue<Runnable>) container.getProperties().get(PROPERTY_DIALOG_SHOW_LATER);
        if (queue == null) {
            queue = new LinkedList<>();
            container.getProperties().put(PROPERTY_DIALOG_SHOW_LATER, queue);
        }
        queue.add(showDialogAction);
    }

    /// Returns whether content belongs to a dialog that has not been closed.
    ///
    /// Content covered by another dialog is still considered shown.
    public static boolean isShowing(Node content) {
        FXUtils.checkFxUserThread();
        return content.getProperties().containsKey(PROPERTY_PARENT_DIALOG_REF);
    }

    /// Removes content from its dialog stack, closing the dialog if the stack becomes empty.
    ///
    /// Must be called on the JavaFX application thread. Closing already detached content
    /// has no effect. Content may remain attached until the closing animation finishes. Its dialog
    /// references are removed immediately so that it can be safely shown again.
    @SuppressWarnings("unchecked")
    public static void close(Node content) {
        FXUtils.checkFxUserThread();

        Optional.ofNullable(content.getProperties().remove(PROPERTY_DIALOG_CLOSE_HANDLER))
                .ifPresent(handler -> content.removeEventHandler(DialogCloseEvent.CLOSE, (EventHandler<DialogCloseEvent>) handler));

        @Nullable JFXDialogPane pane = (JFXDialogPane) content.getProperties().remove(PROPERTY_PARENT_PANE_REF);
        @Nullable JFXDialog dialog = (JFXDialog) content.getProperties().remove(PROPERTY_PARENT_DIALOG_REF);

        @Nullable Runnable showNextDialogAction = null;

        if (dialog != null && pane != null) {
            if (pane.size() == 1 && pane.peek().orElse(null) == content) {
                dialog.setOnDialogClosed(e -> pane.pop(content));
                dialog.close();

                @Nullable StackPane container = dialog.getDialogContainer();
                if (container != null) {
                    container.getProperties().remove(PROPERTY_DIALOG_INSTANCE);
                    container.getProperties().remove(PROPERTY_DIALOG_PANE_INSTANCE);
                    container.getProperties().remove(PROPERTY_PARENT_DIALOG_REF);
                    container.getProperties().remove(PROPERTY_PARENT_PANE_REF);

                    @Nullable Queue<Runnable> queue = (Queue<Runnable>) container.getProperties().get(PROPERTY_DIALOG_SHOW_LATER);
                    if (queue != null) {
                        if (!queue.isEmpty()) {
                            @Nullable Runnable next = queue.remove();
                            if (next != null) showNextDialogAction = next;
                        }
                        if (queue.isEmpty()) {
                            container.getProperties().remove(PROPERTY_DIALOG_SHOW_LATER);
                        }
                    }
                }
            } else {
                pane.pop(content);
            }

            if (content instanceof DialogAware dialogAware) {
                dialogAware.onDialogClosed();
            }
        }

        if (showNextDialogAction != null) showNextDialogAction.run();
    }
}
