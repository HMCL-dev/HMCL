// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.construct;

import com.jfoenix.controls.JFXPopup;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;

public final class IconedMenuItem extends IconedItem {

    public IconedMenuItem(SVG icon, String text, Runnable action, JFXPopup popup) {
        super(icon != null ? icon.createIcon(14) : null, text);

        getStyleClass().setAll("iconed-menu-item");

        if (popup == null) {
            FXUtils.onClicked(this, action);
        } else {
            FXUtils.onClicked(this, () -> {
                action.run();
                popup.hide();
            });
        }
    }

    public IconedMenuItem addTooltip(String tooltip) {
        FXUtils.installFastTooltip(this, tooltip);
        return this;
    }
}
