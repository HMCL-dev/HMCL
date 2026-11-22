// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.modpack;

import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.download.DefaultGameBuilder;
import org.jackhuang.hmcl.download.GameBuilder;
import org.jackhuang.hmcl.game.DefaultGameInstance;
import org.jackhuang.hmcl.game.GameInstanceID;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.function.ExceptionalConsumer;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

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

    /// Reserves the target instance for a synchronous installation and aborts any draft still open
    /// when the action finishes. A failed new installation rolls back the instance published by the
    /// builder if its stored manifest is still current, unless the action reports [ModpackCompletionException]
    /// and `keepIncompleteInstance` is true. A reservation failure skips the installation action.
    /// Cleanup failures are suppressed on the installation failure.
    ///
    /// @param dependencyManager the manager that creates the game builder
    /// @param instanceId the id to reserve for a new installation
    /// @param updateTarget the exact published instance to update, or `null` for a new installation
    /// @param keepIncompleteInstance whether a new instance remains available for manual completion
    /// @param install the action that configures the builder and runs the installation to completion.
    ///                Preparatory writes must finish before the build task starts so that it can safely
    ///                abort its draft. All instance writes must have stopped when the action exits.
    /// @throws Exception if reservation, installation, or cleanup fails
    public static void installWithGameBuilder(
            DefaultDependencyManager dependencyManager, GameInstanceID instanceId,
            @Nullable DefaultGameInstance updateTarget, boolean keepIncompleteInstance,
            ExceptionalConsumer<GameBuilder, Exception> install) throws Exception {
        DefaultGameBuilder builder = updateTarget == null
                ? dependencyManager.newGameBuilder(instanceId)
                : dependencyManager.newGameBuilder(updateTarget);
        // A build task that is rejected or cancelled before it starts cannot abort its draft.
        try {
            install.accept(builder);
        } catch (Exception | Error failure) {
            try {
                if (keepIncompleteInstance && failure instanceof ModpackCompletionException)
                    builder.abort();
                else
                    builder.rollback();
            } catch (Exception | Error cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
        builder.abort();
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
