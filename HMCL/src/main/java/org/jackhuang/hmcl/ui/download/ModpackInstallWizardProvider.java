/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
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
import org.jackhuang.hmcl.game.ManuallyCreatedModpackException;
import org.jackhuang.hmcl.mod.ModpackCompletionException;
import org.jackhuang.hmcl.mod.MismatchedModpackTypeException;
import org.jackhuang.hmcl.mod.Modpack;
import org.jackhuang.hmcl.mod.UnsupportedModpackException;
import org.jackhuang.hmcl.mod.server.ServerModpackManifest;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane.MessageType;
import org.jackhuang.hmcl.ui.wizard.WizardController;
import org.jackhuang.hmcl.ui.wizard.WizardProvider;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Map;

import static org.jackhuang.hmcl.util.Lang.tryCast;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class ModpackInstallWizardProvider implements WizardProvider {
    private final Profile profile;
    private final File file;
    private final String updateVersion;

    public ModpackInstallWizardProvider(Profile profile) {
        this(profile, null, null);
    }

    public ModpackInstallWizardProvider(Profile profile, File modpackFile) {
        this(profile, modpackFile, null);
    }

    public ModpackInstallWizardProvider(Profile profile, String updateVersion) {
        this(profile, null, updateVersion);
    }

    public ModpackInstallWizardProvider(Profile profile, File modpackFile, String updateVersion) {
        this.profile = profile;
        this.file = modpackFile;
        this.updateVersion = updateVersion;
    }

    @Override
    public void start(Map<String, Object> settings) {
        if (file != null)
            settings.put(LocalModpackPage.MODPACK_FILE, file);
        if (updateVersion != null)
            settings.put(LocalModpackPage.MODPACK_NAME, updateVersion);
        settings.put(PROFILE, profile);
    }

    private Task<?> finishModpackInstallingAsync(Map<String, Object> settings) {
        File selected = tryCast(settings.get(LocalModpackPage.MODPACK_FILE), File.class).orElse(null);
        ServerModpackManifest serverModpackManifest = tryCast(settings.get(RemoteModpackPage.MODPACK_SERVER_MANIFEST), ServerModpackManifest.class).orElse(null);
        Modpack modpack = tryCast(settings.get(LocalModpackPage.MODPACK_MANIFEST), Modpack.class).orElse(null);
        String name = tryCast(settings.get(LocalModpackPage.MODPACK_NAME), String.class).orElse(null);
        Charset charset = tryCast(settings.get(LocalModpackPage.MODPACK_CHARSET), Charset.class).orElse(null);
        boolean isManuallyCreated = tryCast(settings.get(LocalModpackPage.MODPACK_MANUALLY_CREATED), Boolean.class).orElse(false);

        if (isManuallyCreated) {
            return ModpackHelper.getInstallManuallyCreatedModpackTask(profile, selected, name, charset);
        }

        if ((selected == null && serverModpackManifest == null) || modpack == null || name == null) return null;

        if (updateVersion != null) {
            if (selected == null) {
                Controllers.dialog(i18n("modpack.unsupported"), i18n("message.error"), MessageType.ERROR);
                return null;
            }
            try {
                if (serverModpackManifest != null) {
                    return ModpackHelper.getUpdateTask(profile, serverModpackManifest, modpack.getEncoding(), name, ModpackHelper.readModpackConfiguration(profile.getRepository().getModpackConfiguration(name)));
                } else {
                    return ModpackHelper.getUpdateTask(profile, selected, modpack.getEncoding(), name, ModpackHelper.readModpackConfiguration(profile.getRepository().getModpackConfiguration(name)));
                }
            } catch (UnsupportedModpackException | ManuallyCreatedModpackException e) {
                Controllers.dialog(i18n("modpack.unsupported"), i18n("message.error"), MessageType.ERROR);
            } catch (MismatchedModpackTypeException e) {
                Controllers.dialog(i18n("modpack.mismatched_type"), i18n("message.error"), MessageType.ERROR);
            } catch (IOException e) {
                Controllers.dialog(i18n("modpack.invalid"), i18n("message.error"), MessageType.ERROR);
            }
            return null;
        } else {
            if (serverModpackManifest != null) {
                return ModpackHelper.getInstallTask(profile, serverModpackManifest, name, modpack)
                        .thenRunAsync(Schedulers.javafx(), () -> profile.setSelectedVersion(name));
            } else {
                return ModpackHelper.getInstallTask(profile, selected, name, modpack)
                        .thenRunAsync(Schedulers.javafx(), () -> profile.setSelectedVersion(name));
            }
        }
    }

    @Override
    public Object finish(Map<String, Object> settings) {
        settings.put("title", i18n("install.modpack"));
        settings.put("success_message", i18n("install.success"));
        settings.put("failure_callback", new FailureCallback() {
            @Override
            public void onFail(Map<String, Object> settings, Exception exception, Runnable next) {
                if (exception instanceof ModpackCompletionException) {
                    if (exception.getCause() instanceof FileNotFoundException) {
                        Controllers.dialog(i18n("modpack.type.curse.not_found"), i18n("install.failed"), MessageType.ERROR, next);
                    } else {
                        Controllers.dialog(i18n("install.success"), i18n("install.success"), MessageType.SUCCESS, next);
                    }
                } else {
                    UpdateInstallerWizardProvider.alertFailureMessage(exception, next);
                }
            }
        });

        return finishModpackInstallingAsync(settings);
    }

    @Override
    public Node createPage(WizardController controller, int step, Map<String, Object> settings) {
        switch (step) {
            case 0:
                return new ModpackSelectionPage(controller);
            case 1:
                if (controller.getSettings().containsKey(LocalModpackPage.MODPACK_FILE))
                    return new LocalModpackPage(controller);
                else if (controller.getSettings().containsKey(RemoteModpackPage.MODPACK_SERVER_MANIFEST))
                    return new RemoteModpackPage(controller);
                else
                    throw new IllegalArgumentException();
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
