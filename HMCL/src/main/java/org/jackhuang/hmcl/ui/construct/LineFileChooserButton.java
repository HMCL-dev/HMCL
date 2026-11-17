// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.construct;

import javafx.beans.property.*;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.ui.PathSelector;
import org.jackhuang.hmcl.ui.SVG;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jsoup.internal.StringUtil;

import java.nio.file.Path;

@NotNullByDefault
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

    private final ObjectProperty<PathSelector> selector = new SimpleObjectProperty<>(this, "selector", new PathSelector());

    private final StringProperty location = new SimpleStringProperty(this, "location", "");

    {
        trailingTextProperty().bind(location);
    }

    public StringProperty locationProperty() {
        return location;
    }

    private @Nullable ObjectProperty<PathSelector.Type> type = null;

    public void setLocation(String location) {
        locationProperty().set(location);
    }

    private @Nullable BooleanProperty convertToRelativePath;

    public String getLocation() {
        return locationProperty().get();
    }

    @Override
    public void fire() {
        super.fire();

        if (!StringUtil.isBlank(getLocation())) {
            FileUtils.tryGetPath(getLocation()).ifPresent(getSelector()::setInitialDirectoryFromExisting);
        }

        Path path = getSelector().show(getType());
        if (path != null) {
            setLocation(processPath(path));
        }
    }

    public ObjectProperty<PathSelector.Type> typeProperty() {
        if (type == null) {
            type = new SimpleObjectProperty<>(this, "type", PathSelector.Type.OPEN);
        }
        return type;
    }

    public PathSelector.Type getType() {
        return type != null ? type.get() : PathSelector.Type.OPEN;
    }

    public void setType(PathSelector.Type type) {
        typeProperty().set(type);
    }

    public PathSelector getSelector() {
        return selector.get();
    }

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
