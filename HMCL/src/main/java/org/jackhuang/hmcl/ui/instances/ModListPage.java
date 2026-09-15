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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.jfoenix.controls.*;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.DoubleBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ObservableValue;
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
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.Duration;
import org.jackhuang.hmcl.addon.AddonLoader;
import org.jackhuang.hmcl.addon.RemoteAddon;
import org.jackhuang.hmcl.addon.RemoteAddonRepository;
import org.jackhuang.hmcl.addon.mod.LocalModFile;
import org.jackhuang.hmcl.addon.mod.ModLoaderType;
import org.jackhuang.hmcl.addon.mod.ModManager;
import org.jackhuang.hmcl.addon.repository.CurseForgeRemoteAddonRepository;
import org.jackhuang.hmcl.addon.repository.ModrinthRemoteAddonRepository;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.game.*;
import org.jackhuang.hmcl.setting.DownloadProviders;
import org.jackhuang.hmcl.setting.GameDirectory;
import org.jackhuang.hmcl.setting.GameInstanceIconType;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.*;
import org.jackhuang.hmcl.ui.animation.ContainerAnimations;
import org.jackhuang.hmcl.ui.animation.TransitionPane;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.util.*;
import org.jackhuang.hmcl.util.i18n.I18n;
import org.jackhuang.hmcl.util.io.CSVTable;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.io.NetworkUtils;
import org.jackhuang.hmcl.util.javafx.ItemPropertyAsyncCache;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;

import static org.jackhuang.hmcl.ui.FXUtils.ignoreEvent;
import static org.jackhuang.hmcl.ui.FXUtils.onEscPressed;
import static org.jackhuang.hmcl.ui.ToolbarListPageSkin.createToolbarButton2;
import static org.jackhuang.hmcl.util.Lang.mapOf;
import static org.jackhuang.hmcl.util.Pair.pair;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Displays installed mods and supports managing and exporting the selected entries.
public final class ModListPage extends ListPageBase<ModListPage.ModInfoObject> implements PageAware {
    private final ReentrantLock lock = new ReentrantLock();
    private final WeakListenerHolder listenerHolder = new WeakListenerHolder();

    private ModManager modManager;
    private @Nullable HMCLGameInstance gameInstance;
    private String gameVersion;

    final EnumSet<ModLoaderType> supportedLoaders = EnumSet.noneOf(ModLoaderType.class);

    /// Stores remote links for a local mod, using empty strings for unavailable links.
    private static final class RemoteModInfo {
        /// The CurseForge project page URL, or an empty string.
        final String curseForgeUrl;
        /// The CurseForge file URL, or an empty string.
        final String curseForgeFileUrl;
        /// The CurseForge download page URL, or an empty string.
        final String curseForgeDownloadPage;
        /// The Modrinth project page URL, or an empty string.
        final String modrinthUrl;
        /// The Modrinth file URL, or an empty string.
        final String modrinthFileUrl;
        /// Whether an I/O failure interrupted either remote lookup.
        final boolean hasNetworkError;

        /// Creates a lookup result containing the available links and failure status.
        RemoteModInfo(String curseForgeUrl, String curseForgeFileUrl, String curseForgeDownloadPage, String modrinthUrl, String modrinthFileUrl, boolean hasNetworkError) {
            this.curseForgeUrl = curseForgeUrl;
            this.curseForgeFileUrl = curseForgeFileUrl;
            this.curseForgeDownloadPage = curseForgeDownloadPage;
            this.modrinthUrl = modrinthUrl;
            this.modrinthFileUrl = modrinthFileUrl;
            this.hasNetworkError = hasNetworkError;
        }
    }

    /// Remote lookup results shared by export tasks and their retries.
    private final Map<Path, RemoteModInfo> remoteModInfoCache = new ConcurrentHashMap<>();
    /// Successfully computed SHA-1 digests for the current export.
    private final Map<Path, String> sha1Cache = new ConcurrentHashMap<>();
    /// Successfully computed SHA-512 digests for the current export.
    private final Map<Path, String> sha512Cache = new ConcurrentHashMap<>();

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

        this.gameVersion = gameInstance.getVersion().toString();

