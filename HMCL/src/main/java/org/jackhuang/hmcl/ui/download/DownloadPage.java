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
import org.jackhuang.hmcl.download.*;
import org.jackhuang.hmcl.download.game.GameRemoteVersion;
import org.jackhuang.hmcl.mod.RemoteMod;
import org.jackhuang.hmcl.mod.curse.CurseForgeRemoteModRepository;
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
import org.jackhuang.hmcl.ui.animation.TransitionPane;
import org.jackhuang.hmcl.ui.construct.AdvancedListBox;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane;
import org.jackhuang.hmcl.ui.construct.TabHeader;
import org.jackhuang.hmcl.ui.construct.Validator;
import org.jackhuang.hmcl.ui.decorator.DecoratorAnimatedPage;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.ui.versions.*;
import org.jackhuang.hmcl.ui.wizard.Navigation;
import org.jackhuang.hmcl.ui.wizard.WizardController;
import org.jackhuang.hmcl.ui.wizard.WizardProvider;
import org.jackhuang.hmcl.util.SettingsMap;
import org.jackhuang.hmcl.util.TaskCancellationAction;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.jackhuang.hmcl.ui.FXUtils.runInFX;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public class DownloadPage extends DecoratorAnimatedPage implements DecoratorPage {
    private final ReadOnlyObjectWrapper<DecoratorPage.State> state = new ReadOnlyObjectWrapper<>(DecoratorPage.State.fromTitle(i18n("download"), -1));
    private final TabHeader tab;
    private final TabHeader.Tab<VersionsPage> newGameTab = new TabHeader.Tab<>("newGameTab");
    private final TabHeader.Tab<DownloadListPage> modTab = new TabHeader.Tab<>("modTab");
    private final TabHeader.Tab<DownloadListPage> modpackTab = new TabHeader.Tab<>("modpackTab");
    private final TabHeader.Tab<DownloadListPage> resourcePackTab = new TabHeader.Tab<>("resourcePackTab");
    private final TabHeader.Tab<DownloadListPage> shaderTab = new TabHeader.Tab<>("shaderTab");
    private final TabHeader.Tab<DownloadListPage> worldTab = new TabHeader.Tab<>("worldTab");
    private final TabHeader.Tab<DownloadListPage> downloadListTab = new TabHeader.Tab<>("downloadListTab");
    private final TransitionPane transitionPane = new TransitionPane();
    private final DownloadNavigator versionPageNavigator = new DownloadNavigator();
    private final DownloadTaskList downloadTaskList;

    private WeakListenerHolder listenerHolder;

    public DownloadPage() {
        this(null);
    }

    public DownloadPage(String uploadVersion) {
        this.downloadTaskList = new DownloadTaskList();
        DifferentDownloadTask2OneTask.setActiveDownloadTaskList(downloadTaskList);

        newGameTab.setNodeSupplier(loadVersionFor(() -> new VersionsPage(versionPageNavigator, i18n("install.installer.choose", i18n("install.installer.game")), "", DownloadProviders.getDownloadProvider(),
                "game", versionPageNavigator::onGameSelected)));
        modpackTab.setNodeSupplier(loadVersionFor(() -> {
            DownloadListPage page = HMCLLocalizedDownloadListPage.ofModPack((downloadProvider, profile, __, mod, file) -> {
                Versions.downloadModpackImpl(downloadProvider, profile, uploadVersion, mod, file);
            }, false);

            JFXButton installLocalModpackButton = FXUtils.newRaisedButton(i18n("install.modpack"));
            installLocalModpackButton.setOnAction(e -> Versions.importModpack());

            page.getActions().add(installLocalModpackButton);
            return page;
        }));

        //下面是设置搜索界面的
        modTab.setNodeSupplier(loadVersionFor(() -> HMCLLocalizedDownloadListPage.ofMod((downloadProvider, profile, version, mod, file) -> download(downloadProvider, profile, version, file, "mods"), true)));
        resourcePackTab.setNodeSupplier(loadVersionFor(() -> HMCLLocalizedDownloadListPage.ofResourcePack((downloadProvider, profile, version, mod, file) -> download(downloadProvider, profile, version, file, "resourcepacks"), true)));
        shaderTab.setNodeSupplier(loadVersionFor(() -> HMCLLocalizedDownloadListPage.ofShaderPack((downloadProvider, profile, version, mod, file) -> download(downloadProvider, profile, version, file, "shaderpacks"), true)));
        worldTab.setNodeSupplier(loadVersionFor(() -> new DownloadListPage(CurseForgeRemoteModRepository.WORLDS)));
        tab = new TabHeader(transitionPane, newGameTab, modpackTab, modTab, resourcePackTab, shaderTab, worldTab);
        downloadListTab.setNodeSupplier(() -> downloadTaskList);

        Profiles.registerVersionsListener(this::loadVersions);

        tab.select(newGameTab);

        //这一堆规划了侧边栏的哪个按钮在哪个栏目
        AdvancedListBox sideBar = new AdvancedListBox()
                .startCategory(i18n("download.game").toUpperCase(Locale.ROOT))
                .addNavigationDrawerTab(tab, newGameTab, i18n("game"), SVG.STADIA_CONTROLLER, SVG.STADIA_CONTROLLER_FILL)
                .addNavigationDrawerTab(tab, modpackTab, i18n("modpack"), SVG.PACKAGE2, SVG.PACKAGE2_FILL)

                .startCategory(i18n("download.content").toUpperCase(Locale.ROOT))
                .addNavigationDrawerTab(tab, modTab, i18n("mods"), SVG.EXTENSION, SVG.EXTENSION_FILL)
                .addNavigationDrawerTab(tab, resourcePackTab, i18n("resourcepack"), SVG.TEXTURE)
                .addNavigationDrawerTab(tab, shaderTab, i18n("download.shader"), SVG.WB_SUNNY, SVG.WB_SUNNY_FILL)
                .addNavigationDrawerTab(tab, worldTab, i18n("world"), SVG.PUBLIC)

                .startCategory(i18n("download.others").toUpperCase(Locale.ROOT))
                .addNavigationDrawerTab(tab, downloadListTab, i18n("download.task"), SVG.DOWNLOAD);

        FXUtils.setLimitWidth(sideBar, 200);
        setLeft(sideBar);

        setCenter(transitionPane);
    }

    public DownloadTaskList getDownloadTaskList() {
        return downloadTaskList;
    }

    public void addDownloadTask(TaskExecutor executor, String name) {
        downloadTaskList.addDownloadEntry(executor, name);
    }

    public void addDownloadTask(Task<?> task) {
        addDownloadTask(task.executor(), task.getName());
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

    public static void download(DownloadProvider downloadProvider, Profile profile, @Nullable String version, RemoteMod.Version file, String subdirectoryName) {
        if (version == null) version = profile.getSelectedVersion();

        Path runDirectory = profile.getRepository().hasVersion(version) ? profile.getRepository().getRunDirectory(version) : profile.getRepository().getBaseDirectory();

        Set<String> existingFiles;

        try (var list = Files.list(runDirectory.resolve(subdirectoryName))) {
            existingFiles = list.map(Path::getFileName)
                    .map(Path::toString)
                    .collect(Collectors.toSet());
        } catch (IOException e) {
            LOG.warning("Failed to list files in " + runDirectory.resolve(subdirectoryName), e);
            existingFiles = Set.of();
        }

        Set<String> finalExistingFiles = existingFiles;

        Controllers.prompt(i18n("archive.file.name"), (result, handler) -> {
            Path dest = runDirectory.resolve(subdirectoryName).resolve(result);

            Controllers.taskDialog(Task.composeAsync(() -> {
                var task = new FileDownloadTask(downloadProvider.injectURLWithCandidates(file.getFile().getUrl()), dest);
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
            handler.resolve();
        }, file.getFile().getFilename(), new Validator(i18n("install.new_game.malformed"), FileUtils::isNameValid), new Validator(i18n("profile.already_exists"), (it) -> !finalExistingFiles.contains(it)));

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
}
