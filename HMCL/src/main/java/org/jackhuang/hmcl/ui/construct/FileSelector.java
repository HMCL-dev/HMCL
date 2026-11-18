// SPDX-FileCopyrightText: Copyright (C) 2021 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.construct;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXPopup;
import com.jfoenix.controls.JFXTextField;
import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.geometry.Pos;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.PathSelector;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

@NotNullByDefault
public class FileSelector extends HBox {
    private final PathSelector selector = new PathSelector();
    private final JFXTextField pathField = new JFXTextField();
    private final JFXButton selectButton = FXUtils.newToggleButton4(SVG.FOLDER_OPEN, 15);
    private final ReadOnlyObjectWrapper<@Nullable Path> validPath = new ReadOnlyObjectWrapper<>(this, "validPath", null);
    private final BooleanProperty existingValidationEnabled = new SimpleBooleanProperty(this, "existingValidationEnabled", true);
    private final BooleanProperty convertToRelativePath = new SimpleBooleanProperty(this, "convertToRelativePath", false) {
        @Override
        protected void invalidated() {
            if (getValidPath() != null)
                setLocation(processPath(FileUtils.toAbsolute(getValidPath())));
        }
    };

    public FileSelector(SelectionMode selectionMode) {
        pathField.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(pathField, Priority.ALWAYS);

        pathField.setValidators(new Validator(i18n("selector.invalid_path"), s -> StringUtils.isBlank(s) || getValidPath() != null));
        FXUtils.setValidateWhileTextChanged(pathField, true);
        selectButton.setOnAction(e -> {
            if (!getLocation().isBlank() && getValidPath() != null) {
                selector.setInitialDirectoryFromExisting(getValidPath());
            } else {
                selector.setInitialDirectory(null);
            }
            switch (selectionMode) {
                case OPEN_FILE -> open(PathSelector.Type.OPEN);
                case DIRECTORY -> open(PathSelector.Type.DIRECTORY);
                case SAVE_FILE -> open(PathSelector.Type.SAVE);
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

        validPath.bind(Bindings.createObjectBinding(() -> FileUtils.tryGetPath(getLocation())
                .filter(p -> !existingValidationEnabled.get() || switch (selectionMode) {
                    case DIRECTORY -> Files.isDirectory(p);
                    case OPEN_FILE -> Files.isRegularFile(p);
                    case SAVE_FILE -> true;
                    case FILE_OR_DIRECTORY -> Files.exists(p);
                }).orElse(null), locationProperty(), existingValidationEnabled));
    }

    public String getLocation() {
        return pathField.textProperty().getValueSafe();
    }

    public void setLocation(@Nullable String value) {
        pathField.setText(value);
    }

    public StringProperty locationProperty() {
        return pathField.textProperty();
    }

    public PathSelector getSelector() {
        return selector;
    }

    public void setExistingValidationEnabled(boolean enable) {
        existingValidationEnabled.set(enable);
        pathField.validate();
    }

    public ReadOnlyObjectProperty<@Nullable Path> validPathProperty() {
        return validPath.getReadOnlyProperty();
    }

    public @Nullable Path getValidPath() {
        return validPath.get();
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
        if (convertToRelativePath.get() && path.isAbsolute()) {
            try {
                return Metadata.CURRENT_DIRECTORY.relativize(path).normalize().toString();
            } catch (IllegalArgumentException e) {
                // the given path can't be relativized against current path
            }
        }
        return path.normalize().toString();
    }

    public BooleanProperty convertToRelativePathProperty() {
        return convertToRelativePath;
    }

    public enum SelectionMode {
        OPEN_FILE,
        SAVE_FILE,
        DIRECTORY,
        FILE_OR_DIRECTORY
    }
}
