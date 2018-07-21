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
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;

import org.jackhuang.hmcl.event.EventBus;
import org.jackhuang.hmcl.event.ProfileChangedEvent;
import org.jackhuang.hmcl.event.RefreshedVersionsEvent;
import org.jackhuang.hmcl.event.RefreshingVersionsEvent;
import org.jackhuang.hmcl.game.*;
import org.jackhuang.hmcl.mod.MismatchedModpackTypeException;
import org.jackhuang.hmcl.mod.UnsupportedModpackException;
import org.jackhuang.hmcl.setting.Accounts;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.setting.Settings;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskExecutor;
import org.jackhuang.hmcl.ui.construct.DialogCloseEvent;
import org.jackhuang.hmcl.ui.construct.MessageBox;
import org.jackhuang.hmcl.ui.download.DownloadWizardProvider;
import org.jackhuang.hmcl.ui.wizard.DecoratorPage;
import org.jackhuang.hmcl.util.Lang;
import org.jackhuang.hmcl.util.OperatingSystem;
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

public final class MainPage extends StackPane implements DecoratorPage {

    private final StringProperty title = new SimpleStringProperty(this, "title", i18n("main_page"));

    private Profile profile;

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
    private ScrollPane scrollPane;

    {
        FXUtils.loadFXML(this, "/assets/fxml/main.fxml");

        loadingVersions();

        EventBus.EVENT_BUS.channel(RefreshedVersionsEvent.class).register(event -> {
            if (event.getSource() == profile.getRepository())
                loadVersions((HMCLGameRepository) event.getSource());
        });
        EventBus.EVENT_BUS.channel(RefreshingVersionsEvent.class).register(event -> {
            if (event.getSource() == profile.getRepository())
                // This will occupy 0.5s. Too slow!
                JFXUtilities.runInFX(this::loadingVersions);
        });
        EventBus.EVENT_BUS.channel(ProfileChangedEvent.class).register(event -> {
            this.profile = event.getProfile();
        });

        btnAdd.setOnMouseClicked(e -> Controllers.getDecorator().startWizard(new DownloadWizardProvider(), i18n("install")));
        FXUtils.installTooltip(btnAdd, i18n("install"));
        btnRefresh.setOnMouseClicked(e -> Settings.INSTANCE.getSelectedProfile().getRepository().refreshVersionsAsync().start());
        FXUtils.installTooltip(btnRefresh, i18n("button.refresh"));
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
            for (Library library : version.getLibraries()) {
                if (library.getGroupId().equalsIgnoreCase("net.minecraftforge") && library.getArtifactId().equalsIgnoreCase("forge")) {
                    libraries.append(i18n("install.installer.forge")).append(": ").append(modifyVersion(game, library.getVersion().replaceAll("(?i)forge", ""))).append("\n");
                }
                if (library.getGroupId().equalsIgnoreCase("com.mumfrey") && library.getArtifactId().equalsIgnoreCase("liteloader")) {
                    libraries.append(i18n("install.installer.liteloader")).append(": ").append(modifyVersion(game, library.getVersion().replaceAll("(?i)liteloader", ""))).append("\n");
                }
                if (library.getGroupId().equalsIgnoreCase("net.optifine") && library.getArtifactId().equalsIgnoreCase("optifine")) {
                    libraries.append(i18n("install.installer.optifine")).append(": ").append(modifyVersion(game, library.getVersion().replaceAll("(?i)optifine", ""))).append("\n");
                }
            }

            item.setLibraries(libraries.toString());
        });
        item.setVersionName(id);
        item.setOnLaunchButtonClicked(e -> {
            if (Accounts.getSelectedAccount() == null)
                Controllers.getLeftPaneController().checkAccount();
            else
                LauncherHelper.INSTANCE.launch(profile, Accounts.getSelectedAccount(), id, null);
        });
        item.setOnScriptButtonClicked(e -> {
            if (Accounts.getSelectedAccount() == null)
                Controllers.dialog(i18n("login.empty_username"));
            else {
                FileChooser chooser = new FileChooser();
                if (repository.getRunDirectory(id).isDirectory())
                    chooser.setInitialDirectory(repository.getRunDirectory(id));
                chooser.setTitle(i18n("version.launch_script.save"));
                chooser.getExtensionFilters().add(OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS
                        ? new FileChooser.ExtensionFilter(i18n("extension.bat"), "*.bat")
                        : new FileChooser.ExtensionFilter(i18n("extension.sh"), "*.sh"));
                File file = chooser.showSaveDialog(Controllers.getStage());
                if (file != null)
                    LauncherHelper.INSTANCE.launch(profile, Accounts.getSelectedAccount(), id, file);
            }
        });
        item.setOnSettingsButtonClicked(e -> {
            Controllers.getDecorator().showPage(Controllers.getVersionPage());
            Controllers.getVersionPage().load(id, profile);
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
                            VersionPage.renameVersion(profile, id);
                            break;
                        case 1:
                            VersionPage.deleteVersion(profile, id);
                            break;
                        case 2:
                            VersionPage.exportVersion(profile, id);
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
                if (Accounts.getSelectedAccount() == null)
                    Controllers.dialog(i18n("login.empty_username"));
                else
                    LauncherHelper.INSTANCE.launch(profile, Accounts.getSelectedAccount(), id, null);
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
