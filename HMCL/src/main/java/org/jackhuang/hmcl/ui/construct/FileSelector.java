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
    private final PathChooser chooser = new PathChooser();
    private final JFXTextField pathField = new JFXTextField();
    private final StringProperty text = new SimpleStringProperty(this, "text");
    private final JFXButton selectButton = FXUtils.newToggleButton4(SVG.FOLDER_OPEN, 15);
    private final ReadOnlyObjectWrapper<@Nullable Path> path = new ReadOnlyObjectWrapper<>(this, "validPath", null);
    private final BooleanProperty convertToRelativePath = new SimpleBooleanProperty(this, "convertToRelativePath", false);

    public FileSelector(SelectionMode selectionMode) {
        pathField.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(pathField, Priority.ALWAYS);
        pathField.setValidators(new Validator(i18n("selector.invalid_path"), s -> StringUtils.isBlank(s) || getPath() != null));
        FXUtils.setValidateWhileTextChanged(pathField, true);

        selectButton.setOnAction(e -> {
            chooser.setInitialDirectory(pathField.getText());
            switch (selectionMode) {
                case OPEN_FILE -> postOpen(chooser.showOpen());
                case DIRECTORY -> postOpen(chooser.showOpenDirectory());
                case SAVE_FILE -> postOpen(chooser.showSave());
                case FILE_OR_DIRECTORY -> {
                    PopupMenu selectPopupMenu = new PopupMenu();
                    JFXPopup selectModePopup = new JFXPopup(selectPopupMenu);

                    selectPopupMenu.getContent().addAll(
                            new IconedMenuItem(SVG.FILE_OPEN, i18n("selector.choose_file"), () -> postOpen(chooser.showOpen()), selectModePopup),
                            new IconedMenuItem(SVG.FOLDER_OPEN, i18n("selector.choose_directory"), () -> postOpen(chooser.showOpenDirectory()), selectModePopup)
                    );

                    selectModePopup.show(selectButton, JFXPopup.PopupVPosition.TOP, JFXPopup.PopupHPosition.RIGHT, -selectButton.getWidth(), 0);
                }
            }
        });

        setAlignment(Pos.CENTER_LEFT);
        setSpacing(3);
        getChildren().addAll(pathField, selectButton);

        FXUtils.bindString(pathField, text);
        path.bind(Bindings.createObjectBinding(() -> FileUtils.tryGetPath(pathField.getText())
                .filter(path -> !StringUtils.isBlank(pathField.getText()))
                .map(this::processPath)
                .filter(path -> switch (selectionMode) {
                    case DIRECTORY -> Files.isDirectory(path) || Files.notExists(path);
                    case OPEN_FILE -> Files.isRegularFile(path);
                    case SAVE_FILE -> true;
                    case FILE_OR_DIRECTORY -> Files.exists(path);
                }).orElse(null), pathField.textProperty(), convertToRelativePath));
        convertToRelativePath.addListener(o -> {
            if (getPath() != null)
                setText(getPath().toString());
        });
    }

    public @Nullable String getText() {
        return text.get();
    }

    public void setText(@Nullable String value) {
        text.set(value);
    }

    public StringProperty textProperty() {
        return text;
    }

    public PathChooser getChooser() {
        return chooser;
    }

    /// A read-only property that tracks [#getPath()] as the input changes.
    public ReadOnlyObjectProperty<@Nullable Path> pathProperty() {
        return path.getReadOnlyProperty();
    }

    /// Returns the normalized path from the current input, or `null` if the input is blank
    /// or fails validation for the selection mode.
    public @Nullable Path getPath() {
        return path.get();
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
        return relativePath.toString().isBlank() ? Path.of(".") : relativePath;
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
