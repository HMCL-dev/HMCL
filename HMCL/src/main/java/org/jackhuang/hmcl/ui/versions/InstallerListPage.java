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
package org.jackhuang.hmcl.ui.versions;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Skin;
import javafx.stage.FileChooser;
import org.jackhuang.hmcl.download.LibraryAnalyzer;
import org.jackhuang.hmcl.game.Version;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskExecutor;
import org.jackhuang.hmcl.task.TaskListener;
import org.jackhuang.hmcl.ui.*;
import org.jackhuang.hmcl.ui.download.UpdateInstallerWizardProvider;
import org.jackhuang.hmcl.util.TaskCancellationAction;
import org.jackhuang.hmcl.util.io.FileUtils;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.jackhuang.hmcl.ui.FXUtils.runInFX;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class InstallerListPage extends ListPageBase<InstallerItem> implements VersionPage.VersionLoadable {
    private Profile profile;
    private String versionId;
    private Version version;
    private String gameVersion;

    {
        FXUtils.applyDragListener(this, it -> Arrays.asList("jar", "exe").contains(FileUtils.getExtension(it)), mods -> {
            if (!mods.isEmpty())
                doInstallOffline(mods.get(0));
        });
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new InstallerListPageSkin();
    }

    @Override
    public void loadVersion(Profile profile, String versionId) {
        this.profile = profile;
        this.versionId = versionId;
        this.version = profile.getRepository().getVersion(versionId);
        this.gameVersion = null;

        CompletableFuture.supplyAsync(() -> {
            gameVersion = profile.getRepository().getGameVersion(version).orElse(null);

            return LibraryAnalyzer.analyze(profile.getRepository().getResolvedPreservingPatchesVersion(versionId), gameVersion);
        }).thenAcceptAsync(analyzer -> {
            itemsProperty().clear();

            InstallerItem.InstallerItemGroup group = new InstallerItem.InstallerItemGroup(gameVersion, InstallerItem.Style.LIST_ITEM);

            // Conventional libraries: game, fabric, forge, neoforge, liteloader, optifine
            for (InstallerItem item : group.getLibraries()) {
                String libraryId = item.getLibraryId();

                // Skip fabric-api and quilt-api
                if (libraryId.contains("fabric-api") || libraryId.contains("quilt-api")) {
                    continue;
                }

                String libraryVersion = analyzer.getVersion(libraryId).orElse(null);

                if (libraryVersion != null) {
                    item.versionProperty().set(new InstallerItem.InstalledState(
                            libraryVersion,
                            analyzer.getLibraryStatus(libraryId) != LibraryAnalyzer.LibraryMark.LibraryStatus.CLEAR,
                            false
                    ));
                } else {
                    item.versionProperty().set(null);
                }

                item.setOnInstall(() -> {
                    Controllers.getDecorator().startWizard(new UpdateInstallerWizardProvider(profile, gameVersion, version, libraryId, libraryVersion));
                });

                item.setOnRemove(() -> profile.getDependency().removeLibraryAsync(version, libraryId)
                        .thenComposeAsync(profile.getRepository()::saveAsync)
                        .withComposeAsync(profile.getRepository().refreshVersionsAsync())
                        .withRunAsync(Schedulers.javafx(), () -> loadVersion(this.profile, this.versionId))
                        .start());

                itemsProperty().add(item);
            }

            // other third-party libraries which are unable to manage.
            for (LibraryAnalyzer.LibraryMark mark : analyzer) {
                String libraryId = mark.getLibraryId();
                String libraryVersion = mark.getLibraryVersion();
                if ("mcbbs".equals(libraryId))
                    continue;

                // we have done this library above.
                if (LibraryAnalyzer.LibraryType.fromPatchId(libraryId) != null)
                    continue;

                InstallerItem installerItem = new InstallerItem(libraryId, InstallerItem.Style.LIST_ITEM);
                installerItem.versionProperty().set(new InstallerItem.InstalledState(libraryVersion, false, false));
                installerItem.setOnRemove(() -> profile.getDependency().removeLibraryAsync(version, libraryId)
                        .thenComposeAsync(profile.getRepository()::saveAsync)
                        .withComposeAsync(profile.getRepository().refreshVersionsAsync())
                        .withRunAsync(Schedulers.javafx(), () -> loadVersion(this.profile, this.versionId))
                        .start());

                itemsProperty().add(installerItem);
            }
        }, Platform::runLater);
    }

    public void installOffline() {
        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(i18n("install.installer.install_offline.extension"), "*.jar", "*.exe"));
        File file = chooser.showOpenDialog(Controllers.getStage());
        if (file != null) doInstallOffline(file);
    }

    private void doInstallOffline(File file) {
        Task<?> task = profile.getDependency().installLibraryAsync(version, file.toPath())
                .thenComposeAsync(profile.getRepository()::saveAsync)
                .thenComposeAsync(profile.getRepository().refreshVersionsAsync());
        task.setName(i18n("install.installer.install_offline"));
        TaskExecutor executor = task.executor(new TaskListener() {
            @Override
            public void onStop(boolean success, TaskExecutor executor) {
                runInFX(() -> {
                    if (success) {
                        loadVersion(profile, versionId);
                        Controllers.dialog(i18n("install.success"));
                    } else {
                        if (executor.getException() == null)
                            return;
                        UpdateInstallerWizardProvider.alertFailureMessage(executor.getException(), null);
                    }
                });
            }
        });
        Controllers.taskDialog(executor, i18n("install.installer.install_offline"), TaskCancellationAction.NO_CANCEL);
        executor.start();
    }

    private class InstallerListPageSkin extends ToolbarListPageSkin<InstallerListPage> {

        InstallerListPageSkin() {
            super(InstallerListPage.this);
        }

        @Override
        protected List<Node> initializeToolbar(InstallerListPage skinnable) {
            return Collections.singletonList(
                    createToolbarButton2(i18n("install.installer.install_offline"), SVG.ADD, skinnable::installOffline)
            );
        }
    }
}
