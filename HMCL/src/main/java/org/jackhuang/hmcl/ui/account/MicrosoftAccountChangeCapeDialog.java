package org.jackhuang.hmcl.ui.account;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXDialogLayout;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import org.jackhuang.hmcl.auth.AuthenticationException;
import org.jackhuang.hmcl.auth.microsoft.MicrosoftAccount;
import org.jackhuang.hmcl.auth.microsoft.MicrosoftService;
import org.jackhuang.hmcl.setting.Accounts;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.construct.DialogCloseEvent;
import org.jackhuang.hmcl.ui.construct.MessageDialogPane;
import org.jackhuang.hmcl.ui.construct.MultiFileItem;
import org.jackhuang.hmcl.util.logging.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.jackhuang.hmcl.ui.FXUtils.onEscPressed;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class MicrosoftAccountChangeCapeDialog extends JFXDialogLayout {
    private final MultiFileItem<String> capeItem = new MultiFileItem<>();
    private final MicrosoftService.MinecraftProfileResponse profile;
    private final MicrosoftAccount account;
    private MicrosoftService.MinecraftProfileResponseCape currentCape;

    public MicrosoftAccountChangeCapeDialog(MicrosoftAccount account, MicrosoftService.MinecraftProfileResponse profile) {
        this.profile = profile;
        this.account = account;

        setHeading(new Label(i18n("account.cape.change")));
        StackPane body = new StackPane();

        initCapeItem();
        body.getChildren().add(capeItem);

        getChildren().add(body);

        JFXButton acceptButton = new JFXButton(i18n("button.ok"));
        acceptButton.getStyleClass().add("dialog-accept");
        acceptButton.setOnAction(e -> {
            if (!capeItem.getSelectedData().equals(currentCape.getId()))
                updateCapeSetting();
            fireEvent(new DialogCloseEvent());
        });

        JFXButton cancelButton = new JFXButton(i18n("button.cancel"));
        cancelButton.getStyleClass().add("dialog-cancel");
        cancelButton.setOnAction(e -> fireEvent(new DialogCloseEvent()));
        onEscPressed(this, cancelButton::fire);

        setBody(body);
        setActions(acceptButton, cancelButton);
    }

    private void updateCapeSetting() {
        try {
            account.changeCape(capeItem.getSelectedData());
        } catch (AuthenticationException e) {
            Logger.LOG.error("Failed to change cape", e);
            Controllers.dialog(Accounts.localizeErrorMessage(e), i18n("message.failed"), MessageDialogPane.MessageType.ERROR);
        }
    }

    private void initCapeItem() {
        ArrayList<MultiFileItem.Option<String>> options = new ArrayList<>();
        List<MicrosoftService.MinecraftProfileResponseCape> capes = profile.getCapes();

        for (MicrosoftService.MinecraftProfileResponseCape cape : capes) {
            MultiFileItem.Option<String> option = new MultiFileItem.Option<>(cape.getAlias(), cape.getId());
            options.add(option);
            if (Objects.equals(cape.getState(), "ACTIVE")) {
                currentCape = cape;
            }
        }

        if (currentCape != null) {
            capeItem.setSelectedData(currentCape.getId());
        } else {
            options.add(new MultiFileItem.Option<>(i18n("account.cape.none"), "none"));
            capeItem.setSelectedData("none");
        }

        capeItem.loadChildren(options);
    }
}
