// SPDX-FileCopyrightText: Copyright (C) 2025 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util.platform.hardware;

import org.junit.jupiter.api.Test;
import org.jetbrains.annotations.NotNullByDefault;

import static org.jackhuang.hmcl.util.platform.hardware.GraphicsCard.cleanName;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Verifies graphics adapter names and builder defaults.
///
/// @author Glavo
@NotNullByDefault
public final class GraphicsCardTest {

    /// Normalizes known graphics adapter naming conventions.
    @Test
    public void testCleanName() {
        assertEquals("Intel UHD Graphics 770", cleanName("Intel(R) UHD Graphics 770"));

        assertEquals("Qualcomm Adreno 630", cleanName("Qualcomm(R) Adreno(TM) 630 GPU"));
        assertEquals("Qualcomm Adreno Graphics", cleanName("Snapdragon X Elite - X1E78100 - Qualcomm Adreno"));
    }

    /// Unset and explicitly reset types remain non-null without adding an unknown display label.
    @Test
    public void testUnknownType() {
        GraphicsCard.Builder builder = GraphicsCard.builder().setName("Test GPU");
        assertEquals(GraphicsCard.Type.UNKNOWN, builder.getType());
        assertEquals(GraphicsCard.Type.UNKNOWN, builder.build().getType());
        assertEquals("Test GPU", builder.build().toString());

        builder.setType(GraphicsCard.Type.DISCRETE);
        assertEquals("Test GPU [Discrete]", builder.build().toString());
        builder.setType(GraphicsCard.Type.UNKNOWN);
        assertEquals(GraphicsCard.Type.UNKNOWN, builder.build().getType());
    }

    /// Rejecting a null type leaves the builder's valid type intact.
    @Test
    @SuppressWarnings("DataFlowIssue")
    public void testNullTypeRejected() {
        GraphicsCard.Builder builder = GraphicsCard.builder().setType(GraphicsCard.Type.VIRTUAL);
        assertThrows(NullPointerException.class, () -> builder.setType(null));
        assertEquals(GraphicsCard.Type.VIRTUAL, builder.build().getType());
    }
}
