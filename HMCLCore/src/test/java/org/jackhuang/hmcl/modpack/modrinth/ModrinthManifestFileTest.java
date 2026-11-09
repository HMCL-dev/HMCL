// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.modpack.modrinth;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/// Exercises file-update matching with synthetic examples of modpack naming conventions and URL edge cases.
@NotNullByDefault
public final class ModrinthManifestFileTest {

    /// A valid synthetic hash distinct from NEW_HASH prevents accidental hash matches.
    private static final String OLD_HASH = "0".repeat(128);

    /// The second synthetic file's hash.
    private static final String NEW_HASH = "1".repeat(128);

    /// A synthetic Modrinth download with a multi-part artifact name.
    private static final String MODRINTH_URL = "https://cdn.modrinth.com/data/ProjectA/versions/Old123/example-api-1.0.jar";

    /// Synthetic names cover multi-part names, loader/game qualifiers, and encoded plus signs or spaces.
    private static Stream<Arguments> modrinthArtifactNames() {
        return Stream.of(
                Arguments.of("example-api-1.0+1.21.jar", "example-api-2.0+1.21.1.jar"),
                Arguments.of("renderer-extra-fabric-0.8.1+mc1.21.jar", "renderer-extra-fabric-0.9.2+mc1.21.1.jar"),
                Arguments.of("frame-rate-3.1.0+minecraft-1.21-fabric.jar", "frame-rate-3.2.0+minecraft-1.21.1-fabric.jar"),
                Arguments.of("example helper-1.0.jar", "example helper-2.0.jar"));
    }

    /// Typical naming conventions match across updates even when paths and URL escaping differ.
    @ParameterizedTest
    @MethodSource("modrinthArtifactNames")
    void modrinthNamingConventionsMatchAcrossVersions(String oldName, String newName) {
        String oldUrl = "https://cdn.modrinth.com/data/ProjectA/versions/Old123/"
                + oldName.replace("+", "%2B").replace(" ", "%20");
        String newUrl = "https://cdn.modrinth.com/data/ProjectA/versions/New456/"
                + newName.replace("+", "%2B").replace(" ", "%20");
        ModrinthManifest.File a = file("mods/" + oldName, OLD_HASH, List.of(oldUrl));
        ModrinthManifest.File b = file("mods/" + newName, NEW_HASH, List.of(newUrl));
        assertTrue(a.looksLike(b));
        assertTrue(b.looksLike(a));
    }

    /// Produces files whose distinct hashes and paths isolate download URL matching.
    private static ModrinthManifest.File file(String path, String hash, @Unmodifiable List<String> downloads) {
        return new ModrinthManifest.File(path, Map.of("sha512", hash), null, downloads, 1);
    }

    /// Asserts the intended match in both argument orders without hash, path, or exact-URL shortcuts.
    private static void assertUrlMatch(boolean expected, String left, String right) {
        ModrinthManifest.File a = file("mods/old.jar", OLD_HASH, List.of(left));
        ModrinthManifest.File b = file("mods/new.jar", NEW_HASH, List.of(right));
        a.validate();
        b.validate();
        assertAll(
                () -> assertEquals(expected, a.looksLike(b), left + " -> " + right),
                () -> assertEquals(expected, b.looksLike(a), right + " -> " + left));
    }

    /// Identity, equal hashes, shared URLs, and equal paths independently preserve existing shortcuts.
    @Test
    void directIdentityShortcuts() {
        ModrinthManifest.File original = file("mods/original.jar", OLD_HASH, List.of("https://example.org/a"));
        assertTrue(original.looksLike(original));
        assertTrue(original.looksLike(file("mods/renamed.jar", OLD_HASH, List.of("https://example.org/b"))));
        assertTrue(original.looksLike(file("mods/renamed.jar", NEW_HASH, List.of("https://example.org/a"))));
        assertTrue(original.looksLike(file("mods/original.jar", NEW_HASH, List.of("https://example.org/b"))));
        assertFalse(original.looksLike(file("mods/other.jar", NEW_HASH, List.of("https://example.org/b"))));
    }

