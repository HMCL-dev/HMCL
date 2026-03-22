package org.jackhuang.hmcl.ui.versions;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXDialogLayout;
import com.jfoenix.controls.JFXListView;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.*;
import org.jackhuang.hmcl.ui.animation.ContainerAnimations;
import org.jackhuang.hmcl.ui.animation.TransitionPane;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.util.i18n.I18n;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static org.jackhuang.hmcl.ui.FXUtils.ignoreEvent;
import static org.jackhuang.hmcl.ui.ToolbarListPageSkin.createToolbarButton2;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public final class ScreenshotsPage extends ListPageBase<ScreenshotsPage.Screenshot> implements VersionPage.VersionLoadable, PageAware {

    private Path screenshotsDir;

    @Override
    protected Skin<?> createDefaultSkin() {
        return new ScreenshotsPageSkin(this);
    }

    @Override
    public void loadVersion(Profile profile, String version) {
        screenshotsDir = profile.getRepository().getRunDirectory(version).resolve("screenshots");
        refresh();
    }

    public void refresh() {
        setLoading(true);
        Task.supplyAsync(Schedulers.io(), () -> {
            try (Stream<Path> stream = Files.list(screenshotsDir)) {
                return stream.map(Screenshot::fromFile).filter(Objects::nonNull).sorted(Comparator.reverseOrder()).toList();
            }
        }).whenComplete(Schedulers.javafx(), (list, exception) -> {
            if (exception != null) {
                LOG.warning("Failed to load screenshots in: " + screenshotsDir, exception);
                getItems().clear();
            } else {
                getItems().setAll(list);
            }
            setLoading(false);
        }).start();
    }

    private void revealFolder() {
        FXUtils.openFolder(screenshotsDir);
    }

    private void deleteAt(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            LOG.warning("Failed to delete screenshot: " + path, e);
        }
    }

    private void delete(Screenshot screenshot) {
        deleteAt(screenshot.getPath());
        refresh();
    }

    private void delete(Collection<Screenshot> screenshots) {
        screenshots.stream().map(Screenshot::getPath).forEach(this::deleteAt);
        refresh();
    }

    private void clear() {
        try (var stream = Files.list(screenshotsDir)) {
            stream.filter(Screenshot::isFileScreenshot).forEach(this::deleteAt);
        } catch (IOException e) {
            LOG.warning("Failed to clear screenshots at: " + screenshotsDir, e);
        }
    }

    private void selectDate(JFXListView<Screenshot> listView) {
        FXUtils.chooseDateRange(pair -> {
            Instant from = pair.key().atStartOfDay().atZone(ZoneId.systemDefault()).toInstant();
            Instant to = pair.value().plusDays(1).atStartOfDay().atZone(ZoneId.systemDefault()).toInstant();
            if (from.compareTo(to) >= 0) return;
            List<Screenshot> items = listView.getItems();
            int start = -1, end = -1;
            for (int i = 0; i < items.size(); i++) {
                // Reversed order
                if (items.get(i).getCreationTime().compareTo(from) < 0 && end < 0) end = i;
                if (items.get(i).getCreationTime().compareTo(to) < 0 && start < 0) start = i;
            }
            if (start < 0) return;
            if (end < 0) end = items.size();
            listView.getSelectionModel().selectRange(start, end);
            listView.scrollTo(start);
        });
    }

    public static final class Screenshot implements Comparable<Screenshot> {
        private final Path path;
        private final String fileName;
        private final BasicFileAttributes attributes;
        private Image thumbnail, fullImage;

        public static boolean isFileScreenshot(Path path) {
            return Files.isRegularFile(path) && "png".equalsIgnoreCase(FileUtils.getExtension(path));
        }

        public static Screenshot fromFile(Path path) {
            if (!isFileScreenshot(path)) return null;
            BasicFileAttributes attr = null;
            try {
                attr = Files.readAttributes(path, BasicFileAttributes.class);
            } catch (IOException e) {
                LOG.warning("Failed to read screenshot creation time at: " + path, e);
            }
            return new Screenshot(path, FileUtils.getName(path), attr);
        }

        public Screenshot(Path path, String fileName, BasicFileAttributes attributes) {
            this.path = path;
            this.fileName = fileName;
            this.attributes = attributes;
        }

        public Path getPath() {
            return path;
        }

        public String getFileName() {
            return fileName;
        }

        public BasicFileAttributes getAttributes() {
            return attributes;
        }

        public Instant getCreationTime() {
            return attributes.creationTime().toInstant();
        }

        public long getFileSize() {
            return attributes.size();
        }

        public boolean isThumbnailLoaded() {
            return thumbnail != null;
        }

        public boolean isFullImageLoaded() {
            return fullImage != null;
        }

        @Nullable
        public Image getThumbnail() {
            if (thumbnail == null) {
                try {
                    thumbnail = FXUtils.loadImage(path, 72, 72, true, false);
                } catch (Exception e) {
                    LOG.warning("Failed to load screenshot thumbnail at: " + path, e);
                }
            }
            return thumbnail;
        }

        @Nullable
        public Image getFullImage() {
            if (fullImage == null) {
                try {
                    fullImage = FXUtils.loadImage(path);
                } catch (Exception e) {
                    LOG.warning("Failed to load screenshot content at: " + path, e);
                }
            }
            return fullImage;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof Screenshot that && Objects.equals(this.path, that.path);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(path);
        }

        @Override
        public int compareTo(@NotNull ScreenshotsPage.Screenshot o) {
            return this.path.compareTo(o.path);
        }

    }

    private static final class ScreenshotCell extends MDListCell<Screenshot> {

        private final StackPane imagePane = new StackPane();
        private final TwoLineListItem content = new TwoLineListItem();

        public ScreenshotCell(JFXListView<Screenshot> listView, ScreenshotsPage page) {
            super(listView);

            setSelectable();

            HBox container = new HBox(8);
            container.setPickOnBounds(false);
            container.setAlignment(Pos.CENTER_LEFT);

            content.setMouseTransparent(true);
            HBox.setHgrow(content, Priority.ALWAYS);

            JFXButton copyButton = FXUtils.newToggleButton4(SVG.CONTENT_COPY);
            copyButton.setOnAction(e -> {
                Screenshot screenshot = getItem();
                if (screenshot != null) {
                    FXUtils.copyFiles(List.of(screenshot.getPath()));
                }
            });

            JFXButton revealButton = FXUtils.newToggleButton4(SVG.FOLDER);
            revealButton.setOnAction(e -> {
                Screenshot screenshot = getItem();
                if (screenshot != null) {
                    FXUtils.showFileInExplorer(screenshot.getPath());
                }
            });

            JFXButton infoButton = FXUtils.newToggleButton4(SVG.INFO);
            infoButton.setOnAction(e -> {
                Screenshot screenshot = getItem();
                if (screenshot != null) {
                    Controllers.dialog(new ScreenshotDialog(screenshot));
                }
            });
            FXUtils.onDoubleClicked(this, infoButton::fire);

            JFXButton deleteButton = FXUtils.newToggleButton4(SVG.DELETE_FOREVER);
            deleteButton.setOnAction(e -> {
                Screenshot screenshot = getItem();
                if (screenshot != null) {
                    Controllers.confirm(i18n("button.remove.confirm"), i18n("button.remove"),
                            () -> page.delete(screenshot), null);
                }
            });

            container.getChildren().setAll(imagePane, content, copyButton, revealButton, infoButton, deleteButton);

            StackPane.setMargin(container, new Insets(8));
            getContainer().getChildren().setAll(container);
        }

        @Override
        protected void updateControl(Screenshot item, boolean empty) {
            if (item == null || empty) return;

            if (item.isThumbnailLoaded()) {
                imagePane.getChildren().setAll(new ImageContainer(item.getThumbnail(), 36, 36));
            } else {
                imagePane.getChildren().setAll(SVG.SCREENSHOT_MONITOR.createIcon(36));
                CompletableFuture
                        .supplyAsync(item::getThumbnail, Schedulers.io())
                        .whenCompleteAsync((image, t) -> {
                            if (image != null) imagePane.getChildren().setAll(new ImageContainer(image, 36, 36));
                        }, Schedulers.javafx());
            }

            content.setTitle(item.getFileName());
            content.setSubtitle(I18n.formatDateTime(item.getCreationTime()));
        }
    }

    private static final class ScreenshotDialog extends JFXDialogLayout {

        public ScreenshotDialog(Screenshot screenshot) {
            TwoLineListItem head = new TwoLineListItem();
            head.setTitle(screenshot.getFileName());
            setHeading(head);

            var image = screenshot.getFullImage();
            if (image == null) {
                setBody(SVG.SCREENSHOT_MONITOR.createIcon(360));
                head.setSubtitle(I18n.formatDateTime(screenshot.getCreationTime()) + "    " + FileUtils.parseFileSize(screenshot.getFileSize()));
            } else {
                setBody(new ImageContainer(image, Math.min(Controllers.getScene().getWidth() * 0.8, image.getWidth()), Controllers.getScene().getHeight() * 0.57));
                head.setSubtitle(I18n.formatDateTime(screenshot.getCreationTime())
                        + "    " + FileUtils.parseFileSize(screenshot.getFileSize())
                        + "    " + (int) image.getWidth() + " * " + (int) image.getHeight()
                );
            }

            JFXButton okButton = new JFXButton();
            okButton.getStyleClass().add("dialog-accept");
            okButton.setText(i18n("button.ok"));
            okButton.setOnAction(e -> fireEvent(new DialogCloseEvent()));
            FXUtils.onEscPressed(this, okButton::fire);
            setActions(okButton);
        }
    }

    private static final class ScreenshotsPageSkin extends SkinBase<ScreenshotsPage> {

        private final TransitionPane toolbarPane;
        private final HBox toolbarNormal;
        private final HBox toolbarSelecting;

        private final JFXListView<Screenshot> listView;

        public ScreenshotsPageSkin(ScreenshotsPage skinnable) {
            super(skinnable);

            StackPane pane = new StackPane();
            pane.setPadding(new Insets(10));
            pane.getStyleClass().addAll("notice-pane");

            ComponentList root = new ComponentList();
            root.getStyleClass().add("no-padding");
            listView = new JFXListView<>();
            listView.getStyleClass().add("no-horizontal-scrollbar");

            {
                toolbarPane = new TransitionPane();

                toolbarNormal = new HBox();
                toolbarSelecting = new HBox();

                // Toolbar Normal
                toolbarNormal.getChildren().setAll(
                        createToolbarButton2(i18n("button.refresh"), SVG.REFRESH, skinnable::refresh),
                        createToolbarButton2(i18n("button.reveal_dir"), SVG.FOLDER_OPEN, skinnable::revealFolder),
                        createToolbarButton2(i18n("button.clear"), SVG.DELETE_FOREVER, () -> {
                            Controllers.confirm(i18n("screenshots.clear.confirm"), i18n("button.clear"), skinnable::clear, null);
                        }),
                        createToolbarButton2(i18n("button.select_date"), SVG.DATE_RANGE, () -> skinnable.selectDate(listView))
                );

                // Toolbar Selecting
                toolbarSelecting.getChildren().setAll(
                        createToolbarButton2(i18n("menu.copy"), SVG.FOLDER_COPY, () -> {
                            FXUtils.copyFiles(listView.getSelectionModel().getSelectedItems().stream().map(Screenshot::getPath).toList());
                        }),
                        createToolbarButton2(i18n("button.remove"), SVG.DELETE_FOREVER, () -> {
                            Controllers.confirm(i18n("button.remove.confirm"), i18n("button.remove"), () ->
                                    skinnable.delete(listView.getSelectionModel().getSelectedItems()), null);
                        }),
                        createToolbarButton2(i18n("button.select_all"), SVG.SELECT_ALL, () ->
                                listView.getSelectionModel().selectAll()),
                        createToolbarButton2(i18n("button.cancel"), SVG.CANCEL, () ->
                                listView.getSelectionModel().clearSelection())
                );

                FXUtils.onChangeAndOperate(listView.getSelectionModel().selectedItemProperty(),
                        selectedItem -> {
                            if (selectedItem == null)
                                changeToolbar(toolbarNormal);
                            else
                                changeToolbar(toolbarSelecting);
                        });
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
                center.loadingProperty().bind(skinnable.loadingProperty());

                listView.setCellFactory(x -> new ScreenshotCell(listView, skinnable));
                listView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
                Bindings.bindContent(listView.getItems(), skinnable.getItems());

                listView.setOnContextMenuRequested(event -> {
                    Screenshot selectedItem = listView.getSelectionModel().getSelectedItem();
                    if (selectedItem != null && listView.getSelectionModel().getSelectedItems().size() == 1) {
                        listView.getSelectionModel().clearSelection();
                        Controllers.dialog(new ScreenshotDialog(selectedItem));
                    }
                });

                // ListViewBehavior would consume ESC pressed event, preventing us from handling it
                // So we ignore it here
                ignoreEvent(listView, KeyEvent.KEY_PRESSED, e -> e.getCode() == KeyCode.ESCAPE);

                center.setContent(listView);
                root.getContent().add(center);
            }

            pane.getChildren().setAll(root);
            getChildren().setAll(pane);
        }

        private void changeToolbar(HBox newToolbar) {
            if (newToolbar != toolbarPane.getCurrentNode()) toolbarPane.setContent(newToolbar, ContainerAnimations.FADE);
        }
    }

}
