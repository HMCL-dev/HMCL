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
import org.jackhuang.hmcl.download.ArtifactMalformedException;
import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.download.RemoteVersion;
import org.jackhuang.hmcl.download.VersionMismatchException;
import org.jackhuang.hmcl.download.fabric.FabricInstallTask;
import org.jackhuang.hmcl.download.game.LibraryDownloadException;
import org.jackhuang.hmcl.download.optifine.OptiFineInstallTask;
import org.jackhuang.hmcl.game.Version;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.task.DownloadException;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskStages;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane.MessageType;
import org.jackhuang.hmcl.ui.wizard.WizardController;
import org.jackhuang.hmcl.ui.wizard.WizardProvider;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.i18n.I18n;
import org.jackhuang.hmcl.util.io.ResponseCodeException;

import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.Map;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public final class InstallerWizardProvider implements WizardProvider {
    private final Profile profile;
    private final String gameVersion;
    private final Version version;

    public InstallerWizardProvider(Profile profile, String gameVersion, Version version) {
        this.profile = profile;
        this.gameVersion = gameVersion;
        this.version = version;
    }

    public Profile getProfile() {
        return profile;
    }

    public String getGameVersion() {
        return gameVersion;
    }

    public Version getVersion() {
        return version;
    }

    @Override
    public void start(Map<String, Object> settings) {
    }

    @Override
    public Object finish(Map<String, Object> settings) {
        settings.put("title", i18n("install.installer.install_online"));
        settings.put("success_message", i18n("install.success"));
        settings.put("failure_callback", (FailureCallback) (settings1, exception, next) -> alertFailureMessage(exception, next));
        settings.put("stages", new TaskStages() {
            {
                if (settings.containsKey("forge")) {
                    RemoteVersion forge = (RemoteVersion) settings.get("forge");
                    addStage("hmcl.install.forge", i18n("install.installer.install", i18n("install.installer.forge") + " " + forge.getSelfVersion()));
                }
                if (settings.containsKey("liteloader")) {
                    RemoteVersion liteloader = (RemoteVersion) settings.get("liteloader");
                    addStage("hmcl.install.liteloader", i18n("install.installer.install", i18n("install.installer.liteloader") + " " + liteloader.getSelfVersion()));
                }
                if (settings.containsKey("optifine")) {
                    RemoteVersion optifine = (RemoteVersion) settings.get("optifine");
                    addStage("hmcl.install.optifine", i18n("install.installer.install", i18n("install.installer.optifine") + " " + optifine.getSelfVersion()));
                }
                if (settings.containsKey("fabric")) {
                    RemoteVersion fabric = (RemoteVersion) settings.get("fabric");
                    addStage("hmcl.install.fabric", i18n("install.installer.install", i18n("install.installer.fabric") + " " + fabric.getSelfVersion()));
                }
            }
        });

        Task<Version> ret = Task.supplyAsync(() -> version);

        for (Object value : settings.values()) {
            if (value instanceof RemoteVersion)
                ret = ret.thenComposeAsync(version -> profile.getDependency().installLibraryAsync(version, (RemoteVersion) value));
        }

        return ret.thenComposeAsync(profile.getRepository().refreshVersionsAsync());
    }

    @Override
    public Node createPage(WizardController controller, int step, Map<String, Object> settings) {
        DownloadProvider provider = profile.getDependency().getPrimaryDownloadProvider();
        switch (step) {
            case 0:
                return new AdditionalInstallersPage(this, controller, profile.getRepository(), provider);
            default:
                throw new IllegalStateException();
        }
    }

    @Override
    public boolean cancel() {
        return true;
    }

    public static void alertFailureMessage(Exception exception, Runnable next) {
        if (exception instanceof LibraryDownloadException) {
            String message = i18n("launch.failed.download_library", ((LibraryDownloadException) exception).getLibrary().getName()) + "\n";
            if (exception.getCause() instanceof ResponseCodeException) {
                ResponseCodeException rce = (ResponseCodeException) exception.getCause();
                int responseCode = rce.getResponseCode();
                URL url = rce.getUrl();
                if (responseCode == 404)
                    message += i18n("download.code.404", url);
                else
                    message += i18n("download.failed", url, responseCode);
            } else {
                message += StringUtils.getStackTrace(exception.getCause());
            }
            Controllers.dialog(message, i18n("install.failed.downloading"), MessageType.ERROR, next);
        } else if (exception instanceof DownloadException) {
            if (exception.getCause() instanceof SocketTimeoutException) {
                Controllers.dialog(i18n("install.failed.downloading.timeout", ((DownloadException) exception).getUrl()), i18n("install.failed.downloading"), MessageType.ERROR, next);
            } else if (exception.getCause() instanceof ResponseCodeException) {
                ResponseCodeException responseCodeException = (ResponseCodeException) exception.getCause();
                if (I18n.hasKey("download.code." + responseCodeException.getResponseCode())) {
                    Controllers.dialog(i18n("download.code." + responseCodeException.getResponseCode(), ((DownloadException) exception).getUrl()), i18n("install.failed.downloading"), MessageType.ERROR, next);
                } else {
                    Controllers.dialog(i18n("install.failed.downloading.detail", ((DownloadException) exception).getUrl()) + "\n" + StringUtils.getStackTrace(exception.getCause()), i18n("install.failed.downloading"), MessageType.ERROR, next);
                }
            } else {
                Controllers.dialog(i18n("install.failed.downloading.detail", ((DownloadException) exception).getUrl()) + "\n" + StringUtils.getStackTrace(exception.getCause()), i18n("install.failed.downloading"), MessageType.ERROR, next);
            }
        } else if (exception instanceof OptiFineInstallTask.UnsupportedOptiFineInstallationException ||
                exception instanceof FabricInstallTask.UnsupportedFabricInstallationException) {
            Controllers.dialog(i18n("install.failed.optifine_conflict"), i18n("install.failed"), MessageType.ERROR, next);
        } else if (exception instanceof DefaultDependencyManager.UnsupportedLibraryInstallerException) {
            Controllers.dialog(i18n("install.failed.install_online"), i18n("install.failed"), MessageType.ERROR, next);
        } else if (exception instanceof ArtifactMalformedException) {
            Controllers.dialog(i18n("install.failed.malformed"), i18n("install.failed"), MessageType.ERROR, next);
        } else if (exception instanceof VersionMismatchException) {
            VersionMismatchException e = ((VersionMismatchException) exception);
            Controllers.dialog(i18n("install.failed.version_mismatch", e.getExpect(), e.getActual()), i18n("install.failed"), MessageType.ERROR, next);
        } else {
            Controllers.dialog(StringUtils.getStackTrace(exception), i18n("install.failed"), MessageType.ERROR, next);
        }
    }
}
