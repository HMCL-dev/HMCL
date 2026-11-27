// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.modpack;

import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.download.DefaultGameBuilder;
import org.jackhuang.hmcl.game.DefaultGameInstance;
import org.jackhuang.hmcl.game.GameInstanceID;
import org.jackhuang.hmcl.game.GameInstanceManifest;
import org.jackhuang.hmcl.task.FetchTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.task.TaskExecutionContext;
import org.jackhuang.hmcl.util.function.ExceptionalConsumer;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ThreadLocalRandom;

/**
 *
 * @author huangyuhui
 */
public abstract class Modpack {

    public static final Set<String> SUPPORTED_ICON_EXTS = Set.of("png", "jpg", "jpeg", "bmp", "gif", "webp", "apng");
    public static final Set<String> SUPPORTED_ICON_NAMES = Set.of("icon.png", "icon.jpg", "icon.jpeg", "icon.bmp", "icon.gif", "icon.webp", "icon.apng");

    private String name;
    private String author;
    private String version;
    private String gameVersion;
    private String description;
    private ModpackManifest manifest;

    public Modpack() {
        this("", null, null, null, null, null);
    }

    public Modpack(String name, String author, String version, String gameVersion, String description, ModpackManifest manifest) {
        this.name = name;
        this.author = author;
        this.version = version;
        this.gameVersion = gameVersion;
        this.description = description;
        this.manifest = manifest;
    }

    public String getName() {
        return name;
    }

    public Modpack setName(String name) {
        this.name = name;
        return this;
    }

    public String getAuthor() {
        return author;
    }

    public Modpack setAuthor(String author) {
        this.author = author;
        return this;
    }

    public String getVersion() {
        return version;
    }

    public Modpack setVersion(String version) {
        this.version = version;
        return this;
    }

    public String getGameVersion() {
        return gameVersion;
    }

    public Modpack setGameVersion(String gameVersion) {
        this.gameVersion = gameVersion;
        return this;
    }

    public String getDescription() {
        return description;
    }

    public Modpack setDescription(String description) {
        this.description = description;
        return this;
    }

    public ModpackManifest getManifest() {
        return manifest;
    }

    public Modpack setManifest(ModpackManifest manifest) {
        this.manifest = manifest;
        return this;
    }

    /// Creates the install task for this modpack.
    ///
    /// @param dependencyManager the dependency manager
    /// @param zipFile           the modpack archive
    /// @param instanceId        the target instance id
    /// @param iconUrl           the optional icon URL, or `null`
    /// @param excludedFiles   keys of optional files the user chose not to install; `null` or empty means
    ///                          install all files. When non-null, must not contain `null` elements.
    /// @return the install task
    public abstract Task<?> getInstallTask(
            DefaultDependencyManager dependencyManager,
            Path zipFile,
            GameInstanceID instanceId,
            String iconUrl,
            @Nullable Set<String> excludedFiles);

    /// Reserves the target and checks cancellation before accepting the installation result.
    /// Updates retain a backup on success and restore it on failure.
    /// Failure rolls back a new instance if its manifest is still current, except when
    /// `keepIncompleteInstance` allows manual recovery from a [ModpackCompletionException].
    /// Cleanup failures are suppressed on the installation failure.
    ///
    /// @param updateTarget the exact published instance to update, or `null` for a new installation
    /// @param install must finish preparatory writes before building and all instance writes before returning
    public static void installWithGameBuilder(
            TaskExecutionContext context, DefaultDependencyManager dependencyManager, GameInstanceID instanceId,
            @Nullable DefaultGameInstance updateTarget, boolean keepIncompleteInstance,
            ExceptionalConsumer<? super DefaultGameBuilder, Exception> install) throws Exception {
        try (DefaultGameBuilder builder = updateTarget == null
                ? dependencyManager.newGameBuilder(instanceId)
                : dependencyManager.newGameBuilder(updateTarget)) {
            context.checkCancellation();
            @Nullable Path backup = updateTarget == null ? null : createUpdateBackup(updateTarget);
            context.checkCancellation();
            try {
                try {
                    install.accept(builder);
                } catch (ModpackCompletionException incomplete) {
                    if (keepIncompleteInstance)
                        context.commit();
                    throw incomplete;
                }
                context.commit();
            } catch (Exception | Error failure) {
                try {
                    if (backup != null && updateTarget != null) {
                        builder.abort();
                        restoreUpdateBackup(updateTarget, backup, builder.getCommittedManifest());
                    } else if (!(keepIncompleteInstance && failure instanceof ModpackCompletionException)) {
                        builder.rollback();
                    }
                } catch (Exception | Error cleanup) {
                    failure.addSuppressed(cleanup);
                }
                throw failure;
            }
        }
    }

    /// Copies the reserved instance to a new backup retained after the update.
    public static Path createUpdateBackup(DefaultGameInstance instance) throws IOException {
        Path parent = Files.createDirectories(instance.getLayout().getBaseDirectory().resolve("backup"));
        Path backup;
        while (true) {
            backup = parent.resolve(instance.getId() + "-" + ThreadLocalRandom.current().nextInt(10_000_000));
            try {
                Files.createDirectory(backup);
                break;
            } catch (FileAlreadyExistsException ignored) {
                // Reserve a different name without touching an existing backup.
            }
        }
        FileUtils.copyDirectory(instance.getInstanceRoot(), backup);
        return backup;
    }

    /// Checks ownership after the install draft closes and refreshes a rolled-back publication.
    public static void restoreUpdateBackup(DefaultGameInstance instance, Path backup, @Nullable GameInstanceManifest published) throws IOException {
        var repository = instance.getRepository();
        try (var draft = repository.openDraft()) {
            @Nullable DefaultGameInstance current = draft.getBaseSnapshot().findInstance(instance.getId());
            if (current == null || current.getManifest() != (published == null ? instance.getManifest() : published))
                throw new IllegalStateException("Game instance changed before backup restoration: " + instance.getId());
            Path root = instance.getInstanceRoot();
            FileUtils.deleteDirectory(root);
            FileUtils.copyDirectory(backup, root);
        }
        if (published != null)
            repository.refresh();
    }

    /// Removes a retired pack file only when its path resolves inside the pack directory.
    public static void removePackFile(Path directory, Path file) throws IOException {
        Path root = directory.toAbsolutePath().normalize();
        Path target = file.toAbsolutePath().normalize();
        if (target.startsWith(root) && Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)
                && target.toRealPath().equals(root.toRealPath().resolve(root.relativize(target))))
            Files.deleteIfExists(target);
    }

    /// Downloads missing pack files. Cancellation or interruption takes precedence over incomplete files.
    public static void completeDownloads(TaskExecutionContext context, List<? extends Task<?>> downloads,
                                         @Nullable ModpackCompletionException incomplete) throws Exception {
        if (!downloads.isEmpty())
            context.addStageItems(downloads.size());
        try {
            context.runAll(downloads, FetchTask.getDownloadExecutorConcurrency());
        } catch (InterruptedException | CancellationException cancelled) {
            throw cancelled;
        } catch (Exception failure) {
            if (incomplete == null)
                incomplete = new ModpackCompletionException();
            incomplete.addSuppressed(failure);
        }
        context.checkCancellation();
        if (incomplete != null)
            throw incomplete;
    }

    public static boolean acceptFile(String path, List<String> blackList, List<String> whiteList) {
        if (path.isEmpty())
            return true;
        if (ModAdviser.match(blackList, path, false))
            return false;
        if (whiteList == null || whiteList.isEmpty())
            return true;
        for (String s : whiteList)
            if (path.equals(s))
                return true;
        return false;
    }
}
