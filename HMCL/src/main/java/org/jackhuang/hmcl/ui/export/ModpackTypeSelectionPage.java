/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2019  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.ui.export;

import com.jfoenix.controls.JFXButton;
import javafx.fxml.FXML;
import javafx.scene.layout.StackPane;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.wizard.WizardController;
import org.jackhuang.hmcl.ui.wizard.WizardPage;

import java.util.Map;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public final class ModpackTypeSelectionPage extends StackPane implements WizardPage {
    private final WizardController controller;
    @FXML
    private JFXButton btnHMCL;
    @FXML
    private JFXButton btnMultiMC;

    public ModpackTypeSelectionPage(WizardController controller) {
        this.controller = controller;
        FXUtils.loadFXML(this, "/assets/fxml/modpack/type.fxml");

        JFXButton[] buttons = new JFXButton[]{btnHMCL, btnMultiMC};
        String[] types = new String[]{MODPACK_TYPE_HMCL, MODPACK_TYPE_MULTIMC};
        for (int i = 0; i < types.length; ++i) {
            String type = types[i];
            buttons[i].setOnMouseClicked(e -> {
                controller.getSettings().put(MODPACK_TYPE, type);
                controller.onFinish();
            });
        }
    }

    @Override
    public void cleanup(Map<String, Object> settings) {
    }

    @Override
    public String getTitle() {
        return i18n("modpack.wizard.step.3.title");
    }

    public static final String MODPACK_TYPE = "modpack.type";

    public static final String MODPACK_TYPE_MULTIMC = "multimc";
    public static final String MODPACK_TYPE_HMCL = "hmcl";
}
