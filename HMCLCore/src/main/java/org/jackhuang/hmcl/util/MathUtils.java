// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util;

/// Utility class for mathematical operations.
///
/// @author Glavo
public final class MathUtils {

    /// Clamp value to fit between min and max.
    public static double clamp(double value, double min, double max) {
        assert min <= max;
        return Math.min(max, Math.max(value, min));
    }

    /// Clamp value to fit between min and max.
    public static int clamp(int value, int min, int max) {
        assert min <= max;
        return Math.min(max, Math.max(value, min));
    }

    private MathUtils() {
    }
}
