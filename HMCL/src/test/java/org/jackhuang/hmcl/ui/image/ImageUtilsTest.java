/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2025 huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.ui.image;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class ImageUtilsTest {

    private static byte[] readHeaderBuffer(String fileName) {
        try (var input = Files.newInputStream(Path.of("src/test/resources/image/" + fileName))) {
            return input.readNBytes(ImageUtils.HEADER_BUFFER_SIZE);

        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    public void testIsApng() {
        assertTrue(ImageUtils.isApng(readHeaderBuffer("16x16.apng")));
        assertFalse(ImageUtils.isApng(readHeaderBuffer("16x16.png")));
        assertFalse(ImageUtils.isApng(readHeaderBuffer("16x16-lossless.webp")));
        assertFalse(ImageUtils.isApng(readHeaderBuffer("16x16-lossy.webp")));
        assertFalse(ImageUtils.isApng(readHeaderBuffer("16x16-animation-lossy.webp")));
        assertFalse(ImageUtils.isApng(readHeaderBuffer("16x16-animation-lossy.webp")));
    }

    @Test
    public void testIsWebP() {
        assertFalse(ImageUtils.isWebP(readHeaderBuffer("16x16.apng")));
        assertFalse(ImageUtils.isWebP(readHeaderBuffer("16x16.png")));
        assertTrue(ImageUtils.isWebP(readHeaderBuffer("16x16-lossless.webp")));
        assertTrue(ImageUtils.isWebP(readHeaderBuffer("16x16-lossy.webp")));
        assertTrue(ImageUtils.isWebP(readHeaderBuffer("16x16-animation-lossy.webp")));
        assertTrue(ImageUtils.isWebP(readHeaderBuffer("16x16-animation-lossy.webp")));
    }

}
