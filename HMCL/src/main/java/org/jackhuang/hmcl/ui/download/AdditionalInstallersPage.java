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

import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.control.Label;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.download.LibraryAnalyzer;
import org.jackhuang.hmcl.download.RemoteVersion;
import org.jackhuang.hmcl.game.GameRepository;
import org.jackhuang.hmcl.game.Version;
import org.jackhuang.hmcl.ui.wizard.WizardController;
import org.jackhuang.hmcl.util.Lang;

import java.util.Map;
import java.util.Optional;

import static org.jackhuang.hmcl.download.LibraryAnalyzer.LibraryType.*;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

class AdditionalInstallersPage extends InstallersPage {
    protected final BooleanProperty compatible = new SimpleBooleanProperty();
    protected final GameRepository repository;
    protected final String gameVersion;
    protected final Version version;

    public AdditionalInstallersPage(String gameVersion, Version version, WizardController controller, GameRepository repository, DownloadProvider downloadProvider) {
        super(controller, repository, gameVersion, downloadProvider);
        this.gameVersion = gameVersion;
        this.version = version;
        this.repository = repository;

        txtName.getValidators().clear();
        txtName.setText(version.getId());
        txtName.setEditable(false);

        btnInstall.disableProperty().bind(Bindings.createBooleanBinding(
                () -> !compatible.get() || !txtName.validate(),
                txtName.textProperty(), compatible));
    }

    @Override
    protected void onInstall() {
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
        LibraryAnalyzer analyzer = LibraryAnalyzer.analyze(version.resolvePreservingPatches(repository));
        String game = analyzer.getVersion(MINECRAFT).orElse(null);
        String fabric = analyzer.getVersion(FABRIC).orElse(null);
        String forge = analyzer.getVersion(FORGE).orElse(null);
        String liteLoader = analyzer.getVersion(LITELOADER).orElse(null);
        String optiFine = analyzer.getVersion(OPTIFINE).orElse(null);

        Label[] labels = new Label[]{lblGame, lblFabric, lblForge, lblLiteLoader, lblOptiFine};
        String[] libraryIds = new String[]{"game", "fabric", "forge", "liteloader", "optifine"};
        String[] versions = new String[]{game, fabric, forge, liteLoader, optiFine};

        String currentGameVersion = Lang.nonNull(getVersion("game"), game);

        boolean compatible = true;
        for (int i = 0; i < libraryIds.length; ++i) {
            String libraryId = libraryIds[i];
            String libraryVersion = Lang.nonNull(getVersion(libraryId), versions[i]);
            boolean alreadyInstalled = versions[i] != null;
            if (!"game".equals(libraryId) && currentGameVersion != null && !currentGameVersion.equals(game) && getVersion(libraryId) == null && alreadyInstalled) {
                // For third-party libraries, if game version is being changed, and the library is not being reinstalled,
                // warns the user that we should update the library.
                labels[i].setText(i18n("install.installer.change_version", i18n("install.installer." + libraryId), libraryVersion));
                compatible = false;
            } else if (alreadyInstalled || controller.getSettings().containsKey(libraryId)) {
                labels[i].setText(i18n("install.installer.version", i18n("install.installer." + libraryId)) + ": " + libraryVersion);
            } else {
                labels[i].setText(i18n("install.installer.not_installed", i18n("install.installer." + libraryId)));
            }
        }
        this.compatible.set(compatible);
    }

    @Override
    public void cleanup(Map<String, Object> settings) {
    }
}
