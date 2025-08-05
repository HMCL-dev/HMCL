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

import javafx.animation.Timeline;
import javafx.scene.image.Image;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import org.jackhuang.hmcl.ui.image.apng.Png;
import org.jackhuang.hmcl.ui.image.apng.argb8888.Argb8888Bitmap;
import org.jackhuang.hmcl.ui.image.apng.argb8888.Argb8888BitmapSequence;
import org.jackhuang.hmcl.ui.image.apng.chunks.PngAnimationControl;
import org.jackhuang.hmcl.ui.image.apng.chunks.PngFrameControl;
import org.jackhuang.hmcl.ui.image.apng.error.PngException;
import org.jackhuang.hmcl.ui.image.apng.error.PngIntegrityException;
import org.jackhuang.hmcl.ui.image.internal.AnimationImageImpl;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/**
 * @author Glavo
 */
public final class ImageUtils {
    private static Image toImage(Argb8888BitmapSequence sequence) throws PngException {
        final int width = sequence.header.width;
        final int height = sequence.header.height;

        List<Argb8888BitmapSequence.Frame> frames = sequence.getAnimationFrames();

        var framePixels = new int[frames.size()][];
        var durations = new int[framePixels.length];

        int[] buffer = new int[Math.multiplyExact(width, height)];
        for (int frameIndex = 0; frameIndex < frames.size(); frameIndex++) {
            var frame = frames.get(frameIndex);

            PngFrameControl control = frame.control;

            if (control.xOffset < 0 || control.yOffset < 0
                    || width < 0 || height < 0
                    || control.xOffset + control.width > width
                    || control.yOffset + control.height > height
                    || control.delayNumerator < 0 || control.delayDenominator < 0
            ) {
                throw new PngIntegrityException("Invalid frame control: " + control);
            }

            int[] currentFrameBuffer = buffer.clone();
            if (control.blendOp == 0) {
                for (int row = 0; row < control.height; row++) {
                    System.arraycopy(frame.bitmap.array,
                            row * control.width,
                            currentFrameBuffer,
                            (control.yOffset + row) * width + control.xOffset,
                            control.width);
                }
            } else if (control.blendOp == 1) {
                // fixme: APNG_BLEND_OP_OVER - Alpha blending
                for (int row = 0; row < control.height; row++) {
                    for (int col = 0; col < control.width; col++) {
                        int srcIndex = row * control.width + col;
                        int dstIndex = (control.yOffset + row) * width + control.xOffset + col;

                        int srcPixel = frame.bitmap.array[srcIndex];
                        int dstPixel = currentFrameBuffer[dstIndex];

                        int srcAlpha = (srcPixel >>> 24) & 0xFF;
                        if (srcAlpha == 0) {
                            continue;
                        } else if (srcAlpha == 255) {
                            currentFrameBuffer[dstIndex] = srcPixel;
                        } else {
                            int srcR = (srcPixel >>> 16) & 0xFF;
                            int srcG = (srcPixel >>> 8) & 0xFF;
                            int srcB = srcPixel & 0xFF;

                            int dstAlpha = (dstPixel >>> 24) & 0xFF;
                            int dstR = (dstPixel >>> 16) & 0xFF;
                            int dstG = (dstPixel >>> 8) & 0xFF;
                            int dstB = dstPixel & 0xFF;

                            int invSrcAlpha = 255 - srcAlpha;
                            int outAlpha = srcAlpha + (dstAlpha * invSrcAlpha) / 255;
                            int outR = (srcR * srcAlpha + dstR * invSrcAlpha) / 255;
                            int outG = (srcG * srcAlpha + dstG * invSrcAlpha) / 255;
                            int outB = (srcB * srcAlpha + dstB * invSrcAlpha) / 255;

                            currentFrameBuffer[dstIndex] = (outAlpha << 24) | (outR << 16) | (outG << 8) | outB;
                        }
                    }
                }
            } else {
                throw new PngIntegrityException("Unsupported blendOp " + control.blendOp + " at frame " + frameIndex);
            }

            framePixels[frameIndex] = currentFrameBuffer;

            if (control.delayNumerator == 0) {
                durations[frameIndex] = 10;
            } else {
                int durationsMills = 1000 * control.delayNumerator;
                if (control.delayDenominator == 0)
                    durationsMills /= 100;
                else
                    durationsMills /= control.delayDenominator;

                durations[frameIndex] = durationsMills;
            }

            switch (control.disposeOp) {
                case 0:  // APNG_DISPOST_OP_NONE
                    System.arraycopy(currentFrameBuffer, 0, buffer, 0, currentFrameBuffer.length);
                    break;
                case 1: // APNG_DISPOSE_OP_BACKGROUND
                    Arrays.fill(buffer, 0);
                    break;
                case 2: // APNG_DISPOSE_OP_PREVIOUS
                    // Do nothing, keep the previous frame.
                    break;
                default:
                    throw new PngIntegrityException("Unsupported disposeOp " + control.disposeOp + " at frame " + frameIndex);
            }
        }

        PngAnimationControl animationControl = sequence.getAnimationControl();
        int cycleCount;
        if (animationControl != null) {
            cycleCount = animationControl.numPlays;
            if (cycleCount == 0)
                cycleCount = Timeline.INDEFINITE;
        } else {
            cycleCount = Timeline.INDEFINITE;
        }

        return new AnimationImageImpl(width, height, framePixels, durations, cycleCount);
    }

    public static Image loadApng(InputStream input) throws IOException {
        try {
            var sequence = Png.readArgb8888BitmapSequence(input);
            if (sequence.isAnimated()) {
                try {
                    return toImage(sequence);
                } catch (Throwable e) {
                    LOG.warning("Failed to load animated image", e);
                }
            }

            Argb8888Bitmap defaultImage = sequence.defaultImage;
            WritableImage image = new WritableImage(defaultImage.width, defaultImage.height);
            image.getPixelWriter().setPixels(0, 0, defaultImage.width, defaultImage.height,
                    PixelFormat.getIntArgbInstance(), defaultImage.array,
                    0, defaultImage.width);
            return image;
        } catch (PngException e) {
            throw new IOException(e);
        }
    }

    private ImageUtils() {
    }
}
