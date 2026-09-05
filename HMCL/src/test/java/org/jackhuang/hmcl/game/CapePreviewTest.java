/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.game;

import javafx.scene.image.Image;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import static org.junit.jupiter.api.Assertions.*;

/// Unit tests for the Minecraft cape UV crop logic.
public final class CapePreviewTest {

    private static WritableImage newTexture(int width, int height, int argb) {
        WritableImage image = new WritableImage(width, height);
        PixelWriter writer = image.getPixelWriter();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                writer.setArgb(x, y, argb);
            }
        }
        return image;
    }

    @Test
    public void testStandard64x32FrontRegion() {
        assertArrayEquals(new int[]{1, 1, 10, 16}, CapePreview.computeFrontRegion(64, 32));
    }

    @Test
    public void testHighResolutionFrontRegionsScaleProportionally() {
        assertArrayEquals(new int[]{2, 2, 20, 32}, CapePreview.computeFrontRegion(128, 64));
        assertArrayEquals(new int[]{4, 4, 40, 64}, CapePreview.computeFrontRegion(256, 128));
    }

    @Test
    public void testUnusualSizeStaysInBounds() {
        int[] region = CapePreview.computeFrontRegion(45, 22);
        assertNotNull(region);
        int x = region[0], y = region[1], w = region[2], h = region[3];
        assertTrue(x >= 0 && y >= 0 && w > 0 && h > 0);
        assertTrue(x + w <= 45 && y + h <= 22);
    }

    @Test
    public void testInvalidSizeReturnsNull() {
        assertNull(CapePreview.computeFrontRegion(0, 32));
        assertNull(CapePreview.computeFrontRegion(64, 0));
    }

    @Test
    @EnabledIf("org.jackhuang.hmcl.JavaFXLauncher#isStarted")
    public void testExtractFrontFaceCopiesPixels() {
        int color = 0xFF336699;

        Image preview64 = CapePreview.extractFrontFace(newTexture(64, 32, color));
        assertNotNull(preview64);
        assertEquals(10, (int) preview64.getWidth());
        assertEquals(16, (int) preview64.getHeight());
        assertEquals(color, preview64.getPixelReader().getArgb(0, 0));

        Image preview128 = CapePreview.extractFrontFace(newTexture(128, 64, color));
        assertNotNull(preview128);
        assertEquals(20, (int) preview128.getWidth());
        assertEquals(32, (int) preview128.getHeight());
        assertEquals(color, preview128.getPixelReader().getArgb(0, 0));

        Image preview256 = CapePreview.extractFrontFace(newTexture(256, 128, color));
        assertNotNull(preview256);
        assertEquals(40, (int) preview256.getWidth());
        assertEquals(64, (int) preview256.getHeight());
        assertEquals(color, preview256.getPixelReader().getArgb(0, 0));
    }

    @Test
    public void testExtractFrontFaceOnNullReturnsNull() {
        assertNull(CapePreview.extractFrontFace(null));
    }
}