    /// All seven supported URL shapes must recognize ordinary version changes, including encoded plus signs.
    private static Stream<Arguments> supportedUrls() {
        return Stream.of(
                Arguments.of(MODRINTH_URL,
                        "https://cdn.modrinth.com/data/ProjectA/versions/New456/example-api-2.0%2B1.21.jar"),
                Arguments.of("https://github.com/owner/repo/releases/download/v1/mod-1.jar",
                        "https://github.com/owner/repo/releases/download/v2/mod-2.jar"),
                Arguments.of("https://github.com/owner/repo/releases/latest/download/mod-1.jar",
                        "https://github.com/owner/repo/releases/latest/download/mod-2.jar"),
                Arguments.of("https://raw.githubusercontent.com/owner/repo/main/nested/mods/mod-1.jar",
                        "https://raw.githubusercontent.com/owner/repo/v2/nested/mods/mod-2.jar"),
                Arguments.of("https://gitlab.com/owner/repo/-/releases/v1/downloads/mod-1.jar",
                        "https://gitlab.com/owner/repo/-/releases/v2/downloads/mod-2.jar"),
                Arguments.of("https://gitlab.com/owner/repo/-/releases/permalink/latest/downloads/mod-1.jar",
                        "https://gitlab.com/owner/repo/-/releases/permalink/latest/downloads/mod-2.jar"),
                Arguments.of("https://gitlab.com/owner/repo/-/raw/main/nested/mods/mod-1.jar",
                        "https://gitlab.com/owner/repo/-/raw/v2/nested/mods/mod-2.jar"));
    }

    /// Each supported URL shape ignores only the version-bearing components.
    @ParameterizedTest
    @MethodSource("supportedUrls")
    void supportedVersionChanges(String left, String right) {
        assertUrlMatch(true, left, right);
    }

    /// Different owners, repositories, directories, artifact names, or projects must not collapse.
    private static Stream<Arguments> distinctUrls() {
        return Stream.of(
                Arguments.of(MODRINTH_URL,
                        "https://cdn.modrinth.com/data/ProjectB/versions/New456/example-2.jar"),
                Arguments.of(MODRINTH_URL,
                        "https://cdn.modrinth.com/data/ProjectA/versions/New456/other-2.jar"),
                Arguments.of(MODRINTH_URL,
                        "https://cdn.modrinth.com/data/ProjectB/versions/New456/example-2.jar"),
                Arguments.of("https://github.com/owner/repo/releases/download/v1/mod-1.jar",
                        "https://github.com/other/repo/releases/download/v2/mod-2.jar"),
                Arguments.of("https://github.com/owner/repo/releases/download/v1/mod-1.jar",
                        "https://github.com/owner/other/releases/download/v2/mod-2.jar"),
                Arguments.of("https://raw.githubusercontent.com/owner/repo/main/a/mod-1.jar",
                        "https://raw.githubusercontent.com/owner/repo/v2/b/mod-2.jar"),
                Arguments.of("https://gitlab.com/owner/repo/-/raw/main/a/mod-1.jar",
                        "https://gitlab.com/owner/repo/-/raw/v2/b/mod-2.jar"),
                Arguments.of("https://gitlab.com/owner/repo/-/releases/v1/downloads/mod-1.jar",
                        "https://gitlab.com/owner/other/-/releases/v2/downloads/mod-2.jar"));
    }

    /// URL identity components must distinguish otherwise similar download names.
    @ParameterizedTest
    @MethodSource("distinctUrls")
    void differentProjectsAndArtifacts(String left, String right) {
        assertUrlMatch(false, left, right);
    }

    /// Query strings may contain redirects, escaped separators, Unicode, or a fragment.
    @ParameterizedTest
    @ValueSource(strings = {"?download=1", "?token=a%2Fb%3Fc", "?next=https://example.org/a-b.jar",
            "?name=%E6%A8%A1%E7%BB%84", "?download=1#fragment", "?"})
    void queryStringsDoNotChangeProject(String suffix) {
        assertUrlMatch(true, MODRINTH_URL, MODRINTH_URL.replace("Old123", "New456") + suffix);
    }

    /// Queries and fragments are independently optional and must not supply a misleading extension.
    @ParameterizedTest
    @MethodSource("supportedUrls")
    void optionalQueriesAndFragmentsDoNotChangeProject(String left, String right) {
        assertUrlMatch(true, left, right);
        assertUrlMatch(true, left, right + "?download=1");
        assertUrlMatch(true, left, right + "#download");
        assertUrlMatch(true, left, right + "?download=1#download");
        assertUrlMatch(true, left, right + "?");
        assertUrlMatch(true, left, right + "?#");
        assertUrlMatch(true, left + "#old.zip", right + "#new.tar.gz");
        assertUrlMatch(true, left + "#", right + "#download?name=other.zip");
        assertUrlMatch(true, left + "?name=old.zip#old", right + "?name=new.tar.gz#new");
    }

