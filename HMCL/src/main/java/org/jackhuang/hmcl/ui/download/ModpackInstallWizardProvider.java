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
import org.jackhuang.hmcl.game.ManuallyCreatedModpackException;
import org.jackhuang.hmcl.game.ModpackHelper;
import org.jackhuang.hmcl.mod.MismatchedModpackTypeException;
import org.jackhuang.hmcl.mod.Modpack;
import org.jackhuang.hmcl.mod.ModpackCompletionException;
import org.jackhuang.hmcl.mod.UnsupportedModpackException;
import org.jackhuang.hmcl.mod.server.ServerModpackManifest;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane.MessageType;
import org.jackhuang.hmcl.ui.wizard.WizardController;
import org.jackhuang.hmcl.ui.wizard.WizardProvider;
import org.jackhuang.hmcl.util.SettingsMap;
import org.jackhuang.hmcl.util.StringUtils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public final class ModpackInstallWizardProvider implements WizardProvider {
    private final Profile profile;
    private final Path file;
    private final String updateVersion;
    private String iconUrl;

    public ModpackInstallWizardProvider(Profile profile) {
        this(profile, null, null);
    }

    public ModpackInstallWizardProvider(Profile profile, Path modpackFile) {
        this(profile, modpackFile, null);
    }

    public ModpackInstallWizardProvider(Profile profile, String updateVersion) {
        this(profile, null, updateVersion);
    }

    public ModpackInstallWizardProvider(Profile profile, Path modpackFile, String updateVersion) {
        this.profile = profile;
        this.file = modpackFile;
        this.updateVersion = updateVersion;
    }

    public void setIconUrl(String iconUrl) {
        this.iconUrl = iconUrl;
    }

    @Override
    public void start(SettingsMap settings) {
        if (file != null)
            settings.put(LocalModpackPage.MODPACK_FILE, file);
        if (updateVersion != null)
            settings.put(LocalModpackPage.MODPACK_NAME, updateVersion);
        if (StringUtils.isNotBlank(iconUrl))
            settings.put(LocalModpackPage.MODPACK_ICON_URL, iconUrl);
        settings.put(ModpackPage.PROFILE, profile);
    }

    private Task<?> finishModpackInstallingAsync(SettingsMap settings) {
        Path selected = settings.get(LocalModpackPage.MODPACK_FILE);
        ServerModpackManifest serverModpackManifest = settings.get(RemoteModpackPage.MODPACK_SERVER_MANIFEST);
        Modpack modpack = settings.get(LocalModpackPage.MODPACK_MANIFEST);
        String name = settings.get(LocalModpackPage.MODPACK_NAME);
        String iconUrl = settings.get(LocalModpackPage.MODPACK_ICON_URL);
        Charset charset = settings.get(LocalModpackPage.MODPACK_CHARSET);
        boolean isManuallyCreated = settings.getOrDefault(LocalModpackPage.MODPACK_MANUALLY_CREATED, false);

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
                Controllers.dialog(i18n("modpack.mismatched_type", e.getRequired(), e.getFound()), i18n("message.error"), MessageType.ERROR);
            } catch (IOException e) {
                Controllers.dialog(i18n("modpack.invalid"), i18n("message.error"), MessageType.ERROR);
            }
            return null;
        } else {
            if (serverModpackManifest != null) {
                return ModpackHelper.getInstallTask(profile, serverModpackManifest, name, modpack)
                        .thenRunAsync(Schedulers.javafx(), () -> profile.setSelectedVersion(name));
            } else {
                return ModpackHelper.getInstallTask(profile, selected, name, modpack, iconUrl)
                        .thenRunAsync(Schedulers.javafx(), () -> profile.setSelectedVersion(name));
            }
        }
    }

    @Override
    public Object finish(SettingsMap settings) {
        settings.put("title", i18n("install.modpack.installation"));
        settings.put("success_message", i18n("install.success"));
        settings.put(FailureCallback.KEY, (ignored, exception, next) -> {
            if (exception instanceof ModpackCompletionException) {
                if (exception.getCause() instanceof FileNotFoundException) {
                    Controllers.dialog(i18n("modpack.type.curse.not_found"), i18n("install.failed"), MessageType.ERROR, next);
                } else {
                    Controllers.dialog(i18n("install.success"), i18n("install.success"), MessageType.SUCCESS, next);
                }
            } else {
                UpdateInstallerWizardProvider.alertFailureMessage(exception, next);
            }
        });

        return finishModpackInstallingAsync(settings);
    }

    @Override
    public Node createPage(WizardController controller, int step, SettingsMap settings) {
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
}
