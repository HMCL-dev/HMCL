// SPDX-FileCopyrightText: Copyright (C) 2025 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.setting;

import javafx.scene.paint.Color;
import org.jackhuang.hmcl.theme.ThemeColor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/// @author Glavo
public final class ThemeColorTest {

    @Test
    public void testOf() {
        assertEquals(new ThemeColor("#AABBCC", Color.web("#AABBCC")), ThemeColor.of("#AABBCC"));
        assertEquals(new ThemeColor("blue", Color.web("#5C6BC0")), ThemeColor.of("blue"));
        assertEquals(new ThemeColor("darker_blue", Color.web("#283593")), ThemeColor.of("darker_blue"));
        assertEquals(new ThemeColor("green", Color.web("#43A047")), ThemeColor.of("green"));
        assertEquals(new ThemeColor("orange", Color.web("#E67E22")), ThemeColor.of("orange"));
        assertEquals(new ThemeColor("purple", Color.web("#9C27B0")), ThemeColor.of("purple"));
        assertEquals(new ThemeColor("red", Color.web("#B71C1C")), ThemeColor.of("red"));

        assertNull(ThemeColor.of((String) null));
        assertNull(ThemeColor.of(""));
        assertNull(ThemeColor.of("unknown"));
    }
}
