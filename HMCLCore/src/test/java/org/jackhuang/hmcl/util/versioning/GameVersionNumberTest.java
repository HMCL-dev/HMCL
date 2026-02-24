/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2024 huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.util.versioning;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Supplier;

import static org.jackhuang.hmcl.util.versioning.GameVersionNumber.asGameVersion;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Glavo
 */
public final class GameVersionNumberTest {

    //region Helpers

    private static List<String> readVersions() {
        List<String> versions = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(GameVersionNumber.class.getResourceAsStream("/assets/game/versions.txt"), StandardCharsets.UTF_8))) {
            for (String line; (line = reader.readLine()) != null && !line.isEmpty(); ) {
                versions.add(line);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        return versions;
    }

    private static Supplier<String> errorMessage(GameVersionNumber version1, GameVersionNumber version2) {
        return () -> "version1=%s, version2=%s".formatted(version1.toDebugString(), version2.toDebugString());
    }

    private static void assertGameVersionEquals(String version) {
        assertGameVersionEquals(version, version);
    }

    private static void assertGameVersionEquals(String version1, String version2) {
        GameVersionNumber gameVersion1 = asGameVersion(version1);
        GameVersionNumber gameVersion2 = asGameVersion(version2);
        assertEquals(0, gameVersion1.compareTo(gameVersion2), errorMessage(gameVersion1, gameVersion2));
        assertEquals(0, gameVersion2.compareTo(gameVersion1), errorMessage(gameVersion1, gameVersion2));
        assertEquals(gameVersion1, gameVersion2, errorMessage(gameVersion1, gameVersion2));
        assertEquals(gameVersion2, gameVersion1, errorMessage(gameVersion1, gameVersion2));
        assertEquals(gameVersion1.hashCode(), gameVersion2.hashCode(), errorMessage(gameVersion1, gameVersion2));
    }

    private static void assertOrder(String... versions) {
        var gameVersionNumbers = new GameVersionNumber[versions.length];
        for (int i = 0; i < versions.length; i++) {
            gameVersionNumbers[i] = asGameVersion(versions[i]);
        }

        for (int i = 0; i < versions.length - 1; i++) {
            GameVersionNumber version1 = gameVersionNumbers[i];

            for (int j = 0; j < i; j++) {
                GameVersionNumber version2 = gameVersionNumbers[j];

                assertTrue(version1.compareTo(version2) > 0, errorMessage(version1, version2));
                assertTrue(version2.compareTo(version1) < 0, errorMessage(version1, version2));
                assertNotEquals(version1, version2, errorMessage(version1, version2));
                assertNotEquals(version2, version1, errorMessage(version1, version2));
            }

            assertGameVersionEquals(versions[i]);

            for (int j = i + 1; j < versions.length; j++) {
                GameVersionNumber version2 = gameVersionNumbers[j];

                assertTrue(version1.compareTo(version2) < 0, errorMessage(version1, version2));
                assertTrue(version2.compareTo(version1) > 0, errorMessage(version1, version2));
                assertNotEquals(version1, version2, errorMessage(version1, version2));
                assertNotEquals(version2, version1, errorMessage(version1, version2));
            }
        }

        assertGameVersionEquals(versions[versions.length - 1]);
    }

    private void assertOldVersion(String oldVersion, GameVersionNumber.Type type, String versionNumber) {
        GameVersionNumber version = asGameVersion(oldVersion);
        assertInstanceOf(GameVersionNumber.Old.class, version);
        GameVersionNumber.Old old = (GameVersionNumber.Old) version;
        assertSame(type, old.type);
        assertEquals(VersionNumber.asVersion(versionNumber), old.versionNumber);
    }

    //endregion Helpers

    private static boolean isAprilFools(String version) {
        return asGameVersion(version).isAprilFools();
    }

    @Test
    public void testIsAprilFools() {
        assertTrue(isAprilFools("15w14a"));
        assertTrue(isAprilFools("1.RV-Pre1"));
        assertTrue(isAprilFools("3D Shareware v1.34"));
        assertTrue(isAprilFools("2.0"));
        assertTrue(isAprilFools("20w14infinite"));
        assertTrue(isAprilFools("22w13oneBlockAtATime"));
        assertTrue(isAprilFools("23w13a_or_b"));
        assertTrue(isAprilFools("24w14potato"));
        assertTrue(isAprilFools("25w14craftmine"));

        assertFalse(isAprilFools("1.21.8"));
        assertFalse(isAprilFools("1.21.8-rc1"));
        assertFalse(isAprilFools("25w21a"));
        assertFalse(isAprilFools("13w12~"));
        assertFalse(isAprilFools("15w14b"));
        assertFalse(isAprilFools("25w45a_unobfuscated"));
    }

    @Test
    public void testSortVersions() {
        List<String> versions = readVersions();

        {
            List<String> copied = new ArrayList<>(versions);
            copied.sort(Comparator.comparing(GameVersionNumber::asGameVersion));
            assertIterableEquals(versions, copied);
        }

        {
            List<String> copied = new ArrayList<>(versions);
            Collections.reverse(copied);
            copied.sort(Comparator.comparing(GameVersionNumber::asGameVersion));
            assertIterableEquals(versions, copied);
        }

        for (int randomSeed = 0; randomSeed < 5; randomSeed++) {
            List<String> copied = new ArrayList<>(versions);
            Collections.shuffle(copied, new Random(randomSeed));
            copied.sort(Comparator.comparing(GameVersionNumber::asGameVersion));
            assertIterableEquals(versions, copied);
        }
    }

    @Test
    public void testParseOld() {
        assertOldVersion("rd-132211", GameVersionNumber.Type.PRE_CLASSIC, "132211");
        assertOldVersion("in-20100223", GameVersionNumber.Type.INDEV, "20100223");
        assertOldVersion("in-20100212-2", GameVersionNumber.Type.INDEV, "20100212-2");
        assertOldVersion("inf-20100618", GameVersionNumber.Type.INFDEV, "20100618");
        assertOldVersion("inf-20100330-1", GameVersionNumber.Type.INFDEV, "20100330-1");
        assertOldVersion("a1.0.6", GameVersionNumber.Type.ALPHA, "1.0.6");
        assertOldVersion("a1.0.8_01", GameVersionNumber.Type.ALPHA, "1.0.8_01");
        assertOldVersion("a1.0.13_01-1", GameVersionNumber.Type.ALPHA, "1.0.13_01-1");
        assertOldVersion("b1.0", GameVersionNumber.Type.BETA, "1.0");
        assertOldVersion("b1.0_01", GameVersionNumber.Type.BETA, "1.0_01");
        assertOldVersion("b1.6-tb3", GameVersionNumber.Type.BETA, "1.6-tb3");
        assertOldVersion("b1.8-pre1-2", GameVersionNumber.Type.BETA, "1.8-pre1-2");
        assertOldVersion("b1.9-pre1", GameVersionNumber.Type.BETA, "1.9-pre1");

        assertThrows(IllegalArgumentException.class, () -> GameVersionNumber.Old.parse(""));
        assertThrows(IllegalArgumentException.class, () -> GameVersionNumber.Old.parse("1.21"));
        assertThrows(IllegalArgumentException.class, () -> GameVersionNumber.Old.parse("r-132211"));
        assertThrows(IllegalArgumentException.class, () -> GameVersionNumber.Old.parse("rd-"));
        assertThrows(IllegalArgumentException.class, () -> GameVersionNumber.Old.parse("rd-a"));
        assertThrows(IllegalArgumentException.class, () -> GameVersionNumber.Old.parse("i-20100223"));
        assertThrows(IllegalArgumentException.class, () -> GameVersionNumber.Old.parse("in-"));
        assertThrows(IllegalArgumentException.class, () -> GameVersionNumber.Old.parse("in-a"));
        assertThrows(IllegalArgumentException.class, () -> GameVersionNumber.Old.parse("inf-"));
        assertThrows(IllegalArgumentException.class, () -> GameVersionNumber.Old.parse("inf-a"));
    }

    private static void testParseLegacySnapshot(int year, int week, char suffix) {
        String raw = "%02dw%02d%s".formatted(year, week, suffix);
        var rawVersion = (GameVersionNumber.LegacySnapshot) asGameVersion(raw);
        assertInstanceOf(GameVersionNumber.LegacySnapshot.class, rawVersion);
        assertEquals(raw, rawVersion.toString());
        assertEquals(raw, rawVersion.toNormalizedString());
        assertEquals(year, rawVersion.getYear());
        assertEquals(week, rawVersion.getWeek());
        assertEquals(suffix, rawVersion.getSuffix());
        assertFalse(rawVersion.isUnobfuscated());

        var unobfuscated = raw + "_unobfuscated";
        var unobfuscatedVersion = (GameVersionNumber.LegacySnapshot) asGameVersion(unobfuscated);
        assertInstanceOf(GameVersionNumber.LegacySnapshot.class, rawVersion);
        assertEquals(unobfuscated, unobfuscatedVersion.toString());
        assertEquals(unobfuscated, unobfuscatedVersion.toNormalizedString());
        assertEquals(year, unobfuscatedVersion.getYear());
        assertEquals(week, unobfuscatedVersion.getWeek());
        assertEquals(suffix, unobfuscatedVersion.getSuffix());
        assertTrue(unobfuscatedVersion.isUnobfuscated());

        var unobfuscated2 = raw + " Unobfuscated";
        var unobfuscatedVersion2 = (GameVersionNumber.LegacySnapshot) asGameVersion(unobfuscated2);
        assertInstanceOf(GameVersionNumber.LegacySnapshot.class, rawVersion);
        assertEquals(unobfuscated2, unobfuscatedVersion2.toString());
        assertEquals(unobfuscated, unobfuscatedVersion2.toNormalizedString());
        assertEquals(year, unobfuscatedVersion2.getYear());
        assertEquals(week, unobfuscatedVersion2.getWeek());
        assertEquals(suffix, unobfuscatedVersion2.getSuffix());
        assertTrue(unobfuscatedVersion2.isUnobfuscated());
    }

    @Test
    public void testParseNew() {
        List<String> versions = readVersions();
        for (String version : versions) {
            GameVersionNumber gameVersion = asGameVersion(version);
            assertFalse(gameVersion instanceof GameVersionNumber.Old, "version=" + gameVersion.toDebugString());
        }

        testParseLegacySnapshot(25, 46, 'a');

        assertThrows(IllegalArgumentException.class, () -> GameVersionNumber.Release.parse("2.1"));
        assertThrows(IllegalArgumentException.class, () -> GameVersionNumber.LegacySnapshot.parse("1.0"));
        assertThrows(IllegalArgumentException.class, () -> GameVersionNumber.LegacySnapshot.parse("1.100.1"));
        assertThrows(IllegalArgumentException.class, () -> GameVersionNumber.LegacySnapshot.parse("aawbba"));
        assertThrows(IllegalArgumentException.class, () -> GameVersionNumber.LegacySnapshot.parse("13w12A"));
        assertThrows(IllegalArgumentException.class, () -> GameVersionNumber.LegacySnapshot.parse("13w12~"));
    }

    private static void assertSimpleReleaseVersion(String simpleReleaseVersion, int major, int minor, int patch) {
        GameVersionNumber.Release release = GameVersionNumber.Release.parseSimple(simpleReleaseVersion);
        assertAll("Assert Simple Release Version " + simpleReleaseVersion,
                () -> assertEquals(major, release.getMajor()),
                () -> assertEquals(minor, release.getMinor()),
                () -> assertEquals(patch, release.getPatch()),
                () -> assertEquals(GameVersionNumber.Release.ReleaseType.UNKNOWN, release.getEaType()),
                () -> assertEquals(VersionNumber.ZERO, release.getEaVersion())
        );
    }

    @Test
    public void testParseSimpleRelease() {
        assertSimpleReleaseVersion("1.0", 1, 0, 0);
        assertSimpleReleaseVersion("1.13", 1, 13, 0);
        assertSimpleReleaseVersion("1.21.8", 1, 21, 8);
        assertSimpleReleaseVersion("26.1", 26, 1, 0);
        assertSimpleReleaseVersion("26.1.1", 26, 1, 1);

        assertThrows(IllegalArgumentException.class, () -> GameVersionNumber.Release.parseSimple("26"));
        assertThrows(IllegalArgumentException.class, () -> GameVersionNumber.Release.parseSimple("24.0.0"));
        assertThrows(IllegalArgumentException.class, () -> GameVersionNumber.Release.parseSimple("24.0"));
        assertThrows(IllegalArgumentException.class, () -> GameVersionNumber.Release.parseSimple("2.0"));
        assertThrows(IllegalArgumentException.class, () -> GameVersionNumber.Release.parseSimple("1"));
        assertThrows(IllegalArgumentException.class, () -> GameVersionNumber.Release.parseSimple("1..0"));
        assertThrows(IllegalArgumentException.class, () -> GameVersionNumber.Release.parseSimple("1.0."));
        assertThrows(IllegalArgumentException.class, () -> GameVersionNumber.Release.parseSimple("1.a"));
        assertThrows(IllegalArgumentException.class, () -> GameVersionNumber.Release.parseSimple("1.1a"));
        assertThrows(IllegalArgumentException.class, () -> GameVersionNumber.Release.parseSimple("1.0a"));
        assertThrows(IllegalArgumentException.class, () -> GameVersionNumber.Release.parseSimple("1.0.0.0"));
    }

    @Test
    public void testCompareRelease() {
        assertGameVersionEquals("0.0");
        assertGameVersionEquals("1.100");
        assertGameVersionEquals("1.100.1");
        assertGameVersionEquals("1.100.1-pre1");
        assertGameVersionEquals("1.100.1-pre1", "1.100.1 Pre-Release 1");

        assertOrder(
                "0.0",
                "1.0",
                "1.99",
                "1.99.1-pre1",
                "1.99.1 Pre-Release 2",
                "1.99.1-rc1",
                "1.99.1",
                "1.100",
                "1.100.1",
                "26.1"
        );
    }

    @Test
    public void testCompareSnapshot() {
        assertOrder(
                "90w01a",
                "90w01b",
                "90w01e",
                "90w02a"
        );
    }

    @Test
    public void testCompareMix() {
        assertOrder(
                "rd-132211",
                "rd-161348",
                "rd-20090515",
                "c0.0.11a",
                "c0.0.13a",
                "c0.0.13a_03",
                "c0.30_01c",
                "inf-20100330-1",
                "inf-20100330-2",
                "inf-20100618",
                "a1.0.4",
                "a1.0.8_01",
                "a1.0.10",
                "a1.0.13_01-1",
                "a1.0.17_02",
                "a1.0.17_04",
                "a1.1.0",
                "a1.1.1",
                "b1.0",
                "b1.0_01",
                "b1.1_02",
                "b1.2",
                "b1.8-pre1-2",
                "b1.8.1",
                "0.0",
                "1.0.0-rc1",
                "1.0.0-rc2-1",
                "1.0.0-rc2-2",
                "1.0.0-rc2-3",
                "1.0",
                "11w47a",
                "1.1",
                "1.5.1",
                "2.0",
                "1.5.2",
                "1.9.2",
                "1.RV-Pre1",
                "16w14a",
                "1.9.3-pre1",
                "1.13.2",
                "19w13b",
                "3D Shareware v1.34",
                "19w14a",
                "1.14 Pre-Release 1",
                "1.14",
                "1.15.2",
                "20w06a",
                "20w13b",
                "20w14infinite",
                "20w22a",
                "1.16-pre1",
                "1.16",
                "1.18.2",
                "22w13oneblockatatime",
                "22w11a",
                "1.19-pre1",
                "1.19.4",
                "23w13a",
                "23w13a_or_b",
                "23w14a",
                "1.20",
                "24w13a",
                "24w14potato",
                "24w14a",
                "25w46a",
                "25w46a_unobfuscated",
                "1.21.11-pre1",
                "1.21.11-pre1_unobfuscated",
                "1.21.11-pre2",
                "1.21.11-pre2_unobfuscated",
                "99w99a",
                "26.1-snapshot-1",
                "26.1-snapshot-2",
                "26.1",
                "26.2-snapshot-1",
                "26.2-snapshot-2",
                "26.2",
                "100.0",
                "Unknown"
        );
    }

    @Test
    public void testCompareUnknown() {
        assertOrder(
                "23w35a",
                "1.20.2-pre1",
                "1.20.2-rc1",
                "1.20.2",
                "23w35b", // fictional version number
                "23w40a"
        );

        assertOrder(
                "1.20.4",
                "24w04a",
                "1.100"   // fictional version number
        );

        assertOrder(
                "1.19.4",
                "23w18a", // fictional version number
                "1.19.5",
                "1.20"
        );

        assertOrder(
                "1.0",
                "10w47a", // fictional version number
                "11w47a",
                "1.1"
        );
    }

    private static void assertNormalized(String normalized, String version) {
        assertGameVersionEquals(version);
        assertGameVersionEquals(normalized, version);
        assertEquals(normalized, asGameVersion(version).toNormalizedString());
    }

    @Test
    public void testToNormalizedString() {
        for (String version : readVersions()) {
            assertNormalized(version, version);
        }

        assertNormalized("26.1-snapshot-1", "26.1 Snapshot 1");
        assertNormalized("1.21.11-pre3", "1.21.11 Pre-Release 3");
        assertNormalized("1.21.11-pre3_unobfuscated", "1.21.11 Pre-Release 3 Unobfuscated");
        assertNormalized("1.21.11-pre3_unobfuscated", "1.21.11-pre3 Unobfuscated");
        assertNormalized("1.21.11-rc1", "1.21.11 Release Candidate 1");
        assertNormalized("1.21.11-rc1_unobfuscated", "1.21.11 Release Candidate 1 Unobfuscated");
        assertNormalized("1.14_combat-212796", "1.14.3 - Combat Test");
        assertNormalized("1.14_combat-0", "Combat Test 2");
        assertNormalized("1.14_combat-3", "Combat Test 3");
        assertNormalized("1.15_combat-1", "Combat Test 4");
        assertNormalized("1.15_combat-6", "Combat Test 5");
        assertNormalized("1.16_combat-0", "Combat Test 6");
        assertNormalized("1.16_combat-1", "Combat Test 7");
        assertNormalized("1.16_combat-2", "Combat Test 7b");
        assertNormalized("1.16_combat-3", "Combat Test 7c");
        assertNormalized("1.16_combat-4", "Combat Test 8");
        assertNormalized("1.16_combat-5", "Combat Test 8b");
        assertNormalized("1.16_combat-6", "Combat Test 8c");
        assertNormalized("1.16.2-pre1", "1.16.2 Pre-release 1"); // https://github.com/HMCL-dev/HMCL/pull/5476
        assertNormalized("1.18_experimental-snapshot-1", "1.18 Experimental Snapshot 1");
        assertNormalized("1.18_experimental-snapshot-2", "1.18 experimental snapshot 2");
        assertNormalized("1.18_experimental-snapshot-3", "1.18 experimental snapshot 3");
        assertNormalized("1.18_experimental-snapshot-4", "1.18 experimental snapshot 4");
        assertNormalized("1.18_experimental-snapshot-5", "1.18 experimental snapshot 5");
        assertNormalized("1.18_experimental-snapshot-6", "1.18 experimental snapshot 6");
        assertNormalized("1.18_experimental-snapshot-7", "1.18 experimental snapshot 7");
        assertNormalized("1.19_deep_dark_experimental_snapshot-1", "Deep Dark Experimental Snapshot 1");
        assertNormalized("20w14infinite", "20w14~");
        assertNormalized("22w13oneBlockAtATime", "22w13oneblockatatime");
    }

    @Test
    public void isAtLeast() {
        assertTrue(asGameVersion("1.13").isAtLeast("1.13", "17w43a", true));
        assertTrue(asGameVersion("1.13").isAtLeast("1.13", "17w43a", false));
        assertTrue(asGameVersion("1.13.1").isAtLeast("1.13", "17w43a", true));
        assertTrue(asGameVersion("1.13.1").isAtLeast("1.13", "17w43a", false));
        assertTrue(asGameVersion("1.14").isAtLeast("1.13", "17w43a", true));
        assertTrue(asGameVersion("1.14").isAtLeast("1.13", "17w43a", false));
        assertTrue(asGameVersion("1.13-rc1").isAtLeast("1.13", "17w43a", false));
        assertTrue(asGameVersion("1.13-pre1").isAtLeast("1.13", "17w43a", false));
        assertTrue(asGameVersion("17w43a").isAtLeast("1.13", "17w43a", true));
        assertTrue(asGameVersion("17w43a").isAtLeast("1.13", "17w43a", false));
        assertTrue(asGameVersion("17w43b").isAtLeast("1.13", "17w43a", true));
        assertTrue(asGameVersion("17w43b").isAtLeast("1.13", "17w43a", false));
        assertTrue(asGameVersion("17w45a").isAtLeast("1.13", "17w43a", true));
        assertTrue(asGameVersion("17w45a").isAtLeast("1.13", "17w43a", false));


        assertFalse(asGameVersion("1.13-rc1").isAtLeast("1.13", "17w43a", true));
        assertFalse(asGameVersion("1.13-pre1").isAtLeast("1.13", "17w43a", true));
        assertFalse(asGameVersion("17w31a").isAtLeast("1.13", "17w43a", true));
        assertFalse(asGameVersion("17w31a").isAtLeast("1.13", "17w43a", false));
        assertFalse(asGameVersion("1.12").isAtLeast("1.13", "17w43a", true));
        assertFalse(asGameVersion("1.12").isAtLeast("1.13", "17w43a", false));
        assertFalse(asGameVersion("1.12.2").isAtLeast("1.13", "17w43a", true));
        assertFalse(asGameVersion("1.12.2").isAtLeast("1.13", "17w43a", false));
        assertFalse(asGameVersion("1.12.2-pre1").isAtLeast("1.13", "17w43a", true));
        assertFalse(asGameVersion("1.12.2-pre1").isAtLeast("1.13", "17w43a", false));
        assertFalse(asGameVersion("rd-132211").isAtLeast("1.13", "17w43a", true));
        assertFalse(asGameVersion("rd-132211").isAtLeast("1.13", "17w43a", false));
        assertFalse(asGameVersion("a1.0.6").isAtLeast("1.13", "17w43a", true));
        assertFalse(asGameVersion("a1.0.6").isAtLeast("1.13", "17w43a", false));

        assertThrows(IllegalArgumentException.class, () -> asGameVersion("1.13").isAtLeast("17w43a", "17w43a", true));
        assertThrows(IllegalArgumentException.class, () -> asGameVersion("1.13").isAtLeast("17w43a", "17w43a", false));
        assertThrows(IllegalArgumentException.class, () -> asGameVersion("17w43a").isAtLeast("1.13", "1.13", true));
        assertThrows(IllegalArgumentException.class, () -> asGameVersion("17w43a").isAtLeast("1.13", "1.13", false));
        assertThrows(IllegalArgumentException.class, () -> asGameVersion("17w43a").isAtLeast("1.13", "22w13oneblockatatime", true));
        assertThrows(IllegalArgumentException.class, () -> asGameVersion("17w43a").isAtLeast("1.13", "22w13oneblockatatime", false));
    }
}
