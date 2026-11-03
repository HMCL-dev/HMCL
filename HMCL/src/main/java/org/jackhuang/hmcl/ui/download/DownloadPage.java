// SPDX-FileCopyrightText: Copyright (C) 2021 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.download;

import com.jfoenix.controls.JFXButton;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.scene.Node;
import org.jackhuang.hmcl.addon.RemoteAddon;
import org.jackhuang.hmcl.addon.repository.CurseForgeRemoteAddonRepository;
import org.jackhuang.hmcl.download.*;
import org.jackhuang.hmcl.download.game.GameRemoteVersion;
import org.jackhuang.hmcl.game.GameComponentType;
import org.jackhuang.hmcl.game.GameInstanceID;
import org.jackhuang.hmcl.game.HMCLDependencyManager;
import org.jackhuang.hmcl.game.HMCLGameBuilder;
import org.jackhuang.hmcl.game.HMCLGameInstance;
import org.jackhuang.hmcl.game.HMCLGameRepository;
import org.jackhuang.hmcl.setting.DownloadProviders;
import org.jackhuang.hmcl.setting.GameDirectoryManager;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.WeakListenerHolder;
import org.jackhuang.hmcl.ui.animation.TransitionPane;
import org.jackhuang.hmcl.ui.construct.AdvancedListBox;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane;
import org.jackhuang.hmcl.ui.construct.TabController;
import org.jackhuang.hmcl.ui.construct.TabItem;
import org.jackhuang.hmcl.ui.construct.Validator;
import org.jackhuang.hmcl.ui.decorator.DecoratorAnimatedPage;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.ui.instances.DownloadListPage;
import org.jackhuang.hmcl.ui.instances.HMCLLocalizedDownloadListPage;
import org.jackhuang.hmcl.ui.instances.Instances;
import org.jackhuang.hmcl.ui.wizard.Navigation;
import org.jackhuang.hmcl.ui.wizard.WizardController;
import org.jackhuang.hmcl.ui.wizard.WizardProvider;
import org.jackhuang.hmcl.util.FileNameSet;
import org.jackhuang.hmcl.util.SettingsMap;
import org.jackhuang.hmcl.util.TaskCancellationAction;
import org.jackhuang.hmcl.util.i18n.I18n;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.jackhuang.hmcl.ui.FXUtils.runInFX;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public class DownloadPage extends DecoratorAnimatedPage implements DecoratorPage {
    public static final org.jackhuang.hmcl.ui.instances.DownloadPage.DownloadCallback FOR_MOD =
            (downloadProvider, repository, version, mod, file) -> download(downloadProvider, repository, version, file, "mods");
    public static final org.jackhuang.hmcl.ui.instances.DownloadPage.DownloadCallback FOR_RESOURCE_PACK =
            (downloadProvider, repository, version, pack, file) -> download(downloadProvider, repository, version, file, "resourcepacks");
    public static final org.jackhuang.hmcl.ui.instances.DownloadPage.DownloadCallback FOR_SHADER =
            (downloadProvider, repository, version, shader, file) -> download(downloadProvider, repository, version, file, "shaderpacks");

    private final ReadOnlyObjectWrapper<DecoratorPage.State> state = new ReadOnlyObjectWrapper<>(DecoratorPage.State.fromTitle(i18n("download"), -1));
    /// Manages tab selection and content transitions independently of the sidebar.
    private final TabController tabController;
    /// The tab for choosing a game version to install.
    private final TabItem<VersionsPage> newGameTab;
    /// The tab for browsing and importing modpacks.
    private final TabItem<DownloadListPage> modpackTab;
    /// The tab for browsing downloadable mods.
    private final TabItem<DownloadListPage> modTab;
    /// The tab for browsing downloadable resource packs.
    private final TabItem<DownloadListPage> resourcePackTab;
    /// The tab for browsing downloadable shader packs.
    private final TabItem<DownloadListPage> shaderTab;
    /// The tab for browsing downloadable worlds.
    private final TabItem<DownloadListPage> worldTab;
    private final TransitionPane transitionPane = new TransitionPane();
    private final DownloadNavigator versionPageNavigator = new DownloadNavigator();

    private WeakListenerHolder listenerHolder;

    public DownloadPage() {
        this(null);
    }

    public DownloadPage(GameInstanceID uploadInstance) {
        this.newGameTab = new TabItem<>(() -> new VersionsPage(
                versionPageNavigator,
                i18n("install.installer.choose", i18n("install.installer.game")),
                GameVersionNumber.unknown(),
                DownloadProviders.getDownloadProvider(),
                GameComponentType.GAME, versionPageNavigator::onGameSelected));
        this.modpackTab = newDownloadListTab(() -> {
            DownloadListPage page = HMCLLocalizedDownloadListPage.ofModPack((downloadProvider, repository, __, modpack, file) -> {
                Instances.downloadModpackImpl(downloadProvider, repository, uploadInstance, modpack, file);
            }, false);

            JFXButton installLocalModpackButton = FXUtils.newRaisedButton(i18n("install.modpack"));
            installLocalModpackButton.setOnAction(e -> Instances.importModpack());

            page.getActions().add(installLocalModpackButton);
            return page;
        });
        this.modTab = newDownloadListTab(() -> HMCLLocalizedDownloadListPage.ofMod(FOR_MOD, true));
        this.resourcePackTab = newDownloadListTab(() -> HMCLLocalizedDownloadListPage.ofResourcePack(FOR_RESOURCE_PACK, true));
        this.shaderTab = newDownloadListTab(() -> HMCLLocalizedDownloadListPage.ofShaderPack(FOR_SHADER, true));
        this.worldTab = newDownloadListTab(() -> new DownloadListPage(RemoteAddon.Type.WORLD, CurseForgeRemoteAddonRepository.getInstance()));

        this.tabController = new TabController(transitionPane,
                newGameTab,
                modpackTab,
                modTab,
                resourcePackTab,
                shaderTab,
                worldTab
        );

        GameDirectoryManager.registerVersionsListener(this::loadVersions);

        tabController.selectTab(newGameTab);

        AdvancedListBox sideBar = new AdvancedListBox()
                .startCategory(i18n("download.game").toUpperCase(Locale.ROOT))
                .addNavigationDrawerTab(tabController, newGameTab, i18n("game"), SVG.STADIA_CONTROLLER, SVG.STADIA_CONTROLLER_FILL)
                .addNavigationDrawerTab(tabController, modpackTab, i18n("modpack"), SVG.PACKAGE2, SVG.PACKAGE2_FILL)
                .startCategory(i18n("download.content").toUpperCase(Locale.ROOT))
                .addNavigationDrawerTab(tabController, modTab, i18n("mods"), SVG.EXTENSION, SVG.EXTENSION_FILL)
                .addNavigationDrawerTab(tabController, resourcePackTab, i18n("resourcepack"), SVG.TEXTURE)
                .addNavigationDrawerTab(tabController, shaderTab, i18n("download.shader"), SVG.WB_SUNNY, SVG.WB_SUNNY_FILL)
                .addNavigationDrawerTab(tabController, worldTab, i18n("world"), SVG.PUBLIC);
        FXUtils.setLimitWidth(sideBar, 200);
        setLeft(sideBar);

        setCenter(transitionPane);
    }

    private static TabItem<DownloadListPage> newDownloadListTab(Supplier<DownloadListPage> pageSupplier) {
        return new TabItem<>(() -> {
            DownloadListPage page = pageSupplier.get();
            page.loadInstance(HMCLGameInstance.Optional.empty(GameDirectoryManager.getSelectedRepository()));
            return page;
        });
    }

    public static void download(DownloadProvider downloadProvider, HMCLGameRepository repository, @Nullable GameInstanceID instanceId, RemoteAddon.Version file, String subdirectoryName) {
        @Nullable HMCLGameInstance instance = instanceId != null
                ? repository.findInstance(instanceId)
                : repository.getSelectedInstance();
        Path runDirectory = instance != null ? instance.getRunDirectory() : repository.getBaseDirectory();

        var targetPath = runDirectory.resolve(subdirectoryName);

        FileNameSet existingPaths;
        try {
            existingPaths = FileNameSet.list(targetPath, null);
        } catch (Exception e) {
            LOG.warning("Failed to list folders in " + targetPath, e);
            existingPaths = new FileNameSet(false);
        }

        Controllers.prompt(i18n("archive.file.name"), (result, handler) -> {
            Path dest = targetPath.resolve(result);

            Controllers.taskDialog(Task.composeAsync(() -> {
                var task = new FileDownloadTask(downloadProvider.getDownloadCandidates(file.file().url()), dest);
                task.setName(file.name());
                return task;
            }).whenComplete(Schedulers.javafx(), exception -> {
                if (exception != null) {
                    if (!(exception instanceof CancellationException)) {
                        Controllers.dialog(I18n.localizeErrorMessage(exception), i18n("install.failed.downloading"), MessageDialogPane.MessageType.ERROR);
                    }
                } else {
                    Controllers.showToast(i18n("install.success"));
                }
            }), i18n("message.downloading"), TaskCancellationAction.NORMAL);
            handler.resolve();
        }, file.file().filename(), new Validator(i18n("install.new_game.malformed"), FileUtils::isNameValid), new Validator(i18n("game_directory.already_exists"), existingPaths::notContains));

    }

    private void loadVersions(HMCLGameRepository repository) {
        listenerHolder = new WeakListenerHolder();
        runInFX(() -> {
            if (repository.getGameDirectory() == GameDirectoryManager.getSelectedGameDirectory()) {
                listenerHolder.add(FXUtils.onWeakChangeAndOperate(GameDirectoryManager.selectedInstanceProperty(), version -> {
                    if (modTab.isInitialized()) {
                        modTab.getNode().loadInstance(HMCLGameInstance.Optional.empty(repository));
                    }
                    if (modpackTab.isInitialized()) {
                        modpackTab.getNode().loadInstance(HMCLGameInstance.Optional.empty(repository));
                    }
                    if (resourcePackTab.isInitialized()) {
                        resourcePackTab.getNode().loadInstance(HMCLGameInstance.Optional.empty(repository));
                    }
                    if (shaderTab.isInitialized()) {
                        shaderTab.getNode().loadInstance(HMCLGameInstance.Optional.empty(repository));
                    }
                    if (worldTab.isInitialized()) {
                        worldTab.getNode().loadInstance(HMCLGameInstance.Optional.empty(repository));
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
        tabController.selectTab(newGameTab, false);
    }

    public void showModpackDownloads() {
        tabController.selectTab(modpackTab, false);
    }

    public DownloadListPage showResourcePackDownloads() {
        tabController.selectTab(resourcePackTab, false);
        return resourcePackTab.getNode();
    }

    public DownloadListPage showModDownloads() {
        tabController.selectTab(modTab, false);
        return modTab.getNode();
    }

    public void showWorldDownloads() {
        tabController.selectTab(worldTab, false);
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
            HMCLGameRepository repository = GameDirectoryManager.getSelectedRepository();
            if (repository.isLoaded()) {
                Controllers.getDecorator().startWizard(new VanillaInstallWizardProvider(repository, (GameRemoteVersion) settings.get("game")), i18n("install.new_game"));
            }
        }

    }

    private static class VanillaInstallWizardProvider implements WizardProvider {
        private final HMCLGameRepository repository;
        private final HMCLDependencyManager dependencyManager;
        private final DownloadProvider downloadProvider;
        private final GameRemoteVersion gameVersion;

        public VanillaInstallWizardProvider(HMCLGameRepository repository, GameRemoteVersion gameVersion) {
            this.repository = repository;
            this.gameVersion = gameVersion;
            this.downloadProvider = DownloadProviders.getDownloadProvider();
            this.dependencyManager = repository.getDependency(downloadProvider);
        }

        @Override
        public void start(SettingsMap settings) {
            settings.put(ModpackPage.GAME_DIRECTORY, repository.getGameDirectory());
            settings.put(ModpackPage.REPOSITORY, repository);
            settings.put(GameComponentType.GAME.getPatchId(), gameVersion);
        }

        /// Captures the installation selections and defers reserving the instance until execution.
        /// Registers cleanup for the eventual builder, including cancellation before its build task
        /// starts. Successful completion selects the installed instance in the original repository.
        ///
        /// @param settings the installer selections and target instance id
        /// @return a deferred build task retaining the builder task's stage hints
        private Task<?> finishVersionDownloadingAsync(SettingsMap settings) {
            @Nullable GameInstanceID instanceId = settings.get(AbstractInstallersPage.INSTANCE_ID);
            if (instanceId == null) {
                throw new IllegalStateException("Instance ID is not set");
            }

            String selectedGameVersion = ((ComponentRemoteVersion) settings.get(GameComponentType.GAME.getPatchId()))
                    .getGameVersion().toString();
            @Unmodifiable List<ComponentRemoteVersion> components = settings.asStringMap().entrySet().stream()
                    .filter(entry -> !GameComponentType.GAME.getPatchId().equals(entry.getKey()))
                    .map(Map.Entry::getValue)
                    .filter(ComponentRemoteVersion.class::isInstance)
                    .map(ComponentRemoteVersion.class::cast)
                    .toList();
            boolean modded = GameComponentType.MOD_LOADERS.stream()
                    .anyMatch(componentType -> settings.get(componentType.getPatchId()) instanceof ComponentRemoteVersion);
            boolean isolated = repository.shouldIsolateNewInstance(modded);

            AtomicReference<@Nullable HMCLGameBuilder> builderReference = new AtomicReference<>();
            settings.put(TaskCleanup.KEY, () -> {
                @Nullable HMCLGameBuilder builder = builderReference.get();
                if (builder != null)
                    builder.abort();
            });

            return Task.composeAsync(() -> {
                try (HMCLGameBuilder builder = dependencyManager.newGameBuilder(instanceId)) {
                    builderReference.set(builder);
                    builder.component(GameComponentType.GAME, selectedGameVersion);
                    components.forEach(builder::component);
                    if (isolated) {
                        builder.enableIsolation();
                    }

                    Task<?> buildTask = builder.buildAsync();
                    buildTask.onDone().register(event -> {
                        if (!event.isFailed()) {
                            runInFX(() -> repository.setSelectedInstance(repository.getInstance(instanceId)));
                        }
                    });
                    return buildTask;
                }
            });
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
                    return new InstallersPage(controller, repository, ((ComponentRemoteVersion) controller.getSettings().get("game")).getGameVersion(), downloadProvider);
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
