/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
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
import javafx.collections.ObservableList;
import javafx.geometry.HPos;
import javafx.geometry.VPos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.ui.FXUtils;

public class TwoLineListItem extends VBox {
    private static final String DEFAULT_STYLE_CLASS = "two-line-list-item";
    private static final double TITLE_TAGS_SPACING = 8.0;

    private final StringProperty title = new SimpleStringProperty(this, "title");
    private final StringProperty subtitle = new SimpleStringProperty(this, "subtitle");

    private final TagsBar tagsBar = new TagsBar();

    public TwoLineListItem(String titleString, String subtitleString) {
        this();

        title.set(titleString);
        subtitle.set(subtitleString);
    }

    public TwoLineListItem() {
        setMouseTransparent(true);

        Label lblTitle = new Label();
        lblTitle.getStyleClass().add("title");
        lblTitle.textProperty().bind(title);

        // Allow tagsBar to receive mouse events for tooltip
        tagsBar.setMouseTransparent(false);
        FXUtils.installFastTooltip(tagsBar.getCollapsedIndicator(), tagsBar.getCollapsedTooltip());

        // Custom first line layout that prioritizes lblTitle but ensures +N is visible
        Pane firstLine = new Pane() {
            @Override
            protected void layoutChildren() {
                double width = getWidth();
                double height = getHeight();

                double titlePrefWidth = lblTitle.prefWidth(-1);
                double tagsMinWidth = tagsBar.minWidth(-1);
                double tagsPrefWidth = tagsBar.prefWidth(-1);

                double titleWidth;
                double tagsWidth;

                if (tagsBar.getTags().isEmpty()) {
                    // No tags, give all space to title
                    titleWidth = Math.min(titlePrefWidth, width);
                    tagsWidth = 0;
                } else if (titlePrefWidth + TITLE_TAGS_SPACING + tagsPrefWidth <= width) {
                    // Everything fits
                    titleWidth = titlePrefWidth;
                    tagsWidth = tagsPrefWidth;
                } else if (titlePrefWidth + TITLE_TAGS_SPACING + tagsMinWidth <= width) {
                    // Title fits fully, tags get remaining space
                    titleWidth = titlePrefWidth;
                    tagsWidth = width - titleWidth - TITLE_TAGS_SPACING;
                } else {
                    // Need to shrink title to make room for +N indicator
                    tagsWidth = tagsMinWidth;
                    titleWidth = Math.max(0, width - tagsMinWidth - TITLE_TAGS_SPACING);
                }

                layoutInArea(lblTitle, 0, 0, titleWidth, height, 0, HPos.LEFT, VPos.CENTER);

                if (tagsWidth > 0) {
                    double tagsX = titleWidth + TITLE_TAGS_SPACING;
                    layoutInArea(tagsBar, tagsX, 0, tagsWidth, height, 0, HPos.LEFT, VPos.CENTER);
                }
            }

            @Override
            protected double computePrefWidth(double height) {
                double titleWidth = lblTitle.prefWidth(-1);
                double tagsWidth = tagsBar.prefWidth(-1);
                if (tagsBar.getTags().isEmpty()) {
                    return titleWidth;
                }
                return titleWidth + TITLE_TAGS_SPACING + tagsWidth;
            }

            @Override
            protected double computePrefHeight(double width) {
                return Math.max(lblTitle.prefHeight(-1), tagsBar.prefHeight(-1));
            }

            @Override
            protected double computeMinWidth(double height) {
                // Minimum is enough for +N indicator
                double tagsMin = tagsBar.minWidth(-1);
                if (tagsBar.getTags().isEmpty()) {
                    return 0;
                }
                return tagsMin;
            }

            @Override
            protected double computeMinHeight(double width) {
                return computePrefHeight(width);
            }
        };
        firstLine.getStyleClass().add("first-line");
        firstLine.getChildren().addAll(lblTitle, tagsBar);

        Label lblSubtitle = new Label();
        lblSubtitle.getStyleClass().add("subtitle");
        lblSubtitle.textProperty().bind(subtitle);

        HBox secondLine = new HBox();
        secondLine.getChildren().setAll(lblSubtitle);

        getChildren().setAll(firstLine, secondLine);

        FXUtils.onChangeAndOperate(subtitle, subtitleString -> {
            if (subtitleString == null) getChildren().setAll(firstLine);
            else getChildren().setAll(firstLine, secondLine);
        });

        getStyleClass().add(DEFAULT_STYLE_CLASS);
    }

    public String getTitle() {
        return title.get();
    }

    public StringProperty titleProperty() {
        return title;
    }

    public void setTitle(String title) {
        this.title.set(title);
    }

    public String getSubtitle() {
        return subtitle.get();
    }

    public StringProperty subtitleProperty() {
        return subtitle;
    }

    public void setSubtitle(String subtitle) {
        this.subtitle.set(subtitle);
    }

    public void addTag(String tag) {
        tagsBar.addTag(tag);
    }

    public void addTagWarning(String tag) {
        tagsBar.addTagWarning(tag);
    }

    public ObservableList<TagsBar.Tag> getTags() {
        return tagsBar.getTags();
    }

    public TagsBar getTagsBar() {
        return tagsBar;
    }

    @Override
    public String toString() {
        return getTitle();
    }
}
