/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
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

import java.util.*;
import java.util.function.Supplier;

import static org.jackhuang.hmcl.util.versioning.VersionNumber.isIntVersionNumber;
import static org.jackhuang.hmcl.util.versioning.VersionNumber.normalize;
import static org.junit.jupiter.api.Assertions.*;

public class VersionNumberTest {

    @Test
    public void testCanonical() {
        assertEquals("3.2", normalize("3.2.0.0"));
        assertEquals("3.2-5", normalize("3.2.0.0-5"));
        assertEquals("3.2", normalize("3.2.0.0-0"));
        assertEquals("3.2", normalize("3.2--------"));
        assertEquals("3.2", normalize("3.0002"));
        assertEquals("1.7.2$%%^@&snapshot-3.1.1", normalize("1.7.2$%%^@&snapshot-3.1.1"));
        assertEquals("1.99999999999999999999", normalize("1.99999999999999999999"));
        assertEquals("1.99999999999999999999", normalize("1.0099999999999999999999"));
        assertEquals("1.99999999999999999999", normalize("1.99999999999999999999.0"));
        assertEquals("1.99999999999999999999", normalize("1.99999999999999999999--------"));
    }

    @Test
    public void testIsIntVersion() {
        assertFalse(isIntVersionNumber(""));
        assertFalse(isIntVersionNumber(" "));
        assertFalse(isIntVersionNumber("."));
        assertFalse(isIntVersionNumber("1."));
        assertFalse(isIntVersionNumber(".1"));
        assertFalse(isIntVersionNumber(".1."));
        assertFalse(isIntVersionNumber("1..8"));
        assertFalse(isIntVersionNumber("1.8."));
        assertFalse(isIntVersionNumber(".1.8"));
        assertFalse(isIntVersionNumber("1.7.10forge1614_FTBInfinity"));
        assertFalse(isIntVersionNumber("3.2-5"));
        assertFalse(isIntVersionNumber("1.9999999999"));

        assertTrue(isIntVersionNumber("0"));
        assertTrue(isIntVersionNumber("1"));
        assertTrue(isIntVersionNumber("0.1"));
        assertTrue(isIntVersionNumber("0.1.0"));
        assertTrue(isIntVersionNumber("1.8"));
        assertTrue(isIntVersionNumber("1.12.2"));
        assertTrue(isIntVersionNumber("1.13.1"));
        assertTrue(isIntVersionNumber("1.999999999"));
        assertTrue(isIntVersionNumber("999999999.0"));
    }

    private static void assertLessThan(String s1, String s2) {
        Supplier<String> messageSupplier = () -> String.format("%s should be less than %s", s1, s2);

        VersionNumber v1 = VersionNumber.asVersion(s1);
        VersionNumber v2 = VersionNumber.asVersion(s2);

        assertTrue(v1.compareTo(v2) < 0, messageSupplier);
        assertTrue(v2.compareTo(v1) > 0, messageSupplier);
    }

    @Test
    public void testComparator() {
        assertLessThan("1.7.10forge1614_FTBInfinity", "1.12.2");
        assertLessThan("1.8.0_51", "1.8.0.51");
        assertLessThan("1.8.0_77", "1.8.0_151");
        assertLessThan("1.6.0_22", "1.8.0_11");
        assertLessThan("1.7.0_22", "1.7.99");
        assertLessThan("1.12.2-14.23.4.2739", "1.12.2-14.23.5.2760");
        assertLessThan("1.9", "1.99999999999999999999");
        assertLessThan("1.99999999999999999999", "1.199999999999999999999");
        assertLessThan("1.99999999999999999999", "2");
        assertLessThan("1.99999999999999999999", "2.0");
    }

    @Test
    public void testSorting() {
        final Comparator<String> comparator = VersionNumber.VERSION_COMPARATOR.thenComparing(String::compareTo);
        final List<String> input = Collections.unmodifiableList(Arrays.asList(
                "0",
                "0.10.0",
                "1.6.4",
                "1.6.4-Forge9.11.1.1345",
                "1.7.10",
                "1.7.10Agrarian_Skies_2",
                "1.7.10-F1614-L",
                "1.7.10-FL1614_04",
                "1.7.10-Forge10.13.4.1614-1.7.10",
                "1.7.10-Forge1614",
                "1.7.10Forge1614_FTBInfinity-2.6.0",
                "1.7.10Forge1614_FTBInfinity-3.0.1",
                "1.7.10-Forge1614.1",
                "1.7.10forge1614_ATlauncher",
                "1.7.10forge1614_FTBInfinity",
                "1.7.10forge1614_FTBInfinity_server",
                "1.7.10forge1614test",
                "1.7.10-1614",
                "1.7.10-1614-test",
                "1.8",
                "1.8-forge1577",
                "1.8.9",
                "1.8.9-forge1902",
                "1.9",
                "1.10",
                "1.10.2",
                "1.10.2-AOE",
                "1.10.2-AOE-1.1.5",
                "1.10.2-All the Mods",
                "1.10.2-FTB_Beyond",
                "1.10.2-LiteLoader1.10.2",
                "1.10.2-forge2511-AOE-1.1.2",
                "1.10.2-forge2511-ATM-E",
                "1.10.2-forge2511-Age_of_Progression",
                "1.10.2-forge2511_Farming_Valley",
                "1.10.2-forge2511_bxztest",
                "1.10.2-forge2511-simple_life_2",
                "1.10.2-forge2511中文",
                "1.12.2",
                "1.12.2_Modern_Skyblock-3.4.2",
                "1.13.1",
                "1.99999999999999999999",
                "2",
                "2.0",
                "2.1"));

        List<String> output = new ArrayList<>(input);
        output.sort(comparator);
        assertIterableEquals(input, output);

        Collections.shuffle(output, new Random(0));
        output.sort(comparator);
        assertIterableEquals(input, output);
    }
}
