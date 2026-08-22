/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.addon.mod;

import org.jackhuang.hmcl.addon.RemoteAddon;
import org.jackhuang.hmcl.addon.RemoteAddonRepository;
import org.jackhuang.hmcl.download.DownloadProvider;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/// Result of checking whether a local mod file is compatible with a target game version.
///
/// Unlike [LocalModFile#checkUpdates], which looks for a newer build and therefore requires the
/// candidate to be published later than the local file, this check asks whether the game versions
/// declared by the local file cover the target instance at all. Publication time is never compared,
/// so downgrades are covered as well: when the instance runs an older game version, the build that
/// fits it was usually published earlier.
///
/// The check relies entirely on the remote repository being able to identify the local file, since
/// mod metadata is not a usable source: `fabric.mod.json` has no game version field at all, so
/// [LocalModFile#getGameVersion()] is an empty string for every Fabric and Quilt mod.
///
/// @param localModFile      the local mod file being checked
/// @param status            the conclusion of the check
/// @param localGameVersions game versions the local file declares support for, or an empty list when
///                          the remote repository cannot identify the file
/// @param targetVersion     the build compatible with the target game version, `null` unless the
///                          status is [Status#REPLACEABLE]
/// @param source            the repository that produced this conclusion, `null` for [Status#UNKNOWN]
@NotNullByDefault
public record ModGameVersionCheck(
        LocalModFile localModFile,
        Status status,
        @Unmodifiable List<String> localGameVersions,
        @Nullable RemoteAddon.Version targetVersion,
        @Nullable RemoteAddon.Source source
) {

    /// Conclusion of a compatibility check.
    public enum Status {
        /// The local file declares support for the target game version, nothing to do.
        COMPATIBLE,

        /// The local file does not target the game version, but a compatible build exists and can be
        /// downloaded in place of it.
        REPLACEABLE,

        /// The local file does not target the game version and no compatible build exists, so the file
        /// can only be disabled.
        NO_CANDIDATE,

        /// The remote repository cannot identify the file, which happens for self-made, repackaged or
        /// modified jars. No conclusion can be drawn.
        UNKNOWN
    }

    /// Tests whether a set of declared game versions covers the target game version.
    ///
    /// Remote repositories report a set rather than a single value, because one jar commonly declares
    /// `1.20`, `1.20.1` and `1.20.2` at once. Membership is therefore the only correct test; comparing
    /// the first element would be wrong.
    ///
    /// @param declaredGameVersions game versions declared by a file or build
    /// @param targetGameVersion    the game version of the target instance
    /// @return `true` if the declaration covers the target game version
    public static boolean isCompatible(List<String> declaredGameVersions, String targetGameVersion) {
        return declaredGameVersions.contains(targetGameVersion);
    }

    /// Tests whether a remote build can be loaded by the given mod loader.
    ///
    /// An empty `loaders()` always passes: CurseForge derives loader information by recognizing strings
    /// inside the `gameVersions` array and only knows fabric, forge, quilt and neoforge, so `loaders()`
    /// is always empty for LiteLoader and Cleanroom mods. Filtering strictly here would make it
    /// impossible to ever find a candidate for those mods. [ModLoaderType#UNKNOWN] passes for the same
    /// reason: the local metadata could not be parsed, so there is nothing to compare against.
    ///
    /// @param version     the remote build to test
    /// @param localLoader the mod loader of the local file
    /// @return `true` if the build fits the loader, or if the information is too incomplete to rule it out
    public static boolean matchesLoader(RemoteAddon.Version version, ModLoaderType localLoader) {
        return localLoader == ModLoaderType.UNKNOWN
                || version.loaders().isEmpty()
                || version.loaders().contains(localLoader);
    }

    /// Selects the latest build that is compatible with the target game version.
    ///
    /// Filters by game version and mod loader only, never by publication time; among the builds that
    /// remain, the one published last wins.
    ///
    /// @param candidates        all remote builds of the project
    /// @param targetGameVersion the game version of the target instance
    /// @param localLoader       the mod loader of the local file
    /// @return the selected build, or `null` if none is compatible
    public static @Nullable RemoteAddon.Version selectCandidate(
            Stream<RemoteAddon.Version> candidates, String targetGameVersion, ModLoaderType localLoader) {
        return candidates
                .filter(version -> isCompatible(version.gameVersions(), targetGameVersion))
                .filter(version -> matchesLoader(version, localLoader))
                .max(Comparator.comparing(RemoteAddon.Version::datePublished))
                .orElse(null);
    }

    /// Checks a local mod file against one repository source.
    ///
    /// CurseForge identifies the file by its proprietary fingerprint and Modrinth by SHA-1. When neither
    /// can identify it, [Status#UNKNOWN] is returned and no guess is made.
    ///
    /// When the file already declares support for the target game version the build list is not fetched
    /// at all, saving one request per mod.
    ///
    /// @param localModFile      the local mod file to check
    /// @param downloadProvider  the download provider used when fetching the build list
    /// @param targetGameVersion the game version of the target instance
    /// @param source            the repository source to check against
    /// @return the check result, or `null` if the source provides no mod repository
    /// @throws IOException if a remote request fails; callers should treat the source as unavailable
    public static @Nullable ModGameVersionCheck check(
            LocalModFile localModFile,
            DownloadProvider downloadProvider,
            String targetGameVersion,
            RemoteAddon.Source source) throws IOException {
        Objects.requireNonNull(localModFile, "localModFile");
        Objects.requireNonNull(targetGameVersion, "targetGameVersion");

        RemoteAddonRepository repository = source.getRepoForType(RemoteAddon.Type.MOD);
        if (repository == null) {
            return null;
        }

        Optional<RemoteAddon.Version> currentVersion = repository.getRemoteVersionByLocalFile(localModFile.getFile());
        if (currentVersion.isEmpty()) {
            return new ModGameVersionCheck(localModFile, Status.UNKNOWN, List.of(), null, null);
        }

        @Unmodifiable List<String> localGameVersions = List.copyOf(currentVersion.get().gameVersions());

        // A remote entry declaring no game version at all is anomalous data, and any verdict here would
        // be a guess: calling it incompatible would mark a possibly working mod for disabling
        if (localGameVersions.isEmpty()) {
            return new ModGameVersionCheck(localModFile, Status.UNKNOWN, List.of(), null, source);
        }

        if (isCompatible(localGameVersions, targetGameVersion)) {
            return new ModGameVersionCheck(localModFile, Status.COMPATIBLE, localGameVersions, null, source);
        }

        RemoteAddon.Version candidate = selectCandidate(
                repository.getRemoteVersionsById(downloadProvider, currentVersion.get().projectId()),
                targetGameVersion,
                localModFile.getModLoaderType()
        );

        return candidate != null
                ? new ModGameVersionCheck(localModFile, Status.REPLACEABLE, localGameVersions, candidate, source)
                : new ModGameVersionCheck(localModFile, Status.NO_CANDIDATE, localGameVersions, null, source);
    }

    /// Merges the results a single mod file produced across several repository sources.
    ///
    /// Merge rules, in order of precedence:
    /// 1. Any [Status#COMPATIBLE] result wins. Two repositories may disagree about the game versions a
    ///    jar declares, and reporting a working mod as incompatible is worse than missing one.
    /// 2. Otherwise a result carrying a candidate wins; if several sources offer one, the build published
    ///    last is kept.
    /// 3. Otherwise any [Status#NO_CANDIDATE] result is kept.
    /// 4. If no source could identify the file, [Status#UNKNOWN] is returned.
    ///
    /// @param results per-source results for one mod file, possibly containing `null` entries for sources
    ///                that provide no mod repository
    /// @return the merged result, or `null` if `results` holds no non-`null` entry
    public static @Nullable ModGameVersionCheck merge(List<@Nullable ModGameVersionCheck> results) {
        ModGameVersionCheck unknown = null;
        ModGameVersionCheck noCandidate = null;
        ModGameVersionCheck replaceable = null;

        for (ModGameVersionCheck result : results) {
            if (result == null) {
                continue;
            }

            switch (result.status()) {
                case COMPATIBLE -> {
                    return result;
                }
                case REPLACEABLE -> {
                    // targetVersion is never null for REPLACEABLE; the null checks only satisfy the compiler
                    if (replaceable == null || replaceable.targetVersion() == null
                            || (result.targetVersion() != null && replaceable.targetVersion()
                            .datePublished().isBefore(result.targetVersion().datePublished()))) {
                        replaceable = result;
                    }
                }
                case NO_CANDIDATE -> {
                    if (noCandidate == null) {
                        noCandidate = result;
                    }
                }
                case UNKNOWN -> {
                    if (unknown == null) {
                        unknown = result;
                    }
                }
            }
        }

        if (replaceable != null) {
            return replaceable;
        }
        return noCandidate != null ? noCandidate : unknown;
    }

    /// Tests whether this conclusion requires the user to act.
    ///
    /// [Status#COMPATIBLE] needs nothing and [Status#UNKNOWN] cannot be acted upon, so neither should be
    /// shown to the user.
    ///
    /// @return `true` for [Status#REPLACEABLE] and [Status#NO_CANDIDATE]
    public boolean needsAction() {
        return status == Status.REPLACEABLE || status == Status.NO_CANDIDATE;
    }
}
