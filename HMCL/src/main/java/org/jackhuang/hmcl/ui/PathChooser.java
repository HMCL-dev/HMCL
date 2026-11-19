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
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

@NotNullByDefault
public class PathChooser {
    private final ObservableList<FileChooser.ExtensionFilter> extensionFilter = FXCollections.observableArrayList();
    private ChooserContext context = ChooserContext.DEFAULT;
    private @Nullable Path initialDirectory;
    private @Nullable String title;
    private @Nullable String initialFileName;

    public static FileChooser.ExtensionFilter getImageExtensionFilter() {
        return new FileChooser.ExtensionFilter(i18n("extension.png"), FXUtils.IMAGE_EXTENSIONS.stream().map(ext -> "*." + ext).toList());
    }

    private static Optional<Path> getExistingPath(@Nullable String pathString) {
        return FileUtils.tryGetPath(pathString).filter(Files::exists).map(p -> {
            if (Files.isRegularFile(p))
                return p.getParent();
            else if (Files.isDirectory(p))
                return p;
            return null;
        });
    }

    public PathChooser setChooserContext(ChooserContext context) {
        this.context = context;
        return this;
    }

    public void setInitialDirectory(@Nullable String path) {
        initialDirectory = getExistingPath(path).orElse(null);
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

    private DirectoryChooser getDirectoryChooser() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setInitialDirectory(Objects.requireNonNullElseGet(initialDirectory, context::getDir).toFile());
        chooser.setTitle(StringUtils.isBlank(title) ? i18n("selector.choose_directory") : title);
        return chooser;
    }

    private FileChooser getFileChooser() {
        FileChooser chooser = new FileChooser();
        chooser.setInitialDirectory(Objects.requireNonNullElseGet(initialDirectory, context::getDir).toFile());
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

    public sealed interface ChooserContext {
        ChooserContext DEFAULT = new PersistentContext("default");
        ChooserContext IMPORT = new PersistentContext("import");
        ChooserContext IMPORT_IMAGE = new PersistentContext("import_image");
        ChooserContext EXPORT = new PersistentContext("export");

        static ChooserContext GAME(Supplier<@Nullable GameInstance> supplier) {
            return new ObjectContext<>(supplier, GameInstance::getRunDirectory);
        }

        Path getDir();

        default void postOpen(@Nullable Path path, boolean isDir) {
        }

        static Path getDefaultDir() {
            return GameDirectoryManager.getSelectedGameDirectory().getPath().toPath();
        }

        record PersistentContext(String name) implements ChooserContext {
            @Override
            public Path getDir() {
                String path = SettingsManager.userSettings().getRecentFileChooserPaths().get(name);
                return getExistingPath(path).orElseGet(ChooserContext::getDefaultDir);
            }

            @Override
            public void postOpen(@Nullable Path path, boolean isDir) {
                if (path != null) {
                    String s = FileUtils.getAbsolutePath(isDir ? path : path.getParent());
                    SettingsManager.userSettings().getRecentFileChooserPaths().put(name, s);
                }
            }
        }

        record ObjectContext<T>(Supplier<@Nullable T> supplier, Function<T, Path> func) implements ChooserContext {
            @Override
            public Path getDir() {
                return Optional.ofNullable(supplier.get())
                        .map(func)
                        .map(Object::toString)
                        .flatMap(PathChooser::getExistingPath)
                        .orElseGet(ChooserContext::getDefaultDir);
            }
        }
    }
}
