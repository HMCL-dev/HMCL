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
package org.jackhuang.hmcl.modpack.curse;

import java.util.List;
import java.util.Map;

/// The CurseForge metadata a modpack completion run needs, resolved without reading anything from
/// the instance being installed.
///
/// Both lookups behind it cost one round trip per manifest entry and depend on nothing but the
/// manifest, which is known as soon as the archive has been parsed. Resolving them there rather
/// than at the end takes a few hundred sequential round trips off the tail of the installation,
/// where they used to sit after every file had already been unpacked.
///
/// @param files          the manifest entries, carrying the file name, url and hashes each needs
/// @param addonClassIds  the class id of every entry CurseForge answered for, keyed by project id
/// @param allNameKnown   whether every entry's name could be resolved
/// @param notFound       whether some entry refers to a file CurseForge no longer serves
public record CurseManifestLookup(
        List<CurseManifestFile> files,
        Map<String, Integer> addonClassIds,
        boolean allNameKnown,
        boolean notFound) {
}
