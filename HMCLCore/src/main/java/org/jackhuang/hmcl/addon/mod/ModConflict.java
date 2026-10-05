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

import org.jetbrains.annotations.NotNullByDefault;

import java.util.Objects;

/// One loader-declared incompatible mod capability and version constraint.
///
/// @param id the conflicting capability or mod ID
/// @param versionConstraint the raw loader-specific range
/// @param hard whether the loader treats the conflict as a hard failure
/// @param declaringLoader the loader whose version grammar applies
@NotNullByDefault
public record ModConflict(
        String id,
        String versionConstraint,
        boolean hard,
        ModLoaderType declaringLoader) {

    /// Creates and normalizes a conflict declaration without changing its range semantics.
    public ModConflict {
        id = Objects.requireNonNull(id, "id").trim();
        versionConstraint = Objects.requireNonNullElse(versionConstraint, "*").trim();
        declaringLoader = Objects.requireNonNullElse(declaringLoader, ModLoaderType.UNKNOWN);
    }

    /// Returns whether a provider version activates this conflict.
    public boolean matches(String providerVersion) {
        return ModVersionPredicate.satisfies(declaringLoader, versionConstraint, providerVersion);
    }
}
