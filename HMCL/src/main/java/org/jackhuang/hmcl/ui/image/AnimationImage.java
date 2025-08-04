package org.jackhuang.hmcl.ui.image;

import javafx.scene.image.Image;

public interface AnimationImage {
    default Image self() {
         return (Image) this;
    }
}
