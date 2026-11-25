// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import org.jackhuang.hmcl.game.GameInstance;
import org.jackhuang.hmcl.setting.GameDirectoryManager;
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
import java.util.Optional;
import java.util.function.Supplier;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// A wrapper of [FileChooser] & [DirectoryChooser]. Automatically sets initial directory according to context.
@NotNullByDefault
public class PathChooser {
    private final ObservableList<FileChooser.ExtensionFilter> extensionFilter = FXCollections.observableArrayList();
    private ChooserContext context = ChooserContext.DEFAULT;
    private @Nullable String initialDirectory;
    private @Nullable String title;
    private @Nullable String initialFileName;
    private @Nullable FileChooser fileChooser;
    private @Nullable DirectoryChooser directoryChooser;

    public static FileChooser.ExtensionFilter getImageExtensionFilter() {
        return new FileChooser.ExtensionFilter(i18n("extension.png"), FXUtils.IMAGE_EXTENSIONS.stream().map(ext -> "*." + ext).toList());
    }

    private static @Nullable Path toDirectory(Path path) {
        if (Files.isDirectory(path))
            return path;
        return Files.isRegularFile(path) ? path.getParent() : null;
    }

    public PathChooser setChooserContext(ChooserContext context) {
        this.context = context;
        return this;
    }

    public PathChooser setTitle(@Nullable String title) {
        this.title = title;
        return this;
    }

    public PathChooser setExtensionFilters(FileChooser.ExtensionFilter... extensionFilter) {
        this.extensionFilter.setAll(extensionFilter);
        return this;
    }

    public PathChooser setExtensionFilters(Collection<FileChooser.ExtensionFilter> extensionFilter) {
        this.extensionFilter.setAll(extensionFilter);
        return this;
    }

    public PathChooser setInitialFileName(@Nullable String initialFileName) {
        this.initialFileName = initialFileName;
        return this;
    }

    private Optional<Path> getInitialDirectory() {
        return FileUtils.tryGetPath(initialDirectory)
                .map(PathChooser::toDirectory)
                .or(() -> context.getDir().map(PathChooser::toDirectory))
                .or(() -> Optional.of(ChooserContext.getDefaultDir()).map(PathChooser::toDirectory));
    }

    /// This overrides the initial directory of context. Only take effects when given path exists.
    public void setInitialDirectory(@Nullable String path) {
        initialDirectory = StringUtils.isBlank(path) ? null : path;
    }

    private DirectoryChooser getDirectoryChooser() {
        DirectoryChooser chooser = directoryChooser == null ? (directoryChooser = new DirectoryChooser()) : directoryChooser;
        chooser.setInitialDirectory(getInitialDirectory().map(Path::toFile).orElse(null));
        chooser.setTitle(StringUtils.isBlank(title) ? i18n("selector.choose_directory") : title);
        return chooser;
    }

    private FileChooser getFileChooser() {
        FileChooser chooser = fileChooser == null ? (fileChooser = new FileChooser()) : fileChooser;
        chooser.setInitialDirectory(getInitialDirectory().map(Path::toFile).orElse(null));
        chooser.setTitle(StringUtils.isBlank(title) ? i18n("selector.choose_file") : title);
        chooser.getExtensionFilters().setAll(extensionFilter);
        chooser.setInitialFileName(initialFileName);
        return chooser;
    }

    public @Nullable Path showOpenDirectory() {
        @Nullable Path result = FileUtils.toPath(getDirectoryChooser().showDialog(Controllers.getStage()));
        context.postOpen(result, true);
        return result;
    }

    public @Nullable Path showOpen() {
        @Nullable Path result = FileUtils.toPath(getFileChooser().showOpenDialog(Controllers.getStage()));
        context.postOpen(result, false);
        return result;
    }

    public @Nullable Path showSave() {
        @Nullable Path result = FileUtils.toPath(getFileChooser().showSaveDialog(Controllers.getStage()));
        context.postOpen(result, false);
        return result;
    }

    public @Nullable @Unmodifiable List<Path> showMulti() {
        @Nullable List<File> result = getFileChooser().showOpenMultipleDialog(Controllers.getStage());
        if (result != null) {
            context.postOpen(result.get(result.size() - 1).toPath(), false);
            return result.stream().map(File::toPath).toList();
        }
        return null;
    }

    /// The context for the automatic determination of initial directory.
    public sealed interface ChooserContext {
        /// Persistent default context
        ChooserContext DEFAULT = new PersistentContext("default");
        /// Persistent context for file (except image) imports. e.g. mod, resource pack...
        ChooserContext IMPORT = new PersistentContext("import");
        /// Persistent context for image imports. e.g. icon, skin...
        ChooserContext IMPORT_IMAGE = new PersistentContext("import_image");
        /// Persistent context for exports. e.g. world export, modpack export...
        ChooserContext EXPORT = new PersistentContext("export");

        /// Context for related game directories. e.g. game setting, download...
        static ChooserContext GAME(Supplier<@Nullable GameInstance> supplier) {
            return new SimpleContext(() -> Optional.ofNullable(supplier.get()).map(GameInstance::getRunDirectory));
        }

        Optional<Path> getDir();

        default void postOpen(@Nullable Path path, boolean isDir) {
        }

        static Path getDefaultDir() {
            return GameDirectoryManager.getSelectedGameDirectory().getPath().toPath();
        }

        /// The path most recently used will be saved persistently in user settings.
        record PersistentContext(String name) implements ChooserContext {
            @Override
            public Optional<Path> getDir() {
                return FileUtils.tryGetPath(SettingsManager.userSettings().getRecentFileChooserPaths().get(name));
            }

            @Override
            public void postOpen(@Nullable Path path, boolean isDir) {
                if (path != null) {
                    String s = FileUtils.getAbsolutePath(isDir ? path : path.getParent());
                    SettingsManager.userSettings().getRecentFileChooserPaths().put(name, s);
                }
            }
        }

        record SimpleContext(Supplier<Optional<Path>> supplier) implements ChooserContext {
            @Override
            public Optional<Path> getDir() {
                return supplier.get();
            }
        }
    }
}
