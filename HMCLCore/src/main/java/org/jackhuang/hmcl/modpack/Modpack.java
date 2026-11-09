// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.modpack;

import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.game.GameInstanceID;
import org.jackhuang.hmcl.task.Task;
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
