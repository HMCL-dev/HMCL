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

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.property.StringPropertyBase;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/// @author Glavo
public class LinePane extends BorderPane {

    private static final Insets PADDING = new Insets(8, 8, 8, 16);

    private final Label titleLabel;

    public LinePane() {
        this.setPadding(PADDING);
        this.setMinHeight(48);

        this.titleLabel = new Label();
        this.setCenter(titleLabel);
        BorderPane.setAlignment(titleLabel, Pos.CENTER_LEFT);
        titleLabel.textProperty().bind(titleProperty());
        titleLabel.getStyleClass().add("title");
    }

    private final StringProperty title = new SimpleStringProperty(this, "title");

    public StringProperty titleProperty() {
        return title;
    }

    public String getTitle() {
        return titleProperty().get();
    }

    public void setTitle(String title) {
        this.titleProperty().set(title);
    }

    private StringProperty subtitle;

    public StringProperty subtitleProperty() {
        if (subtitle == null) {
            subtitle = new StringPropertyBase() {
                private VBox left;
                private Label subtitleLabel;

                @Override
                public String getName() {
                    return "subtitle";
                }

                @Override
                public Object getBean() {
                    return LinePane.this;
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
                        left.getChildren().setAll(titleLabel, subtitleLabel);
                        LinePane.this.setCenter(left);
                    } else if (left != null) {
                        subtitleLabel.setText(null);
                        LinePane.this.setCenter(titleLabel);
                    }
                }
            };
        }

        return subtitle;
    }

    public String getSubtitle() {
        return subtitleProperty().get();
    }

    public void setSubtitle(String subtitle) {
        subtitleProperty().set(subtitle);
    }
}
