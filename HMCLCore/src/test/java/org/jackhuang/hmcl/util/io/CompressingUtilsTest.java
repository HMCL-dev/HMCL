// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util.io;

import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import static org.jackhuang.hmcl.util.Pair.pair;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Glavo
 */
public final class CompressingUtilsTest {

    public static Stream<Arguments> arguments() {
        return Stream.of(
                pair("utf-8.zip", StandardCharsets.UTF_8),
                pair("gbk.zip", Charset.forName("GB18030"))
        ).map(pair -> {
            try {
                return Arguments.of(Paths.get(CompressingUtilsTest.class.getResource("/zip/" + pair.getKey()).toURI()), pair.getValue());
            } catch (URISyntaxException e) {
                throw new AssertionError(e);
            }
        });
    }

    @ParameterizedTest
    @MethodSource("arguments")
    public void testFindSuitableEncoding(Path path, Charset charset) throws IOException {
        var found = CompressingUtils.findSuitableEncoding(path);
        System.out.println("Native: " + OperatingSystem.NATIVE_CHARSET.name());
        System.out.println("Desired: " + charset.name());
        System.out.println("Found: " + found.name());
        assertEquals(charset, found);
    }
}
