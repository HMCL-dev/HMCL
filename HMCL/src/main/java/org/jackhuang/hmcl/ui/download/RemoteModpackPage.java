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

import javafx.application.Platform;
import org.jackhuang.hmcl.game.HMCLGameRepository;
import org.jackhuang.hmcl.mod.Modpack;
import org.jackhuang.hmcl.mod.server.ServerModpackManifest;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.WebPage;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane;
import org.jackhuang.hmcl.ui.construct.RequiredValidator;
import org.jackhuang.hmcl.ui.construct.Validator;
import org.jackhuang.hmcl.ui.wizard.WizardController;
import org.jackhuang.hmcl.util.SettingsMap;
import org.jackhuang.hmcl.util.StringUtils;

import java.io.IOException;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public final class RemoteModpackPage extends ModpackPage {
    private final ServerModpackManifest manifest;

    public RemoteModpackPage(WizardController controller) {
        super(controller);

        manifest = controller.getSettings().get(MODPACK_SERVER_MANIFEST);
        if (manifest == null)
            throw new IllegalStateException("MODPACK_SERVER_MANIFEST should exist");

        try {
            controller.getSettings().put(MODPACK_MANIFEST, manifest.toModpack(null));
        } catch (IOException e) {
            Controllers.dialog(i18n("modpack.type.server.malformed"), i18n("message.error"), MessageDialogPane.MessageType.ERROR);
            Platform.runLater(controller::onEnd);
            return;
        }

        nameProperty.set(manifest.getName());
        versionProperty.set(manifest.getVersion());
        authorProperty.set(manifest.getAuthor());

        Profile profile = controller.getSettings().get(ModpackPage.PROFILE);
        String name = controller.getSettings().get(MODPACK_NAME);
        if (name != null) {
            txtModpackName.setText(name);
            txtModpackName.setDisable(true);
        } else {
            // trim: https://github.com/HMCL-dev/HMCL/issues/962
            txtModpackName.setText(manifest.getName().trim());
            txtModpackName.getValidators().addAll(
                    new RequiredValidator(),
                    new Validator(i18n("install.new_game.already_exists"), str -> !profile.getRepository().versionIdConflicts(str)),
                    new Validator(i18n("install.new_game.malformed"), HMCLGameRepository::isValidVersionId));
        }

        btnDescription.setVisible(StringUtils.isNotBlank(manifest.getDescription()));
    }

    @Override
    public void cleanup(SettingsMap settings) {
        settings.remove(MODPACK_SERVER_MANIFEST);
    }

    protected void onInstall() {
        if (!txtModpackName.validate()) return;
        controller.getSettings().put(MODPACK_NAME, txtModpackName.getText());
        controller.onFinish();
    }

    protected void onDescribe() {
        Controllers.navigate(new WebPage(i18n("modpack.description"), manifest.getDescription()));
    }

    public static final SettingsMap.Key<ServerModpackManifest> MODPACK_SERVER_MANIFEST = new SettingsMap.Key<>("MODPACK_SERVER_MANIFEST");
    public static final SettingsMap.Key<String> MODPACK_NAME = new SettingsMap.Key<>("MODPACK_NAME");
    public static final SettingsMap.Key<Modpack> MODPACK_MANIFEST = new SettingsMap.Key<>("MODPACK_MANIFEST");
}
