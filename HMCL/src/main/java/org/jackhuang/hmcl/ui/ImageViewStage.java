package org.jackhuang.hmcl.ui;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

public class ImageViewStage extends Stage {
    private final ObjectProperty<Image> imageProperty = new SimpleObjectProperty<>();

    public ImageViewStage(Image image) {
        this();

        imageProperty.set(image);
    }

    public ImageViewStage() {
        HBox root = new HBox();
        Scene scene = new Scene(root, 400, 400);

        ImageView view = new ImageView();
        view.imageProperty().bind(imageProperty);
        view.fitWidthProperty().bind(scene.widthProperty());
        view.fitHeightProperty().bind(scene.heightProperty());

        root.getChildren().add(view);
        setScene(scene);
        FXUtils.setIcon(this);
    }

    public ObjectProperty<Image> imageProperty() {
        return imageProperty;
    }
}
