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
import javafx.collections.ObservableList;
import javafx.scene.control.Skin;
import javafx.stage.FileChooser;
import org.jackhuang.hmcl.game.*;
import org.jackhuang.hmcl.addon.mod.LocalModFile;
import org.jackhuang.hmcl.addon.mod.ModGameVersionCheck;
import org.jackhuang.hmcl.addon.mod.ModLoaderType;
import org.jackhuang.hmcl.addon.mod.ModManager;
import org.jackhuang.hmcl.setting.DownloadProviders;
import org.jackhuang.hmcl.setting.GameDirectory;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.ListPageBase;
import org.jackhuang.hmcl.ui.WeakListenerHolder;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane;
import org.jackhuang.hmcl.ui.construct.PageAware;
import org.jackhuang.hmcl.util.TaskCancellationAction;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.*;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReentrantLock;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class ModListPage extends ListPageBase<ModListPageSkin.ModInfoObject> implements PageAware {
    private final ReentrantLock lock = new ReentrantLock();
    private final WeakListenerHolder listenerHolder = new WeakListenerHolder();

    private ModManager modManager;
    private @Nullable HMCLGameInstance gameInstance;
    private String gameVersion;

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
                return modManager.getLocalFiles().stream().map(ModListPageSkin.ModInfoObject::new).toList();
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

    /// Checks whether a set of mods targets the game version of the current instance and guides the user
    /// through the mismatches.
    ///
    /// The division of labour with [#checkUpdates]: that method looks for newer builds within the same
    /// game version and requires the candidate to be published later than the local file, while this one
    /// asks whether the game versions declared by the local file cover the instance at all. Publication
    /// time is never compared here, so instances running an older game version than their mods are
    /// covered too. See [ModGameVersionCheck] for the selection rules.
    ///
    /// Modpack instances get the same `mods.update_modpack_mod.warning` confirmation as regular updates,
    /// since swapping mods across game versions is even more likely to break a modpack.
    ///
    /// @param mods the mods to check, usually every row of the list or the user's selection
    public void checkGameVersion(Collection<LocalModFile> mods) {
        Objects.requireNonNull(mods);
        if (isLoading() || gameInstance == null) {
            return;
        }

        HMCLGameInstance gameInstance = this.gameInstance;
        ModManager modManager = this.modManager;
        // 提前取出版本字符串，保证提示文案与实际用于检查的版本是同一个值
        String targetGameVersion = gameInstance.getVersion().toString();
        Runnable action = () -> Controllers.taskDialog(Task
                        .<List<ModGameVersionCheck>>composeAsync(() -> {
                            GameVersionNumber version = gameInstance.getVersion();
                            return version != GameVersionNumber.unknown()
                                    ? new ModGameVersionCheckTask(
                                            DownloadProviders.getDownloadProvider(), version.toString(), mods)
                                    : null;
                        })
                        .whenComplete(Schedulers.javafx(), (result, exception) -> {
                            if (exception instanceof CancellationException) return;
                            if (exception != null || result == null) {
                                Controllers.dialog(i18n("mods.check_game_version.failed_check"),
                                        i18n("message.failed"), MessageDialogPane.MessageType.ERROR);
                            } else if (result.isEmpty()) {
                                Controllers.dialog(i18n("mods.check_game_version.empty", targetGameVersion));
                            } else {
                                promptMigration(modManager, targetGameVersion, result);
                            }
                        })
                        .withStagesHints(ModGameVersionCheckTask.STAGE),
                i18n("mods.check_game_version"), TaskCancellationAction.NORMAL);

        if (gameInstance.isModpack()) {
            Controllers.confirm(
                    i18n("mods.update_modpack_mod.warning"), null,
                    MessageDialogPane.MessageType.WARNING,
                    action, null);
        } else {
            action.run();
        }
    }

    /// Asks the user for confirmation and navigates to the migration page once they agree.
    ///
    /// The dialog reports how many mods can be replaced by a compatible build and how many can only be
    /// disabled, so the user knows what is about to happen. [#refresh()] is passed as the completion
    /// callback because the old files get renamed and the new ones have just landed; without a refresh
    /// the list would still show the pre-migration state.
    ///
    /// @param modManager        the mod manager of the target instance
    /// @param targetGameVersion the game version of the target instance, used only in the message
    /// @param checks            the check results needing user action, expected to be non-empty
    private void promptMigration(ModManager modManager, String targetGameVersion,
                                 List<ModGameVersionCheck> checks) {
        long replaceable = checks.stream()
                .filter(check -> check.status() == ModGameVersionCheck.Status.REPLACEABLE)
                .count();

        Controllers.confirm(
                i18n("mods.check_game_version.prompt",
                        checks.size(), targetGameVersion, replaceable, checks.size() - replaceable),
                i18n("mods.check_game_version"),
                MessageDialogPane.MessageType.WARNING,
                () -> Controllers.navigateForward(
                        new ModGameVersionMigrationPage(modManager, checks, this::refresh)),
                null);
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
}
