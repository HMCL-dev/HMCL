/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2019  huangyuhui <huanghongxun2008@126.com> and contributors
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

import com.jfoenix.controls.JFXButton;
import javafx.beans.DefaultProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.setting.Theme;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

@DefaultProperty("image")
public final class ImagePickerItem extends BorderPane {

    private final ImageView imageView;

    private final StringProperty title = new SimpleStringProperty(this, "title");
    private final ObjectProperty<EventHandler<? super MouseEvent>> onSelectButtonClicked = new SimpleObjectProperty<>(this, "onSelectButtonClicked");
    private final ObjectProperty<EventHandler<? super MouseEvent>> onDeleteButtonClicked = new SimpleObjectProperty<>(this, "onDeleteButtonClicked");
    private final ObjectProperty<Image> image = new SimpleObjectProperty<>(this, "image");

    public ImagePickerItem() {
        imageView = new ImageView();
        imageView.setSmooth(false);
        imageView.setPreserveRatio(true);

        JFXButton selectButton = new JFXButton();
        selectButton.setGraphic(SVG.pencil(Theme.blackFillBinding(), 15, 15));
        selectButton.onMouseClickedProperty().bind(onSelectButtonClicked);
        selectButton.getStyleClass().add("toggle-icon4");

        JFXButton deleteButton = new JFXButton();
        deleteButton.setGraphic(SVG.close(Theme.blackFillBinding(), 15, 15));
        deleteButton.onMouseClickedProperty().bind(onDeleteButtonClicked);
        deleteButton.getStyleClass().add("toggle-icon4");

        FXUtils.installTooltip(selectButton, i18n("button.edit"));

        HBox hBox = new HBox();
        hBox.getChildren().setAll(imageView, selectButton, deleteButton);
        hBox.setAlignment(Pos.CENTER_RIGHT);
        hBox.setSpacing(8);
        setRight(hBox);

        VBox vBox = new VBox();
        Label label = new Label();
        label.textProperty().bind(title);
        vBox.getChildren().setAll(label);
        vBox.setAlignment(Pos.CENTER_LEFT);
        setLeft(vBox);

        imageView.imageProperty().bind(image);
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

    public EventHandler<? super MouseEvent> getOnSelectButtonClicked() {
        return onSelectButtonClicked.get();
    }

    public ObjectProperty<EventHandler<? super MouseEvent>> onSelectButtonClickedProperty() {
        return onSelectButtonClicked;
    }

    public void setOnSelectButtonClicked(EventHandler<? super MouseEvent> onSelectButtonClicked) {
        this.onSelectButtonClicked.set(onSelectButtonClicked);
    }

    public EventHandler<? super MouseEvent> getOnDeleteButtonClicked() {
        return onDeleteButtonClicked.get();
    }

    public ObjectProperty<EventHandler<? super MouseEvent>> onDeleteButtonClickedProperty() {
        return onDeleteButtonClicked;
    }

    public void setOnDeleteButtonClicked(EventHandler<? super MouseEvent> onDeleteButtonClicked) {
        this.onDeleteButtonClicked.set(onDeleteButtonClicked);
    }

    public Image getImage() {
        return image.get();
    }

    public ObjectProperty<Image> imageProperty() {
        return image;
    }

    public void setImage(Image image) {
        this.image.set(image);
    }

    public ImageView getImageView() {
        return imageView;
    }
}
