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
package org.jackhuang.hmcl.ui.download;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXTextField;
import com.jfoenix.effects.JFXDepthManager;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.download.RemoteVersion;
import org.jackhuang.hmcl.game.GameRepository;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.construct.Validator;
import org.jackhuang.hmcl.ui.wizard.WizardController;
import org.jackhuang.hmcl.ui.wizard.WizardPage;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.platform.OperatingSystem;

import java.nio.file.Paths;
import java.util.Map;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class InstallersPage extends StackPane implements WizardPage {
    protected final WizardController controller;

    @FXML
    protected VBox list;

    @FXML
    protected Node btnGame;

    @FXML
    protected Node btnFabric;

    @FXML
    protected Node btnForge;

    @FXML
    protected Node btnLiteLoader;

    @FXML
    protected Node btnOptiFine;

    @FXML
    protected Label lblGame;

    @FXML
    protected Label lblFabric;

    @FXML
    protected Label lblForge;

    @FXML
    protected Label lblLiteLoader;

    @FXML
    protected Label lblOptiFine;

    @FXML
    protected JFXTextField txtName;

    @FXML
    protected JFXButton btnInstall;

    public InstallersPage(WizardController controller, GameRepository repository, String gameVersion, DownloadProvider downloadProvider) {
        this.controller = controller;

        FXUtils.loadFXML(this, "/assets/fxml/download/installers.fxml");

        Validator hasVersion = new Validator(s -> !repository.hasVersion(s) && StringUtils.isNotBlank(s));
        hasVersion.setMessage(i18n("install.new_game.already_exists"));
        Validator nameValidator = new Validator(OperatingSystem::isNameValid);
        nameValidator.setMessage(i18n("install.new_game.malformed"));
        txtName.getValidators().addAll(hasVersion, nameValidator);
        txtName.textProperty().addListener(e -> btnInstall.setDisable(!txtName.validate()));
        txtName.setText(gameVersion);

        Label[] labels = new Label[]{lblGame, lblFabric, lblForge, lblLiteLoader, lblOptiFine};
        Node[] buttons = new Node[]{btnGame, btnFabric, btnForge, btnLiteLoader, btnOptiFine};
        String[] libraryIds = new String[]{"game", "fabric", "forge", "liteloader", "optifine"};

        for (Node node : list.getChildren()) {
            JFXDepthManager.setDepth(node, 1);
        }

        for (int i = 0; i < libraryIds.length; ++i) {
            String libraryId = libraryIds[i];
            BorderPane.setMargin(labels[i], new Insets(0, 0, 0, 8));
            if (libraryId.equals("game")) continue;
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
        Label[] labels = new Label[]{lblGame, lblFabric, lblForge, lblLiteLoader, lblOptiFine};
        String[] libraryIds = new String[]{"game", "fabric", "forge", "liteloader", "optifine"};

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
    protected void onInstall() {
        controller.getSettings().put("name", txtName.getText());
        controller.onFinish();
    }
}
