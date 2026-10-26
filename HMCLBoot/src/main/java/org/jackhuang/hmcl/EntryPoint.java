// SPDX-FileCopyrightText: Copyright (C) 2025 The HMCL Authors.
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl;

/**
 * This is a dummy class and will be overwritten in the shadow jar.
 */
public final class EntryPoint {
    private EntryPoint() {
    }

    public static void main(String[] args) {
        throw new AssertionError("This method should not be called, please verify that the HMCL is complete.");
    }
}
