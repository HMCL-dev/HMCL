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
package org.jackhuang.hmcl.modpack.modrinth;

import com.google.gson.JsonParseException;
import org.jackhuang.hmcl.addon.RemoteAddon;
import org.jackhuang.hmcl.modpack.ModpackFile;
import org.jackhuang.hmcl.modpack.ModpackManifest;
import org.jackhuang.hmcl.modpack.ModpackProvider;
import org.jackhuang.hmcl.util.DigestUtils;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.gson.JsonSerializable;
import org.jackhuang.hmcl.util.gson.TolerableValidationException;
import org.jackhuang.hmcl.util.gson.Validation;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/// Modrinth modpack index (`modrinth.index.json`).
@NotNullByDefault
public class ModrinthManifest implements ModpackManifest, ModpackManifest.SupportOptional, Validation {

    /// The game id, typically `minecraft`.
    private final String game;

    /// The Modrinth index format version.
    private final int formatVersion;

    /// The pack version id.
    private final String versionId;

    /// The pack display name.
    private final String name;

    /// The pack summary, or `null`.
    private final @Nullable String summary;

    /// Declared files in the pack.
    private final List<File> files;

    /// Dependency versions such as `minecraft` and loaders.
    private final Map<String, String> dependencies;

    /// Creates a Modrinth index.
    ///
    /// @param game          the game id
    /// @param formatVersion the format version
    /// @param versionId     the pack version id
    /// @param name          the pack name
    /// @param summary       the summary, or `null`
    /// @param files         the files
    /// @param dependencies  the dependency map
    public ModrinthManifest(String game, int formatVersion, String versionId, String name, @Nullable String summary, List<File> files, Map<String, String> dependencies) {
        this.game = game;
        this.formatVersion = formatVersion;
        this.versionId = versionId;
        this.name = name;
        this.summary = summary;
        this.files = files;
        this.dependencies = dependencies;
    }

    /// Returns the game id.
    ///
    /// @return the game id
    public String getGame() {
        return game;
    }

    /// Returns the format version.
    ///
    /// @return the format version
    public int getFormatVersion() {
        return formatVersion;
    }

    /// Returns the pack version id.
    ///
    /// @return the version id
    public String getVersionId() {
        return versionId;
    }

    /// Returns the pack name.
    ///
    /// @return the name
    public String getName() {
        return name;
    }

    /// Returns the summary, or an empty string when absent.
    ///
    /// @return the summary
    public String getSummary() {
        return summary == null ? "" : summary;
    }

    @Override
    public @Unmodifiable List<File> getFiles() {
        return files;
    }

    /// Returns a copy with a different file list.
    ///
    /// @param files the new files
    /// @return the updated manifest
    public ModrinthManifest withFiles(List<File> files) {
        return new ModrinthManifest(game, formatVersion, versionId, name, summary, files, dependencies);
    }

    /// Returns dependency versions such as Minecraft and loaders.
    ///
    /// @return the dependency map
    public Map<String, String> getDependencies() {
        return dependencies;
    }

    /// Returns the Minecraft version from dependencies.
    ///
    /// @return the Minecraft version
    public String getGameVersion() {
        return dependencies.get("minecraft");
    }

    @Override
    public ModpackProvider getProvider() {
        return ModrinthModpackProvider.INSTANCE;
    }

    @Override
    public void validate() throws JsonParseException, TolerableValidationException {
        if (dependencies == null || dependencies.get("minecraft") == null) {
            throw new JsonParseException("missing Modrinth.dependencies.minecraft");
        }
    }

    /// A file entry in a Modrinth index.
    @JsonSerializable
    @NotNullByDefault
    public record File(
            String path,
            Map<String, String> hashes,
            @Nullable Map<String, String> env,
            List<String> downloads,
            int fileSize,
            @Nullable RemoteAddon remoteAddon,
            boolean addonQueried) implements Validation, ModpackFile {

        /// Creates a file entry that has not been queried for remote addon metadata.
        ///
        /// @param path      the relative path
        /// @param hashes    the hashes
        /// @param env       the environment map, or `null`
        /// @param downloads the download URLs
        /// @param fileSize  the file size
        public File(String path, Map<String, String> hashes, @Nullable Map<String, String> env, List<String> downloads, int fileSize) {
            this(path, hashes, env, downloads, fileSize, null, false);
        }

        @Override
        public void validate() throws JsonParseException {
            if (StringUtils.isBlank(path))
                throw new JsonParseException("Modrinth file path is missing.");
            Path normalizedPath = Path.of(path).normalize();
            if (normalizedPath.isAbsolute() || normalizedPath.startsWith(".."))
                throw new JsonParseException("Modrinth file path escapes the instance directory: " + path);
            if (hashes == null || !DigestUtils.isSha512Digest(hashes.get("sha512")))
                throw new JsonParseException("Modrinth file sha512 is missing or invalid.");
            if (env != null && !env.containsKey("client"))
                throw new JsonParseException("Modrinth file env must contain a client key when present.");
            if (downloads == null || downloads.isEmpty())
                throw new JsonParseException("Modrinth file downloads are missing.");
        }

        @Override
        public String key() {
            return "modrinth:" + Objects.requireNonNull(hashes.get("sha512"), "sha512") + ":" + path;
        }

        @Override
        public String fileName() {
            return Path.of(path).getFileName().toString();
        }

        /// Returns a copy marked as queried, with the given remote addon metadata.
        ///
        /// @param remoteAddon the remote addon, or `null` when not found
        /// @return the updated file entry
        public File withAddon(@Nullable RemoteAddon remoteAddon) {
            return new File(path, hashes, env, downloads, fileSize, remoteAddon, true);
        }

        @Override
        public boolean optional() {
            return env != null && "optional".equals(env.get("client"));
        }

        @Override
        public boolean equals(Object o) {
            return this == o || o instanceof File file
                    && fileSize == file.fileSize
                    && path.equals(file.path)
                    && hashes.equals(file.hashes)
                    && Objects.equals(env, file.env)
                    && downloads.equals(file.downloads);
        }

        @Override
        public int hashCode() {
            return Objects.hash(path, hashes, env, downloads, fileSize);
        }
    }

}
