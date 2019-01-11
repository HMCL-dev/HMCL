/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2019  huangyuhui <huanghongxun2008@126.com> and contributors
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

import org.jackhuang.hmcl.util.versioning.VersionNumber;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class VersionNumberTest {

    @Test
    public void testCanonical() {
        VersionNumber u, v;

        v = VersionNumber.asVersion("3.2.0.0");
        Assert.assertEquals("3.2", v.getCanonical());

        v = VersionNumber.asVersion("3.2.0.0-5");
        Assert.assertEquals("3.2-5", v.getCanonical());

        v = VersionNumber.asVersion("3.2.0.0-0");
        Assert.assertEquals("3.2", v.getCanonical());

        v = VersionNumber.asVersion("3.2--------");
        Assert.assertEquals("3.2", v.getCanonical());

        v = VersionNumber.asVersion("1.7.2$%%^@&snapshot-3.1.1");
        Assert.assertEquals("1.7.2$%%^@&snapshot-3.1.1", v.getCanonical());
    }

    @Test
    public void testComparator() {
        VersionNumber u, v;

        u = VersionNumber.asVersion("1.7.10forge1614_FTBInfinity");
        v = VersionNumber.asVersion("1.12.2");
        Assert.assertTrue(u.compareTo(v) < 0);

        u = VersionNumber.asVersion("1.8.0_51");
        v = VersionNumber.asVersion("1.8.0.51");
        Assert.assertTrue(u.compareTo(v) < 0);

        u = VersionNumber.asVersion("1.8.0_151");
        v = VersionNumber.asVersion("1.8.0_77");
        Assert.assertTrue(u.compareTo(v) > 0);

        u = VersionNumber.asVersion("1.6.0_22");
        v = VersionNumber.asVersion("1.8.0_11");
        Assert.assertTrue(u.compareTo(v) < 0);

        u = VersionNumber.asVersion("1.12.2-14.23.5.2760");
        v = VersionNumber.asVersion("1.12.2-14.23.4.2739");
        Assert.assertTrue(u.compareTo(v) > 0);
    }

    @Test
    public void testSorting() {
        List<String> input = Arrays.asList(
                "1.10",
                "1.10.2",
                "1.10.2-All the Mods",
                "1.10.2-AOE",
                "1.10.2-AOE-1.1.5",
                "1.10.2-forge2511-Age_of_Progression",
                "1.10.2-forge2511-AOE-1.1.2",
                "1.10.2-forge2511-ATM-E",
                "1.10.2-forge2511-simple_life_2",
                "1.10.2-forge2511_bxztest",
                "1.10.2-forge2511_Farming_Valley",
                "1.10.2-forge2511简单生活BXZ",
                "1.10.2-FTB_Beyond",
                "1.10.2-LiteLoader1.10.2",
                "1.12.2",
                "1.12.2_Modern_Skyblock-3.4.2",
                "1.13.1",
                "1.6.4",
                "1.6.4-Forge9.11.1.1345",
                "1.7.10",
                "1.7.10-1614",
                "1.7.10-1614-test",
                "1.7.10-F1614-L",
                "1.7.10-FL1614_04",
                "1.7.10-Forge10.13.4.1614-1.7.10",
                "1.7.10-Forge1614",
                "1.7.10-Forge1614.1",
                "1.7.10Agrarian_Skies_2",
                "1.7.10forge1614test",
                "1.7.10forge1614_ATlauncher",
                "1.7.10forge1614_FTBInfinity",
                "1.7.10Forge1614_FTBInfinity-2.6.0",
                "1.7.10Forge1614_FTBInfinity-3.0.1",
                "1.7.10forge1614_FTBInfinity_server",
                "1.8",
                "1.8-forge1577",
                "1.8.9",
                "1.8.9-forge1902",
                "1.9");
        input.sort(Comparator.comparing(VersionNumber::asVersion));
    }
}
