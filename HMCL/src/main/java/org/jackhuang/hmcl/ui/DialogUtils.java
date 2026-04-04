/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2025  huangyuhui <huanghongxun2008@126.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
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

import java.util.Optional;
import java.util.function.Consumer;

public final class DialogUtils {
    private DialogUtils() {
    }

    public static final String PROPERTY_DIALOG_INSTANCE = DialogUtils.class.getName() + ".dialog.instance";
    public static final String PROPERTY_DIALOG_PANE_INSTANCE = DialogUtils.class.getName() + ".dialog.pane.instance";
    public static final String PROPERTY_DIALOG_CLOSE_HANDLER = DialogUtils.class.getName() + ".dialog.closeListener";

    public static final String PROPERTY_PARENT_PANE_REF = DialogUtils.class.getName() + ".dialog.parentPaneRef";
    public static final String PROPERTY_PARENT_DIALOG_REF = DialogUtils.class.getName() + ".dialog.parentDialogRef";

    public static void show(Decorator decorator, Node content) {
        if (decorator.getDrawerWrapper() == null) {
            Platform.runLater(() -> show(decorator, content));
            return;
        }

        show(decorator.getDrawerWrapper(), content, (dialog) -> {
            JFXDialogPane pane = (JFXDialogPane) dialog.getContent();
            decorator.capableDraggingWindow(dialog);
            decorator.forbidDraggingWindow(pane);
            dialog.setDialogContainer(decorator.getDrawerWrapper());
        });
    }

    public static void show(StackPane container, Node content) {
        show(container, content, null);
    }

    public static void show(StackPane container, Node content, @Nullable Consumer<JFXDialog> onDialogCreated) {
        FXUtils.checkFxUserThread();

        JFXDialog dialog = (JFXDialog) container.getProperties().get(PROPERTY_DIALOG_INSTANCE);
        JFXDialogPane dialogPane = (JFXDialogPane) container.getProperties().get(PROPERTY_DIALOG_PANE_INSTANCE);

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

    @SuppressWarnings("unchecked")
    public static void close(Node content) {
        FXUtils.checkFxUserThread();

        Optional.ofNullable(content.getProperties().get(PROPERTY_DIALOG_CLOSE_HANDLER))
                .ifPresent(handler -> content.removeEventHandler(DialogCloseEvent.CLOSE, (EventHandler<DialogCloseEvent>) handler));

        JFXDialogPane pane = (JFXDialogPane) content.getProperties().get(PROPERTY_PARENT_PANE_REF);
        JFXDialog dialog = (JFXDialog) content.getProperties().get(PROPERTY_PARENT_DIALOG_REF);

        if (dialog != null && pane != null) {
            if (pane.size() == 1 && pane.peek().orElse(null) == content) {
                dialog.setOnDialogClosed(e -> pane.pop(content));
                dialog.close();

                StackPane container = dialog.getDialogContainer();
                if (container != null) {
                    container.getProperties().remove(PROPERTY_DIALOG_INSTANCE);
                    container.getProperties().remove(PROPERTY_DIALOG_PANE_INSTANCE);
                    container.getProperties().remove(PROPERTY_PARENT_DIALOG_REF);
                    container.getProperties().remove(PROPERTY_PARENT_PANE_REF);
                }
            } else {
                pane.pop(content);
            }

            if (content instanceof DialogAware dialogAware) {
                dialogAware.onDialogClosed();
            }
        }
    }
}
