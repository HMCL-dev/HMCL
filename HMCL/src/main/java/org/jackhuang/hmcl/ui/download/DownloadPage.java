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
package org.jackhuang.hmcl.ui.download;

import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.scene.layout.BorderPane;
import org.jackhuang.hmcl.mod.curse.CurseAddon;
import org.jackhuang.hmcl.mod.curse.CurseModManager;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.setting.Profiles;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskExecutor;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.WeakListenerHolder;
import org.jackhuang.hmcl.ui.animation.ContainerAnimations;
import org.jackhuang.hmcl.ui.animation.TransitionPane;
import org.jackhuang.hmcl.ui.construct.AdvancedListBox;
import org.jackhuang.hmcl.ui.construct.TabHeader;
import org.jackhuang.hmcl.ui.construct.TaskExecutorDialogPane;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.ui.versions.ModDownloadListPage;
import org.jackhuang.hmcl.ui.versions.VersionPage;
import org.jackhuang.hmcl.ui.versions.Versions;
import org.jackhuang.hmcl.util.io.NetworkUtils;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

import static org.jackhuang.hmcl.ui.FXUtils.runInFX;
import static org.jackhuang.hmcl.ui.versions.VersionPage.wrap;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class DownloadPage extends BorderPane implements DecoratorPage {
    private final ReadOnlyObjectWrapper<DecoratorPage.State> state = new ReadOnlyObjectWrapper<>(DecoratorPage.State.fromTitle(i18n("download"), -1));
    private final TabHeader tab;
    private final TabHeader.Tab<ModDownloadListPage> modTab = new TabHeader.Tab<>("modTab");
    private final TabHeader.Tab<ModDownloadListPage> modpackTab = new TabHeader.Tab<>("modpackTab");
    private final TabHeader.Tab<ModDownloadListPage> resourcePackTab = new TabHeader.Tab<>("resourcePackTab");
    private final TabHeader.Tab<ModDownloadListPage> customizationTab = new TabHeader.Tab<>("customizationTab");
    private final TabHeader.Tab<ModDownloadListPage> worldTab = new TabHeader.Tab<>("worldTab");
    private final TransitionPane transitionPane = new TransitionPane();

    private WeakListenerHolder listenerHolder;

    public DownloadPage() {
        modpackTab.setNodeSupplier(() -> new ModDownloadListPage(CurseModManager.SECTION_MODPACK, Versions::downloadModpackImpl));
        modTab.setNodeSupplier(() -> new ModDownloadListPage(CurseModManager.SECTION_MOD, (profile, version, file) -> download(profile, version, file, "mods")));
        resourcePackTab.setNodeSupplier(() -> new ModDownloadListPage(CurseModManager.SECTION_RESOURCE_PACK, (profile, version, file) -> download(profile, version, file, "resourcepacks")));
//        customizationTab.setNodeSupplier(() -> new ModDownloadListPage(CurseModManager.SECTION_CUSTOMIZATION, this::download));
        worldTab.setNodeSupplier(() -> new ModDownloadListPage(CurseModManager.SECTION_WORLD));
        tab = new TabHeader(modpackTab, modTab, resourcePackTab, worldTab);

        Profiles.registerVersionsListener(this::loadVersions);

        tab.getSelectionModel().select(modTab);
        FXUtils.onChangeAndOperate(tab.getSelectionModel().selectedItemProperty(), newValue -> {
            if (newValue.initializeIfNeeded()) {
                if (newValue.getNode() instanceof VersionPage.VersionLoadable) {
                    ((VersionPage.VersionLoadable) newValue.getNode()).loadVersion(Profiles.getSelectedProfile(), null);
                }
            }
            transitionPane.setContent(newValue.getNode(), ContainerAnimations.FADE.getAnimationProducer());
        });

        {
            AdvancedListBox sideBar = new AdvancedListBox()
                    .addNavigationDrawerItem(item -> {
                        item.setTitle(i18n("install.new_game"));
                        item.setLeftGraphic(wrap(SVG.gamepad(null, 20, 20)));
                        item.setOnAction(e -> Versions.addNewGame());
                    })
                    .startCategory(i18n("download"))
                    .addNavigationDrawerItem(item -> {
                        item.setTitle(i18n("mods"));
                        item.setLeftGraphic(wrap(SVG.puzzle(null, 20, 20)));
                        item.activeProperty().bind(tab.getSelectionModel().selectedItemProperty().isEqualTo(modTab));
                        item.setOnAction(e -> tab.getSelectionModel().select(modTab));
                    })
                    .addNavigationDrawerItem(settingsItem -> {
                        settingsItem.setTitle(i18n("modpack"));
                        settingsItem.setLeftGraphic(wrap(SVG.pack(null, 20, 20)));
                        settingsItem.activeProperty().bind(tab.getSelectionModel().selectedItemProperty().isEqualTo(modpackTab));
                        settingsItem.setOnAction(e -> tab.getSelectionModel().select(modpackTab));
                    })
                    .addNavigationDrawerItem(item -> {
                        item.setTitle(i18n("resourcepack"));
                        item.setLeftGraphic(wrap(SVG.textureBox(null, 20, 20)));
                        item.activeProperty().bind(tab.getSelectionModel().selectedItemProperty().isEqualTo(resourcePackTab));
                        item.setOnAction(e -> tab.getSelectionModel().select(resourcePackTab));
                    })
//                    .addNavigationDrawerItem(item -> {
//                        item.setTitle(i18n("download.curseforge.customization"));
//                        item.setLeftGraphic(wrap(SVG.script(null, 20, 20)));
//                        item.activeProperty().bind(tab.getSelectionModel().selectedItemProperty().isEqualTo(customizationTab));
//                        item.setOnAction(e -> tab.getSelectionModel().select(customizationTab));
//                    })
                    .addNavigationDrawerItem(item -> {
                        item.setTitle(i18n("world"));
                        item.setLeftGraphic(wrap(SVG.earth(null, 20, 20)));
                        item.activeProperty().bind(tab.getSelectionModel().selectedItemProperty().isEqualTo(worldTab));
                        item.setOnAction(e -> tab.getSelectionModel().select(worldTab));
                    });
            FXUtils.setLimitWidth(sideBar, 200);
            setLeft(sideBar);
        }

        setCenter(transitionPane);
    }

    private void download(Profile profile, @Nullable String version, CurseAddon.LatestFile file, String subdirectoryName) {
        if (version == null) version = profile.getSelectedVersion();

        Path runDirectory = profile.getRepository().hasVersion(version) ? profile.getRepository().getRunDirectory(version).toPath() : profile.getRepository().getBaseDirectory().toPath();
        Path dest = runDirectory.resolve(subdirectoryName).resolve(file.getFileName());

        TaskExecutorDialogPane downloadingPane = new TaskExecutorDialogPane(it -> {
        });

        TaskExecutor executor = Task.composeAsync(() -> {
            FileDownloadTask task = new FileDownloadTask(NetworkUtils.toURL(file.getDownloadUrl()), dest.toFile());
            task.setName(file.getDisplayName());
            return task;
        }).executor(false);

        downloadingPane.setExecutor(executor, true);
        Controllers.dialog(downloadingPane);
        executor.start();
    }

    private void loadVersions(Profile profile) {
        WeakListenerHolder listenerHolder = new WeakListenerHolder();
        runInFX(() -> {
            if (profile == Profiles.getSelectedProfile()) {
                if (modTab.isInitialized()) {
                    modTab.getNode().loadVersion(profile, null);
                }
                if (modpackTab.isInitialized()) {
                    modpackTab.getNode().loadVersion(profile, null);
                }
                if (resourcePackTab.isInitialized()) {
                    resourcePackTab.getNode().loadVersion(profile, null);
                }
                if (customizationTab.isInitialized()) {
                    customizationTab.getNode().loadVersion(profile, null);
                }
                if (worldTab.isInitialized()) {
                    worldTab.getNode().loadVersion(profile, null);
                }
            }
        });
    }

    @Override
    public ReadOnlyObjectProperty<State> stateProperty() {
        return state.getReadOnlyProperty();
    }
}
