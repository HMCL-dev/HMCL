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

/// Opens JavaFX [FileChooser] and [DirectoryChooser] dialogs.
/// For the initial directory, it tries the explicit path, the chooser context, and the selected game directory in order.
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

    /// Sets an explicit starting path.
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

    /// Supplies a starting directory.
    public sealed interface ChooserContext {
        /// Remembers the last-used directory for general-purpose dialogs.
        ChooserContext DEFAULT = new PersistentContext("default");
        /// Remembers the last-used directory for imports such as mods and resource packs.
        ChooserContext IMPORT = new PersistentContext("import");
        /// Remembers the last-used directory for image imports such as icons and skins.
        ChooserContext IMPORT_IMAGE = new PersistentContext("import_image");
        /// Remembers the last-used directory for exports such as worlds and modpacks.
        ChooserContext EXPORT = new PersistentContext("export");

        /// Uses the supplied game instance's run directory when an instance is available.
        static ChooserContext GAME(Supplier<@Nullable GameInstance> supplier) {
            return new SimpleContext(() -> Optional.ofNullable(supplier.get()).map(GameInstance::getRunDirectory));
        }

        Optional<Path> getDir();

        default void postOpen(@Nullable Path path, boolean isDir) {
        }

        static Path getDefaultDir() {
            return GameDirectoryManager.getSelectedGameDirectory().getPath().toPath();
        }

        /// Stores the last-used directory in user settings under this context's name.
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
