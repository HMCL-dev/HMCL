package org.jackhuang.hmcl.ui.construct;

import javafx.scene.image.ImageView;
import javafx.scene.shape.Rectangle;

public class JFXImageView extends ImageView {

    private final Rectangle clip = new Rectangle();
    private double cornerRadius = 6;

    public JFXImageView() {
        super();
        init();
    }

    public JFXImageView(String url) {
        super(url);
        init();
    }

    private void init() {
        clip.widthProperty().bind(this.fitWidthProperty());
        clip.heightProperty().bind(this.fitHeightProperty());

        updateCornerRadius(cornerRadius);

        this.setClip(clip);
    }

    public void setCornerRadius(double radius) {
        this.cornerRadius = radius;
        updateCornerRadius(radius);
    }

    private void updateCornerRadius(double radius) {
        clip.setArcWidth(radius);
        clip.setArcHeight(radius);
    }
}