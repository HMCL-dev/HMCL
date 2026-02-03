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
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/// @author Glavo
public interface LineComponent extends NoPaddingComponent {
    Insets PADDING = new Insets(8, 8, 8, 16);
    double MIN_HEIGHT = 48.0;

    BorderPane getRoot();

    StringProperty titleProperty();

    String getTitle();

    void setTitle(String title);

    abstract class SubtitleProperty extends StringPropertyBase {
        private VBox left;
        private Label subtitleLabel;

        public abstract Label getTitleLabel();

        @Override
        public abstract LineComponent getBean();

        @Override
        public String getName() {
            return "subtitle";
        }

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

    String getSubtitle();

    void setSubtitle(String subtitle);
}
