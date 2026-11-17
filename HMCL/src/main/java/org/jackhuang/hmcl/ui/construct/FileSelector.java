// SPDX-FileCopyrightText: Copyright (C) 2021 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.construct;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXPopup;
import com.jfoenix.controls.JFXTextField;
import javafx.beans.property.StringProperty;
import javafx.geometry.Pos;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.PathSelector;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jsoup.internal.StringUtil;

import java.nio.file.Path;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

@NotNullByDefault
public class FileSelector extends HBox {
    private SelectionMode selectionMode = SelectionMode.FILE;
    private final PathSelector selector = new PathSelector();
    private final JFXTextField pathField = new JFXTextField();

    JFXButton selectButton = FXUtils.newToggleButton4(SVG.FOLDER_OPEN, 15);

    public enum SelectionMode {
        FILE,
        DIRECTORY,
        FILE_OR_DIRECTORY
    }

    public String getValue() {
        return pathField.getText();
    }

    public StringProperty valueProperty() {
        return pathField.textProperty();
    }

    public void setValue(String value) {
        pathField.setText(value);
    }

    public PathSelector getSelector() {
        return selector;
    }

    public FileSelector setSelectionMode(SelectionMode selectionMode) {
        this.selectionMode = selectionMode;
        return this;
    }

    public FileSelector() {
        pathField.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(pathField, Priority.ALWAYS);

        selectButton.setOnAction(e -> {
            if (!StringUtil.isBlank(getValue())) {
                FileUtils.tryGetPath(getValue()).ifPresent(selector::setInitialDirectoryFromExisting);
            }
            switch (selectionMode) {
                case FILE -> openFileChooser();
                case DIRECTORY -> openDirectoryChooser();
                case FILE_OR_DIRECTORY -> {
                    PopupMenu selectPopupMenu = new PopupMenu();
                    JFXPopup selectModePopup = new JFXPopup(selectPopupMenu);

                    selectPopupMenu.getContent().addAll(
                            new IconedMenuItem(SVG.FILE_OPEN, i18n("selector.choose_file"), this::openFileChooser, selectModePopup),
                            new IconedMenuItem(SVG.FOLDER_OPEN, i18n("selector.choose_directory"), this::openDirectoryChooser, selectModePopup)
                    );

                    selectModePopup.show(selectButton, JFXPopup.PopupVPosition.TOP, JFXPopup.PopupHPosition.RIGHT, -selectButton.getWidth(), 0);
                }
            }
        });

        setAlignment(Pos.CENTER_LEFT);
        setSpacing(3);
        getChildren().addAll(pathField, selectButton);
    }

    private void openFileChooser() {
        Path file = selector.showOpen();
        if (file != null) {
            pathField.setText(FileUtils.getAbsolutePath(file));
        }
    }

    private void openDirectoryChooser() {
        Path dir = selector.showOpenDirectory();
        if (dir != null) {
            pathField.setText(FileUtils.getAbsolutePath(dir));
        }
    }
}
