package org.jackhuang.hmcl.ui.account;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXDialogLayout;
import javafx.beans.InvalidationListener;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.*;
import javafx.scene.layout.BorderPane;
import org.jackhuang.hmcl.auth.microsoft.MicrosoftAccount;
import org.jackhuang.hmcl.auth.microsoft.MicrosoftService;
import org.jackhuang.hmcl.setting.Accounts;
import org.jackhuang.hmcl.task.Schedulers;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.construct.DialogCloseEvent;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane;
import org.jackhuang.hmcl.ui.construct.MultiFileItem;
import org.jackhuang.hmcl.ui.construct.SpinnerPane;
import org.jackhuang.hmcl.util.i18n.I18n;
import org.jackhuang.hmcl.util.logging.Logger;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import static org.jackhuang.hmcl.ui.FXUtils.onEscPressed;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public class MicrosoftAccountChangeCapeDialog extends JFXDialogLayout {
    private final MultiFileItem<MicrosoftService.MinecraftProfileResponseCape> capeItem = new MultiFileItem<>();
    private final MicrosoftService.MinecraftProfileResponse profile;
    private final ImageView capePreview = new ImageView();
    private final SpinnerPane capePreviewSpinner = new SpinnerPane();
    private Image previewCapeImage;
    private MicrosoftService.MinecraftProfileResponseCape currentCape;

    public MicrosoftAccountChangeCapeDialog(MicrosoftAccount account, MicrosoftService.MinecraftProfileResponse profile) {
        this.profile = profile;
        setWidth(400);
        setHeading(new Label(i18n("account.cape.change")));
        BorderPane body = new BorderPane();

        capePreviewSpinner.setPrefHeight(150);
        capePreviewSpinner.setPrefWidth(100);

        initCapeItems();

        ScrollPane scrollPane = new ScrollPane(capeItem);

        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setMaxHeight(250);
        scrollPane.setMaxWidth(270);

        body.setCenter(scrollPane);
        FXUtils.smoothScrolling(scrollPane);

        body.setRight(capePreviewSpinner);

        capePreview.setViewport(new Rectangle2D(10, 0, 10 * 10, 17 * 10));
        capePreviewSpinner.setContent(capePreview);

        InvalidationListener updateCapePreviewListener = observable -> {
            if (capeItem.getSelectedData() == null) {
                capePreview.setImage(null);
                return;
            }
            updateCapePreview().whenComplete(Schedulers.javafx(), (exception -> {
                if (exception == null) {
                    capePreviewSpinner.hideSpinner();
                } else {
                    Logger.LOG.error("Failed to load cape preview", exception);
                }
            })).start();
        };
        updateCapePreviewListener.invalidated(null);

        capeItem.selectedDataProperty().addListener(updateCapePreviewListener);

        getChildren().add(body);

        JFXButton saveButton = new JFXButton(i18n("button.save"));
        saveButton.getStyleClass().add("dialog-accept");

        saveButton.setOnAction(e -> {
            String cape = capeItem.getSelectedData().id();

            Task<?> updateCapeTask;
            if ("empty".equals(cape) && currentCape != null) {
                updateCapeTask = Task.runAsync(account::hideCape);
            } else if (currentCape != null && cape.equals(currentCape.id())) {
                updateCapeTask = null;
            } else {
                updateCapeTask = Task.runAsync(() -> account.changeCape(cape));
            }

            if (updateCapeTask != null) {
                updateCapeTask.whenComplete(Schedulers.javafx(), (exception) -> {
                    if (exception != null) {
                        Logger.LOG.error("Failed to change cape", exception);
                        Controllers.dialog(Accounts.localizeErrorMessage(exception), i18n("message.failed"), MessageDialogPane.MessageType.ERROR);
                    }
                    fireEvent(new DialogCloseEvent());
                }).start();
            } else {
                fireEvent(new DialogCloseEvent());
            }
        });

        JFXButton cancelButton = new JFXButton(i18n("button.cancel"));
        cancelButton.getStyleClass().add("dialog-cancel");
        cancelButton.setOnAction(e -> fireEvent(new DialogCloseEvent()));
        onEscPressed(this, cancelButton::fire);

        setBody(body);
        setActions(saveButton, cancelButton);
    }

    public static Image scaleImageNearestNeighbor(Image img, double sx, double sy) {
        int ow = (int) img.getWidth(), oh = (int) img.getHeight();
        WritableImage scaled = new WritableImage((int) (ow * sx), (int) (oh * sy));
        for (int y = 0; y < scaled.getHeight(); y++)
            for (int x = 0; x < scaled.getWidth(); x++)
                scaled.getPixelWriter().setColor(x, y, img.getPixelReader().getColor(Math.min(Math.max((int) (x / sx), 0), ow - 1), Math.min(Math.max((int) (y / sy), 0), oh - 1)));
        return scaled;
    }

    private void initCapeItems() {
        ArrayList<MultiFileItem.Option<MicrosoftService.MinecraftProfileResponseCape>> options = new ArrayList<>();
        List<MicrosoftService.MinecraftProfileResponseCape> capes = profile.getCapes();

        options.add(new MultiFileItem.Option<>(i18n("account.cape.none"), null));

        for (MicrosoftService.MinecraftProfileResponseCape cape : capes) {
            String key = "account.cape.name." + getCapeId(cape.alias());
            String displayName;

            if (I18n.hasKey(key)) {
                displayName = i18n(key);
            } else {
                LOG.warning("Cannot find key " + key + " in resource bundle");
                displayName = cape.alias();
            }

            MultiFileItem.Option<MicrosoftService.MinecraftProfileResponseCape> option = new MultiFileItem.Option<>(displayName, cape);
            options.add(option);
            if (Objects.equals(cape.state(), "ACTIVE")) {
                currentCape = cape;
            }
        }

        capeItem.loadChildren(options);
        capeItem.setSelectedData(currentCape);
    }

    private Task<?> updateCapePreview() {
        CompletableFuture<Image> imageFuture = new CompletableFuture<>();

        String imagePath = "/assets/img/cape/" + getCapeId(capeItem.getSelectedData().alias()) + ".png";
        URL imageURL = MicrosoftAccountChangeCapeDialog.class.getResource(imagePath);

        if (imageURL != null) {
            Image builtinImage = FXUtils.newBuiltinImage(imagePath);
            imageFuture.complete(builtinImage);
        } else {
            capePreviewSpinner.showSpinner();
            Task<Image> remoteImageTask = FXUtils.getRemoteImageTask(capeItem.getSelectedData().url(), 0, 0, false, false);
            remoteImageTask.whenComplete(Schedulers.javafx(), (loadedImage, exception) -> {
                if (exception != null) {
                    LOG.warning("Cannot download cape image " + capeItem.getSelectedData().url(), exception);
                    imageFuture.completeExceptionally(exception);
                } else {
                    imageFuture.complete(loadedImage);
                }
            }).start();
        }

        return Task.fromCompletableFuture(imageFuture).thenRunAsync(Schedulers.javafx(), () -> {
            previewCapeImage = scaleImageNearestNeighbor(imageFuture.getNow(null), 10, 10);
            capePreview.setImage(previewCapeImage);
        }).whenComplete(Schedulers.javafx(), (result, exception) -> {
            if (exception != null) {
                Logger.LOG.error("Failed to load cape preview", exception);
                capePreviewSpinner.hideSpinner();
            }
        });
    }

    private String getCapeId(String alias) {
        return alias.toLowerCase(Locale.ROOT).replace(" ", "_").replace("'", "_").replace("-", "_");
    }
}
