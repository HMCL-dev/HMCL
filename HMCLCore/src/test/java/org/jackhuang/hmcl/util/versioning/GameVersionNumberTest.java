package org.jackhuang.hmcl.util.versioning;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

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
            assertGameVersionEquals(versions[i]);

            for (int j = i + 1; j < versions.length; j++) {
                assertLessThan(versions[i], versions[j]);
            }
        }
    }

    @Test
    public void testCompareRelease() {
        assertGameVersionEquals("1.100");
        assertGameVersionEquals("1.100.1");
        assertGameVersionEquals("1.100.1-pre1");
        assertGameVersionEquals("1.100.1-pre1", "1.100.1 Pre-Release 1");

        assertOrder(
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
                "1.13.2",
                "19w13b",
                "3D Shareware v1.34",
                "19w14a",
                "1.14 Pre-Release 1",
                "1.14"
        );

        assertOrder(
                "1.15.2",
                "20w06a",
                "20w14infinite",
                "20w22a",
                "1.16-pre1",
                "1.16"
        );

        assertOrder(
                "1.18.2",
                "22w13oneblockatatime",
                "22w11a",
                "1.19-pre1"
        );

        assertOrder(
                "1.19.4",
                "23w13a",
                "23w13a_or_b",
                "23w14a",
                "1.20"
        );

        assertOrder(
                "1.9.2",
                "1.RV-Pre1",
                "16w14a",
                "1.9.3-pre1"
        );

        assertOrder(
                "1.5.1",
                "2.0",
                "1.5.2");
    }
}
