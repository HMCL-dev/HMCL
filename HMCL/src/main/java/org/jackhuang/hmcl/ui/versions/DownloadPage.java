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
package org.jackhuang.hmcl.ui.versions;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXDialogLayout;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Skin;
import javafx.scene.control.SkinBase;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.jackhuang.hmcl.download.LibraryAnalyzer;
import org.jackhuang.hmcl.game.HMCLGameRepository;
import org.jackhuang.hmcl.game.Version;
import org.jackhuang.hmcl.mod.ModLoaderType;
import org.jackhuang.hmcl.mod.RemoteMod;
import org.jackhuang.hmcl.mod.RemoteModRepository;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.Pair;
import org.jackhuang.hmcl.util.SimpleMultimap;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.TaskCancellationAction;
import org.jackhuang.hmcl.util.i18n.I18n;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.javafx.BindingMapping;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.jackhuang.hmcl.ui.FXUtils.onEscPressed;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class DownloadPage extends Control implements DecoratorPage {
    private final ReadOnlyObjectWrapper<State> state = new ReadOnlyObjectWrapper<>();
    private final BooleanProperty loaded = new SimpleBooleanProperty(false);
    private final BooleanProperty loading = new SimpleBooleanProperty(false);
    private final BooleanProperty failed = new SimpleBooleanProperty(false);
    private final RemoteModRepository repository;
    private final ModTranslations translations;
    private final RemoteMod addon;
    private final ModTranslations.Mod mod;
    private final Profile.ProfileVersion version;
    private final DownloadCallback callback;
    private final DownloadListPage page;

    private SimpleMultimap<String, RemoteMod.Version, List<RemoteMod.Version>> versions;

    public DownloadPage(DownloadListPage page, RemoteMod addon, Profile.ProfileVersion version, @Nullable DownloadCallback callback) {
        this.page = page;
        this.repository = page.repository;
        this.addon = addon;
        this.translations = ModTranslations.getTranslationsByRepositoryType(repository.getType());
        this.mod = translations.getModByCurseForgeId(addon.getSlug());
        this.version = version;
        this.callback = callback;
        loadModVersions();

        this.state.set(State.fromTitle(addon.getTitle()));
    }

    private void loadModVersions() {
        setLoading(true);
        setFailed(false);

        Task.supplyAsync(() -> {
            Stream<RemoteMod.Version> versions = addon.getData().loadVersions(repository);
            return sortVersions(versions);
        }).whenComplete(Schedulers.javafx(), (result, exception) -> {
            if (exception == null) {
                this.versions = result;

                loaded.set(true);
                setFailed(false);
            } else {
                setFailed(true);
            }
            setLoading(false);
        }).start();
    }

    private SimpleMultimap<String, RemoteMod.Version, List<RemoteMod.Version>> sortVersions(Stream<RemoteMod.Version> versions) {
        SimpleMultimap<String, RemoteMod.Version, List<RemoteMod.Version>> classifiedVersions
                = new SimpleMultimap<>(HashMap::new, ArrayList::new);
        versions.forEach(version -> {
            for (String gameVersion : version.getGameVersions()) {
                classifiedVersions.put(gameVersion, version);
            }
        });

        for (String gameVersion : classifiedVersions.keys()) {
            List<RemoteMod.Version> versionList = classifiedVersions.get(gameVersion);
            versionList.sort(Comparator.comparing(RemoteMod.Version::getDatePublished).reversed());
        }
        return classifiedVersions;
    }

    public RemoteMod getAddon() {
        return addon;
    }

    public Profile.ProfileVersion getVersion() {
        return version;
    }

    public boolean isLoading() {
        return loading.get();
    }

    public BooleanProperty loadingProperty() {
        return loading;
    }

    public void setLoading(boolean loading) {
        this.loading.set(loading);
    }

    public boolean isFailed() {
        return failed.get();
    }

    public BooleanProperty failedProperty() {
        return failed;
    }

    public void setFailed(boolean failed) {
        this.failed.set(failed);
    }

    public void download(RemoteMod mod, RemoteMod.Version file) {
        if (this.callback == null) {
            saveAs(mod, file);
        } else {
            this.callback.download(version.getProfile(), version.getVersion(), mod, file);
        }
    }

    public void saveAs(RemoteMod mod, RemoteMod.Version file) {
        String extension = StringUtils.substringAfterLast(file.getFile().getFilename(), '.');

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(i18n("button.save_as"));
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(i18n("file"), "*." + extension));
        fileChooser.setInitialFileName(file.getFile().getFilename());
        Path dest = FileUtils.toPath(fileChooser.showSaveDialog(Controllers.getStage()));
        if (dest == null) {
            return;
        }

        Controllers.taskDialog(
                Task.composeAsync(() -> {
                    var task = new FileDownloadTask(file.getFile().getUrl(), dest, file.getFile().getIntegrityCheck());
                    task.setName(file.getName());
                    return task;
                }),
                i18n("message.downloading"),
                TaskCancellationAction.NORMAL);
    }

    @Override
    public ReadOnlyObjectProperty<State> stateProperty() {
        return state.getReadOnlyProperty();
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new ModDownloadPageSkin(this);
    }

    private static class ModDownloadPageSkin extends SkinBase<DownloadPage> {

        protected ModDownloadPageSkin(DownloadPage control) {
            super(control);

            VBox pane = new VBox(8);
            pane.getStyleClass().add("gray-background");
            pane.setPadding(new Insets(10));
            ScrollPane scrollPane = new ScrollPane(pane);
            FXUtils.smoothScrolling(scrollPane);
            scrollPane.setFitToWidth(true);
            scrollPane.setFitToHeight(true);

            HBox descriptionPane = new HBox(8);
            descriptionPane.setMinHeight(Region.USE_PREF_SIZE);
            descriptionPane.setAlignment(Pos.CENTER);
            pane.getChildren().add(descriptionPane);
            descriptionPane.getStyleClass().add("card-non-transparent");
            BorderPane.setMargin(descriptionPane, new Insets(11, 11, 0, 11));
            {
                ImageView imageView = new ImageView();
                imageView.setFitWidth(40);
                imageView.setFitHeight(40);
                if (StringUtils.isNotBlank(getSkinnable().addon.getIconUrl())) {
                    imageView.imageProperty().bind(FXUtils.newRemoteImage(getSkinnable().addon.getIconUrl(), 80, 80, true, true));
                }
                descriptionPane.getChildren().add(FXUtils.limitingSize(imageView, 40, 40));

                TwoLineListItem content = new TwoLineListItem();
                HBox.setHgrow(content, Priority.ALWAYS);
                ModTranslations.Mod mod = getSkinnable().translations.getModByCurseForgeId(getSkinnable().addon.getSlug());
                content.setTitle(mod != null && I18n.isUseChinese() ? mod.getDisplayName() : getSkinnable().addon.getTitle());
                content.setSubtitle(getSkinnable().addon.getDescription());
                content.getSubtitleLabel().setWrapText(true);
                getSkinnable().addon.getCategories().stream()
                        .map(category -> getSkinnable().page.getLocalizedCategory(category))
                        .forEach(content::addTag);
                descriptionPane.getChildren().add(content);

                if (getSkinnable().mod != null) {
                    JFXHyperlink openMcmodButton = new JFXHyperlink(i18n("mods.mcmod"));
                    openMcmodButton.setExternalLink(getSkinnable().translations.getMcmodUrl(getSkinnable().mod));
                    descriptionPane.getChildren().add(openMcmodButton);
                    openMcmodButton.setMinWidth(Region.USE_PREF_SIZE);
                    FXUtils.installFastTooltip(openMcmodButton, i18n("mods.mcmod"));
                }

                JFXHyperlink openUrlButton = new JFXHyperlink(control.page.getLocalizedOfficialPage());
                openUrlButton.setExternalLink(getSkinnable().addon.getPageUrl());
                descriptionPane.getChildren().add(openUrlButton);
                openUrlButton.setMinWidth(Region.USE_PREF_SIZE);
                FXUtils.installFastTooltip(openUrlButton, control.page.getLocalizedOfficialPage());
            }

            SpinnerPane spinnerPane = new SpinnerPane();
            VBox.setVgrow(spinnerPane, Priority.ALWAYS);
            pane.getChildren().add(spinnerPane);
            {
                spinnerPane.loadingProperty().bind(getSkinnable().loadingProperty());
                spinnerPane.failedReasonProperty().bind(Bindings.createStringBinding(() -> {
                    if (getSkinnable().isFailed()) {
                        return i18n("download.failed.refresh");
                    } else {
                        return null;
                    }
                }, getSkinnable().failedProperty()));
                spinnerPane.setOnFailedAction(e -> getSkinnable().loadModVersions());

                ComponentList list = new ComponentList();
                StackPane.setAlignment(list, Pos.TOP_CENTER);
                spinnerPane.setContent(list);

                FXUtils.onChangeAndOperate(control.loaded, loaded -> {
                    if (control.versions == null) return;

                    if (control.version.getProfile() != null && control.version.getVersion() != null) {
                        HMCLGameRepository repository = control.version.getProfile().getRepository();
                        Version game = repository.getResolvedPreservingPatchesVersion(control.version.getVersion());
                        String gameVersion = repository.getGameVersion(game).orElse(null);

                        if (gameVersion != null) {
                            List<RemoteMod.Version> modVersions = control.versions.get(gameVersion);
                            if (modVersions != null && !modVersions.isEmpty()) {
                                Set<ModLoaderType> targetLoaders = LibraryAnalyzer.analyze(game, gameVersion).getModLoaders();

                                resolve:
                                for (RemoteMod.Version modVersion : modVersions) {
                                    for (ModLoaderType loader : modVersion.getLoaders()) {
                                        if (targetLoaders.contains(loader)) {
                                            list.getContent().addAll(
                                                    ComponentList.createComponentListTitle(i18n("mods.download.recommend", gameVersion)),
                                                    new ModItem(control.addon, modVersion, control)
                                            );
                                            break resolve;
                                        }
                                    }
                                }
                            }
                        }
                    }

                    for (String gameVersion : control.versions.keys().stream()
                            .sorted(Collections.reverseOrder(GameVersionNumber::compare))
                            .toList()) {
                        List<RemoteMod.Version> versions = control.versions.get(gameVersion);
                        if (versions == null || versions.isEmpty()) {
                            continue;
                        }

                        var sublist = new ComponentSublist(() -> {
                            ArrayList<ModItem> items = new ArrayList<>(versions.size());
                            for (RemoteMod.Version v : versions) {
                                items.add(new ModItem(control.addon, v, control));
                            }
                            return items;
                        });
                        sublist.getStyleClass().add("no-padding");
                        sublist.setTitle("Minecraft " + gameVersion);

                        list.getContent().add(sublist);
                    }
                });
            }

            getChildren().setAll(scrollPane);
        }
    }

    private static final class DependencyModItem extends StackPane {
        public static final EnumMap<RemoteMod.DependencyType, String> I18N_KEY = new EnumMap<>(Lang.mapOf(
                Pair.pair(RemoteMod.DependencyType.EMBEDDED, "mods.dependency.embedded"),
                Pair.pair(RemoteMod.DependencyType.OPTIONAL, "mods.dependency.optional"),
                Pair.pair(RemoteMod.DependencyType.REQUIRED, "mods.dependency.required"),
                Pair.pair(RemoteMod.DependencyType.TOOL, "mods.dependency.tool"),
                Pair.pair(RemoteMod.DependencyType.INCLUDE, "mods.dependency.include"),
                Pair.pair(RemoteMod.DependencyType.INCOMPATIBLE, "mods.dependency.incompatible"),
                Pair.pair(RemoteMod.DependencyType.BROKEN, "mods.dependency.broken")
        ));

        DependencyModItem(DownloadListPage page, RemoteMod addon, Profile.ProfileVersion version, DownloadCallback callback) {
            HBox pane = new HBox(8);
            pane.setPadding(new Insets(0, 8, 0, 8));
            pane.setAlignment(Pos.CENTER_LEFT);
            TwoLineListItem content = new TwoLineListItem();
            HBox.setHgrow(content, Priority.ALWAYS);
            ImageView imageView = new ImageView();
            imageView.setFitWidth(40);
            imageView.setFitHeight(40);
            pane.getChildren().setAll(FXUtils.limitingSize(imageView, 40, 40), content);

            RipplerContainer container = new RipplerContainer(pane);
            FXUtils.onClicked(container, () -> {
                fireEvent(new DialogCloseEvent());
                Controllers.navigate(new DownloadPage(page, addon, version, callback));
            });
            getChildren().setAll(container);

            if (addon != RemoteMod.BROKEN) {
                ModTranslations.Mod mod = ModTranslations.getTranslationsByRepositoryType(page.repository.getType()).getModByCurseForgeId(addon.getSlug());
                content.setTitle(mod != null && I18n.isUseChinese() ? mod.getDisplayName() : addon.getTitle());
                content.setSubtitle(addon.getDescription());
                for (String category : addon.getCategories()) {
                    if (page.shouldDisplayCategory(category))
                        content.addTag(page.getLocalizedCategory(category));
                }
                if (StringUtils.isNotBlank(addon.getIconUrl())) {
                    imageView.imageProperty().bind(FXUtils.newRemoteImage(addon.getIconUrl(), 80, 80, true, true));
                }
            } else {
                content.setTitle(i18n("mods.broken_dependency.title"));
                content.setSubtitle(i18n("mods.broken_dependency.desc"));
                imageView.setImage(FXUtils.newBuiltinImage("/assets/img/icon@4x.png"));
            }
        }
    }

    private static final class ModItem extends StackPane {

        ModItem(RemoteMod mod, RemoteMod.Version dataItem, DownloadPage selfPage) {
            VBox pane = new VBox(8);
            pane.setPadding(new Insets(8, 0, 8, 0));

            {
                HBox descPane = new HBox(8);
                descPane.setPadding(new Insets(0, 8, 0, 8));
                descPane.setAlignment(Pos.CENTER_LEFT);
                descPane.setMouseTransparent(true);

                {
                    StackPane graphicPane = new StackPane();
                    TwoLineListItem content = new TwoLineListItem();
                    HBox.setHgrow(content, Priority.ALWAYS);
                    content.setTitle(dataItem.getName());
                    content.setSubtitle(I18n.formatDateTime(dataItem.getDatePublished()));

                    switch (dataItem.getVersionType()) {
                        case Alpha:
                            content.addTag(i18n("mods.channel.alpha"));
                            graphicPane.getChildren().setAll(SVG.ALPHA_CIRCLE.createIcon(24));
                            break;
                        case Beta:
                            content.addTag(i18n("mods.channel.beta"));
                            graphicPane.getChildren().setAll(SVG.BETA_CIRCLE.createIcon(24));
                            break;
                        case Release:
                            content.addTag(i18n("mods.channel.release"));
                            graphicPane.getChildren().setAll(SVG.RELEASE_CIRCLE.createIcon(24));
                            break;
                    }

                    for (ModLoaderType modLoaderType : dataItem.getLoaders()) {
                        switch (modLoaderType) {
                            case FORGE:
                                content.addTag(i18n("install.installer.forge"));
                                break;
                            case CLEANROOM:
                                content.addTag(i18n("install.installer.cleanroom"));
                                break;
                            case NEO_FORGED:
                                content.addTag(i18n("install.installer.neoforge"));
                                break;
                            case FABRIC:
                                content.addTag(i18n("install.installer.fabric"));
                                break;
                            case LITE_LOADER:
                                content.addTag(i18n("install.installer.liteloader"));
                                break;
                            case QUILT:
                                content.addTag(i18n("install.installer.quilt"));
                                break;
                        }
                    }

                    descPane.getChildren().setAll(graphicPane, content);
                }

                pane.getChildren().add(descPane);
            }

            RipplerContainer container = new RipplerContainer(pane);
            FXUtils.onClicked(container, () -> Controllers.dialog(new ModVersion(mod, dataItem, selfPage)));
            getChildren().setAll(container);

            // Workaround for https://github.com/HMCL-dev/HMCL/issues/2129
            this.setMinHeight(50);
        }
    }

    private static final class ModVersion extends JFXDialogLayout {
        public ModVersion(RemoteMod mod, RemoteMod.Version version, DownloadPage selfPage) {
            RemoteModRepository.Type type = selfPage.repository.getType();

            String title = switch (type) {
                case WORLD -> "world.download.title";
                case MODPACK -> "modpack.download.title";
                case RESOURCE_PACK -> "resourcepack.download.title";
                case SHADER_PACK -> "shaderpack.download.title";
                default -> "mods.download.title";
            };
            this.setHeading(new HBox(new Label(i18n(title, version.getName()))));

            VBox box = new VBox(8);
            box.setPadding(new Insets(8));
            ModItem modItem = new ModItem(mod, version, selfPage);
            modItem.setMouseTransparent(true); // Item is displayed for info, clicking shouldn't open the dialog again
            box.getChildren().setAll(modItem);
            SpinnerPane spinnerPane = new SpinnerPane();
            ScrollPane scrollPane = new ScrollPane();
            ComponentList dependenciesList = new ComponentList();
            loadDependencies(version, selfPage, spinnerPane, dependenciesList);
            spinnerPane.setOnFailedAction(e -> loadDependencies(version, selfPage, spinnerPane, dependenciesList));

            scrollPane.setContent(dependenciesList);
            scrollPane.setFitToWidth(true);
            scrollPane.setFitToHeight(true);
            FXUtils.smoothScrolling(scrollPane);
            spinnerPane.setContent(scrollPane);
            box.getChildren().add(spinnerPane);
            VBox.setVgrow(spinnerPane, Priority.SOMETIMES);

            this.setBody(box);

            JFXButton downloadButton = null;
            if (selfPage.callback != null) {
                downloadButton = new JFXButton(type == RemoteModRepository.Type.MODPACK ? i18n("install.modpack") : i18n("mods.install"));
                downloadButton.getStyleClass().add("dialog-accept");
                downloadButton.setOnAction(e -> {
                    if (type == RemoteModRepository.Type.MODPACK || !spinnerPane.isLoading() && spinnerPane.getFailedReason() == null) {
                        fireEvent(new DialogCloseEvent());
                    }
                    selfPage.download(mod, version);
                });
            }

            JFXButton saveAsButton = new JFXButton(i18n("mods.save_as"));
            saveAsButton.getStyleClass().add("dialog-accept");
            saveAsButton.setOnAction(e -> {
                if (!spinnerPane.isLoading() && spinnerPane.getFailedReason() == null) {
                    fireEvent(new DialogCloseEvent());
                }
                selfPage.saveAs(mod, version);
            });

            JFXButton cancelButton = new JFXButton(i18n("button.cancel"));
            cancelButton.getStyleClass().add("dialog-cancel");
            cancelButton.setOnAction(e -> fireEvent(new DialogCloseEvent()));

            if (downloadButton == null) {
                this.setActions(saveAsButton, cancelButton);
            } else {
                this.setActions(downloadButton, saveAsButton, cancelButton);
            }

            this.prefWidthProperty().bind(BindingMapping.of(Controllers.getStage().widthProperty()).map(w -> w.doubleValue() * 0.7));
            this.prefHeightProperty().bind(BindingMapping.of(Controllers.getStage().heightProperty()).map(w -> w.doubleValue() * 0.7));

            onEscPressed(this, cancelButton::fire);
        }

        private void loadDependencies(RemoteMod.Version version, DownloadPage selfPage, SpinnerPane spinnerPane, ComponentList dependenciesList) {
            spinnerPane.setLoading(true);
            Task.composeAsync(() -> {
                // TODO: Massive tasks may cause OOM.
                EnumMap<RemoteMod.DependencyType, List<Node>> dependencies = new EnumMap<>(RemoteMod.DependencyType.class);
                List<Task<?>> queue = new ArrayList<>(version.getDependencies().size());
                for (RemoteMod.Dependency dependency : version.getDependencies()) {
                    if (dependency.getType() == RemoteMod.DependencyType.INCOMPATIBLE || dependency.getType() == RemoteMod.DependencyType.BROKEN) {
                        continue;
                    }

                    if (!dependencies.containsKey(dependency.getType())) {
                        List<Node> list = new ArrayList<>();
                        Label title = new Label(i18n(DependencyModItem.I18N_KEY.get(dependency.getType())));
                        title.setPadding(new Insets(0, 8, 0, 8));
                        list.add(title);
                        dependencies.put(dependency.getType(), list);
                    }

                    queue.add(Task.supplyAsync(Schedulers.io(), dependency::load)
                            .setSignificance(Task.TaskSignificance.MINOR)
                            .thenAcceptAsync(Schedulers.javafx(), dep -> {
                                if (dep == RemoteMod.BROKEN) {
                                    return;
                                }
                                DependencyModItem dependencyModItem = new DependencyModItem(selfPage.page, dep, selfPage.version, selfPage.callback);
                                dependencies.get(dependency.getType()).add(dependencyModItem);
                            })
                            .setSignificance(Task.TaskSignificance.MINOR));
                }

                return Task.allOf(queue).thenSupplyAsync(() ->
                        dependencies.values().stream().flatMap(Collection::stream).collect(Collectors.toList())
                );
            }).whenComplete(Schedulers.javafx(), (result, exception) -> {
                spinnerPane.setLoading(false);
                if (exception == null) {
                    dependenciesList.getContent().setAll(result);
                    spinnerPane.setFailedReason(null);
                } else {
                    dependenciesList.getContent().setAll();
                    spinnerPane.setFailedReason(i18n("download.failed.refresh"));
                }
            }).start();
        }
    }

    public interface DownloadCallback {
        void download(Profile profile, @Nullable String version, RemoteMod mod, RemoteMod.Version file);
    }
}
