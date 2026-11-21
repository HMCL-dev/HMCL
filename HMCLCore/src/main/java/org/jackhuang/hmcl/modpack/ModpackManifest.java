// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.modpack;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/// Format-specific modpack manifest metadata.
@NotNullByDefault
public interface ModpackManifest {

    /// Returns the provider that understands this manifest.
    ///
    /// @return the modpack provider
    ModpackProvider getProvider();

    /// Marker for manifests that expose optional file entries.
    @NotNullByDefault
    interface SupportOptional extends ModpackManifest {

        /// Returns all files declared by this manifest, including required and optional ones.
        ///
        /// @return the modpack files
        @Unmodifiable
        List<? extends ModpackFile> getFiles();

        ModpackProvider.SupportOptional getProvider();

        default Set<String> getExcludedFilesFromAnother(ModpackManifest.SupportOptional other, Set<String> otherExcluded) {
            // The new manifest is the superset of the old manifest, so we return the exact set of excluded files from the old manifest.
            if (this == other)
                return otherExcluded;
            Set<String> otherKeyList = other.getFiles().stream().map(ModpackFile::key).collect(Collectors.toSet());
            if (getFiles().stream().map(ModpackFile::key).allMatch(otherKeyList::contains))
                return getFiles().stream().filter(ModpackFile::optional).map(ModpackFile::key).filter(otherExcluded::contains).collect(Collectors.toSet());
            List<? extends ModpackFile> otherExcludedFiles = other.getFiles().stream().filter(f -> otherExcluded.contains(f.key())).toList();

            // Otherwise, fuzzy match the excluded files based on similarity.
            return getFiles().stream()
                    .filter(f -> f.optional() && otherExcludedFiles.stream().anyMatch(of -> of.looksLike(f)))
                    .map(ModpackFile::key)
                    .collect(Collectors.toSet());
        }
    }
}
