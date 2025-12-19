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
import com.jfoenix.controls.JFXComboBox;
import com.jfoenix.controls.JFXDialogLayout;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.util.StringConverter;
import org.jackhuang.hmcl.download.*;
import org.jackhuang.hmcl.download.game.GameRemoteVersion;
import org.jackhuang.hmcl.game.World;
import org.jackhuang.hmcl.mod.RemoteMod;
import org.jackhuang.hmcl.mod.curse.CurseForgeRemoteModRepository;
import org.jackhuang.hmcl.mod.modrinth.ModrinthRemoteModRepository;
import org.jackhuang.hmcl.setting.DownloadProviders;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.setting.Profiles;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.WeakListenerHolder;
import org.jackhuang.hmcl.ui.animation.TransitionPane;
import org.jackhuang.hmcl.ui.construct.AdvancedListBox;
import org.jackhuang.hmcl.ui.construct.DialogCloseEvent;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane;
import org.jackhuang.hmcl.ui.construct.TabHeader;
import org.jackhuang.hmcl.ui.decorator.DecoratorAnimatedPage;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.ui.versions.DownloadListPage;
import org.jackhuang.hmcl.ui.versions.HMCLLocalizedDownloadListPage;
import org.jackhuang.hmcl.ui.versions.VersionPage;
import org.jackhuang.hmcl.ui.versions.Versions;
import org.jackhuang.hmcl.ui.wizard.Navigation;
import org.jackhuang.hmcl.ui.wizard.WizardController;
import org.jackhuang.hmcl.ui.wizard.WizardProvider;
import org.jackhuang.hmcl.util.SettingsMap;
import org.jackhuang.hmcl.util.TaskCancellationAction;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.CancellationException;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.jackhuang.hmcl.ui.FXUtils.runInFX;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class DownloadPage extends DecoratorAnimatedPage implements DecoratorPage {
    private final ReadOnlyObjectWrapper<DecoratorPage.State> state = new ReadOnlyObjectWrapper<>(DecoratorPage.State.fromTitle(i18n("download"), -1));
    private final TabHeader tab;
    private final TabHeader.Tab<VersionsPage> newGameTab = new TabHeader.Tab<>("newGameTab");
    private final TabHeader.Tab<DownloadListPage> modTab = new TabHeader.Tab<>("modTab");
    private final TabHeader.Tab<DownloadListPage> modpackTab = new TabHeader.Tab<>("modpackTab");
    private final TabHeader.Tab<DownloadListPage> resourcePackTab = new TabHeader.Tab<>("resourcePackTab");
    private final TabHeader.Tab<DownloadListPage> shaderTab = new TabHeader.Tab<>("shaderTab");
    private final TabHeader.Tab<DownloadListPage> dataPackTab = new TabHeader.Tab<>("dataPackTab");
    private final TabHeader.Tab<DownloadListPage> worldTab = new TabHeader.Tab<>("worldTab");
    private final TransitionPane transitionPane = new TransitionPane();
    private final DownloadNavigator versionPageNavigator = new DownloadNavigator();

    private WeakListenerHolder listenerHolder;

    public DownloadPage() {
        this(null);
    }

    public DownloadPage(String uploadVersion) {
        newGameTab.setNodeSupplier(loadVersionFor(() -> new VersionsPage(versionPageNavigator, i18n("install.installer.choose", i18n("install.installer.game")), "", DownloadProviders.getDownloadProvider(),
                "game", versionPageNavigator::onGameSelected)));
        modpackTab.setNodeSupplier(loadVersionFor(() -> {
            DownloadListPage page = HMCLLocalizedDownloadListPage.ofModPack((profile, __, file) -> {
                Versions.downloadModpackImpl(profile, uploadVersion, file);
            }, false);

            JFXButton installLocalModpackButton = FXUtils.newRaisedButton(i18n("install.modpack"));
            installLocalModpackButton.setOnAction(e -> Versions.importModpack());

            page.getActions().add(installLocalModpackButton);
            return page;
        }));
        modTab.setNodeSupplier(loadVersionFor(() -> HMCLLocalizedDownloadListPage.ofMod((profile, version, file) -> download(profile, version, file, "mods"), true)));
        resourcePackTab.setNodeSupplier(loadVersionFor(() -> HMCLLocalizedDownloadListPage.ofResourcePack((profile, version, file) -> download(profile, version, file, "resourcepacks"), true)));
        shaderTab.setNodeSupplier(loadVersionFor(() -> new DownloadListPage(ModrinthRemoteModRepository.SHADER_PACKS, (profile, version, file) -> download(profile, version, file, "shaderpacks"), true)));
        dataPackTab.setNodeSupplier(loadVersionFor(() -> HMCLLocalizedDownloadListPage.ofDataPack((profile, version, file) -> downloadForDatapack(profile, version, file, "datapacks"), true)));
        worldTab.setNodeSupplier(loadVersionFor(() -> new DownloadListPage(CurseForgeRemoteModRepository.WORLDS)));
        tab = new TabHeader(transitionPane, newGameTab, modpackTab, modTab, resourcePackTab, shaderTab, dataPackTab, worldTab);

        Profiles.registerVersionsListener(this::loadVersions);

        tab.select(newGameTab);

        AdvancedListBox sideBar = new AdvancedListBox()
                .startCategory(i18n("download.game").toUpperCase(Locale.ROOT))
                .addNavigationDrawerTab(tab, newGameTab, i18n("game"), SVG.STADIA_CONTROLLER, SVG.STADIA_CONTROLLER_FILL)
                .addNavigationDrawerTab(tab, modpackTab, i18n("modpack"), SVG.PACKAGE2, SVG.PACKAGE2_FILL)
                .startCategory(i18n("download.content").toUpperCase(Locale.ROOT))
                .addNavigationDrawerTab(tab, modTab, i18n("mods"), SVG.EXTENSION, SVG.EXTENSION_FILL)
                .addNavigationDrawerTab(tab, resourcePackTab, i18n("resourcepack"), SVG.TEXTURE)
                .addNavigationDrawerTab(tab, shaderTab, i18n("download.shader"), SVG.WB_SUNNY, SVG.WB_SUNNY_FILL)
                .addNavigationDrawerTab(tab, dataPackTab, i18n("datapack"), SVG.CODE_BLOCKS, SVG.CODE_BLOCKS_FILL)
                .addNavigationDrawerTab(tab, worldTab, i18n("world"), SVG.PUBLIC);
        FXUtils.setLimitWidth(sideBar, 200);
        setLeft(sideBar);

        setCenter(transitionPane);
    }

    private static <T extends Node> Supplier<T> loadVersionFor(Supplier<T> nodeSupplier) {
        return () -> {
            T node = nodeSupplier.get();
            if (node instanceof VersionPage.VersionLoadable) {
                ((VersionPage.VersionLoadable) node).loadVersion(Profiles.getSelectedProfile(), null);
            }
            return node;
        };
    }

    public static void download(Profile profile, @Nullable String version, RemoteMod.Version file, String subdirectoryName) {
        if (version == null) version = profile.getSelectedVersion();

        Path runDirectory = profile.getRepository().hasVersion(version) ? profile.getRepository().getRunDirectory(version) : profile.getRepository().getBaseDirectory();

        Controllers.prompt(i18n("archive.file.name"), (result, resolve, reject) -> {
            if (!FileUtils.isNameValid(result)) {
                reject.accept(i18n("install.new_game.malformed"));
                return;
            }
            Path dest = runDirectory.resolve(subdirectoryName).resolve(result);

            Controllers.taskDialog(Task.composeAsync(() -> {
                var task = new FileDownloadTask(file.getFile().getUrl(), dest);
                task.setName(file.getName());
                return task;
            }).whenComplete(Schedulers.javafx(), exception -> {
                if (exception != null) {
                    if (exception instanceof CancellationException) {
                        Controllers.showToast(i18n("message.cancelled"));
                    } else {
                        Controllers.dialog(DownloadProviders.localizeErrorMessage(exception), i18n("install.failed.downloading"), MessageDialogPane.MessageType.ERROR);
                    }
                } else {
                    Controllers.showToast(i18n("install.success"));
                }
            }), i18n("message.downloading"), TaskCancellationAction.NORMAL);

            resolve.run();
        }, file.getFile().getFilename());

    }

    public static void downloadForDatapack(Profile profile, @Nullable String version, RemoteMod.Version file, String subdirectoryName) {
        if (version == null) version = profile.getSelectedVersion();

        Path runDirectory = profile.getRepository().hasVersion(version) ? profile.getRepository().getRunDirectory(version) : profile.getRepository().getBaseDirectory();

        Controllers.dialog(new WorldChooser(runDirectory, (worldName) -> Controllers.prompt(i18n("archive.file.name"), (result, resolve, reject) -> {
            if (!FileUtils.isNameValid(result)) {
                reject.accept(i18n("install.new_game.malformed"));
                return;
            }
            Path dest = runDirectory.resolve("saves").resolve(worldName).resolve(subdirectoryName).resolve(result);
            System.out.println("dest: " + dest);

            Controllers.taskDialog(Task.composeAsync(() -> {
                var task = new FileDownloadTask(file.getFile().getUrl(), dest);
                task.setName(file.getName());
                return task;
            }).whenComplete(Schedulers.javafx(), exception -> {
                if (exception != null) {
                    if (exception instanceof CancellationException) {
                        Controllers.showToast(i18n("message.cancelled"));
                    } else {
                        Controllers.dialog(DownloadProviders.localizeErrorMessage(exception), i18n("install.failed.downloading"), MessageDialogPane.MessageType.ERROR);
                    }
                } else {
                    Controllers.showToast(i18n("install.success"));
                }
            }), i18n("message.downloading"), TaskCancellationAction.NORMAL);

            resolve.run();
        }, file.getFile().getFilename())));

    }

    private void loadVersions(Profile profile) {
        listenerHolder = new WeakListenerHolder();
        runInFX(() -> {
            if (profile == Profiles.getSelectedProfile()) {
                listenerHolder.add(FXUtils.onWeakChangeAndOperate(profile.selectedVersionProperty(), version -> {
                    if (modTab.isInitialized()) {
                        modTab.getNode().loadVersion(profile, null);
                    }
                    if (modpackTab.isInitialized()) {
                        modpackTab.getNode().loadVersion(profile, null);
                    }
                    if (resourcePackTab.isInitialized()) {
                        resourcePackTab.getNode().loadVersion(profile, null);
                    }
                    if (shaderTab.isInitialized()) {
                        shaderTab.getNode().loadVersion(profile, null);
                    }
                    if (dataPackTab.isInitialized()) {
                        dataPackTab.getNode().loadVersion(profile, null);
                    }
                    if (worldTab.isInitialized()) {
                        worldTab.getNode().loadVersion(profile, null);
                    }
                }));
            }
        });
    }

    @Override
    public ReadOnlyObjectProperty<State> stateProperty() {
        return state.getReadOnlyProperty();
    }

    public void showGameDownloads() {
        tab.select(newGameTab, false);
    }

    public void showModpackDownloads() {
        tab.select(modpackTab, false);
    }

    public void showResourcepackDownloads() {
        tab.select(resourcePackTab, false);
    }

    public DownloadListPage showModDownloads() {
        tab.select(modTab, false);
        return modTab.getNode();
    }

    public void showWorldDownloads() {
        tab.select(worldTab, false);
    }

    public void showDatapackDownloads() {
        tab.select(dataPackTab, false);
    }

    private static final class DownloadNavigator implements Navigation {
        private final SettingsMap settings = new SettingsMap();

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
        public SettingsMap getSettings() {
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
        public void start(SettingsMap settings) {
            settings.put(ModpackPage.PROFILE, profile);
            settings.put(LibraryAnalyzer.LibraryType.MINECRAFT.getPatchId(), gameVersion);
        }

        private Task<Void> finishVersionDownloadingAsync(SettingsMap settings) {
            GameBuilder builder = dependencyManager.gameBuilder();

            String name = (String) settings.get("name");
            builder.name(name);
            builder.gameVersion(((RemoteVersion) settings.get(LibraryAnalyzer.LibraryType.MINECRAFT.getPatchId())).getGameVersion());

            settings.asStringMap().forEach((key, value) -> {
                if (!LibraryAnalyzer.LibraryType.MINECRAFT.getPatchId().equals(key)
                        && value instanceof RemoteVersion remoteVersion)
                    builder.version(remoteVersion);
            });

            return builder.buildAsync().whenComplete(any -> profile.getRepository().refreshVersions())
                    .thenRunAsync(Schedulers.javafx(), () -> profile.setSelectedVersion(name));
        }

        @Override
        public Object finish(SettingsMap settings) {
            settings.put("title", i18n("install.new_game.installation"));
            settings.put("success_message", i18n("install.success"));
            settings.put(FailureCallback.KEY, (settings1, exception, next) -> UpdateInstallerWizardProvider.alertFailureMessage(exception, next));

            return finishVersionDownloadingAsync(settings);
        }

        @Override
        public Node createPage(WizardController controller, int step, SettingsMap settings) {
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
    }

    private static class WorldChooser extends JFXDialogLayout {
        public WorldChooser(Path runDirectory, Consumer<String> consumer) {
            setHeading(new Label(i18n("mods.install.select_world.title", i18n("datapack"))));

            ObservableList<World> worlds = FXCollections.observableArrayList(World.getWorlds(runDirectory.resolve("saves")).toList());
            JFXComboBox<World> worldListComboBox = getWorldJFXComboBox(worlds);

            setBody(worldListComboBox);

            Label notChooseWorld = new Label("");

            JFXButton cancelButton = new JFXButton(i18n("button.cancel"));
            cancelButton.getStyleClass().add("dialog-accept");
            cancelButton.setOnAction(e -> {
                fireEvent(new DialogCloseEvent());
            });

            JFXButton okButton = new JFXButton(i18n("button.ok"));
            okButton.getStyleClass().add("dialog-accept");
            okButton.setOnAction(e -> {

                if (worldListComboBox.getSelectionModel().getSelectedItem() != null) {
                    String world = worldListComboBox.getSelectionModel().getSelectedItem().getFileName();
                    System.out.println("world: " + world);
                    fireEvent(new DialogCloseEvent());
                    consumer.accept(world);
                } else {
                    notChooseWorld.setText(i18n("mods.install.select_world.not_select.hint"));
                }

            });
            setActions(notChooseWorld, okButton, cancelButton);
        }

        private static @NotNull JFXComboBox<World> getWorldJFXComboBox(ObservableList<World> worlds) {
            JFXComboBox<World> worldListComboBox = new JFXComboBox<>();
            worldListComboBox.setItems(worlds);
            System.out.println("worlds: " + worlds);
            worldListComboBox.setConverter(new StringConverter<>() {
                @Override
                public String toString(World object) {
                    if (object == null) {
                        return "";
                    }
                    return object.getWorldName();
                }

                @Override
                public World fromString(String string) {
                    return null;
                }
            });
            return worldListComboBox;
        }
    }
}
