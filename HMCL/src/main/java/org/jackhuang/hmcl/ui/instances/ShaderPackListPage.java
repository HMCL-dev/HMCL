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
package org.jackhuang.hmcl.ui.instances;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXDialogLayout;
import com.jfoenix.controls.JFXListView;
import com.jfoenix.controls.JFXTextField;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.value.ObservableValue;
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
import javafx.util.Duration;
import org.jackhuang.hmcl.addon.RemoteAddon;
import org.jackhuang.hmcl.addon.RemoteAddonRepository;
import org.jackhuang.hmcl.addon.repository.CurseForgeRemoteAddonRepository;
import org.jackhuang.hmcl.addon.repository.ModrinthRemoteAddonRepository;
import org.jackhuang.hmcl.addon.shader.ShaderPackFile;
import org.jackhuang.hmcl.addon.shader.ShaderPackManager;
import org.jackhuang.hmcl.game.HMCLGameInstance;
import org.jackhuang.hmcl.setting.DownloadProviders;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.*;
import org.jackhuang.hmcl.ui.animation.ContainerAnimations;
import org.jackhuang.hmcl.ui.animation.TransitionPane;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.util.Pair;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.TaskCancellationAction;
import org.jackhuang.hmcl.util.i18n.I18n;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
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

public class ShaderPackListPage extends ListPageBase<ShaderPackFile> {

    private final ReentrantLock lock = new ReentrantLock();

    private final WeakListenerHolder listenerHolder = new WeakListenerHolder();
    private @Nullable HMCLGameInstance gameInstance;

    private Path shaderPackDir;
    private ShaderPackManager shaderPackManager;

    public ShaderPackListPage(ObservableValue<? extends HMCLGameInstance.Optional> instanceContext) {
        Objects.requireNonNull(instanceContext, "instanceContext");
        FXUtils.applyDragListener(this, ShaderPackFile::isFileShaderPack, this::addFiles);

        listenerHolder.add(FXUtils.onWeakChangeAndOperate(instanceContext, current -> {
            if (current != null) {
                loadInstance(current);
            }
        }));
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new ShaderPackListPageSkin(this);
    }

    public void loadInstance(HMCLGameInstance.Optional instance) {
        this.gameInstance = instance.instance();
        if (gameInstance == null) {
            this.shaderPackDir = null;
            this.shaderPackManager = null;
            getItems().clear();
            return;
        }

        this.shaderPackDir = gameInstance.getShadersDirectory();
        this.shaderPackManager = gameInstance.getShaderPackManager();

        refresh();
    }

