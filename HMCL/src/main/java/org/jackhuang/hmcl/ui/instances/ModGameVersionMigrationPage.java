/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026  huangyuhui <huanghongxun2008@126.com> and contributors
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

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXCheckBox;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import org.jackhuang.hmcl.addon.RemoteAddon;
import org.jackhuang.hmcl.addon.mod.LocalModFile;
import org.jackhuang.hmcl.addon.mod.ModGameVersionCheck;
import org.jackhuang.hmcl.addon.mod.ModManager;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.construct.JFXCheckBoxTableCell;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane;
import org.jackhuang.hmcl.ui.construct.PageCloseEvent;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.util.TaskCancellationAction;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.jackhuang.hmcl.ui.FXUtils.onEscPressed;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// Lists mods that do not target the game version of the current instance and migrates the selected
/// ones to a compatible build.
///
/// The difference from [AddonUpdatesPage] is how the old file is handled: there `setOld(true)` renames
/// it with an `.old` suffix, which removes it from the mod list and leaves the rollback button as the
/// only way back. Here the old file is disabled instead, so it stays in the list as an unchecked entry
/// and the user can simply switch it back on.
///
/// The page performs no checking of its own; it only consumes the result of [ModGameVersionCheckTask].
@NotNullByDefault
public final class ModGameVersionMigrationPage extends BorderPane implements DecoratorPage {

    /// Progress stage identifier of the migration task, also used as the i18n key of the progress dialog.
    static final String STAGE = "mods.check_game_version.migrating";

    private final ReadOnlyObjectWrapper<State> state =
            new ReadOnlyObjectWrapper<>(DecoratorPage.State.fromTitle(i18n("mods.check_game_version")));

    /// Provides the mods directory that new files are downloaded into.
    private final ModManager modManager;

    /// Invoked after the migration finishes so that the mod list can reload its on-disk state.
    private final Runnable onFinished;

    /// Table rows, one per mod that needs action.
    private final ObservableList<MigrationObject> objects;

    /// Creates the migration page.
    ///
    /// @param modManager the mod manager of the target instance; new files land in its directory
    /// @param checks     check results, expected to contain only entries whose
    ///                   [ModGameVersionCheck#needsAction()] is `true`
    /// @param onFinished callback run on the JavaFX thread once the migration task completes, used to
    ///                   refresh the mod list
    public ModGameVersionMigrationPage(ModManager modManager, List<ModGameVersionCheck> checks,
                                       Runnable onFinished) {
        this.modManager = modManager;
        this.onFinished = onFinished;

        getStyleClass().add("gray-background");

        this.objects = FXCollections.observableArrayList(checks.stream().map(MigrationObject::new).toList());

        TableView<MigrationObject> table = new TableView<>(objects);
        table.setEditable(true);
        setupColumns(table);
        table.setRowFactory(__ -> new MigrationRow());
        setMargin(table, new Insets(10, 10, 5, 10));
        setCenter(table);

        HBox actions = new HBox(8);
        actions.setPadding(new Insets(8));
        actions.setAlignment(Pos.CENTER_RIGHT);

        JFXButton confirmButton = FXUtils.newRaisedButton(i18n("mods.check_game_version.confirm"));
        confirmButton.setOnAction(e -> migrate());

        JFXButton cancelButton = FXUtils.newRaisedButton(i18n("button.cancel"));
        cancelButton.setOnAction(e -> fireEvent(new PageCloseEvent()));
        onEscPressed(this, cancelButton::fire);
        onEscPressed(table, cancelButton::fire);

        actions.getChildren().setAll(confirmButton, cancelButton);
        setBottom(actions);
    }

