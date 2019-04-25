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
import org.jackhuang.hmcl.download.MaintainTask;
import org.jackhuang.hmcl.download.RemoteVersion;
import org.jackhuang.hmcl.game.Library;
import org.jackhuang.hmcl.game.Version;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.wizard.WizardController;
import org.jackhuang.hmcl.ui.wizard.WizardProvider;

import java.util.LinkedList;
import java.util.Map;

import static org.jackhuang.hmcl.ui.download.InstallerWizardProvider.alertFailureMessage;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public final class UpdateInstallerWizardProvider implements WizardProvider {
    private final Profile profile;
    private final String gameVersion;
    private final Version version;
    private final String libraryId;
    private final Library oldLibrary;

    public UpdateInstallerWizardProvider(Profile profile, String gameVersion, Version version, String libraryId, Library oldLibrary) {
        this.profile = profile;
        this.gameVersion = gameVersion;
        this.version = version;
        this.libraryId = libraryId;
        this.oldLibrary = oldLibrary;
    }

    @Override
    public void start(Map<String, Object> settings) {
    }

    @Override
    public Object finish(Map<String, Object> settings) {
        settings.put("success_message", i18n("install.success"));
        settings.put("failure_callback", (FailureCallback) (settings1, exception, next) -> alertFailureMessage(exception, next));

        // We remove library but not save it,
        // so if installation failed will not break down current version.
        LinkedList<Library> newList = new LinkedList<>(version.getLibraries());
        newList.remove(oldLibrary);
        return new MaintainTask(version.setLibraries(newList))
                .thenCompose(profile.getDependency().installLibraryAsync((RemoteVersion) settings.get(libraryId)))
                .then(profile.getRepository().refreshVersionsAsync());
    }

    @Override
    public Node createPage(WizardController controller, int step, Map<String, Object> settings) {
        DownloadProvider provider = profile.getDependency().getDownloadProvider();
        switch (step) {
            case 0:
                return new VersionsPage(controller, i18n("install.installer.choose", i18n("install.installer." + libraryId)), gameVersion, provider, libraryId, () -> {
                    Controllers.confirmDialog(i18n("install.change_version.confirm", i18n("install.installer." + libraryId), oldLibrary.getVersion(), ((RemoteVersion) settings.get(libraryId)).getSelfVersion()),
                            i18n("install.change_version"), controller::onFinish, controller::onCancel);
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
}
