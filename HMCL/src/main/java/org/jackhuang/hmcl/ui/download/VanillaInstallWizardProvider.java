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

import javafx.scene.Node;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.download.GameBuilder;
import org.jackhuang.hmcl.download.RemoteVersion;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.wizard.WizardController;
import org.jackhuang.hmcl.ui.wizard.WizardProvider;

import java.util.Map;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public final class VanillaInstallWizardProvider implements WizardProvider {
    private final Profile profile;

    public VanillaInstallWizardProvider(Profile profile) {
        this.profile = profile;
    }

    @Override
    public void start(Map<String, Object> settings) {
        settings.put(PROFILE, profile);
    }

    private Task<Void> finishVersionDownloadingAsync(Map<String, Object> settings) {
        GameBuilder builder = profile.getDependency().gameBuilder();

        String name = (String) settings.get("name");
        builder.name(name);
        builder.gameVersion(((RemoteVersion) settings.get("game")).getGameVersion());

        if (settings.containsKey("forge"))
            builder.version((RemoteVersion) settings.get("forge"));

        if (settings.containsKey("liteloader"))
            builder.version((RemoteVersion) settings.get("liteloader"));

        if (settings.containsKey("optifine"))
            builder.version((RemoteVersion) settings.get("optifine"));

        return builder.buildAsync().whenComplete((a, b) -> profile.getRepository().refreshVersions())
                .thenRun(Schedulers.javafx(), () -> profile.setSelectedVersion(name));
    }

    @Override
    public Object finish(Map<String, Object> settings) {
        settings.put("success_message", i18n("install.success"));
        settings.put("failure_callback", (FailureCallback) (settings1, exception, next) -> InstallerWizardProvider.alertFailureMessage(exception, next));

        return finishVersionDownloadingAsync(settings);
    }

    @Override
    public Node createPage(WizardController controller, int step, Map<String, Object> settings) {
        DownloadProvider provider = profile.getDependency().getDownloadProvider();
        switch (step) {
            case 0:
                return new VersionsPage(controller, i18n("install.installer.choose", i18n("install.installer.game")), "", provider, "game", () -> controller.onNext(new InstallersPage(controller, profile.getRepository(), provider)));
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
