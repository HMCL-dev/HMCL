/**
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
package org.jackhuang.hmcl.ui;

import com.jfoenix.effects.JFXDepthManager;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;

import java.util.function.Consumer;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/**
 * @author huangyuhui
 */
public class InstallerItem extends BorderPane {
    private final Consumer<InstallerItem> deleteCallback;

    @FXML
    private Label lblInstallerArtifact;

    @FXML
    private Label lblInstallerVersion;

    public InstallerItem(String artifact, String version, Consumer<InstallerItem> deleteCallback) {
        this.deleteCallback = deleteCallback;
        FXUtils.loadFXML(this, "/assets/fxml/version/installer-item.fxml");

        setStyle("-fx-background-radius: 2; -fx-background-color: white; -fx-padding: 8;");
        JFXDepthManager.setDepth(this, 1);
        lblInstallerArtifact.setText(artifact);
        lblInstallerVersion.setText(i18n("archive.version") + ": " + version);
    }

    @FXML
    private void onDelete() {
        deleteCallback.accept(this);
    }
}
