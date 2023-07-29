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
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import org.jackhuang.hmcl.mod.ModLoaderType;
import org.jackhuang.hmcl.mod.ModManager;
import org.jackhuang.hmcl.mod.RemoteMod;
import org.jackhuang.hmcl.mod.RemoteModRepository;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.setting.Theme;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.util.*;
import org.jackhuang.hmcl.util.i18n.I18n;
import org.jackhuang.hmcl.util.io.NetworkUtils;
import org.jackhuang.hmcl.util.versioning.VersionNumber;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.jackhuang.hmcl.mod.RemoteMod.DependencyType.*;
import static org.jackhuang.hmcl.util.Logging.LOG;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.ui.FXUtils.runInFX;

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

    private SimpleMultimap<String, RemoteMod.Version> versions;

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
        File versionJar = StringUtils.isNotBlank(version.getVersion())
                ? version.getProfile().getRepository().getVersionJar(version.getVersion())
                : null;

        setLoading(true);
        setFailed(false);

        Task.supplyAsync(() -> {
            Stream<RemoteMod.Version> versions = addon.getData().loadVersions(repository);
//                            if (StringUtils.isNotBlank(version.getVersion())) {
//                                Optional<String> gameVersion = GameVersion.minecraftVersion(versionJar);
//                                if (gameVersion.isPresent()) {
//                                    return sortVersions(
//                                            .filter(file -> file.getGameVersions().contains(gameVersion.get())));
//                                }
//                            }
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

    private SimpleMultimap<String, RemoteMod.Version> sortVersions(Stream<RemoteMod.Version> versions) {
        SimpleMultimap<String, RemoteMod.Version> classifiedVersions
                = new SimpleMultimap<String, RemoteMod.Version>(HashMap::new, ArrayList::new);
        versions.forEach(version -> {
            for (String gameVersion : version.getGameVersions()) {
                classifiedVersions.put(gameVersion, version);
            }
        });

        for (String gameVersion : classifiedVersions.keys()) {
            List<RemoteMod.Version> versionList = (List<RemoteMod.Version>) classifiedVersions.get(gameVersion);
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

    public void download(RemoteMod.Version file) {
        if (this.callback == null) {
            saveAs(file);
        } else {
            this.callback.download(version.getProfile(), version.getVersion(), file);
        }
    }

    public void saveAs(RemoteMod.Version file) {
        String extension = StringUtils.substringAfterLast(file.getFile().getFilename(), '.');

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(i18n("button.save_as"));
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(i18n("file"), "*." + extension));
        fileChooser.setInitialFileName(file.getFile().getFilename());
        File dest = fileChooser.showSaveDialog(Controllers.getStage());
        if (dest == null) {
            return;
        }

        Controllers.taskDialog(
                Task.composeAsync(() -> {
                    FileDownloadTask task = new FileDownloadTask(NetworkUtils.toURL(file.getFile().getUrl()), dest, file.getFile().getIntegrityCheck());
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
            descriptionPane.setAlignment(Pos.CENTER);
            pane.getChildren().add(descriptionPane);
            descriptionPane.getStyleClass().add("card-non-transparent");
            BorderPane.setMargin(descriptionPane, new Insets(11, 11, 0, 11));
            {
                ImageView imageView = new ImageView();
                if (StringUtils.isNotBlank(getSkinnable().addon.getIconUrl())) {
                    imageView.setImage(new Image(getSkinnable().addon.getIconUrl(), 40, 40, true, true, true));
                }
                descriptionPane.getChildren().add(FXUtils.limitingSize(imageView, 40, 40));

                TwoLineListItem content = new TwoLineListItem();
                HBox.setHgrow(content, Priority.ALWAYS);
                ModTranslations.Mod mod = getSkinnable().translations.getModByCurseForgeId(getSkinnable().addon.getSlug());
                content.setTitle(mod != null && I18n.getCurrentLocale().getLocale() == Locale.CHINA ? mod.getDisplayName() : getSkinnable().addon.getTitle());
                content.setSubtitle(getSkinnable().addon.getDescription());
                content.getTags().setAll(getSkinnable().addon.getCategories().stream()
                        .map(category -> getSkinnable().page.getLocalizedCategory(category))
                        .collect(Collectors.toList()));
                descriptionPane.getChildren().add(content);

                if (getSkinnable().mod != null) {
                    JFXHyperlink openMcmodButton = new JFXHyperlink(i18n("mods.mcmod"));
                    openMcmodButton.setExternalLink(getSkinnable().translations.getMcmodUrl(getSkinnable().mod));
                    descriptionPane.getChildren().add(openMcmodButton);
                    runInFX(() -> FXUtils.installFastTooltip(openMcmodButton, i18n("mods.mcmod")));

                    if (StringUtils.isNotBlank(getSkinnable().mod.getMcbbs())) {
                        JFXHyperlink openMcbbsButton = new JFXHyperlink(i18n("mods.mcbbs"));
                        openMcbbsButton.setExternalLink(ModManager.getMcbbsUrl(getSkinnable().mod.getMcbbs()));
                        descriptionPane.getChildren().add(openMcbbsButton);
                        runInFX(() -> FXUtils.installFastTooltip(openMcbbsButton, i18n("mods.mcbbs")));
                    }
                }

                JFXHyperlink openUrlButton = new JFXHyperlink(control.page.getLocalizedOfficialPage());
                openUrlButton.setExternalLink(getSkinnable().addon.getPageUrl());
                descriptionPane.getChildren().add(openUrlButton);
                runInFX(() -> FXUtils.installFastTooltip(openUrlButton, control.page.getLocalizedOfficialPage()));
            }

            if (control.repository.getType() != RemoteModRepository.Type.MOD) {
                Node title = ComponentList.createComponentListTitle(i18n("mods.dependency.embedded"));
                ComponentList dependencyPane = new ComponentList(Lang::immutableListOf);
                Task.supplyAsync(() -> control.addon.getData().loadDependencies(control.repository).stream()
                        .map(remoteMod -> new DependencyModItem(getSkinnable().page, remoteMod, control.version, control.callback))
                        .map(dependencyModItem -> {
                            VBox box = new VBox();
                            box.setPadding(new Insets(8, 0, 8, 0));
                            box.getChildren().setAll(dependencyModItem);
                            return box;
                        })
                        .collect(Collectors.toList())
                ).whenComplete(Schedulers.javafx(), (result, exception) -> {
                    if (exception == null) {
                        dependencyPane.getContent().setAll(result);
                    } else {
                        Label msg = new Label(i18n("download.failed.refresh"));
                        msg.setPadding(new Insets(8));
                        dependencyPane.getContent().setAll(msg);
                    }
                }).start();
                dependencyPane.getStyleClass().add("no-padding");

                pane.getChildren().addAll(title, dependencyPane);
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

                    for (String gameVersion : control.versions.keys().stream()
                            .sorted(VersionNumber.VERSION_COMPARATOR.reversed())
                            .collect(Collectors.toList())) {
                        ComponentList sublist = new ComponentList(() ->
                                control.versions.get(gameVersion).stream()
                                        .map(version -> new ModItem(version, control))
                                        .collect(Collectors.toList()));
                        sublist.getStyleClass().add("no-padding");
                        sublist.setTitle(gameVersion);

                        list.getContent().add(sublist);
                    }
                });
            }

            getChildren().setAll(scrollPane);
        }
    }

    private static final class DependencyModItem extends StackPane {
        public static final EnumMap<RemoteMod.DependencyType, String> I18N_KEY = new EnumMap<>(Lang.mapOf(
                Pair.pair(EMBEDDED, "mods.dependency.embedded"),
                Pair.pair(OPTIONAL, "mods.dependency.optional"),
                Pair.pair(REQUIRED, "mods.dependency.required"),
                Pair.pair(TOOL, "mods.dependency.tool"),
                Pair.pair(INCLUDE, "mods.dependency.include"),
                Pair.pair(INCOMPATIBLE, "mods.dependency.incompatible"),
                Pair.pair(BROKEN, "mods.dependency.broken")
        ));

        DependencyModItem(DownloadListPage page, RemoteMod addon, Profile.ProfileVersion version, DownloadCallback callback) {
            HBox pane = new HBox(8);
            pane.setPadding(new Insets(0, 8, 0, 8));
            pane.setAlignment(Pos.CENTER_LEFT);
            TwoLineListItem content = new TwoLineListItem();
            HBox.setHgrow(content, Priority.ALWAYS);
            ImageView imageView = new ImageView();
            pane.getChildren().setAll(FXUtils.limitingSize(imageView, 40, 40), content);

            RipplerContainer container = new RipplerContainer(pane);
            container.setOnMouseClicked(e -> Controllers.navigate(new DownloadPage(page, addon, version, callback)));
            getChildren().setAll(container);

            ModTranslations.Mod mod = ModTranslations.getTranslationsByRepositoryType(page.repository.getType()).getModByCurseForgeId(addon.getSlug());
            content.setTitle(mod != null && I18n.getCurrentLocale().getLocale() == Locale.CHINA ? mod.getDisplayName() : addon.getTitle());
            content.setSubtitle(addon.getDescription());
            content.getTags().setAll(addon.getCategories().stream()
                    .map(page::getLocalizedCategory)
                    .collect(Collectors.toList()));

            if (StringUtils.isNotBlank(addon.getIconUrl())) {
                imageView.setImage(new Image(addon.getIconUrl(), 40, 40, true, true, true));
            }
        }
    }

    private static final class ModItem extends StackPane {

        ModItem(RemoteMod.Version dataItem, DownloadPage selfPage) {
            VBox pane = new VBox(8);
            pane.setPadding(new Insets(8, 0, 8, 0));

            {
                if (selfPage.repository.getType() == RemoteModRepository.Type.MOD) {
                    List<Node> elements = new ArrayList<>();
                    EnumMap<RemoteMod.DependencyType, List<DependencyModItem>> dependencies = new EnumMap<>(RemoteMod.DependencyType.class);
                    try {
                        for (RemoteMod.Dependency dependency : dataItem.getDependencies()) {
                            if (dependency.getType() == INCOMPATIBLE || dependency.getType() == BROKEN) {
                                continue;
                            }

                            if (!dependencies.containsKey(dependency.getType())) {
                                dependencies.put(dependency.getType(), new ArrayList<>());
                            }
                            dependencies.get(dependency.getType()).add(new DependencyModItem(selfPage.page, dependency.load(), selfPage.version, selfPage.callback));
                        }
                    } catch (IOException exception) {
                        dependencies.clear();
                        Label msg = new Label(i18n("download.failed.refresh"));
                        msg.setPadding(new Insets(8));
                        elements.add(msg);
                        LOG.log(Level.WARNING, String.format("Fail to load dependencies of mod %s.", dataItem.getModid()));
                    }

                    for (Map.Entry<RemoteMod.DependencyType, List<DependencyModItem>> entry : dependencies.entrySet()) {
                        Label title = new Label(i18n(DependencyModItem.I18N_KEY.get(entry.getKey())));
                        title.setPadding(new Insets(0, 8, 0, 8));
                        elements.add(title);
                        elements.addAll(entry.getValue());
                    }
                    pane.getChildren().addAll(elements);
                }

                HBox descPane = new HBox(8);
                descPane.setPadding(new Insets(0, 8, 0, 8));
                descPane.setAlignment(Pos.CENTER_LEFT);

                {
                    StackPane graphicPane = new StackPane();
                    graphicPane.getChildren().setAll(SVG.releaseCircleOutline(Theme.blackFillBinding(), 24, 24));

                    TwoLineListItem content = new TwoLineListItem();
                    HBox.setHgrow(content, Priority.ALWAYS);
                    content.setTitle(dataItem.getName());
                    content.setSubtitle(FORMATTER.format(dataItem.getDatePublished().toInstant()));

                    switch (dataItem.getVersionType()) {
                        case Alpha:
                        case Beta:
                            content.getTags().add(i18n("version.game.snapshot"));
                            break;
                        case Release:
                            content.getTags().add(i18n("version.game.release"));
                            break;
                    }

                    for (ModLoaderType modLoaderType : dataItem.getLoaders()) {
                        switch (modLoaderType) {
                            case FORGE:
                                content.getTags().add(i18n("install.installer.forge"));
                                break;
                            case FABRIC:
                                content.getTags().add(i18n("install.installer.fabric"));
                                break;
                            case LITE_LOADER:
                                content.getTags().add(i18n("install.installer.liteloader"));
                                break;
                            case QUILT:
                                content.getTags().add(i18n("install.installer.quilt"));
                                break;
                        }
                    }

                    JFXButton saveAsButton = new JFXButton();
                    saveAsButton.getStyleClass().add("toggle-icon4");
                    saveAsButton.setGraphic(SVG.contentSaveMoveOutline(Theme.blackFillBinding(), -1, -1));
                    saveAsButton.setOnAction(e -> selfPage.saveAs(dataItem));

                    descPane.getChildren().setAll(graphicPane, content, saveAsButton);
                    descPane.setOnMouseClicked(e -> selfPage.download(dataItem));
                }

                pane.getChildren().add(descPane);
            }

            RipplerContainer container = new RipplerContainer(pane);
            getChildren().setAll(container);

            // Workaround for https://github.com/huanghongxun/HMCL/issues/2129
            this.setMinHeight(50);
        }
    }

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.FULL).withLocale(Locale.getDefault()).withZone(ZoneId.systemDefault());

    public interface DownloadCallback {
        void download(Profile profile, @Nullable String version, RemoteMod.Version file);
    }
}