        loadMods(gameInstance.getModManager());
    }

    private void loadMods(ModManager modManager) {
        setLoading(true);

        if (this.modManager != modManager) {
            getItems().clear();
        }
        this.modManager = modManager;
        CompletableFuture.supplyAsync(() -> {
            lock.lock();
            try {
                modManager.refresh();
                return modManager.getLocalFiles().stream().map(ModInfoObject::new).toList();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } finally {
                lock.unlock();
            }
        }, Schedulers.io()).whenCompleteAsync((list, exception) -> {
            if (this.modManager != modManager) {
                return;
            }

            updateSupportedLoaders(modManager);

            if (exception == null) {
                getItems().setAll(list);
            } else {
                LOG.warning("Failed to load mods", exception);
                getItems().clear();
            }
            setLoading(false);
        }, Schedulers.javafx());
    }

    private void updateSupportedLoaders(ModManager modManager) {
        supportedLoaders.clear();

        GameComponentAnalyzer analyzer = modManager.getComponentAnalyzer();
        if (analyzer == null) {
            Collections.addAll(supportedLoaders, ModLoaderType.values());
            return;
        }

        for (GameComponentType type : GameComponentType.MOD_LOADERS) {
            if (type.isModLoader() && analyzer.has(type)) {
                ModLoaderType modLoaderType = type.getModLoaderType();
                if (modLoaderType != null) {
                    supportedLoaders.add(modLoaderType);

                    if (modLoaderType == ModLoaderType.CLEANROOM)
                        supportedLoaders.add(ModLoaderType.FORGE);
                }
            }
        }

        if (analyzer.has(GameComponentType.NEO_FORGE) && "1.20.1".equals(gameVersion)) {
            supportedLoaders.add(ModLoaderType.FORGE);
        }

        if (analyzer.has(GameComponentType.QUILT)) {
            supportedLoaders.add(ModLoaderType.FABRIC);
        }

        if (analyzer.has(GameComponentType.LEGACY_FABRIC)) {
            supportedLoaders.add(ModLoaderType.FABRIC);
        }

        if (analyzer.has(GameComponentType.FABRIC) && modManager.hasMod("kilt", ModLoaderType.FABRIC)) {
            supportedLoaders.add(ModLoaderType.FORGE);
            supportedLoaders.add(ModLoaderType.NEO_FORGE);
        }

        // Sinytra Connector
        if (analyzer.has(GameComponentType.NEO_FORGE) && (modManager.hasMod("connector", ModLoaderType.NEO_FORGE) || modManager.hasMod("connectormod", ModLoaderType.NEO_FORGE))
                || "1.20.1".equals(gameVersion) && analyzer.has(GameComponentType.FORGE) && modManager.hasMod("connectormod", ModLoaderType.FORGE)) {
            supportedLoaders.add(ModLoaderType.FABRIC);
        }
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

    /// Prompts for a destination and exports the selected mods in a background task.
    /// Does nothing if no game instance is available or the file chooser is cancelled.
    /// The selected entries and fields are copied before the task starts; the entries
    /// themselves remain shared. Remote lookup failures offer a retry after the file is written.
    ///
    /// @param selectedMods the selected entries in export order
    /// @param format the export format: "csv", "json", or "custom"
    /// @param fields the field names in export order for CSV and JSON
    /// @param customTemplate the template for custom text; must be non-null for "custom"
    ///                       and may be null for CSV and JSON
    public void exportMods(List<ModInfoObject> selectedMods, String format, Set<String> fields, @Nullable String customTemplate) {
        @Nullable HMCLGameInstance instance = gameInstance;
        if (instance == null) {
            return;
        }

        remoteModInfoCache.clear();
        sha1Cache.clear();
        sha512Cache.clear();

        FileChooser chooser = new FileChooser();
        chooser.setTitle(i18n("mods.export.title"));
        String extension;
        if (format.equals("csv")) {
            extension = ".csv";
        } else if (format.equals("json")) {
            extension = ".json";
        } else {
            extension = ".txt";
        }
        FileChooser.ExtensionFilter filter = format.equals("csv")
                ? new FileChooser.ExtensionFilter(i18n("mods.export.format.csv"), "*" + extension)
                : format.equals("json")
                        ? new FileChooser.ExtensionFilter(i18n("mods.export.format.json"), "*" + extension)
                        : new FileChooser.ExtensionFilter(i18n("mods.export.format.txt"), "*" + extension);
        chooser.getExtensionFilters().setAll(filter);
        chooser.setInitialFileName(instance.getId() + "_mods" + extension);
        @Nullable Path targetPath = FileUtils.toPath(chooser.showSaveDialog(Controllers.getStage()));
        if (targetPath == null) return;

        final List<ModInfoObject> modsSnapshot = new ArrayList<>(selectedMods);
        final Path outputPath = targetPath;
        final String exportFormat = format;
        final @Nullable String template = customTemplate;

        final Set<String> fieldsSnapshot = new LinkedHashSet<>();
        if (format.equals("custom") && customTemplate != null) {
            int i = 0;
            while (i < customTemplate.length()) {
                if (customTemplate.charAt(i) == '{') {
                    int end = customTemplate.indexOf('}', i);
                    if (end != -1) {
                        fieldsSnapshot.add(customTemplate.substring(i + 1, end));
                        i = end + 1;
                    } else {
                        i++;
                    }
                } else {
                    i++;
                }
            }
        } else {
            fieldsSnapshot.addAll(fields);
        }

        exportModsWithRetry(modsSnapshot, fieldsSnapshot, exportFormat, template, outputPath);
    }

    /// Starts an export and offers retries when remote metadata lookup fails.
    private void exportModsWithRetry(List<ModInfoObject> mods, Set<String> fields, String format, @Nullable String template, Path outputPath) {
        exportModsWithRetry(mods, fields, format, template, outputPath, null);
    }

    /// Starts an export, restricting metadata retries to `failedPaths` when supplied.
    private void exportModsWithRetry(List<ModInfoObject> mods, Set<String> fields, String format, @Nullable String template, Path outputPath, @Nullable Set<Path> failedPaths) {
        ExportTask task = new ExportTask(mods, fields, format, template, outputPath);
        if (failedPaths != null) {
            task.failedModPaths.addAll(failedPaths);
        }

        Controllers.taskDialog(
                task
                        .whenComplete(Schedulers.javafx(), (networkErrorCount, exception) -> {
                            if (exception != null) {
                                LOG.warning("Failed to export mods", exception);
                                String errorMessage = StringUtils.isBlank(exception.getMessage()) ? exception.toString() : exception.getMessage();
                                Controllers.dialog(errorMessage, i18n("message.error"), MessageDialogPane.MessageType.ERROR);
                                return;
                            }

                            if (networkErrorCount != null && networkErrorCount > 0) {
                                Controllers.confirm(
                                        i18n("mods.export.network_error"),
                                        i18n("mods.export.title"),
                                        MessageDialogPane.MessageType.WARNING,
                                        () -> {
                                            task.getFailedModPaths().forEach(remoteModInfoCache::remove);
                                            exportModsWithRetry(mods, fields, format, template, outputPath, task.getFailedModPaths());
                                        },
                                        () -> {
                                            Controllers.dialog(i18n("mods.export.success"), i18n("mods.export.title"));
                                        }
                                );
                            } else {
                                Controllers.dialog(i18n("mods.export.success"), i18n("mods.export.title"));
                            }
                        }),
                i18n("mods.export.title"), TaskCancellationAction.NORMAL);
    }

    /// Custom Task class for exporting mods with progress updates.
    private class ExportTask extends Task<Integer> {
        /// The selected mod entries copied when the export was requested.
        private final List<ModInfoObject> mods;
        /// The requested fields in export order.
        private final Set<String> fields;
        /// The output format: `csv`, `json`, or `custom`.
        private final String format;
        /// The custom text template, or `null` for CSV and JSON.
        private final @Nullable String template;
        /// The file to create or overwrite with exported data.
        private final Path targetPath;
        /// The mutable set of paths requiring remote metadata retries.
        private final Set<Path> failedModPaths = ConcurrentHashMap.newKeySet();
        /// The number of mods whose remote lookups failed in this attempt.
        private final AtomicInteger networkErrorCount = new AtomicInteger(0);

        /// Creates an export task for the captured selection and output settings.
        ExportTask(List<ModInfoObject> mods, Set<String> fields, String format, @Nullable String template, Path targetPath) {
            this.mods = mods;
            this.fields = fields;
            this.format = format;
            this.template = template;
            this.targetPath = targetPath;
            setName(i18n("mods.export.exporting"));
        }

        /// Returns the live, mutable set of paths requiring remote metadata retries.
        Set<Path> getFailedModPaths() {
            return failedModPaths;
        }

        /// Fetches requested metadata, writes the output, and reports the remote lookup failure count.
        @Override
        public void execute() throws Exception {
            networkErrorCount.set(0);

            prefetchDataWithProgress();

            if (format.equals("csv")) {
                exportToCSVWithProgress();
            } else if (format.equals("json")) {
                exportToJSONWithProgress();
            } else {
                exportToCustomTextWithProgress();
            }

            setResult(networkErrorCount.get());
        }

        /// Fetches requested links and digests, processing at most three mods concurrently.
        private void prefetchDataWithProgress() {
            boolean needsRemoteInfo = fields.stream().anyMatch(f ->
                    f.equals("curseForgeUrl") || f.equals("curseForgeFileUrl") ||
                            f.equals("curseForgeDownloadPage") || f.equals("modrinthUrl") ||
                            f.equals("modrinthFileUrl"));
            boolean needsSha1 = fields.contains("sha1");
            boolean needsSha512 = fields.contains("sha512");

            if (!needsRemoteInfo && !needsSha1 && !needsSha512) {
                return;
            }

            List<ModInfoObject> modsToProcess;
            if (failedModPaths.isEmpty()) {
                modsToProcess = mods;
            } else {
                modsToProcess = mods.stream()
                        .filter(m -> failedModPaths.contains(m.getModInfo().getFile()))
                        .toList();
            }

            final int totalTasks = modsToProcess.size();
            if (totalTasks == 0) return;

            Semaphore semaphore = new Semaphore(3);
            AtomicInteger completedTasks = new AtomicInteger(0);
            List<CompletableFuture<Void>> futures = new ArrayList<>();

            for (ModInfoObject modInfo : modsToProcess) {
                LocalModFile mod = modInfo.getModInfo();
                Path filePath = mod.getFile();

                try {
                    semaphore.acquire();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }

                futures.add(CompletableFuture.runAsync(() -> {
                    try {
                        if (needsRemoteInfo) {
                            RemoteModInfo remoteInfo = getRemoteModInfo(mod);
                            if (remoteInfo.hasNetworkError) {
                                networkErrorCount.incrementAndGet();
                                failedModPaths.add(filePath);
                            } else {
                                failedModPaths.remove(filePath);
                            }
                        }
                        if (needsSha1) {
                            computeSha1Cached(filePath);
                        }
                        if (needsSha512) {
                            computeSha512Cached(filePath);
                        }
                    } catch (Exception e) {
                        LOG.warning("Failed to prefetch data for " + filePath, e);
                    } finally {
                        semaphore.release();
                        updateProgress(completedTasks.incrementAndGet(), totalTasks);
                    }
                }, Schedulers.io()));
            }

            // Wait for all prefetch tasks to complete
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        }

        /// Writes one expanded template per mod as UTF-8 text, replacing the target file.
        private void exportToCustomTextWithProgress() throws IOException {
            StringBuilder sb = new StringBuilder();
            for (ModInfoObject modInfo : mods) {
                sb.append(applyTemplate(modInfo, Objects.requireNonNull(template, "template")));
                sb.append(System.lineSeparator());
            }
            Files.writeString(targetPath, sb.toString(), StandardCharsets.UTF_8);
        }

        /// Writes selected fields as a CSV table with a header row, replacing the target file.
        private void exportToCSVWithProgress() throws IOException {
            CSVTable table = new CSVTable();

            List<String> orderedFields = new ArrayList<>(fields);

            List<String> headers = getFieldHeaders(orderedFields);
            for (int col = 0; col < headers.size(); col++) {
                table.set(col, 0, headers.get(col));
            }

            int row = 1;
            for (ModInfoObject mod : mods) {
                List<String> values = getFieldValues(mod, orderedFields);
                for (int col = 0; col < values.size(); col++) {
                    table.set(col, row, values.get(col));
                }
                row++;
            }

            table.write(targetPath);
        }

        /// Writes selected fields as a UTF-8 JSON array, replacing the target file.
        private void exportToJSONWithProgress() throws IOException {
            List<Map<String, String>> jsonData = new ArrayList<>();
            for (ModInfoObject mod : mods) {
                Map<String, String> modData = new LinkedHashMap<>();
                for (String field : fields) {
                    modData.put(field, getFieldValue(mod, field));
                }
                jsonData.add(modData);
            }

            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            String json = gson.toJson(jsonData);
            Files.writeString(targetPath, json, StandardCharsets.UTF_8);
        }
    }

    /// Expands recognized field placeholders; unknown fields produce empty strings.
    private String applyTemplate(ModInfoObject modInfo, String template) {
        // Parse template with {field} placeholders
        StringBuilder result = new StringBuilder();
        int i = 0;
        while (i < template.length()) {
            if (template.charAt(i) == '{') {
                int end = template.indexOf('}', i);
                if (end != -1) {
                    String field = template.substring(i + 1, end);
                    result.append(getFieldValue(modInfo, field));
                    i = end + 1;
                } else {
                    result.append(template.charAt(i));
                    i++;
                }
            } else {
                result.append(template.charAt(i));
                i++;
            }
        }
        return result.toString();
    }

    /// Returns CSV labels in the supplied field order, preserving unknown identifiers.
    private List<String> getFieldHeaders(List<String> fields) {
        List<String> headers = new ArrayList<>();
        for (String field : fields) {
            headers.add(switch (field) {
                case "name" -> "Name";
                case "version" -> "Version";
                case "modid" -> "Mod ID";
                case "gameVersion" -> "Game Version";
                case "authors" -> "Authors";
                case "description" -> "Description";
                case "url" -> "URL";
                case "active" -> "Active";
                case "modLoaderType" -> "Mod Loader Type";
                case "mcmodId" -> "MCMod ID";
                case "abbr" -> "Abbreviation";
                case "chineseName" -> "Chinese Name";
                case "sha1" -> "SHA1";
                case "sha512" -> "SHA512";
                case "curseForgeUrl" -> "CurseForge URL";
                case "curseForgeFileUrl" -> "CurseForge File URL";
                case "curseForgeDownloadPage" -> "CurseForge Download Page";
                case "modrinthUrl" -> "Modrinth URL";
                case "modrinthFileUrl" -> "Modrinth File URL";
                default -> field;
            });
        }
        return headers;
    }

    /// Returns field values for a mod in the supplied order.
    private List<String> getFieldValues(ModInfoObject modInfo, List<String> fields) {
        List<String> values = new ArrayList<>();
        for (String field : fields) {
            values.add(getFieldValue(modInfo, field));
        }
        return values;
    }

    /// Returns the requested metadata value, resolving links or digests when needed.
    private String getFieldValue(ModInfoObject modInfo, String field) {
        LocalModFile mod = modInfo.getModInfo();
        @Nullable ModTranslations.Mod modTranslations = modInfo.getModTranslations();

        return switch (field) {
            case "name" -> mod.getName() != null ? mod.getName() : "";
            case "version" -> mod.getVersion() != null ? mod.getVersion() : "";
            case "modid" -> mod.getId() != null ? mod.getId() : "";
            case "gameVersion" -> mod.getGameVersion() != null ? mod.getGameVersion() : "";
            case "authors" -> mod.getAuthors() != null ? mod.getAuthors() : "";
            case "description" -> mod.getDescription() != null ? mod.getDescription().toStringSingleLine() : "";
            case "url" -> mod.getUrl() != null ? mod.getUrl() : "";
            case "active" -> String.valueOf(mod.isActive());
            case "modLoaderType" -> mod.getModLoaderType() != null ? mod.getModLoaderType().name() : "";
            case "mcmodId" -> modTranslations != null ? modTranslations.getMcmod() : "";
            case "abbr" -> modTranslations != null ? modTranslations.getAbbr() : "";
            case "chineseName" -> {
                if (modTranslations == null) {
                    yield "";
                }
                String chineseName = modTranslations.getName();
                if (chineseName == null || !StringUtils.containsChinese(chineseName)) {
                    yield "";
                }
                chineseName = StringUtils.removeEmoji(chineseName);
                yield chineseName;
            }
            case "sha1" -> {
                @Nullable String sha1 = computeSha1Cached(mod.getFile());
                yield sha1 != null ? sha1 : "";
            }
            case "sha512" -> {
                @Nullable String sha512 = computeSha512Cached(mod.getFile());
                yield sha512 != null ? sha512 : "";
            }
            case "curseForgeUrl" -> {
                RemoteModInfo remoteInfo = getRemoteModInfo(mod);
                yield remoteInfo.curseForgeUrl;
            }
            case "curseForgeFileUrl" -> {
                RemoteModInfo remoteInfo = getRemoteModInfo(mod);
                yield remoteInfo.curseForgeFileUrl;
            }
            case "curseForgeDownloadPage" -> {
                RemoteModInfo remoteInfo = getRemoteModInfo(mod);
                yield remoteInfo.curseForgeDownloadPage;
            }
            case "modrinthUrl" -> {
                RemoteModInfo remoteInfo = getRemoteModInfo(mod);
                yield remoteInfo.modrinthUrl;
            }
            case "modrinthFileUrl" -> {
                RemoteModInfo remoteInfo = getRemoteModInfo(mod);
                yield remoteInfo.modrinthFileUrl;
            }
            default -> "";
        };
    }

    private @Nullable String computeSha1(Path path) {
        try {
            return DigestUtils.digestToString("SHA-1", path);
        } catch (IOException e) {
            LOG.warning("Failed to compute SHA1 for " + path, e);
            return null;
        }
    }

    private @Nullable String computeSha512(Path path) {
        try {
            return DigestUtils.digestToString("SHA-512", path);
        } catch (IOException e) {
            LOG.warning("Failed to compute SHA512 for " + path, e);
            return null;
        }
    }

    /// Computes SHA1 hash with caching to avoid redundant computation.
    /// @param path The file path to compute hash for
    /// @return The SHA1 hash string, or null if computation failed
    private @Nullable String computeSha1Cached(Path path) {
        return sha1Cache.computeIfAbsent(path, p -> computeSha1(p));
    }

    /// Computes SHA512 hash with caching to avoid redundant computation.
    /// @param path The file path to compute hash for
    /// @return The SHA512 hash string, or null if computation failed
    private @Nullable String computeSha512Cached(Path path) {
        return sha512Cache.computeIfAbsent(path, p -> computeSha512(p));
    }

    private RemoteModInfo getRemoteModInfo(LocalModFile mod) {
        Path filePath = mod.getFile();
        @Nullable RemoteModInfo cached = remoteModInfoCache.get(filePath);
        if (cached != null) {
            return cached;
        }

        String curseForgeUrl = "";
        String curseForgeFileUrl = "";
        String curseForgeDownloadPage = "";
        String modrinthUrl = "";
        String modrinthFileUrl = "";
        boolean hasNetworkError = false;

        DownloadProvider downloadProvider = DownloadProviders.getDownloadProvider();

        // Fetch CurseForge info
        try {
            if (CurseForgeRemoteAddonRepository.isAvailable()) {
                RemoteAddonRepository curseForgeRepo = RemoteAddon.Source.CURSEFORGE.getRepoForType(RemoteAddon.Type.MOD);
                if (curseForgeRepo != null) {
                    Optional<RemoteAddon.Version> curseForgeVersion = curseForgeRepo.getRemoteVersionByLocalFile(filePath);
                    if (curseForgeVersion.isPresent()) {
                        RemoteAddon.Version version = curseForgeVersion.get();
                        curseForgeFileUrl = version.file() != null && version.file().url() != null ? version.file().url() : "";
                        try {
                            RemoteAddon addon = curseForgeRepo.getAddonById(downloadProvider, version.projectId());
                            if (addon != null) {
                                curseForgeUrl = addon.pageUrl() != null ? addon.pageUrl() : "";
                                if (version.self() instanceof CurseForgeRemoteAddonRepository.CurseAddon.LatestFile latestFile) {
                                    curseForgeDownloadPage = curseForgeUrl + "/download/" + latestFile.id();
                                }
                            }
                        } catch (IOException e) {
                            hasNetworkError = true;
                            LOG.warning("Failed to get CurseForge mod info for " + filePath, e);
                        }
                    }
                }
            }
        } catch (IOException e) {
            hasNetworkError = true;
            LOG.warning("Failed to lookup CurseForge version for " + filePath, e);
        }

        // Fetch Modrinth info
        try {
            RemoteAddonRepository modrinthRepo = RemoteAddon.Source.MODRINTH.getRepoForType(RemoteAddon.Type.MOD);
            if (modrinthRepo != null) {
                Optional<RemoteAddon.Version> modrinthVersion = modrinthRepo.getRemoteVersionByLocalFile(filePath);
                if (modrinthVersion.isPresent()) {
                    RemoteAddon.Version version = modrinthVersion.get();
                    modrinthFileUrl = version.file() != null && version.file().url() != null ? version.file().url() : "";
                    try {
                        RemoteAddon addon = modrinthRepo.getAddonById(downloadProvider, version.projectId());
                        if (addon != null) {
                            modrinthUrl = addon.pageUrl() != null ? addon.pageUrl() : "";
                        }
                    } catch (IOException e) {
                        hasNetworkError = true;
                        LOG.warning("Failed to get Modrinth mod info for " + filePath, e);
                    }
                }
            }
        } catch (IOException e) {
            hasNetworkError = true;
            LOG.warning("Failed to lookup Modrinth version for " + filePath, e);
        }

        RemoteModInfo result = new RemoteModInfo(
                curseForgeUrl,
                curseForgeFileUrl,
                curseForgeDownloadPage,
                modrinthUrl,
                modrinthFileUrl,
                hasNetworkError);
        remoteModInfoCache.put(filePath, result);
        return result;
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

    /// Displays the mod list and its search, selection, and export controls.
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

        /// Creates controls bound to the page's loading state and mod entries.
        public ModListPageSkin(ModListPage skinnable) {
            super(skinnable);

            StackPane pane = new StackPane();
            pane.setPadding(new Insets(10));
            pane.getStyleClass().addAll("notice-pane");

            ComponentList root = new ComponentList();
            pane.getChildren().setAll(root);
            root.getStyleClass().add("no-padding");
            listView = new JFXListView<>();
            listView.getStyleClass().add("no-horizontal-scrollbar");

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
                toolbarNormal.getChildren().setAll(
                        createToolbarButton2(i18n("button.refresh"), SVG.REFRESH, skinnable::refresh),
                        createToolbarButton2(i18n("mods.add"), SVG.ADD, skinnable::add),
                        createToolbarButton2(i18n("button.reveal_dir"), SVG.FOLDER_OPEN, skinnable::openModFolder),
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

                toolbarSelecting.getChildren().setAll(
                        createToolbarButton2(i18n("button.remove"), SVG.DELETE_FOREVER, () -> {
                            Controllers.confirm(i18n("button.remove.confirm"), i18n("button.remove"), () -> {
                                skinnable.removeSelected(listView.getSelectionModel().getSelectedItems());
                            }, null);
                        }),
                        createToolbarButton2(i18n("mods.enable"), SVG.CHECK, () ->
                                skinnable.enableSelected(listView.getSelectionModel().getSelectedItems())),
                        createToolbarButton2(i18n("mods.disable"), SVG.CLOSE, () ->
                                skinnable.disableSelected(listView.getSelectionModel().getSelectedItems())),
                        createToolbarButton2(i18n("addon.check_update.button"), SVG.UPDATE, () ->
                                skinnable.checkUpdates(
                                        listView.getSelectionModel().getSelectedItems().stream()
                                                .map(ModListPage.ModInfoObject::getModInfo)
                                                .toList()
                                )
                        ),
                        createToolbarButton2(i18n("mods.export"), SVG.DOWNLOAD, this::showExportDialog),
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
                    if (isSearching.get()) {
                        search();
                    }
                });

                listView.setOnContextMenuRequested(event -> {
                    ModListPage.ModInfoObject selectedItem = listView.getSelectionModel().getSelectedItem();
                    if (selectedItem != null && listView.getSelectionModel().getSelectedItems().size() == 1) {
                        listView.getSelectionModel().clearSelection();
                        Controllers.dialog(new ModListPage.ModInfoDialog(selectedItem, skinnable));
                    }
                });

                // ListViewBehavior would consume ESC pressed event, preventing us from handling it
                // So we ignore it here
                ignoreEvent(listView, KeyEvent.KEY_PRESSED, e -> e.getCode() == KeyCode.ESCAPE);

                center.setContent(listView);
                root.getContent().add(center);
            }

            getChildren().setAll(pane);
        }

        /// Describes an export field and its initial selection state.
        private static final class FieldInfo {
            /// The field identifier used in exported data and templates.
            final String id;
            /// The resource key for the field label.
            final String i18nKey;
            /// Whether the field is selected when the dialog opens.
            final boolean selectedByDefault;

            /// Creates a field descriptor with its label key and default selection.
            FieldInfo(String id, @PropertyKey(resourceBundle = "assets.lang.I18N") String i18nKey, boolean selectedByDefault) {
                this.id = id;
                this.i18nKey = i18nKey;
                this.selectedByDefault = selectedByDefault;
            }
        }

        /// The available fields in their display order.
        private static final @Unmodifiable List<FieldInfo> FIELD_INFOS = List.of(
                new FieldInfo("name", "mods.export.field.name", true),
                new FieldInfo("version", "mods.export.field.version", true),
                new FieldInfo("modid", "mods.export.field.modid", true),
                new FieldInfo("gameVersion", "mods.export.field.game_version", false),
                new FieldInfo("authors", "mods.export.field.authors", false),
                new FieldInfo("description", "mods.export.field.description", false),
                new FieldInfo("url", "mods.export.field.url", false),
                new FieldInfo("active", "mods.export.field.active", false),
                new FieldInfo("modLoaderType", "mods.export.field.mod_loader_type", false),
                new FieldInfo("mcmodId", "mods.export.field.mcmod_id", false),
                new FieldInfo("abbr", "mods.export.field.abbr", false),
                new FieldInfo("chineseName", "mods.export.field.chinese_name", false),
                new FieldInfo("sha1", "SHA1", false),
                new FieldInfo("sha512", "SHA512", false),
                new FieldInfo("curseForgeUrl", "mods.export.field.curseforge_url", false),
                new FieldInfo("curseForgeFileUrl", "mods.export.field.curseforge_file_url", false),
                new FieldInfo("curseForgeDownloadPage", "mods.export.field.curseforge_download_page", false),
                new FieldInfo("modrinthUrl", "mods.export.field.modrinth_url", false),
                new FieldInfo("modrinthFileUrl", "mods.export.field.modrinth_file_url", false)
        );

        /// Opens format, field, and template controls for exporting the selected mods.
        private void showExportDialog() {
            ToggleGroup formatGroup = new ToggleGroup();
            JFXRadioButton csvRadio = new JFXRadioButton("CSV");
            JFXRadioButton jsonRadio = new JFXRadioButton("JSON");
            JFXRadioButton customRadio = new JFXRadioButton(i18n("mods.export.format.custom"));
            csvRadio.setToggleGroup(formatGroup);
            jsonRadio.setToggleGroup(formatGroup);
            customRadio.setToggleGroup(formatGroup);
            csvRadio.setSelected(true);

            Map<String, JFXCheckBox> checkBoxes = new LinkedHashMap<>();
            VBox fieldsBox = new VBox(8);
            for (FieldInfo info : FIELD_INFOS) {
                JFXCheckBox checkBox = new JFXCheckBox(i18n(info.i18nKey));
                checkBox.setSelected(info.selectedByDefault);
                checkBoxes.put(info.id, checkBox);
                fieldsBox.getChildren().add(checkBox);
            }
            fieldsBox.setAlignment(Pos.CENTER_LEFT);

            Label formatLabel = new Label(i18n("mods.export.format"));
            Label fieldsLabel = new Label(i18n("mods.export.fields"));
            Label templateLabel = new Label(i18n("mods.export.template"));

            JFXTextField templateTextField = new JFXTextField("- {name}, {version}, {modid}");

            Label placeholdersLabel = new Label(i18n("mods.export.placeholders"));
            FlowPane placeholdersPane = new FlowPane(5, 5);
            placeholdersPane.setAlignment(Pos.CENTER_LEFT);
            for (FieldInfo info : FIELD_INFOS) {
                String placeholderText = "{" + info.id + "}";
                JFXButton btn = FXUtils.newBorderButton(placeholderText);
                btn.setOnAction(ev -> FXUtils.copyText(placeholderText));
                placeholdersPane.getChildren().add(btn);
            }

            HBox formatBox = new HBox(10, csvRadio, jsonRadio, customRadio);
            formatBox.setAlignment(Pos.CENTER_LEFT);

            VBox templateBox = new VBox(5, templateLabel, templateTextField, placeholdersLabel, placeholdersPane);
            templateBox.setAlignment(Pos.CENTER_LEFT);
            templateBox.setMaxWidth(360);
            templateBox.setVisible(false);
            templateBox.setManaged(false);

            csvRadio.setOnAction(e -> {
                fieldsLabel.setVisible(true);
                fieldsLabel.setManaged(true);
                fieldsBox.setVisible(true);
                fieldsBox.setManaged(true);
                templateBox.setVisible(false);
                templateBox.setManaged(false);
            });
            jsonRadio.setOnAction(e -> {
                fieldsLabel.setVisible(true);
                fieldsLabel.setManaged(true);
                fieldsBox.setVisible(true);
                fieldsBox.setManaged(true);
                templateBox.setVisible(false);
                templateBox.setManaged(false);
            });
            customRadio.setOnAction(e -> {
                fieldsLabel.setVisible(false);
                fieldsLabel.setManaged(false);
                fieldsBox.setVisible(false);
                fieldsBox.setManaged(false);
                templateBox.setVisible(true);
                templateBox.setManaged(true);
            });

            VBox contentBox = new VBox(12, formatLabel, formatBox, fieldsLabel, fieldsBox, templateBox);
            contentBox.setAlignment(Pos.CENTER_LEFT);
            contentBox.setMaxWidth(380);
            contentBox.setPadding(new Insets(0, 0, 12, 0));

            ScrollPane scrollPane = new ScrollPane(contentBox);
            FXUtils.smoothScrolling(scrollPane);
            scrollPane.setFitToWidth(true);
            scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
            scrollPane.setMaxHeight(400);
            scrollPane.setPrefHeight(350);

            JFXDialogLayout dialogLayout = new JFXDialogLayout();
            dialogLayout.setHeading(new Label(i18n("mods.export.title")));
            dialogLayout.setBody(scrollPane);

            JFXButton exportButton = new JFXButton(i18n("button.export"));
            exportButton.getStyleClass().add("dialog-accept");
            exportButton.setOnAction(e -> {
                String format;
                Set<String> fields = new LinkedHashSet<>();
                @Nullable String customTemplate = null;

                if (customRadio.isSelected()) {
                    format = "custom";
                    customTemplate = templateTextField.getText();
                } else {
                    format = csvRadio.isSelected() ? "csv" : "json";
                    checkBoxes.forEach((id, chk) -> {
                        if (chk.isSelected()) {
                            fields.add(id);
                        }
                    });
                }

                dialogLayout.fireEvent(new DialogCloseEvent());
                getSkinnable().exportMods(listView.getSelectionModel().getSelectedItems(), format, fields, customTemplate);
            });

            JFXButton cancelButton = new JFXButton(i18n("button.cancel"));
            cancelButton.setButtonType(JFXButton.ButtonType.FLAT);
            cancelButton.getStyleClass().add("dialog-cancel");
            cancelButton.setOnAction(ev -> dialogLayout.fireEvent(new DialogCloseEvent()));

            dialogLayout.setActions(exportButton, cancelButton);

            Controllers.dialog(dialogLayout);
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
                            || predicate.test((item.getModTranslations() != null ? item.getModTranslations().getDisplayName() : null))) {
                        listView.getItems().add(item);
                    }
                }
            }
        }

    }

    public static final class ModInfoObject {
        private final BooleanProperty active;
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

    /// Displays mod metadata and links to download and information pages.
    private static final class ModInfoDialog extends JFXDialogLayout {

        /// Creates a details dialog whose download actions use the page's current instance.
        ModInfoDialog(ModInfoObject modInfo, ModListPage page) {
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
                        pair("addon.curseforge", CurseForgeRemoteAddonRepository.MODS),
                        pair("addon.modrinth", ModrinthRemoteAddonRepository.MODS)
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

                                button.setOnAction(e -> {
                                    @Nullable HMCLGameInstance instance = page.gameInstance;
                                    if (instance == null) {
                                        return;
                                    }
                                    fireEvent(new DialogCloseEvent());
                                    Controllers.navigate(new DownloadPage(
                                            HMCLLocalizedDownloadListPage.ofMod(null, false),
                                            remoteAddon,
                                            HMCLGameInstance.Optional.of(instance),
                                            org.jackhuang.hmcl.ui.download.DownloadPage.FOR_MOD
                                    ));
                                });
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
                officialPageButton.setOnAction(e -> {
                    fireEvent(new DialogCloseEvent());
                    FXUtils.openLink(modInfo.getModInfo().getUrl());
                });

                getActions().add(officialPageButton);
            }

            if (modInfo.getModTranslations() == null || StringUtils.isBlank(modInfo.getModTranslations().getMcmod())) {
                JFXHyperlink searchButton = new JFXHyperlink(i18n("mods.mcmod.search"));
                searchButton.setOnAction(e -> {
                    fireEvent(new DialogCloseEvent());
                    FXUtils.openLink(NetworkUtils.withQuery("https://search.mcmod.cn/s", mapOf(
                            pair("key", modInfo.getModInfo().getName()),
                            pair("site", "all"),
                            pair("filter", "0")
                    )));
                });
                getActions().add(searchButton);
            } else {
                JFXHyperlink mcmodButton = new JFXHyperlink(i18n("mods.mcmod.page"));
                mcmodButton.setOnAction(e -> {
                    fireEvent(new DialogCloseEvent());
                    FXUtils.openLink(ModTranslations.MOD.getMcmodUrl(modInfo.getModTranslations()));
                });
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

        private BooleanProperty booleanProperty;

        private Tooltip warningTooltip;

        ModInfoListCell(JFXListView<ModInfoObject> listView, ModListPage page) {
            super(listView);
            this.page = page;

            this.getStyleClass().add("mod-info-list-cell");

            HBox container = new HBox(8);
            container.setPickOnBounds(false);
            container.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(content, Priority.ALWAYS);
            content.setMouseTransparent(true);
            setSelectable();

            imageContainer.setImage(GameInstanceIconType.COMMAND.getIcon());

            FXUtils.installFastTooltip(restoreButton, i18n("mods.restore"));

            container.getChildren().setAll(checkBox, imageContainer, content, restoreButton, revealButton, infoButton);

            StackPane.setMargin(container, new Insets(8));
            getContainer().getChildren().setAll(container);
        }

        @Override
        protected void updateControl(ModInfoObject dataItem, boolean empty) {
            pseudoClassStateChanged(WARNING, false);
            if (warningTooltip != null) {
                Tooltip.uninstall(this, warningTooltip);
                warningTooltip = null;
            }

            if (empty) return;

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
                    chineseName = StringUtils.removeEmoji(chineseName);

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
            infoButton.setOnAction(e -> Controllers.dialog(new ModInfoDialog(dataItem, page)));

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
