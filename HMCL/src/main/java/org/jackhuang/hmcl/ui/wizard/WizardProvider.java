// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.wizard;

import javafx.scene.Node;
import org.jackhuang.hmcl.util.SettingsMap;

public interface WizardProvider {

    void start(SettingsMap settings);

    Object finish(SettingsMap settings);

    Node createPage(WizardController controller, int step, SettingsMap settings);

    boolean cancel();

    default boolean cancelIfCannotGoBack() {
        return false;
    }

    /// Reports a failed wizard task and continues after the failure is acknowledged.
    interface FailureCallback {
        /// Optional callback consumed by the wizard's task dialog.
        SettingsMap.Key<FailureCallback> KEY = new SettingsMap.Key<>("failure_callback");

        /// Reports the task failure and invokes `next` when finished.
        void onFail(SettingsMap settings, Throwable exception, Runnable next);
    }

    /// Keeps the dialog open until the cancelled task finishes.
    SettingsMap.Key<Boolean> WAIT_FOR_CANCELLATION = new SettingsMap.Key<>("wait_for_cancellation");
}
