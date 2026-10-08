/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2025  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.addon;

import org.jackhuang.hmcl.addon.update.AddonUpdate;
import org.jackhuang.hmcl.addon.update.AddonUpdateConditions;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jackhuang.hmcl.util.Pair;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/// Sub-classes should implement `Comparable`
public abstract class LocalAddonFile {

    protected LocalAddonFile() {
    }

    public abstract Path getFile();

    /// Without extension
    public abstract String getFileName();

    public boolean isDisabled() {
        return FileUtils.getName(getFile()).endsWith(LocalAddonManager.DISABLED_EXTENSION);
    }

    public abstract void markDisabled() throws IOException;

    public abstract void setOld(boolean old) throws IOException;

    public abstract boolean keepOldFiles();

    public abstract void delete() throws IOException;

    /// @return the update target version restrictions for this addon file, or null if not upgradable.
    @Nullable
    protected AddonUpdateConditions getTargetVersionRestrictions() {
        return null;
    }

    /// @return A pair of addon update info, the first for all versions and the second for release versions only (might be null).
    @Nullable
    public Pair<AddonUpdate, @Nullable AddonUpdate> checkUpdates(DownloadProvider downloadProvider, String gameVersion, RemoteAddon.Source source) throws IOException {
        var restrictions = getTargetVersionRestrictions();
        if (restrictions == null) return null;

        RemoteAddonRepository repo = source.getRepository();
        Optional<RemoteAddon.Version> currentVersion = repo.getRemoteVersionByLocalFile(getFile());
        if (currentVersion.isEmpty()) return null;
        var current = currentVersion.orElseThrow();

        var stream = repo.getRemoteVersionsById(downloadProvider, currentVersion.get().projectId())
                .filter(version -> version.gameVersions().contains(gameVersion));
        if (current.gameVersions().contains(gameVersion)) // Otherwise it means we are upgrading from another game version
            stream = stream.filter(version -> version.datePublished().isAfter(current.datePublished()));
        for (var p : restrictions.restrictions())
            stream = stream.filter(p);

        List<RemoteAddon.Version> remoteVersions = stream.sorted(Comparator.comparing(RemoteAddon.Version::datePublished).reversed()).toList();
        if (remoteVersions.isEmpty()) return null;
        var release = remoteVersions.stream()
                .filter(v -> v.versionType() == RemoteAddon.VersionType.Release)
                .findFirst().orElse(null);

        return Pair.pair(
                new AddonUpdate(this, current, remoteVersions.get(0)), // All channels
                release != null ? new AddonUpdate(this, current, release) : null // Release channel
        );
    }

    public void onUpdated(String newFileNameWithExt) {
    }

    @NotNullByDefault
    public record Description(@Unmodifiable List<Part> parts) {

        public Description {
            parts = List.copyOf(parts);
        }

        public Description(String text) {
            this(List.of(new Part(text, "black")));
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            for (Part part : parts) {
                builder.append(part.text);
            }
            return builder.toString();
        }

        public String toStringSingleLine() {
            return toString().lines().map(String::trim).filter(StringUtils::isNotBlank).collect(Collectors.joining(" | "));
        }

        public record Part(String text, String color) {

            public Part {
                Objects.requireNonNull(text);
                Objects.requireNonNull(color);
            }

            public Part(String text) {
                this(text, "");
            }
        }
    }
}
