// SPDX-FileCopyrightText: Copyright (C) 2025 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util.platform.hardware;

import org.junit.jupiter.api.Test;

import static org.jackhuang.hmcl.util.platform.hardware.GraphicsCard.cleanName;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Glavo
 */
public final class GraphicsCardTest {

    @Test
    public void testCleanName() {
        assertEquals("Intel UHD Graphics 770", cleanName("Intel(R) UHD Graphics 770"));

        assertEquals("Qualcomm Adreno 630", cleanName("Qualcomm(R) Adreno(TM) 630 GPU"));
        assertEquals("Qualcomm Adreno Graphics", cleanName("Snapdragon X Elite - X1E78100 - Qualcomm Adreno"));
    }
}
