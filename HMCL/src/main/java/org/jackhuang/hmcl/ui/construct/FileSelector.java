// SPDX-FileCopyrightText: Copyright (C) 2021 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.construct;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXPopup;
import com.jfoenix.controls.JFXTextField;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.StringProperty;
import javafx.geometry.Pos;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.PathSelector;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jsoup.internal.StringUtil;

import java.nio.file.Path;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

@NotNullByDefault
public class FileSelector extends HBox {
    private SelectionMode selectionMode = SelectionMode.FILE;
    private final PathSelector selector = new PathSelector();
    private final JFXTextField pathField = new JFXTextField();
    private final JFXButton selectButton = FXUtils.newToggleButton4(SVG.FOLDER_OPEN, 15);

    public enum SelectionMode {
        FILE,
        SAVE,
        DIRECTORY,
        FILE_OR_DIRECTORY
    }

    public String getLocation() {
        return pathField.getText();
    }

    public StringProperty locationProperty() {
        return pathField.textProperty();
    }

    public void setLocation(String value) {
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
            if (!StringUtil.isBlank(getLocation())) {
                FileUtils.tryGetPath(getLocation()).ifPresent(selector::setInitialDirectoryFromExisting);
            }
            switch (selectionMode) {
                case FILE -> open(PathSelector.Type.OPEN);
                case DIRECTORY -> open(PathSelector.Type.DIRECTORY);
                case SAVE -> open(PathSelector.Type.SAVE);
                case FILE_OR_DIRECTORY -> {
                    PopupMenu selectPopupMenu = new PopupMenu();
                    JFXPopup selectModePopup = new JFXPopup(selectPopupMenu);

                    selectPopupMenu.getContent().addAll(
                            new IconedMenuItem(SVG.FILE_OPEN, i18n("selector.choose_file"), () -> open(PathSelector.Type.OPEN), selectModePopup),
                            new IconedMenuItem(SVG.FOLDER_OPEN, i18n("selector.choose_directory"), () -> open(PathSelector.Type.DIRECTORY), selectModePopup)
                    );

                    selectModePopup.show(selectButton, JFXPopup.PopupVPosition.TOP, JFXPopup.PopupHPosition.RIGHT, -selectButton.getWidth(), 0);
                }
            }
        });

        setAlignment(Pos.CENTER_LEFT);
        setSpacing(3);
        getChildren().addAll(pathField, selectButton);
    }

    public void fire() {
        selectButton.fire();
    }

    private void open(PathSelector.Type type) {
        Path file = selector.show(type);
        if (file != null) {
            setLocation(processPath(file));
        }
    }

    /// Converts the given path to absolute/relative(if possible) path according to [#convertToRelativePathProperty()].
    private String processPath(Path path) {
        if (isConvertToRelativePath() && path.isAbsolute()) {
            try {
                return Metadata.CURRENT_DIRECTORY.relativize(path).normalize().toString();
            } catch (IllegalArgumentException e) {
                // the given path can't be relativized against current path
            }
        }
        return path.normalize().toString();
    }

    private @Nullable BooleanProperty convertToRelativePath;

    public BooleanProperty convertToRelativePathProperty() {
        if (convertToRelativePath == null)
            convertToRelativePath = new SimpleBooleanProperty(this, "convertToRelativePath", false) {
                @Override
                protected void invalidated() {
                    try {
                        setLocation(processPath(FileUtils.toAbsolute(Path.of(getLocation()))));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            };
        return convertToRelativePath;
    }

    public boolean isConvertToRelativePath() {
        return convertToRelativePath != null && convertToRelativePath.get();
    }

    public void setConvertToRelativePath(boolean convertToRelativePath) {
        convertToRelativePathProperty().set(convertToRelativePath);
    }
}
