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
import org.jackhuang.hmcl.download.*;
import org.jackhuang.hmcl.download.game.GameAssetIndexDownloadTask;
import org.jackhuang.hmcl.download.game.LibraryDownloadException;
import org.jackhuang.hmcl.game.GameComponentType;
import org.jackhuang.hmcl.game.GameInstanceManifest;
import org.jackhuang.hmcl.game.HMCLGameInstance;
import org.jackhuang.hmcl.setting.DownloadProviders;
import org.jackhuang.hmcl.task.DownloadException;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane;
import org.jackhuang.hmcl.ui.wizard.WizardController;
import org.jackhuang.hmcl.ui.wizard.WizardProvider;
import org.jackhuang.hmcl.util.SettingsMap;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.i18n.I18n;
import org.jackhuang.hmcl.util.io.ResponseCodeException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.SocketTimeoutException;
import java.net.URI;
import java.util.ArrayList;
import java.util.concurrent.CancellationException;
import java.util.zip.ZipException;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public final class UpdateInstallerWizardProvider implements WizardProvider {
    private final HMCLGameInstance gameInstance;
    private final DefaultDependencyManager dependencyManager;
    private final GameComponentType componentType;
    private final String oldLibraryVersion;
    private final DownloadProvider downloadProvider;

    public UpdateInstallerWizardProvider(@NotNull HMCLGameInstance gameInstance, @NotNull GameComponentType componentType, @Nullable String oldLibraryVersion) {
        this.gameInstance = gameInstance;
        this.componentType = componentType;
        this.oldLibraryVersion = oldLibraryVersion;
        this.downloadProvider = DownloadProviders.getDownloadProvider();
        this.dependencyManager = gameInstance.getRepository().getDependency(downloadProvider);
    }

    @Override
    public void start(SettingsMap settings) {
    }

    @Override
    public Object finish(SettingsMap settings) {
        settings.put("title", i18n("install.change_version.process"));
        settings.put("success_message", i18n("install.success"));
        settings.put(FailureCallback.KEY, (settings1, exception, next) -> alertFailureMessage(exception, next));

        var hints = new ArrayList<Task.StagesHint>();
        for (Object value : settings.asStringMap().values()) {
            if (value instanceof RemoteVersion remoteVersion) {
                hints.add(new Task.StagesHint(String.format("hmcl.install.%s:%s", remoteVersion.getComponentType().getPatchId(), remoteVersion.getSelfVersion())));
                if (remoteVersion.getComponentType() == GameComponentType.GAME) {
                    hints.add(new Task.StagesHint("hmcl.install.libraries"));
                    hints.add(new Task.StagesHint("hmcl.install.assets"));
                }
            }
        }

        return gameInstance.getRepository().updateInstanceAsync(gameInstance.getId(), publishedInstance -> {
            Task<GameInstanceManifest> update = Task.supplyAsync(publishedInstance::getManifest);
            for (Object value : settings.asStringMap().values()) {
                if (value instanceof RemoteVersion remoteVersion) {
                    update = update.thenComposeAsync(manifest ->
                            dependencyManager.installComponentAsync(publishedInstance, manifest, remoteVersion));
                } else if (value instanceof RemoveVersionAction removeVersionAction) {
                    update = update.thenComposeAsync(manifest ->
                            dependencyManager.removeComponentAsync(
                                    publishedInstance,
                                    manifest,
                                    removeVersionAction.componentType));
                }
            }
            return update;
        }).withStagesHints(hints);
    }

    @Override
    public Node createPage(WizardController controller, int step, SettingsMap settings) {
        switch (step) {
            case 0:
                return new VersionsPage(controller, i18n("install.installer.choose", i18n("install.installer." + componentType)), gameInstance.getVersion().toString(), downloadProvider, componentType, () -> {
                    if (oldLibraryVersion == null) {
                        controller.onFinish();
                    } else if (componentType == GameComponentType.GAME) {
                        String newGameVersion = ((RemoteVersion) settings.get(componentType.getPatchId())).getSelfVersion();
                        controller.onNext(new AdditionalInstallersPage(gameInstance, newGameVersion, controller, downloadProvider));
                    } else {
                        Controllers.confirm(i18n("install.change_version.confirm", i18n("install.installer." + componentType), oldLibraryVersion, ((RemoteVersion) settings.get(componentType.getPatchId())).getSelfVersion()),
                                i18n("install.change_version"), controller::onFinish, controller::onCancel);
                    }
                });
            default:
                throw new IllegalStateException();
        }
    }

    @Override
    public boolean cancel() {
        return true;
    }

    @Override
    public boolean cancelIfCannotGoBack() {
        // VersionsPage will call wizardController.onPrev(cleanUp = true) when list is empty.
        // So we cancel this wizard when VersionPage calls the method.
        return true;
    }

    public static void alertFailureMessage(Exception exception, Runnable next) {
        if (exception instanceof LibraryDownloadException) {
            String message = i18n("launch.failed.download_library", ((LibraryDownloadException) exception).getLibrary().name()) + "\n";
            if (exception.getCause() instanceof ResponseCodeException rce) {
                int responseCode = rce.getResponseCode();
                String uri = rce.getUri();
                if (responseCode == 404)
                    message += i18n("download.code.404", uri);
                else
                    message += i18n("download.failed", uri, responseCode);
            } else {
                message += StringUtils.getStackTrace(exception.getCause());
            }
            Controllers.dialog(message, i18n("install.failed.downloading"), MessageDialogPane.MessageType.ERROR, next);
        } else if (exception instanceof DownloadException) {
            URI uri = ((DownloadException) exception).getUri();
            if (exception.getCause() instanceof SocketTimeoutException) {
                Controllers.dialog(i18n("install.failed.downloading.timeout", uri), i18n("install.failed.downloading"), MessageDialogPane.MessageType.ERROR, next);
            } else if (exception.getCause() instanceof ResponseCodeException responseCodeException) {
                if (I18n.hasKey("download.code." + responseCodeException.getResponseCode())) {
                    Controllers.dialog(i18n("download.code." + responseCodeException.getResponseCode(), uri), i18n("install.failed.downloading"), MessageDialogPane.MessageType.ERROR, next);
                } else {
                    Controllers.dialog(i18n("install.failed.downloading.detail", uri) + "\n" + StringUtils.getStackTrace(exception.getCause()), i18n("install.failed.downloading"), MessageDialogPane.MessageType.ERROR, next);
                }
            } else {
                Controllers.dialog(i18n("install.failed.downloading.detail", uri) + "\n" + StringUtils.getStackTrace(exception.getCause()), i18n("install.failed.downloading"), MessageDialogPane.MessageType.ERROR, next);
            }
        } else if (exception instanceof UnsupportedInstallationException unsupportedInstallationException) {
            switch (unsupportedInstallationException.getReason()) {
                case UnsupportedInstallationException.FORGE_1_17_OPTIFINE_H1_PRE2 ->
                    Controllers.dialog(i18n("install.failed.optifine_forge_1.17"), i18n("install.failed"), MessageDialogPane.MessageType.ERROR, next);
                default ->
                    Controllers.dialog(i18n("install.failed.optifine_conflict"), i18n("install.failed"), MessageDialogPane.MessageType.ERROR, next);
            }
        } else if (exception instanceof DefaultDependencyManager.UnsupportedLibraryInstallerException) {
            Controllers.dialog(i18n("install.failed.install_online"), i18n("install.failed"), MessageDialogPane.MessageType.ERROR, next);
        } else if (exception instanceof ArtifactMalformedException || exception instanceof ZipException) {
            Controllers.dialog(i18n("install.failed.malformed"), i18n("install.failed"), MessageDialogPane.MessageType.ERROR, next);
        } else if (exception instanceof GameAssetIndexDownloadTask.GameAssetIndexMalformedException) {
            Controllers.dialog(i18n("assets.index.malformed"), i18n("install.failed"), MessageDialogPane.MessageType.ERROR, next);
        } else if (exception instanceof VersionMismatchException e) {
            Controllers.dialog(i18n("install.failed.version_mismatch", e.getExpect(), e.getActual()), i18n("install.failed"), MessageDialogPane.MessageType.ERROR, next);
        } else if (exception instanceof CancellationException) {
            // Ignore cancel
        } else {
            Controllers.dialog(StringUtils.getStackTrace(exception), i18n("install.failed"), MessageDialogPane.MessageType.ERROR, next);
        }
    }

    public static class RemoveVersionAction {
        private final GameComponentType componentType;

        public RemoveVersionAction(GameComponentType componentType) {
            this.componentType = componentType;
        }
    }
}