    /// Tagged and latest release URLs still identify artifacts in the same hosting project.
    private static Stream<Arguments> taggedAndLatestUrls() {
        return Stream.of(
                Arguments.of("https://github.com/owner/repo/releases/download/v1/mod-1.jar",
                        "https://github.com/owner/repo/releases/latest/download/mod-2.jar"),
                Arguments.of("https://gitlab.com/owner/repo/-/releases/v1/downloads/mod-1.jar",
                        "https://gitlab.com/owner/repo/-/releases/permalink/latest/downloads/mod-2.jar"));
    }

    /// Tagged and latest release patterns intentionally remain distinct descriptions.
    @ParameterizedTest
    @MethodSource("taggedAndLatestUrls")
    void taggedAndLatestReleaseUrlsRemainDistinct(String left, String right) {
        assertUrlMatch(false, left, right);
    }

    /// Unsupported schemes, hosts, malformed paths, and hostile-looking authorities must not match.
    @ParameterizedTest
    @ValueSource(strings = {
            "", "not a URL", "https://", "file:///tmp/example-api-2.jar",
            "http://cdn.modrinth.com/data/ProjectA/versions/New456/example-api-2.jar",
            "//cdn.modrinth.com/data/ProjectA/versions/New456/example-api-2.jar",
            "https://cdn.modrinth.com.evil.example/data/ProjectA/versions/New456/example-api-2.jar",
            "https://cdn.modrinth.com@evil.example/data/ProjectA/versions/New456/example-api-2.jar",
            "https://evil.example/?url=https://cdn.modrinth.com/data/ProjectA/versions/New456/example-api-2.jar",
            "https://cdn.modrinth.com/data/ProjectB/versions/New456/example-api-2.jar?project=ProjectA",
            "https://cdn.modrinth.com/data/ProjectA/versions//example-api-2.jar",
            " https://cdn.modrinth.com/data/ProjectA/versions/New456/example-api-2.jar"
    })
    void strangeUrlsDoNotProduceSpuriousMatches(String url) {
        assertUrlMatch(false, MODRINTH_URL, url);
    }

    /// Unrecognized URL descriptions must not make unrelated files match through null descriptions.
    @Test
    void unsupportedUrlsDoNotMatchEachOther() {
        assertUrlMatch(false, "https://example.org/mod-1.jar", "https://example.org/mod-2.jar");
        assertUrlMatch(false, "", "not a URL");
        assertFalse(file("mods/a.jar", OLD_HASH, List.of()).looksLike(file("mods/b.jar", NEW_HASH, List.of())));
    }

    /// A matching URL may appear after unsupported mirrors on either side.
    @Test
    void everyDownloadMirrorIsConsidered() {
        ModrinthManifest.File a = file("mods/a.jar", OLD_HASH,
                List.of("https://example.org/old.jar", MODRINTH_URL));
        ModrinthManifest.File b = file("mods/b.jar", NEW_HASH,
                List.of("https://example.net/new.jar", MODRINTH_URL.replace("Old123", "New456")));
        assertTrue(a.looksLike(b));
        assertTrue(b.looksLike(a));
    }

    /// Only the first name segment is intentionally used for hyphenated artifact names.
    @Test
    void hyphenatedArtifactNamesUseTheFirstSegment() {
        assertUrlMatch(true, "https://github.com/owner/repo/releases/download/v1/mod-api-1.jar",
                "https://github.com/owner/repo/releases/download/v2/mod-core-2.jar");
    }

    /// The final extension distinguishes artifacts across every supported URL pattern.
    @ParameterizedTest
    @MethodSource("supportedUrls")
    void differentArtifactExtensionsDoNotMatch(String left, String right) {
        assertUrlMatch(false, left, right.replace(".jar", ".zip"));
        assertUrlMatch(false, left, right.replace(".jar", ".jar.zip") + "?filename=mod.jar#mod.jar");
        assertUrlMatch(true, left.replace(".jar", ".tar.gz"), right.replace(".jar", ".tar.gz"));
        assertUrlMatch(false, left.replace(".jar", ".tar.gz"), right.replace(".jar", ".tar.xz"));
    }
}
