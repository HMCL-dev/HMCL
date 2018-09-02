/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2017  huangyuhui <huanghongxun2008@126.com>
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
 * along with this program.  If not, see {http://www.gnu.org/licenses/}.
 */
package org.jackhuang.hmcl.ui;

import com.jfoenix.concurrency.JFXUtilities;
import com.jfoenix.controls.*;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.jackhuang.hmcl.download.LibraryAnalyzer;
import org.jackhuang.hmcl.event.EventBus;
import org.jackhuang.hmcl.event.RefreshedVersionsEvent;
import org.jackhuang.hmcl.event.RefreshingVersionsEvent;
import org.jackhuang.hmcl.game.GameVersion;
import org.jackhuang.hmcl.game.HMCLGameRepository;
import org.jackhuang.hmcl.game.ModpackHelper;
import org.jackhuang.hmcl.game.Version;
import org.jackhuang.hmcl.mod.MismatchedModpackTypeException;
import org.jackhuang.hmcl.mod.UnsupportedModpackException;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.setting.Profiles;
import org.jackhuang.hmcl.setting.Theme;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskExecutor;
import org.jackhuang.hmcl.ui.construct.DialogCloseEvent;
import org.jackhuang.hmcl.ui.construct.MessageBox;
import org.jackhuang.hmcl.ui.versions.Versions;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.VersionNumber;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.jackhuang.hmcl.util.StringUtils.removePrefix;
import static org.jackhuang.hmcl.util.StringUtils.removeSuffix;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class GameVersionListPage extends StackPane {
    private final JFXSpinner spinner;
    private final StackPane contentPane;
    private final JFXMasonryPane masonryPane;

    private Profile profile;

    public GameVersionListPage() {
        spinner = new JFXSpinner();
        spinner.getStyleClass().setAll("first-spinner");

        contentPane = new StackPane();

        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setFitToWidth(true);

        masonryPane = new JFXMasonryPane();
        masonryPane.setHSpacing(3);
        masonryPane.setVSpacing(3);
        masonryPane.setCellWidth(182);
        masonryPane.setCellHeight(153);

        scrollPane.setContent(masonryPane);

        VBox vBox = new VBox();
        vBox.setPadding(new Insets(15));
        vBox.setPickOnBounds(false);
        vBox.setAlignment(Pos.BOTTOM_RIGHT);
        vBox.setSpacing(15);

        JFXButton btnRefresh = new JFXButton();
        btnRefresh.setPrefWidth(40);
        btnRefresh.setPrefHeight(40);
        btnRefresh.setButtonType(JFXButton.ButtonType.RAISED);
        btnRefresh.getStyleClass().setAll("jfx-button-raised-round");
        btnRefresh.setGraphic(SVG.refresh(Theme.foregroundFillBinding(), -1, -1));
        btnRefresh.setOnMouseClicked(e -> profile.getRepository().refreshVersionsAsync().start());
        FXUtils.installTooltip(btnRefresh, i18n("button.refresh"));

        vBox.getChildren().setAll(btnRefresh);

        contentPane.getChildren().setAll(scrollPane, vBox);

        getChildren().setAll(spinner);

        Profiles.selectedProfileProperty().addListener((o, a, b) -> this.profile = b);
        profile = Profiles.getSelectedProfile();

        EventBus.EVENT_BUS.channel(RefreshedVersionsEvent.class).register(event -> {
            if (event.getSource() == profile.getRepository())
                loadVersions((HMCLGameRepository) event.getSource());
        });
        EventBus.EVENT_BUS.channel(RefreshingVersionsEvent.class).register(event -> {
            if (event.getSource() == profile.getRepository())
                // This will occupy 0.5s. Too slow!
                JFXUtilities.runInFX(this::loadingVersions);
        });
        if (profile.getRepository().isLoaded())
            loadVersions(profile.getRepository());
        else
            profile.getRepository().refreshVersionsAsync().start();
    }

    private String modifyVersion(String gameVersion, String version) {
        return removeSuffix(removePrefix(removeSuffix(removePrefix(version.replace(gameVersion, "").trim(), "-"), "-"), "_"), "_");
    }

    private Node buildNode(HMCLGameRepository repository, Version version, Callable<String> gameCallable) {
        Profile profile = repository.getProfile();
        String id = version.getId();
        VersionItem item = new VersionItem();
        item.setUpdate(repository.isModpack(id));
        Task.ofResult("game", gameCallable).subscribe(Schedulers.javafx(), vars -> {
            String game = vars.get("game");
            item.setGameVersion(game);

            StringBuilder libraries = new StringBuilder();
            LibraryAnalyzer analyzer = LibraryAnalyzer.analyze(version);
            analyzer.getForge().ifPresent(library -> libraries.append(i18n("install.installer.forge")).append(": ").append(modifyVersion(game, library.getVersion().replaceAll("(?i)forge", ""))).append("\n"));
            analyzer.getLiteLoader().ifPresent(library -> libraries.append(i18n("install.installer.liteloader")).append(": ").append(modifyVersion(game, library.getVersion().replaceAll("(?i)liteloader", ""))).append("\n"));
            analyzer.getOptiFine().ifPresent(library -> libraries.append(i18n("install.installer.optifine")).append(": ").append(modifyVersion(game, library.getVersion().replaceAll("(?i)optifine", ""))).append("\n"));

            item.setLibraries(libraries.toString());
        });
        item.setVersionName(id);
        item.setOnLaunchButtonClicked(e -> Versions.launch(profile, id));
        item.setOnScriptButtonClicked(e -> Versions.generateLaunchScript(profile, id));
        item.setOnSettingsButtonClicked(e -> {
            Controllers.getVersionPage().load(id, profile);
            Controllers.navigate(Controllers.getVersionPage());
        });
        item.setOnUpdateButtonClicked(event -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle(i18n("modpack.choose"));
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(i18n("modpack"), "*.zip"));
            File selectedFile = chooser.showOpenDialog(Controllers.getStage());
            if (selectedFile != null) {
                AtomicReference<Region> region = new AtomicReference<>();
                try {
                    TaskExecutor executor = ModpackHelper.getUpdateTask(profile, selectedFile, id, ModpackHelper.readModpackConfiguration(repository.getModpackConfiguration(id)))
                            .then(Task.of(Schedulers.javafx(), () -> region.get().fireEvent(new DialogCloseEvent()))).executor();
                    region.set(Controllers.taskDialog(executor, i18n("modpack.update"), ""));
                    executor.start();
                } catch (UnsupportedModpackException e) {
                    region.get().fireEvent(new DialogCloseEvent());
                    Controllers.dialog(i18n("modpack.unsupported"), i18n("message.error"), MessageBox.ERROR_MESSAGE);
                } catch (MismatchedModpackTypeException e) {
                    region.get().fireEvent(new DialogCloseEvent());
                    Controllers.dialog(i18n("modpack.mismatched_type"), i18n("message.error"), MessageBox.ERROR_MESSAGE);
                } catch (IOException e) {
                    region.get().fireEvent(new DialogCloseEvent());
                    Controllers.dialog(i18n("modpack.invalid"), i18n("message.error"), MessageBox.ERROR_MESSAGE);
                }
            }
        });
        item.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.SECONDARY) {
                JFXListView<String> versionList = new JFXListView<>();
                JFXPopup versionPopup = new JFXPopup(versionList);
                versionList.getStyleClass().add("option-list-view");
                FXUtils.setLimitWidth(versionList, 150);
                versionList.getItems().setAll(Lang.immutableListOf(
                        i18n("version.manage.rename"),
                        i18n("version.manage.remove"),
                        i18n("modpack.export"),
                        i18n("folder.game")
                ));
                versionList.setOnMouseClicked(e -> {
                    versionPopup.hide();
                    switch (versionList.getSelectionModel().getSelectedIndex()) {
                        case 0:
                            Versions.renameVersion(profile, id);
                            break;
                        case 1:
                            Versions.deleteVersion(profile, id);
                            break;
                        case 2:
                            Versions.exportVersion(profile, id);
                            break;
                        case 3:
                            FXUtils.openFolder(repository.getRunDirectory(id));
                            break;
                        default:
                            break;
                    }
                });
                versionPopup.show(item, JFXPopup.PopupVPosition.TOP, JFXPopup.PopupHPosition.LEFT, event.getX(), event.getY());
            } else if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                Versions.launch(profile, id);
            }
        });
        File iconFile = repository.getVersionIcon(id);
        if (iconFile.exists())
            item.setImage(new Image("file:" + iconFile.getAbsolutePath()));
        return item;
    }

    private void loadingVersions() {
        getChildren().setAll(spinner);
        masonryPane.getChildren().clear();
    }

    private void loadVersions(HMCLGameRepository repository) {
        List<Node> children = repository.getVersions().parallelStream()
                .filter(version -> !version.isHidden())
                .sorted((a, b) -> VersionNumber.COMPARATOR.compare(VersionNumber.asVersion(a.getId()), VersionNumber.asVersion(b.getId())))
                .map(version -> buildNode(repository, version, () -> GameVersion.minecraftVersion(repository.getVersionJar(version.getId())).orElse("Unknown")))
                .collect(Collectors.toList());
        JFXUtilities.runInFX(() -> {
            if (profile == repository.getProfile()) {
                masonryPane.getChildren().setAll(children);
                getChildren().setAll(contentPane);
            }
        });
    }
}