    /// Builds and installs the table columns: selection, file name, declared versions, target version,
    /// source and action.
    ///
    /// The header checkbox is kept in sync with the per-row selection through [FXUtils#bindAllEnabled].
    ///
    /// @param table the table to install the columns on
    @SuppressWarnings("unchecked")
    private void setupColumns(TableView<MigrationObject> table) {
        TableColumn<MigrationObject, Boolean> enabledColumn = new TableColumn<>();
        var allEnabledBox = new JFXCheckBox();
        enabledColumn.setStyle("-fx-alignment: CENTER;");
        enabledColumn.setGraphic(allEnabledBox);
        enabledColumn.setCellFactory(JFXCheckBoxTableCell.forTableColumn(enabledColumn));
        setupCellValueFactory(enabledColumn, object -> object.enabled);
        enabledColumn.setEditable(true);
        enabledColumn.setMaxWidth(40);
        enabledColumn.setMinWidth(40);
        FXUtils.bindAllEnabled(allEnabledBox.selectedProperty(),
                objects.stream().map(object -> object.enabled).toArray(BooleanProperty[]::new));

        TableColumn<MigrationObject, String> fileNameColumn = new TableColumn<>(i18n("addon.check_update.file"));
        fileNameColumn.setPrefWidth(200);
        setupCellValueFactory(fileNameColumn, object -> object.fileName);

        TableColumn<MigrationObject, String> localGameVersionColumn =
                new TableColumn<>(i18n("mods.check_game_version.local_game_version"));
        localGameVersionColumn.setPrefWidth(140);
        setupCellValueFactory(localGameVersionColumn, object -> object.localGameVersion);

        TableColumn<MigrationObject, String> targetVersionColumn =
                new TableColumn<>(i18n("addon.check_update.target_version"));
        targetVersionColumn.setPrefWidth(180);
        setupCellValueFactory(targetVersionColumn, object -> object.targetVersion);

        TableColumn<MigrationObject, String> sourceColumn = new TableColumn<>(i18n("addon.check_update.source"));
        sourceColumn.setPrefWidth(110);
        setupCellValueFactory(sourceColumn, object -> object.source);

        TableColumn<MigrationObject, String> actionColumn =
                new TableColumn<>(i18n("mods.check_game_version.action"));
        actionColumn.setPrefWidth(200);
        setupCellValueFactory(actionColumn, object -> object.action);

        table.getColumns().setAll(enabledColumn, fileNameColumn, localGameVersionColumn,
                targetVersionColumn, sourceColumn, actionColumn);
    }

    /// Binds a property as the value source of a table column.
    ///
    /// @param column the target column
    /// @param mapper extracts the matching property from a row
    /// @param <T>    the value type of the column
    private <T> void setupCellValueFactory(TableColumn<MigrationObject, T> column,
                                           Function<MigrationObject, ObservableValue<T>> mapper) {
        column.setCellValueFactory(param -> mapper.apply(param.getValue()));
    }

    /// Migrates the selected mods and closes this page afterwards.
    ///
    /// Nothing is started when no row is selected. Mods that failed to download are collected into a
    /// single error dialog, and the success dialog is suppressed in that case. [#onFinished] runs
    /// regardless of the outcome, because some mods may already have been processed and the on-disk
    /// state no longer matches the list.
    private void migrate() {
        List<ModGameVersionCheck> selected = objects.stream()
                .filter(object -> object.enabled.get())
                .map(object -> object.check)
                .toList();

        if (selected.isEmpty()) {
            Controllers.dialog(i18n("mods.check_game_version.nothing_selected"));
            return;
        }

        MigrationTask task = new MigrationTask(modManager.getDirectory(), selected);
        Controllers.taskDialog(
                task.whenComplete(Schedulers.javafx(), exception -> {
                    fireEvent(new PageCloseEvent());
                    onFinished.run();

                    if (!task.getFailedMods().isEmpty()) {
                        Controllers.dialog(i18n("addon.check_update.failed_download") + "\n"
                                        + task.getFailedMods().stream()
                                        .map(LocalModFile::getFileName)
                                        .collect(Collectors.joining("\n")),
                                i18n("install.failed"),
                                MessageDialogPane.MessageType.ERROR);
                    } else if (exception == null) {
                        Controllers.dialog(i18n("install.success"));
                    }
                }),
                i18n("mods.check_game_version"), TaskCancellationAction.NORMAL);
    }

    /// Table row that paints rows without a compatible build in the error container colour.
    ///
    /// The row background is used rather than the text fill because the `-fx-text-fill` a table cell
    /// carries by default would override anything set on the row, while cell backgrounds are transparent
    /// and let the row show through.
    private static final class MigrationRow extends TableRow<MigrationObject> {
        @Override
        protected void updateItem(@Nullable MigrationObject item, boolean empty) {
            super.updateItem(item, empty);

            boolean noCandidate = !empty && item != null
                    && item.check.status() == ModGameVersionCheck.Status.NO_CANDIDATE;
            setStyle(noCandidate ? "-fx-background-color: -monet-error-container;" : "");
        }
    }

