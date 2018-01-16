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
package org.jackhuang.hmcl.ui.download;

import com.jfoenix.controls.JFXListView;
import javafx.fxml.FXML;
import javafx.scene.layout.StackPane;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.wizard.WizardController;
import org.jackhuang.hmcl.ui.wizard.WizardPage;

import java.util.Map;

public final class InstallTypePage extends StackPane implements WizardPage {
    private final WizardController controller;

    @FXML private JFXListView<Object> list;

    public InstallTypePage(WizardController controller) {
        this.controller = controller;

        FXUtils.loadFXML(this, "/assets/fxml/download/dltype.fxml");
        list.setOnMouseClicked(e -> {
            controller.getSettings().put(INSTALL_TYPE, list.getSelectionModel().getSelectedIndex());
            controller.onNext();
        });
    }

    @Override
    public void cleanup(Map<String, Object> settings) {
        settings.remove(INSTALL_TYPE);
    }

    @Override
    public String getTitle() {
        return "Select an operation";
    }

    public static final String INSTALL_TYPE = "INSTALL_TYPE";
}
