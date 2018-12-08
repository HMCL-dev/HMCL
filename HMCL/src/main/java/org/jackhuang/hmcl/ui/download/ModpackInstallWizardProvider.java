/**
 * Hello Minecraft! Launcher
 * Copyright (C) 2018  huangyuhui <huanghongxun2008@126.com> and contributors
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
import org.jackhuang.hmcl.game.ModpackHelper;
import org.jackhuang.hmcl.mod.CurseCompletionException;
import org.jackhuang.hmcl.mod.Modpack;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.task.DownloadException;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.construct.MessageBox;
import org.jackhuang.hmcl.ui.wizard.WizardController;
import org.jackhuang.hmcl.ui.wizard.WizardProvider;
import org.jackhuang.hmcl.util.StringUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Map;

import static org.jackhuang.hmcl.util.Lang.tryCast;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class ModpackInstallWizardProvider implements WizardProvider {
    private final Profile profile;
    private final File file;

    public ModpackInstallWizardProvider(Profile profile) {
        this(profile, null);
    }

    public ModpackInstallWizardProvider(Profile profile, File modpackFile) {
        this.profile = profile;
        this.file = modpackFile;
    }

    @Override
    public void start(Map<String, Object> settings) {
        if (file != null)
            settings.put(ModpackPage.MODPACK_FILE, file);
        settings.put(PROFILE, profile);
    }

    private Task finishModpackInstallingAsync(Map<String, Object> settings) {
        if (!settings.containsKey(ModpackPage.MODPACK_FILE))
            return null;

        File selected = tryCast(settings.get(ModpackPage.MODPACK_FILE), File.class).orElse(null);
        Modpack modpack = tryCast(settings.get(ModpackPage.MODPACK_MANIFEST), Modpack.class).orElse(null);
        String name = tryCast(settings.get(ModpackPage.MODPACK_NAME), String.class).orElse(null);
        if (selected == null || modpack == null || name == null) return null;

        return ModpackHelper.getInstallTask(profile, selected, name, modpack)
                .then(Task.of(Schedulers.javafx(), () -> profile.setSelectedVersion(name)));
    }

    @Override
    public Object finish(Map<String, Object> settings) {
        settings.put("success_message", i18n("install.success"));
        settings.put("failure_callback", new FailureCallback() {
            @Override
            public void onFail(Map<String, Object> settings, Exception exception, Runnable next) {
                if (exception instanceof CurseCompletionException) {
                    if (exception.getCause() instanceof FileNotFoundException) {
                        Controllers.dialog(i18n("modpack.type.curse.not_found"), i18n("install.failed"), MessageBox.ERROR_MESSAGE, next);
                    } else {
                        Controllers.dialog(i18n("modpack.type.curse.tolerable_error"), i18n("install.success"), MessageBox.INFORMATION_MESSAGE, next);
                    }
                } else if (exception instanceof DownloadException) {
                    Controllers.dialog(StringUtils.getStackTrace(exception), i18n("install.failed.downloading"), MessageBox.ERROR_MESSAGE, next);
                } else {
                    Controllers.dialog(StringUtils.getStackTrace(exception), i18n("install.failed"), MessageBox.ERROR_MESSAGE, next);
                }
            }
        });

        return finishModpackInstallingAsync(settings);
    }

    @Override
    public Node createPage(WizardController controller, int step, Map<String, Object> settings) {
        switch (step) {
            case 0:
                return new ModpackPage(controller);
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
