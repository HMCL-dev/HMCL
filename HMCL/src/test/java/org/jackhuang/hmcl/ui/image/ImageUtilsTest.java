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

import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Verifies image-format detection from bounded input prefixes.
@NotNullByDefault
public final class ImageUtilsTest {

    /// Reads the image-loader detection prefix of a test resource.
    ///
    /// @param fileName resource file name
    /// @return up to [ImageUtils#HEADER_BUFFER_SIZE] leading bytes
    private static byte[] readHeaderBuffer(String fileName) {
        try (var input = Files.newInputStream(Path.of("src/test/resources/image/" + fileName))) {
            return input.readNBytes(ImageUtils.HEADER_BUFFER_SIZE);

        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    /// Encodes an XML fixture without introducing non-ASCII bytes.
    ///
    /// @param value fixture text
    /// @return US-ASCII bytes
    private static byte[] asciiBytes(String value) {
        return value.getBytes(StandardCharsets.US_ASCII);
    }

    /// Verifies APNG detection distinguishes animated and static PNG files.
    @Test
    public void testIsApng() {
        assertTrue(ImageUtils.isApng(readHeaderBuffer("16x16.apng")));
        assertFalse(ImageUtils.isApng(readHeaderBuffer("16x16.png")));
        assertFalse(ImageUtils.isApng(readHeaderBuffer("16x16-lossless.webp")));
        assertFalse(ImageUtils.isApng(readHeaderBuffer("16x16-lossy.webp")));
        assertFalse(ImageUtils.isApng(readHeaderBuffer("16x16-animation-lossy.webp")));
        assertFalse(ImageUtils.isApng(readHeaderBuffer("16x16-animation-lossy.webp")));
    }

    /// Verifies WebP detection recognizes all supported WebP variants.
    @Test
    public void testIsWebP() {
        assertFalse(ImageUtils.isWebP(readHeaderBuffer("16x16.apng")));
        assertFalse(ImageUtils.isWebP(readHeaderBuffer("16x16.png")));
        assertTrue(ImageUtils.isWebP(readHeaderBuffer("16x16-lossless.webp")));
        assertTrue(ImageUtils.isWebP(readHeaderBuffer("16x16-lossy.webp")));
        assertTrue(ImageUtils.isWebP(readHeaderBuffer("16x16-animation-lossy.webp")));
        assertTrue(ImageUtils.isWebP(readHeaderBuffer("16x16-animation-lossy.webp")));
    }

    /// Verifies SVG detection accepts XML prologs and an SVG document element.
    @Test
    public void testIsSVG() {
        assertTrue(ImageUtils.isSVG(asciiBytes("<svg/>")));
        assertTrue(ImageUtils.isSVG(asciiBytes(" \t\r\n<svg viewBox='0 0 16 16'>")));
        assertTrue(ImageUtils.isSVG(
                "\ufeff<?xml version='1.0' encoding='UTF-8'?>\n<svg/>"
                        .getBytes(StandardCharsets.UTF_8)
        ));
        assertTrue(ImageUtils.isSVG(asciiBytes(
                "<?xml version='1.0'?>"
                        + "<?xml-stylesheet href='<svg.css'?>"
                        + "<!-- <svg/> -->"
                        + "<svg xmlns='http://www.w3.org/2000/svg'>"
        )));
        assertTrue(ImageUtils.isSVG(asciiBytes(
                "<!DOCTYPE svg ["
                        + "<!ENTITY quoted ']> <svg>'>"
                        + "<!-- ]> <svg -->"
                        + "<!ELEMENT svg ANY>"
                        + "]><svg/>"
        )));
        assertTrue(ImageUtils.isSVG(asciiBytes(
                "<!--" + "x".repeat(ImageUtils.HEADER_BUFFER_SIZE - 13) + "--><svg/>"
        )));
        assertTrue(ImageUtils.isSVG(asciiBytes(
                "<svg:svg xmlns:svg='http://www.w3.org/2000/svg'/>"
        )));
        assertTrue(ImageUtils.isSVG(asciiBytes(
                "<image:svg xmlns:image='http://www.w3.org/2000/svg'/>"
        )));
    }

    /// Verifies SVG detection rejects incomplete, embedded, and non-SVG roots.
    @Test
    public void testIsNotSVG() {
        assertFalse(ImageUtils.isSVG(new byte[0]));
        assertFalse(ImageUtils.isSVG(asciiBytes("<svg")));
        assertFalse(ImageUtils.isSVG(asciiBytes("<svg-image/>")));
        assertFalse(ImageUtils.isSVG(asciiBytes("<svg:circle/>")));
        assertFalse(ImageUtils.isSVG(asciiBytes("<image:svg-icon/>")));
        assertFalse(ImageUtils.isSVG(asciiBytes("<SVG/>")));
        assertFalse(ImageUtils.isSVG(asciiBytes("plain text <svg/>")));
        assertFalse(ImageUtils.isSVG(asciiBytes("<!-- <svg/> -->")));
        assertFalse(ImageUtils.isSVG(asciiBytes(
                "<?xml version='1.0'?><html><svg/></html>"
        )));
        assertFalse(ImageUtils.isSVG(asciiBytes(
                "<!DOCTYPE svg [<!-- incomplete ]> <svg -->"
        )));
        assertFalse(ImageUtils.isSVG(asciiBytes(
                "<!--" + "x".repeat(ImageUtils.HEADER_BUFFER_SIZE) + "--><svg/>"
        )));
    }

    /// Verifies sniffing routes a prefixed SVG document to the SVG loader.
    @Test
    public void testGuessLoaderDetectsPrefixedSVG() {
        assertSame(ImageUtils.SVG, ImageUtils.guessLoader(asciiBytes(
                "<svg:svg xmlns:svg='http://www.w3.org/2000/svg'/>"
        )));
    }
}
