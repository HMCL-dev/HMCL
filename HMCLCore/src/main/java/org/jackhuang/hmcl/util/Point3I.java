// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util;

public record Point3I(int x, int y, int z) {

    public static final Point3I ZERO = new Point3I(0, 0, 0);

}
