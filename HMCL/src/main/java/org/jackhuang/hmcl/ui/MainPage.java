/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2018  huangyuhui <huanghongxun2008@126.com>
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
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.image.Image;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import org.jackhuang.hmcl.Launcher;
import org.jackhuang.hmcl.event.EventBus;
import org.jackhuang.hmcl.event.ProfileChangedEvent;
import org.jackhuang.hmcl.event.RefreshedVersionsEvent;
import org.jackhuang.hmcl.event.RefreshingVersionsEvent;
import org.jackhuang.hmcl.game.*;
import org.jackhuang.hmcl.mod.MismatchedModpackTypeException;
import org.jackhuang.hmcl.mod.UnsupportedModpackException;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.setting.Settings;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskExecutor;
import org.jackhuang.hmcl.ui.construct.MessageBox;
import org.jackhuang.hmcl.ui.construct.TaskExecutorDialogPane;
import org.jackhuang.hmcl.ui.download.DownloadWizardProvider;
import org.jackhuang.hmcl.ui.wizard.DecoratorPage;
import org.jackhuang.hmcl.util.OperatingSystem;
import org.jackhuang.hmcl.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public final class MainPage extends StackPane implements DecoratorPage {

    private final StringProperty title = new SimpleStringProperty(this, "title", Launcher.i18n("main_page"));

    private Profile profile;
    private String rightClickedVersion;
    private HMCLGameRepository rightClickedRepository;

    @FXML
    private JFXButton btnRefresh;
    @FXML
    private StackPane contentPane;
    @FXML
    private JFXButton btnAdd;
    @FXML
    private JFXSpinner spinner;
    @FXML
    private JFXMasonryPane masonryPane;
    @FXML
    private JFXListView versionList;

    private final JFXPopup versionPopup;

    {
        FXUtils.loadFXML(this, "/assets/fxml/main.fxml");

        loadingVersions();

        EventBus.EVENT_BUS.channel(RefreshedVersionsEvent.class).register(event -> {
            if (event.getSource() == profile.getRepository())
                loadVersions((HMCLGameRepository) event.getSource());
        });
        EventBus.EVENT_BUS.channel(RefreshingVersionsEvent.class).register(event -> {
            if (event.getSource() == profile.getRepository())
                JFXUtilities.runInFXAndWait(this::loadingVersions);
        });
        EventBus.EVENT_BUS.channel(ProfileChangedEvent.class).register(event -> {
            this.profile = event.getProfile();
        });

        versionPopup = new JFXPopup(versionList);
        getChildren().remove(versionList);

        btnAdd.setOnMouseClicked(e -> Controllers.getDecorator().startWizard(new DownloadWizardProvider(), Launcher.i18n("install")));
        FXUtils.installTooltip(btnAdd, Launcher.i18n("install"));
        btnRefresh.setOnMouseClicked(e -> Settings.INSTANCE.getSelectedProfile().getRepository().refreshVersionsAsync().start());
        FXUtils.installTooltip(btnRefresh, Launcher.i18n("button.refresh"));
    }

    private Node buildNode(HMCLGameRepository repository, Version version, String game) {
        Profile profile = repository.getProfile();
        String id = version.getId();
        VersionItem item = new VersionItem();
        item.setUpdate(repository.isModpack(id));
        item.setGameVersion(game);
        item.setVersionName(id);

        StringBuilder libraries = new StringBuilder();
        for (Library library : version.getLibraries()) {
            if (library.getGroupId().equalsIgnoreCase("net.minecraftforge") && library.getArtifactId().equalsIgnoreCase("forge")) {
                libraries.append(Launcher.i18n("install.installer.forge")).append(": ").append(StringUtils.removeSuffix(StringUtils.removePrefix(library.getVersion().replaceAll("(?i)forge", "").replace(game, "").trim(), "-"), "-")).append("\n");
            }
            if (library.getGroupId().equalsIgnoreCase("com.mumfrey") && library.getArtifactId().equalsIgnoreCase("liteloader")) {
                libraries.append(Launcher.i18n("install.installer.liteloader")).append(": ").append(StringUtils.removeSuffix(StringUtils.removePrefix(library.getVersion().replaceAll("(?i)liteloader", "").replace(game, "").trim(), "-"), "-")).append("\n");
            }
            if (library.getGroupId().equalsIgnoreCase("net.optifine") && library.getArtifactId().equalsIgnoreCase("optifine")) {
                libraries.append(Launcher.i18n("install.installer.optifine")).append(": ").append(StringUtils.removeSuffix(StringUtils.removePrefix(library.getVersion().replaceAll("(?i)optifine", "").replace(game, "").trim(), "-"), "-")).append("\n");
            }
        }

        item.setLibraries(libraries.toString());
        item.setOnLaunchButtonClicked(e -> {
            if (Settings.INSTANCE.getSelectedAccount() == null)
                Controllers.dialog(Launcher.i18n("login.empty_username"));
            else
                LauncherHelper.INSTANCE.launch(profile, Settings.INSTANCE.getSelectedAccount(), id, null);
        });
        item.setOnScriptButtonClicked(e -> {
            if (Settings.INSTANCE.getSelectedAccount() == null)
                Controllers.dialog(Launcher.i18n("login.empty_username"));
            else {
                FileChooser chooser = new FileChooser();
                chooser.setInitialDirectory(repository.getRunDirectory(id));
                chooser.setTitle(Launcher.i18n("version.launch_script.save"));
                chooser.getExtensionFilters().add(OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS
                        ? new FileChooser.ExtensionFilter(Launcher.i18n("extension.bat"), "*.bat")
                        : new FileChooser.ExtensionFilter(Launcher.i18n("extension.sh"), "*.sh"));
                File file = chooser.showSaveDialog(Controllers.getStage());
                if (file != null)
                    LauncherHelper.INSTANCE.launch(profile, Settings.INSTANCE.getSelectedAccount(), id, file);
            }
        });
        item.setOnSettingsButtonClicked(e -> {
            Controllers.getDecorator().showPage(Controllers.getVersionPage());
            Controllers.getVersionPage().load(id, profile);
        });
        item.setOnUpdateButtonClicked(event -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle(Launcher.i18n("modpack.choose"));
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(Launcher.i18n("modpack"), "*.zip"));
            File selectedFile = chooser.showOpenDialog(Controllers.getStage());
            if (selectedFile != null) {
                TaskExecutorDialogPane pane = new TaskExecutorDialogPane(null);
                try {
                    TaskExecutor executor = ModpackHelper.getUpdateTask(profile, selectedFile, id, ModpackHelper.readModpackConfiguration(repository.getModpackConfiguration(id)))
                            .then(Task.of(Schedulers.javafx(), Controllers::closeDialog)).executor();
                    pane.setExecutor(executor);
                    pane.setTitle(Launcher.i18n("modpack.update"));
                    executor.start();
                    Controllers.dialog(pane);
                } catch (UnsupportedModpackException e) {
                    Controllers.dialog(Launcher.i18n("modpack.unsupported"), Launcher.i18n("message.error"), MessageBox.ERROR_MESSAGE);
                } catch (MismatchedModpackTypeException e) {
                    Controllers.dialog(Launcher.i18n("modpack.mismatched_type"), Launcher.i18n("message.error"), MessageBox.ERROR_MESSAGE);
                } catch (IOException e)  {
                    Controllers.dialog(Launcher.i18n("modpack.invalid"), Launcher.i18n("message.error"), MessageBox.ERROR_MESSAGE);
                }
            }
        });
        item.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.SECONDARY) {
                rightClickedVersion = id;
                rightClickedRepository = repository;
                versionList.getSelectionModel().select(-1);
                versionPopup.show(item, JFXPopup.PopupVPosition.TOP, JFXPopup.PopupHPosition.LEFT, event.getX(), event.getY());
            } else if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                if (Settings.INSTANCE.getSelectedAccount() == null)
                    Controllers.dialog(Launcher.i18n("login.empty_username"));
                else
                    LauncherHelper.INSTANCE.launch(profile, Settings.INSTANCE.getSelectedAccount(), id, null);
            }
        });
        File iconFile = repository.getVersionIcon(id);
        if (iconFile.exists())
            item.setImage(new Image("file:" + iconFile.getAbsolutePath()));
        return item;
    }

    private void loadingVersions() {
        contentPane.getChildren().setAll(spinner);
        FXUtils.resetChildren(masonryPane, Collections.emptyList());
    }

    private void loadVersions(HMCLGameRepository repository) {
        List<Node> children = new LinkedList<>();
        for (Version version : repository.getVersions()) {
            children.add(buildNode(repository, version, GameVersion.minecraftVersion(repository.getVersionJar(version.getId())).orElse("Unknown")));
        }
        JFXUtilities.runInFX(() -> {
            if (profile == repository.getProfile()) {
                contentPane.getChildren().setAll(masonryPane);
                FXUtils.resetChildren(masonryPane, children);
            }
        });
    }

    @FXML
    private void onVersionManagement() {
        versionPopup.hide();
        switch (versionList.getSelectionModel().getSelectedIndex()) {
            case 0:
                VersionPage.renameVersion(rightClickedRepository.getProfile(), rightClickedVersion);
                break;
            case 1:
                VersionPage.deleteVersion(rightClickedRepository.getProfile(), rightClickedVersion);
                break;
            case 2:
                VersionPage.exportVersion(rightClickedRepository.getProfile(), rightClickedVersion);
                break;
            case 3:
                FXUtils.openFolder(rightClickedRepository.getRunDirectory(rightClickedVersion));
                break;
            default:
                throw new Error();
        }
    }

    public String getTitle() {
        return title.get();
    }

    @Override
    public StringProperty titleProperty() {
        return title;
    }

    public void setTitle(String title) {
        this.title.set(title);
    }
}
