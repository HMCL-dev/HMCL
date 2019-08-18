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
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.download.LibraryAnalyzer;
import org.jackhuang.hmcl.download.RemoteVersion;
import org.jackhuang.hmcl.game.GameRepository;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.wizard.WizardController;
import org.jackhuang.hmcl.ui.wizard.WizardPage;
import org.jackhuang.hmcl.util.Lang;

import java.util.Map;
import java.util.Optional;

import static org.jackhuang.hmcl.download.LibraryAnalyzer.LibraryType.*;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

class AdditionalInstallersPage extends StackPane implements WizardPage {
    private final InstallerWizardProvider provider;
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
    private Label lblVersionName;
    @FXML
    private Label lblFabric;
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

        JFXButton[] buttons = new JFXButton[]{btnFabric, btnForge, btnLiteLoader, btnOptiFine};
        String[] libraryIds = new String[]{"fabric", "forge", "liteloader", "optifine"};

        for (int i = 0; i < libraryIds.length; ++i) {
            String libraryId = libraryIds[i];
            buttons[i].setOnMouseClicked(e -> {
                controller.onNext(new VersionsPage(controller, i18n("install.installer.choose", i18n("install.installer." + libraryId)), provider.getGameVersion(), downloadProvider, libraryId, () -> {
                    controller.onPrev(false);
                }));
            });
        }

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

        LibraryAnalyzer analyzer = LibraryAnalyzer.analyze(provider.getVersion().resolvePreservingPatches(provider.getProfile().getRepository()));
        String fabric = analyzer.getVersion(FABRIC).orElse(null);
        String forge = analyzer.getVersion(FORGE).orElse(null);
        String liteLoader = analyzer.getVersion(LITELOADER).orElse(null);
        String optiFine = analyzer.getVersion(OPTIFINE).orElse(null);

        JFXButton[] buttons = new JFXButton[]{btnFabric, btnForge, btnLiteLoader, btnOptiFine};
        Label[] labels = new Label[]{lblFabric, lblForge, lblLiteLoader, lblOptiFine};
        String[] libraryIds = new String[]{"fabric", "forge", "liteloader", "optifine"};
        String[] versions = new String[]{fabric, forge, liteLoader, optiFine};

        for (int i = 0; i < libraryIds.length; ++i) {
            String libraryId = libraryIds[i];
            buttons[i].setDisable(versions[i] != null);
            if (versions[i] != null || controller.getSettings().containsKey(libraryId))
                labels[i].setText(i18n("install.installer.version", i18n("install.installer." + libraryId)) + ": " + Lang.nonNull(versions[i], getVersion(libraryId)));
            else
                labels[i].setText(i18n("install.installer.not_installed", i18n("install.installer." + libraryId)));
        }
    }

    @Override
    public void cleanup(Map<String, Object> settings) {
    }
}
