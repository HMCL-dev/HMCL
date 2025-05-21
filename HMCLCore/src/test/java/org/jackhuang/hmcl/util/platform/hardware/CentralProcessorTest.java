/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2025 huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.util.platform.hardware;

import org.junit.jupiter.api.Test;

import static org.jackhuang.hmcl.util.platform.hardware.CentralProcessor.cleanName;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Glavo
 */
public final class CentralProcessorTest {

    @Test
    public void testCleanName() {
        assertEquals("Intel Core i7-12700K", cleanName("12th Gen Intel(R) Core(TM) i7-12700K"));
        assertEquals("Intel Xeon E5-2660 v2", cleanName("Intel(R) Xeon(R) CPU E5-2660 v2 @ 2.20GHz"));
        assertEquals("Intel Celeron N5105", cleanName("Intel(R) Celeron(R) N5105 @ 2.00GHz"));
        assertEquals("Intel Pentium Silver J5005", cleanName("Intel(R) Pentium(R) Silver J5005 CPU @ 1.50GHz"));

        assertEquals("AMD Ryzen 7 5800X", cleanName("AMD Ryzen 7 5800X 8-Core Processor"));
        assertEquals("AMD Ryzen 7 6800U", cleanName("AMD Ryzen 7 6800U with Radeon Graphics"));
        assertEquals("AMD Ryzen 7 7840HS", cleanName("AMD Ryzen 7 7840HS w/ Radeon 780M Graphics"));
        assertEquals("AMD EPYC 7713", cleanName("AMD EPYC 7713 64-Core Processor"));
    }
}
