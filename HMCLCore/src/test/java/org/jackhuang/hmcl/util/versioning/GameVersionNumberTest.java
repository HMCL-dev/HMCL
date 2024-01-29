package org.jackhuang.hmcl.util.versioning;

import org.junit.jupiter.api.Disabled;
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
    @Disabled("fixme")
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
            assertLessThan(versions[i], versions[i + 1]);
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
    public void testCompareReleaseSnapshot() {

    }
}
