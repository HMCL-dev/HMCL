/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026  huangyuhui <huanghongxun2008@126.com> and contributors
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
import org.jackhuang.hmcl.addon.RemoteAddon;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Covers the network-independent decision and selection logic of [ModGameVersionCheck].
@NotNullByDefault
public final class ModGameVersionCheckTest {

    private static final Instant BASE = Instant.parse("2024-01-01T00:00:00Z");

    /// Builds a remote build.
    ///
    /// @param name          build name, reused as version and file name
    /// @param publishOffset publication offset in seconds relative to [#BASE], controlling which build is newer
    /// @param gameVersions  game versions the build declares support for
    /// @param loaders       mod loaders the build declares support for
    /// @return a build usable by the selection logic
    private static RemoteAddon.Version version(String name, long publishOffset,
                                               List<String> gameVersions, List<ModLoaderType> loaders) {
        return new RemoteAddon.Version(
                () -> RemoteAddon.Source.CURSEFORGE,
                "version-" + name,
                "project-1",
                name,
                name,
                BASE.plusSeconds(publishOffset),
                RemoteAddon.VersionType.Release,
                new RemoteAddon.File(Map.of(), "https://example.invalid/" + name + ".jar", name + ".jar"),
                List.of(),
                gameVersions,
                loaders
        );
    }

    /// Builds a placeholder local mod file.
    ///
    /// Constructing a [LocalModFile] only calls [ModManager#isDisabled] and [ModManager#isOld], both of
    /// which inspect the file name alone and never touch the owning instance, so a [ModManager] without
    /// an instance is safe here.
    ///
    /// @param fileName the file name, which determines [LocalModFile#getFileName()] and the active state
    /// @return the local mod file
    @SuppressWarnings("DataFlowIssue")
    private static LocalModFile localModFile(String fileName) {
        ModManager modManager = new ModManager(null);
        LocalMod mod = new LocalMod("example", ModLoaderType.FORGE);
        return new LocalModFile(modManager, mod, Path.of("mods", fileName), "Example",
                new LocalAddonFile.Description(""));
    }

    /// Builds a check result.
    ///
    /// @param status the conclusion
    /// @param target the target build, may be `null`
    /// @return the check result
    private static ModGameVersionCheck check(ModGameVersionCheck.Status status,
                                             @Nullable RemoteAddon.Version target) {
        return new ModGameVersionCheck(localModFile("example.jar"), status,
                List.of("1.20.1"), target, RemoteAddon.Source.CURSEFORGE);
    }

    /// Declared game versions are treated as a set, since one jar commonly declares several at once.
    @Test
    public void treatsDeclaredGameVersionsAsASet() {
        assertTrue(ModGameVersionCheck.isCompatible(List.of("1.20", "1.20.1", "1.20.2"), "1.20.1"));
        assertFalse(ModGameVersionCheck.isCompatible(List.of("1.20", "1.20.1"), "1.21.1"));
        assertFalse(ModGameVersionCheck.isCompatible(List.of(), "1.20.1"));
    }

    /// Missing loader information must pass, otherwise LiteLoader and Cleanroom mods could never find a
    /// candidate build.
    @Test
    public void passesLoaderCheckWhenInformationIsMissing() {
        RemoteAddon.Version forgeOnly = version("forge", 0, List.of("1.20.1"), List.of(ModLoaderType.FORGE));
        RemoteAddon.Version noLoader = version("none", 0, List.of("1.20.1"), List.of());

        assertTrue(ModGameVersionCheck.matchesLoader(forgeOnly, ModLoaderType.FORGE));
        assertFalse(ModGameVersionCheck.matchesLoader(forgeOnly, ModLoaderType.FABRIC));

        // CurseForge 认不出 LiteLoader / Cleanroom，loaders() 恒为空
        assertTrue(ModGameVersionCheck.matchesLoader(noLoader, ModLoaderType.LITE_LOADER));
        // 本地元数据没读出加载器时无从比较
        assertTrue(ModGameVersionCheck.matchesLoader(forgeOnly, ModLoaderType.UNKNOWN));
    }

    /// Among several builds for the same game version, the one published last is selected.
    @Test
    public void selectsTheLatestBuildForTheTargetGameVersion() {
        RemoteAddon.Version old = version("old", 0, List.of("1.21.1"), List.of(ModLoaderType.FORGE));
        RemoteAddon.Version latest = version("latest", 1000, List.of("1.21.1"), List.of(ModLoaderType.FORGE));
        RemoteAddon.Version otherGameVersion =
                version("other", 2000, List.of("1.20.1"), List.of(ModLoaderType.FORGE));

        RemoteAddon.Version selected = ModGameVersionCheck.selectCandidate(
                List.of(old, latest, otherGameVersion).stream(), "1.21.1", ModLoaderType.FORGE);

        assertSame(latest, selected);
    }

    /// Downgrades: when the instance runs an older game version than the mod, the build that fits it was
    /// usually published earlier and must not be excluded for that reason.
    ///
    /// This is the core difference from [LocalModFile#checkUpdates], which requires the candidate to be
    /// newer than the local file.
    @Test
    public void selectsAnOlderBuildWhenDowngrading() {
        RemoteAddon.Version buildFor1201 = version("for-1.20.1", 0, List.of("1.20.1"), List.of(ModLoaderType.FORGE));
        RemoteAddon.Version buildFor1211 =
                version("for-1.21.1", 5000, List.of("1.21.1"), List.of(ModLoaderType.FORGE));

        RemoteAddon.Version selected = ModGameVersionCheck.selectCandidate(
                List.of(buildFor1201, buildFor1211).stream(), "1.20.1", ModLoaderType.FORGE);

        assertSame(buildFor1201, selected);
    }

