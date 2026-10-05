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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Verifies loader-specific mod dependency version predicates.
@NotNullByDefault
public final class ModVersionPredicateTest {
    /// Verifies Maven ranges, including open bounds and bare soft minimums.
    @Test
    public void testMavenRanges() {
        assertTrue(ModVersionPredicate.satisfies(ModLoaderType.FORGE, "[1.0,2.0)", "1.5"));
        assertFalse(ModVersionPredicate.satisfies(ModLoaderType.FORGE, "[1.0,2.0)", "2.0"));
        assertTrue(ModVersionPredicate.satisfies(ModLoaderType.NEO_FORGE, "(,2.0]", "1.9"));
        assertTrue(ModVersionPredicate.satisfies(ModLoaderType.FORGE, "1.2", "1.2.1"));
        assertFalse(ModVersionPredicate.satisfies(ModLoaderType.FORGE, "1.2", "1.1.9"));
    }

    /// Verifies Fabric extended SemVer AND/OR, wildcard, tilde, caret, build, and prerelease forms.
    @Test
    public void testFabricPredicates() {
        assertTrue(ModVersionPredicate.satisfies(ModLoaderType.FABRIC, ">=1.2 <2", "1.5.0"));
        assertTrue(ModVersionPredicate.satisfies(ModLoaderType.FABRIC, "1.2.x", "1.2.9"));
        assertFalse(ModVersionPredicate.satisfies(ModLoaderType.FABRIC, "1.2.x", "1.3.0"));
        assertTrue(ModVersionPredicate.satisfies(ModLoaderType.FABRIC, "~1.2.3", "1.2.9"));
        assertFalse(ModVersionPredicate.satisfies(ModLoaderType.FABRIC, "~1.2.3", "1.3.0"));
        assertTrue(ModVersionPredicate.satisfies(ModLoaderType.FABRIC, "^0.0.3", "0.0.3+build.4"));
        assertFalse(ModVersionPredicate.satisfies(ModLoaderType.FABRIC, "^0.0.3", "0.0.4"));
        assertTrue(ModVersionPredicate.satisfies(ModLoaderType.FABRIC, ">=1.0-", "1.0-beta.1"));
        assertTrue(ModVersionPredicate.satisfies(ModLoaderType.FABRIC, "v2.0", "2.0.0"));
    }

    /// Verifies opaque non-SemVer versions only use safe equality semantics.
    @Test
    public void testOpaqueFabricVersions() {
        assertTrue(ModVersionPredicate.satisfies(ModLoaderType.FABRIC, "release-final", "release-final"));
        assertFalse(ModVersionPredicate.satisfies(ModLoaderType.FABRIC, ">release-final", "release-next"));
    }

    /// Verifies Quilt's bare version means a compatible range rather than Fabric-style equality.
    @Test
    public void testQuiltBareVersion() {
        assertTrue(ModVersionPredicate.satisfies(ModLoaderType.QUILT, "1.2.3", "1.9.0"));
        assertFalse(ModVersionPredicate.satisfies(ModLoaderType.QUILT, "1.2.3", "2.0.0"));
    }
}
