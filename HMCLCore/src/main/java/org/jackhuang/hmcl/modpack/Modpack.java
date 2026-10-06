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
package org.jackhuang.hmcl.modpack;

import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.game.GameInstanceID;
import org.jackhuang.hmcl.task.Task;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;
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
    private transient Charset encoding;
    private ModpackManifest manifest;

    public Modpack() {
        this("", null, null, null, null, null, null);
    }

    public Modpack(String name, String author, String version, String gameVersion, String description, Charset encoding, ModpackManifest manifest) {
        this.name = name;
        this.author = author;
        this.version = version;
        this.gameVersion = gameVersion;
        this.description = description;
        this.encoding = encoding;
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

    public Charset getEncoding() {
        return encoding;
    }

    public Modpack setEncoding(Charset encoding) {
        this.encoding = encoding;
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

    /// Returns whether a file path should be included in a modpack export.
    ///
    /// <p>A path is accepted when it passes the blacklist check and also either has no whitelist
    /// constraint ({@code null}) or is explicitly listed in the whitelist. An empty (non-null)
    /// whitelist rejects every path, reflecting a user selection of zero files.
    ///
    /// @param path      the archive-relative path to test; an empty string is always accepted
    /// @param blackList patterns for paths that must be excluded regardless of the whitelist
    /// @param whiteList the explicit inclusion list, or {@code null} to accept all non-blacklisted paths;
    ///                  an empty list means no path is accepted
    /// @return {@code true} if the path should be included
    public static boolean acceptFile(String path, List<String> blackList, @Nullable List<String> whiteList) {
        if (path.isEmpty())
            return true;
        if (ModAdviser.match(blackList, path, false))
            return false;
        if (whiteList == null)
            return true;
        for (String s : whiteList)
            if (path.equals(s))
                return true;
        return false;
    }
}
