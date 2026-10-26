// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.construct;

import org.jackhuang.hmcl.ui.Controllers;

/**
 * @author yushijinhun
 * @see Controllers#dialog(javafx.scene.layout.Region)
 */
public interface DialogAware {

    default void onDialogShown() {
    }

    default void onDialogClosed() {
    }

}
