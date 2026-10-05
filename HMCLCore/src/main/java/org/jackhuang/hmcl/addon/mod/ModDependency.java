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

/// One dependency declaration preserved in the declaring loader's version dialect.
///
/// @param id the required capability or mod ID
/// @param versionConstraint the unmodified loader metadata constraint, or `*` for any version
/// @param optional whether absence is permitted
/// @param declaringLoader the loader whose constraint grammar applies
@NotNullByDefault
public record ModDependency(
        String id,
        String versionConstraint,
        boolean optional,
        ModLoaderType declaringLoader) {

    /// Creates and normalizes a dependency declaration without changing its constraint semantics.
    public ModDependency {
        id = Objects.requireNonNull(id, "id").trim();
        versionConstraint = Objects.requireNonNullElse(versionConstraint, "*").trim();
        declaringLoader = Objects.requireNonNullElse(declaringLoader, ModLoaderType.UNKNOWN);
    }

    /// Returns whether the given provider version satisfies this dependency.
    ///
    /// @param providerVersion the provider's declared version
    /// @return whether the version satisfies the loader-specific constraint
    public boolean accepts(String providerVersion) {
        return ModVersionPredicate.satisfies(declaringLoader, versionConstraint, providerVersion);
    }
}
