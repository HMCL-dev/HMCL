// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.wizard;

import javafx.scene.control.Control;
import org.jackhuang.hmcl.util.SettingsMap;

public abstract class WizardSinglePage extends Control implements WizardPage {
    protected final Runnable onFinish;

    protected WizardSinglePage(Runnable onFinish) {
        this.onFinish = onFinish;
    }

    protected abstract Object finish();

    @Override
    public void cleanup(SettingsMap settings) {
    }
}
