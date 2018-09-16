package org.jackhuang.hmcl.ui.wizard;

import javafx.scene.control.Control;

import java.util.Map;

public abstract class WizardSinglePage extends Control implements WizardPage {
    protected final Runnable onFinish;

    protected WizardSinglePage(Runnable onFinish) {
        this.onFinish = onFinish;
    }

    protected abstract Object finish();

    @Override
    public void cleanup(Map<String, Object> settings) {
    }
}
