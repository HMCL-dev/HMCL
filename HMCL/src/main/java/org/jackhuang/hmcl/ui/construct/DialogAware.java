// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.construct;

import org.glavo.m3fx.controls.M3DialogHandle;

/// Receives lifecycle callbacks for content displayed as an application dialog.
public interface DialogAware {

    /// Called when the content is displayed in a new M3FX presentation.
    default void onDialogShown(M3DialogHandle handle) {
        onDialogShown();
    }

    /// Called when the content becomes visible.
    default void onDialogShown() {
    }

    /// Called when the presentation starts closing, including when its window is hidden.
    default void onDialogClosed() {
    }

}
