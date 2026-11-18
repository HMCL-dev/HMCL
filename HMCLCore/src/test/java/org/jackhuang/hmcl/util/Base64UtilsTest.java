// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util;

import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Base64;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Verifies basic Base64 decoding and input validation.
@NotNullByDefault
class Base64UtilsTest {
    /// Checks complete groups, optional padding, and nonzero unused bits in final digits.
    @ParameterizedTest
    @CsvSource({
            "'',''", "Zg==,f", "Zg,f", "Zm8=,fo", "Zm8,fo", "Zm9v,foo",
            "Zm9vYmFy,foobar", "Zh==,f", "Zh,f", "Zm9=,fo", "Zm9,fo"
    })
    void decodesText(String encoded, String expected) {
        assertArrayEquals(expected.getBytes(US_ASCII), Base64Utils.decode(encoded));
    }

    /// Exercises the full byte range across successive groups and a partial final group.
    @Test
    void decodesBinaryData() {
        byte[] bytes = new byte[256];
        for (int i = 0; i < bytes.length; i++)
            bytes[i] = (byte) i;

        assertArrayEquals(bytes, Base64Utils.decode(Base64.getEncoder().encodeToString(bytes)));
    }

    /// Rejects incomplete groups, misplaced padding, trailing data, and non-alphabet characters.
    @ParameterizedTest
    @ValueSource(strings = {
            "A", "AAAAA", "=", "====", "A===", "AA=", "AAA==", "AA=A", "YQ==junk",
            "AA A", "AA\nA", "-w==", "_w==", "A\u0141AA"
    })
    void rejectsMalformedInput(String encoded) {
        assertThrows(IllegalArgumentException.class, () -> Base64Utils.decode(encoded));
    }
}
