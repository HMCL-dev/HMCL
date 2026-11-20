// SPDX-FileCopyrightText: Copyright (C) 2021 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.construct;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import org.glavo.m3fx.controls.M3IconButton;
import org.glavo.m3fx.controls.M3TextField;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.io.FileUtils;

import java.nio.file.Path;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class FileSelector extends HBox {
    private final StringProperty value = new SimpleStringProperty();
    private String chooserTitle = "";
    private SelectionMode selectionMode = SelectionMode.FILE;
    private final ObservableList<FileChooser.ExtensionFilter> extensionFilters = FXCollections.observableArrayList();

    M3IconButton selectButton = new M3IconButton(SVG.FOLDER_OPEN.createIcon(15));

    public enum SelectionMode {
        FILE,
        DIRECTORY,
        FILE_OR_DIRECTORY
    }

    public String getValue() {
        return value.get();
    }

    public StringProperty valueProperty() {
        return value;
    }

    public void setValue(String value) {
        this.value.set(value);
    }

    public String getChooserTitle() {
        return chooserTitle;
    }

    public FileSelector setChooserTitle(String chooserTitle) {
        this.chooserTitle = chooserTitle;
        return this;
    }

    public SelectionMode getSelectionMode() {
        return selectionMode;
    }

    public FileSelector setSelectionMode(SelectionMode selectionMode) {
        this.selectionMode = selectionMode;
        return this;
    }

    public ObservableList<FileChooser.ExtensionFilter> getExtensionFilters() {
        return extensionFilters;
    }

    public FileSelector() {
        M3TextField customField = FXUtils.newTextField();
        customField.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(customField, Priority.ALWAYS);
        FXUtils.bindString(customField, valueProperty());

        selectButton.setOnAction(e -> {
            switch (selectionMode) {
                case FILE -> openFileChooser(customField);
                case DIRECTORY -> openDirectoryChooser(customField);
                case FILE_OR_DIRECTORY -> {
                    PopupMenu selectModePopup = new PopupMenu();

                    selectModePopup.getItems().addAll(
                            PopupMenu.item(SVG.FILE_OPEN, i18n("selector.choose_file"), () -> openFileChooser(customField)),
                            PopupMenu.item(SVG.FOLDER_OPEN, i18n("selector.choose_directory"), () -> openDirectoryChooser(customField))
                    );

                    selectModePopup.showBeside(selectButton, Pos.TOP_LEFT);
                }
            }
        });

        setAlignment(Pos.CENTER_LEFT);
        setSpacing(3);
        getChildren().addAll(customField, selectButton);
    }

    private void openFileChooser(M3TextField customField) {
        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().addAll(getExtensionFilters());
        chooser.setTitle(StringUtils.isBlank(chooserTitle) ? i18n("selector.choose_file") : chooserTitle);
        Path file = Controllers.showOpenDialog(chooser);
        if (file != null) {
            String path = FileUtils.getAbsolutePath(file);
            customField.setText(path);
            value.setValue(path);
        }
    }

    private void openDirectoryChooser(M3TextField customField) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle(StringUtils.isBlank(chooserTitle) ? i18n("selector.choose_directory") : chooserTitle);
        Path dir = Controllers.showDialog(chooser);
        if (dir != null) {
            String path = FileUtils.getAbsolutePath(dir);
            customField.setText(path);
            value.setValue(path);
        }
    }
}
