/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
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

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXCheckBox;
import javafx.beans.property.*;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import org.jackhuang.hmcl.mod.LocalAddonFile;
import org.jackhuang.hmcl.mod.LocalAddonManager;
import org.jackhuang.hmcl.mod.RemoteMod;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.construct.JFXCheckBoxTableCell;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane;
import org.jackhuang.hmcl.ui.construct.PageCloseEvent;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.TaskCancellationAction;
import org.jackhuang.hmcl.util.io.CSVTable;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.jackhuang.hmcl.ui.FXUtils.onEscPressed;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class AddonUpdatesPage<F extends LocalAddonFile> extends BorderPane implements DecoratorPage {
    private final ReadOnlyObjectWrapper<State> state = new ReadOnlyObjectWrapper<>(DecoratorPage.State.fromTitle(i18n("mods.check_updates")));

    private final LocalAddonManager<F> localAddonManager;
    private final ObservableList<AddonUpdateObject> objects;

    @SuppressWarnings("unchecked")
    public AddonUpdatesPage(LocalAddonManager<F> localAddonManager, List<LocalAddonFile.AddonUpdate> updates) {
        this.localAddonManager = localAddonManager;

        getStyleClass().add("gray-background");

        TableColumn<AddonUpdateObject, Boolean> enabledColumn = new TableColumn<>();
        var allEnabledBox = new JFXCheckBox();
        enabledColumn.setStyle("-fx-alignment: CENTER;");
        enabledColumn.setGraphic(allEnabledBox);
        enabledColumn.setCellFactory(JFXCheckBoxTableCell.forTableColumn(enabledColumn));
        setupCellValueFactory(enabledColumn, AddonUpdateObject::enabledProperty);
        enabledColumn.setEditable(true);
        enabledColumn.setMaxWidth(40);
        enabledColumn.setMinWidth(40);

        TableColumn<AddonUpdateObject, String> fileNameColumn = new TableColumn<>(i18n("mods.check_updates.file"));
        fileNameColumn.setPrefWidth(200);
        setupCellValueFactory(fileNameColumn, AddonUpdateObject::fileNameProperty);

        TableColumn<AddonUpdateObject, String> currentVersionColumn = new TableColumn<>(i18n("mods.check_updates.current_version"));
        currentVersionColumn.setPrefWidth(200);
        setupCellValueFactory(currentVersionColumn, AddonUpdateObject::currentVersionProperty);

        TableColumn<AddonUpdateObject, String> targetVersionColumn = new TableColumn<>(i18n("mods.check_updates.target_version"));
        targetVersionColumn.setPrefWidth(200);
        setupCellValueFactory(targetVersionColumn, AddonUpdateObject::targetVersionProperty);

        TableColumn<AddonUpdateObject, String> sourceColumn = new TableColumn<>(i18n("mods.check_updates.source"));
        setupCellValueFactory(sourceColumn, AddonUpdateObject::sourceProperty);

        objects = FXCollections.observableList(updates.stream().map(AddonUpdateObject::new).collect(Collectors.toList()));
        FXUtils.bindAllEnabled(allEnabledBox.selectedProperty(), objects.stream().map(o -> o.enabled).toArray(BooleanProperty[]::new));

        TableView<AddonUpdateObject> table = new TableView<>(objects);
        table.setEditable(true);
        table.getColumns().setAll(enabledColumn, fileNameColumn, currentVersionColumn, targetVersionColumn, sourceColumn);
        setMargin(table, new Insets(10, 10, 5, 10));

        setCenter(table);

        HBox actions = new HBox(8);
        actions.setPadding(new Insets(8));
        actions.setAlignment(Pos.CENTER_RIGHT);

        JFXButton exportListButton = FXUtils.newRaisedButton(i18n("button.export"));
        exportListButton.setOnAction(e -> exportList());

        JFXButton nextButton = FXUtils.newRaisedButton(i18n("mods.check_updates.confirm"));
        nextButton.setOnAction(e -> updateFiles());

        JFXButton cancelButton = FXUtils.newRaisedButton(i18n("button.cancel"));
        cancelButton.setOnAction(e -> fireEvent(new PageCloseEvent()));
        onEscPressed(this, cancelButton::fire);
        onEscPressed(table, cancelButton::fire);

        actions.getChildren().setAll(exportListButton, nextButton, cancelButton);
        setBottom(actions);
    }

    private <T> void setupCellValueFactory(TableColumn<AddonUpdateObject, T> column, Function<AddonUpdateObject, ObservableValue<T>> mapper) {
        column.setCellValueFactory(param -> mapper.apply(param.getValue()));
    }

    private void updateFiles() {
        AddonUpdateTask task = new AddonUpdateTask(
                localAddonManager.getDirectory(),
                objects.stream()
                        .filter(AddonUpdateObject::isEnabled)
                        .map(AddonUpdateObject::getData)
                        .toList()
        );
        Controllers.taskDialog(
                task.whenComplete(Schedulers.javafx(), exception -> {
                    fireEvent(new PageCloseEvent());
                    if (!task.getFailedAddons().isEmpty()) {
                        Controllers.dialog(i18n("mods.check_updates.failed_download") + "\n" +
                                        task.getFailedAddons().stream().map(LocalAddonFile::getFileName).collect(Collectors.joining("\n")),
                                i18n("install.failed"),
                                MessageDialogPane.MessageType.ERROR);
                    }

                    if (exception == null) {
                        Controllers.dialog(i18n("install.success"));
                    }
                }),
                i18n("mods.check_updates"),
                TaskCancellationAction.NORMAL);
    }

    private void exportList() {
        Path path = Paths.get("hmcl-mod-update-list-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss")) + ".csv").toAbsolutePath();

        Controllers.taskDialog(Task.runAsync(() -> {
            CSVTable csvTable = new CSVTable();

            csvTable.set(0, 0, "Source File Name");
            csvTable.set(1, 0, "Current Version");
            csvTable.set(2, 0, "Target Version");
            csvTable.set(3, 0, "Update Source");

            for (int i = 0; i < objects.size(); i++) {
                csvTable.set(0, i + 1, objects.get(i).fileName.get());
                csvTable.set(1, i + 1, objects.get(i).currentVersion.get());
                csvTable.set(2, i + 1, objects.get(i).targetVersion.get());
                csvTable.set(3, i + 1, objects.get(i).source.get());
            }

            csvTable.write(path);

            FXUtils.showFileInExplorer(path);
        }).whenComplete(Schedulers.javafx(), exception -> {
            if (exception == null) {
                Controllers.dialog(path.toString(), i18n("message.success"));
            } else {
                Controllers.dialog("", i18n("message.error"), MessageDialogPane.MessageType.ERROR);
            }
        }), i18n("button.export"), TaskCancellationAction.NORMAL);
    }

    @Override
    public ReadOnlyObjectWrapper<State> stateProperty() {
        return state;
    }

    private static final class AddonUpdateObject {
        final LocalAddonFile.AddonUpdate data;
        final BooleanProperty enabled = new SimpleBooleanProperty();
        final StringProperty fileName = new SimpleStringProperty();
        final StringProperty currentVersion = new SimpleStringProperty();
        final StringProperty targetVersion = new SimpleStringProperty();
        final StringProperty source = new SimpleStringProperty();

        public AddonUpdateObject(LocalAddonFile.AddonUpdate data) {
            this.data = data;

            enabled.set(!data.localAddonFile().isDisabled());
            fileName.set(data.localAddonFile().getFileName());
            currentVersion.set(data.currentVersion().getVersion());
            targetVersion.set(data.targetVersion().getVersion());
            switch (data.currentVersion().getSelf().getType()) {
                case CURSEFORGE:
                    source.set(i18n("mods.curseforge"));
                    break;
                case MODRINTH:
                    source.set(i18n("mods.modrinth"));
            }
        }

        public LocalAddonFile.AddonUpdate getData() {
            return data;
        }

        public boolean isEnabled() {
            return enabled.get();
        }

        public BooleanProperty enabledProperty() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled.set(enabled);
        }

        public String getFileName() {
            return fileName.get();
        }

        public StringProperty fileNameProperty() {
            return fileName;
        }

        public void setFileName(String fileName) {
            this.fileName.set(fileName);
        }

        public String getCurrentVersion() {
            return currentVersion.get();
        }

        public StringProperty currentVersionProperty() {
            return currentVersion;
        }

        public void setCurrentVersion(String currentVersion) {
            this.currentVersion.set(currentVersion);
        }

        public String getTargetVersion() {
            return targetVersion.get();
        }

        public StringProperty targetVersionProperty() {
            return targetVersion;
        }

        public void setTargetVersion(String targetVersion) {
            this.targetVersion.set(targetVersion);
        }

        public String getSource() {
            return source.get();
        }

        public StringProperty sourceProperty() {
            return source;
        }

        public void setSource(String source) {
            this.source.set(source);
        }
    }

    public static class AddonUpdateTask extends Task<Void> {
        private final Collection<Task<?>> dependents;
        private final List<LocalAddonFile> failedAddons = new ArrayList<>();

        AddonUpdateTask(Path addonDirectory, List<LocalAddonFile.AddonUpdate> addons) {
            setStage("mods.check_updates.confirm");
            getProperties().put("total", addons.size());

            this.dependents = new ArrayList<>();
            for (LocalAddonFile.AddonUpdate addon : addons) {
                LocalAddonFile local = addon.localAddonFile();
                RemoteMod.Version remote = addon.targetVersion();
                boolean isDisabled = local.isDisabled();
                String originalFileName = local.getFile().getFileName().toString();

                dependents.add(Task
                        .runAsync(Schedulers.javafx(), () -> local.setOld(true))
                        .thenComposeAsync(() -> {
                            String fileName = addon.useRemoteFileName() ? remote.getFile().getFilename() : originalFileName;
                            if (isDisabled)
                                fileName = StringUtils.addSuffix(fileName, LocalAddonManager.DISABLED_EXTENSION);

                            var task = new FileDownloadTask(
                                    remote.getFile().getUrl(),
                                    addonDirectory.resolve(fileName)
                            );

                            task.setName(remote.getName());
                            return task;
                        })
                        .whenComplete(Schedulers.javafx(), exception -> {
                            if (exception != null) {
                                // restore state if failed
                                local.setOld(false);
                                if (isDisabled)
                                    local.markDisabled();
                                failedAddons.add(local);
                            } else if (!local.keepOldFiles()) {
                                try {
                                    local.delete();
                                } catch (IOException e) {
                                    // ignore
                                }
                            }
                        })
                        .withCounter("mods.check_updates.confirm"));
            }
        }

        public List<LocalAddonFile> getFailedAddons() {
            return failedAddons;
        }

        @Override
        public Collection<Task<?>> getDependents() {
            return dependents;
        }

        @Override
        public boolean doPreExecute() {
            return true;
        }

        @Override
        public void preExecute() {
            notifyPropertiesChanged();
        }

        @Override
        public boolean isRelyingOnDependents() {
            return false;
        }

        @Override
        public void execute() throws Exception {
            if (!isDependentsSucceeded())
                throw getException();
        }
    }
}
