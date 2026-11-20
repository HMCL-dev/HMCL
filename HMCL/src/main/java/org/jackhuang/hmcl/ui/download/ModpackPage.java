// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.download;

import javafx.beans.property.StringProperty;
import javafx.geometry.Pos;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.glavo.m3fx.controls.M3Button;
import org.glavo.m3fx.controls.M3ButtonVariant;
import org.glavo.m3fx.controls.M3FormPane;
import org.glavo.m3fx.controls.M3FormRow;
import org.glavo.m3fx.controls.M3TextField;
import org.glavo.m3fx.controls.M3TextInputLayout;
import org.jackhuang.hmcl.game.HMCLGameRepository;
import org.jackhuang.hmcl.setting.GameDirectory;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.construct.SpinnerPane;
import org.jackhuang.hmcl.ui.wizard.WizardController;
import org.jackhuang.hmcl.ui.wizard.WizardPage;
import org.jackhuang.hmcl.util.SettingsMap;
import org.jackhuang.hmcl.util.i18n.I18n;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public abstract class ModpackPage extends SpinnerPane implements WizardPage {
    public static final SettingsMap.Key<GameDirectory> GAME_DIRECTORY = new SettingsMap.Key<>("GAME_DIRECTORY");
    public static final SettingsMap.Key<HMCLGameRepository> REPOSITORY = new SettingsMap.Key<>("REPOSITORY");

    protected final WizardController controller;

    protected final StringProperty nameProperty;
    protected final StringProperty versionProperty;
    protected final StringProperty authorProperty;
    protected final M3TextField txtModpackName;
    /// Validates the name against the current installation target.
    protected final M3TextInputLayout nameLayout;
    protected final M3Button btnInstall;
    protected final M3Button btnDescription;
    protected final M3Button btnOptionalFiles;

    protected ModpackPage(WizardController controller) {
        this.controller = controller;

        VBox borderPane = new VBox();
        borderPane.setAlignment(Pos.CENTER);
        FXUtils.setLimitWidth(borderPane, 500);

        var form = new M3FormPane();
        {
            txtModpackName = FXUtils.newTextField();
            nameLayout = FXUtils.newValidatedTextField(txtModpackName);
            nameLayout.setPrefWidth(300);
            var name = FXUtils.newCopyableLabel(null);
            var version = FXUtils.newCopyableLabel(null);
            var author = FXUtils.newCopyableLabel(null);
            nameProperty = name.textProperty();
            versionProperty = version.textProperty();
            authorProperty = author.textProperty();

            var descriptionPane = new BorderPane();
            {
                btnDescription = new M3Button(I18n.i18n("modpack.description"), M3ButtonVariant.OUTLINED);
                btnDescription.setOnAction(e -> onDescribe());
                descriptionPane.setLeft(btnDescription);

                var installHBox = new HBox(8);
                btnOptionalFiles = new M3Button(I18n.i18n("modpack.optional_files"), M3ButtonVariant.FILLED);
                btnOptionalFiles.setVisible(false);
                btnOptionalFiles.setManaged(false);
                installHBox.getChildren().add(btnOptionalFiles);

                btnInstall = new M3Button(I18n.i18n("button.install"), M3ButtonVariant.FILLED);
                btnInstall.setOnAction(e -> onInstall());
                var nameInvalid = txtModpackName.errorProperty();
                btnInstall.disableProperty().bind(nameInvalid);
                btnOptionalFiles.disableProperty().bind(nameInvalid);
                installHBox.getChildren().add(btnInstall);
                descriptionPane.setRight(installHBox);
            }

            form.getItems().setAll(
                    new M3FormRow(i18n("instance.name"), nameLayout),
                    new M3FormRow(i18n("modpack.name"), name),
                    new M3FormRow(i18n("archive.version"), version),
                    new M3FormRow(i18n("archive.author"), author),
                    descriptionPane);
        }

        borderPane.getChildren().setAll(form);
        this.setContent(borderPane);
    }

    protected abstract void onInstall();

    protected abstract void onDescribe();

    @Override
    public String getTitle() {
        return i18n("modpack.task.install");
    }
}
