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
import org.jackhuang.hmcl.util.SettingsMap;

import java.util.Optional;

import static org.jackhuang.hmcl.download.LibraryAnalyzer.LibraryType.MINECRAFT;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

class AdditionalInstallersPage extends AbstractInstallersPage {
    protected final BooleanProperty compatible = new SimpleBooleanProperty();
    protected final GameRepository repository;
    protected final String gameVersion;
    protected final Version version;

    public AdditionalInstallersPage(String gameVersion, Version version, WizardController controller, HMCLGameRepository repository, DownloadProvider downloadProvider) {
        super(controller, gameVersion, downloadProvider);
        this.gameVersion = gameVersion;
        this.version = version;
        this.repository = repository;

        txtName.setText(version.getId());
        txtName.setEditable(false);

        for (InstallerItem library : group.getLibraries()) {
            String libraryId = library.getLibraryId();
            if (libraryId.equals("game")) continue;
            library.setOnRemove(() -> {
                controller.getSettings().put(libraryId, new UpdateInstallerWizardProvider.RemoveVersionAction(libraryId));
                reload();
            });
        }

        installable.bind(Bindings.createBooleanBinding(() -> compatible.get() && txtName.validate(), txtName.textProperty(), compatible));
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
        Version resolved = version.resolvePreservingPatches(repository);
        LibraryAnalyzer analyzer = LibraryAnalyzer.analyze(resolved, repository.getGameVersion(resolved).orElse(null));
        String game = analyzer.getVersion(MINECRAFT).orElse(null);
        String currentGameVersion = Lang.nonNull(getVersion("game"), game);

        boolean compatible = true;

        for (InstallerItem library : group.getLibraries()) {
            String libraryId = library.getLibraryId();
            String version = analyzer.getVersion(libraryId).orElse(null);
            String libraryVersion = Lang.requireNonNullElse(getVersion(libraryId), version);
            boolean alreadyInstalled = version != null && !(controller.getSettings().get(libraryId) instanceof UpdateInstallerWizardProvider.RemoveVersionAction);
            if (!"game".equals(libraryId) && currentGameVersion != null && !currentGameVersion.equals(game) && getVersion(libraryId) == null && alreadyInstalled) {
                // For third-party libraries, if game version is being changed, and the library is not being reinstalled,
                // warns the user that we should update the library.
                library.versionProperty().set(new InstallerItem.InstalledState(libraryVersion, false, true));
                compatible = false;
            } else if (alreadyInstalled || getVersion(libraryId) != null) {
                library.versionProperty().set(new InstallerItem.InstalledState(libraryVersion, false, false));
            } else {
                library.versionProperty().set(null);
            }
        }

        this.compatible.set(compatible);
    }

    @Override
    public void cleanup(SettingsMap settings) {
    }
}
