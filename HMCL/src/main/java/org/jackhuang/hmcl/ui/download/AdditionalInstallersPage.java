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

    @Override
    protected void onInstall() {
        controller.onFinish();
    }

    @Override
    public String getTitle() {
        return i18n("install.change_version.title", instance.getId().id());
    }

    private String getVersion(GameComponentType type) {
        return Optional.ofNullable(controller.getSettings().get(type.getPatchId()))
                .flatMap(it -> Lang.tryCast(it, ComponentRemoteVersion.class))
                .map(ComponentRemoteVersion::getSelfVersion).orElse(null);
    }

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
