/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2017  huangyuhui <huanghongxun2008@126.com>
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
 * along with this program.  If not, see {http://www.gnu.org/licenses/}.
 */
package org.jackhuang.hmcl.ui.wizard;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXToolbar;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.animation.TransitionHandler;
import org.jackhuang.hmcl.util.StringUtils;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class DefaultWizardDisplayer extends StackPane implements AbstractWizardDisplayer {

    private final String prefix;
    private final WizardController wizardController;
    private final Queue<Object> cancelQueue = new ConcurrentLinkedQueue<>();

    private Node nowPage;

    private TransitionHandler transitionHandler;

    @FXML
    private StackPane root;
    @FXML
    private JFXButton backButton;
    @FXML
    private JFXToolbar toolbar;
    @FXML
    private JFXButton refreshButton;
    @FXML
    private Label titleLabel;

    public DefaultWizardDisplayer(String prefix, WizardProvider wizardProvider) {
        this.prefix = prefix;

        FXUtils.loadFXML(this, "/assets/fxml/wizard.fxml");
        toolbar.setEffect(null);

        wizardController = new WizardController(this);
        wizardController.setProvider(wizardProvider);
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
    }

    @Override
    public void onCancel() {
    }

    @Override
    public void navigateTo(Node page, Navigation.NavigationDirection nav) {
        backButton.setDisable(!wizardController.canPrev());
        transitionHandler.setContent(page, nav.getAnimation().getAnimationProducer());
        String title = StringUtils.isBlank(prefix) ? "" : prefix + " - ";
        if (page instanceof WizardPage)
            titleLabel.setText(title + ((WizardPage) page).getTitle());
        refreshButton.setVisible(page instanceof Refreshable);
        nowPage = page;
    }

    @FXML
    private void initialize() {
        transitionHandler = new TransitionHandler(root);
        wizardController.onStart();
    }

    @FXML
    private void back() {
        wizardController.onPrev(true);
    }

    @FXML
    private void close() {
        wizardController.onCancel();
        Controllers.navigate(null);
    }

    @FXML
    private void refresh() {
        ((Refreshable) nowPage).refresh();
    }
}
