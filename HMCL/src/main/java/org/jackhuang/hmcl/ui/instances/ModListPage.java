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

import com.jfoenix.controls.*;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.DoubleBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;
import javafx.util.Duration;
import org.jackhuang.hmcl.addon.AddonLoader;
import org.jackhuang.hmcl.addon.RemoteAddon;
import org.jackhuang.hmcl.addon.RemoteAddonRepository;
import org.jackhuang.hmcl.addon.repository.CurseForgeRemoteAddonRepository;
import org.jackhuang.hmcl.addon.repository.ModrinthRemoteAddonRepository;
import org.jackhuang.hmcl.game.*;
import org.jackhuang.hmcl.addon.mod.LocalModFile;
import org.jackhuang.hmcl.addon.mod.MinecraftVersionMatcher;
import org.jackhuang.hmcl.addon.mod.ModConflict;
import org.jackhuang.hmcl.addon.mod.ModDependency;
import org.jackhuang.hmcl.addon.mod.ModLoaderType;
import org.jackhuang.hmcl.addon.mod.ModManager;
import org.jackhuang.hmcl.addon.mod.ModRelationIndex;
import org.jackhuang.hmcl.addon.mod.NestedJarInspector;
import org.jackhuang.hmcl.setting.DownloadProviders;
import org.jackhuang.hmcl.setting.GameDirectory;
import org.jackhuang.hmcl.setting.GameInstanceIconType;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.*;
import org.jackhuang.hmcl.ui.animation.AnimationUtils;
import org.jackhuang.hmcl.ui.animation.ContainerAnimations;
import org.jackhuang.hmcl.ui.animation.Motion;
import org.jackhuang.hmcl.ui.animation.TransitionPane;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.util.*;
import org.jackhuang.hmcl.util.i18n.I18n;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jackhuang.hmcl.util.io.CSVTable;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.io.NetworkUtils;
import org.jackhuang.hmcl.util.javafx.ItemPropertyAsyncCache;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.ref.WeakReference;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.jackhuang.hmcl.ui.FXUtils.ignoreEvent;
import static org.jackhuang.hmcl.ui.FXUtils.onEscPressed;
import static org.jackhuang.hmcl.ui.ToolbarListPageSkin.createToolbarButton2;
import static org.jackhuang.hmcl.util.Lang.mapOf;
import static org.jackhuang.hmcl.util.Pair.pair;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class ModListPage extends ListPageBase<ModListPage.ModInfoObject> implements PageAware {
    private final WeakListenerHolder listenerHolder = new WeakListenerHolder();

    /// Changes after a background JIJ scan so expanded rows can rebuild their nested metadata.
    private final IntegerProperty bundledScanGeneration =
            new SimpleIntegerProperty(this, "bundledScanGeneration", 0);

    /// Whether dependency-changing operations may use the complete bundled-mod provider set.
    private final BooleanProperty bundledScanReady =
            new SimpleBooleanProperty(this, "bundledScanReady", true);

    /// Whether the latest complete-analysis attempt failed or was truncated.
    private final BooleanProperty bundledScanFailed =
            new SimpleBooleanProperty(this, "bundledScanFailed", false);

    private ModManager modManager;
    private @Nullable HMCLGameInstance gameInstance;
    /// Immutable relation index published after the current manager's complete JIJ scan.
    private @Nullable ModRelationIndex relationIndex;

    /// Monotonic token preventing an older asynchronous load from publishing into a newer page state.
    private long loadGeneration;
    /// Context cancelled whenever a newer page load supersedes the active nested-mod scan.
    private @Nullable NestedJarInspector.ScanContext bundledScanContext;

    final EnumSet<ModLoaderType> supportedLoaders = EnumSet.noneOf(ModLoaderType.class);

    /// Creates a mod list that reloads when `instanceContext` changes.
    ///
    /// @param instanceContext the parent page's instance property
    public ModListPage(ObservableValue<? extends HMCLGameInstance.Optional> instanceContext) {
        Objects.requireNonNull(instanceContext, "instanceContext");
        FXUtils.applyDragListener(this, it -> ModManager.MOD_EXTENSIONS.contains(FileUtils.getExtension(it).toLowerCase(Locale.ROOT)), mods -> {
            mods.forEach(it -> {
                try {
                    modManager.addMod(it);
                } catch (IOException | IllegalArgumentException e) {
                    LOG.warning("Unable to parse mod file " + it, e);
                }
            });
            loadMods(modManager);
        });

        listenerHolder.add(FXUtils.onWeakChangeAndOperate(instanceContext, current -> {
            if (current != null) {
                loadInstance(current);
            }
        }));
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new ModListPageSkin(this);
    }

    public void refresh() {
        loadMods(modManager);
    }

    public void loadInstance(HMCLGameInstance.Optional instance) {
        this.gameInstance = instance.instance();
        if (gameInstance == null) {
            return;
        }

        loadMods(gameInstance.getModManager());
    }

    /// Reloads top-level metadata, cancelling any older nested analysis before starting a new one.
    private void loadMods(ModManager modManager) {
        if (bundledScanContext != null) {
            bundledScanContext.cancel();
        }
        NestedJarInspector.ScanContext scanContext = new NestedJarInspector.ScanContext();
        bundledScanContext = scanContext;
        long generation = ++loadGeneration;
        setLoading(true);
        bundledScanReady.set(false);
        bundledScanFailed.set(false);
        relationIndex = null;
        if (this.modManager != modManager) {
            getItems().clear();
        }
        this.modManager = modManager;
        CompletableFuture.supplyAsync(() -> {
            try {
                modManager.refresh();
                return modManager.getLocalFiles().stream().map(ModInfoObject::new).toList();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }, Schedulers.io()).whenCompleteAsync((list, exception) -> {
            if (generation != loadGeneration || this.modManager != modManager) {
                return;
            }

            supportedLoaders.clear();
            supportedLoaders.addAll(modManager.getSupportedLoaders());

            if (exception == null) {
                getItems().setAll(list);
            } else {
                LOG.warning("Failed to load mods", exception);
                getItems().clear();
            }
            setLoading(false);

            if (exception == null) {
                CompletableFuture.supplyAsync(() -> {
                    try {
                        return modManager.getRelationIndex(scanContext);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }, Schedulers.io()).whenCompleteAsync((index, scanException) -> {
                            if (generation != loadGeneration || this.modManager != modManager) {
                                return;
                            }
                            if (bundledScanContext == scanContext) {
                                bundledScanContext = null;
                            }
                            if (scanException != null) {
                                LOG.warning("Failed to scan Jar-in-Jar trees", scanException);
                            } else {
                                relationIndex = index;
                                bundledScanGeneration.set(bundledScanGeneration.get() + 1);
                            }
                            bundledScanReady.set(scanException == null && index != null && index.isComplete());
                            bundledScanFailed.set(scanException != null || index == null || !index.isComplete());
                        }, Schedulers.javafx());
            } else {
                if (bundledScanContext == scanContext) {
                    bundledScanContext = null;
                }
                bundledScanReady.set(false);
                bundledScanFailed.set(true);
            }
        }, Schedulers.javafx());
    }

    /// Returns the generation updated after each completed background JIJ scan.
    public IntegerProperty bundledScanGenerationProperty() {
        return bundledScanGeneration;
    }

    /// Returns whether disable and remove operations may safely resolve reverse dependencies.
    public BooleanProperty bundledScanReadyProperty() {
        return bundledScanReady;
    }

    /// Returns whether the latest nested analysis was incomplete or failed.
    public BooleanProperty bundledScanFailedProperty() {
        return bundledScanFailed;
    }

    /// Returns the current instance's Minecraft version, or an empty string before an instance loads.
    public String getGameVersion() {
        return gameInstance != null ? gameInstance.getVersion().toString() : "";
    }

    /// Opens an aggregate view of unresolved dependencies and active conflicts.
    public void showRelationSummary() {
        if (relationIndex == null || !relationIndex.isComplete()) {
            Controllers.dialog(i18n("mods.dependency_analysis_failed"), i18n("addon.relations"),
                    MessageDialogPane.MessageType.WARNING);
            return;
        }
        Controllers.dialog(new RelationSummaryDialog(relationIndex, this::exportRelations));
    }

    /// Exports top-level and recursively nested mod metadata with resolved dependency states.
    public void exportRelations() {
        @Nullable ModRelationIndex currentIndex = relationIndex;
        if (currentIndex == null || !currentIndex.isComplete()) {
            Controllers.dialog(i18n("mods.dependency_analysis_failed"), i18n("button.export"),
                    MessageDialogPane.MessageType.WARNING);
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle(i18n("button.export"));
        chooser.setInitialFileName("hmcl-mod-relations.csv");
        chooser.getExtensionFilters().setAll(new FileChooser.ExtensionFilter("CSV", "*.csv"));
        @Nullable Path output = Controllers.showSaveDialog(chooser);
        if (output == null) {
            return;
        }

        List<LocalModFile> mods = getItems().stream().map(ModInfoObject::getModInfo).toList();
        Controllers.taskDialog(Task.runAsync(() -> {
            CSVTable table = createRelationExport(mods, currentIndex);
            table.write(output);
            FXUtils.showFileInExplorer(output);
        }).whenComplete(Schedulers.javafx(), exception -> {
            if (exception == null) {
                Controllers.dialog(output.toString(), i18n("message.success"));
            } else {
                LOG.warning("Failed to export mod relation information", exception);
                Controllers.dialog("", i18n("message.error"), MessageDialogPane.MessageType.ERROR);
            }
        }), i18n("button.export"), TaskCancellationAction.NORMAL);
    }

    /// Builds a CSV containing top-level files and every nested node.
    private static CSVTable createRelationExport(List<LocalModFile> mods, ModRelationIndex index) {
        CSVTable table = new CSVTable();
        List<String> headers = List.of(
                "Host File", "Nested Path", "Depth", "Mod ID", "Name", "Version", "Loader",
                "Selected", "Dependencies", "Conflicts");
        for (int column = 0; column < headers.size(); column++) {
            table.set(column, 0, headers.get(column));
        }

        int row = 1;
        for (LocalModFile mod : mods) {
            table.set(0, row, FileUtils.getName(mod.getFile()));
            table.set(2, row, "0");
            table.set(3, row, Objects.toString(mod.getId(), ""));
            table.set(4, row, Objects.toString(mod.getName(), ""));
            table.set(5, row, Objects.toString(mod.getVersion(), ""));
            table.set(6, row, mod.getModLoaderType().name());
            table.set(7, row, Boolean.toString(mod.isActive()));
            table.set(8, row, formatDependencies(mod.getDependencies(), index));
            table.set(9, row, formatConflicts(mod.getConflicts()));
            row++;
            row = appendNestedExportRows(table, row, mod, index.getBundledTree(mod), "", 1, index);
        }
        return table;
    }

    /// Appends one nested tree to the relation export and returns the next free row.
    private static int appendNestedExportRows(
            CSVTable table,
            int firstRow,
            LocalModFile host,
            List<NestedJarInspector.NestedJar> nodes,
            String parentPath,
            int depth,
            ModRelationIndex index) {
        int row = firstRow;
        for (NestedJarInspector.NestedJar node : nodes) {
            String nestedPath = parentPath.isEmpty() ? node.path() : parentPath + "!/" + node.path();
            table.set(0, row, FileUtils.getName(host.getFile()));
            table.set(1, row, nestedPath);
            table.set(2, row, Integer.toString(depth));
            table.set(3, row, Objects.toString(node.id(), ""));
            table.set(4, row, node.displayName());
            table.set(5, row, Objects.toString(node.version(), ""));
            table.set(6, row, node.loaderType().name());
            table.set(7, row, Boolean.toString(index.isNestedSelected(host, node)));
            table.set(8, row, formatDependencies(node.dependencies(), index));
            table.set(9, row, formatConflicts(node.conflicts()));
            row++;
            row = appendNestedExportRows(table, row, host, node.children(), nestedPath, depth + 1, index);
        }
        return row;
    }

    /// Formats dependency declarations together with their current resolution states.
    private static String formatDependencies(List<ModDependency> dependencies, ModRelationIndex index) {
        return dependencies.stream()
                .map(dependency -> dependency.id() + " " + dependency.versionConstraint()
                        + " [" + index.resolve(dependency).status().name() + "]")
                .collect(Collectors.joining("; "));
    }

    /// Formats conflict declarations for a stable machine-readable export cell.
    private static String formatConflicts(List<ModConflict> conflicts) {
        return conflicts.stream()
                .map(conflict -> conflict.id() + " " + conflict.versionConstraint()
                        + " [" + (conflict.hard() ? "HARD" : "SOFT") + "]")
                .collect(Collectors.joining("; "));
    }

    public void add() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(i18n("mods.add.title"));
        chooser.getExtensionFilters().setAll(new FileChooser.ExtensionFilter(i18n("extension.mod"), "*.jar", "*.zip", "*.litemod"));
        List<Path> res = Controllers.showOpenMultipleDialog(chooser);

        if (res == null) return;

        // It's guaranteed that succeeded and failed are thread safe here.
        List<String> succeeded = new ArrayList<>(res.size());
        List<String> failed = new ArrayList<>();

        Task.runAsync(() -> {
            for (Path file : res) {
                try {
                    modManager.addMod(file);
                    succeeded.add(FileUtils.getName(file));
                } catch (Exception e) {
                    LOG.warning("Unable to add mod " + file, e);
                    failed.add(FileUtils.getName(file));

                    // Actually addMod will not throw exceptions because FileChooser has already filtered files.
                }
            }
        }).withRunAsync(Schedulers.javafx(), () -> {
            List<String> prompt = new ArrayList<>(1);
            if (!succeeded.isEmpty())
                prompt.add(i18n("mods.add.success", String.join(", ", succeeded)));
            if (!failed.isEmpty())
                prompt.add(i18n("mods.add.failed", String.join(", ", failed)));
            Controllers.dialog(String.join("\n", prompt), i18n("mods.add"));
            loadMods(modManager);
        }).start();
    }

    void removeSelected(ObservableList<ModInfoObject> selectedItems) {
        try {
            modManager.removeMods(selectedItems.stream()
                    .filter(Objects::nonNull)
                    .map(ModInfoObject::getModInfo)
                    .toArray(LocalModFile[]::new));
            loadMods(modManager);
        } catch (IOException ignore) {
            // Fail to remove mods if the game is running or the mod is absent.
        }
    }

    void enableSelected(ObservableList<ModInfoObject> selectedItems) {
        selectedItems.stream()
                .filter(Objects::nonNull)
                .map(ModInfoObject::getModInfo)
                .forEach(info -> info.setActive(true));
    }

    void disableSelected(ObservableList<ModInfoObject> selectedItems) {
        selectedItems.stream()
                .filter(Objects::nonNull)
                .map(ModInfoObject::getModInfo)
                .forEach(info -> info.setActive(false));
    }

    public void openModFolder() {
        if (gameInstance != null) {
            FXUtils.openFolder(gameInstance.getModsDirectory());
        }
    }

    public void checkUpdates(Collection<LocalModFile> mods) {
        Objects.requireNonNull(mods);
        if (isLoading() || gameInstance == null) {
            return;
        }

        HMCLGameInstance gameInstance = this.gameInstance;
        Runnable action = () -> Controllers.taskDialog(Task
                        .composeAsync(() -> {
                            GameVersionNumber version = gameInstance.getVersion();
                            return version != GameVersionNumber.unknown()
                                    ? new AddonCheckUpdatesTask<>(
                                            DownloadProviders.getDownloadProvider(), version.toString(), mods)
                                    : null;
                        })
                        .whenComplete(Schedulers.javafx(), (result, exception) -> {
                            if (exception instanceof CancellationException) return;
                            if (exception != null || result == null) {
                                Controllers.dialog(i18n("addon.check_update.failed_check"), i18n("message.failed"), MessageDialogPane.MessageType.ERROR);
                            } else if (result.isEmpty()) {
                                Controllers.dialog(i18n("addon.check_update.empty"));
                            } else {
                                Controllers.navigateForward(new AddonUpdatesPage<>(modManager, result));
                            }
                        })
                        .withStagesHints("update.checking"),
                i18n("addon.check_update"), TaskCancellationAction.NORMAL);

        if (gameInstance.isModpack()) {
            Controllers.confirm(
                    i18n("mods.update_modpack_mod.warning"), null,
                    MessageDialogPane.MessageType.WARNING,
                    action, null);
        } else {
            action.run();
        }
    }

    public void download() {
        if (gameInstance == null) {
            return;
        }
        Controllers.getDownloadPage().showModDownloads().selectInstance(gameInstance.getId());
        Controllers.navigate(Controllers.getDownloadPage());
    }

    public void rollback(LocalModFile from, LocalModFile to) {
        try {
            modManager.rollback(from, to);
            refresh();
        } catch (IOException ex) {
            Controllers.showToast(i18n("message.failed"));
        }
    }

    public GameDirectory getGameDirectory() {
        return gameInstance != null ? gameInstance.getRepository().getGameDirectory() : null;
    }

    public HMCLGameRepository getRepository() {
        return gameInstance != null ? gameInstance.getRepository() : null;
    }

    public GameInstanceID getInstanceId() {
        return gameInstance != null ? gameInstance.getId() : null;
    }

    /// Finds active mods that transitively lose their last required provider with the targets gone.
    private List<ModInfoObject> findActiveDependents(Collection<LocalModFile> targets) {
        if (relationIndex == null) {
            return List.of();
        }
        Map<LocalModFile, ModInfoObject> byFile = new HashMap<>();
        for (ModInfoObject item : getItems()) {
            byFile.put(item.getModInfo(), item);
        }
        return relationIndex.findActiveDependents(targets, byFile.keySet()).stream()
                .map(byFile::get)
                .filter(Objects::nonNull)
                .toList();
    }

    /// Describes one action offered after reverse dependencies are detected.
    @NotNullByDefault
    private record CascadeOption(String label, Runnable action) {
        /// Returns the label shown by the choice control.
        @Override
        public String toString() {
            return label;
        }
    }

    /// Searchable aggregate view of unresolved dependencies and active conflicts.
    @NotNullByDefault
    private static final class RelationSummaryDialog extends JFXDialogLayout {
        /// Creates a summary backed by the already-published immutable relation index.
        ///
        /// @param index relation data to display
        /// @param exportAction action that opens the CSV export flow
        private RelationSummaryDialog(ModRelationIndex index, Runnable exportAction) {
            setHeading(new Label(i18n("addon.relations")));

            JFXTextField search = new JFXTextField();
            search.setPromptText(i18n("search"));
            VBox rows = new VBox(8);
            Runnable refresh = () -> populateRows(rows, index, search.getText());
            search.textProperty().addListener(ignored -> refresh.run());
            refresh.run();

            ScrollPane scrollPane = new ScrollPane(rows);
            scrollPane.setFitToWidth(true);
            scrollPane.setMaxHeight(420);
            scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            FXUtils.smoothScrolling(scrollPane);
            setBody(new VBox(12, search, scrollPane));

            JFXButton close = new JFXButton(i18n("button.ok"));
            close.getStyleClass().add("dialog-accept");
            close.setOnAction(event -> fireEvent(new DialogCloseEvent()));
            JFXButton export = new JFXButton(i18n("button.export"));
            export.setOnAction(event -> exportAction.run());
            setActions(export, close);
        }

        /// Rebuilds the visible aggregate rows for the current query.
        private static void populateRows(VBox rows, ModRelationIndex index, @Nullable String query) {
            rows.getChildren().clear();
            String normalizedQuery = Objects.toString(query, "").strip().toLowerCase(Locale.ROOT);

            for (ModRelationIndex.DependencyIssue issue : index.getDependencyIssues()) {
                String line = formatDependencyIssue(issue);
                if (matchesQuery(line, normalizedQuery)) {
                    rows.getChildren().add(createSummaryRow(SVG.EXTENSION, line));
                }
            }
            for (ModRelationIndex.ActiveConflict conflict : index.getActiveConflicts()) {
                String line = formatActiveConflict(conflict);
                if (matchesQuery(line, normalizedQuery)) {
                    rows.getChildren().add(createSummaryRow(SVG.WARNING, line));
                }
            }
            if (rows.getChildren().isEmpty()) {
                Label empty = new Label(i18n(normalizedQuery.isEmpty()
                        ? "mods.relations.no_issues"
                        : "search.no_results_found"));
                empty.setWrapText(true);
                rows.getChildren().add(empty);
            }
        }

        /// Creates one icon-and-text summary row.
        private static Node createSummaryRow(SVG icon, String text) {
            Label label = new Label(text);
            label.setWrapText(true);
            HBox.setHgrow(label, Priority.ALWAYS);
            HBox row = new HBox(8, icon.createIcon(16), label);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(6));
            return row;
        }

        /// Formats one unresolved required dependency with its host, source, and known providers.
        private static String formatDependencyIssue(ModRelationIndex.DependencyIssue issue) {
            ModDependency dependency = issue.resolution().dependency();
            String source = displayName(issue.declaringHost());
            if (issue.nestedSource() != null) {
                source += " / " + issue.nestedSource().displayName();
            }
            String status = switch (issue.resolution().status()) {
                case DISABLED -> i18n("addon.dependencies.disabled");
                case MISSING -> i18n("addon.dependencies.missing");
                case VERSION_MISMATCH -> i18n(
                        "addon.dependencies.incompatible_version", dependency.versionConstraint());
                case UNKNOWN_VERSION -> i18n("addon.dependencies.unknown_version");
                case SATISFIED -> i18n("addon.dependencies.installed");
            };
            String providers = issue.resolution().providers().stream()
                    .map(provider -> displayName(provider.host())
                            + (provider.version().isBlank() ? "" : " " + provider.version()))
                    .distinct()
                    .collect(Collectors.joining(", "));
            return source + " → " + dependency.id()
                    + (dependency.versionConstraint().isBlank() ? "" : " " + dependency.versionConstraint())
                    + " — " + status + (providers.isBlank() ? "" : " (" + providers + ")");
        }

        /// Formats one active hard or soft conflict and the providers that trigger it.
        private static String formatActiveConflict(ModRelationIndex.ActiveConflict conflict) {
            String source = displayName(conflict.declaringHost());
            if (conflict.nestedSource() != null) {
                source += " / " + conflict.nestedSource().displayName();
            }
            String providers = conflict.providers().stream()
                    .map(provider -> displayName(provider.host()))
                    .distinct()
                    .collect(Collectors.joining(", "));
            return i18n(conflict.conflict().hard()
                    ? "addon.conflicts.active_hard"
                    : "addon.conflicts.active_soft")
                    + ": " + source + " ↔ " + conflict.conflict().id()
                    + (providers.isBlank() ? "" : " (" + providers + ")");
        }

        /// Returns whether a formatted row contains the case-insensitive query.
        private static boolean matchesQuery(String text, String normalizedQuery) {
            return normalizedQuery.isEmpty() || text.toLowerCase(Locale.ROOT).contains(normalizedQuery);
        }

        /// Returns a stable human-readable name for a top-level mod file.
        private static String displayName(LocalModFile mod) {
            return StringUtils.isNotBlank(mod.getName())
                    ? mod.getName()
                    : StringUtils.isNotBlank(mod.getId())
                    ? mod.getId()
                    : FileUtils.getName(mod.getFile());
        }
    }

    /// Prompts for the cascade policy when an operation would break dependent mods.
    @NotNullByDefault
    private static final class DependencyWarningDialog extends JFXDialogLayout {
        /// Creates a dependency warning dialog.
        private DependencyWarningDialog(List<ModInfoObject> dependents, String confirmText,
                                        List<CascadeOption> options) {
            setHeading(new Label(i18n("addon.dependencies.warning.title")));

            Label message = new Label(i18n("addon.dependencies.warning"));
            message.setWrapText(true);

            ComponentList list = new ComponentList();
            list.getStyleClass().add("no-padding");
            for (ModInfoObject dependent : dependents) {
                HBox row = new HBox(8);
                row.setAlignment(Pos.CENTER_LEFT);
                row.setPadding(new Insets(8));
                row.setMouseTransparent(true);

                ImageContainer icon = new ImageContainer(32);
                dependent.iconCache.attachValue(icon.imageProperty(), null);

                TwoLineListItem content = new TwoLineListItem();
                HBox.setHgrow(content, Priority.ALWAYS);
                content.setTitle(dependent.getModTranslations() != null && I18n.isUseChinese()
                        ? dependent.getModTranslations().getDisplayName()
                        : dependent.getModInfo().getName());
                StringJoiner subtitle = new StringJoiner(" | ");
                if (StringUtils.isNotBlank(dependent.getModInfo().getId())) {
                    subtitle.add(dependent.getModInfo().getId());
                }
                subtitle.add(FileUtils.getName(dependent.getModInfo().getFile()));
                content.setSubtitle(subtitle.toString());

                row.getChildren().setAll(icon, content);
                list.getContent().add(row);
            }

            ScrollPane scrollPane = new ScrollPane(list);
            scrollPane.setFitToWidth(true);
            scrollPane.setMaxHeight(300);
            scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            FXUtils.smoothScrolling(scrollPane);
            setBody(new VBox(10, message, scrollPane));

            JFXComboBox<CascadeOption> cascade = new JFXComboBox<>();
            cascade.getItems().setAll(options);
            cascade.getSelectionModel().selectFirst();
            HBox cascadeBox = new HBox(
                    8, new Label(i18n("addon.dependencies.warning.cascade.label")), cascade);
            cascadeBox.setAlignment(Pos.CENTER_LEFT);

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            JFXButton cancelButton = new JFXButton(i18n("button.cancel"));
            cancelButton.setOnAction(event -> fireEvent(new DialogCloseEvent()));
            JFXButton confirmButton = new JFXButton(confirmText);
            confirmButton.getStyleClass().add("dialog-accept");
            confirmButton.setOnAction(event -> {
                fireEvent(new DialogCloseEvent());
                @Nullable CascadeOption selected = cascade.getValue();
                if (selected != null) {
                    selected.action().run();
                }
            });
            getActions().setAll(cascadeBox, spacer, cancelButton, confirmButton);
            onEscPressed(this, cancelButton::fire);
        }
    }

    @NotNullByDefault
    private static final class ModListPageSkin extends SkinBase<ModListPage> {

        private final TransitionPane toolbarPane;
        private final HBox searchBar;
        private final HBox toolbarNormal;
        private final HBox toolbarSelecting;

        private final JFXListView<ModListPage.ModInfoObject> listView;

        /// Whether the search mechanism is currently active.
        private final BooleanProperty isSearching = new SimpleBooleanProperty(false);

        private final JFXTextField searchField;

        /// Timer for debouncing search input to avoid executing search on every keystroke.
        private final PauseTransition searchPause = new PauseTransition(Duration.millis(100));

        /// Debounces relation rebuilds when one cascade toggles several files in quick succession.
        private final PauseTransition activeRefreshPause = new PauseTransition(Duration.millis(100));

        /// Schedules a fresh immutable analysis after any local file enable-state mutation.
        private final InvalidationListener activeChangeListener = ignored -> activeRefreshPause.playFromStart();

        public ModListPageSkin(ModListPage skinnable) {
            super(skinnable);
            activeRefreshPause.setOnFinished(ignored -> skinnable.refresh());

            StackPane pane = new StackPane();
            pane.setPadding(new Insets(10));
            pane.getStyleClass().addAll("notice-pane");

            ComponentList root = new ComponentList();
            pane.getChildren().setAll(root);
            root.getStyleClass().add("no-padding");
            listView = new JFXListView<>();
            listView.getStyleClass().add("no-horizontal-scrollbar");
            skinnable.bundledScanGenerationProperty().addListener(ignored -> listView.refresh());

            {
                toolbarPane = new TransitionPane();

                searchBar = new HBox();
                toolbarNormal = new HBox();
                toolbarSelecting = new HBox();

                // Search Bar
                searchBar.setAlignment(Pos.CENTER);
                searchBar.setPadding(new Insets(0, 5, 0, 5));
                searchField = new JFXTextField();
                searchField.setPromptText(i18n("search"));
                HBox.setHgrow(searchField, Priority.ALWAYS);
                searchPause.setOnFinished(e -> search());
                searchField.textProperty().addListener((observable, oldValue, newValue) -> {
                    if (isSearching.get() || !StringUtils.isBlank(newValue)) {
                        searchPause.setRate(1);
                        searchPause.playFromStart();
                    }
                });

                JFXButton closeSearchBar = createToolbarButton2(null, SVG.CLOSE,
                        () -> {
                            changeToolbar(toolbarNormal);

                            searchField.clear();
                            searchPause.stop();

                            isSearching.set(false);
                            Bindings.bindContent(listView.getItems(), getSkinnable().getItems());
                        });

                onEscPressed(searchField, closeSearchBar::fire);

                searchBar.getChildren().setAll(searchField, closeSearchBar);

                // Toolbar Normal
                JFXButton relationSummary = createToolbarButton2(
                        i18n("addon.relations"), SVG.STACKS, skinnable::showRelationSummary);
                relationSummary.disableProperty().bind(skinnable.bundledScanReadyProperty().not());
                toolbarNormal.getChildren().setAll(
                        createToolbarButton2(i18n("button.refresh"), SVG.REFRESH, skinnable::refresh),
                        createToolbarButton2(i18n("mods.add"), SVG.ADD, skinnable::add),
                        createToolbarButton2(i18n("button.reveal_dir"), SVG.FOLDER_OPEN, skinnable::openModFolder),
                        relationSummary,
                        createToolbarButton2(i18n("addon.check_update.button"), SVG.UPDATE, () ->
                                skinnable.checkUpdates(
                                        listView.getItems().stream()
                                                .map(ModListPage.ModInfoObject::getModInfo)
                                                .toList()
                                )
                        ),
                        createToolbarButton2(i18n("mods.download"), SVG.DOWNLOAD, skinnable::download),
                        createToolbarButton2(i18n("search"), SVG.SEARCH, () -> changeToolbar(searchBar))
                );

                // Toolbar Selecting

                // reason for not using selectAll() is that selectAll() first clears all selected then selects all, causing the toolbar to flicker
                var selectAll = createToolbarButton2(i18n("button.select_all"), SVG.SELECT_ALL, () -> listView.getSelectionModel().selectRange(0, listView.getItems().size()));

                ListChangeListener<Object> listener = change -> {
                    selectAll.setDisable(!listView.getItems().isEmpty()
                            && listView.getSelectionModel().getSelectedItems().size() == listView.getItems().size());
                };

                listView.getSelectionModel().getSelectedItems().addListener(listener);
                listView.getItems().addListener(listener);

                JFXButton btnRemove = createToolbarButton2(i18n("button.remove"), SVG.DELETE_FOREVER, () -> {
                    var selected = listView.getSelectionModel().getSelectedItems();
                    List<LocalModFile> targets = new ArrayList<>();
                    for (ModInfoObject item : selected)
                        if (item != null) targets.add(item.getModInfo());
                    List<ModInfoObject> dependents = skinnable.findActiveDependents(targets);
                    if (dependents.isEmpty()) {
                        Controllers.confirm(i18n("button.remove.confirm"), i18n("button.remove"),
                                () -> skinnable.removeSelected(selected), null);
                    } else {
                        List<ModInfoObject> selectedSnapshot = new ArrayList<>(selected);
                        Controllers.dialog(new DependencyWarningDialog(dependents, i18n("button.remove"), List.of(
                                new CascadeOption(i18n("addon.dependencies.warning.cascade.none"),
                                        () -> skinnable.removeSelected(FXCollections.observableArrayList(selectedSnapshot))),
                                new CascadeOption(i18n("addon.dependencies.warning.cascade"), () -> {
                                    for (ModInfoObject dependent : dependents)
                                        dependent.getModInfo().setActive(false);
                                    skinnable.removeSelected(FXCollections.observableArrayList(selectedSnapshot));
                                }),
                                new CascadeOption(i18n("addon.dependencies.warning.cascade.delete"), () -> {
                                    List<ModInfoObject> all = new ArrayList<>(selectedSnapshot);
                                    all.addAll(dependents);
                                    skinnable.removeSelected(FXCollections.observableArrayList(all));
                                })
                        )));
                    }
                });
                JFXButton btnDisable = createToolbarButton2(i18n("mods.disable"), SVG.CLOSE, () -> {
                    var selected = listView.getSelectionModel().getSelectedItems();
                    List<LocalModFile> targets = new ArrayList<>();
                    for (ModInfoObject item : selected)
                        if (item != null) targets.add(item.getModInfo());
                    List<ModInfoObject> dependents = skinnable.findActiveDependents(targets);
                    if (dependents.isEmpty()) {
                        skinnable.disableSelected(selected);
                    } else {
                        Controllers.dialog(new DependencyWarningDialog(dependents, i18n("mods.disable"), List.of(
                                new CascadeOption(i18n("addon.dependencies.warning.cascade.none"),
                                        () -> skinnable.disableSelected(selected)),
                                new CascadeOption(i18n("addon.dependencies.warning.cascade"), () -> {
                                    skinnable.disableSelected(selected);
                                    for (ModInfoObject dependent : dependents)
                                        dependent.getModInfo().setActive(false);
                                })
                        )));
                    }
                });
                JFXButton btnEnable = createToolbarButton2(i18n("mods.enable"), SVG.CHECK, () ->
                        skinnable.enableSelected(listView.getSelectionModel().getSelectedItems()));
                btnRemove.disableProperty().bind(getSkinnable().bundledScanReadyProperty().not());
                btnDisable.disableProperty().bind(getSkinnable().bundledScanReadyProperty().not());
                btnEnable.disableProperty().bind(getSkinnable().bundledScanReadyProperty().not());

                toolbarSelecting.getChildren().setAll(
                        btnRemove,
                        btnEnable,
                        btnDisable,
                        createToolbarButton2(i18n("addon.check_update.button"), SVG.UPDATE, () ->
                                skinnable.checkUpdates(
                                        listView.getSelectionModel().getSelectedItems().stream()
                                                    .map(ModListPage.ModInfoObject::getModInfo)
                                                    .toList()
                                    )
                            ),
                            selectAll,
                            createToolbarButton2(i18n("button.cancel"), SVG.CANCEL, () ->
                                    listView.getSelectionModel().clearSelection())
                );

                FXUtils.onChangeAndOperate(listView.getSelectionModel().selectedItemProperty(),
                        selectedItem -> {
                            if (selectedItem == null)
                                changeToolbar(isSearching.get() ? searchBar : toolbarNormal);
                            else
                                changeToolbar(toolbarSelecting);
                        });

                FXUtils.setOverflowHidden(toolbarPane, 8);

                root.getContent().add(toolbarPane);

                // Clear selection when pressing ESC
                root.addEventHandler(KeyEvent.KEY_PRESSED, e -> {
                    if (e.getCode() == KeyCode.ESCAPE) {
                        if (listView.getSelectionModel().getSelectedItem() != null) {
                            listView.getSelectionModel().clearSelection();
                            e.consume();
                        }
                    }
                });
            }

            {
                SpinnerPane center = new SpinnerPane();
                ComponentList.setVgrow(center, Priority.ALWAYS);
                center.loadingProperty().bind(skinnable.loadingProperty());

                listView.setCellFactory(x -> new ModListPage.ModInfoListCell(listView, skinnable));
                listView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

                StackPane placeholderContainer = new StackPane();
                placeholderContainer.getStyleClass().add("notice-pane");
                Label placeholderLabel = new Label(i18n("mods.empty"));
                placeholderLabel.textProperty().bind(
                    Bindings.createStringBinding(() -> {
                        if (isSearching.get()) {
                            return i18n("search.no_results_found");
                        } else {
                            return i18n("mods.empty");
                        }
                    },
                    isSearching)
                );
                placeholderContainer.getChildren().add(placeholderLabel);
                listView.setPlaceholder(placeholderContainer);

                Bindings.bindContent(listView.getItems(), skinnable.getItems());
                skinnable.getItems().addListener((ListChangeListener<? super ModListPage.ModInfoObject>) c -> {
                    while (c.next()) {
                        for (ModInfoObject removed : c.getRemoved()) {
                            removed.active.removeListener(activeChangeListener);
                        }
                        for (ModInfoObject added : c.getAddedSubList()) {
                            added.active.addListener(activeChangeListener);
                        }
                    }
                    if (isSearching.get()) {
                        search();
                    }
                });
                skinnable.getItems().forEach(item -> item.active.addListener(activeChangeListener));

                listView.setOnContextMenuRequested(event -> {
                    ModListPage.ModInfoObject selectedItem = listView.getSelectionModel().getSelectedItem();
                    if (selectedItem != null && listView.getSelectionModel().getSelectedItems().size() == 1) {
                        listView.getSelectionModel().clearSelection();
                        Controllers.dialog(new ModListPage.ModInfoDialog(selectedItem));
                    }
                });

                // ListViewBehavior would consume ESC pressed event, preventing us from handling it
                // So we ignore it here
                ignoreEvent(listView, KeyEvent.KEY_PRESSED, e -> e.getCode() == KeyCode.ESCAPE);

                center.setContent(listView);

                JFXSpinner scanHintSpinner = new JFXSpinner();
                scanHintSpinner.setRadius(7);
                Label scanHintLabel = new Label();
                scanHintLabel.textProperty().bind(Bindings.createStringBinding(
                        () -> i18n(skinnable.bundledScanFailedProperty().get()
                                ? "mods.dependency_analysis_failed"
                                : "mods.dependency_analyzing"),
                        skinnable.bundledScanFailedProperty()));
                scanHintSpinner.visibleProperty().bind(skinnable.bundledScanFailedProperty().not());
                scanHintSpinner.managedProperty().bind(skinnable.bundledScanFailedProperty().not());
                JFXButton retryScan = new JFXButton(i18n("button.refresh"));
                retryScan.setOnAction(event -> skinnable.refresh());
                retryScan.visibleProperty().bind(skinnable.bundledScanFailedProperty());
                retryScan.managedProperty().bind(skinnable.bundledScanFailedProperty());
                HBox scanHint = new HBox(8, scanHintSpinner, scanHintLabel, retryScan);
                scanHint.getStyleClass().add("mod-scan-hint");
                scanHint.setAlignment(Pos.CENTER_LEFT);
                BooleanProperty showScanHint = new SimpleBooleanProperty(false);
                scanHint.visibleProperty().bind(showScanHint);
                scanHint.managedProperty().bind(showScanHint);
                PauseTransition scanHintDelay = new PauseTransition(Duration.millis(400));
                scanHintDelay.setOnFinished(event -> {
                    if (!skinnable.bundledScanReadyProperty().get()) {
                        showScanHint.set(true);
                    }
                });
                FXUtils.onChangeAndOperate(skinnable.bundledScanReadyProperty(), ready -> {
                    if (ready) {
                        scanHintDelay.stop();
                        showScanHint.set(false);
                    } else {
                        showScanHint.set(false);
                        scanHintDelay.playFromStart();
                    }
                });
                FXUtils.onChangeAndOperate(skinnable.bundledScanFailedProperty(), failed -> {
                    if (failed) {
                        scanHintDelay.stop();
                        showScanHint.set(true);
                    }
                });

                root.getContent().add(scanHint);
                root.getContent().add(center);
            }

            getChildren().setAll(pane);
        }

        private void changeToolbar(HBox newToolbar) {
            Node oldToolbar = toolbarPane.getCurrentNode();
            if (newToolbar != oldToolbar) {
                toolbarPane.setContent(newToolbar, ContainerAnimations.FADE);
                if (newToolbar == searchBar) {
                    Platform.runLater(searchField::requestFocus);
                }
            }
        }

        private void search() {
            isSearching.set(true);

            Bindings.unbindContent(listView.getItems(), getSkinnable().getItems());

            String queryString = searchField.getText();
            if (StringUtils.isBlank(queryString)) {
                listView.getItems().setAll(getSkinnable().getItems());
            } else {
                listView.getItems().clear();

                Predicate<@Nullable String> predicate;
                try {
                    predicate = StringUtils.compileQuery(queryString);
                } catch (Throwable e) {
                    LOG.warning("Illegal regular expression", e);
                    return;
                }

                // Do we need to search in the background thread?
                for (ModListPage.ModInfoObject item : getSkinnable().getItems()) {
                    LocalModFile modInfo = item.getModInfo();
                    if (predicate.test(modInfo.getFileName())
                            || predicate.test(modInfo.getName())
                            || predicate.test(modInfo.getVersion())
                            || predicate.test(modInfo.getGameVersion())
                            || predicate.test(modInfo.getId())
                            || predicate.test(Objects.toString(modInfo.getModLoaderType()))
                            || predicate.test((item.getModTranslations() != null ? item.getModTranslations().getDisplayName() : null))
                            || modInfo.getBundledMods().stream().anyMatch(predicate)
                            || matchesNested(modInfo.getBundledTree(), predicate)
                            || modInfo.getDependencies().stream().map(ModDependency::id).anyMatch(predicate)
                            || modInfo.getProvidedIds().stream().anyMatch(predicate)
                            || modInfo.getConflicts().stream().map(ModConflict::id).anyMatch(predicate)) {
                        listView.getItems().add(item);
                    }
                }
            }
        }

        /// Searches nested names, paths, IDs, aliases, dependencies, and conflicts recursively.
        private static boolean matchesNested(
                List<NestedJarInspector.NestedJar> nodes,
                Predicate<@Nullable String> predicate) {
            for (NestedJarInspector.NestedJar node : nodes) {
                if (predicate.test(node.path())
                        || predicate.test(node.fileName())
                        || predicate.test(node.id())
                        || predicate.test(node.name())
                        || node.providedVersions().keySet().stream().anyMatch(predicate)
                        || node.dependencies().stream().map(ModDependency::id).anyMatch(predicate)
                        || node.conflicts().stream().map(ModConflict::id).anyMatch(predicate)
                        || matchesNested(node.children(), predicate)) {
                    return true;
                }
            }
            return false;
        }

    }

    public static final class ModInfoObject {
        private final BooleanProperty active;
        /// Whether this row's nested JIJ and dependency details are expanded.
        private final BooleanProperty expanded = new SimpleBooleanProperty(this, "expanded", false);
        private final LocalModFile localModFile;
        private final @Nullable ModTranslations.Mod modTranslations;

        private final ItemPropertyAsyncCache<Image, ModInfoObject> iconCache;

        ModInfoObject(LocalModFile localModFile) {
            this.localModFile = localModFile;
            this.active = localModFile.activeProperty();

            this.modTranslations = ModTranslations.MOD.getMod(localModFile.getId(), localModFile.getName());

            this.iconCache = new ItemPropertyAsyncCache.Soft<>(this, this::loadIcon, this::getDefaultIcon);
        }

        public LocalModFile getModInfo() {
            return localModFile;
        }

        /// Returns the expansion state retained across list-cell recycling.
        private BooleanProperty expandedProperty() {
            return expanded;
        }

        public @Nullable ModTranslations.Mod getModTranslations() {
            return modTranslations;
        }

        private Image getDefaultIcon() {
            return GameInstanceIconType.getIconType(this.localModFile.getModLoaderType()).getIcon();
        }

        private Image loadIcon() {
            List<String> iconPaths = new ArrayList<>();

            if (StringUtils.isNotBlank(this.localModFile.getLogoPath())) {
                iconPaths.add(this.localModFile.getLogoPath());
            }

            try (FileSystem fs = CompressingUtils.createReadOnlyZipFileSystem(this.localModFile.getFile())) {
                for (String path : iconPaths) {
                    Path iconPath = fs.getPath(path);
                    if (Files.exists(iconPath)) {
                        Image image = FXUtils.loadImage(iconPath, 80, 80, true, true);
                        if (!image.isError() && image.getWidth() > 0 && image.getHeight() > 0 &&
                                Math.abs(image.getWidth() - image.getHeight()) < 1) {
                            return image;
                        }
                    }
                }
            } catch (Exception e) {
                LOG.warning("Failed to load mod icons", e);
            }

            return getDefaultIcon();
        }
    }

    private static final class ModInfoDialog extends JFXDialogLayout {

        ModInfoDialog(ModInfoObject modInfo) {
            HBox titleContainer = new HBox();
            titleContainer.setSpacing(8);
            titleContainer.setPadding(new Insets(0, 0, 12, 0));

            DoubleBinding widthBinding = Controllers.getDecorator().contentWidthProperty().multiply(0.7);
            prefWidthProperty().bind(widthBinding);
            maxWidthProperty().bind(widthBinding);

            var imageContainer = new ImageContainer(40);
            titleContainer.setAlignment(Pos.CENTER_LEFT);
            modInfo.iconCache.attachValue(imageContainer.imageProperty(), null);

            TwoLineListItem title = new TwoLineListItem();
            title.getTitleLabel().setWrapText(true);
            if (modInfo.getModTranslations() != null && I18n.isUseChinese())
                title.setTitle(modInfo.getModTranslations().getDisplayName());
            else
                title.setTitle(modInfo.getModInfo().getName());

            StringJoiner subtitle = new StringJoiner("\n");
            subtitle.add(i18n("archive.file.name") + ": " + FileUtils.getName(modInfo.getModInfo().getFile()));
            if (StringUtils.isNotBlank(modInfo.getModInfo().getGameVersion())) {
                subtitle.add(i18n("mods.game.version") + ": " + modInfo.getModInfo().getGameVersion());
            }
            if (StringUtils.isNotBlank(modInfo.getModInfo().getVersion())) {
                subtitle.add(i18n("archive.version") + ": " + modInfo.getModInfo().getVersion());
            }
            if (StringUtils.isNotBlank(modInfo.getModInfo().getAuthors())) {
                subtitle.add(i18n("archive.author") + ": " + modInfo.getModInfo().getAuthors());
            }
            title.setSubtitle(subtitle.toString());

            titleContainer.getChildren().setAll(imageContainer, title);
            setHeading(titleContainer);

            Label description = new Label(modInfo.getModInfo().getDescription().toString());
            description.setWrapText(true);
            FXUtils.copyOnDoubleClick(description);

            ScrollPane descriptionPane = new ScrollPane(description);
            FXUtils.smoothScrolling(descriptionPane);
            descriptionPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            descriptionPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
            descriptionPane.setFitToWidth(true);
            description.heightProperty().addListener((obs, oldVal, newVal) -> {
                double maxHeight = Controllers.getDecorator().contentHeightProperty().get() * 0.5;
                double targetHeight = Math.min(newVal.doubleValue(), maxHeight);
                descriptionPane.setPrefViewportHeight(targetHeight);
            });

            setBody(descriptionPane);

            if (StringUtils.isNotBlank(modInfo.getModInfo().getId())) {
                for (Pair<String, ? extends RemoteAddonRepository> item : Arrays.asList(
                        pair("addon.curseforge", CurseForgeRemoteAddonRepository.getInstance()),
                        pair("addon.modrinth", ModrinthRemoteAddonRepository.getInstance())
                )) {
                    RemoteAddonRepository repository = item.getValue();
                    JFXHyperlink button = new JFXHyperlink(i18n(item.getKey()));
                    Task.runAsync(() -> {
                        Optional<RemoteAddon.Version> versionOptional = repository.getRemoteVersionByLocalFile(modInfo.getModInfo().getFile());
                        if (versionOptional.isPresent()) {
                            RemoteAddon remoteAddon = repository.getAddonById(DownloadProviders.getDownloadProvider(), versionOptional.get().projectId());
                            FXUtils.runInFX(() -> {
                                Set<String> tags = new LinkedHashSet<>();
                                for (AddonLoader loader : versionOptional.get().loaders()) {
                                    String tag = I18n.translateLoaderName(loader);
                                    if (tag != null) tags.add(tag);
                                }
                                title.addTagsIfNotExist(tags);

                                button.setExternalLink(remoteAddon.pageUrl());
                                button.setDisable(false);
                            });
                        }
                    }).start();
                    button.setDisable(true);
                    getActions().add(button);
                }
            }

            if (StringUtils.isNotBlank(modInfo.getModInfo().getUrl())) {
                JFXHyperlink officialPageButton = new JFXHyperlink(i18n("mods.url"));
                officialPageButton.setExternalLink(modInfo.getModInfo().getUrl());
                getActions().add(officialPageButton);
            }

            if (modInfo.getModTranslations() == null || StringUtils.isBlank(modInfo.getModTranslations().getMcmod())) {
                JFXHyperlink searchButton = new JFXHyperlink(i18n("mods.mcmod.search"));
                searchButton.setExternalLink(NetworkUtils.withQuery("https://search.mcmod.cn/s", mapOf(
                        pair("key", modInfo.getModInfo().getName()),
                        pair("site", "all"),
                        pair("filter", "0")
                )));
                getActions().add(searchButton);
            } else {
                JFXHyperlink mcmodButton = new JFXHyperlink(i18n("mods.mcmod.page"));
                mcmodButton.setExternalLink(ModTranslations.MOD.getMcmodUrl(modInfo.getModTranslations()));
                getActions().add(mcmodButton);
            }

            JFXButton okButton = new JFXButton();
            okButton.getStyleClass().add("dialog-accept");
            okButton.setText(i18n("button.ok"));
            okButton.setOnAction(e -> fireEvent(new DialogCloseEvent()));
            getActions().add(okButton);

            onEscPressed(this, okButton::fire);
        }
    }

    private static final Lazy<PopupMenu> menu = new Lazy<>(PopupMenu::new);
    private static final Lazy<JFXPopup> popup = new Lazy<>(() -> new JFXPopup(menu.get()));

    private static final class ModInfoListCell extends MDListCell<ModInfoObject> {
        private static final PseudoClass WARNING = PseudoClass.getPseudoClass("warning");

        private final ModListPage page;

        private final JFXCheckBox checkBox = new JFXCheckBox();
        private final ImageContainer imageContainer = new ImageContainer(32);
        private final TwoLineListItem content = new TwoLineListItem();
        private final JFXButton restoreButton = FXUtils.newToggleButton4(SVG.RESTORE);
        private final JFXButton infoButton = FXUtils.newToggleButton4(SVG.INFO);
        private final JFXButton revealButton = FXUtils.newToggleButton4(SVG.FOLDER);
        private final JFXButton expandButton = FXUtils.newToggleButton4(SVG.KEYBOARD_ARROW_DOWN);
        private final VBox nestedBox = new VBox();
        private final Rectangle nestedClip = new Rectangle();
        private @Nullable Timeline nestedAnimation;
        private final List<Runnable> nestedListenerCleanups = new ArrayList<>();
        private boolean suppressExpandAnimation;
        private final BooleanProperty expanded = new SimpleBooleanProperty(this, "expanded", false);
        private @Nullable BooleanProperty expandedBinding;

        private @Nullable BooleanProperty booleanProperty;

        private Tooltip warningTooltip;

        ModInfoListCell(JFXListView<ModInfoObject> listView, ModListPage page) {
            super(listView);
            this.page = page;

            this.getStyleClass().add("mod-info-list-cell");

            HBox header = new HBox(8);
            header.setPickOnBounds(false);
            header.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(content, Priority.ALWAYS);
            content.setMinWidth(0);
            content.setMouseTransparent(true);
            setSelectable();

            imageContainer.setImage(GameInstanceIconType.COMMAND.getIcon());

            FXUtils.installFastTooltip(restoreButton, i18n("mods.restore"));
            FXUtils.installFastTooltip(expandButton, i18n("addon.relations"));
            restoreButton.managedProperty().bind(restoreButton.visibleProperty());

            expandButton.getGraphic().setRotate(0);
            expandButton.addEventHandler(MouseEvent.MOUSE_PRESSED, event -> {
                expanded.set(!expanded.get());
                event.consume();
            });
            page.bundledScanGenerationProperty().addListener((InvalidationListener) observable -> {
                if (expanded.get() && getItem() != null) {
                    rebuildNested(getItem());
                }
            });

            checkBox.setOnAction(event -> guardDisableToggle());
            checkBox.disableProperty().bind(page.bundledScanReadyProperty().not());

            nestedBox.getStyleClass().add("mod-nested-list");
            nestedBox.setVisible(false);
            nestedBox.setManaged(false);
            nestedClip.widthProperty().bind(nestedBox.widthProperty());
            nestedClip.heightProperty().bind(nestedBox.heightProperty());
            nestedBox.setClip(nestedClip);
            expanded.addListener((observable, oldValue, newValue) -> {
                if (!suppressExpandAnimation) {
                    animateExpansion(newValue);
                }
            });

            header.getChildren().setAll(
                    checkBox, imageContainer, content, expandButton, restoreButton, revealButton, infoButton);

            VBox container = new VBox(header, nestedBox);
            container.setPickOnBounds(false);
            StackPane.setMargin(container, new Insets(8));
            getContainer().getChildren().setAll(container);
        }

        /// Reverts a dependency-breaking toggle until the user chooses a cascade policy.
        private void guardDisableToggle() {
            ModInfoObject item = getItem();
            if (item == null || checkBox.isSelected())
                return;
            LocalModFile target = item.getModInfo();
            List<ModInfoObject> dependents = page.findActiveDependents(List.of(target));
            if (dependents.isEmpty())
                return;
            target.setActive(true);
            Controllers.dialog(new DependencyWarningDialog(dependents, i18n("mods.disable"), List.of(
                    new CascadeOption(i18n("addon.dependencies.warning.cascade.none"),
                            () -> target.setActive(false)),
                    new CascadeOption(i18n("addon.dependencies.warning.cascade"), () -> {
                        target.setActive(false);
                        for (ModInfoObject dependent : dependents)
                            dependent.getModInfo().setActive(false);
                    })
            )));
        }

        /// Creates a fallback row for an unparsed nested entry path.
        private Node createNestedRow(String path, int indent) {
            String name = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;

            Label label = new Label(name);
            HBox.setHgrow(label, Priority.ALWAYS);
            if (!name.equals(path))
                label.setTooltip(new Tooltip(path));

            HBox row = new HBox(8, SVG.STACKS.createIcon(16), label);
            row.getStyleClass().add("mod-nested-item");
            row.setAlignment(Pos.CENTER_LEFT);
            if (indent > 0)
                row.getChildren().add(0, indentSpacer(indent));
            return row;
        }

        /// Renders one tree level while grouping alternate versions of the same nested mod.
        private void appendBundledNodes(List<NestedJarInspector.NestedJar> nodes, int indent, String instanceMc) {
            LinkedHashMap<String, List<NestedJarInspector.NestedJar>> groups = new LinkedHashMap<>();
            List<NestedJarInspector.NestedJar> ungrouped = new ArrayList<>();
            for (NestedJarInspector.NestedJar node : nodes) {
                if (node.id() != null && !node.id().isBlank())
                    groups.computeIfAbsent(node.id(), k -> new ArrayList<>()).add(node);
                else
                    ungrouped.add(node);
            }

            for (List<NestedJarInspector.NestedJar> versions : groups.values()) {
                if (versions.size() == 1) {
                    NestedJarInspector.NestedJar node = versions.get(0);
                    nestedBox.getChildren().add(createBundledRow(node, indent, null, null, MatchKind.NONE));
                    appendNestedRelations(node, indent + 1);
                    if (node.hasChildren())
                        appendBundledNodes(node.children(), indent + 1, instanceMc);
                } else {
                    @Nullable ModInfoObject currentItem = getItem();
                    NestedJarInspector.NestedJar selected = currentItem == null || page.relationIndex == null
                            ? null
                            : versions.stream()
                            .filter(candidate -> page.relationIndex.isNestedSelected(
                                    currentItem.getModInfo(), candidate))
                            .findFirst().orElse(null);
                    NestedJarInspector.NestedJar exact = versions.stream()
                            .filter(v -> MinecraftVersionMatcher.matchesExact(v, instanceMc)).findFirst().orElse(null);
                    NestedJarInspector.NestedJar ranged = exact != null ? null : versions.stream()
                            .filter(v -> satisfiesRange(v, instanceMc)).findFirst().orElse(null);
                    NestedJarInspector.NestedJar rep = selected != null
                            ? selected
                            : exact != null ? exact : ranged != null ? ranged : versions.get(0);
                    MatchKind kind = MinecraftVersionMatcher.matchesExact(rep, instanceMc) ? MatchKind.EXACT
                            : satisfiesRange(rep, instanceMc) ? MatchKind.RANGE
                            : !rep.minecraftConstraintRequired() ? MatchKind.NONE
                            : instanceMc != null && !instanceMc.isBlank() ? MatchKind.INCOMPATIBLE
                            : MatchKind.NONE;
                    String tooltip = versions.stream()
                            .map(v -> v.version() != null && !v.version().isBlank() ? v.version() : v.fileName())
                            .collect(Collectors.joining("\n"));
                    nestedBox.getChildren().add(createBundledRow(rep, indent,
                            i18n("addon.bundled.versions", versions.size()), tooltip, kind));
                    appendNestedRelations(rep, indent + 1);
                    if (rep.hasChildren())
                        appendBundledNodes(rep.children(), indent + 1, instanceMc);
                }
            }

            for (NestedJarInspector.NestedJar node : ungrouped) {
                nestedBox.getChildren().add(createBundledRow(node, indent, null, null, MatchKind.NONE));
                appendNestedRelations(node, indent + 1);
                if (node.hasChildren())
                    appendBundledNodes(node.children(), indent + 1, instanceMc);
            }
        }

        /// Creates one parsed nested-mod row.
        private Node createBundledRow(NestedJarInspector.NestedJar node, int indent, String versionsBadge, String badgeTooltip, MatchKind match) {
            HBox row = new HBox(8, SVG.STACKS.createIcon(16));
            row.getStyleClass().add("mod-nested-item");
            row.setAlignment(Pos.CENTER_LEFT);
            if (indent > 0)
                row.getChildren().add(0, indentSpacer(indent));

            Label name = new Label(node.displayName());
            row.getChildren().add(name);

            if (node.version() != null && !node.version().isBlank()) {
                Label version = new Label(node.version());
                version.getStyleClass().add("mod-nested-version");
                row.getChildren().add(version);
            }
            if (versionsBadge != null) {
                Label badge = new Label(versionsBadge);
                badge.getStyleClass().add("mod-nested-badge");
                if (badgeTooltip != null && !badgeTooltip.isBlank())
                    badge.setTooltip(new Tooltip(badgeTooltip));
                row.getChildren().add(badge);
            }
            if (match == MatchKind.EXACT || match == MatchKind.RANGE) {
                Label current = new Label(i18n("addon.bundled.current"));
                current.getStyleClass().add("mod-nested-current");
                row.getChildren().add(current);
            }
            if (match == MatchKind.RANGE) {
                Label range = new Label(i18n("addon.bundled.range"));
                range.getStyleClass().add("mod-nested-range");
                if (node.minecraftVersion() != null && !node.minecraftVersion().isBlank())
                    range.setTooltip(new Tooltip(node.minecraftVersion()));
                row.getChildren().add(range);
            }
            if (match == MatchKind.INCOMPATIBLE) {
                Label incompatible = new Label(i18n("addon.bundled.incompatible"));
                incompatible.getStyleClass().add("mod-nested-incompatible");
                row.getChildren().add(incompatible);
            }
            return row;
        }

        /// Appends dependency and conflict rows declared directly by one nested mod node.
        private void appendNestedRelations(NestedJarInspector.NestedJar node, int indent) {
            for (Map.Entry<String, String> provided : node.providedVersions().entrySet()) {
                Node row = createProvidedRow(provided.getKey(), provided.getValue());
                VBox.setMargin(row, new Insets(0, 0, 0, indent * 16));
                nestedBox.getChildren().add(row);
            }
            for (ModDependency dependency : node.dependencies()) {
                Node row = createDependencyRow(dependency);
                VBox.setMargin(row, new Insets(0, 0, 0, indent * 16));
                nestedBox.getChildren().add(row);
            }
            @Nullable ModInfoObject item = getItem();
            if (item == null) {
                return;
            }
            for (ModConflict conflict : node.conflicts()) {
                Node row = createConflictRow(conflict);
                VBox.setMargin(row, new Insets(0, 0, 0, indent * 16));
                nestedBox.getChildren().add(row);
            }
        }

        /// Compatibility tag for a grouped nested-mod version.
        private enum MatchKind { NONE, INCOMPATIBLE, RANGE, EXACT }

        /// Returns whether the nested mod's declared Minecraft range accepts the instance.
        private static boolean satisfiesRange(NestedJarInspector.NestedJar node, String instanceMc) {
            return node.minecraftConstraintRequired()
                    && instanceMc != null && !instanceMc.isBlank()
                    && MinecraftVersionMatcher.satisfies(node.loaderType(), node.minecraftVersion(), instanceMc);
        }

        /// Creates fixed-width indentation without changing row padding.
        private static Region indentSpacer(int indent) {
            Region spacer = new Region();
            double width = indent * 16;
            spacer.setMinWidth(width);
            spacer.setPrefWidth(width);
            spacer.setMaxWidth(width);
            return spacer;
        }

        /// Creates a nested-details section heading.
        private Node createSectionLabel(String text) {
            Label label = new Label(text);
            label.getStyleClass().add("mod-nested-section");
            return label;
        }

        /// Creates a dependency row backed by the immutable relation index.
        private Node createDependencyRow(ModDependency dependency) {
            Label name = new Label(dependency.id());
            Label constraint = new Label(dependency.versionConstraint());
            constraint.getStyleClass().add("mod-nested-version");

            Label status = new Label();
            Runnable refresh = () -> {
                @Nullable ModRelationIndex index = page.relationIndex;
                if (index == null) {
                    status.setText("(" + i18n("addon.dependencies.unknown") + ")");
                    return;
                }
                ModRelationIndex.Resolution resolution = index.resolve(dependency);
                String statusText = switch (resolution.status()) {
                    case SATISFIED -> resolution.providers().stream()
                            .anyMatch(provider -> provider.nestedSource() != null)
                            ? i18n("addon.dependencies.bundled")
                            : i18n("addon.dependencies.installed");
                    case DISABLED -> i18n("addon.dependencies.disabled");
                    case MISSING -> i18n("addon.dependencies.missing");
                    case VERSION_MISMATCH -> i18n(
                            "addon.dependencies.incompatible_version", dependency.versionConstraint());
                    case UNKNOWN_VERSION -> i18n("addon.dependencies.unknown_version");
                };
                status.setText("(" + statusText + ")");
                String versions = resolution.providers().stream()
                        .map(ModRelationIndex.Provider::version)
                        .filter(version -> !version.isBlank())
                        .distinct()
                        .collect(Collectors.joining(", "));
                status.setTooltip(versions.isBlank() ? null : new Tooltip(versions));
            };
            refresh.run();

            @Nullable ModRelationIndex index = page.relationIndex;
            if (index != null) {
                Set<LocalModFile> observedHosts = new HashSet<>();
                for (ModRelationIndex.Provider provider : index.getProviders(dependency.id())) {
                    if (observedHosts.add(provider.host())) {
                        InvalidationListener listener = ignored -> refresh.run();
                        provider.host().activeProperty().addListener(listener);
                        nestedListenerCleanups.add(() -> provider.host().activeProperty().removeListener(listener));
                    }
                }
            }

            HBox row = new HBox(8, SVG.EXTENSION.createIcon(16), name);
            if (!dependency.versionConstraint().isBlank() && !"*".equals(dependency.versionConstraint())) {
                row.getChildren().add(constraint);
            }
            row.getChildren().add(status);
            row.getStyleClass().add("mod-nested-item");
            row.setAlignment(Pos.CENTER_LEFT);
            return row;
        }

        /// Creates a row describing an additional capability exposed by the current file or node.
        private Node createProvidedRow(String id, String version) {
            Label name = new Label(id);
            HBox row = new HBox(8, SVG.CHECK.createIcon(16), name);
            if (!version.isBlank()) {
                Label providedVersion = new Label(version);
                providedVersion.getStyleClass().add("mod-nested-version");
                row.getChildren().add(providedVersion);
            }
            row.getStyleClass().add("mod-nested-item");
            row.setAlignment(Pos.CENTER_LEFT);
            return row;
        }

        /// Creates a conflict row and marks whether the current provider graph activates it.
        private Node createConflictRow(ModConflict conflict) {
            Label name = new Label(conflict.id());
            Label constraint = new Label(conflict.versionConstraint());
            constraint.getStyleClass().add("mod-nested-version");
            Label status = new Label();
            Runnable refresh = () -> {
                boolean active = page.relationIndex != null
                        && page.relationIndex.getProviders(conflict.id()).stream()
                        .filter(ModRelationIndex.Provider::active)
                        .anyMatch(provider -> "*".equals(conflict.versionConstraint())
                                || !provider.version().isBlank() && conflict.matches(provider.version()));
                status.setText(i18n(active
                        ? conflict.hard() ? "addon.conflicts.active_hard" : "addon.conflicts.active_soft"
                        : "addon.conflicts.inactive"));
            };
            refresh.run();
            if (page.relationIndex != null) {
                Set<LocalModFile> observedHosts = new HashSet<>();
                for (ModRelationIndex.Provider provider : page.relationIndex.getProviders(conflict.id())) {
                    if (observedHosts.add(provider.host())) {
                        InvalidationListener listener = ignored -> refresh.run();
                        provider.host().activeProperty().addListener(listener);
                        nestedListenerCleanups.add(() -> provider.host().activeProperty().removeListener(listener));
                    }
                }
            }
            HBox row = new HBox(8, SVG.WARNING.createIcon(16), name);
            if (!conflict.versionConstraint().isBlank() && !"*".equals(conflict.versionConstraint())) {
                row.getChildren().add(constraint);
            }
            row.getChildren().add(status);
            row.getStyleClass().add("mod-nested-item");
            row.setAlignment(Pos.CENTER_LEFT);
            return row;
        }

        /// Clears nested content and detaches provider listeners from recycled cells.
        private void clearNested() {
            for (Runnable cleanup : nestedListenerCleanups)
                cleanup.run();
            nestedListenerCleanups.clear();
            nestedBox.getChildren().clear();
        }

        private void rebuildNested(ModInfoObject item) {
            clearNested();
            if (item == null)
                return;
            LocalModFile modInfo = item.getModInfo();
            if (modInfo.hasBundledMods()) {
                nestedBox.getChildren().add(createSectionLabel(i18n("addon.bundled")));
                List<NestedJarInspector.NestedJar> tree = page.relationIndex != null
                        ? page.relationIndex.getBundledTree(modInfo)
                        : List.of();
                if (tree.isEmpty()) {
                    for (String path : modInfo.getBundledMods())
                        nestedBox.getChildren().add(createNestedRow(path, 0));
                } else {
                    appendBundledNodes(tree, 0, page.getGameVersion());
                }
            }
            if (modInfo.hasDependencies()) {
                nestedBox.getChildren().add(createSectionLabel(i18n("addon.dependencies")));
                for (ModDependency dependency : modInfo.getDependencies()) {
                    nestedBox.getChildren().add(createDependencyRow(dependency));
                }
            }
            if (!modInfo.getProvidedVersions().isEmpty()) {
                nestedBox.getChildren().add(createSectionLabel(i18n("addon.provides")));
                modInfo.getProvidedVersions().forEach((id, version) ->
                        nestedBox.getChildren().add(createProvidedRow(id, version)));
            }
            if (!modInfo.getConflicts().isEmpty()) {
                nestedBox.getChildren().add(createSectionLabel(i18n("addon.conflicts")));
                for (ModConflict conflict : modInfo.getConflicts()) {
                    nestedBox.getChildren().add(createConflictRow(conflict));
                }
            }
        }

        /// Applies expansion state immediately when a cell is recycled.
        private void snapExpansion(boolean expand) {
            if (nestedAnimation != null) {
                nestedAnimation.stop();
                nestedAnimation = null;
            }
            nestedBox.setMinHeight(Region.USE_COMPUTED_SIZE);
            nestedBox.setMaxHeight(Region.USE_COMPUTED_SIZE);
            expandButton.getGraphic().setRotate(expand ? 180 : 0);
            if (expand) {
                rebuildNested(getItem());
                nestedBox.setManaged(true);
                nestedBox.setVisible(true);
            } else {
                nestedBox.setManaged(false);
                nestedBox.setVisible(false);
                clearNested();
            }
        }

        /// Animates a user-triggered expansion change.
        private void animateExpansion(boolean expand) {
            if (nestedAnimation != null) {
                nestedAnimation.stop();
                nestedAnimation = null;
            }
            if (!AnimationUtils.isAnimationEnabled()) {
                snapExpansion(expand);
                return;
            }
            if (expand) {
                rebuildNested(getItem());
                nestedBox.setManaged(true);
                nestedBox.setVisible(true);
                nestedBox.applyCss();
                nestedBox.layout();
                double target = nestedBox.prefHeight(-1);
                nestedBox.setMinHeight(0);
                nestedBox.setMaxHeight(0);
                Timeline timeline = new Timeline(new KeyFrame(Duration.millis(200),
                        new KeyValue(nestedBox.minHeightProperty(), target, Motion.EASE_IN_OUT_CUBIC),
                        new KeyValue(nestedBox.maxHeightProperty(), target, Motion.EASE_IN_OUT_CUBIC),
                        new KeyValue(expandButton.getGraphic().rotateProperty(), 180, Motion.EASE_IN_OUT_CUBIC)));
                timeline.setOnFinished(e -> {
                    nestedBox.setMinHeight(Region.USE_COMPUTED_SIZE);
                    nestedBox.setMaxHeight(Region.USE_COMPUTED_SIZE);
                    nestedAnimation = null;
                });
                nestedAnimation = timeline;
                timeline.play();
            } else {
                double current = nestedBox.getHeight();
                nestedBox.setMinHeight(current);
                nestedBox.setMaxHeight(current);
                Timeline timeline = new Timeline(new KeyFrame(Duration.millis(200),
                        new KeyValue(nestedBox.minHeightProperty(), 0, Motion.EASE_IN_OUT_CUBIC),
                        new KeyValue(nestedBox.maxHeightProperty(), 0, Motion.EASE_IN_OUT_CUBIC),
                        new KeyValue(expandButton.getGraphic().rotateProperty(), 0, Motion.EASE_IN_OUT_CUBIC)));
                timeline.setOnFinished(e -> {
                    nestedBox.setManaged(false);
                    nestedBox.setVisible(false);
                    clearNested();
                    nestedBox.setMinHeight(Region.USE_COMPUTED_SIZE);
                    nestedBox.setMaxHeight(Region.USE_COMPUTED_SIZE);
                    nestedAnimation = null;
                });
                nestedAnimation = timeline;
                timeline.play();
            }
        }

        @Override
        protected void updateControl(ModInfoObject dataItem, boolean empty) {
            pseudoClassStateChanged(WARNING, false);
            if (warningTooltip != null) {
                Tooltip.uninstall(this, warningTooltip);
                warningTooltip = null;
            }

            if (empty) {
                suppressExpandAnimation = true;
                if (expandedBinding != null) {
                    expanded.unbindBidirectional(expandedBinding);
                    expandedBinding = null;
                }
                expanded.set(false);
                suppressExpandAnimation = false;
                snapExpansion(false);
                content.getTags().clear();
                return;
            }

            List<String> warning = new ArrayList<>();

            content.getTags().clear();

            LocalModFile modInfo = dataItem.getModInfo();
            ModTranslations.Mod modTranslations = dataItem.getModTranslations();

            ModLoaderType modLoaderType = modInfo.getModLoaderType();

            dataItem.iconCache.attachValue(imageContainer.imageProperty(), new WeakReference<>(this.itemProperty()));

            String displayName = modInfo.getName();
            if (modTranslations != null && I18n.isUseChinese()) {
                String chineseName = modTranslations.getName();
                if (StringUtils.containsChinese(chineseName)) {
                    if (StringUtils.containsEmoji(chineseName)) {
                        StringBuilder builder = new StringBuilder();

                        chineseName.codePoints().forEach(ch -> {
                            if (ch < 0x1F300 || ch > 0x1FAFF)
                                builder.appendCodePoint(ch);
                        });

                        chineseName = builder.toString().trim();
                    }

                    if (StringUtils.isNotBlank(chineseName) && !displayName.equalsIgnoreCase(chineseName)) {
                        displayName = displayName + " (" + chineseName + ")";
                    }
                }
            }
            content.setTitle(displayName);

            StringJoiner joiner = new StringJoiner(" | ");
            if (modLoaderType != ModLoaderType.UNKNOWN && StringUtils.isNotBlank(modInfo.getId()))
                joiner.add(modInfo.getId());

            joiner.add(FileUtils.getName(modInfo.getFile()));

            content.setSubtitle(joiner.toString());

            if (modLoaderType == ModLoaderType.UNKNOWN) {
                content.addTagWarning(i18n("mods.unknown"));
            } else if (!page.supportedLoaders.contains(modLoaderType)) {
                warning.add(i18n("mods.warning.loader_mismatch"));
                content.addTagWarning(I18n.translateLoaderType(dataItem.getModInfo().getModLoaderType()));
            }

            String modVersion = modInfo.getVersion();
            if (StringUtils.isNotBlank(modVersion) && !"${version}".equals(modVersion)) {
                content.addTag(modVersion);
            }

            if (booleanProperty != null) {
                checkBox.selectedProperty().unbindBidirectional(booleanProperty);
            }
            checkBox.selectedProperty().bindBidirectional(booleanProperty = dataItem.active);
            restoreButton.setVisible(!modInfo.getMod().getOldFiles().isEmpty());
            restoreButton.setOnAction(e -> {
                menu.get().getContent().setAll(modInfo.getMod().getOldFiles().stream()
                        .map(localModFile -> new IconedMenuItem(null, localModFile.getVersion(),
                                () -> page.rollback(modInfo, localModFile),
                                popup.get()))
                        .toList()
                );

                popup.get().show(restoreButton, JFXPopup.PopupVPosition.TOP, JFXPopup.PopupHPosition.RIGHT, 0, restoreButton.getHeight());
            });
            revealButton.setOnAction(e -> FXUtils.showFileInExplorer(modInfo.getFile()));
            infoButton.setOnAction(e -> Controllers.dialog(new ModInfoDialog(dataItem)));

            suppressExpandAnimation = true;
            if (expandedBinding != null) {
                expanded.unbindBidirectional(expandedBinding);
            }
            expanded.bindBidirectional(expandedBinding = dataItem.expandedProperty());
            suppressExpandAnimation = false;

            boolean expandable = modInfo.hasBundledMods() || modInfo.hasDependencies();
            expandButton.setVisible(expandable);
            expandButton.setManaged(expandable);
            if (modInfo.hasBundledMods()) {
                int bundledCount = page.relationIndex != null
                        ? page.relationIndex.getBundledTree(modInfo).size()
                        : modInfo.getBundledMods().size();
                content.addTag(i18n("addon.bundled") + ": " + bundledCount);
            }
            snapExpansion(dataItem.expandedProperty().get());

            if (!warning.isEmpty()) {
                pseudoClassStateChanged(WARNING, true);

                //noinspection ConstantValue
                this.warningTooltip = warning.size() == 1
                        ? new Tooltip(warning.get(0))
                        : new Tooltip(String.join("\n", warning));
                FXUtils.installFastTooltip(this, warningTooltip);
            }
        }
    }
}
