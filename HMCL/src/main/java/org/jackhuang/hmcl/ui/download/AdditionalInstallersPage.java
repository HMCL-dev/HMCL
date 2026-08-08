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
import org.jackhuang.hmcl.download.RemoteVersion;
import org.jackhuang.hmcl.game.*;
import org.jackhuang.hmcl.ui.InstallerItem;
import org.jackhuang.hmcl.ui.wizard.WizardController;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.SettingsMap;

import java.util.Optional;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

class AdditionalInstallersPage extends AbstractInstallersPage {
    protected final BooleanProperty compatible = new SimpleBooleanProperty();
    protected final String gameVersion;
    protected final GameInstanceManifest manifest;
    protected final HMCLGameInstance instance;

    public AdditionalInstallersPage(HMCLGameInstance instance, String gameVersion, WizardController controller, DownloadProvider downloadProvider) {
        super(controller, gameVersion, downloadProvider);
        this.instance = instance;
        this.gameVersion = gameVersion;
        this.manifest = instance.getManifest();

        txtName.setText(instance.getId().id());
        txtName.setEditable(false);

        for (InstallerItem library : group.getLibraries()) {
            if (library.getComponentType() == GameComponentType.GAME) continue;
            library.setOnRemove(() -> {
                controller.getSettings().put(
                        library.getComponentType().getPatchId(),
                        new UpdateInstallerWizardProvider.RemoveVersionAction(library.getComponentType()));
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

    private String getVersion(GameComponentType type) {
        return Optional.ofNullable(controller.getSettings().get(type.getPatchId()))
                .flatMap(it -> Lang.tryCast(it, RemoteVersion.class))
                .map(RemoteVersion::getSelfVersion).orElse(null);
    }

    @Override
    protected void reload() {
        boolean gameVersionChanged = !instance.getVersion().toString().equals(getVersion(GameComponentType.GAME));
        boolean compatible = true;

        for (InstallerItem library : group.getLibraries()) {
            GameComponentType componentType = library.getComponentType();
            String version = instance.getComponentVersion(library.getComponentType());
            String libraryVersion = Lang.requireNonNullElse(getVersion(componentType), version);
            boolean alreadyInstalled = version != null && !(controller.getSettings().get(componentType.getPatchId()) instanceof UpdateInstallerWizardProvider.RemoveVersionAction);
            if (library.getComponentType() != GameComponentType.GAME && gameVersionChanged && getVersion(componentType) == null && alreadyInstalled) {
                // For third-party libraries, if game version is being changed, and the library is not being reinstalled,
                // warns the user that we should update the library.
                library.versionProperty().set(new InstallerItem.InstalledState(libraryVersion, false, true));
                compatible = false;
            } else if (alreadyInstalled || getVersion(componentType) != null) {
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

    @Override
    protected boolean showExtendPane() {
        return false;
    }

    @Override
    protected void resetDefaultName() {
    }
}
