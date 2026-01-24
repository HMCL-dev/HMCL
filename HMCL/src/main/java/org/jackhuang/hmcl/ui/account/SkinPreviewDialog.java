package org.jackhuang.hmcl.ui.account;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXDialogLayout;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import org.jackhuang.hmcl.auth.Account;
import org.jackhuang.hmcl.game.TexturesLoader;
import org.jackhuang.hmcl.ui.construct.DialogCloseEvent;
import org.jackhuang.hmcl.ui.skin.SkinCanvas;
import org.jackhuang.hmcl.ui.skin.animation.SkinAniRunning;
import org.jackhuang.hmcl.ui.skin.animation.SkinAniWavingArms;
import org.jackhuang.hmcl.util.Lang;

import java.util.Objects;

import static org.jackhuang.hmcl.ui.FXUtils.onEscPressed;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class SkinPreviewDialog extends JFXDialogLayout {
    private final SkinCanvas canvas;
    private final ObservableValue<TexturesLoader.LoadedTexture> skin;
    private final ObservableValue<TexturesLoader.LoadedTexture> cape;

    public SkinPreviewDialog(Account account) {
        setHeading(new Label(i18n("account.character.preview")));


        skin = TexturesLoader.skinBinding(account);
        cape = TexturesLoader.capeBinding(account);

        skin.addListener((observable, oldValue, newValue) -> refresh());

        cape.addListener((observable, oldValue, newValue) -> refresh());

        canvas = new SkinCanvas(TexturesLoader.getDefaultSkinImage(), 300, 300, true);

        setBody(canvas);

        onEscPressed(this, () -> fireEvent(new DialogCloseEvent()));
        setActions(Lang.apply(new JFXButton(i18n("button.cancel")), (jfxButton) -> {
            jfxButton.setOnAction((event) -> fireEvent(new DialogCloseEvent()));
            jfxButton.getStyleClass().add("dialog-accept");
        }));
    }

    private void refresh() {
        Image skinImg = skin.getValue().getImage();
        boolean isSlim = Objects.equals(skin.getValue().getMetadata().get("model"), "slim");

        Image capeImg = null;

        if (cape != null && cape.getValue() != null) {
            capeImg = cape.getValue().getImage();
        }

        canvas.getAnimationPlayer().addSkinAnimation(new SkinAniWavingArms(100, 2000, 7.5, canvas), new SkinAniRunning(100, 100, 30, canvas));
        canvas.enableRotation(.5);
        canvas.updateSkin(skinImg, isSlim, capeImg);
    }
}
