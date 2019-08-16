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
package org.jackhuang.hmcl.ui.download;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXTextField;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.download.RemoteVersion;
import org.jackhuang.hmcl.game.GameRepository;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.construct.Validator;
import org.jackhuang.hmcl.ui.wizard.WizardController;
import org.jackhuang.hmcl.ui.wizard.WizardPage;
import org.jackhuang.hmcl.util.StringUtils;

import java.util.Map;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class InstallersPage extends StackPane implements WizardPage {
    private final WizardController controller;

    @FXML
    private VBox list;

    @FXML
    private JFXButton btnFabric;

    @FXML
    private JFXButton btnForge;

    @FXML
    private JFXButton btnLiteLoader;

    @FXML
    private JFXButton btnOptiFine;

    @FXML
    private Label lblGameVersion;

    @FXML
    private Label lblFabric;

    @FXML
    private Label lblForge;

    @FXML
    private Label lblLiteLoader;

    @FXML
    private Label lblOptiFine;

    @FXML
    private JFXTextField txtName;

    @FXML
    private JFXButton btnInstall;

    public InstallersPage(WizardController controller, GameRepository repository, DownloadProvider downloadProvider) {
        this.controller = controller;

        FXUtils.loadFXML(this, "/assets/fxml/download/installers.fxml");

        String gameVersion = ((RemoteVersion) controller.getSettings().get("game")).getGameVersion();
        Validator hasVersion = new Validator(s -> !repository.hasVersion(s) && StringUtils.isNotBlank(s));
        hasVersion.setMessage(i18n("install.new_game.already_exists"));
        txtName.getValidators().add(hasVersion);
        txtName.textProperty().addListener(e -> btnInstall.setDisable(!txtName.validate()));
        txtName.setText(gameVersion);

        JFXButton[] buttons = new JFXButton[]{btnFabric, btnForge, btnLiteLoader, btnOptiFine};
        String[] libraryIds = new String[]{"fabric", "forge", "liteloader", "optifine"};

        for (int i = 0; i < libraryIds.length; ++i) {
            String libraryId = libraryIds[i];
            buttons[i].setOnMouseClicked(e ->
                    controller.onNext(new VersionsPage(controller, i18n("install.installer.choose", i18n("install.installer." + libraryId)), gameVersion, downloadProvider, libraryId, () -> controller.onPrev(false))));
        }
    }

    @Override
    public String getTitle() {
        return i18n("install.new_game");
    }

    private String getVersion(String id) {
        return ((RemoteVersion) controller.getSettings().get(id)).getSelfVersion();
    }

    @Override
    public void onNavigate(Map<String, Object> settings) {
        lblGameVersion.setText(i18n("install.new_game.current_game_version") + ": " + getVersion("game"));

        Label[] labels = new Label[]{lblFabric, lblForge, lblLiteLoader, lblOptiFine};
        String[] libraryIds = new String[]{"fabric", "forge", "liteloader", "optifine"};

        for (int i = 0; i < libraryIds.length; ++i) {
            String libraryId = libraryIds[i];
            if (controller.getSettings().containsKey(libraryId))
                labels[i].setText(i18n("install.installer.version", i18n("install.installer." + libraryId)) + ": " + getVersion(libraryId));
            else
                labels[i].setText(i18n("install.installer.not_installed", i18n("install.installer." + libraryId)));
        }
    }

    @Override
    public void cleanup(Map<String, Object> settings) {
    }

    @FXML
    private void onInstall() {
        controller.getSettings().put("name", txtName.getText());
        controller.onFinish();
    }
}
