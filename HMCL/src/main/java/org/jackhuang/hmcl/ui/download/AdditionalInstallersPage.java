/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2018  huangyuhui <huanghongxun2008@126.com>
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

import com.jfoenix.controls.JFXButton;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.download.RemoteVersion;
import org.jackhuang.hmcl.game.GameRepository;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.wizard.WizardController;
import org.jackhuang.hmcl.ui.wizard.WizardPage;
import org.jackhuang.hmcl.util.Lang;

import java.util.Map;
import java.util.Optional;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

class AdditionalInstallersPage extends StackPane implements WizardPage {
    private final InstallerWizardProvider provider;
    private final WizardController controller;

    @FXML
    private VBox list;
    @FXML
    private JFXButton btnForge;
    @FXML
    private JFXButton btnLiteLoader;
    @FXML
    private JFXButton btnOptiFine;
    @FXML
    private Label lblGameVersion;
    @FXML
    private Label lblVersionName;
    @FXML
    private Label lblForge;
    @FXML
    private Label lblLiteLoader;
    @FXML
    private Label lblOptiFine;
    @FXML
    private JFXButton btnInstall;

    public AdditionalInstallersPage(InstallerWizardProvider provider, WizardController controller, GameRepository repository, DownloadProvider downloadProvider) {
        this.provider = provider;
        this.controller = controller;

        FXUtils.loadFXML(this, "/assets/fxml/download/additional-installers.fxml");

        lblGameVersion.setText(provider.getGameVersion());
        lblVersionName.setText(provider.getVersion().getId());

        btnForge.setOnMouseClicked(e -> {
            controller.getSettings().put(INSTALLER_TYPE, 0);
            controller.onNext(new VersionsPage(controller, i18n("install.installer.choose", i18n("install.installer.forge")), provider.getGameVersion(), downloadProvider, "forge", () -> {
                controller.onPrev(false);
            }));
        });

        btnLiteLoader.setOnMouseClicked(e -> {
            controller.getSettings().put(INSTALLER_TYPE, 1);
            controller.onNext(new VersionsPage(controller, i18n("install.installer.choose", i18n("install.installer.liteloader")), provider.getGameVersion(), downloadProvider, "liteloader", () -> {
                controller.onPrev(false);
            }));
        });

        btnOptiFine.setOnMouseClicked(e -> {
            controller.getSettings().put(INSTALLER_TYPE, 2);
            controller.onNext(new VersionsPage(controller, i18n("install.installer.choose", i18n("install.installer.optifine")), provider.getGameVersion(), downloadProvider, "optifine", () -> {
                controller.onPrev(false);
            }));
        });

        btnInstall.setOnMouseClicked(e -> onInstall());
    }

    private void onInstall() {
        controller.onFinish();
    }

    @Override
    public String getTitle() {
        return i18n("settings.tabs.installers");
    }

    private String getVersion(String id) {
        return Optional.ofNullable(controller.getSettings().get(id)).map(it -> (RemoteVersion) it).map(RemoteVersion::getSelfVersion).orElse(null);
    }

    @Override
    public void onNavigate(Map<String, Object> settings) {
        lblGameVersion.setText(i18n("install.new_game.current_game_version") + ": " + provider.getGameVersion());
        btnForge.setDisable(provider.getForge() != null);
        if (provider.getForge() != null || controller.getSettings().containsKey("forge"))
            lblForge.setText(i18n("install.installer.version", i18n("install.installer.forge")) + ": " + Lang.nonNull(provider.getForge(), getVersion("forge")));
        else
            lblForge.setText(i18n("install.installer.not_installed", i18n("install.installer.forge")));

        btnLiteLoader.setDisable(provider.getLiteLoader() != null);
        if (provider.getLiteLoader() != null || controller.getSettings().containsKey("liteloader"))
            lblLiteLoader.setText(i18n("install.installer.version", i18n("install.installer.liteloader")) + ": " + Lang.nonNull(provider.getLiteLoader(), getVersion("liteloader")));
        else
            lblLiteLoader.setText(i18n("install.installer.not_installed", i18n("install.installer.liteloader")));

        btnOptiFine.setDisable(provider.getOptiFine() != null);
        if (provider.getOptiFine() != null || controller.getSettings().containsKey("optifine"))
            lblOptiFine.setText(i18n("install.installer.version", i18n("install.installer.optifine")) + ": " + Lang.nonNull(provider.getOptiFine(), getVersion("optifine")));
        else
            lblOptiFine.setText(i18n("install.installer.not_installed", i18n("install.installer.optifine")));

    }

    @Override
    public void cleanup(Map<String, Object> settings) {
        settings.remove(INSTALLER_TYPE);
    }

    public static final String INSTALLER_TYPE = "INSTALLER_TYPE";
}
