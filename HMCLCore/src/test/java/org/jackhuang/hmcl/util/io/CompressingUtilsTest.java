/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026 huangyuhui <huanghongxun2008@126.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.jackhuang.hmcl.util.io;

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
        assertEquals(charset, CompressingUtils.findSuitableEncoding(path));
    }
}
