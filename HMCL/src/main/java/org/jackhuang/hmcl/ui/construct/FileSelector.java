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
import org.jackhuang.hmcl.ui.PathChooser;
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
    private final PathChooser selector = new PathChooser();
    private final JFXTextField pathField = new JFXTextField();
    private final JFXButton selectButton = FXUtils.newToggleButton4(SVG.FOLDER_OPEN, 15);
    private final ReadOnlyObjectWrapper<@Nullable Path> path = new ReadOnlyObjectWrapper<>(this, "validPath", null);
    private final BooleanProperty existingValidationEnabled = new SimpleBooleanProperty(this, "existingValidationEnabled", true);
    private final BooleanProperty convertToRelativePath = new SimpleBooleanProperty(this, "convertToRelativePath", false);

    public FileSelector(SelectionMode selectionMode) {
        pathField.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(pathField, Priority.ALWAYS);
        pathField.setValidators(new Validator(i18n("selector.invalid_path"), s -> StringUtils.isBlank(s) || getPath() != null));
        FXUtils.setValidateWhileTextChanged(pathField, true);

        selectButton.setOnAction(e -> {
            selector.setInitialDirectory(getText());
            switch (selectionMode) {
                case OPEN_FILE -> postOpen(selector.showOpen());
                case DIRECTORY -> postOpen(selector.showOpenDirectory());
                case SAVE_FILE -> postOpen(selector.showSave());
                case FILE_OR_DIRECTORY -> {
                    PopupMenu selectPopupMenu = new PopupMenu();
                    JFXPopup selectModePopup = new JFXPopup(selectPopupMenu);

                    selectPopupMenu.getContent().addAll(
                            new IconedMenuItem(SVG.FILE_OPEN, i18n("selector.choose_file"), () -> postOpen(selector.showOpen()), selectModePopup),
                            new IconedMenuItem(SVG.FOLDER_OPEN, i18n("selector.choose_directory"), () -> postOpen(selector.showOpenDirectory()), selectModePopup)
                    );

                    selectModePopup.show(selectButton, JFXPopup.PopupVPosition.TOP, JFXPopup.PopupHPosition.RIGHT, -selectButton.getWidth(), 0);
                }
            }
        });

        setAlignment(Pos.CENTER_LEFT);
        setSpacing(3);
        getChildren().addAll(pathField, selectButton);

        path.bind(Bindings.createObjectBinding(() -> FileUtils.tryGetPath(getText())
                .filter(path -> !StringUtils.isBlank(getText()))
                .map(this::processPath)
                .filter(path -> !existingValidationEnabled.get() || switch (selectionMode) {
                    case DIRECTORY -> Files.isDirectory(path);
                    case OPEN_FILE -> Files.isRegularFile(path);
                    case SAVE_FILE -> true;
                    case FILE_OR_DIRECTORY -> Files.exists(path);
                }).orElse(null), textProperty(), existingValidationEnabled, convertToRelativePath));
        convertToRelativePath.addListener(o -> {
            if (getPath() != null)
                setText(getPath().toString());
        });
    }

    public @Nullable String getText() {
        return pathField.getText();
    }

    public void setText(@Nullable String value) {
        pathField.setText(value);
    }

    public StringProperty textProperty() {
        return pathField.textProperty();
    }

    public PathChooser getSelector() {
        return selector;
    }

    public void setExistingValidationEnabled(boolean enable) {
        existingValidationEnabled.set(enable);
        pathField.validate();
    }

    public ReadOnlyObjectProperty<@Nullable Path> pathProperty() {
        return path.getReadOnlyProperty();
    }

    public @Nullable Path getPath() {
        return path.get();
    }

    public @Nullable String getPathString() {
        return getPath() == null ? null : getPath().toString();
    }

    public void fire() {
        selectButton.fire();
    }

    private void postOpen(@Nullable Path path) {
        if (path != null)
            setText(processPath(path).toString());
    }

    private Path processPath(Path path) {
        Path absolutePath = FileUtils.toAbsolute(path);
        if (!convertToRelativePath.get() || !Metadata.CURRENT_DIRECTORY.getRoot().equals(absolutePath.getRoot()))
            return absolutePath;

        Path relativePath = Metadata.CURRENT_DIRECTORY.relativize(absolutePath);
        return relativePath.toString().isEmpty() ? Path.of(".") : relativePath;
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
