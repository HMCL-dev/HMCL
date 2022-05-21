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
import com.jfoenix.controls.JFXScrollPane;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Skin;
import javafx.scene.control.SkinBase;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
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
import org.jackhuang.hmcl.util.SimpleMultimap;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.function.ExceptionalConsumer;
import org.jackhuang.hmcl.util.io.NetworkUtils;
import org.jackhuang.hmcl.util.versioning.VersionNumber;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

    private List<RemoteMod> dependencies;
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

        Task.allOf(
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
                        }))
                .whenComplete(Schedulers.javafx(), (result, exception) -> {
                    if (exception == null) {

                        @SuppressWarnings("unchecked")
                        SimpleMultimap<String, RemoteMod.Version> versions = (SimpleMultimap<String, RemoteMod.Version>) result.get(0);

                        this.versions = versions;

                        ArrayList<RemoteMod.Version> allVersions = new ArrayList<>();
                        for (String s : versions.keys()) {
                            allVersions.addAll(versions.get(s));
                        }
                        Task.supplyAsync(() -> addon.getData().loadDependencies(repository,allVersions)).thenAcceptAsync((ExceptionalConsumer<List<RemoteMod>, Exception>) remoteMods -> {
                            this.dependencies = remoteMods;
                        }).whenComplete(exception1 -> {
                            Platform.runLater(() -> {
                                if (exception1 == null) {
                                    loaded.set(true);
                                    setFailed(false);
                                }
                                else {
                                    setFailed(true);
                                }
                                setLoading(false);
                            });
                        }).start();

                    } else {
                        setFailed(true);
                    }
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
                new FileDownloadTask(NetworkUtils.toURL(file.getFile().getUrl()), dest, file.getFile().getIntegrityCheck()).executor(true),
                i18n("message.downloading")
        );
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
            JFXScrollPane.smoothScrolling(scrollPane);
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
                content.setTitle(mod != null ? mod.getDisplayName() : getSkinnable().addon.getTitle());
                content.setSubtitle(getSkinnable().addon.getDescription());
                content.getTags().setAll(getSkinnable().addon.getCategories().stream()
                        .map(category -> getSkinnable().page.getLocalizedCategory(category))
                        .collect(Collectors.toList()));
                descriptionPane.getChildren().add(content);

                if (getSkinnable().mod != null) {
                    JFXHyperlink openMcmodButton = new JFXHyperlink(i18n("mods.mcmod"));
                    openMcmodButton.setOnAction(e -> FXUtils.openLink(ModManager.getMcmodUrl(getSkinnable().mod.getMcmod())));
                    descriptionPane.getChildren().add(openMcmodButton);

                    if (StringUtils.isNotBlank(getSkinnable().mod.getMcbbs())) {
                        JFXHyperlink openMcbbsButton = new JFXHyperlink(i18n("mods.mcbbs"));
                        openMcbbsButton.setOnAction(e -> FXUtils.openLink(ModManager.getMcbbsUrl(getSkinnable().mod.getMcbbs())));
                        descriptionPane.getChildren().add(openMcbbsButton);
                    }
                }

                JFXHyperlink openUrlButton = new JFXHyperlink(control.page.getLocalizedOfficialPage());
                openUrlButton.setOnAction(e -> FXUtils.openLink(getSkinnable().addon.getPageUrl()));
                descriptionPane.getChildren().add(openUrlButton);
            }

            {
                ComponentList dependencyPane = new ComponentList();
                dependencyPane.getStyleClass().add("no-padding");

                FXUtils.onChangeAndOperate(control.loaded, loaded -> {
                    if (loaded) {
                        dependencyPane.getContent().setAll(control.dependencies.stream()
                                .map(dependency -> new DependencyModItem(getSkinnable().page, dependency, control.version, control.callback))
                                .collect(Collectors.toList()));
                    }
                });

                Node title = ComponentList.createComponentListTitle(i18n("mods.dependencies"));

                BooleanBinding show = Bindings.createBooleanBinding(() -> control.loaded.get() && !control.dependencies.isEmpty(), control.loaded);
                title.managedProperty().bind(show);
                title.visibleProperty().bind(show);
                dependencyPane.managedProperty().bind(show);
                dependencyPane.visibleProperty().bind(show);

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
                        ComponentList sublist = new ComponentList();
                        sublist.setLazyInitializer(self -> {
                            self.getContent().setAll(control.versions.get(gameVersion).stream()
                                    .map(version -> new ModItem(version, control))
                                    .collect(Collectors.toList()));
                        });
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

        DependencyModItem(DownloadListPage page, RemoteMod addon, Profile.ProfileVersion version, DownloadCallback callback) {
            HBox pane = new HBox(8);
            pane.setPadding(new Insets(8));
            pane.setAlignment(Pos.CENTER_LEFT);
            TwoLineListItem content = new TwoLineListItem();
            HBox.setHgrow(content, Priority.ALWAYS);
            ImageView imageView = new ImageView();
            pane.getChildren().setAll(FXUtils.limitingSize(imageView, 40, 40), content);

            RipplerContainer container = new RipplerContainer(pane);
            container.setOnMouseClicked(e -> Controllers.navigate(new DownloadPage(page, addon, version, callback)));
            getChildren().setAll(container);

            ModTranslations.Mod mod = ModTranslations.getTranslationsByRepositoryType(page.repository.getType()).getModByCurseForgeId(addon.getSlug());
            content.setTitle(mod != null ? mod.getDisplayName() : addon.getTitle());
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
            HBox pane = new HBox(8);
            pane.setPadding(new Insets(8));
            pane.setAlignment(Pos.CENTER_LEFT);
            TwoLineListItem content = new TwoLineListItem();
            StackPane graphicPane = new StackPane();
            JFXButton saveAsButton = new JFXButton();

            RipplerContainer container = new RipplerContainer(pane);
            container.setOnMouseClicked(e -> {
                selfPage.download(dataItem);
            });
            getChildren().setAll(container);

            saveAsButton.getStyleClass().add("toggle-icon4");
            saveAsButton.setGraphic(SVG.contentSaveMoveOutline(Theme.blackFillBinding(), -1, -1));

            HBox.setHgrow(content, Priority.ALWAYS);
            pane.getChildren().setAll(graphicPane, content, saveAsButton);

            content.setTitle(dataItem.getName());
            content.setSubtitle(FORMATTER.format(dataItem.getDatePublished().toInstant()));
            saveAsButton.setOnMouseClicked(e -> selfPage.saveAs(dataItem));

            switch (dataItem.getVersionType()) {
                case Release:
                    graphicPane.getChildren().setAll(SVG.releaseCircleOutline(Theme.blackFillBinding(), 24, 24));
                    content.getTags().add(i18n("version.game.release"));
                    break;
                case Beta:
                    graphicPane.getChildren().setAll(SVG.betaCircleOutline(Theme.blackFillBinding(), 24, 24));
                    content.getTags().add(i18n("version.game.snapshot"));
                    break;
                case Alpha:
                    graphicPane.getChildren().setAll(SVG.alphaCircleOutline(Theme.blackFillBinding(), 24, 24));
                    content.getTags().add(i18n("version.game.snapshot"));
                    break;
            }
        }
    }

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.FULL).withLocale(Locale.getDefault()).withZone(ZoneId.systemDefault());

    public interface DownloadCallback {
        void download(Profile profile, @Nullable String version, RemoteMod.Version file);
    }
}
