/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026 huangyuhui <huanghongxun2008@126.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.jackhuang.hmcl.ui.construct;

import javafx.css.CssMetaData;
import javafx.css.Styleable;
import javafx.css.StyleableDoubleProperty;
import javafx.css.StyleableProperty;
import javafx.css.converter.SizeConverter;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.shape.Rectangle;
import org.jackhuang.hmcl.ui.FXUtils;

import java.util.ArrayList;
import java.util.List;

/// A custom ImageView with corner radius support.
public class JFXImageView extends ImageView {

    private final Rectangle clip = new Rectangle();

    public JFXImageView() {
        super();
        clip.widthProperty().bind(this.fitWidthProperty());
        clip.heightProperty().bind(this.fitHeightProperty());

        updateCornerRadius(getCornerRadius());

        this.setClip(clip);
    }

    public JFXImageView(Image image) {
        this();
        setImage(image);
    }

    public void setLimitSize(double width, double height) {
        FXUtils.limitSize(this, width, height);
    }

    private void updateCornerRadius(double radius) {
        clip.setArcWidth(radius);
        clip.setArcHeight(radius);
    }

    private static final double DEFAULT_CORNER_RADIUS = 6.0;

    private StyleableDoubleProperty cornerRadius;

    public StyleableDoubleProperty cornerRadiusProperty() {
        if (this.cornerRadius == null) {
            cornerRadius = new StyleableDoubleProperty() {
                @Override
                public Object getBean() {
                    return JFXImageView.this;
                }

                @Override
                public String getName() {
                    return "cornerRadius";
                }

                @Override
                public CssMetaData<? extends Styleable, Number> getCssMetaData() {
                    return StyleableProperties.CORNER_RADIUS;
                }

                @Override
                protected void invalidated() {
                    updateCornerRadius(get());
                }
            };
        }

        return cornerRadius;
    }

    public double getCornerRadius() {
        return cornerRadius == null ? DEFAULT_CORNER_RADIUS : cornerRadius.get();
    }

    public void setCornerRadius(double radius) {
        cornerRadiusProperty().set(radius);
    }

    @Override
    public List<CssMetaData<? extends Styleable, ?>> getCssMetaData() {
        return StyleableProperties.STYLEABLES;
    }

    private static final class StyleableProperties {
        private static final CssMetaData<JFXImageView, Number> CORNER_RADIUS =
                new CssMetaData<>("-jfx-corner-radius", SizeConverter.getInstance(), DEFAULT_CORNER_RADIUS) {
                    @Override
                    public boolean isSettable(JFXImageView control) {
                        return control.cornerRadius == null || !control.cornerRadius.isBound();
                    }

                    @Override
                    public StyleableProperty<Number> getStyleableProperty(JFXImageView control) {
                        return control.cornerRadiusProperty();
                    }
                };

        private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            var styleables = new ArrayList<>(ImageView.getClassCssMetaData());
            styleables.add(CORNER_RADIUS);
            STYLEABLES = List.copyOf(styleables);

        }

    }
}
