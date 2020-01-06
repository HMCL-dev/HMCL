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

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import org.jackhuang.hmcl.ui.animation.TransitionHandler;
import org.jackhuang.hmcl.ui.construct.PageCloseEvent;
import org.jackhuang.hmcl.ui.wizard.*;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class DecoratorWizardDisplayer extends StackPane implements TaskExecutorDialogWizardDisplayer, Refreshable, DecoratorPage {
    private final StringProperty title = new SimpleStringProperty();
    private final BooleanProperty canRefresh = new SimpleBooleanProperty();

    private final TransitionHandler transitionHandler = new TransitionHandler(this);
    private final WizardController wizardController = new WizardController(this);
    private final Queue<Object> cancelQueue = new ConcurrentLinkedQueue<>();

    private final String category;

    private Node nowPage;

    public DecoratorWizardDisplayer(WizardProvider provider) {
        this(provider, null);
    }

    public DecoratorWizardDisplayer(WizardProvider provider, String category) {
        this.category = category;

        wizardController.setProvider(provider);
        wizardController.onStart();

        getStyleClass().add("white-background");
    }

    @Override
    public StringProperty titleProperty() {
        return title;
    }

    @Override
    public BooleanProperty canRefreshProperty() {
        return canRefresh;
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
        nowPage = page;

        transitionHandler.setContent(page, nav.getAnimation().getAnimationProducer());

        canRefresh.set(page instanceof Refreshable);

        String prefix = category == null ? "" : category + " - ";

        if (page instanceof WizardPage)
            title.set(prefix + ((WizardPage) page).getTitle());
    }

    @Override
    public boolean canForceToClose() {
        return true;
    }

    @Override
    public void onForceToClose() {
        wizardController.onCancel();
    }

    @Override
    public boolean onClose() {
        if (wizardController.canPrev()) {
            wizardController.onPrev(true);
            return false;
        } else
            return true;
    }

    @Override
    public void refresh() {
        ((Refreshable) nowPage).refresh();
    }
}
