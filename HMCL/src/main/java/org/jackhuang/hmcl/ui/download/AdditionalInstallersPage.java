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

import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.download.LibraryAnalyzer;
import org.jackhuang.hmcl.download.RemoteVersion;
import org.jackhuang.hmcl.game.GameRepository;
import org.jackhuang.hmcl.game.HMCLGameRepository;
import org.jackhuang.hmcl.game.Version;
import org.jackhuang.hmcl.ui.InstallerItem;
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

    public AdditionalInstallersPage(String gameVersion, Version version, WizardController controller, HMCLGameRepository repository, DownloadProvider downloadProvider) {
        super(controller, repository, gameVersion, downloadProvider);
        this.gameVersion = gameVersion;
        this.version = version;
        this.repository = repository;

        txtName.getValidators().clear();
        txtName.setText(version.getId());
        txtName.setEditable(false);

        installable.bind(Bindings.createBooleanBinding(
                () -> compatible.get() && txtName.validate(),
                txtName.textProperty(), compatible));

        for (InstallerItem library : group.getLibraries()) {
            String libraryId = library.getLibraryId();
            if (libraryId.equals("game")) continue;
            library.removeAction.set(e -> {
                controller.getSettings().put(libraryId, new UpdateInstallerWizardProvider.RemoveVersionAction(libraryId));
                reload();
            });
        }
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
        return Optional.ofNullable(controller.getSettings().get(id))
                .flatMap(it -> Lang.tryCast(it, RemoteVersion.class))
                .map(RemoteVersion::getSelfVersion).orElse(null);
    }

    @Override
    protected void reload() {
        LibraryAnalyzer analyzer = LibraryAnalyzer.analyze(version.resolvePreservingPatches(repository));
        String game = analyzer.getVersion(MINECRAFT).orElse(null);
        String forge = analyzer.getVersion(FORGE).orElse(null);
        String liteLoader = analyzer.getVersion(LITELOADER).orElse(null);
        String optiFine = analyzer.getVersion(OPTIFINE).orElse(null);
        String fabric = analyzer.getVersion(FABRIC).orElse(null);
        String fabricApi = analyzer.getVersion(FABRIC_API).orElse(null);

        InstallerItem[] libraries = group.getLibraries();
        String[] versions = new String[]{game, forge, liteLoader, optiFine, fabric, fabricApi};

        String currentGameVersion = Lang.nonNull(getVersion("game"), game);

        boolean compatible = true;
        for (int i = 0; i < libraries.length; ++i) {
            String libraryId = libraries[i].getLibraryId();
            String libraryVersion = Lang.nonNull(getVersion(libraryId), versions[i]);
            boolean alreadyInstalled = versions[i] != null && !(controller.getSettings().get(libraryId) instanceof UpdateInstallerWizardProvider.RemoveVersionAction);
            if (!"game".equals(libraryId) && currentGameVersion != null && !currentGameVersion.equals(game) && getVersion(libraryId) == null && alreadyInstalled) {
                // For third-party libraries, if game version is being changed, and the library is not being reinstalled,
                // warns the user that we should update the library.
                libraries[i].setState(libraryVersion, /* incompatibleWithGame */ true, /* removable */ true);
                compatible = false;
            } else if (alreadyInstalled || getVersion(libraryId) != null) {
                libraries[i].setState(libraryVersion, /* incompatibleWithGame */ false, /* removable */ true);
            } else {
                libraries[i].setState(/* libraryVersion */ null, /* incompatibleWithGame */ false, /* removable */ false);
            }
        }
        this.compatible.set(compatible);
    }

    @Override
    public void cleanup(Map<String, Object> settings) {
    }
}