    /// A table row, flattening a [ModGameVersionCheck] into bindable properties.
    private static final class MigrationObject {

        /// The check result this row represents.
        final ModGameVersionCheck check;

        /// Whether this mod takes part in the migration.
        final BooleanProperty enabled = new SimpleBooleanProperty();

        /// Mod file name, without extension or state suffix.
        final StringProperty fileName = new SimpleStringProperty();

        /// Game versions the local file declares support for, comma separated.
        final StringProperty localGameVersion = new SimpleStringProperty();

        /// Version of the target build, or a placeholder when there is none.
        final StringProperty targetVersion = new SimpleStringProperty();

        /// Repository that produced the conclusion.
        final StringProperty source = new SimpleStringProperty();

        /// Description of what will be done to this mod.
        final StringProperty action = new SimpleStringProperty();

        MigrationObject(ModGameVersionCheck check) {
            this.check = check;

            LocalModFile localModFile = check.localModFile();

            // 本来就处于禁用状态的模组默认不参与迁移，与 AddonUpdatesPage 的初始化规则保持一致
            enabled.set(!localModFile.isDisabled());
            fileName.set(localModFile.getFileName());
            localGameVersion.set(check.localGameVersions().isEmpty()
                    ? i18n("message.unknown")
                    : String.join(", ", check.localGameVersions()));

            @Nullable RemoteAddon.Version target = check.targetVersion();
            targetVersion.set(target != null ? target.version() : "-");
            action.set(target != null
                    ? i18n("mods.check_game_version.action.replace")
                    : i18n("mods.check_game_version.action.disable_only"));

            @Nullable RemoteAddon.Source checkSource = check.source();
            if (checkSource != null) {
                source.set(switch (checkSource) {
                    case CURSEFORGE -> i18n("addon.curseforge");
                    case MODRINTH -> i18n("addon.modrinth");
                });
            }
        }
    }

    /// Task that performs the migration.
    ///
    /// One parallel subtask per mod, and a failing mod does not affect the others. When a compatible
    /// build exists the old file is disabled first and the new file downloaded afterwards; when there is
    /// none the file is only disabled.
    ///
    /// The order of disabling and downloading must not be swapped: [ModManager#disableMod] only acts on
    /// active files, and the old file has to leave its active state before the new one lands, otherwise
    /// the game would load two copies of the same mod.
    static final class MigrationTask extends Task<Void> {

        private final Collection<Task<?>> dependents;

        /// Mods whose download failed and that were rolled back to their previous state.
        private final List<LocalModFile> failedMods = new ArrayList<>();

        /// Creates the migration task.
        ///
        /// @param modsDirectory the directory new files are downloaded into, i.e. the instance mods directory
        /// @param checks        the check results to process
        MigrationTask(Path modsDirectory, List<ModGameVersionCheck> checks) {
            setStage(STAGE);
            getProperties().put("total", checks.size());

            this.dependents = new ArrayList<>(checks.size());
            for (ModGameVersionCheck check : checks) {
                LocalModFile local = check.localModFile();
                @Nullable RemoteAddon.Version target = check.targetVersion();

                if (target == null) {
                    dependents.add(Task.runAsync(Schedulers.javafx(), () -> local.setActive(false))
                            .withCounter(STAGE));
                    continue;
                }

                // 在构造阶段（JavaFX 线程）读取原状态，失败回滚时据此决定是否重新启用
                boolean wasActive = local.isActive();

                dependents.add(Task
                        .runAsync(Schedulers.javafx(), () -> local.setActive(false))
                        .thenComposeAsync(() -> {
                            var downloadTask = new FileDownloadTask(
                                    target.file().url(),
                                    modsDirectory.resolve(target.file().filename())
                            );
                            downloadTask.setName(target.name());
                            return downloadTask;
                        })
                        .whenComplete(Schedulers.javafx(), exception -> {
                            if (exception != null) {
                                if (wasActive) {
                                    local.setActive(true);
                                }
                                failedMods.add(local);
                            }
                        })
                        .withCounter(STAGE));
            }
        }

        /// Returns the mods whose download failed.
        ///
        /// @return the failed mods, already restored to the state they had before the migration
        List<LocalModFile> getFailedMods() {
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
            if (!isDependentsSucceeded()) {
                throw getException();
            }
        }
    }

    @Override
    public ReadOnlyObjectWrapper<State> stateProperty() {
        return state;
    }
}
