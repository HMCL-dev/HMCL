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
package org.jackhuang.hmcl.ui;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ObjectPropertyBase;
import javafx.css.CssMetaData;
import javafx.css.StyleConverter;
import javafx.css.Styleable;
import javafx.css.StyleableDoubleProperty;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Control;
import javafx.scene.shape.SVGPath;

import java.util.ArrayList;
import java.util.List;

/// @author Glavo
public final class SVGIcon extends Control {

    public SVGIcon() {
    }

    public SVGIcon(SVG icon) {
        setIcon(icon);
    }

    public SVGIcon(double size) {
        setIconSize(size);
    }

    public SVGIcon(SVG icon, double size) {
        setIcon(icon);
        setIconSize(size);
    }

    @Override
    protected javafx.scene.control.Skin<?> createDefaultSkin() {
        return new Skin();
    }

    private ObjectProperty<SVG> icon;

    public ObjectProperty<SVG> iconProperty() {
        if (icon == null) {
            icon = new ObjectPropertyBase<>(SVG.NONE) {
                @Override
                public Object getBean() {
                    return SVGIcon.this;
                }

                @Override
                public String getName() {
                    return "icon";
                }
            };
        }
        return icon;
    }

    public SVG getIcon() {
        return icon != null ? icon.get() : SVG.NONE;
    }

    public void setIcon(SVG svg) {
        iconProperty().set(svg);
    }

    private StyleableDoubleProperty iconSize;

    public StyleableDoubleProperty iconSizeProperty() {
        if (iconSize == null) {
            iconSize = new StyleableDoubleProperty(SVG.DEFAULT_SIZE) {
                @Override
                public Object getBean() {
                    return SVGIcon.this;
                }

                @Override
                public String getName() {
                    return "iconSize";
                }

                @Override
                public javafx.css.CssMetaData<SVGIcon, Number> getCssMetaData() {
                    return StyleableProperties.ICON_SIZE;
                }
            };
        }
        return iconSize;
    }

    public double getIconSize() {
        return iconSize != null ? iconSize.get() : SVG.DEFAULT_SIZE;
    }

    public void setIconSize(double size) {
        iconSizeProperty().set(size);
    }

    private final class Skin extends Parent implements javafx.scene.control.Skin<SVGIcon> {

        private final SVGPath svgPath = new SVGPath();

        Skin() {
            FXUtils.onChangeAndOperate(iconProperty(), svg -> svgPath.setContent(svg.getPath()));
            FXUtils.onChangeAndOperate(iconSizeProperty(), size -> {
                double scale = size.doubleValue() / SVG.DEFAULT_SIZE;
                svgPath.setScaleX(scale);
                svgPath.setScaleY(scale);
            });

            this.getChildren().add(svgPath);
        }

        // Skin

        @Override
        public SVGIcon getSkinnable() {
            return SVGIcon.this;
        }

        @Override
        public Node getNode() {
            return this;
        }

        @Override
        public void dispose() {
        }

        // Parent

        @Override
        public double prefWidth(double height) {
            return getIconSize();
        }

        @Override
        public double prefHeight(double width) {
            return getIconSize();
        }

        @Override
        public double minHeight(double width) {
            return getIconSize();
        }

        @Override
        public double minWidth(double height) {
            return getIconSize();
        }

        @Override
        protected void layoutChildren() {
        }

    }

    private static final class StyleableProperties {
        private static final javafx.css.CssMetaData<SVGIcon, Number> ICON_SIZE =
                new javafx.css.CssMetaData<>("-icon-size", StyleConverter.getSizeConverter(), SVG.DEFAULT_SIZE) {
                    @Override
                    public boolean isSettable(SVGIcon control) {
                        return control.iconSize == null || !control.iconSize.isBound();
                    }

                    @Override
                    public StyleableDoubleProperty getStyleableProperty(SVGIcon control) {
                        return control.iconSizeProperty();
                    }
                };

        private static final List<CssMetaData<? extends Styleable, ?>> STYLEABLES;

        static {
            var styleables = new ArrayList<>(Control.getClassCssMetaData());
            styleables.add(ICON_SIZE);
            STYLEABLES = List.copyOf(styleables);
        }
    }
}
