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
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Tests for {@link Modpack#acceptFile} covering the interaction between null and empty whitelists.
///
/// <p>Before the fix, an empty whitelist was treated identically to a null one (include everything).
/// After the fix, an empty whitelist means no file is accepted; only null means "no filter".
@NotNullByDefault
public class ModpackAcceptFileTest {

    /// Shared blacklist that excludes paths matching `logs/latest.log` exactly
    /// and any file whose name ends with `.log` via a regex pattern.
    private static final List<String> BLACKLIST = List.of("regex:(.*?)\\.log", "logs/latest.log");

    /// An empty whitelist (nothing selected) must reject every path, even non-blacklisted ones.
    @Test
    void emptyWhitelistRejectsAll() {
        List<String> emptyWhitelist = List.of();
        assertFalse(Modpack.acceptFile("mods/mod.jar", BLACKLIST, emptyWhitelist),
                "empty whitelist should reject a mod file");
        assertFalse(Modpack.acceptFile("config/settings.cfg", BLACKLIST, emptyWhitelist),
                "empty whitelist should reject a config file");
        assertFalse(Modpack.acceptFile("resourcepacks/pack.zip", BLACKLIST, emptyWhitelist),
                "empty whitelist should reject a resourcepack file");
    }

    /// A non-empty whitelist must accept exactly the listed paths and nothing else.
    @Test
    void nonEmptyWhitelistAcceptsOnlyListedFiles() {
        List<String> whitelist = List.of("mods/mod.jar", "config/settings.cfg");
        assertTrue(Modpack.acceptFile("mods/mod.jar", BLACKLIST, whitelist),
                "selected file must be accepted");
        assertTrue(Modpack.acceptFile("config/settings.cfg", BLACKLIST, whitelist),
                "selected file must be accepted");
        assertFalse(Modpack.acceptFile("resourcepacks/pack.zip", BLACKLIST, whitelist),
                "file not in whitelist must be rejected");
    }

    /// A null whitelist is the conventional "no filter" signal; all non-blacklisted paths pass.
    @Test
    void nullWhitelistAcceptsAllNonBlacklisted() {
        assertTrue(Modpack.acceptFile("mods/mod.jar", BLACKLIST, null),
                "null whitelist should accept a non-blacklisted file");
        // "logs/latest.log" is in the blacklist literally; it must be rejected regardless.
        assertFalse(Modpack.acceptFile("logs/latest.log", BLACKLIST, null),
                "null whitelist should still reject blacklisted files");
        // ".log" extension matches the regex blacklist pattern.
        assertFalse(Modpack.acceptFile("debug/crash.log", BLACKLIST, null),
                "null whitelist should still reject regex-blacklisted files");
    }

    /// Blacklist exclusion overrides a whitelist that explicitly names the same path.
    @Test
    void blacklistTakesPrecedenceOverWhitelist() {
        List<String> whitelist = List.of("logs/latest.log", "mods/mod.jar");
        // "logs/latest.log" is on the literal blacklist; listing it in the whitelist must not rescue it.
        assertFalse(Modpack.acceptFile("logs/latest.log", BLACKLIST, whitelist),
                "blacklisted path must be rejected even when whitelisted");
        assertTrue(Modpack.acceptFile("mods/mod.jar", BLACKLIST, whitelist),
                "non-blacklisted path explicitly in whitelist must be accepted");
    }
}
