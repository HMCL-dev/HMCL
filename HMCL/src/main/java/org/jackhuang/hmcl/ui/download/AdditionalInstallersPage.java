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

import com.jfoenix.controls.JFXButton;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.game.GameRepository;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.wizard.WizardController;
import org.jackhuang.hmcl.ui.wizard.WizardPage;
import org.jackhuang.hmcl.util.Lang;

import java.util.Map;

import static org.jackhuang.hmcl.Main.i18n;

class AdditionalInstallersPage extends StackPane implements WizardPage {
    private final InstallerWizardProvider provider;
    private final WizardController controller;
    private final GameRepository repository;
    private final DownloadProvider downloadProvider;

    @FXML private VBox list;
    @FXML private JFXButton btnForge;
    @FXML private JFXButton btnLiteLoader;
    @FXML private JFXButton btnOptiFine;
    @FXML private Label lblGameVersion;
    @FXML private Label lblVersionName;
    @FXML private Label lblForge;
    @FXML private Label lblLiteLoader;
    @FXML
    private Label lblOptiFine;
    @FXML private JFXButton btnInstall;

    public AdditionalInstallersPage(InstallerWizardProvider provider, WizardController controller, GameRepository repository, DownloadProvider downloadProvider) {
        this.provider = provider;
        this.controller = controller;
        this.repository = repository;
        this.downloadProvider = downloadProvider;

        FXUtils.loadFXML(this, "/assets/fxml/download/additional-installers.fxml");

        lblGameVersion.setText(provider.getGameVersion());
        lblVersionName.setText(provider.getVersion().getId());

        btnForge.setOnMouseClicked(e -> {
            controller.getSettings().put(INSTALLER_TYPE, 0);
            controller.onNext(new VersionsPage(controller, i18n("install.installer.choose", i18n("install.installer.forge")), provider.getGameVersion(), downloadProvider, "forge", () -> { controller.onPrev(false); }));
        });

        btnLiteLoader.setOnMouseClicked(e -> {
            controller.getSettings().put(INSTALLER_TYPE, 1);
            controller.onNext(new VersionsPage(controller, i18n("install.installer.choose", i18n("install.installer.liteloader")), provider.getGameVersion(), downloadProvider, "liteloader", () -> { controller.onPrev(false); }));
        });

        btnOptiFine.setOnMouseClicked(e -> {
            controller.getSettings().put(INSTALLER_TYPE, 2);
            controller.onNext(new VersionsPage(controller, i18n("install.installer.choose", i18n("install.installer.optifine")), provider.getGameVersion(), downloadProvider, "optifine", () -> { controller.onPrev(false); }));
        });
    }

    @Override
    public String getTitle() {
        return "Choose a game version";
    }

    @Override
    public void onNavigate(Map<String, Object> settings) {
        lblGameVersion.setText("Current Game Version: " + provider.getGameVersion());
        btnForge.setDisable(provider.getForge() != null);
        if (provider.getForge() != null || controller.getSettings().containsKey("forge"))
            lblForge.setText("Forge Versoin: " + Lang.nonNull(provider.getForge(), controller.getSettings().get("forge")));
        else
            lblForge.setText("Forge not installed");

        btnLiteLoader.setDisable(provider.getLiteLoader() != null);
        if (provider.getLiteLoader() != null || controller.getSettings().containsKey("liteloader"))
            lblLiteLoader.setText("LiteLoader Versoin: " + Lang.nonNull(provider.getLiteLoader(), controller.getSettings().get("liteloader")));
        else
            lblLiteLoader.setText("LiteLoader not installed");

        btnOptiFine.setDisable(provider.getOptiFine() != null);
        if (provider.getOptiFine() != null || controller.getSettings().containsKey("optifine"))
            lblOptiFine.setText("OptiFine Versoin: " + Lang.nonNull(provider.getOptiFine(), controller.getSettings().get("optifine")));
        else
            lblOptiFine.setText("OptiFine not installed");

    }

        @Override public void cleanup(Map<String, Object> settings) {
            settings.remove(INSTALLER_TYPE);
        }

        public void onInstall() {
        controller.onFinish();
        }

        public static final String INSTALLER_TYPE = "INSTALLER_TYPE";
}
