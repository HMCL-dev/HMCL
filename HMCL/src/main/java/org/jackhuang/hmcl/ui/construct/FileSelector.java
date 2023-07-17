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
import com.jfoenix.controls.JFXTextField;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.layout.HBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import org.jackhuang.hmcl.setting.Theme;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.SVG;

import java.io.File;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class FileSelector extends HBox {
    private final StringProperty value = new SimpleStringProperty();
    private String chooserTitle = i18n("selector.choose_file");
    private boolean directory = false;
    private final ObservableList<FileChooser.ExtensionFilter> extensionFilters = FXCollections.observableArrayList();

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

    public boolean isDirectory() {
        return directory;
    }

    public FileSelector setDirectory(boolean directory) {
        this.directory = directory;
        return this;
    }

    public ObservableList<FileChooser.ExtensionFilter> getExtensionFilters() {
        return extensionFilters;
    }

    public FileSelector() {
        JFXTextField customField = new JFXTextField();
        FXUtils.bindString(customField, valueProperty());

        JFXButton selectButton = new JFXButton();
        selectButton.setGraphic(SVG.folderOpen(Theme.blackFillBinding(), 15, 15));
        selectButton.setOnAction(e -> {
            if (directory) {
                DirectoryChooser chooser = new DirectoryChooser();
                chooser.setTitle(chooserTitle);
                File dir = chooser.showDialog(Controllers.getStage());
                if (dir != null) {
                    String path = dir.getAbsolutePath();
                    customField.setText(path);
                    value.setValue(path);
                }
            } else {
                FileChooser chooser = new FileChooser();
                chooser.getExtensionFilters().addAll(getExtensionFilters());
                chooser.setTitle(chooserTitle);
                File file = chooser.showOpenDialog(Controllers.getStage());
                if (file != null) {
                    String path = file.getAbsolutePath();
                    customField.setText(path);
                    value.setValue(path);
                }
            }
        });

        setAlignment(Pos.CENTER_LEFT);
        setSpacing(3);
        getChildren().addAll(customField, selectButton);
    }
}
