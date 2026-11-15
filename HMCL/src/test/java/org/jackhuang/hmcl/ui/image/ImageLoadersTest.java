// SPDX-FileCopyrightText: Copyright (C) 2025 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.image;

import org.jackhuang.hmcl.image.ImageLoaders;
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
public final class ImageLoadersTest {

    /// Reads the image-loader detection prefix of a test resource.
    ///
    /// @param fileName resource file name
    /// @return up to [ImageLoaders#HEADER_BUFFER_SIZE] leading bytes
    private static byte[] readHeaderBuffer(String fileName) {
        try (var input = Files.newInputStream(Path.of("src/test/resources/image/" + fileName))) {
            return input.readNBytes(ImageLoaders.HEADER_BUFFER_SIZE);

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
        assertTrue(ImageLoaders.isApng(readHeaderBuffer("16x16.apng")));
        assertFalse(ImageLoaders.isApng(readHeaderBuffer("16x16.png")));
        assertFalse(ImageLoaders.isApng(readHeaderBuffer("16x16-lossless.webp")));
        assertFalse(ImageLoaders.isApng(readHeaderBuffer("16x16-lossy.webp")));
        assertFalse(ImageLoaders.isApng(readHeaderBuffer("16x16-animation-lossy.webp")));
        assertFalse(ImageLoaders.isApng(readHeaderBuffer("16x16-animation-lossy.webp")));
    }

    /// Verifies WebP detection recognizes all supported WebP variants.
    @Test
    public void testIsWebP() {
        assertFalse(ImageLoaders.isWebP(readHeaderBuffer("16x16.apng")));
        assertFalse(ImageLoaders.isWebP(readHeaderBuffer("16x16.png")));
        assertTrue(ImageLoaders.isWebP(readHeaderBuffer("16x16-lossless.webp")));
        assertTrue(ImageLoaders.isWebP(readHeaderBuffer("16x16-lossy.webp")));
        assertTrue(ImageLoaders.isWebP(readHeaderBuffer("16x16-animation-lossy.webp")));
        assertTrue(ImageLoaders.isWebP(readHeaderBuffer("16x16-animation-lossy.webp")));
    }

    /// Verifies SVG detection accepts XML prologs and an SVG document element.
    @Test
    public void testIsSVG() {
        assertTrue(ImageLoaders.isSVG(asciiBytes("<svg/>")));
        assertTrue(ImageLoaders.isSVG(asciiBytes(" \t\r\n<svg viewBox='0 0 16 16'>")));
        assertTrue(ImageLoaders.isSVG(
                "\ufeff<?xml version='1.0' encoding='UTF-8'?>\n<svg/>"
                        .getBytes(StandardCharsets.UTF_8)
        ));
        assertTrue(ImageLoaders.isSVG(asciiBytes(
                "<?xml version='1.0'?>"
                        + "<?xml-stylesheet href='<svg.css'?>"
                        + "<!-- <svg/> -->"
                        + "<svg xmlns='http://www.w3.org/2000/svg'>"
        )));
        assertTrue(ImageLoaders.isSVG(asciiBytes(
                "<!DOCTYPE svg ["
                        + "<!ENTITY quoted ']> <svg>'>"
                        + "<!-- ]> <svg -->"
                        + "<!ELEMENT svg ANY>"
                        + "]><svg/>"
        )));
        assertTrue(ImageLoaders.isSVG(asciiBytes(
                "<!--" + "x".repeat(ImageLoaders.HEADER_BUFFER_SIZE - 13) + "--><svg/>"
        )));
        assertTrue(ImageLoaders.isSVG(asciiBytes(
                "<svg:svg xmlns:svg='http://www.w3.org/2000/svg'/>"
        )));
        assertTrue(ImageLoaders.isSVG(asciiBytes(
                "<image:svg xmlns:image='http://www.w3.org/2000/svg'/>"
        )));
    }

    /// Verifies SVG detection rejects incomplete, embedded, and non-SVG roots.
    @Test
    public void testIsNotSVG() {
        assertFalse(ImageLoaders.isSVG(new byte[0]));
        assertFalse(ImageLoaders.isSVG(asciiBytes("<svg")));
        assertFalse(ImageLoaders.isSVG(asciiBytes("<svg-image/>")));
        assertFalse(ImageLoaders.isSVG(asciiBytes("<svg:circle/>")));
        assertFalse(ImageLoaders.isSVG(asciiBytes("<image:svg-icon/>")));
        assertFalse(ImageLoaders.isSVG(asciiBytes("<SVG/>")));
        assertFalse(ImageLoaders.isSVG(asciiBytes("plain text <svg/>")));
        assertFalse(ImageLoaders.isSVG(asciiBytes("<!-- <svg/> -->")));
        assertFalse(ImageLoaders.isSVG(asciiBytes(
                "<?xml version='1.0'?><html><svg/></html>"
        )));
        assertFalse(ImageLoaders.isSVG(asciiBytes(
                "<!DOCTYPE svg [<!-- incomplete ]> <svg -->"
        )));
        assertFalse(ImageLoaders.isSVG(asciiBytes(
                "<!--" + "x".repeat(ImageLoaders.HEADER_BUFFER_SIZE) + "--><svg/>"
        )));
    }

    /// Verifies sniffing routes a prefixed SVG document to the SVG loader.
    @Test
    public void testGuessLoaderDetectsPrefixedSVG() {
        assertSame(ImageLoaders.SVG, ImageLoaders.guessLoader(asciiBytes(
                "<svg:svg xmlns:svg='http://www.w3.org/2000/svg'/>"
        )));
    }
}
