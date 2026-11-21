// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util;

import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Base64;
import java.util.Random;

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
            "Zm9vYg==,foob", "Zm9vYmE=,fooba", "Zm9vYmFy,foobar", "Zh==,f", "Zh,f", "Zm9=,fo", "Zm9,fo"
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

    /// Compares padded and unpadded encodings of reproducible binary inputs at every length.
    @Test
    void matchesJdkAcrossLengths() {
        Random random = new Random(4648);
        for (int length = 0; length <= 1024; length++) {
            byte[] input = new byte[length];
            random.nextBytes(input);
            assertMatchesJdk(Base64.getEncoder().encodeToString(input));
            assertMatchesJdk(Base64.getEncoder().withoutPadding().encodeToString(input));
        }
    }

    /// Checks every ASCII character at each position in full, short, and padded groups.
    @Test
    void matchesJdkForSingleCharacterSubstitutions() {
        for (String group : new String[]{"AA", "AAA", "AAAA", "AA==", "AAA="}) {
            for (int position = 0; position < group.length(); position++) {
                for (char ch = 0; ch < 128; ch++) {
                    String encoded = group.substring(0, position) + ch + group.substring(position + 1);
                    assertMatchesJdk(encoded);
                }
            }
        }
    }

    /// Checks that both decoders return equal bytes or both reject the input.
    private static void assertMatchesJdk(String encoded) {
        byte[] expected;
        try {
            expected = Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException e) {
            assertThrows(IllegalArgumentException.class, () -> Base64Utils.decode(encoded), encoded);
            return;
        }
        assertArrayEquals(expected, Base64Utils.decode(encoded), encoded);
    }

    /// Rejects incomplete groups, misplaced padding, trailing data, and non-alphabet characters.
    @ParameterizedTest
    @ValueSource(strings = {
            "A", "AAAAA", "=", "====", "A===", "AA=", "AAA==", "=AAA", "A=AA", "AA=A", "AAA=AAAA", "YQ==junk",
            "AA A", "AA\nA", "-w==", "_w==", "A\u0141AA"
    })
    void rejectsMalformedInput(String encoded) {
        assertThrows(IllegalArgumentException.class, () -> Base64Utils.decode(encoded));
    }

    /// Checks that a long valid prefix cannot hide a malformed final group.
    /// Keep these rejection checks independent of the JDK decoder affected by CVE-2026-114938.
    @ParameterizedTest
    @ValueSource(strings = {"A", "AA=", "AA!A", "YQ==AAAA"})
    void rejectsMalformedSuffixAfterValidGroups(String suffix) {
        String encoded = "Zm9v".repeat(1024) + suffix;
        assertThrows(IllegalArgumentException.class, () -> Base64Utils.decode(encoded));
    }
}
