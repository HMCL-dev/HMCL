// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.construct;

import com.jfoenix.controls.JFXButton;
import javafx.beans.DefaultProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

@DefaultProperty("image")
public final class ImagePickerItem extends BorderPane {

    private final ImageContainer imageContainer;

    private final StringProperty title = new SimpleStringProperty(this, "title");
    private final ObjectProperty<EventHandler<ActionEvent>> onSelectButtonClicked = new SimpleObjectProperty<>(this, "onSelectButtonClicked");
    private final ObjectProperty<EventHandler<ActionEvent>> onDeleteButtonClicked = new SimpleObjectProperty<>(this, "onDeleteButtonClicked");
    private final ObjectProperty<Image> image = new SimpleObjectProperty<>(this, "image");

    public ImagePickerItem() {
        imageContainer = new ImageContainer(32);
        imageContainer.setSmooth(false);

        JFXButton selectButton = FXUtils.newToggleButton4(SVG.EDIT, 20);
        selectButton.onActionProperty().bind(onSelectButtonClicked);

        JFXButton deleteButton = FXUtils.newToggleButton4(SVG.RESTORE, 20);
        deleteButton.onActionProperty().bind(onDeleteButtonClicked);

        FXUtils.installFastTooltip(selectButton, i18n("button.edit"));
        FXUtils.installFastTooltip(deleteButton, i18n("button.reset"));

        HBox hBox = new HBox();
        hBox.getChildren().setAll(imageContainer, selectButton, deleteButton);
        hBox.setAlignment(Pos.CENTER_RIGHT);
        hBox.setSpacing(8);
        setRight(hBox);

        VBox vBox = new VBox();
        Label label = new Label();
        label.textProperty().bind(title);
        vBox.getChildren().setAll(label);
        vBox.setAlignment(Pos.CENTER_LEFT);
        setLeft(vBox);

        imageContainer.imageProperty().bind(image);
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

    public EventHandler<ActionEvent> getOnSelectButtonClicked() {
        return onSelectButtonClicked.get();
    }

    public ObjectProperty<EventHandler<ActionEvent>> onSelectButtonClickedProperty() {
        return onSelectButtonClicked;
    }

    public void setOnSelectButtonClicked(EventHandler<ActionEvent> onSelectButtonClicked) {
        this.onSelectButtonClicked.set(onSelectButtonClicked);
    }

    public EventHandler<ActionEvent> getOnDeleteButtonClicked() {
        return onDeleteButtonClicked.get();
    }

    public ObjectProperty<EventHandler<ActionEvent>> onDeleteButtonClickedProperty() {
        return onDeleteButtonClicked;
    }

    public void setOnDeleteButtonClicked(EventHandler<ActionEvent> onDeleteButtonClicked) {
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
}
