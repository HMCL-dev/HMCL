/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.jackhuang.hmcl.ui.decorator;

import javafx.scene.Node;
import javafx.scene.control.SkinBase;
import org.jackhuang.hmcl.ui.construct.Navigator;
import org.jackhuang.hmcl.ui.construct.PageCloseEvent;
import org.jackhuang.hmcl.ui.wizard.*;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class DecoratorWizardDisplayer extends DecoratorTransitionPage implements TaskExecutorDialogWizardDisplayer {
    private final WizardController wizardController = new WizardController(this);
    private final Queue<Object> cancelQueue = new ConcurrentLinkedQueue<>();

    private final String category;

    public DecoratorWizardDisplayer(WizardProvider provider) {
        this(provider, null);
    }

    public DecoratorWizardDisplayer(WizardProvider provider, String category) {
        this.category = category;

        wizardController.setProvider(provider);
        wizardController.onStart();

        addEventHandler(Navigator.NavigationEvent.NAVIGATED, this::onDecoratorPageNavigating);
    }

    @Override
    public WizardController getWizardController() {
        return wizardController;
    }

    @Override
    public Queue<Object> getCancelQueue() {
        return cancelQueue;
    }

    @Override
    public void onStart() {

    }

    @Override
    public void onEnd() {
        fireEvent(new PageCloseEvent());
    }

    @Override
    public void navigateTo(Node page, Navigation.NavigationDirection nav) {
        navigate(page, nav.getAnimation().getAnimationProducer());

        String prefix = category == null ? "" : category + " - ";

        String title;
        if (page instanceof WizardPage)
            title = prefix + ((WizardPage) page).getTitle();
        else
            title = "";
        state.set(new State(title, null, true, refreshableProperty().get(), true));

        if (page instanceof Refreshable) {
            refreshableProperty().bind(((Refreshable) page).refreshableProperty());
        } else {
            refreshableProperty().unbind();
            refreshableProperty().set(false);
        }
    }

    @Override
    public boolean isPageCloseable() {
        return true;
    }

    @Override
    public void closePage() {
        wizardController.onCancel();
    }

    @Override
    public boolean back() {
        if (wizardController.canPrev()) {
            wizardController.onPrev(true);
            return false;
        } else
            return true;
    }

    @Override
    public void refresh() {
        ((Refreshable) getCurrentPage()).refresh();
    }

    @Override
    protected Skin createDefaultSkin() {
        return new Skin(this);
    }

    private static class Skin extends SkinBase<DecoratorWizardDisplayer> {

        protected Skin(DecoratorWizardDisplayer control) {
            super(control);

            getChildren().setAll(control.transitionPane);
        }
    }
}
