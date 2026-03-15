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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
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
            Files.deleteIfExists(screenshot.path());
            refresh();
        } catch (IOException e) {
            LOG.warning("Failed to delete screenshot: " + screenshot.path(), e);
        }
    }

    public record Screenshot(Path path, String fileName, Instant creationTime, Image thumbnail) implements Comparable<Screenshot> {

        public static Screenshot fromFile(Path path) {
            if (!Files.isRegularFile(path) || !path.toString().endsWith(".png")) return null;
            Image thumbnail = null;
            Instant creationTime = null;
            try {
                thumbnail = FXUtils.loadImage(path, 72, 72, true, false);
            } catch (Exception e) {
                LOG.warning("Failed to load screenshot thumbnail at: " + path, e);
            }
            try {
                creationTime = Files.readAttributes(path, BasicFileAttributes.class).creationTime().toInstant();
            } catch (IOException e) {
                LOG.warning("Failed to read screenshot creation time at: " + path, e);
            }
            return new Screenshot(path, FileUtils.getName(path), creationTime, thumbnail);
        }

        public Image loadFullImage() {
            Image image = null;
            try {
                image = FXUtils.loadImage(path);
            } catch (Exception e) {
                LOG.warning("Failed to load screenshot content at: " + path, e);
            }
            return image;
        }

        @Override
        public int compareTo(@NotNull ScreenshotsPage.Screenshot o) {
            return this.fileName().compareTo(o.fileName());
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

            var image = item.thumbnail();
            if (image != null) {
                double width = 36;
                double height = width / image.getWidth() * image.getHeight();
                if (height > 36) {
                    height = 36;
                    width = height / image.getHeight() * image.getWidth();
                }
                ImageContainer imageContainer = new ImageContainer(width, height);
                imageContainer.setImage(image);
                imagePane.getChildren().setAll(imageContainer);
            } else {
                imagePane.getChildren().setAll(SVG.SCREENSHOT_MONITOR.createIcon(36));
            }

            content.setTitle(item.fileName());
            content.setSubtitle(I18n.formatDateTime(item.creationTime()));

            setGraphic(graphics);
        }
    }

    public static final class ScreenshotDialog extends JFXDialogLayout {

        public ScreenshotDialog(Screenshot screenshot) {
            TwoLineListItem head = new TwoLineListItem();
            head.setTitle(screenshot.fileName());
            head.setSubtitle(I18n.formatDateTime(screenshot.creationTime()));
            setHeading(head);

            var image = screenshot.loadFullImage();
            if (image != null) {
                double maxHeight = Controllers.getScene().getHeight() * 0.5;
                double width = Math.min(Controllers.getScene().getWidth() * 0.8, image.getWidth());
                double height = width / image.getWidth() * image.getHeight();
                if (height > maxHeight) {
                    height = maxHeight;
                    width = height / image.getHeight() * image.getWidth();
                }
                ImageContainer imageContainer = new ImageContainer(width, height);
                imageContainer.setImage(image);
                setBody(imageContainer);
            } else {
                setBody(SVG.SCREENSHOT_MONITOR.createIcon(360));
            }

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
