// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.modpack;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

/// Format-specific modpack manifest metadata.
@NotNullByDefault
public interface ModpackManifest {

    /// Returns the provider that understands this manifest.
    ///
    /// @return the modpack provider
    ModpackProvider getProvider();

    /// Marker for manifests that expose optional file entries.
    @NotNullByDefault
    interface SupportOptional {

        /// Returns all files declared by this manifest, including required and optional ones.
        ///
        /// @return the modpack files
        @Unmodifiable
        List<? extends ModpackFile> getFiles();
    }
}
