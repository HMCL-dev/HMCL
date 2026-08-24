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
package org.jackhuang.hmcl.ui.instances;

import javafx.beans.value.ObservableValue;
import javafx.scene.Node;
import javafx.scene.control.Skin;
import javafx.stage.FileChooser;
import org.jackhuang.hmcl.game.GameComponentAnalyzer;
import org.jackhuang.hmcl.game.HMCLGameInstance;
import org.jackhuang.hmcl.game.HMCLGameRepository;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskExecutor;
import org.jackhuang.hmcl.task.TaskListener;
import org.jackhuang.hmcl.ui.*;
import org.jackhuang.hmcl.ui.download.UpdateInstallerWizardProvider;
import org.jackhuang.hmcl.util.TaskCancellationAction;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.*;

import static org.jackhuang.hmcl.ui.FXUtils.runInFX;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class InstallerListPage extends ListPageBase<InstallerItem> {
    private final WeakListenerHolder listenerHolder = new WeakListenerHolder();
    private @Nullable HMCLGameInstance gameInstance;

    /// Creates an installer list that reloads when `instanceContext` changes.
    ///
    /// @param instanceContext the parent page's instance property
    public InstallerListPage(ObservableValue<? extends HMCLGameInstance.Optional> instanceContext) {
        Objects.requireNonNull(instanceContext, "instanceContext");
        FXUtils.applyDragListener(this, it -> Set.of("jar", "exe").contains(FileUtils.getExtension(it)), mods -> {
            if (!mods.isEmpty())
                doInstallOffline(mods.get(0));
        });

        listenerHolder.add(FXUtils.onWeakChangeAndOperate(instanceContext, current -> {
            if (current != null) {
                loadInstance(current);
            }
        }));
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new InstallerListPageSkin();
    }

    public void loadInstance(HMCLGameInstance.Optional instance) {
        this.gameInstance = instance.instance();
        if (gameInstance == null) {
            itemsProperty().clear();
            return;
        }

        HMCLGameRepository repository = gameInstance.getRepository();

        itemsProperty().clear();
        InstallerItem.InstallerItemGroup group = new InstallerItem.InstallerItemGroup(gameInstance.getVersion(), InstallerItem.Style.LIST_ITEM);

        // Conventional libraries: game, fabric, legacyfabric, forge, cleanroom, neoforge, liteloader, optifine
        for (InstallerItem component : group.getComponents()) {

            // Skip fabric-api and quilt-api and legacyfabric-api
            if (component.getComponentType().getPatchId().endsWith("-api")) {
                continue;
            }

            @Nullable String libraryVersion = gameInstance.getComponentVersion(component.getComponentType());

            if (libraryVersion != null) {
                component.versionProperty().set(new InstallerItem.InstalledState(
                        libraryVersion,
                        !gameInstance.getAnalyzer().isClear(component.getComponentType()),
                        false
                ));
            } else {
                component.versionProperty().set(null);
            }

            component.setOnInstall(() -> {
                Controllers.getDecorator().startWizard(new UpdateInstallerWizardProvider(gameInstance, component.getComponentType(), libraryVersion));
            });

            component.setOnRemove(() -> repository.updateInstanceAsync(
                            gameInstance.getId(),
                            publishedInstance -> repository.getDependency().removeComponentAsync(
                                    publishedInstance,
                                    component.getComponentType()))
                    .withRunAsync(Schedulers.javafx(), this::reloadCurrentInstance)
                    .start());

            itemsProperty().add(component);
        }

        // other third-party libraries which are unable to manage.
        for (GameComponentAnalyzer.Mark mark : gameInstance.getAnalyzer()) {
            // we have done this library above.

            InstallerItem installerItem = new InstallerItem(mark.componentType(), InstallerItem.Style.LIST_ITEM);
            installerItem.versionProperty().set(new InstallerItem.InstalledState(mark.version(), false, false));
            installerItem.setOnRemove(() -> repository.updateInstanceAsync(
                            gameInstance.getId(),
                            publishedInstance -> repository.getDependency().removeComponentAsync(
                                    publishedInstance,
                                    mark.componentType()))
                    .withRunAsync(Schedulers.javafx(), this::reloadCurrentInstance)
                    .start());

            itemsProperty().add(installerItem);
        }
    }

    private void reloadCurrentInstance() {
        if (gameInstance != null) {
            loadInstance(HMCLGameInstance.Optional.of(gameInstance.getRepository(), gameInstance.getId()));
        }
    }

    public void installOffline() {
        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(i18n("extension.modloader.installer"), "*.jar", "*.exe"));
        Path file = Controllers.showOpenDialog(chooser);
        if (file != null) doInstallOffline(file);
    }

    private void doInstallOffline(Path file) {
        if (gameInstance == null) {
            return;
        }

        HMCLGameRepository repository = gameInstance.getRepository();
        Task<?> task = repository.updateInstanceAsync(
                gameInstance.getId(),
                publishedInstance -> repository.getDependency().installComponentLocalAsync(publishedInstance, file));
        task.setName(i18n("install.installer.install_offline"));
        TaskExecutor executor = task.executor(new TaskListener() {
            @Override
            public void onStop(boolean success, TaskExecutor executor) {
                runInFX(() -> {
                    if (success) {
                        reloadCurrentInstance();
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

    private class InstallerListPageSkin extends ToolbarListPageSkin<InstallerItem, InstallerListPage> {

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
