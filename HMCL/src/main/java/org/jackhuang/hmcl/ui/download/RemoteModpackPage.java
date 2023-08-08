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
import org.jackhuang.hmcl.mod.server.ServerModpackManifest;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane;
import org.jackhuang.hmcl.ui.construct.RequiredValidator;
import org.jackhuang.hmcl.ui.construct.Validator;
import org.jackhuang.hmcl.ui.wizard.WizardController;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

import static org.jackhuang.hmcl.util.Lang.tryCast;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public final class RemoteModpackPage extends ModpackPage {
    private final ServerModpackManifest manifest;

    public RemoteModpackPage(WizardController controller) {
        super(controller);

        manifest = tryCast(controller.getSettings().get(MODPACK_SERVER_MANIFEST), ServerModpackManifest.class)
                .orElseThrow(() -> new IllegalStateException("MODPACK_SERVER_MANIFEST should exist"));
        lblModpackLocation.setText(manifest.getFileApi());

        try {
            controller.getSettings().put(MODPACK_MANIFEST, manifest.toModpack(null));
        } catch (IOException e) {
            Controllers.dialog(i18n("modpack.type.server.malformed"), i18n("message.error"), MessageDialogPane.MessageType.ERROR);
            Platform.runLater(controller::onEnd);
            return;
        }

        lblName.setText(manifest.getName());
        lblVersion.setText(manifest.getVersion());
        lblAuthor.setText(manifest.getAuthor());

        Profile profile = (Profile) controller.getSettings().get("PROFILE");
        Optional<String> name = tryCast(controller.getSettings().get(MODPACK_NAME), String.class);
        if (name.isPresent()) {
            txtModpackName.setText(name.get());
            txtModpackName.setDisable(true);
        } else {
            // trim: https://github.com/huanghongxun/HMCL/issues/962
            txtModpackName.setText(manifest.getName().trim());
            txtModpackName.getValidators().addAll(
                    new RequiredValidator(),
                    new Validator(i18n("install.new_game.already_exists"), str -> !profile.getRepository().versionIdConflicts(str)),
                    new Validator(i18n("install.new_game.malformed"), HMCLGameRepository::isValidVersionId));
        }
    }

    @Override
    public void cleanup(Map<String, Object> settings) {
        settings.remove(MODPACK_SERVER_MANIFEST);
    }

    protected void onInstall() {
        if (!txtModpackName.validate()) return;
        controller.getSettings().put(MODPACK_NAME, txtModpackName.getText());
        controller.onFinish();
    }

    protected void onDescribe() {
        FXUtils.showWebDialog(i18n("modpack.description"), manifest.getDescription());
    }

    public static final String MODPACK_SERVER_MANIFEST = "MODPACK_SERVER_MANIFEST";
    public static final String MODPACK_NAME = "MODPACK_NAME";
    public static final String MODPACK_MANIFEST = "MODPACK_MANIFEST";
}
