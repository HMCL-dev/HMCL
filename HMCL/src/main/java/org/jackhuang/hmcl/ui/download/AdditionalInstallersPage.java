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
import org.jackhuang.hmcl.download.ComponentRemoteVersion;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.game.GameComponentType;
import org.jackhuang.hmcl.game.GameInstanceManifest;
import org.jackhuang.hmcl.game.HMCLGameInstance;
import org.jackhuang.hmcl.download.RemoteVersion;
import org.jackhuang.hmcl.game.GameComponentType;
import org.jackhuang.hmcl.game.HMCLGameInstance;
import org.jackhuang.hmcl.ui.InstallerItem;
import org.jackhuang.hmcl.ui.wizard.WizardController;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.SettingsMap;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// Displays optional component changes while updating an existing game instance.
class AdditionalInstallersPage extends AbstractInstallersPage {
    /// Creates a page for selecting additional component changes.
    ///
    /// @param instance the instance being updated
    /// @param gameVersion the selected game version
    /// @param controller the wizard controller that stores the selected changes
    /// @param downloadProvider the provider used to retrieve available component versions
    public AdditionalInstallersPage(HMCLGameInstance instance, String gameVersion, WizardController controller, DownloadProvider downloadProvider) {
        super(controller, gameVersion, downloadProvider);

        txtName.setText(instance.getId().id());
        txtName.setEditable(false);

        for (InstallerItem component : group.getComponents()) {
            if (component.getComponentType() == GameComponentType.GAME) continue;
            component.setOnRemove(() -> {
                controller.getSettings().put(
                        component.getComponentType().getPatchId(),
                        new UpdateInstallerWizardProvider.RemoveComponentAction(component.getComponentType()));
                reload();
            });
        }

        installable.bind(Bindings.createBooleanBinding(() -> txtName.validate(), txtName.textProperty()));
    }

    /// Finishes the update wizard.
    @Override
    protected void onInstall() {
        controller.onFinish();
    }

    /// Returns the title of the installer selection page.
    ///
    /// @return the localized page title
    @Override
    public String getTitle() {
        return i18n("install.change_version.title", instance.getId().id());
    }

    /// Returns the selected version of a component, or `null` if no version is selected.
    ///
    /// @param type the component type
    /// @return the selected component version, or `null`
    private @Nullable String getVersion(GameComponentType type) {
        return Optional.ofNullable(controller.getSettings().get(type.getPatchId()))
                .flatMap(it -> Lang.tryCast(it, ComponentRemoteVersion.class))
                .map(ComponentRemoteVersion::getSelfVersion).orElse(null);
    }

    /// Refreshes component states from the changes selected in the wizard.
    @Override
    protected void reload() {
        for (InstallerItem component : group.getComponents()) {
            GameComponentType componentType = component.getComponentType();
            if (controller.getSettings().containsKey(componentType.getPatchId())) {
                component.versionProperty().set(new InstallerItem.InstalledState(getVersion(componentType), false, false));
            } else {
                component.versionProperty().set(null);
            }
        }
    }

    /// Leaves the wizard settings unchanged when navigating away from this page.
    ///
    /// @param settings the wizard settings
    @Override
    public void cleanup(SettingsMap settings) {
    }

    /// Returns whether name-field extension controls are displayed.
    ///
    /// @return `false`
    @Override
    protected boolean showExtendPane() {
        return false;
    }

    /// Performs no action because the instance name cannot be edited on this page.
    @Override
    protected void resetDefaultName() {
    }
}
