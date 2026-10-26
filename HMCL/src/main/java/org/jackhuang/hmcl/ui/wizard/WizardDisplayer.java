// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.wizard;

import javafx.scene.Node;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.SettingsMap;

public interface WizardDisplayer {
    default void onStart() {
    }

    default void onEnd() {
    }

    default void onCancel() {
    }

    void navigateTo(Node page, Navigation.NavigationDirection nav);

    void handleTask(SettingsMap settings, Task<?> task);
}
