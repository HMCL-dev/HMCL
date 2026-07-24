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
package org.jackhuang.hmcl.upgrade;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Tests parsing of package-manager marker values.
@NotNullByDefault
public final class PackageManagerIntegrationTest {
    /// Verifies that conventional disabled marker values are not enabled.
    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", " ", "0", "false", "FALSE", "no", "NO", "off", "OFF"})
    public void shouldRejectDisabledMarkers(@Nullable String value) {
        assertFalse(PackageManagerIntegration.isEnabled(value));
    }

    /// Verifies that package type markers and conventional enabled values remain enabled.
    @ParameterizedTest
    @ValueSource(strings = {"1", "true", "deb", "rpm"})
    public void shouldAcceptEnabledMarkers(String value) {
        assertTrue(PackageManagerIntegration.isEnabled(value));
    }
}
