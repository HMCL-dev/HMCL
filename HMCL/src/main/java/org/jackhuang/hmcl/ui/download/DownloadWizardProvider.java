/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2018  huangyuhui <huanghongxun2008@126.com>
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

import javafx.scene.Node;

import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.download.GameBuilder;
import org.jackhuang.hmcl.download.RemoteVersion;
import org.jackhuang.hmcl.game.ModpackHelper;
import org.jackhuang.hmcl.mod.Modpack;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.setting.Settings;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.wizard.WizardController;
import org.jackhuang.hmcl.ui.wizard.WizardProvider;
import org.jackhuang.hmcl.util.Lang;
import java.io.File;
import java.util.Map;

import static org.jackhuang.hmcl.util.Lang.tryCast;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public final class DownloadWizardProvider implements WizardProvider {
    private Profile profile;

    @Override
    public void start(Map<String, Object> settings) {
        profile = Settings.INSTANCE.getSelectedProfile();
        settings.put(PROFILE, profile);
    }

    private Task finishVersionDownloadingAsync(Map<String, Object> settings) {
        GameBuilder builder = profile.getDependency().gameBuilder();

        builder.name((String) settings.get("name"));
        builder.gameVersion(((RemoteVersion<?>) settings.get("game")).getGameVersion());

        if (settings.containsKey("forge"))
            builder.version((RemoteVersion<?>) settings.get("forge"));

        if (settings.containsKey("liteloader"))
            builder.version((RemoteVersion<?>) settings.get("liteloader"));

        if (settings.containsKey("optifine"))
            builder.version((RemoteVersion<?>) settings.get("optifine"));

        return builder.buildAsync().finalized((a, b) -> profile.getRepository().refreshVersions());
    }

    private Task finishModpackInstallingAsync(Map<String, Object> settings) {
        if (!settings.containsKey(ModpackPage.MODPACK_FILE))
            return null;

        File selected = tryCast(settings.get(ModpackPage.MODPACK_FILE), File.class).orElse(null);
        Modpack modpack = tryCast(settings.get(ModpackPage.MODPACK_MANIFEST), Modpack.class).orElse(null);
        String name = tryCast(settings.get(ModpackPage.MODPACK_NAME), String.class).orElse(null);
        if (selected == null || modpack == null || name == null) return null;

        return ModpackHelper.getInstallTask(profile, selected, name, modpack);
    }

    @Override
    public Object finish(Map<String, Object> settings) {
        settings.put("success_message", i18n("install.success"));
        settings.put("failure_message", i18n("install.failed"));

        switch (Lang.parseInt(settings.get(InstallTypePage.INSTALL_TYPE), -1)) {
            case 0: return finishVersionDownloadingAsync(settings);
            case 1: return finishModpackInstallingAsync(settings);
            default: return null;
        }
    }

    @Override
    public Node createPage(WizardController controller, int step, Map<String, Object> settings) {
        DownloadProvider provider = profile.getDependency().getDownloadProvider();
        switch (step) {
            case 0:
                return new InstallTypePage(controller);
            case 1:
                int subStep = Lang.parseInt(settings.get(InstallTypePage.INSTALL_TYPE), -1);
                switch (subStep) {
                    case 0:
                        return new VersionsPage(controller, i18n("install.installer.choose", i18n("install.installer.game")), "", provider, "game", () -> controller.onNext(new InstallersPage(controller, profile.getRepository(), provider)));
                    case 1:
                        return new ModpackPage(controller);
                    default:
                        throw new IllegalStateException("Error step " + step + ", subStep " + subStep + ", settings: " + settings + ", pages: " + controller.getPages());
                }
            default:
                throw new IllegalStateException("error step " + step + ", settings: " + settings + ", pages: " + controller.getPages());
        }
    }

    @Override
    public boolean cancel() {
        return true;
    }

    public static final String PROFILE = "PROFILE";
}
