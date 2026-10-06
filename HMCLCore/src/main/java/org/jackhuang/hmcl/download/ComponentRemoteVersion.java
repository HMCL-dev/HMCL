/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.download;

import org.jackhuang.hmcl.game.GameComponentType;
import org.jackhuang.hmcl.game.GameInstanceManifest;
import org.jackhuang.hmcl.game.GameInstancePatch;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.ToStringBuilder;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;
import org.jackhuang.hmcl.util.versioning.VersionNumber;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/// The remote version.
///
/// @author huangyuhui
@NotNullByDefault
public abstract class ComponentRemoteVersion implements Comparable<ComponentRemoteVersion> {

    private final GameComponentType componentType;
    private final GameVersionNumber gameVersion;
    private final String selfVersion;
    private final @Nullable Instant releaseDate;
    private final @Unmodifiable List<String> urls;
    private final Type type;

    /// Constructor.
    ///
    /// @param gameVersion the Minecraft version that this remote version suits.
    /// @param selfVersion the version string of the remote version.
    /// @param urls        the installer or universal jar URL.
    public ComponentRemoteVersion(
            GameComponentType componentType,
            GameVersionNumber gameVersion,
            String selfVersion,
            @Nullable Instant releaseDate, Type type, List<String> urls) {
        this.componentType = Objects.requireNonNull(componentType);
        this.gameVersion = Objects.requireNonNull(gameVersion);
        this.selfVersion = Objects.requireNonNull(selfVersion);
        this.releaseDate = releaseDate;
        this.urls = List.copyOf(urls);
        this.type = Objects.requireNonNull(type);
    }

    public GameComponentType getComponentType() {
        return componentType;
    }

    public GameVersionNumber getGameVersion() {
        return gameVersion;
    }

    public String getSelfVersion() {
        return selfVersion;
    }

    public String getFullVersion() {
        return getSelfVersion();
    }

    public @Nullable Instant getReleaseDate() {
        return releaseDate;
    }

    public @Unmodifiable List<String> getUrls() {
        return urls;
    }

    public Type getVersionType() {
        return type;
    }

    /// Creates an installation task with an explicit mods directory for libraries that download into the
    /// instance run tree (for example Fabric/Quilt API).
    ///
    /// @param dependencyManager the dependency manager
    /// @param baseManifest       the manifest being installed into
    /// @param modsDirectory     the mods directory of the target instance run directory
    /// @return the installation task
    public abstract Task<GameInstancePatch> getInstallTask(
            DefaultDependencyManager dependencyManager,
            GameInstanceManifest baseManifest,
            Path modsDirectory);

    @Override
    public boolean equals(Object obj) {
        return obj instanceof ComponentRemoteVersion that && Objects.equals(selfVersion, that.selfVersion);
    }

    @Override
    public int hashCode() {
        return selfVersion.hashCode();
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("selfVersion", selfVersion)
                .append("gameVersion", gameVersion)
                .toString();
    }

    @Override
    public int compareTo(ComponentRemoteVersion o) {
        if (this.getComponentType() != o.getComponentType()) {
            return this.getComponentType().compareTo(o.getComponentType());
        }

        // newer versions are smaller than older versions
        return VersionNumber.asVersion(o.selfVersion).compareTo(VersionNumber.asVersion(selfVersion));
    }

    public enum Type {
        UNCATEGORIZED,
        RELEASE,
        SNAPSHOT,
        OLD,
        PENDING,
        UNOBFUSCATED,
    }
}
