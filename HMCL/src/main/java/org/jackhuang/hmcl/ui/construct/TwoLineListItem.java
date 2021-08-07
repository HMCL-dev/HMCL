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

import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.util.javafx.MappedObservableList;

public class TwoLineListItem extends VBox {
    private static final String DEFAULT_STYLE_CLASS = "two-line-list-item";

    private final StringProperty title = new SimpleStringProperty(this, "title");
    private final ObservableList<String> tags = FXCollections.observableArrayList();
    private final StringProperty subtitle = new SimpleStringProperty(this, "subtitle");

    private final ObservableList<Label> tagLabels;

    public TwoLineListItem(String titleString, String subtitleString) {
        this();

        title.set(titleString);
        subtitle.set(subtitleString);
    }

    public TwoLineListItem() {
        setMouseTransparent(true);

        HBox firstLine = new HBox();

        Label lblTitle = new Label();
        lblTitle.getStyleClass().add("title");
        lblTitle.textProperty().bind(title);

        HBox tagContainer = new HBox();

        tagLabels = MappedObservableList.create(tags, tag -> {
            Label tagLabel = new Label();
            tagLabel.getStyleClass().add("tag");
            tagLabel.setText(tag);
            return tagLabel;
        });
        Bindings.bindContent(tagContainer.getChildren(), tagLabels);

        firstLine.getChildren().addAll(lblTitle, tagContainer);

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

    public ObservableList<String> getTags() {
        return tags;
    }

    @Override
    public String toString() {
        return getTitle();
    }
}
