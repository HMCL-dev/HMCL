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
package org.jackhuang.hmcl.util;

import org.jackhuang.hmcl.download.LibraryAnalyzer;
import org.jackhuang.hmcl.download.RemoteVersion;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Tests for installer state stored in [SettingsMap].
@NotNullByDefault
public final class SettingsMapTest {
    /// Tests that vanilla game selection alone is not treated as a modded installation.
    @Test
    public void minecraftSelectionIsNotModdedInstallation() {
        SettingsMap settings = new SettingsMap();
        settings.put(LibraryAnalyzer.LibraryType.MINECRAFT.getPatchId(), remoteVersion("game"));

        assertFalse(settings.isInstallingModdedVersion());
    }

    /// Tests that selecting a non-vanilla installer is treated as a modded installation.
    @Test
    public void modLoaderSelectionIsModdedInstallation() {
        SettingsMap settings = new SettingsMap();
        settings.put(LibraryAnalyzer.LibraryType.MINECRAFT.getPatchId(), remoteVersion("game"));
        settings.put(LibraryAnalyzer.LibraryType.FABRIC.getPatchId(), remoteVersion("fabric"));

        assertTrue(settings.isInstallingModdedVersion());
    }

    /// Creates a minimal remote version for installer state tests.
    private static RemoteVersion remoteVersion(String libraryId) {
        return new RemoteVersion(libraryId, "1.21.11", "test", Instant.EPOCH, List.of());
    }
}
