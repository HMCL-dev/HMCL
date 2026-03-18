/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
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
import com.jfoenix.controls.JFXPopup;
import com.jfoenix.controls.JFXTextField;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.layout.HBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
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

    JFXButton selectButton = FXUtils.newToggleButton4(SVG.FOLDER_OPEN, 15);

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

    public boolean isDirectory() {
        return selectionMode == SelectionMode.DIRECTORY;
    }

    public FileSelector setDirectory(boolean directory) {
        setSelectionMode(directory ? SelectionMode.DIRECTORY : SelectionMode.FILE);
        return this;
    }

    public ObservableList<FileChooser.ExtensionFilter> getExtensionFilters() {
        return extensionFilters;
    }

    public FileSelector() {
        JFXTextField customField = new JFXTextField();
        FXUtils.bindString(customField, valueProperty());

        selectButton.setOnAction(e -> {
            switch (selectionMode) {
                case FILE -> openFileChooser(customField);
                case DIRECTORY -> openDirectoryChooser(customField);
                case FILE_OR_DIRECTORY -> {
                    PopupMenu selectPopupMenu = new PopupMenu();
                    JFXPopup selectModePopup = new JFXPopup(selectPopupMenu);

                    selectPopupMenu.getContent().addAll(
                            new IconedMenuItem(SVG.FILE_OPEN, i18n("selector.choose_file"), () -> openFileChooser(customField), selectModePopup),
                            new IconedMenuItem(SVG.FOLDER_OPEN, i18n("selector.choose_folder"), () -> openDirectoryChooser(customField), selectModePopup)
                    );

                    selectModePopup.show(selectButton, JFXPopup.PopupVPosition.TOP, JFXPopup.PopupHPosition.LEFT, selectButton.getWidth(), 0);
                }
            }
        });

        setAlignment(Pos.CENTER_LEFT);
        setSpacing(3);
        getChildren().addAll(customField, selectButton);
    }

    private void openFileChooser(JFXTextField customField) {
        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().addAll(getExtensionFilters());
        chooser.setTitle(StringUtils.isBlank(chooserTitle) ? i18n("selector.choose_file") : chooserTitle);
        Path file = FileUtils.toPath(chooser.showOpenDialog(Controllers.getStage()));
        if (file != null) {
            String path = FileUtils.getAbsolutePath(file);
            customField.setText(path);
            value.setValue(path);
        }
    }

    private void openDirectoryChooser(JFXTextField customField) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle(StringUtils.isBlank(chooserTitle) ? i18n("selector.choose_folder") : chooserTitle);
        Path dir = FileUtils.toPath(chooser.showDialog(Controllers.getStage()));
        if (dir != null) {
            String path = FileUtils.getAbsolutePath(dir);
            customField.setText(path);
            value.setValue(path);
        }
    }
}
