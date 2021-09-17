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

import com.jfoenix.controls.JFXButton;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.scene.Node;
import javafx.scene.layout.BorderPane;
import org.jackhuang.hmcl.download.*;
import org.jackhuang.hmcl.download.game.GameRemoteVersion;
import org.jackhuang.hmcl.mod.DownloadManager;
import org.jackhuang.hmcl.mod.curse.CurseModManager;
import org.jackhuang.hmcl.setting.DownloadProviders;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.setting.Profiles;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskExecutor;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.WeakListenerHolder;
import org.jackhuang.hmcl.ui.animation.ContainerAnimations;
import org.jackhuang.hmcl.ui.animation.TransitionPane;
import org.jackhuang.hmcl.ui.construct.AdvancedListBox;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane;
import org.jackhuang.hmcl.ui.construct.TabHeader;
import org.jackhuang.hmcl.ui.construct.TaskExecutorDialogPane;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.ui.versions.DownloadListPage;
import org.jackhuang.hmcl.ui.versions.ModDownloadListPage;
import org.jackhuang.hmcl.ui.versions.VersionPage;
import org.jackhuang.hmcl.ui.versions.Versions;
import org.jackhuang.hmcl.ui.wizard.Navigation;
import org.jackhuang.hmcl.ui.wizard.WizardController;
import org.jackhuang.hmcl.ui.wizard.WizardProvider;
import org.jackhuang.hmcl.util.io.NetworkUtils;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.jackhuang.hmcl.ui.FXUtils.runInFX;
import static org.jackhuang.hmcl.ui.versions.VersionPage.wrap;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class DownloadPage extends BorderPane implements DecoratorPage {
    private final ReadOnlyObjectWrapper<DecoratorPage.State> state = new ReadOnlyObjectWrapper<>(DecoratorPage.State.fromTitle(i18n("download"), -1));
    private final TabHeader tab;
    private final TabHeader.Tab<VersionsPage> newGameTab = new TabHeader.Tab<>("newGameTab");
    private final TabHeader.Tab<DownloadListPage> modTab = new TabHeader.Tab<>("modTab");
    private final TabHeader.Tab<DownloadListPage> modpackTab = new TabHeader.Tab<>("modpackTab");
    private final TabHeader.Tab<DownloadListPage> resourcePackTab = new TabHeader.Tab<>("resourcePackTab");
    private final TabHeader.Tab<DownloadListPage> customizationTab = new TabHeader.Tab<>("customizationTab");
    private final TabHeader.Tab<DownloadListPage> worldTab = new TabHeader.Tab<>("worldTab");
    private final TransitionPane transitionPane = new TransitionPane();
    private final DownloadNavigator versionPageNavigator = new DownloadNavigator();

    private WeakListenerHolder listenerHolder;

    public DownloadPage() {
        newGameTab.setNodeSupplier(() -> new VersionsPage(versionPageNavigator, i18n("install.installer.choose", i18n("install.installer.game")), "", DownloadProviders.getDownloadProvider(),
                "game", versionPageNavigator::onGameSelected));
        modpackTab.setNodeSupplier(() -> {
            DownloadListPage page = new DownloadListPage(CurseModManager.SECTION_MODPACK, Versions::downloadModpackImpl);

            JFXButton installLocalModpackButton = new JFXButton(i18n("install.modpack"));
            installLocalModpackButton.setButtonType(JFXButton.ButtonType.RAISED);
            installLocalModpackButton.getStyleClass().add("jfx-button-raised");
            installLocalModpackButton.setOnAction(e -> Versions.importModpack());

            page.getActions().add(installLocalModpackButton);
            return page;
        });
        modTab.setNodeSupplier(() -> new ModDownloadListPage(CurseModManager.SECTION_MOD, (profile, version, file) -> download(profile, version, file, "mods"), true));
        resourcePackTab.setNodeSupplier(() -> new DownloadListPage(CurseModManager.SECTION_RESOURCE_PACK, (profile, version, file) -> download(profile, version, file, "resourcepacks")));
//        customizationTab.setNodeSupplier(() -> new ModDownloadListPage(CurseModManager.SECTION_CUSTOMIZATION, this::download));
        worldTab.setNodeSupplier(() -> new DownloadListPage(CurseModManager.SECTION_WORLD));
        tab = new TabHeader(newGameTab, modpackTab, modTab, resourcePackTab, worldTab);

        Profiles.registerVersionsListener(this::loadVersions);

        tab.getSelectionModel().select(newGameTab);
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
                        item.setTitle(i18n("game"));
                        item.setLeftGraphic(wrap(SVG::gamepad));
                        item.activeProperty().bind(tab.getSelectionModel().selectedItemProperty().isEqualTo(newGameTab));
                        item.setOnAction(e -> tab.getSelectionModel().select(newGameTab));
                    })
                    .addNavigationDrawerItem(item -> {
                        item.setTitle(i18n("mods"));
                        item.setLeftGraphic(wrap(SVG::puzzle));
                        item.activeProperty().bind(tab.getSelectionModel().selectedItemProperty().isEqualTo(modTab));
                        item.setOnAction(e -> tab.getSelectionModel().select(modTab));
                    })
                    .addNavigationDrawerItem(settingsItem -> {
                        settingsItem.setTitle(i18n("modpack"));
                        settingsItem.setLeftGraphic(wrap(SVG::pack));
                        settingsItem.activeProperty().bind(tab.getSelectionModel().selectedItemProperty().isEqualTo(modpackTab));
                        settingsItem.setOnAction(e -> tab.getSelectionModel().select(modpackTab));
                    })
                    .addNavigationDrawerItem(item -> {
                        item.setTitle(i18n("resourcepack"));
                        item.setLeftGraphic(wrap(SVG::textureBox));
                        item.activeProperty().bind(tab.getSelectionModel().selectedItemProperty().isEqualTo(resourcePackTab));
                        item.setOnAction(e -> tab.getSelectionModel().select(resourcePackTab));
                    })
