package org.jackhuang.hmcl.ui.construct;

import javafx.geometry.Insets;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

public class MenuSeparator extends StackPane {

    public MenuSeparator() {
        Rectangle rect = new Rectangle();
        rect.widthProperty().bind(widthProperty().add(-14));
        rect.setHeight(1);
        rect.setFill(Color.GRAY);
        maxHeightProperty().set(10);
        setPadding(new Insets(3));
        getChildren().setAll(rect);
    }
}
