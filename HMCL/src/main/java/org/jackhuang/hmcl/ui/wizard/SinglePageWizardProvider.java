package org.jackhuang.hmcl.ui.wizard;

import javafx.scene.Node;

import java.util.Map;
import java.util.function.Function;

public class SinglePageWizardProvider implements WizardProvider {

    private final Function<WizardController, WizardSinglePage> provider;
    private WizardSinglePage page;

    public SinglePageWizardProvider(Function<WizardController, WizardSinglePage> provider) {
        this.provider = provider;
    }

    @Override
    public void start(Map<String, Object> settings) {
    }

    @Override
    public Object finish(Map<String, Object> settings) {
        return page.finish();
    }

    @Override
    public Node createPage(WizardController controller, int step, Map<String, Object> settings) {
        if (step != 0) throw new IllegalStateException("Step must be 0");

        return page = provider.apply(controller);
    }

    @Override
    public boolean cancel() {
        return true;
    }
}