    public void refresh() {
        if (shaderPackManager == null) return;
        setLoading(true);
        Task.supplyAsync(Schedulers.io(), () -> {
            lock.lock();
            try {
                shaderPackManager.refresh();
                return shaderPackManager.getLocalFiles();
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
        if (shaderPackManager == null) return;

        List<Path> failures = new ArrayList<>();
        Task.runAsync(() -> {
            for (Path file : files) {
                try {
                    shaderPackManager.importShaderPack(file);
                } catch (Exception e) {
                    LOG.warning("Failed to add shader pack " + file, e);
                    failures.add(file);
                }
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

    public void removeFiles(List<ShaderPackFile> selectedItems) {
        try {
            shaderPackManager.removeShaderPacks(selectedItems);
        } catch (IOException e) {
            Controllers.dialog(i18n("shaderpack.delete.failed", e.getMessage()), i18n("message.error"), MessageDialogPane.MessageType.ERROR);
            LOG.warning("Failed to delete shader packs", e);
        }
        refresh();
    }

    public void checkUpdates(Collection<ShaderPackFile> shaderPacks) {
        HMCLGameInstance gameInstance = this.gameInstance;
        if (gameInstance == null) return;

        Controllers.taskDialog(
                Task.composeAsync(() -> {
                    GameVersionNumber version = gameInstance.getVersion();
                    return version != GameVersionNumber.unknown()
                            ? new AddonCheckUpdatesTask(DownloadProviders.getDownloadProvider(),
                            version.toString(), shaderPacks)
                            : null;
                }).whenComplete(Schedulers.javafx(), (result, exception) -> {
                    if (exception != null || result == null) {
                        Controllers.dialog(I18n.i18n("addon.check_update.failed_check"), I18n.i18n("message.failed"), MessageDialogPane.MessageType.ERROR);
                    } else if (result.isEmpty()) {
                        Controllers.dialog(I18n.i18n("addon.check_update.empty"));
                    } else {
                        Controllers.navigateForward(new AddonUpdatesPage(shaderPackDir, result));
                    }
                }).withStagesHints("update.checking"),
                I18n.i18n("addon.check_update"), TaskCancellationAction.NORMAL);
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
        if (gameInstance == null) return;
        Controllers.getDownloadPage().showShaderDownloads().selectInstance(gameInstance.getId());
        Controllers.navigate(Controllers.getDownloadPage());
    }

    private void onOpenFolder() {
        if (shaderPackDir != null) {
            FXUtils.openFolder(shaderPackDir);
        }
    }

    private static Image getOrCreateIcon(ShaderPackFile shaderPackFile) {
        Image image = shaderPackFile.loadIcon();
        if (image == null || image.isError() || image.getWidth() <= 0 || image.getHeight() <= 0 ||
                (Math.abs(image.getWidth() - image.getHeight()) >= 1)) {
            image = switch (shaderPackFile.getLoaderType()) {
                case OPTIFINE_IRIS -> FXUtils.newBuiltinImage("/assets/img/opti-iris.png");
                default -> ResourcePackListPage.UNKNOWN_PACK_IMAGE.get();
            };
        }
        return image;
    }

    private static final class ShaderPackListPageSkin extends SkinBase<ShaderPackListPage> {
        private final JFXListView<ShaderPackFile> listView;
        private final JFXTextField searchField = new JFXTextField();

        private final TransitionPane toolbarPane = new TransitionPane();
        private final HBox searchBar = new HBox();
        private final HBox toolbarNormal = new HBox();
        private final HBox toolbarSelecting = new HBox();

        private boolean isSearching;

        public ShaderPackListPageSkin(ShaderPackListPage control) {
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
                        createToolbarButton2(i18n("shaderpack.add"), SVG.ADD, control::onAddFiles),
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

                listView.setCellFactory(x -> new ShaderPackListCell(listView));
                listView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
                Bindings.bindContent(listView.getItems(), control.getItems());

                listView.setOnContextMenuRequested(event -> {
                    ShaderPackFile selectedItem = listView.getSelectionModel().getSelectedItem();
                    if (selectedItem != null && listView.getSelectionModel().getSelectedItems().size() == 1) {
                        listView.getSelectionModel().clearSelection();
                        Controllers.dialog(new ShaderPackInfoDialog(selectedItem));
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
                for (ShaderPackFile item : getSkinnable().getItems()) {
                    var meta = item.getMeta();
                    if (predicate.test(item.getFile().getFileName().toString())
                            || predicate.test(item.getName())
                            || (meta != null && (predicate.test(meta.version()) || predicate.test(meta.description())))) {
                        listView.getItems().add(item);
                    }
                }
            }
        }

    }

    private static final class ShaderPackListCell extends MDListCell<ShaderPackFile> {

        private final ImageContainer imageContainer = new ImageContainer(24);
        private final TwoLineListItem content = new TwoLineListItem();
        private final JFXButton btnReveal = FXUtils.newToggleButton4(SVG.FOLDER);
        private final JFXButton btnInfo = FXUtils.newToggleButton4(SVG.INFO);

        public ShaderPackListCell(JFXListView<ShaderPackFile> listView) {
            super(listView);

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
        protected void updateControl(ShaderPackFile item, boolean empty) {
            if (empty || item == null) return;

            imageContainer.setImage(getOrCreateIcon(item));

            content.getTags().clear();
            content.setTitle(item.getFileName());
            content.setSubtitle(item.getMeta() == null || item.getMeta().name() == null ? "" : item.getMeta().name());
            content.addTag(switch (item.getLoaderType()) {
                case OPTIFINE_IRIS -> i18n("shaderpack.loader.optifine_iris");
                case APERTURE -> i18n("shaderpack.loader.aperture");
            });
            if (item.getMeta() != null && item.getMeta().version() != null)
                content.addTag(item.getMeta().version());

            FXUtils.installFastTooltip(btnReveal, i18n("reveal.in_file_manager"));
            btnReveal.setOnAction(event -> FXUtils.showFileInExplorer(item.getFile()));

            btnInfo.setOnAction(e -> Controllers.dialog(new ShaderPackInfoDialog(item)));
        }
    }

    private static final class ShaderPackInfoDialog extends JFXDialogLayout {

        public ShaderPackInfoDialog(ShaderPackFile shaderPackFile) {

            HBox titleContainer = new HBox();
            titleContainer.setSpacing(8);

            maxWidthProperty().bind(Controllers.getDecorator().contentWidthProperty().multiply(0.7));

            ImageContainer imageContainer = new ImageContainer(40);
            imageContainer.setImage(getOrCreateIcon(shaderPackFile));

            TwoLineListItem title = new TwoLineListItem();
            title.setTitle(shaderPackFile.getFileName());
            title.setSubtitle(shaderPackFile.getFile().getFileName().toString());
            title.addTag(switch (shaderPackFile.getLoaderType()) {
                case OPTIFINE_IRIS -> i18n("shaderpack.loader.optifine_iris");
                case APERTURE -> i18n("shaderpack.loader.aperture");
            });

            titleContainer.getChildren().setAll(imageContainer, title);
            setHeading(titleContainer);

            Label description = new Label();
            if (shaderPackFile.getMeta() != null && shaderPackFile.getMeta().description() != null)
                description.setText(shaderPackFile.getMeta().description());
            description.setWrapText(true);
            FXUtils.copyOnDoubleClick(description);

            ScrollPane descriptionPane = new ScrollPane(description);
            FXUtils.smoothScrolling(descriptionPane);
            descriptionPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            descriptionPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
            descriptionPane.setFitToWidth(true);
            FXUtils.onChange(description.heightProperty(), newVal -> {
                double maxHeight = Controllers.getDecorator().contentHeightProperty().get() * 0.5;
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
                    Optional<RemoteAddon.Version> versionOptional = repository.getRemoteVersionByLocalFile(shaderPackFile.getFile());
                    if (versionOptional.isPresent()) {
                        RemoteAddon remoteAddon = repository.getAddonById(DownloadProviders.getDownloadProvider(), versionOptional.get().projectId());
                        FXUtils.runInFX(() -> {
                            button.setExternalLink(remoteAddon.pageUrl());
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
