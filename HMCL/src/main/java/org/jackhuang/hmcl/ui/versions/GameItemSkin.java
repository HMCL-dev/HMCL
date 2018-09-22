package org.jackhuang.hmcl.ui.versions;

import javafx.geometry.Pos;
import javafx.scene.control.SkinBase;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.construct.TwoLineListItem;

public class GameItemSkin extends SkinBase<GameItem> {
    public GameItemSkin(GameItem skinnable) {
        super(skinnable);

        HBox center = new HBox();
        center.setSpacing(8);
        center.setAlignment(Pos.CENTER_LEFT);

        StackPane imageViewContainer = new StackPane();
        FXUtils.setLimitWidth(imageViewContainer, 32);
        FXUtils.setLimitHeight(imageViewContainer, 32);

        ImageView imageView = new ImageView();
        FXUtils.limitSize(imageView, 32, 32);
        imageView.imageProperty().bind(skinnable.imageProperty());
        imageViewContainer.getChildren().setAll(imageView);

        TwoLineListItem item = new TwoLineListItem();
        item.titleProperty().bind(skinnable.titleProperty());
        item.subtitleProperty().bind(skinnable.subtitleProperty());
        BorderPane.setAlignment(item, Pos.CENTER);
        center.getChildren().setAll(imageView, item);
        getChildren().setAll(center);
    }
}
