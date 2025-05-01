package org.jackhuang.hmcl.ui.account;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXDialogLayout;
import javafx.beans.InvalidationListener;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.Label;
import javafx.scene.image.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
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
import org.jackhuang.hmcl.util.ResourceNotFoundError;
import org.jackhuang.hmcl.util.i18n.I18n;
import org.jackhuang.hmcl.util.logging.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.jackhuang.hmcl.ui.FXUtils.onEscPressed;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class MicrosoftAccountChangeCapeDialog extends JFXDialogLayout {
    private final MultiFileItem<MicrosoftService.MinecraftProfileResponseCape> capeItem = new MultiFileItem<>();
    private final MicrosoftService.MinecraftProfileResponse profile;
    private final MicrosoftAccount account;
    private final ImageView capePreview = new ImageView();
    private final SpinnerPane capePreviewSpinner = new SpinnerPane();
    private Image previewCapeImage;
    private MicrosoftService.MinecraftProfileResponseCape currentCape;

    public MicrosoftAccountChangeCapeDialog(MicrosoftAccount account, MicrosoftService.MinecraftProfileResponse profile) {
        this.profile = profile;
        this.account = account;
        setWidth(400);
        setHeading(new Label(i18n("account.cape.change")));
        BorderPane body = new BorderPane();

        initCapeItem();
        body.setCenter(capeItem);

        BorderPane rightPane = new BorderPane();
        rightPane.setCenter(capePreviewSpinner);
        rightPane.setMinWidth(Region.USE_PREF_SIZE);
        body.setRight(rightPane);

        capePreview.setViewport(new Rectangle2D(1 * 10, 0, 10 * 10, 17 * 10));
        capePreviewSpinner.setContent(capePreview);

        InvalidationListener invalidationListener = observable -> {
            if (capeItem.getSelectedData() == null) {
                capePreview.setImage(null);
                return;
            }
            capePreviewSpinner.showSpinner();
            loadCapePreview().whenComplete(Schedulers.javafx(), (exception -> {
                if (exception == null) {
                    capePreviewSpinner.hideSpinner();
                } else {
                    Logger.LOG.error("Failed to load cape preview", exception);
                }
            })).start();
        };
        invalidationListener.invalidated(null);

        capeItem.selectedDataProperty().addListener(invalidationListener);

        getChildren().add(body);

        JFXButton acceptButton = new JFXButton(i18n("button.ok"));
        acceptButton.getStyleClass().add("dialog-accept");

        acceptButton.setOnAction(e -> {
            Task<?> updateCapeTask = updateCapeSetting();
            if (updateCapeTask != null) {
                updateCapeTask.whenComplete(Schedulers.javafx(), (exception -> {
                    if (exception != null) {
                        Logger.LOG.error("Failed to change cape", exception);
                        Controllers.dialog(Accounts.localizeErrorMessage(exception), i18n("message.failed"), MessageDialogPane.MessageType.ERROR);
                    }
                    fireEvent(new DialogCloseEvent());
                })).start();
            } else {
                fireEvent(new DialogCloseEvent());
            }
        });

        JFXButton cancelButton = new JFXButton(i18n("button.cancel"));
        cancelButton.getStyleClass().add("dialog-cancel");
        cancelButton.setOnAction(e -> fireEvent(new DialogCloseEvent()));
        onEscPressed(this, cancelButton::fire);

        setBody(body);
        setActions(acceptButton, cancelButton);
    }

    public static Image scaleImageNearestNeighbor(Image img, double sx, double sy) {
        PixelReader pr = img.getPixelReader();
        int ow = (int) img.getWidth(), oh = (int) img.getHeight();
        WritableImage scaled = new WritableImage((int) (ow * sx), (int) (oh * sy));
        PixelWriter pw = scaled.getPixelWriter();
        for (int y = 0; y < scaled.getHeight(); y++)
            for (int x = 0; x < scaled.getWidth(); x++)
                pw.setColor(x, y, pr.getColor(Math.min(Math.max((int) (x / sx), 0), ow - 1), Math.min(Math.max((int) (y / sy), 0), oh - 1)));
        return scaled;
    }

    private Task<?> updateCapeSetting() {
        String cape = capeItem.getSelectedData().getId();

        if ("empty".equals(cape)) {
            if (currentCape == null) return null;
            cape = null;
        } else if (currentCape != null && cape.equals(currentCape.getId())) {
            return null;
        }

        if (cape == null) {
            return Task.runAsync(account::hideCape);
        } else {
            String finalCape = cape;
            return Task.runAsync(() -> account.changeCape(finalCape));
        }
    }

    private void initCapeItem() {
        ArrayList<MultiFileItem.Option<MicrosoftService.MinecraftProfileResponseCape>> options = new ArrayList<>();
        List<MicrosoftService.MinecraftProfileResponseCape> capes = profile.getCapes();

        options.add(new MultiFileItem.Option<>(i18n("account.cape.none"), null));

        for (MicrosoftService.MinecraftProfileResponseCape cape : capes) {
            String key = "account.cape.name." + capeId(cape.getAlias());
            MultiFileItem.Option<MicrosoftService.MinecraftProfileResponseCape> option = new MultiFileItem.Option<>(I18n.hasKey(key) ? i18n(key) : cape.getAlias(), cape);
            options.add(option);
            if (Objects.equals(cape.getState(), "ACTIVE")) {
                currentCape = cape;
            }
        }

        capeItem.loadChildren(options);
        if (currentCape != null) {
            capeItem.setSelectedData(currentCape);
        } else {
            capeItem.setSelectedData(null);
        }
    }

    private Task<?> loadCapePreview() {
        return Task.runAsync(() -> {
            try {
                previewCapeImage = FXUtils.newBuiltinImage("/assets/img/cape/" + capeId(capeItem.getSelectedData().getAlias()));
            } catch (ResourceNotFoundError error) {
                previewCapeImage = FXUtils.newRemoteImage(capeItem.getSelectedData().getUrl());
            }
            previewCapeImage = scaleImageNearestNeighbor(previewCapeImage, 10, 10);
        }).whenComplete(Schedulers.javafx(), (exception) -> {
            if (exception == null) {
                capePreview.setImage(previewCapeImage);
            } else {
                Logger.LOG.error("Failed to load cape preview", exception);
            }
        });
    }

    private String capeId(String alias) {
        return alias.toLowerCase().replace(" ", "_").replace("'", "_").replace("-", "_");
    }
}
