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
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.ObservableList;
import javafx.scene.control.Skin;
import javafx.stage.FileChooser;
import org.jackhuang.hmcl.download.LibraryAnalyzer;
import org.jackhuang.hmcl.game.HMCLGameRepository;
import org.jackhuang.hmcl.game.Version;
import org.jackhuang.hmcl.addon.mod.LocalModFile;
import org.jackhuang.hmcl.addon.mod.ModLoaderType;
import org.jackhuang.hmcl.addon.mod.ModManager;
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
import org.jackhuang.hmcl.util.io.FileUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReentrantLock;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class ModListPage extends ListPageBase<ModListPageSkin.ModInfoObject> implements VersionPage.GameInstanceLoadable, PageAware {
    private final BooleanProperty modded = new SimpleBooleanProperty(this, "modded", false);

    // Bumped on the FX thread when the background Jar-in-Jar deep scan finishes, so open nested
    // panels can rebuild with the now-resolved nested mod names/versions.
    private final IntegerProperty bundledScanGeneration = new SimpleIntegerProperty(this, "bundledScanGeneration", 0);
    // True once the background bundled-id scan has finished (or there was nothing to scan); the skin
    // disables dependency-changing operations while it is false.
    private final BooleanProperty bundledScanReady = new SimpleBooleanProperty(this, "bundledScanReady", true);

    private final ReentrantLock lock = new ReentrantLock();

    private ModManager modManager;
    private HMCLGameRepository repository;
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
    public void loadInstance(HMCLGameRepository repository, String instanceId) {
        this.repository = repository;
        this.instanceId = instanceId;

        Version resolved = repository.getResolvedPreservingPatchesVersion(instanceId);
        this.gameVersion = repository.getGameVersion(resolved).orElse(null);
        LibraryAnalyzer analyzer = LibraryAnalyzer.analyze(resolved, gameVersion);
        modded.set(analyzer.hasModLoader());

        // Reuse the existing ModManager (and its incremental scan cache) when reopening the same
        // instance, so navigating back to the mod list doesn't rescan every jar from scratch.
        ModManager manager = this.modManager;
        if (manager == null || manager.getRepository() != repository || !instanceId.equals(manager.getInstanceId())) {
            manager = repository.getModManager(instanceId);
        }
        loadMods(manager);
    }

    private void loadMods(ModManager modManager) {
        setLoading(true);
        // Gate dependency-changing operations until the background bundled-id scan finishes: until
        // then getAllBundledModIds() is incomplete and a cascade could miss dependents provided via
        // Jar-in-Jar.
        bundledScanReady.set(false);

        // The mod set may have changed (added/removed/refreshed); drop the download page's cached
        // installed-mods snapshot so dependency status there is re-read.
        DownloadPage.invalidateInstalledMods();

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
            if (this.modManager != modManager)
                return; // instance switched while this (slower) load was in flight — a newer load governs
            updateSupportedLoaders(modManager);

            if (exception == null) {
                getItems().setAll(list);
            } else {
                LOG.warning("Failed to load mods", exception);
                getItems().clear();
            }
            setLoading(false);

            // Now that the list is on screen, resolve the deeper Jar-in-Jar layers in the background
            // (they need each nested jar extracted, too slow to block the first paint). When done, bump
            // the generation so open nested panels rebuild with the resolved names and the dependency
            // cascade sees the complete set of bundled ids.
            if (exception == null) {
                CompletableFuture.runAsync(modManager::scanBundledTrees, Schedulers.io())
                        .whenCompleteAsync((r, ex) -> {
                            if (this.modManager != modManager) // instance switched — a newer load governs
                                return;
                            if (ex != null)
                                LOG.warning("Failed to scan Jar-in-Jar trees", ex);
                            else
                                bundledScanGeneration.set(bundledScanGeneration.get() + 1);
                            // Ready even on failure: with no bundled data the cascade is as complete as
                            // it will get; keeping the ops disabled forever would be worse.
                            bundledScanReady.set(true);
                        }, Schedulers.javafx());
            } else {
                bundledScanReady.set(true); // load failed — nothing to scan, don't leave ops disabled
            }
        }, Schedulers.javafx());
    }

    /// Bumped when the background Jar-in-Jar deep scan completes; the skin listens to rebuild any open
    /// nested panels with the newly-resolved nested mod metadata.
    public IntegerProperty bundledScanGenerationProperty() {
        return bundledScanGeneration;
    }

    /// False while the background bundled-id scan is still running after a (re)load; true once it has
    /// finished (or there was nothing to scan). The skin disables the dependency-changing operations
    /// (disable / remove) while false, so a cascade never runs against an incomplete bundled-id set.
    public BooleanProperty bundledScanReadyProperty() {
        return bundledScanReady;
    }

    /// The instance's Minecraft version (resolved once at load, the same value used elsewhere on this
    /// page), or null if detection failed. Used to highlight which copy of a multi-version Jar-in-Jar
    /// wrapper matches this instance.
    public String getGameVersion() {
        return gameVersion;
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
        if (analyzer.has(LibraryAnalyzer.LibraryType.NEO_FORGE) && (modManager.hasMod("connector", ModLoaderType.NEO_FORGE) || modManager.hasMod("connectormod", ModLoaderType.NEO_FORGE))
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
