// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.wizard;

import org.jackhuang.hmcl.util.SettingsMap;

public interface WizardPage {
    default void onNavigate(SettingsMap settings) {
    }

    default void cleanup(SettingsMap settings) {
    }

    String getTitle();
}
