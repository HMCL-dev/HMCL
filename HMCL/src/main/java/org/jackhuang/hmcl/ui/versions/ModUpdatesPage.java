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
import com.jfoenix.controls.JFXDialogLayout;
import javafx.beans.property.*;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import org.jackhuang.hmcl.mod.*;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.util.Pair;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.TaskCancellationAction;
import org.jackhuang.hmcl.util.i18n.I18n;
import org.jackhuang.hmcl.util.io.CSVTable;
import org.jackhuang.hmcl.util.javafx.BindingMapping;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.jackhuang.hmcl.ui.FXUtils.onEscPressed;
import static org.jackhuang.hmcl.util.Pair.pair;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class ModUpdatesPage extends BorderPane implements DecoratorPage {
    private final ReadOnlyObjectWrapper<State> state = new ReadOnlyObjectWrapper<>(DecoratorPage.State.fromTitle(i18n("mods.check_updates")));

    private final ModManager modManager;
    private final ObservableList<ModUpdateObject> objects;

    @SuppressWarnings("unchecked")
    public ModUpdatesPage(ModManager modManager, List<LocalModFile.ModUpdate> updates) {
        this.modManager = modManager;

        getStyleClass().add("gray-background");

        TableColumn<ModUpdateObject, Boolean> enabledColumn = new TableColumn<>();
        CheckBox allEnabledBox = new CheckBox();
        enabledColumn.setGraphic(allEnabledBox);
        enabledColumn.setCellFactory(CheckBoxTableCell.forTableColumn(enabledColumn));
        setupCellValueFactory(enabledColumn, ModUpdateObject::enabledProperty);
        enabledColumn.setEditable(true);
        enabledColumn.setMaxWidth(40);
        enabledColumn.setMinWidth(40);

        TableColumn<ModUpdateObject, String> fileNameColumn = new TableColumn<>(i18n("mods.check_updates.file"));
        fileNameColumn.setPrefWidth(180);
        setupCellValueFactory(fileNameColumn, ModUpdateObject::fileNameProperty);

        TableColumn<ModUpdateObject, String> currentVersionColumn = new TableColumn<>(i18n("mods.check_updates.current_version"));
        currentVersionColumn.setPrefWidth(180);
        setupCellValueFactory(currentVersionColumn, ModUpdateObject::currentVersionProperty);

        TableColumn<ModUpdateObject, String> targetVersionColumn = new TableColumn<>(i18n("mods.check_updates.target_version"));
        targetVersionColumn.setPrefWidth(180);
        setupCellValueFactory(targetVersionColumn, ModUpdateObject::targetVersionProperty);

        TableColumn<ModUpdateObject, String> sourceColumn = new TableColumn<>(i18n("mods.check_updates.source"));
        setupCellValueFactory(sourceColumn, ModUpdateObject::sourceProperty);

        TableColumn<ModUpdateObject, String> detailColumn = new TableColumn<>();
        detailColumn.setCellFactory(param -> {
            TableCell<ModUpdateObject, String> cell = (TableCell<ModUpdateObject, String>) TableColumn.DEFAULT_CELL_FACTORY.call(param);
            cell.setOnMouseClicked(event -> {
                List<ModUpdateObject> items = cell.getTableColumn().getTableView().getItems();
                if (cell.getIndex() >= items.size()) {
                    return;
                }
                ModUpdateObject object = items.get(cell.getIndex());
                Controllers.dialog(new ModDetail(object.data.getCandidates().get(0), object.data.getRepository(), object.getSource()));
            });
            return cell;
        });
        detailColumn.setCellValueFactory(it -> new SimpleStringProperty(i18n("mods.check_updates.show_detail")));

        objects = FXCollections.observableList(updates.stream().map(ModUpdateObject::new).collect(Collectors.toList()));
        FXUtils.bindAllEnabled(allEnabledBox.selectedProperty(), objects.stream().map(o -> o.enabled).toArray(BooleanProperty[]::new));

        TableView<ModUpdateObject> table = new TableView<>(objects);
        table.setEditable(true);
        table.getColumns().setAll(enabledColumn, fileNameColumn, currentVersionColumn, targetVersionColumn, sourceColumn, detailColumn);

        setCenter(table);

        HBox actions = new HBox(8);
        actions.setPadding(new Insets(8));
        actions.setAlignment(Pos.CENTER_RIGHT);

        JFXButton exportListButton = FXUtils.newRaisedButton(i18n("button.export"));
        exportListButton.setOnAction(e -> exportList());

        JFXButton nextButton = FXUtils.newRaisedButton(i18n("mods.check_updates.confirm"));
        nextButton.setOnAction(e -> updateMods());

        JFXButton cancelButton = FXUtils.newRaisedButton(i18n("button.cancel"));
        cancelButton.setOnAction(e -> fireEvent(new PageCloseEvent()));
        onEscPressed(this, cancelButton::fire);
        onEscPressed(table, cancelButton::fire);

        actions.getChildren().setAll(exportListButton, nextButton, cancelButton);
        setBottom(actions);
    }

    private <T> void setupCellValueFactory(TableColumn<ModUpdateObject, T> column, Function<ModUpdateObject, ObservableValue<T>> mapper) {
        column.setCellValueFactory(param -> mapper.apply(param.getValue()));
    }

    private void updateMods() {
        ModUpdateTask task = new ModUpdateTask(
                modManager,
                objects.stream()
                        .filter(o -> o.enabled.get())
                        .map(object -> pair(object.data.getLocalMod(), object.data.getCandidates().get(0)))
                        .collect(Collectors.toList()));
        Controllers.taskDialog(
                task.whenComplete(Schedulers.javafx(), exception -> {
                    fireEvent(new PageCloseEvent());
                    if (!task.getFailedMods().isEmpty()) {
                        Controllers.dialog(i18n("mods.check_updates.failed_download") + "\n" +
                                        task.getFailedMods().stream().map(LocalModFile::getFileName).collect(Collectors.joining("\n")),
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

    private static final class ModUpdateObject {
        final LocalModFile.ModUpdate data;
        final BooleanProperty enabled = new SimpleBooleanProperty();
        final StringProperty fileName = new SimpleStringProperty();
        final StringProperty currentVersion = new SimpleStringProperty();
        final StringProperty targetVersion = new SimpleStringProperty();
        final StringProperty source = new SimpleStringProperty();

        public ModUpdateObject(LocalModFile.ModUpdate data) {
            this.data = data;

            enabled.set(!data.getLocalMod().getModManager().isDisabled(data.getLocalMod().getFile()));
            fileName.set(data.getLocalMod().getFileName());
            currentVersion.set(data.getCurrentVersion().getVersion());
            targetVersion.set(data.getCandidates().get(0).getVersion());
            switch (data.getCurrentVersion().getSelf().getType()) {
                case CURSEFORGE:
                    source.set(i18n("mods.curseforge"));
                    break;
                case MODRINTH:
                    source.set(i18n("mods.modrinth"));
            }
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

    private static final class ModItem extends StackPane {

        ModItem(RemoteMod.Version targetVersion, String source) {
            VBox pane = new VBox(8);
            pane.setPadding(new Insets(8, 0, 8, 0));

            {
                HBox descPane = new HBox(8);
                descPane.setPadding(new Insets(0, 8, 0, 8));
                descPane.setAlignment(Pos.CENTER_LEFT);
                descPane.setMouseTransparent(true);

                {
                    StackPane graphicPane = new StackPane();
                    TwoLineListItem content = new TwoLineListItem();
                    HBox.setHgrow(content, Priority.ALWAYS);
                    content.setTitle(targetVersion.getVersion());
                    content.setSubtitle(I18n.formatDateTime(targetVersion.getDatePublished()));

                    switch (targetVersion.getVersionType()) {
                        case Alpha:
                            content.addTag(i18n("mods.channel.alpha"));
                            graphicPane.getChildren().setAll(SVG.ALPHA_CIRCLE.createIcon(24));
                            break;
                        case Beta:
                            content.addTag(i18n("mods.channel.beta"));
                            graphicPane.getChildren().setAll(SVG.BETA_CIRCLE.createIcon(24));
                            break;
                        case Release:
                            content.addTag(i18n("mods.channel.release"));
                            graphicPane.getChildren().setAll(SVG.RELEASE_CIRCLE.createIcon(24));
                            break;
                    }

                    for (ModLoaderType modLoaderType : targetVersion.getLoaders()) {
                        switch (modLoaderType) {
                            case FORGE:
                                content.addTag(i18n("install.installer.forge"));
                                break;
                            case CLEANROOM:
                                content.addTag(i18n("install.installer.cleanroom"));
                                break;
                            case NEO_FORGED:
                                content.addTag(i18n("install.installer.neoforge"));
                                break;
                            case FABRIC:
                                content.addTag(i18n("install.installer.fabric"));
                                break;
                            case LITE_LOADER:
                                content.addTag(i18n("install.installer.liteloader"));
                                break;
                            case QUILT:
                                content.addTag(i18n("install.installer.quilt"));
                                break;
                        }
                    }

                    content.addTag(source);

                    descPane.getChildren().setAll(graphicPane, content);
                }

                pane.getChildren().add(descPane);
            }

            getChildren().setAll(new RipplerContainer(pane));

            // Workaround for https://github.com/HMCL-dev/HMCL/issues/2129
            this.setMinHeight(50);
        }
    }

    private static final class ModDetail extends JFXDialogLayout {

        private final RemoteModRepository repository;

        public ModDetail(RemoteMod.Version targetVersion, RemoteModRepository repository, String source) {
            this.repository = repository;

            this.setHeading(new HBox(new Label(i18n("mods.check_updates.update_mod", targetVersion.getName()))));

            VBox box = new VBox(8);
            box.setPadding(new Insets(8));
            box.getChildren().setAll(new ModItem(targetVersion, source));

            SpinnerPane spinnerPane = new SpinnerPane();
            ScrollPane scrollPane = new ScrollPane();
            ComponentList changelogComponent = new ComponentList(null);
            loadChangelog(targetVersion, spinnerPane, changelogComponent);
            spinnerPane.setOnFailedAction(e -> loadChangelog(targetVersion, spinnerPane, changelogComponent));

            scrollPane.setContent(changelogComponent);
            scrollPane.setFitToWidth(true);
            scrollPane.setFitToHeight(true);
            spinnerPane.setContent(scrollPane);
            box.getChildren().add(spinnerPane);
            VBox.setVgrow(spinnerPane, Priority.SOMETIMES);

            this.setBody(box);

            JFXButton closeButton = new JFXButton(i18n("button.ok"));
            closeButton.getStyleClass().add("dialog-accept");
            closeButton.setOnAction(e -> fireEvent(new DialogCloseEvent()));

            setActions(closeButton);

            this.prefWidthProperty().bind(BindingMapping.of(Controllers.getStage().widthProperty()).map(w -> w.doubleValue() * 0.7));
            this.prefHeightProperty().bind(BindingMapping.of(Controllers.getStage().heightProperty()).map(w -> w.doubleValue() * 0.7));

            onEscPressed(this, closeButton::fire);
        }

        private void loadChangelog(RemoteMod.Version version, SpinnerPane spinnerPane, ComponentList componentList) {
            spinnerPane.setLoading(true);
            Task.supplyAsync(() -> {
                if (version.getChangelog() != null) {
                    return StringUtils.nullIfBlank(version.getChangelog());
                } else {
                    try {
                        return StringUtils.nullIfBlank(StringUtils.htmlToText(repository.getModChangelog(version.getModid(), version.getVersionId())));
                    } catch (UnsupportedOperationException e) {
                        return Optional.<String>empty();
                    }
                }
            }).whenComplete(Schedulers.javafx(), (result, exception) -> {
                if (exception == null) {
                    result.ifPresent(s -> componentList.getContent().setAll(new HBox(new Text(s))));
                    spinnerPane.setFailedReason(null);
                } else {
                    spinnerPane.setFailedReason(i18n("download.failed.refresh"));
                }
                spinnerPane.setLoading(false);
            }).start();
        }
    }

    public static class ModUpdateTask extends Task<Void> {
        private final Collection<Task<?>> dependents;
        private final List<LocalModFile> failedMods = new ArrayList<>();

        ModUpdateTask(ModManager modManager, List<Pair<LocalModFile, RemoteMod.Version>> mods) {
            setStage("mods.check_updates.confirm");
            getProperties().put("total", mods.size());

            this.dependents = new ArrayList<>();
            for (Pair<LocalModFile, RemoteMod.Version> mod : mods) {
                LocalModFile local = mod.getKey();
                RemoteMod.Version remote = mod.getValue();
                boolean isDisabled = local.getModManager().isDisabled(local.getFile());

                dependents.add(Task
                        .runAsync(Schedulers.javafx(), () -> local.setOld(true))
                        .thenComposeAsync(() -> {
                            String fileName = remote.getFile().getFilename();
                            if (isDisabled)
                                fileName += ModManager.DISABLED_EXTENSION;

                            var task = new FileDownloadTask(
                                    remote.getFile().getUrl(),
                                    modManager.getModsDirectory().resolve(fileName));

                            task.setName(remote.getName());
                            return task;
                        })
                        .whenComplete(Schedulers.javafx(), exception -> {
                            if (exception != null) {
                                // restore state if failed
                                local.setOld(false);
                                if (isDisabled)
                                    local.disable();
                                failedMods.add(local);
                            }
                        })
                        .withCounter("mods.check_updates.confirm"));
            }
        }

        public List<LocalModFile> getFailedMods() {
            return failedMods;
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
