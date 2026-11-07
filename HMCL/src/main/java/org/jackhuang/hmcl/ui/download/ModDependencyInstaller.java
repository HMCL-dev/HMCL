// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.download;

import org.jackhuang.hmcl.addon.ModDependencyResolver;
import org.jackhuang.hmcl.addon.RemoteAddon;
import org.jackhuang.hmcl.util.DigestUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Plans required dependency downloads against locally installed mod metadata.
@NotNullByDefault
final class ModDependencyInstaller {

    /// Returns only the dependency versions that still need to be downloaded.
    static Plan plan(List<LocalModSnapshot> installedMods, ModDependencyResolver.ResolutionResult resolution,
                     Function<RemoteAddon.Version, Set<String>> projectModIds) {
        List<RemoteAddon.Version> dependenciesToDownload = new ArrayList<>();
        List<InstalledDependency> versionConflicts = new ArrayList<>();
        Map<Path, Map<String, String>> digestCache = new HashMap<>();

        for (RemoteAddon.Version dependency : resolution.versions()) {
            Set<String> modIds = projectModIds.apply(dependency);
            Optional<LocalModSnapshot> installed = modIds.isEmpty()
                    ? Optional.empty()
                    : installedMods.stream()
                    .filter(LocalModSnapshot::active)
                    .filter(mod -> modIds.contains(mod.id()))
                    .findFirst();

            if (installed.isPresent()) {
                boolean exactRequirement = resolution.exactVersionId(dependency).isPresent();
                if (!exactRequirement || fileHashesMatch(installed.get().file(), dependency.file(), digestCache)) {
                    continue;
                }
                versionConflicts.add(new InstalledDependency(dependency, installed.get()));
            } else {
                dependenciesToDownload.add(dependency);
            }
        }

        return new Plan(dependenciesToDownload, versionConflicts);
    }

    /// Returns whether a local file exactly matches any published hash of the remote file.
    static boolean fileHashesMatch(Path file, RemoteAddon.File remoteFile) {
        return fileHashesMatch(file, remoteFile, new HashMap<>());
    }

    /// Returns whether a local file exactly matches any published hash of the remote file.
    private static boolean fileHashesMatch(Path file, RemoteAddon.File remoteFile, Map<Path, Map<String, String>> digestCache) {
        if (!Files.isRegularFile(file)) {
            return false;
        }

        for (Map.Entry<String, String> publishedHash : remoteFile.hashes().entrySet()) {
            @Nullable String algorithm = toDigestAlgorithm(publishedHash.getKey());
            if (algorithm == null) {
                continue;
            }
            @Nullable String actualHash = digest(file, algorithm, digestCache);
            if (actualHash != null && actualHash.equalsIgnoreCase(publishedHash.getValue())) {
                return true;
            }
        }
        return false;
    }

    /// Maps repository hash keys to Java digest algorithm names.
    private static @Nullable String toDigestAlgorithm(String key) {
        return switch (key.toLowerCase(Locale.ROOT)) {
            case "md5" -> "MD5";
            case "sha1" -> "SHA-1";
            case "sha256" -> "SHA-256";
            case "sha512" -> "SHA-512";
            default -> null;
        };
    }

    /// Calculates and caches one file digest, returning null when the file cannot be read.
    private static @Nullable String digest(Path file, String algorithm, Map<Path, Map<String, String>> digestCache) {
        try {
            return digestCache.computeIfAbsent(file, path -> new HashMap<>())
                    .computeIfAbsent(algorithm, ignored -> {
                        try {
                            return DigestUtils.digestToString(algorithm, file);
                        } catch (IOException e) {
                            LOG.warning("Failed to calculate " + algorithm + " digest of " + file, e);
                            return null;
                        }
                    });
        } catch (RuntimeException e) {
            LOG.warning("Failed to calculate " + algorithm + " digest of " + file, e);
            return null;
        }
    }

    /// Prevents instantiation of this utility class.
    private ModDependencyInstaller() {
    }

    /// Minimal local mod metadata needed for installed-dependency checks.
    record LocalModSnapshot(String id, Path file, @Nullable String version, boolean active) {
    }

    /// An installed mod that may not satisfy an exact dependency requirement.
    record InstalledDependency(RemoteAddon.Version dependency, LocalModSnapshot installedMod) {
    }

    /// Dependency versions to download and installed versions that require user confirmation.
    record Plan(List<RemoteAddon.Version> dependenciesToDownload, List<InstalledDependency> versionConflicts) {

        /// Returns a plan that downloads dependencies whose installed versions may not satisfy requirements.
        private Plan includeVersionConflicts() {
            List<RemoteAddon.Version> downloads = new ArrayList<>(dependenciesToDownload);
            versionConflicts.stream().map(InstalledDependency::dependency).forEach(downloads::add);
            return new Plan(List.copyOf(downloads), List.of());
        }

        /// Returns whether the user confirmed downloading conflicting versions.
        Plan resolveConflicts(boolean downloadConflictingVersions) {
            return downloadConflictingVersions
                    ? includeVersionConflicts()
                    : new Plan(dependenciesToDownload, List.of());
        }
    }
}
