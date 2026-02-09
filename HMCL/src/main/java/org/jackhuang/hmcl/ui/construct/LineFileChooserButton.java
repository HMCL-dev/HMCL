/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026 huangyuhui <huanghongxun2008@126.com> and contributors
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

import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.ui.Controllers;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.util.io.FileUtils;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

public class LineFileChooserButton extends LineButton {
    private static final String DEFAULT_STYLE_CLASS = "line-file-select-button";

    public LineFileChooserButton() {
        getStyleClass().add(DEFAULT_STYLE_CLASS);
        setTrailingIcon(SVG.EDIT);
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

    @Override
    public void fire() {
        super.fire();

        Stage owner = Controllers.getStage(); // TODO: Allow user to set owner stage
        String windowTitle = getFileChooserTitle();

        Path initialDirectory = null;
        if (getLocation() != null) {
            Path file;
            try {
                file = FileUtils.toAbsolute(Path.of(getLocation()));
                if (Files.exists(file)) {
                    if (Files.isRegularFile(file))
                        initialDirectory = file.getParent();
                    else if (Files.isDirectory(file))
                        initialDirectory = file;
                }
            } catch (IllegalArgumentException e) {
                LOG.warning("Failed to resolve path: " + getLocation());
            }
        }

        Path path;
        Type type = getType();
        if (type == Type.OPEN_DIRECTORY) {
            var directoryChooser = new DirectoryChooser();
            if (windowTitle != null)
                directoryChooser.setTitle(windowTitle);
            if (initialDirectory != null)
                directoryChooser.setInitialDirectory(initialDirectory.toFile());

            path = FileUtils.toPath(directoryChooser.showDialog(owner));
        } else {
            var fileChooser = new FileChooser();
            if (windowTitle != null)
                fileChooser.setTitle(windowTitle);
            if (initialDirectory != null)
                fileChooser.setInitialDirectory(initialDirectory.toFile());

            if (extensionFilters != null)
                fileChooser.getExtensionFilters().setAll(extensionFilters);

            fileChooser.setInitialFileName(getInitialFileName());

            path = FileUtils.toPath(switch (type) {
                case OPEN_FILE -> fileChooser.showOpenDialog(owner);
                case SAVE_FILE -> fileChooser.showSaveDialog(owner);
                default -> throw new AssertionError("Unknown Type: " + type);
            });
        }

        if (path != null) {
            setLocation(processPath(path));
        }
    }

    private final StringProperty location = new StringPropertyBase() {
        @Override
        public Object getBean() {
            return LineFileChooserButton.this;
        }

        @Override
        public String getName() {
            return "location";
        }

        @Override
        protected void invalidated() {
            setTrailingText(get());
        }
    };

    public StringProperty locationProperty() {
        return location;
    }

    public String getLocation() {
        return locationProperty().get();
    }

    public void setLocation(String location) {
        locationProperty().set(location);
    }

    private final StringProperty fileChooserTitle = new SimpleStringProperty(this, "fileChooserTitle");

    public StringProperty fileChooserTitleProperty() {
        return fileChooserTitle;
    }

    public String getFileChooserTitle() {
        return fileChooserTitleProperty().get();
    }

    public void setFileChooserTitle(String fileChooserTitle) {
        fileChooserTitleProperty().set(fileChooserTitle);
    }

    private ObjectProperty<Type> type;

    public ObjectProperty<Type> typeProperty() {
        if (type == null) {
            type = new SimpleObjectProperty<>(this, "type", Type.OPEN_FILE);
        }
        return type;
    }

    public Type getType() {
        return type != null ? type.get() : Type.OPEN_FILE;
    }

    public void setType(Type type) {
        typeProperty().set(type);
    }

    private ObjectProperty<String> initialFileName;

    public final ObjectProperty<String> initialFileNameProperty() {
        if (initialFileName == null)
            initialFileName = new SimpleObjectProperty<>(this, "initialFileName");

        return initialFileName;
    }

    public final String getInitialFileName() {
        return initialFileName != null ? initialFileName.get() : null;
    }

    public final void setInitialFileName(String value) {
        initialFileNameProperty().set(value);
    }

    private ObservableList<FileChooser.ExtensionFilter> extensionFilters;

    public ObservableList<FileChooser.ExtensionFilter> getExtensionFilters() {
        if (extensionFilters == null)
            extensionFilters = FXCollections.observableArrayList();
        return extensionFilters;
    }

    private BooleanProperty convertToRelativePath;

    public BooleanProperty convertToRelativePathProperty() {
        if (convertToRelativePath == null)
            convertToRelativePath = new BooleanPropertyBase(false) {
                @Override
                public Object getBean() {
                    return LineFileChooserButton.this;
                }

                @Override
                public String getName() {
                    return "convertToRelativePath";
                }

                @Override
                protected void invalidated() {
                    String location = getLocation();
                    if (location == null)
                        return;
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

    public enum Type {
        OPEN_FILE,
        OPEN_DIRECTORY,
        SAVE_FILE
    }
}
