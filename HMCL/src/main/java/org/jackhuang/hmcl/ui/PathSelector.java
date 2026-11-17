// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import org.jackhuang.hmcl.setting.SettingsManager;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

@NotNullByDefault
public class PathSelector {
    private final ObservableList<FileChooser.ExtensionFilter> extensionFilter = FXCollections.observableArrayList();
    private ChooserContext context = ChooserContext.DEFAULT;
    private @Nullable Path initialDirectory;
    private @Nullable String title;
    private @Nullable String initialFileName;

    public static FileChooser.ExtensionFilter getImageExtensionFilter() {
        return new FileChooser.ExtensionFilter(i18n("extension.png"), FXUtils.IMAGE_EXTENSIONS.stream().map(ext -> "*." + ext).toList());
    }

    private Path getDir() {
        var path = FileUtils.tryGetPath(SettingsManager.userSettings().getRecentFileChooserPaths().get(context.name()));
        return path.orElseGet(ChooserContext.DEFAULT::getDefaultPath);
    }

    private void setDir(Path selected, boolean isDir) {
        String path = FileUtils.getAbsolutePath(isDir ? selected : selected.getParent());
        SettingsManager.userSettings().getRecentFileChooserPaths().put(context.name(), path);
    }

    public PathSelector setChooserContext(ChooserContext context) {
        this.context = context;
        return this;
    }

    public PathSelector setInitialDirectory(@Nullable Path initialDirectory) {
        this.initialDirectory = initialDirectory;
        return this;
    }

    private static @Nullable Path getInitialDirectoryFromExisting(Path existingPath) {
        if (Files.exists(existingPath)) {
            if (Files.isRegularFile(existingPath))
                return existingPath.getParent();
            else if (Files.isDirectory(existingPath))
                return existingPath;
        }
        return null;
    }

    public PathSelector setInitialDirectoryFromExisting(Path existingPath) {
        initialDirectory = getInitialDirectoryFromExisting(existingPath);
        return this;
    }

    public PathSelector setTitle(@Nullable String title) {
        this.title = title;
        return this;
    }

    public PathSelector setExtensionFilters(FileChooser.ExtensionFilter... extensionFilter) {
        this.extensionFilter.setAll(extensionFilter);
        return this;
    }

    public PathSelector setExtensionFilters(Collection<FileChooser.ExtensionFilter> extensionFilter) {
        this.extensionFilter.setAll(extensionFilter);
        return this;
    }

    public PathSelector setInitialFileName(@Nullable String initialFileName) {
        this.initialFileName = initialFileName;
        return this;
    }

    private DirectoryChooser getDirectoryChooser() {
        DirectoryChooser chooser = new DirectoryChooser();
        if (initialDirectory == null) {
            Path path = Objects.requireNonNullElseGet(getInitialDirectoryFromExisting(getDir()), ChooserContext.DEFAULT::getDefaultPath);
            chooser.setInitialDirectory(path.toFile());
        } else {
            chooser.setInitialDirectory(initialDirectory.toFile());
        }
        chooser.setTitle(StringUtils.isBlank(title) ? i18n("selector.choose_directory") : title);
        return chooser;
    }

    private FileChooser getFileChooser() {
        FileChooser chooser = new FileChooser();
        if (initialDirectory == null) {
            Path path = Objects.requireNonNullElseGet(getInitialDirectoryFromExisting(getDir()), ChooserContext.DEFAULT::getDefaultPath);
            chooser.setInitialDirectory(path.toFile());
        } else {
            chooser.setInitialDirectory(initialDirectory.toFile());
        }
        chooser.setTitle(StringUtils.isBlank(title) ? i18n("selector.choose_file") : title);
        chooser.getExtensionFilters().setAll(extensionFilter);
        chooser.setInitialFileName(initialFileName);
        return chooser;
    }

    public @Nullable Path showOpenDirectory() {
        @Nullable Path result = FileUtils.toPath(getDirectoryChooser().showDialog(Controllers.getStage()));
        if (result != null && initialDirectory == null) {
            setDir(result, true);
        }
        return result;
    }

    public @Nullable Path showOpen() {
        @Nullable Path result = FileUtils.toPath(getFileChooser().showOpenDialog(Controllers.getStage()));
        if (result != null && initialDirectory == null) {
            setDir(result, false);
        }
        return result;
    }

    public @Nullable Path showSave() {
        @Nullable Path result = FileUtils.toPath(getFileChooser().showSaveDialog(Controllers.getStage()));
        if (result != null && initialDirectory == null) {
            setDir(result, false);
        }
        return result;
    }

    public @Nullable @Unmodifiable List<Path> showMulti() {
        @Nullable List<File> result = getFileChooser().showOpenMultipleDialog(Controllers.getStage());
        if (result != null) {
            if (initialDirectory == null)
                setDir(result.get(result.size() - 1).toPath(), false);
            return result.stream().map(File::toPath).toList();
        }
        return null;
    }

    public @Nullable Path show(Type type) {
        return switch (type) {
            case OPEN -> showOpen();
            case SAVE -> showSave();
            case DIRECTORY -> showOpenDirectory();
        };
    }

    public enum ChooserContext {
        DEFAULT(() -> "");

        private final Path defaultPath;

        ChooserContext(Supplier<String> defaultPath) {
            this.defaultPath = Path.of(defaultPath.get());
        }

        public Path getDefaultPath() {
            return defaultPath;
        }
    }

    public enum Type {
        OPEN,
        SAVE,
        DIRECTORY
    }
}