//                    .addNavigationDrawerItem(item -> {
//                        item.setTitle(i18n("download.curseforge.customization"));
//                        item.setLeftGraphic(wrap(SVG::script));
//                        item.activeProperty().bind(tab.getSelectionModel().selectedItemProperty().isEqualTo(customizationTab));
//                        item.setOnAction(e -> tab.getSelectionModel().select(customizationTab));
//                    })
                    .addNavigationDrawerItem(item -> {
                        item.setTitle(i18n("world"));
                        item.setLeftGraphic(wrap(SVG::earth));
                        item.activeProperty().bind(tab.getSelectionModel().selectedItemProperty().isEqualTo(worldTab));
                        item.setOnAction(e -> tab.getSelectionModel().select(worldTab));
                    });
            FXUtils.setLimitWidth(sideBar, 200);
            setLeft(sideBar);
        }

        setCenter(transitionPane);
    }

    private void download(Profile profile, @Nullable String version, DownloadManager.Version file, String subdirectoryName) {
        if (version == null) version = profile.getSelectedVersion();

        Path runDirectory = profile.getRepository().hasVersion(version) ? profile.getRepository().getRunDirectory(version).toPath() : profile.getRepository().getBaseDirectory().toPath();
        Path dest = runDirectory.resolve(subdirectoryName).resolve(file.getFile().getFilename());

        TaskExecutorDialogPane downloadingPane = new TaskExecutorDialogPane(it -> {
        });

        TaskExecutor executor = Task.composeAsync(() -> {
            FileDownloadTask task = new FileDownloadTask(NetworkUtils.toURL(file.getFile().getUrl()), dest.toFile());
            task.setName(file.getName());
            return task;
        }).whenComplete(exception -> {
            if (exception != null) {
                Controllers.dialog(DownloadProviders.localizeErrorMessage(exception), i18n("install.failed.downloading"), MessageDialogPane.MessageType.ERROR);
            } else {
                Controllers.showToast(i18n("install.success"));
            }
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

    private class DownloadNavigator implements Navigation {
        private final Map<String, Object> settings = new HashMap<>();

        @Override
        public void onStart() {

        }

        @Override
        public void onNext() {

        }

        @Override
        public void onPrev(boolean cleanUp) {
        }

        @Override
        public boolean canPrev() {
            return false;
        }

        @Override
        public void onFinish() {

        }

        @Override
        public void onEnd() {

        }

        @Override
        public void onCancel() {

        }

        @Override
        public Map<String, Object> getSettings() {
            return settings;
        }

        public void onGameSelected() {
            Profile profile = Profiles.getSelectedProfile();
            if (profile.getRepository().isLoaded()) {
                Controllers.getDecorator().startWizard(new VanillaInstallWizardProvider(profile, (GameRemoteVersion) settings.get("game")), i18n("install.new_game"));
            }
        }

    }

    private static class VanillaInstallWizardProvider implements WizardProvider {
        private final Profile profile;
        private final DefaultDependencyManager dependencyManager;
        private final DownloadProvider downloadProvider;
        private final GameRemoteVersion gameVersion;

        public VanillaInstallWizardProvider(Profile profile, GameRemoteVersion gameVersion) {
            this.profile = profile;
            this.gameVersion = gameVersion;
            this.downloadProvider = DownloadProviders.getDownloadProvider();
            this.dependencyManager = profile.getDependency(downloadProvider);
        }

        @Override
        public void start(Map<String, Object> settings) {
            settings.put(PROFILE, profile);
            settings.put(LibraryAnalyzer.LibraryType.MINECRAFT.getPatchId(), gameVersion);
        }

        private Task<Void> finishVersionDownloadingAsync(Map<String, Object> settings) {
            GameBuilder builder = dependencyManager.gameBuilder();

            String name = (String) settings.get("name");
            builder.name(name);
            builder.gameVersion(((RemoteVersion) settings.get(LibraryAnalyzer.LibraryType.MINECRAFT.getPatchId())).getGameVersion());

            for (Map.Entry<String, Object> entry : settings.entrySet())
                if (!LibraryAnalyzer.LibraryType.MINECRAFT.getPatchId().equals(entry.getKey()) && entry.getValue() instanceof RemoteVersion)
                    builder.version((RemoteVersion) entry.getValue());

            return builder.buildAsync().whenComplete(any -> profile.getRepository().refreshVersions())
                    .thenRunAsync(Schedulers.javafx(), () -> profile.setSelectedVersion(name));
        }

        @Override
        public Object finish(Map<String, Object> settings) {
            settings.put("title", i18n("install.new_game"));
            settings.put("success_message", i18n("install.success"));
            settings.put("failure_callback", (FailureCallback) (settings1, exception, next) -> UpdateInstallerWizardProvider.alertFailureMessage(exception, next));

            return finishVersionDownloadingAsync(settings);
        }

        @Override
        public Node createPage(WizardController controller, int step, Map<String, Object> settings) {
            switch (step) {
                case 0:
                    return new InstallersPage(controller, profile.getRepository(), ((RemoteVersion) controller.getSettings().get("game")).getGameVersion(), downloadProvider);
                default:
                    throw new IllegalStateException("error step " + step + ", settings: " + settings + ", pages: " + controller.getPages());
            }
        }

        @Override
        public boolean cancel() {
            return true;
        }

        public static final String PROFILE = "PROFILE";
    }
}
