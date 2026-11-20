// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.construct;

import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import org.glavo.m3fx.controls.M3Dialog;
import org.glavo.m3fx.controls.M3DialogEvent;
import org.glavo.m3fx.controls.M3DialogHandle;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

/// A native launcher dialog that can also be closed by its content or an asynchronous operation.
@NotNullByDefault
public class LauncherDialog extends M3Dialog {
    /// The active presentation, if the dialog is currently shown.
    private @Nullable M3DialogHandle handle;

    /// Creates a dialog with explicit dismissal and forwards close requests from its content.
    public LauncherDialog() {
        setDismissOnScrimClick(false);
        getDialogPane().setPrefWidth(480);
        addEventHandler(M3DialogEvent.SHOWN, event -> handle = event.getHandle());
        addEventHandler(M3DialogEvent.HIDING, event -> handle = null);
        getDialogPane().addEventHandler(DialogCloseEvent.CLOSE, event -> {
            if (handle != null)
                handle.requestClose();
        });
    }

    /// Requests dismissal, including when the main window has queued this dialog for later.
    public final void closeDialog() {
        getDialogPane().fireEvent(new DialogCloseEvent());
    }

    /// Allows a form to scroll in a compact window while keeping its actions visible.
    public final void setFormContent(Node form) {
        var scroll = new ScrollPane(form);
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(true);
        scroll.setMinHeight(0);
        getDialogPane().setContent(scroll);
    }
}
