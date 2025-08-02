// Copy from javafx.swing
/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package org.jackhuang.hmcl.util;

import javafx.scene.image.*;
import javafx.scene.image.Image;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.awt.image.SampleModel;
import java.awt.image.SinglePixelPackedSampleModel;
import java.nio.IntBuffer;

/**
 * This class provides utility methods for converting data types between
 * Swing/AWT and JavaFX formats.
 *
 * @since JavaFX 2.2
 */
public final class SwingFXUtils {
    private SwingFXUtils() {
    }

    /**
     * Snapshots the specified {@link BufferedImage} and stores a copy of
     * its pixels into a JavaFX {@link Image} object, creating a new
     * object if needed.
     * The returned {@code Image} will be a static snapshot of the state
     * of the pixels in the {@code BufferedImage} at the time the method
     * completes.  Further changes to the {@code BufferedImage} will not
     * be reflected in the {@code Image}.
     * <p>
     * The optional JavaFX {@link WritableImage} parameter may be reused
     * to store the copy of the pixels.
     * A new {@code Image} will be created if the supplied object is null,
     * is too small or of a type which the image pixels cannot be easily
     * converted into.
     *
     * @param bimg the {@code BufferedImage} object to be converted
     * @param wimg an optional {@code WritableImage} object that can be
     *             used to store the returned pixel data
     * @return an {@code Image} object representing a snapshot of the
     * current pixels in the {@code BufferedImage}.
     * @since JavaFX 2.2
     */
    public static WritableImage toFXImage(BufferedImage bimg, WritableImage wimg) {
        int bw = bimg.getWidth();
        int bh = bimg.getHeight();
        switch (bimg.getType()) {
            case BufferedImage.TYPE_INT_ARGB:
            case BufferedImage.TYPE_INT_ARGB_PRE:
                break;
            default:
                BufferedImage converted =
                        new BufferedImage(bw, bh, BufferedImage.TYPE_INT_ARGB_PRE);
                Graphics2D g2d = converted.createGraphics();
                g2d.drawImage(bimg, 0, 0, null);
                g2d.dispose();
                bimg = converted;
                break;
        }
        // assert(bimg.getType == TYPE_INT_ARGB[_PRE]);
        if (wimg != null) {
            int iw = (int) wimg.getWidth();
            int ih = (int) wimg.getHeight();
            if (iw < bw || ih < bh) {
                wimg = null;
            } else if (bw < iw || bh < ih) {
                int[] empty = new int[iw];
                PixelWriter pw = wimg.getPixelWriter();
                PixelFormat<IntBuffer> pf = PixelFormat.getIntArgbPreInstance();
                if (bw < iw) {
                    pw.setPixels(bw, 0, iw - bw, bh, pf, empty, 0, 0);
                }
                if (bh < ih) {
                    pw.setPixels(0, bh, iw, ih - bh, pf, empty, 0, 0);
                }
            }
        }
        if (wimg == null) {
            wimg = new WritableImage(bw, bh);
        }
        PixelWriter pw = wimg.getPixelWriter();
        DataBufferInt db = (DataBufferInt) bimg.getRaster().getDataBuffer();
        int[] data = db.getData();
        int offset = bimg.getRaster().getDataBuffer().getOffset();
        int scan = 0;
        SampleModel sm = bimg.getRaster().getSampleModel();
        if (sm instanceof SinglePixelPackedSampleModel) {
            scan = ((SinglePixelPackedSampleModel) sm).getScanlineStride();
        }

        PixelFormat<IntBuffer> pf = (bimg.isAlphaPremultiplied() ?
                PixelFormat.getIntArgbPreInstance() :
                PixelFormat.getIntArgbInstance());
        pw.setPixels(0, 0, bw, bh, pf, data, offset, scan);
        return wimg;
    }

    public static WritableImage toFXImage(BufferedImage bimg, double requestedWidth, double requestedHeight, boolean preserveRatio, boolean smooth) {
        if (requestedWidth <= 0. || requestedHeight <= 0.) {
            return toFXImage(bimg, null);
        }

        int width = (int) requestedWidth;
        int height = (int) requestedHeight;

        // Calculate actual dimensions if preserveRatio is true
        if (preserveRatio) {
            double originalWidth = bimg.getWidth();
            double originalHeight = bimg.getHeight();
            double scaleX = requestedWidth / originalWidth;
            double scaleY = requestedHeight / originalHeight;
            double scale = Math.min(scaleX, scaleY);

            width = (int) (originalWidth * scale);
            height = (int) (originalHeight * scale);
        }

        // Create scaled BufferedImage
        BufferedImage scaledImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D g2d = scaledImage.createGraphics();
        try {
            if (smooth) {
                g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            }

            g2d.drawImage(bimg, 0, 0, width, height, null);
        } finally {
            g2d.dispose();
        }

        return toFXImage(scaledImage, null);
    }
}

