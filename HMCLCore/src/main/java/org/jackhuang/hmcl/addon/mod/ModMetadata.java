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
package org.jackhuang.hmcl.addon.mod;

import org.jackhuang.hmcl.addon.LocalAddonFile;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

/// The loader-independent outcome of parsing one mod file's metadata.
///
/// Instances are produced by the readers in `org.jackhuang.hmcl.addon.meta` and carry no reference
/// to the owning [ModManager], so parsing touches no shared state. That property is what allows
/// [ModManager] to parse files off the manager lock and concurrently, and to cache the result of a
/// file across refreshes. The owning manager converts a value into a [LocalModFile] later, during
/// its single-threaded merge phase.
///
/// Values are immutable and safe to share between threads.
///
/// @param modId       the identifier declared by the mod metadata, never `null`
/// @param loaderType  the mod loader the file was recognized as, never `null`
/// @param name        the display name, `null` when the metadata omits it
/// @param description the description shown in the UI, never `null`
/// @param authors     the author list, `null` when the metadata omits it
/// @param version     the mod version, `null` when the metadata omits it
/// @param gameVersion the targeted game version, `null` when the metadata omits it
/// @param url         the project or update URL, `null` when the metadata omits it
/// @param logoPath    the icon path inside the archive, `null` when the metadata omits it
@NotNullByDefault
public record ModMetadata(
        String modId,
        ModLoaderType loaderType,
        @Nullable String name,
        LocalAddonFile.Description description,
        @Nullable String authors,
        @Nullable String version,
        @Nullable String gameVersion,
        @Nullable String url,
        @Nullable String logoPath
) {
}
