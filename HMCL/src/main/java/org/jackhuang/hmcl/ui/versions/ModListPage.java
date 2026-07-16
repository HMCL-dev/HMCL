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

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.ObservableList;
import javafx.scene.control.Skin;
import javafx.stage.FileChooser;
import org.jackhuang.hmcl.download.LibraryAnalyzer;
import org.jackhuang.hmcl.game.HMCLGameRepository;
import org.jackhuang.hmcl.game.Version;
import org.jackhuang.hmcl.addon.mod.LocalModFile;
import org.jackhuang.hmcl.addon.mod.ModLoaderType;
import org.jackhuang.hmcl.addon.mod.ModManager;
import org.jackhuang.hmcl.addon.RemoteAddon;
import org.jackhuang.hmcl.addon.RemoteAddonRepository;
import org.jackhuang.hmcl.addon.repository.CurseForgeRemoteAddonRepository;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.util.DigestUtils;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.setting.DownloadProviders;
import org.jackhuang.hmcl.setting.GameDirectory;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.ListPageBase;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane;
import org.jackhuang.hmcl.ui.construct.PageAware;
import org.jackhuang.hmcl.util.TaskCancellationAction;
import org.jackhuang.hmcl.util.io.CSVTable;
import org.jackhuang.hmcl.util.io.FileUtils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.jetbrains.annotations.Nullable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class ModListPage extends ListPageBase<ModListPageSkin.ModInfoObject> implements VersionPage.GameInstanceLoadable, PageAware {
    private final BooleanProperty modded = new SimpleBooleanProperty(this, "modded", false);

    private final ReentrantLock lock = new ReentrantLock();

    private ModManager modManager;
    private HMCLGameRepository repository;
    private String instanceId;
    private String gameVersion;

    final EnumSet<ModLoaderType> supportedLoaders = EnumSet.noneOf(ModLoaderType.class);

    private static final class RemoteModInfo {
        final String curseForgeUrl;
        final String curseForgeFileUrl;
        final String curseForgeDownloadPage;
        final String modrinthUrl;
        final String modrinthFileUrl;
        final boolean hasNetworkError;

        RemoteModInfo(String curseForgeUrl, String curseForgeFileUrl, String curseForgeDownloadPage, String modrinthUrl, String modrinthFileUrl, boolean hasNetworkError) {
            this.curseForgeUrl = curseForgeUrl;
            this.curseForgeFileUrl = curseForgeFileUrl;
            this.curseForgeDownloadPage = curseForgeDownloadPage;
            this.modrinthUrl = modrinthUrl;
            this.modrinthFileUrl = modrinthFileUrl;
            this.hasNetworkError = hasNetworkError;
        }
    }

    private final Map<Path, RemoteModInfo> remoteModInfoCache = new ConcurrentHashMap<>();
    private final Map<Path, String> sha1Cache = new ConcurrentHashMap<>();
    private final Map<Path, String> sha512Cache = new ConcurrentHashMap<>();

    public ModListPage() {
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
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new ModListPageSkin(this);
    }

    public void refresh() {
        loadMods(modManager);
    }

    @Override
    public void loadInstance(HMCLGameRepository repository, String instanceId) {
        this.repository = repository;
        this.instanceId = instanceId;

        Version resolved = repository.getResolvedPreservingPatchesVersion(instanceId);
        this.gameVersion = repository.getGameVersion(resolved).orElse(null);
        LibraryAnalyzer analyzer = LibraryAnalyzer.analyze(resolved, gameVersion);
        modded.set(analyzer.hasModLoader());
        loadMods(repository.getModManager(instanceId));
    }

    private void loadMods(ModManager modManager) {
        setLoading(true);

        this.modManager = modManager;
        CompletableFuture.supplyAsync(() -> {
            lock.lock();
            try {
                modManager.refresh();
                return modManager.getLocalFiles().stream().map(ModListPageSkin.ModInfoObject::new).toList();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } finally {
                lock.unlock();
            }
        }, Schedulers.io()).whenCompleteAsync((list, exception) -> {
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

        LibraryAnalyzer analyzer = modManager.getLibraryAnalyzer();
        if (analyzer == null) {
            Collections.addAll(supportedLoaders, ModLoaderType.values());
            return;
        }

        for (LibraryAnalyzer.LibraryType type : LibraryAnalyzer.LibraryType.values()) {
            if (type.isModLoader() && analyzer.has(type)) {
                ModLoaderType modLoaderType = type.getModLoaderType();
                if (modLoaderType != null) {
                    supportedLoaders.add(modLoaderType);

                    if (modLoaderType == ModLoaderType.CLEANROOM)
                        supportedLoaders.add(ModLoaderType.FORGE);
                }
            }
        }

        if (analyzer.has(LibraryAnalyzer.LibraryType.NEO_FORGE) && "1.20.1".equals(gameVersion)) {
            supportedLoaders.add(ModLoaderType.FORGE);
        }

        if (analyzer.has(LibraryAnalyzer.LibraryType.QUILT)) {
            supportedLoaders.add(ModLoaderType.FABRIC);
        }

        if (analyzer.has(LibraryAnalyzer.LibraryType.LEGACY_FABRIC)) {
            supportedLoaders.add(ModLoaderType.FABRIC);
        }

        if (analyzer.has(LibraryAnalyzer.LibraryType.FABRIC) && modManager.hasMod("kilt", ModLoaderType.FABRIC)) {
            supportedLoaders.add(ModLoaderType.FORGE);
            supportedLoaders.add(ModLoaderType.NEO_FORGE);
        }

        // Sinytra Connector
        if (analyzer.has(LibraryAnalyzer.LibraryType.NEO_FORGE) && modManager.hasMod("connectormod", ModLoaderType.NEO_FORGE)
                || "1.20.1".equals(gameVersion) && analyzer.has(LibraryAnalyzer.LibraryType.FORGE) && modManager.hasMod("connectormod", ModLoaderType.FORGE)) {
            supportedLoaders.add(ModLoaderType.FABRIC);
        }
    }

    public void add() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(i18n("mods.add.title"));
        chooser.getExtensionFilters().setAll(new FileChooser.ExtensionFilter(i18n("extension.mod"), "*.jar", "*.litemod"));
        List<Path> res = FileUtils.toPaths(chooser.showOpenMultipleDialog(Controllers.getStage()));

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

    void removeSelected(ObservableList<ModListPageSkin.ModInfoObject> selectedItems) {
        try {
            modManager.removeMods(selectedItems.stream()
                    .filter(Objects::nonNull)
                    .map(ModListPageSkin.ModInfoObject::getModInfo)
                    .toArray(LocalModFile[]::new));
            loadMods(modManager);
        } catch (IOException ignore) {
            // Fail to remove mods if the game is running or the mod is absent.
        }
    }

    void enableSelected(ObservableList<ModListPageSkin.ModInfoObject> selectedItems) {
        selectedItems.stream()
                .filter(Objects::nonNull)
                .map(ModListPageSkin.ModInfoObject::getModInfo)
                .forEach(info -> info.setActive(true));
    }

    void disableSelected(ObservableList<ModListPageSkin.ModInfoObject> selectedItems) {
        selectedItems.stream()
                .filter(Objects::nonNull)
                .map(ModListPageSkin.ModInfoObject::getModInfo)
                .forEach(info -> info.setActive(false));
    }

    public void openModFolder() {
        FXUtils.openFolder(repository.getRunDirectory(instanceId).resolve("mods"));
    }

    public void checkUpdates(Collection<LocalModFile> mods) {
        Objects.requireNonNull(mods);
        Runnable action = () -> Controllers.taskDialog(Task
                        .composeAsync(() -> {
                            Optional<String> gameVersion = repository.getGameVersion(instanceId);
                            return gameVersion.map(g -> new AddonCheckUpdatesTask<>(DownloadProviders.getDownloadProvider(), g, mods)).orElse(null);
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

        if (repository.isModpack(instanceId)) {
            Controllers.confirm(
                    i18n("mods.update_modpack_mod.warning"), null,
                    MessageDialogPane.MessageType.WARNING,
                    action, null);
        } else {
            action.run();
        }
    }

    public void download() {
        Controllers.getDownloadPage().showModDownloads().selectVersion(instanceId);
        Controllers.navigate(Controllers.getDownloadPage());
    }

    /// Exports the mod list to a file asynchronously to avoid blocking the UI thread.
    /// @param selectedMods The list of selected mods to export
    /// @param format The export format: "csv", "json", or "custom"
    /// @param fields The set of field names to export (used for csv/json)
    /// @param customTemplate The custom format template string (used when format is "custom")
    public void exportMods(List<ModListPageSkin.ModInfoObject> selectedMods, String format, Set<String> fields, String customTemplate) {
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
                ? new FileChooser.ExtensionFilter(i18n("extension.csv"), "*" + extension)
                : format.equals("json")
                        ? new FileChooser.ExtensionFilter(i18n("extension.json"), "*" + extension)
                        : new FileChooser.ExtensionFilter(i18n("extension.txt"), "*" + extension);
        chooser.getExtensionFilters().setAll(filter);
        chooser.setInitialFileName(instanceId + "-mods" + extension);
        Path targetPath = FileUtils.toPath(chooser.showSaveDialog(Controllers.getStage()));
        if (targetPath == null) return;

        final List<ModListPageSkin.ModInfoObject> modsSnapshot = new ArrayList<>(selectedMods);
        final Path outputPath = targetPath;
        final String exportFormat = format;
        final String template = customTemplate;

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

    private void exportModsWithRetry(List<ModListPageSkin.ModInfoObject> mods, Set<String> fields, String format, String template, Path outputPath) {
        exportModsWithRetry(mods, fields, format, template, outputPath, null);
    }

    private void exportModsWithRetry(List<ModListPageSkin.ModInfoObject> mods, Set<String> fields, String format, String template, Path outputPath, @Nullable Set<Path> failedPaths) {
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
        private final List<ModListPageSkin.ModInfoObject> mods;
        private final Set<String> fields;
        private final String format;
        private final String template;
        private final Path targetPath;
        private final Set<Path> failedModPaths = ConcurrentHashMap.newKeySet();
        private final AtomicInteger networkErrorCount = new AtomicInteger(0);

        ExportTask(List<ModListPageSkin.ModInfoObject> mods, Set<String> fields, String format, String template, Path targetPath) {
            this.mods = mods;
            this.fields = fields;
            this.format = format;
            this.template = template;
            this.targetPath = targetPath;
            setName(i18n("mods.export.exporting"));
        }

        Set<Path> getFailedModPaths() {
            return failedModPaths;
        }

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

            List<ModListPageSkin.ModInfoObject> modsToProcess;
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

            for (ModListPageSkin.ModInfoObject modInfo : modsToProcess) {
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

        private void exportToCustomTextWithProgress() throws IOException {
            StringBuilder sb = new StringBuilder();
            for (ModListPageSkin.ModInfoObject modInfo : mods) {
                sb.append(applyTemplate(modInfo, template));
                sb.append(System.lineSeparator());
            }
            Files.writeString(targetPath, sb.toString(), StandardCharsets.UTF_8);
        }

        private void exportToCSVWithProgress() throws IOException {
            CSVTable table = new CSVTable();

            List<String> orderedFields = new ArrayList<>(fields);

            List<String> headers = getFieldHeaders(orderedFields);
            for (int col = 0; col < headers.size(); col++) {
                table.set(col, 0, headers.get(col));
            }

            int row = 1;
            for (ModListPageSkin.ModInfoObject mod : mods) {
                List<String> values = getFieldValues(mod, orderedFields);
                for (int col = 0; col < values.size(); col++) {
                    table.set(col, row, values.get(col));
                }
                row++;
            }

            table.write(targetPath);
        }

        private void exportToJSONWithProgress() throws IOException {
            List<Map<String, String>> jsonData = new ArrayList<>();
            for (ModListPageSkin.ModInfoObject mod : mods) {
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

    private String applyTemplate(ModListPageSkin.ModInfoObject modInfo, String template) {
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

    private List<String> getFieldValues(ModListPageSkin.ModInfoObject modInfo, List<String> fields) {
        List<String> values = new ArrayList<>();
        for (String field : fields) {
            values.add(getFieldValue(modInfo, field));
        }
        return values;
    }

    private String getFieldValue(ModListPageSkin.ModInfoObject modInfo, String field) {
        LocalModFile mod = modInfo.getModInfo();
        ModTranslations.Mod modTranslations = modInfo.getModTranslations();

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
                String sha1 = computeSha1Cached(mod.getFile());
                yield sha1 != null ? sha1 : "";
            }
            case "sha512" -> {
                String sha512 = computeSha512Cached(mod.getFile());
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
        RemoteModInfo cached = remoteModInfoCache.get(filePath);
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
                RemoteAddonRepository curseForgeRepo = RemoteAddon.Source.CURSEFORGE.getRepoForType(RemoteAddonRepository.Type.MOD);
                if (curseForgeRepo != null) {
                    Optional<RemoteAddon.Version> curseForgeVersion = curseForgeRepo.getRemoteVersionByLocalFile(filePath);
                    if (curseForgeVersion.isPresent()) {
                        RemoteAddon.Version version = curseForgeVersion.get();
                        curseForgeFileUrl = version.file() != null && version.file().url() != null ? version.file().url() : "";
                        try {
                            RemoteAddon addon = curseForgeRepo.getModById(downloadProvider, version.modid());
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
            RemoteAddonRepository modrinthRepo = RemoteAddon.Source.MODRINTH.getRepoForType(RemoteAddonRepository.Type.MOD);
            if (modrinthRepo != null) {
                Optional<RemoteAddon.Version> modrinthVersion = modrinthRepo.getRemoteVersionByLocalFile(filePath);
                if (modrinthVersion.isPresent()) {
                    RemoteAddon.Version version = modrinthVersion.get();
                    modrinthFileUrl = version.file() != null && version.file().url() != null ? version.file().url() : "";
                    try {
                        RemoteAddon addon = modrinthRepo.getModById(downloadProvider, version.modid());
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

    public boolean isModded() {
        return modded.get();
    }

    public BooleanProperty moddedProperty() {
        return modded;
    }

    public void setModded(boolean modded) {
        this.modded.set(modded);
    }

    public GameDirectory getGameDirectory() {
        return this.repository.getGameDirectory();
    }

    public HMCLGameRepository getRepository() {
        return this.repository;
    }

    public String getInstanceId() {
        return this.instanceId;
    }
}
