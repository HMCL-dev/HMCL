// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.instances;

import javafx.beans.value.ObservableValue;
import javafx.geometry.Insets;
import javafx.scene.control.ListView;
import javafx.stage.FileChooser;
import org.jackhuang.hmcl.game.GameComponentAnalyzer;
import org.jackhuang.hmcl.game.GameComponentType;
import org.jackhuang.hmcl.game.HMCLGameInstance;
import org.jackhuang.hmcl.game.HMCLGameRepository;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.*;
import org.jackhuang.hmcl.ui.download.UpdateInstallerWizardProvider;
import org.jackhuang.hmcl.util.TaskCancellationAction;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.*;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class InstallerListPage extends ListPaneBase<InstallerItem> {
    private final WeakListenerHolder listenerHolder = new WeakListenerHolder();
    private @Nullable HMCLGameInstance gameInstance;

    /// Creates an installer list that reloads when `instanceContext` changes.
    ///
    /// @param instanceContext the parent page's instance property
    public InstallerListPage(ObservableValue<? extends HMCLGameInstance.Optional> instanceContext) {
        getListView().setPadding(Insets.EMPTY);
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

        setToolbarButtons(Collections.singletonList(
                createToolbarButton(i18n("install.installer.install_offline"), SVG.ADD, this::installOffline)
        ));
    }

    /// Styles the cell surface and forwards single clicks to the current installer item.
    @Override
    protected ItemCell<InstallerItem> createItemCell(ListView<InstallerItem> listView) {
        var cell = ItemCell.forNode(listView);
        var feedback = cell.getFeedbackContainer();
        feedback.getStyleClass().add("installer-item-wrapper");
        feedback.setOnMouseClicked(event -> {
            @Nullable InstallerItem item = cell.getItem();
            if (item != null)
                item.handleInstallClick(event);
        });
        return cell;
    }

    /// Rebuilds the component controls for the selected instance, or clears them if none is selected.
    /// Minecraft editing follows the stored manifest's modifiability; other installed components
    /// require a recognized patch to expose editing controls.
    public void loadInstance(HMCLGameInstance.Optional instance) {
        @Nullable HMCLGameInstance gameInstance = instance.instance();
        this.gameInstance = gameInstance;
        if (gameInstance == null) {
            getItems().clear();
            return;
        }

        HMCLGameRepository repository = gameInstance.getRepository();

        getItems().clear();
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
                        component.getComponentType() == GameComponentType.GAME
                                ? !gameInstance.getManifest().isModifiable()
                                : !gameInstance.getAnalyzer().isClear(component.getComponentType()),
                        false
                ));
            } else {
                component.versionProperty().set(null);
            }

            component.setOnInstall(() -> {
                Controllers.getDecorator().startWizard(new UpdateInstallerWizardProvider(gameInstance, component.getComponentType(), libraryVersion));
            });

            component.setOnRemove(() -> repository.getDependency().updateInstanceAsync(
                            gameInstance.getId(),
                            publishedInstance -> repository.getDependency().removeComponentAsync(
                                    publishedInstance,
                                    component.getComponentType()))
                    .withRunAsync(Schedulers.javafx(), this::reloadCurrentInstance)
                    .start());

            getItems().add(component);
        }

        var currentItems = getItems().stream().map(InstallerItem::getComponentType).toList();
        // other third-party libraries which are unable to manage.
        for (GameComponentAnalyzer.Mark mark : gameInstance.getAnalyzer()) {
            if (currentItems.contains(mark.componentType())) continue;

            // we have done this library above.

            InstallerItem installerItem = new InstallerItem(mark.componentType(), InstallerItem.Style.LIST_ITEM);
            installerItem.versionProperty().set(new InstallerItem.InstalledState(mark.version(), false, false));
            installerItem.setOnRemove(() -> repository.getDependency().updateInstanceAsync(
                            gameInstance.getId(),
                            publishedInstance -> repository.getDependency().removeComponentAsync(
                                    publishedInstance,
                                    mark.componentType()))
                    .withRunAsync(Schedulers.javafx(), this::reloadCurrentInstance)
                    .start());

            getItems().add(installerItem);
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

    /// Runs a local component installer for the current modifiable instance and displays its result.
    private void doInstallOffline(Path file) {
        if (gameInstance == null || !gameInstance.getManifest().isModifiable()) {
            return;
        }

        HMCLGameRepository repository = gameInstance.getRepository();
        Task<?> task = repository.getDependency().updateInstanceAsync(
                gameInstance.getId(),
                publishedInstance -> repository.getDependency().installComponentLocalAsync(publishedInstance, file));
        task.setName(i18n("install.installer.install_offline"));
        Controllers.taskDialog(task.whenComplete(Schedulers.javafx(), (@Nullable Exception exception) -> {
            if (exception == null) {
                reloadCurrentInstance();
                Controllers.dialog(i18n("install.success"));
            } else {
                UpdateInstallerWizardProvider.alertFailureMessage(exception, null);
            }
        }), i18n("install.installer.install_offline"), TaskCancellationAction.NO_CANCEL);
    }

}