    /// Returns `null` when the loader does not match or no build targets the game version.
    @Test
    public void returnsNoCandidateWhenNothingMatches() {
        RemoteAddon.Version fabricOnly = version("fabric", 0, List.of("1.21.1"), List.of(ModLoaderType.FABRIC));

        assertNull(ModGameVersionCheck.selectCandidate(
                List.of(fabricOnly).stream(), "1.21.1", ModLoaderType.FORGE));
        assertNull(ModGameVersionCheck.selectCandidate(
                List.of(fabricOnly).stream(), "1.20.1", ModLoaderType.FABRIC));
        assertNull(ModGameVersionCheck.selectCandidate(
                List.<RemoteAddon.Version>of().stream(), "1.21.1", ModLoaderType.FORGE));
    }

    /// Any source reporting compatibility wins: the two repositories may disagree about the game versions
    /// a jar declares, and a false negative is preferable to a false positive.
    @Test
    public void prefersCompatibleResultFromAnySource() {
        ModGameVersionCheck compatible = check(ModGameVersionCheck.Status.COMPATIBLE, null);
        ModGameVersionCheck replaceable = check(ModGameVersionCheck.Status.REPLACEABLE,
                version("candidate", 0, List.of("1.21.1"), List.of(ModLoaderType.FORGE)));

        assertSame(compatible, ModGameVersionCheck.merge(Arrays.asList(replaceable, compatible)));
        assertSame(compatible, ModGameVersionCheck.merge(Arrays.asList(compatible, replaceable)));
    }

    /// A result carrying a candidate takes precedence over disable-only and unidentifiable results.
    @Test
    public void prefersReplaceableOverFallbackResults() {
        ModGameVersionCheck replaceable = check(ModGameVersionCheck.Status.REPLACEABLE,
                version("candidate", 0, List.of("1.21.1"), List.of(ModLoaderType.FORGE)));
        ModGameVersionCheck noCandidate = check(ModGameVersionCheck.Status.NO_CANDIDATE, null);
        ModGameVersionCheck unknown = check(ModGameVersionCheck.Status.UNKNOWN, null);

        assertSame(replaceable, ModGameVersionCheck.merge(Arrays.asList(unknown, noCandidate, replaceable)));
        assertSame(noCandidate, ModGameVersionCheck.merge(Arrays.asList(unknown, noCandidate)));
    }

    /// When several sources offer a candidate, the build published last is kept.
    @Test
    public void picksTheNewerCandidateAcrossSources() {
        ModGameVersionCheck older = check(ModGameVersionCheck.Status.REPLACEABLE,
                version("older", 0, List.of("1.21.1"), List.of(ModLoaderType.FORGE)));
        ModGameVersionCheck newer = check(ModGameVersionCheck.Status.REPLACEABLE,
                version("newer", 1000, List.of("1.21.1"), List.of(ModLoaderType.FORGE)));

        assertSame(newer, ModGameVersionCheck.merge(Arrays.asList(older, newer)));
        assertSame(newer, ModGameVersionCheck.merge(Arrays.asList(newer, older)));
    }

    /// Falls back to the unidentifiable result when no source could identify the file, and to `null` when
    /// there is no result at all.
    @Test
    public void fallsBackToUnknownAndNull() {
        ModGameVersionCheck unknown = check(ModGameVersionCheck.Status.UNKNOWN, null);

        assertSame(unknown, ModGameVersionCheck.merge(Arrays.asList(null, unknown)));
        assertNull(ModGameVersionCheck.merge(Arrays.asList(null, null)));
        assertNull(ModGameVersionCheck.merge(List.of()));
    }

    /// Only conclusions requiring a download or a disable should be shown to the user.
    @Test
    public void reportsWhetherUserActionIsNeeded() {
        assertTrue(check(ModGameVersionCheck.Status.REPLACEABLE,
                version("candidate", 0, List.of("1.21.1"), List.of(ModLoaderType.FORGE))).needsAction());
        assertTrue(check(ModGameVersionCheck.Status.NO_CANDIDATE, null).needsAction());
        assertFalse(check(ModGameVersionCheck.Status.COMPATIBLE, null).needsAction());
        assertFalse(check(ModGameVersionCheck.Status.UNKNOWN, null).needsAction());
    }

    /// The check result must pass the local file and source through unchanged, since the page and the
    /// migration task both rely on them.
    @Test
    public void keepsLocalFileAndSource() {
        LocalModFile localModFile = localModFile("jei-1.20.1-forge.jar");
        ModGameVersionCheck result = new ModGameVersionCheck(localModFile,
                ModGameVersionCheck.Status.NO_CANDIDATE, List.of("1.20", "1.20.1"), null,
                RemoteAddon.Source.MODRINTH);

        assertSame(localModFile, result.localModFile());
        assertEquals(List.of("1.20", "1.20.1"), result.localGameVersions());
        assertEquals(RemoteAddon.Source.MODRINTH, result.source());
        assertNull(result.targetVersion());
    }
}
