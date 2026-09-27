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
