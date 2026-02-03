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

import javafx.beans.property.StringProperty;
import javafx.beans.property.StringPropertyBase;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.ui.SVG;

/// @author Glavo
public interface LineComponent extends NoPaddingComponent {
    String DEFAULT_STYLE_CLASS = "line-component";

    PseudoClass PSEUDO_LARGER_TITLE = PseudoClass.getPseudoClass("large-title");

    Insets PADDING = new Insets(8, 8, 8, 16);
    Insets ICON_MARGIN = new Insets(0, 16, 0, 0);
    double MIN_HEIGHT = 48.0;

    private Node self() {
        return (Node) this;
    }

    BorderPane getRoot();

    StringProperty titleProperty();

    default String getTitle() {
        return titleProperty().get();
    }

    default void setTitle(String title) {
        titleProperty().set(title);
    }

    abstract class SubtitleProperty extends StringPropertyBase {
        private VBox left;
        private Label subtitleLabel;

        @Override
        public String getName() {
            return "subtitle";
        }

        @Override
        public abstract LineComponent getBean();

        public abstract Label getTitleLabel();

        @Override
        protected void invalidated() {
            String subtitle = get();
            if (subtitle != null && !subtitle.isEmpty()) {
                if (left == null) {
                    left = new VBox();
                    left.setMouseTransparent(true);
                    left.setAlignment(Pos.CENTER_LEFT);

                    subtitleLabel = new Label();
                    subtitleLabel.setWrapText(true);
                    subtitleLabel.setMinHeight(Region.USE_PREF_SIZE);
                    subtitleLabel.getStyleClass().add("subtitle");
                }
                subtitleLabel.setText(subtitle);
                left.getChildren().setAll(getTitleLabel(), subtitleLabel);
                getBean().getRoot().setCenter(left);
            } else if (left != null) {
                subtitleLabel.setText(null);
                getBean().getRoot().setCenter(getTitleLabel());
            }
        }
    }

    StringProperty subtitleProperty();

    default String getSubtitle() {
        return subtitleProperty().get();
    }

    default void setSubtitle(String subtitle) {
        subtitleProperty().set(subtitle);
    }

    default void setLeftIcon(Image icon) {
        setLeftIcon(icon, -1.0);
    }

    default void setLeftIcon(Image icon, double size) {
        ImageView imageView = new ImageView(icon);
        imageView.getStyleClass().add("left-icon");
        if (size > 0) {
            imageView.setFitWidth(size);
            imageView.setFitHeight(size);
            imageView.setPreserveRatio(true);
            imageView.setSmooth(true);
        }
        imageView.setMouseTransparent(true);
        BorderPane.setAlignment(imageView, Pos.CENTER);
        BorderPane.setMargin(imageView, ICON_MARGIN);
        getRoot().setLeft(imageView);
    }

    default void setLeftIcon(SVG svg) {
        setLeftIcon(svg, SVG.DEFAULT_SIZE);
    }

    default void setLeftIcon(SVG svg, double size) {
        Node node = svg.createIcon(size);
        node.getStyleClass().add("left-icon");
        node.setMouseTransparent(true);
        BorderPane.setAlignment(node, Pos.CENTER);
        BorderPane.setMargin(node, ICON_MARGIN);
        getRoot().setLeft(node);
    }

    default void setLargeTitle(boolean largeTitle) {
        self().pseudoClassStateChanged(PSEUDO_LARGER_TITLE, largeTitle);
    }
}
