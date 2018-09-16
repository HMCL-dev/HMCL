package org.jackhuang.hmcl.ui.versions;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXCheckBox;
import com.jfoenix.effects.JFXDepthManager;
import javafx.geometry.Pos;
import javafx.scene.layout.BorderPane;
import org.jackhuang.hmcl.mod.Datapack;
import org.jackhuang.hmcl.setting.Theme;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.ui.construct.TwoLineListItem;

import java.util.function.Consumer;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class DatapackListItem extends BorderPane {

    public DatapackListItem(Datapack.Pack info, Consumer<DatapackListItem> deleteCallback) {
        JFXCheckBox chkEnabled = new JFXCheckBox();
        BorderPane.setAlignment(chkEnabled, Pos.CENTER);
        setLeft(chkEnabled);

        TwoLineListItem modItem = new TwoLineListItem();
        BorderPane.setAlignment(modItem, Pos.CENTER);
        setCenter(modItem);

        JFXButton btnRemove = new JFXButton();
        FXUtils.installTooltip(btnRemove, i18n("datapack.remove"));
        btnRemove.setOnMouseClicked(e -> deleteCallback.accept(this));
        btnRemove.getStyleClass().add("toggle-icon4");
        BorderPane.setAlignment(btnRemove, Pos.CENTER);
        btnRemove.setGraphic(SVG.close(Theme.blackFillBinding(), 15, 15));
        setRight(btnRemove);

        setStyle("-fx-background-radius: 2; -fx-background-color: white; -fx-padding: 8;");
        JFXDepthManager.setDepth(this, 1);
        modItem.setTitle(info.getId());
        modItem.setSubtitle(info.getDescription());
        chkEnabled.selectedProperty().bindBidirectional(info.activeProperty());
    }
}
