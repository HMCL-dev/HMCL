// SPDX-FileCopyrightText: Copyright (C) 2025 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public final class MainTest {
    @Test
    public void testGetJavaFeatureVersion() {
        assertEquals(6, Main.getJavaFeatureVersion("1.6.0"));
        assertEquals(6, Main.getJavaFeatureVersion("1.6.0_45"));
        assertEquals(7, Main.getJavaFeatureVersion("1.7.0"));
        assertEquals(7, Main.getJavaFeatureVersion("1.7.0_80"));
        assertEquals(8, Main.getJavaFeatureVersion("1.8"));
        assertEquals(8, Main.getJavaFeatureVersion("1.8u321"));
        assertEquals(8, Main.getJavaFeatureVersion("1.8.0_321"));
        assertEquals(11, Main.getJavaFeatureVersion("11"));
        assertEquals(11, Main.getJavaFeatureVersion("11.0.26"));
        assertEquals(21, Main.getJavaFeatureVersion("21"));
        assertEquals(26, Main.getJavaFeatureVersion("26-ea"));

        assertEquals(-1, Main.getJavaFeatureVersion(null));
        assertEquals(-1, Main.getJavaFeatureVersion(""));
        assertEquals(-1, Main.getJavaFeatureVersion("0"));
        assertEquals(-1, Main.getJavaFeatureVersion("0.8"));
        assertEquals(-1, Main.getJavaFeatureVersion("abc"));
        assertEquals(-1, Main.getJavaFeatureVersion("1.abc"));
        assertEquals(-1, Main.getJavaFeatureVersion(".1"));
        assertEquals(-1, Main.getJavaFeatureVersion("1111111111111111111111"));
    }
}
