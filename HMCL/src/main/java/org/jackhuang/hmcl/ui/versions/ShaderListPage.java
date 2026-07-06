/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026 huangyuhui <huanghongxun2008@126.com> and contributors
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

import com.jfoenix.controls.*;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.jackhuang.hmcl.addon.RemoteAddon;
import org.jackhuang.hmcl.addon.RemoteAddonRepository;
import org.jackhuang.hmcl.addon.repository.CurseForgeRemoteAddonRepository;
import org.jackhuang.hmcl.addon.repository.ModrinthRemoteAddonRepository;
import org.jackhuang.hmcl.addon.shader.ShaderFile;
import org.jackhuang.hmcl.game.HMCLGameRepository;
import org.jackhuang.hmcl.setting.DownloadProviders;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.ListPageBase;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.animation.ContainerAnimations;
import org.jackhuang.hmcl.ui.animation.TransitionPane;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.util.Pair;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.TaskCancellationAction;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;

import static org.jackhuang.hmcl.ui.FXUtils.ignoreEvent;
import static org.jackhuang.hmcl.ui.FXUtils.onEscPressed;
import static org.jackhuang.hmcl.ui.ToolbarListPageSkin.createToolbarButton2;
import static org.jackhuang.hmcl.util.Pair.pair;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public class ShaderListPage extends ListPageBase<ShaderFile> implements VersionPage.GameInstanceLoadable {

    private final ReentrantLock lock = new ReentrantLock();

    private Path shadersDir;
    private HMCLGameRepository repository;
    private String instanceId;

    public ShaderListPage() {
        FXUtils.applyDragListener(this, ShaderFile::isFileShaderPack, this::addFiles);
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new ShaderListPageSkin(this);
    }

    @Override
    public void loadInstance(HMCLGameRepository repository, @Nullable String instanceId) {
        this.repository = repository;
        this.instanceId = instanceId;
        this.shadersDir = repository.getShadersDirectory(instanceId);
        refresh();
    }

    public void refresh() {
        if (shadersDir == null) return;
        setLoading(true);
        Task.supplyAsync(Schedulers.io(), () -> {
            lock.lock();
            try (var stream = Files.list(shadersDir)) {
                return stream.map(file -> {
                    try {
                        return ShaderFile.fromFile(file);
                    } catch (IOException e) {
                        LOG.warning("Failed to load shader pack " + file, e);
                        return null;
                    }
                }).filter(Objects::nonNull).toList();
            } finally {
                lock.unlock();
            }
        }).whenComplete(Schedulers.javafx(), (result, exception) -> {
            if (exception == null) {
                getItems().setAll(result);
            } else {
                LOG.warning("Failed to load shader packs", exception);
                getItems().clear();
            }
            setLoading(false);
        }).start();
    }

    public void addFiles(List<Path> files) {
        if (shadersDir == null) return;
        List<Path> failures = new ArrayList<>();
        Task.runAsync(() -> {
            lock.lock();
            try {
                for (Path file : files) {
                    if (ShaderFile.isFileShaderPack(file)) {
                        try {
                            FileUtils.copyTo(file, shadersDir);
                        } catch (Exception e) {
                            LOG.warning("Failed to add shader pack " + file, e);
                            failures.add(file);
                        }
                    } else {
                        LOG.warning("Failed to add shader pack", new IllegalArgumentException("File '" + file + "' is not a shader pack"));
                        failures.add(file);
                    }
                }
            } finally {
                lock.unlock();
            }
        }).withRunAsync(Schedulers.javafx(), () -> {
            if (!failures.isEmpty()) {
                StringBuilder failure = new StringBuilder(i18n("shaderpack.add.failed"));
                for (Path file: failures) {
                    failure.append("\n").append(file.toString());
                }
                Controllers.dialog(failure.toString(), i18n("message.error"), MessageDialogPane.MessageType.ERROR);
            }
            refresh();
        }).start();
    }

    public void removeFiles(List<ShaderFile> selectedItems) {
        try {
            for (var shader : selectedItems) {
                shader.delete();
            }
        } catch (IOException e) {
            Controllers.dialog(i18n("shaderpack.delete.failed", e.getMessage()), i18n("message.error"), MessageDialogPane.MessageType.ERROR);
            LOG.warning("Failed to delete shader packs", e);
        }
        refresh();
    }

    public void checkUpdates(Collection<ShaderFile> shaderPacks) {
        if (shadersDir == null) return;
        Runnable action = () -> Controllers.taskDialog(Task
                        .composeAsync(() -> {
                            Optional<String> gameVersion = repository.getGameVersion(instanceId);
                            return gameVersion.map(g -> new AddonCheckUpdatesTask(DownloadProviders.getDownloadProvider(), g, shaderPacks)).orElse(null);
                        })
                        .whenComplete(Schedulers.javafx(), (result, exception) -> {
                            if (exception != null || result == null) {
                                Controllers.dialog(i18n("addon.check_update.failed_check"), i18n("message.failed"), MessageDialogPane.MessageType.ERROR);
                            } else if (result.isEmpty()) {
                                Controllers.dialog(i18n("addon.check_update.empty"));
                            } else {
                                Controllers.navigateForward(new AddonUpdatesPage(shadersDir, result));
                            }
                        })
                        .withStagesHints("update.checking"),
                i18n("addon.check_update"), TaskCancellationAction.NORMAL);

        if (repository.isModpack(instanceId)) {
            Controllers.confirm(
                    i18n("resourcepack.update_in_modpack.warning"), null,
                    MessageDialogPane.MessageType.WARNING,
                    action, null);
        } else {
            action.run();
        }
    }

    private void onAddFiles() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(i18n("shaderpack.add"));
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(i18n("extension.shaderpack"), "*.zip"));
        List<Path> files = FileUtils.toPaths(fileChooser.showOpenMultipleDialog(Controllers.getStage()));
        if (files != null && !files.isEmpty()) {
            addFiles(files);
        }
    }

    private void onDownload() {
        Controllers.getDownloadPage().showShaderDownloads().selectVersion(instanceId);
        Controllers.navigate(Controllers.getDownloadPage());
    }

    private void onOpenFolder() {
        if (shadersDir != null) {
            FXUtils.openFolder(shadersDir);
        }
    }

    private static Image getOrCreateIcon(ShaderFile shaderFile) {
        Image image = shaderFile.getIcon();
        if (image == null || image.isError() || image.getWidth() <= 0 || image.getHeight() <= 0 ||
                (Math.abs(image.getWidth() - image.getHeight()) >= 1)) {
            image = switch (shaderFile.getLoaderType()) {
                case OPTIFINE_IRIS -> FXUtils.newBuiltinImage("/assets/img/opti-iris.png");
                default -> ResourcePackListPage.UNKNOWN_PACK_IMAGE.get();
            };
        }
        return image;
    }

    private static final class ShaderListPageSkin extends SkinBase<ShaderListPage> {
        private final JFXListView<ShaderFile> listView;
        private final JFXTextField searchField = new JFXTextField();

        private final TransitionPane toolbarPane = new TransitionPane();
        private final HBox searchBar = new HBox();
        private final HBox toolbarNormal = new HBox();
        private final HBox toolbarSelecting = new HBox();

        private boolean isSearching;

        public ShaderListPageSkin(ShaderListPage control) {
            super(control);

            StackPane pane = new StackPane();
            pane.setPadding(new Insets(10));
            pane.getStyleClass().addAll("notice-pane");

            ComponentList root = new ComponentList();
            root.getStyleClass().add("no-padding");

            listView = new JFXListView<>();

            {

                // Toolbar Selecting
                toolbarSelecting.getChildren().setAll(
                        createToolbarButton2(i18n("button.remove"), SVG.DELETE_FOREVER, () -> {
                            Controllers.confirm(i18n("button.remove.confirm"), i18n("button.remove"), () ->
                                    control.removeFiles(listView.getSelectionModel().getSelectedItems()),
                                    null);
                        }),
                        createToolbarButton2(i18n("addon.check_update.button"), SVG.UPDATE, () ->
                                control.checkUpdates(listView.getSelectionModel().getSelectedItems().stream().toList())
                        ),
                        createToolbarButton2(i18n("button.select_all"), SVG.SELECT_ALL, () ->
                                listView.getSelectionModel().selectAll()),
                        createToolbarButton2(i18n("button.cancel"), SVG.CANCEL, () ->
                                listView.getSelectionModel().clearSelection())
                );

                // Search Bar
                searchBar.setAlignment(Pos.CENTER);
                searchBar.setPadding(new Insets(0, 5, 0, 5));
                searchField.setPromptText(i18n("search"));
                HBox.setHgrow(searchField, Priority.ALWAYS);
                PauseTransition pause = new PauseTransition(Duration.millis(100));
                pause.setOnFinished(e -> search());
                FXUtils.onChange(searchField.textProperty(), newValue -> {
                    pause.setRate(1);
                    pause.playFromStart();
                });

                JFXButton closeSearchBar = createToolbarButton2(null, SVG.CLOSE,
                        () -> {
                            changeToolbar(toolbarNormal);

                            isSearching = false;
                            searchField.clear();
                            Bindings.bindContent(listView.getItems(), getSkinnable().getItems());
                        });

                onEscPressed(searchField, closeSearchBar::fire);

                searchBar.getChildren().setAll(searchField, closeSearchBar);

                // Toolbar Normal
                toolbarNormal.setAlignment(Pos.CENTER_LEFT);
                toolbarNormal.setPickOnBounds(false);
                toolbarNormal.getChildren().setAll(
                        createToolbarButton2(i18n("button.refresh"), SVG.REFRESH, control::refresh),
                        createToolbarButton2(i18n("resourcepack.add"), SVG.ADD, control::onAddFiles),
                        createToolbarButton2(i18n("button.reveal_dir"), SVG.FOLDER_OPEN, control::onOpenFolder),
                        createToolbarButton2(i18n("addon.check_update.button"), SVG.UPDATE, () ->
                                control.checkUpdates(listView.getItems().stream().toList())
                        ),
                        createToolbarButton2(i18n("download"), SVG.DOWNLOAD, control::onDownload),
                        createToolbarButton2(i18n("search"), SVG.SEARCH, () -> changeToolbar(searchBar))
                );

                FXUtils.onChangeAndOperate(listView.getSelectionModel().selectedItemProperty(),
                        selectedItem -> {
                            if (selectedItem == null)
                                changeToolbar(isSearching ? searchBar : toolbarNormal);
                            else
                                changeToolbar(toolbarSelecting);
                        });
                FXUtils.setOverflowHidden(toolbarPane, 8);
                root.getContent().add(toolbarPane);

                // Clear selection when pressing ESC
                root.addEventHandler(KeyEvent.KEY_PRESSED, e -> {
                    if (e.getCode() == KeyCode.ESCAPE) {
                        if (listView.getSelectionModel().getSelectedItem() != null) {
                            listView.getSelectionModel().clearSelection();
                            e.consume();
                        }
                    }
                });
            }

            {
                SpinnerPane center = new SpinnerPane();
                ComponentList.setVgrow(center, Priority.ALWAYS);
                center.loadingProperty().bind(control.loadingProperty());

                listView.setCellFactory(x -> new ShaderListCell(listView, control));
                listView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
                Bindings.bindContent(listView.getItems(), control.getItems());

                listView.setOnContextMenuRequested(event -> {
                    ShaderFile selectedItem = listView.getSelectionModel().getSelectedItem();
                    if (selectedItem != null && listView.getSelectionModel().getSelectedItems().size() == 1) {
                        listView.getSelectionModel().clearSelection();
                        Controllers.dialog(new ShaderInfoDialog(control, selectedItem));
                    }
                });

                ignoreEvent(listView, KeyEvent.KEY_PRESSED, e -> e.getCode() == KeyCode.ESCAPE);
                listView.getStyleClass().add("no-horizontal-scrollbar");

                center.setContent(listView);
                root.getContent().add(center);
            }

            pane.getChildren().setAll(root);
            getChildren().setAll(pane);
        }

        private void changeToolbar(HBox newToolbar) {
            Node oldToolbar = toolbarPane.getCurrentNode();
            if (newToolbar != oldToolbar) {
                toolbarPane.setContent(newToolbar, ContainerAnimations.FADE);
                if (newToolbar == searchBar) {
                    Platform.runLater(searchField::requestFocus);
                }
            }
        }

        private void search() {
            isSearching = true;

            Bindings.unbindContent(listView.getItems(), getSkinnable().getItems());

            String queryString = searchField.getText();
            if (StringUtils.isBlank(queryString)) {
                listView.getItems().setAll(getSkinnable().getItems());
            } else {
                listView.getItems().clear();

                Predicate<@Nullable String> predicate;
                try {
                    predicate = StringUtils.compileQuery(queryString);
                } catch (Throwable e) {
                    LOG.warning("Illegal regular expression", e);
                    return;
                }

                // Do we need to search in the background thread?
                for (ShaderFile item : getSkinnable().getItems()) {
                    if (predicate.test(item.getFile().getFileName().toString())
                            || predicate.test(item.getFileName())) {
                        listView.getItems().add(item);
                    }
                }
            }
        }

    }

    private static final class ShaderListCell extends MDListCell<ShaderFile> {

        private final ShaderListPage page;

        private final ImageContainer imageContainer = new ImageContainer(24);
        private final TwoLineListItem content = new TwoLineListItem();
        private final JFXButton btnReveal = FXUtils.newToggleButton4(SVG.FOLDER);
        private final JFXButton btnInfo = FXUtils.newToggleButton4(SVG.INFO);

        public ShaderListCell(JFXListView<ShaderFile> listView, ShaderListPage page) {
            super(listView);
            this.page = page;

            HBox root = new HBox(8);
            root.setPickOnBounds(false);
            root.setAlignment(Pos.CENTER_LEFT);

            HBox.setHgrow(content, Priority.ALWAYS);
            content.setMouseTransparent(true);

            root.getChildren().setAll(imageContainer, content, btnReveal, btnInfo);

            setSelectable();

            StackPane.setMargin(root, new Insets(8));
            getContainer().getChildren().add(root);
        }

        @Override
        protected void updateControl(ShaderFile item, boolean empty) {
            if (empty || item == null) return;

            imageContainer.setImage(getOrCreateIcon(item));

            content.getTags().clear();
            content.setTitle(item.getFileName());
            {
                var apertureMeta = item.getApertureMeta();
                content.setSubtitle(apertureMeta != null ? apertureMeta.name() + " " + apertureMeta.version() : "");
            }

            FXUtils.installFastTooltip(btnReveal, i18n("reveal.in_file_manager"));
            btnReveal.setOnAction(event -> FXUtils.showFileInExplorer(item.getFile()));

            btnInfo.setOnAction(e -> Controllers.dialog(new ShaderInfoDialog(this.page, item)));
        }
    }

    private static final class ShaderInfoDialog extends JFXDialogLayout {

        public ShaderInfoDialog(ShaderListPage page, ShaderFile shaderFile) {

            HBox titleContainer = new HBox();
            titleContainer.setSpacing(8);

            Stage stage = Controllers.getStage();
            maxWidthProperty().bind(stage.widthProperty().multiply(0.7));

            ImageContainer imageContainer = new ImageContainer(40);
            imageContainer.setImage(getOrCreateIcon(shaderFile));

            TwoLineListItem title = new TwoLineListItem();
            title.setTitle(shaderFile.getFileName());
            title.setSubtitle(shaderFile.getFile().getFileName().toString());
            title.addTag(switch (shaderFile.getLoaderType()) {
                case OPTIFINE_IRIS -> i18n("shaderpack.loader.optifine_iris");
                case APERTURE -> i18n("shaderpack.loader.aperture");
            });

            titleContainer.getChildren().setAll(imageContainer, title);
            setHeading(titleContainer);

            Label description = new Label(shaderFile.getApertureMeta() == null ? ""
                    : shaderFile.getApertureMeta().name() + " " + shaderFile.getApertureMeta().version());
            description.setWrapText(true);
            FXUtils.copyOnDoubleClick(description);

            ScrollPane descriptionPane = new ScrollPane(description);
            FXUtils.smoothScrolling(descriptionPane);
            descriptionPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            descriptionPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
            descriptionPane.setFitToWidth(true);
            FXUtils.onChange(description.heightProperty(), newVal -> {
                double maxHeight = stage.getHeight() * 0.5;
                double targetHeight = Math.min(newVal.doubleValue(), maxHeight);
                descriptionPane.setPrefViewportHeight(targetHeight);
            });

            setBody(descriptionPane);

            for (Pair<String, ? extends RemoteAddonRepository> item : Arrays.asList(
                    pair("addon.curseforge", CurseForgeRemoteAddonRepository.SHADERS),
                    pair("addon.modrinth", ModrinthRemoteAddonRepository.SHADER_PACKS)
            )) {
                RemoteAddonRepository repository = item.getValue();
                JFXHyperlink button = new JFXHyperlink(i18n(item.getKey()));
                Task.runAsync(() -> {
                    Optional<RemoteAddon.Version> versionOptional = repository.getRemoteVersionByLocalFile(shaderFile.getFile());
                    if (versionOptional.isPresent()) {
                        RemoteAddon remoteAddon = repository.getModById(DownloadProviders.getDownloadProvider(), versionOptional.get().modid());
                        FXUtils.runInFX(() -> {
                            button.setOnAction(e -> {
                                fireEvent(new DialogCloseEvent());
                                Controllers.navigate(new DownloadPage(
                                        repository instanceof CurseForgeRemoteAddonRepository
                                                ? HMCLLocalizedDownloadListPage.ofCurseForgeShaderPack(false)
                                                : HMCLLocalizedDownloadListPage.ofModrinthShaderPack(false),
                                        remoteAddon,
                                        new HMCLGameRepository.InstanceReference(page.repository, page.instanceId),
                                        null
                                ));
                            });
                            button.setDisable(false);
                        });
                    }
                }).start();
                button.setDisable(true);
                getActions().add(button);
            }

            JFXButton okButton = new JFXButton();
            okButton.getStyleClass().add("dialog-accept");
            okButton.setText(i18n("button.ok"));
            okButton.setOnAction(e -> fireEvent(new DialogCloseEvent()));
            getActions().add(okButton);

            onEscPressed(this, okButton::fire);
        }

    }
}
