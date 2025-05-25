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
        assertEquals("Intel Core Ultra 9 285K", cleanName("Intel(R) Core(TM) Ultra 9 285K"));
        assertEquals("Intel Xeon Platinum 8380", cleanName("Intel(R) Xeon(R) Platinum 8380 CPU @ 2.30GHz"));
        assertEquals("Intel Xeon E5-2660 v2", cleanName("Intel(R) Xeon(R) CPU E5-2660 v2 @ 2.20GHz"));
        assertEquals("Intel Xeon Phi 7250", cleanName("Intel(R) Xeon Phi(TM) CPU 7250 @ 1.40GHz"));
        assertEquals("Intel Celeron N5105", cleanName("Intel(R) Celeron(R) N5105 @ 2.00GHz"));
        assertEquals("Intel Pentium Silver J5005", cleanName("Intel(R) Pentium(R) Silver J5005 CPU @ 1.50GHz"));
        assertEquals("Intel Atom E3940", cleanName("Intel(R) Atom(TM) Processor E3940 @ 1.60GHz"));
        assertEquals("Intel Core i7 X 990", cleanName("Intel(R) Core(TM) i7 CPU       X 990  @ 3.47GHz"));
        assertEquals("Intel Core 2 Duo T7500", cleanName("Intel(R) Core(TM)2 Duo CPU     T7500  @ 2.20GHz"));
        assertEquals("Intel Core 2 Quad Q9500", cleanName("Intel(R) Core(TM)2 Quad CPU    Q9500  @ 2.83GHz"));

        assertEquals("AMD Ryzen 7 7840HS", cleanName("AMD Ryzen 7 7840HS w/ Radeon 780M Graphics"));
        assertEquals("AMD Ryzen 7 6800U", cleanName("AMD Ryzen 7 6800U with Radeon Graphics"));
        assertEquals("AMD Ryzen 7 5800X", cleanName("AMD Ryzen 7 5800X 8-Core Processor"));
        assertEquals("AMD Ryzen 5 2400G", cleanName("AMD Ryzen 5 2400G with Radeon Vega Graphics"));
        assertEquals("AMD EPYC 7713", cleanName("AMD EPYC 7713 64-Core Processor"));
        assertEquals("AMD Ryzen Threadripper 3960X", cleanName("AMD Ryzen Threadripper 3960X 24-Core Processor"));
        assertEquals("AMD Ryzen Threadripper PRO 5995WX", cleanName("AMD Ryzen Threadripper PRO 5995WX 64-Cores"));
        assertEquals("AMD Ryzen Embedded V2748", cleanName("AMD Ryzen Embedded V2748 with Radeon Graphics"));
        assertEquals("AMD A8-7410", cleanName("AMD A8-7410 APU with AMD Radeon R5 Graphics"));
        assertEquals("AMD FX-8350", cleanName("AMD FX(tm)-8350 Eight-Core Processor"));
        assertEquals("AMD Phenom II X6 1055T", cleanName("AMD Phenom(tm) II X6 1055T Processor"));
        assertEquals("AMD Athlon 5350", cleanName("AMD Athlon(tm) 5350 APU with Radeon(tm) R3"));

        assertEquals("Qualcomm Snapdragon X Elite X1E78100", cleanName("Snapdragon(R) X Elite - X1E78100 - Qualcomm(R) Oryon(TM) CPU"));
        assertEquals("Qualcomm Snapdragon 850", cleanName("Snapdragon (TM) 850 @ 2.96 GHz"));

        assertEquals("Hygon C86 7285", cleanName("Hygon C86 7285 32-core Processor"));
        assertEquals("Hygon C86 3250", cleanName("Hygon C86 3250  8-core Processor"));

        assertEquals("ZHAOXIN KaiXian KX-6640MA", cleanName("ZHAOXIN KaiXian KX-6640MA@2.2+GHz"));
        assertEquals("ZHAOXIN KaiXian KX-U6780A", cleanName("ZHAOXIN KaiXian KX-U6780A@2.7GHz"));
        assertEquals("ZHAOXIN KaiSheng KH-40000/16", cleanName("ZHAOXIN KaiSheng KH-40000/16@2.2GHz"));
        assertEquals("ZHAOXIN KaiSheng KH-37800D", cleanName("ZHAOXIN KaiSheng KH-37800D@2.7GHz"));

        assertEquals("Loongson-3A3000", cleanName("Loongson-3A R3 (Loongson-3A3000) @ 1400MHz"));
        assertEquals("Loongson-3B4000", cleanName("Loongson-3A R4 (Loongson-3B4000) @ 1800MHz"));
        assertEquals("Loongson-3A6000", cleanName("Loongson-3A6000"));
    }
}
