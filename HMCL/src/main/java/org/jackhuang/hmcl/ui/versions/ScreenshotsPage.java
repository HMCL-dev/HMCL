package org.jackhuang.hmcl.ui.versions;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXDialogLayout;
import com.jfoenix.controls.JFXListView;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ListCell;
import javafx.scene.control.Skin;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import org.jackhuang.hmcl.setting.Profile;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.*;
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
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public class ScreenshotsPage extends ListPageBase<ScreenshotsPage.Screenshot> implements VersionPage.VersionLoadable, PageAware {

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
            getItems().clear();
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

    private void delete(Screenshot screenshot) {
        try {
            Files.deleteIfExists(screenshot.getPath());
            refresh();
        } catch (IOException e) {
            LOG.warning("Failed to delete screenshot: " + screenshot.getPath(), e);
        }
    }

    public static final class Screenshot implements Comparable<Screenshot> {
        private final Path path;
        private final String fileName;
        private final Instant creationTime;
        private Image thumbnail, fullImage;

        public static Screenshot fromFile(Path path) {
            if (!Files.isRegularFile(path) || !path.toString().endsWith(".png")) return null;
            Instant creationTime = null;
            try {
                creationTime = Files.readAttributes(path, BasicFileAttributes.class).creationTime().toInstant();
            } catch (IOException e) {
                LOG.warning("Failed to read screenshot creation time at: " + path, e);
            }
            return new Screenshot(path, FileUtils.getName(path), creationTime);
        }

        public Screenshot(Path path, String fileName, Instant creationTime) {
            this.path = path;
            this.fileName = fileName;
            this.creationTime = creationTime;
        }

        @Override
        public int compareTo(@NotNull ScreenshotsPage.Screenshot o) {
            return this.getFileName().compareTo(o.getFileName());
        }

        public Path getPath() {
            return path;
        }

        public String getFileName() {
            return fileName;
        }

        public Instant getCreationTime() {
            return creationTime;
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
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            var that = (Screenshot) obj;
            return Objects.equals(this.path, that.path) &&
                    Objects.equals(this.fileName, that.fileName) &&
                    Objects.equals(this.creationTime, that.creationTime) &&
                    Objects.equals(this.thumbnail, that.thumbnail);
        }

        @Override
        public int hashCode() {
            return Objects.hash(path, fileName, creationTime, thumbnail);
        }

        @Override
        public String toString() {
            return "Screenshot[" +
                    "path=" + path + ", " +
                    "fileName=" + fileName + ", " +
                    "creationTime=" + creationTime + ", " +
                    "thumbnail=" + thumbnail + ']';
        }

    }

    public static final class ScreenshotCell extends ListCell<Screenshot> {

        private final RipplerContainer graphics;
        private final StackPane imagePane = new StackPane();
        private final BorderPane container = new BorderPane();
        private final TwoLineListItem content = new TwoLineListItem();
        private final JFXButton deleteButton = FXUtils.newToggleButton4(SVG.DELETE_FOREVER);

        public ScreenshotCell(ScreenshotsPage page) {
            super();

            container.getStyleClass().add("md-list-cell");
            container.setPadding(new Insets(8));

            imagePane.setPadding(new Insets(0, 8, 0, 0));
            BorderPane.setAlignment(imagePane, Pos.CENTER);
            container.setLeft(imagePane);

            container.setCenter(content);

            BorderPane.setAlignment(deleteButton, Pos.CENTER_RIGHT);
            deleteButton.setOnAction(e -> {
                Screenshot screenshot = getItem();
                if (screenshot != null) {
                    Controllers.confirm(i18n("button.remove.confirm"), i18n("button.remove"),
                            () -> page.delete(screenshot), null);
                }
            });
            container.setRight(deleteButton);

            graphics = new RipplerContainer(container);
            FXUtils.onClicked(graphics, () -> {
                Screenshot screenshot = getItem();
                if (screenshot != null) Controllers.dialog(new ScreenshotDialog(screenshot));
            });
        }

        @Override
        protected void updateItem(Screenshot item, boolean empty) {
            super.updateItem(item, empty);

            if (item == null || empty) {
                setGraphic(null);
                return;
            }

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

            setGraphic(graphics);
        }
    }

    public static final class ScreenshotDialog extends JFXDialogLayout {

        public ScreenshotDialog(Screenshot screenshot) {
            TwoLineListItem head = new TwoLineListItem();
            head.setTitle(screenshot.getFileName());
            head.setSubtitle(I18n.formatDateTime(screenshot.getCreationTime()));
            setHeading(head);

            var image = screenshot.getFullImage();
            setBody(image != null
                    ? new ImageContainer(image, Math.min(Controllers.getScene().getWidth() * 0.8, image.getWidth()), Controllers.getScene().getHeight() * 0.5)
                    : SVG.SCREENSHOT_MONITOR.createIcon(360)
            );

            JFXButton okButton = new JFXButton();
            okButton.getStyleClass().add("dialog-accept");
            okButton.setText(i18n("button.ok"));
            okButton.setOnAction(e -> fireEvent(new DialogCloseEvent()));
            FXUtils.onEscPressed(this, okButton::fire);
            setActions(okButton);
        }
    }

    public static final class ScreenshotsPageSkin extends ToolbarListPageSkin<Screenshot, ScreenshotsPage> {

        private final ScreenshotsPage skinnable;

        public ScreenshotsPageSkin(ScreenshotsPage skinnable) {
            super(skinnable);
            this.skinnable = skinnable;
        }

        @Override
        protected List<Node> initializeToolbar(ScreenshotsPage skinnable) {
            return List.of(
                    createToolbarButton2(i18n("button.refresh"), SVG.REFRESH, skinnable::refresh)
            );
        }

        @Override
        protected ListCell<Screenshot> createListCell(JFXListView<Screenshot> listView) {
            return new ScreenshotCell(skinnable);
        }
    }

}
