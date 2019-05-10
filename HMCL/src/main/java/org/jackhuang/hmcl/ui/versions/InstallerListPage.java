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
package org.jackhuang.hmcl.ui.versions;

import javafx.scene.Node;
import javafx.scene.control.Skin;
import javafx.stage.FileChooser;
import org.jackhuang.hmcl.download.LibraryAnalyzer;
import org.jackhuang.hmcl.download.MaintainTask;
import org.jackhuang.hmcl.download.game.VersionJsonSaveTask;
import org.jackhuang.hmcl.game.GameVersion;
import org.jackhuang.hmcl.game.Library;
import org.jackhuang.hmcl.game.Version;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskExecutor;
import org.jackhuang.hmcl.task.TaskListener;
import org.jackhuang.hmcl.ui.*;
import org.jackhuang.hmcl.ui.download.InstallerWizardProvider;
import org.jackhuang.hmcl.ui.download.UpdateInstallerWizardProvider;
import org.jackhuang.hmcl.util.io.FileUtils;

import java.io.File;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.jackhuang.hmcl.download.LibraryAnalyzer.LibraryType.*;
import static org.jackhuang.hmcl.ui.FXUtils.runInFX;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class InstallerListPage extends ListPageBase<InstallerItem> {
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

    public void loadVersion(Profile profile, String versionId) {
        this.profile = profile;
        this.versionId = versionId;
        this.version = profile.getRepository().getResolvedVersion(versionId);
        this.gameVersion = null;

        Task.ofResult(() -> {
            gameVersion = GameVersion.minecraftVersion(profile.getRepository().getVersionJar(version)).orElse(null);

            return LibraryAnalyzer.analyze(version);
        }).thenAccept(Schedulers.javafx(), analyzer -> {
            Function<Library, Consumer<InstallerItem>> removeAction = library -> x -> {
                LinkedList<Library> newList = new LinkedList<>(version.getLibraries());
                newList.remove(library);
                new MaintainTask(version.setLibraries(newList))
                        .thenCompose(maintainedVersion -> new VersionJsonSaveTask(profile.getRepository(), maintainedVersion))
                        .withCompose(profile.getRepository().refreshVersionsAsync())
                        .withRun(Schedulers.javafx(), () -> loadVersion(this.profile, this.versionId))
                        .start();
            };

            itemsProperty().clear();
            analyzer.get(FORGE).ifPresent(library -> itemsProperty().add(
                    new InstallerItem("Forge", library.getVersion(), () -> {
                        Controllers.getDecorator().startWizard(new UpdateInstallerWizardProvider(profile, gameVersion, version, "forge", library));
                    }, removeAction.apply(library))));
            analyzer.get(LITELOADER).ifPresent(library -> itemsProperty().add(
                    new InstallerItem("LiteLoader", library.getVersion(), () -> {
                        Controllers.getDecorator().startWizard(new UpdateInstallerWizardProvider(profile, gameVersion, version, "liteloader", library));
                    }, removeAction.apply(library))));
            analyzer.get(OPTIFINE).ifPresent(library -> itemsProperty().add(
                    new InstallerItem("OptiFine", library.getVersion(), () -> {
                        Controllers.getDecorator().startWizard(new UpdateInstallerWizardProvider(profile, gameVersion, version, "optifine", library));
                    }, removeAction.apply(library))));
            analyzer.get(FABRIC).ifPresent(library -> itemsProperty().add(new InstallerItem("Fabric", library.getVersion(), null, null)));
        }).start();
    }

    public void installOnline() {
        if (gameVersion == null)
            Controllers.dialog(i18n("version.cannot_read"));
        else
            Controllers.getDecorator().startWizard(new InstallerWizardProvider(profile, gameVersion, version));
    }

    public void installOffline() {
        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(i18n("install.installer.install_offline.extension"), "*.jar", "*.exe"));
        File file = chooser.showOpenDialog(Controllers.getStage());
        if (file != null) doInstallOffline(file);
    }

    private void doInstallOffline(File file) {
        Task task = profile.getDependency().installLibraryAsync(version, file.toPath())
                .then(profile.getRepository().refreshVersionsAsync());
        task.setName(i18n("install.installer.install_offline"));
        TaskExecutor executor = task.executor(new TaskListener() {
            @Override
            public void onStop(boolean success, TaskExecutor executor) {
                runInFX(() -> {
                    if (success) {
                        loadVersion(profile, versionId);
                        Controllers.dialog(i18n("install.success"));
                    } else {
                        if (executor.getLastException() == null)
                            return;
                        InstallerWizardProvider.alertFailureMessage(executor.getLastException(), null);
                    }
                });
            }
        });
        Controllers.taskDialog(executor, i18n("install.installer.install_offline"));
        executor.start();
    }

    private class InstallerListPageSkin extends ToolbarListPageSkin<InstallerListPage> {

        InstallerListPageSkin() {
            super(InstallerListPage.this);
        }

        @Override
        protected List<Node> initializeToolbar(InstallerListPage skinnable) {
            return Arrays.asList(
                    createToolbarButton(i18n("install.installer.install_online"), SVG::plus, skinnable::installOnline),
                    createToolbarButton(i18n("install.installer.install_offline"), SVG::plus, skinnable::installOffline));
        }
    }
}
