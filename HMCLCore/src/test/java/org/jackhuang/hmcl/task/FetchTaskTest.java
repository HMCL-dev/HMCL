// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.task;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class FetchTaskTest {
    @Test
    public void strongETagMustBeQuoted() {
        assertFalse(FetchTask.isStrongETag(null));
        assertFalse(FetchTask.isStrongETag(""));
        assertFalse(FetchTask.isStrongETag("   "));
        // piston-data.mojang.com: 无引号，不是合法的 entity-tag
        assertFalse(FetchTask.isStrongETag("0x8DF131C92002158"));
        // 弱校验器
        assertFalse(FetchTask.isStrongETag("W/\"abc\""));
        // 合法强校验器
        assertTrue(FetchTask.isStrongETag("\"abc\""));
        assertTrue(FetchTask.isStrongETag("\"0x8DF131C92002158\""));
    }
}
