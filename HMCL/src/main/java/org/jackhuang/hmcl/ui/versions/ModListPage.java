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

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.ObservableList;
import javafx.scene.control.Skin;
import javafx.stage.FileChooser;
import org.jackhuang.hmcl.download.LibraryAnalyzer;
import org.jackhuang.hmcl.mod.LocalModFile;
import org.jackhuang.hmcl.mod.ModManager;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.ListPageBase;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane;
import org.jackhuang.hmcl.ui.construct.PageAware;
import org.jackhuang.hmcl.util.Logging;
import org.jackhuang.hmcl.util.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.stream.Collectors;

import static org.jackhuang.hmcl.ui.FXUtils.runInFX;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public final class ModListPage extends ListPageBase<ModListPageSkin.ModInfoObject> implements VersionPage.VersionLoadable, PageAware {
    private final BooleanProperty modded = new SimpleBooleanProperty(this, "modded", false);

    private ModManager modManager;
    private LibraryAnalyzer libraryAnalyzer;
    private Profile profile;
    private String versionId;

    public ModListPage() {
        FXUtils.applyDragListener(this, it -> Arrays.asList("jar", "zip", "litemod").contains(FileUtils.getExtension(it)), mods -> {
            mods.forEach(it -> {
                try {
                    modManager.addMod(it);
                } catch (IOException | IllegalArgumentException e) {
                    Logging.LOG.log(Level.WARNING, "Unable to parse mod file " + it, e);
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
        this.versionId = id;

        libraryAnalyzer = LibraryAnalyzer.analyze(profile.getRepository().getResolvedPreservingPatchesVersion(id));
        modded.set(libraryAnalyzer.hasModLoader());
        loadMods(profile.getRepository().getModManager(id));
    }

    private CompletableFuture<?> loadMods(ModManager modManager) {
        this.modManager = modManager;
        return CompletableFuture.supplyAsync(() -> {
            try {
                synchronized (ModListPage.this) {
                    runInFX(() -> loadingProperty().set(true));
                    modManager.refreshMods();
                    return new LinkedList<>(modManager.getMods());
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }, Schedulers.defaultScheduler()).whenCompleteAsync((list, exception) -> {
            loadingProperty().set(false);
            if (exception == null)
                itemsProperty().setAll(list.stream().map(ModListPageSkin.ModInfoObject::new).sorted().collect(Collectors.toList()));
            else
                getProperties().remove(ModListPage.class);

            // https://github.com/huanghongxun/HMCL/issues/938
            System.gc();
        }, Platform::runLater);
    }

    public void add() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(i18n("mods.choose_mod"));
        chooser.getExtensionFilters().setAll(new FileChooser.ExtensionFilter(i18n("extension.mod"), "*.jar", "*.zip", "*.litemod"));
        List<File> res = chooser.showOpenMultipleDialog(Controllers.getStage());

        // It's guaranteed that succeeded and failed are thread safe here.
        List<String> succeeded = new LinkedList<>();
        List<String> failed = new LinkedList<>();
        if (res == null) return;
        Task.runAsync(() -> {
            for (File file : res) {
                try {
                    modManager.addMod(file);
                    succeeded.add(file.getName());
                } catch (Exception e) {
                    Logging.LOG.log(Level.WARNING, "Unable to add mod " + file, e);
                    failed.add(file.getName());

                    // Actually addMod will not throw exceptions because FileChooser has already filtered files.
                }
            }
        }).withRunAsync(Schedulers.javafx(), () -> {
            List<String> prompt = new LinkedList<>();
            if (!succeeded.isEmpty())
                prompt.add(i18n("mods.add.success", String.join(", ", succeeded)));
            if (!failed.isEmpty())
                prompt.add(i18n("mods.add.failed", String.join(", ", failed)));
            Controllers.dialog(String.join("\n", prompt), i18n("mods.add"));
            loadMods(modManager);
        }).start();
    }

    public void removeSelected(ObservableList<ModListPageSkin.ModInfoObject> selectedItems) {
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

    public void enableSelected(ObservableList<ModListPageSkin.ModInfoObject> selectedItems) {
        selectedItems.stream()
                .filter(Objects::nonNull)
                .map(ModListPageSkin.ModInfoObject::getModInfo)
                .forEach(info -> info.setActive(true));
    }

    public void disableSelected(ObservableList<ModListPageSkin.ModInfoObject> selectedItems) {
        selectedItems.stream()
                .filter(Objects::nonNull)
                .map(ModListPageSkin.ModInfoObject::getModInfo)
                .forEach(info -> info.setActive(false));
    }

    public void openModFolder() {
        FXUtils.openFolder(new File(profile.getRepository().getRunDirectory(versionId), "mods"));
    }

    public void checkUpdates() {
        Controllers.taskDialog(
                Task.composeAsync(() -> {
                            Optional<String> gameVersion = profile.getRepository().getGameVersion(versionId);
                            if (gameVersion.isPresent()) {
                                return new ModCheckUpdatesTask(gameVersion.get(), modManager.getMods());
                            }
                            return null;
                        })
                        .whenComplete(Schedulers.javafx(), (result, exception) -> {
                            if (exception != null) {
                                Controllers.dialog("Failed to check updates", "failed", MessageDialogPane.MessageType.ERROR);
                            } else {
                                Controllers.navigate(new ModUpdatesPage(modManager, result));
                            }
                        })
                        .withStagesHint(Collections.singletonList("mods.check_updates"))
                , i18n("update.checking"), pane -> {
                });
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
}
