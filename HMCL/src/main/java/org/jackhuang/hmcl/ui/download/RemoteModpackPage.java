/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
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
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;

import org.jackhuang.hmcl.game.HMCLGameRepository;
import org.jackhuang.hmcl.mod.server.ServerModpackManifest;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.ui.wizard.WizardController;
import org.jackhuang.hmcl.ui.wizard.WizardPage;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import static javafx.beans.binding.Bindings.createBooleanBinding;
import static org.jackhuang.hmcl.util.Lang.tryCast;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class RemoteModpackPage extends StackPane implements WizardPage {
    private final WizardController controller;

    private final ServerModpackManifest manifest;

    @FXML
    private Region borderPane;

    @FXML
    private Label lblName;

    @FXML
    private Label lblVersion;

    @FXML
    private Label lblAuthor;

    @FXML
    private Label lblModpackLocation;

    @FXML
    private JFXTextField txtModpackName;

    @FXML
    private JFXButton btnInstall;

    @FXML
    private SpinnerPane spinnerPane;

    @FXML
    private ScrollPane scrollPane;

    @FXML
    private OptionalFilesSelectionPane optionalFilesPane;

    public RemoteModpackPage(WizardController controller) {
        this.controller = controller;

        FXUtils.loadFXML(this, "/assets/fxml/download/modpack.fxml");

        Profile profile = (Profile) controller.getSettings().get("PROFILE");

        Optional<String> name = tryCast(controller.getSettings().get(MODPACK_NAME), String.class);
        if (name.isPresent()) {
            txtModpackName.setText(name.get());
            txtModpackName.setDisable(true);
        }

        manifest = tryCast(controller.getSettings().get(MODPACK_SERVER_MANIFEST), ServerModpackManifest.class)
                .orElseThrow(() -> new IllegalStateException("MODPACK_SERVER_MANIFEST should exist"));
        lblModpackLocation.setText(manifest.getFileApi());

        try {
            controller.getSettings().put(MODPACK_MANIFEST, manifest.toModpack(null));
        } catch (IOException e) {
            Controllers.dialog(i18n("modpack.type.server.malformed"), i18n("message.error"), MessageDialogPane.MessageType.ERROR);
            Platform.runLater(controller::onEnd);
            return;
        }

        lblName.setText(manifest.getName());
        lblVersion.setText(manifest.getVersion());
        lblAuthor.setText(manifest.getAuthor());
        optionalFilesPane.updateOptionalFileList(Collections.emptyList());

        if (!name.isPresent()) {
            // trim: https://github.com/huanghongxun/HMCL/issues/962
            txtModpackName.setText(manifest.getName().trim());
            txtModpackName.getValidators().addAll(
                    new RequiredValidator(),
                    new Validator(i18n("install.new_game.already_exists"), str -> !profile.getRepository().versionIdConflicts(str)),
                    new Validator(i18n("install.new_game.malformed"), HMCLGameRepository::isValidVersionId));
            btnInstall.disableProperty().bind(
                    createBooleanBinding(txtModpackName::validate, txtModpackName.textProperty())
                            .not());
        }
    }

    @Override
    public void cleanup(Map<String, Object> settings) {
        settings.remove(MODPACK_SERVER_MANIFEST);
    }

    @FXML
    private void onInstall() {
        if (!txtModpackName.validate()) return;
        controller.getSettings().put(MODPACK_NAME, txtModpackName.getText());
        controller.onFinish();
    }

    @FXML
    private void onDescribe() {
        FXUtils.showWebDialog(i18n("modpack.description"), manifest.getDescription());
    }

    @Override
    public String getTitle() {
        return i18n("modpack.task.install");
    }

    public static final String MODPACK_SERVER_MANIFEST = "MODPACK_SERVER_MANIFEST";
    public static final String MODPACK_NAME = "MODPACK_NAME";
    public static final String MODPACK_MANIFEST = "MODPACK_MANIFEST";
}
