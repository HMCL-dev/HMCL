package org.jackhuang.hmcl.util.versioning;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Glavo
 */
public class GameVersionNumberTest {

    @Test
    public void testSortVersions() throws IOException {
        List<String> versions = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(GameVersionNumber.class.getResourceAsStream("/assets/game/versions.txt"), StandardCharsets.UTF_8))) {
            for (String line; (line = reader.readLine()) != null && !line.isEmpty(); ) {
                versions.add(line);
            }
        }

        List<String> copied = new ArrayList<>(versions);
        Collections.shuffle(copied, new Random(0));
        copied.sort(Comparator.comparing(GameVersionNumber::asGameVersion));

        assertIterableEquals(versions, copied);
    }

    private static String errorMessage(String version1, String version2) {
        return String.format("version1=%s, version2=%s", version1, version2);
    }

    private static void assertGameVersionEquals(String version) {
        assertGameVersionEquals(version, version);
    }

    private static void assertGameVersionEquals(String version1, String version2) {
        assertEquals(0, GameVersionNumber.asGameVersion(version1).compareTo(version2), errorMessage(version1, version2));
    }

    private static void assertLessThan(String version1, String version2) {
        assertTrue(GameVersionNumber.asGameVersion(version1).compareTo(version2) < 0, errorMessage(version1, version2));
    }

    private static void assertOrder(String... versions) {
        for (int i = 0; i < versions.length - 1; i++) {
            GameVersionNumber version1 = GameVersionNumber.asGameVersion(versions[i]);

            //noinspection EqualsWithItself
            assertEquals(0, version1.compareTo(version1), "version=" + versions[i]);

            for (int j = i + 1; j < versions.length; j++) {
                GameVersionNumber version2 = GameVersionNumber.asGameVersion(versions[j]);

                assertEquals(-1, version1.compareTo(version2), String.format("version1=%s, version2=%s", versions[i], versions[j]));
                assertEquals(1, version2.compareTo(version1), String.format("version1=%s, version2=%s", versions[i], versions[j]));
            }
        }

        assertGameVersionEquals(versions[versions.length - 1]);
    }

    @Test
    public void testParseOld() {
        {
            GameVersionNumber version = GameVersionNumber.asGameVersion("b1.0");
            assertInstanceOf(GameVersionNumber.Old.class, version);
            GameVersionNumber.Old old = (GameVersionNumber.Old) version;
            assertEquals(GameVersionNumber.Type.BETA, old.type);
            assertEquals(1, old.major);
            assertEquals(0, old.minor);
            assertEquals(0, old.patch);
            assertEquals(0, old.additional);
        }

        {
            GameVersionNumber version = GameVersionNumber.asGameVersion("b1.0_01");
            assertInstanceOf(GameVersionNumber.Old.class, version);
            GameVersionNumber.Old old = (GameVersionNumber.Old) version;
            assertEquals(GameVersionNumber.Type.BETA, old.type);
            assertEquals(1, old.major);
            assertEquals(0, old.minor);
            assertEquals(0, old.patch);
            assertEquals(1, old.additional);
        }
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
                "1.99.1-unknown1",
                "1.99.1-pre1",
                "1.99.1 Pre-Release 2",
                "1.99.1-rc1",
                "1.99.1",
                "1.100",
                "1.100.1"
        );
    }

    @Test
    public void testCompareSnapshot() {
        assertOrder(
                "90w01a",
                "90w01b",
                "90w01e",
                "90w01~",
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
                "a1.0.17_02",
                "a1.0.17_04",
                "a1.1.0",
                "b1.0",
                "b1.0_01",
                "b1.1_02",
                "b1.2",
                "b1.8.1",
                "0.0",
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
                "Unknown",
                "100.0"
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
}
