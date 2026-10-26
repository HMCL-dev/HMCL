// SPDX-FileCopyrightText: Copyright (C) 2025 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl;

import org.jackhuang.hmcl.util.UTF8Control;

import java.util.ResourceBundle;

final class BootProperties {

    private static ResourceBundle resourceBundle;

    static ResourceBundle getResourceBundle() {
        if (resourceBundle == null) {
            resourceBundle = ResourceBundle.getBundle("assets.lang.boot", new UTF8Control());
        }

        return resourceBundle;
    }

    private BootProperties() {
    }
}
