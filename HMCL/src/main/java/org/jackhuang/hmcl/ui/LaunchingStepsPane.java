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
package org.jackhuang.hmcl.ui;

import com.jfoenix.controls.JFXProgressBar;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;

public class LaunchingStepsPane extends StackPane {
    @FXML
    private JFXProgressBar pgsTasks;
    @FXML
    private Label lblCurrentState;
    @FXML
    private Label lblSteps;

    public LaunchingStepsPane() {
        FXUtils.loadFXML(this, "/assets/fxml/launching-steps.fxml");

        FXUtils.limitHeight(this, 200);
        FXUtils.limitWidth(this, 400);
    }

    public void setCurrentState(String currentState) {
        lblCurrentState.setText(currentState);
    }

    public void setSteps(String steps) {
        lblSteps.setText(steps);
    }

    public void setProgress(double progress) {
        pgsTasks.setProgress(progress);
    }
}
