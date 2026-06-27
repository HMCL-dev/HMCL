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
import org.jackhuang.hmcl.util.DigestUtils;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.setting.DownloadProviders;
import org.jackhuang.hmcl.setting.Profile;
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
import java.util.concurrent.locks.ReentrantLock;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class ModListPage extends ListPageBase<ModListPageSkin.ModInfoObject> implements VersionPage.VersionLoadable, PageAware {
    private final BooleanProperty modded = new SimpleBooleanProperty(this, "modded", false);

    private final ReentrantLock lock = new ReentrantLock();

    private ModManager modManager;
    private Profile profile;
    private String instanceId;
    private String gameVersion;

    final EnumSet<ModLoaderType> supportedLoaders = EnumSet.noneOf(ModLoaderType.class);

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
    public void loadVersion(Profile profile, String id) {
        this.profile = profile;
        this.instanceId = id;

        HMCLGameRepository repository = profile.getRepository();
        Version resolved = repository.getResolvedPreservingPatchesVersion(id);
        this.gameVersion = repository.getGameVersion(resolved).orElse(null);
        LibraryAnalyzer analyzer = LibraryAnalyzer.analyze(resolved, gameVersion);
        modded.set(analyzer.hasModLoader());
        loadMods(profile.getRepository().getModManager(id));
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
        FXUtils.openFolder(profile.getRepository().getRunDirectory(instanceId).resolve("mods"));
    }

    public void checkUpdates(Collection<LocalModFile> mods) {
        Objects.requireNonNull(mods);
        Runnable action = () -> Controllers.taskDialog(Task
                        .composeAsync(() -> {
                            Optional<String> gameVersion = profile.getRepository().getGameVersion(instanceId);
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

        if (profile.getRepository().isModpack(instanceId)) {
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

    /// Exports the mod list to a file.
    /// @param selectedMods The list of selected mods to export
    /// @param format The export format: "csv" or "json"
    /// @param fields The set of field names to export
    public void exportMods(List<ModListPageSkin.ModInfoObject> selectedMods, String format, Set<String> fields) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(i18n("mods.export.title"));
        String extension = format.equals("csv") ? ".csv" : ".json";
        chooser.getExtensionFilters().setAll(
                new FileChooser.ExtensionFilter(format.equals("csv") ? i18n("extension.csv") : i18n("extension.json"),
                        "*" + extension));
        chooser.setInitialFileName(instanceId + "-mods" + extension);
        Path targetPath = FileUtils.toPath(chooser.showSaveDialog(Controllers.getStage()));
        if (targetPath == null) return;

        try {
            if (format.equals("csv")) {
                exportToCSV(selectedMods, fields, targetPath);
            } else {
                exportToJSON(selectedMods, fields, targetPath);
            }
            Controllers.showToast(i18n("mods.export.success"));
        } catch (IOException e) {
            LOG.warning("Failed to export mods", e);
            Controllers.dialog(e.getMessage(), i18n("message.error"), MessageDialogPane.MessageType.ERROR);
        }
    }

    private void exportToCSV(List<ModListPageSkin.ModInfoObject> mods, Set<String> fields, Path targetPath) throws IOException {
        CSVTable table = new CSVTable();

        // Header row
        List<String> headers = getFieldHeaders(fields);
        for (int col = 0; col < headers.size(); col++) {
            table.set(col, 0, headers.get(col));
        }

        // Data rows
        for (int row = 0; row < mods.size(); row++) {
            ModListPageSkin.ModInfoObject mod = mods.get(row);
            List<String> values = getFieldValues(mod, fields);
            for (int col = 0; col < values.size(); col++) {
                table.set(col, row + 1, values.get(col));
            }
        }

        table.write(targetPath);
    }

    private void exportToJSON(List<ModListPageSkin.ModInfoObject> mods, Set<String> fields, Path targetPath) throws IOException {
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

    private List<String> getFieldHeaders(Set<String> fields) {
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
                default -> field;
            });
        }
        return headers;
    }

    private List<String> getFieldValues(ModListPageSkin.ModInfoObject modInfo, Set<String> fields) {
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
            case "name" -> mod.getName();
            case "version" -> mod.getVersion();
            case "modid" -> mod.getId() != null ? mod.getId() : "";
            case "gameVersion" -> mod.getGameVersion();
            case "authors" -> mod.getAuthors();
            case "description" -> mod.getDescription().toStringSingleLine();
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
                if (StringUtils.containsEmoji(chineseName)) {
                    StringBuilder builder = new StringBuilder();
                    chineseName.codePoints().forEach(cp -> {
                        if (cp < 0x1F300 || cp > 0x1FAFF) {
                            builder.appendCodePoint(cp);
                        }
                    });
                    chineseName = builder.toString().trim();
                }
                yield chineseName;
            }
            case "sha1" -> {
                String sha1 = computeSha1(mod.getFile());
                yield sha1 != null ? sha1 : "";
            }
            case "sha512" -> {
                String sha512 = computeSha512(mod.getFile());
                yield sha512 != null ? sha512 : "";
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

    public Profile getProfile() {
        return this.profile;
    }

    public String getInstanceId() {
        return this.instanceId;
    }
}
