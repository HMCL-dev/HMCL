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

import javafx.scene.image.Image;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritablePixelFormat;
import javafx.scene.paint.Color;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.awt.image.SampleModel;
import java.awt.image.SinglePixelPackedSampleModel;
import java.nio.IntBuffer;

/**
 * This class provides utility methods for converting data types between
 * Swing/AWT and JavaFX formats.
 * @since JavaFX 2.2
 */
public class SwingFXUtils {
    private SwingFXUtils() {} // no instances

    /**
     * Determine the optimal BufferedImage type to use for the specified
     * {@code fxFormat} allowing for the specified {@code bimg} to be used
     * as a potential default storage space if it is not null and is compatible.
     *
     * @param fxFormat the PixelFormat of the source FX Image
     * @param bimg an optional existing {@code BufferedImage} to be used
     *             for storage if it is compatible, or null
     * @return
     */
    static int
    getBestBufferedImageType(PixelFormat<?> fxFormat, BufferedImage bimg,
                             boolean isOpaque)
    {
        if (bimg != null) {
            int bimgType = bimg.getType();
            if (bimgType == BufferedImage.TYPE_INT_ARGB ||
                    bimgType == BufferedImage.TYPE_INT_ARGB_PRE ||
                    (isOpaque &&
                            (bimgType == BufferedImage.TYPE_INT_BGR ||
                                    bimgType == BufferedImage.TYPE_INT_RGB)))
            {
                // We will allow the caller to give us a BufferedImage
                // that has an alpha channel, but we might not otherwise
                // construct one ourselves.
                // We will also allow them to choose their own premultiply
                // type which may not match the image.
                // If left to our own devices we might choose a more specific
                // format as indicated by the choices below.
                return bimgType;
            }
        }
        switch (fxFormat.getType()) {
            default:
            case BYTE_BGRA_PRE:
            case INT_ARGB_PRE:
                return BufferedImage.TYPE_INT_ARGB_PRE;
            case BYTE_BGRA:
            case INT_ARGB:
                return BufferedImage.TYPE_INT_ARGB;
            case BYTE_RGB:
                return BufferedImage.TYPE_INT_RGB;
            case BYTE_INDEXED:
                return (fxFormat.isPremultiplied()
                        ? BufferedImage.TYPE_INT_ARGB_PRE
                        : BufferedImage.TYPE_INT_ARGB);
        }
    }

    /**
     * Determine the appropriate {@link WritablePixelFormat} type that can
     * be used to transfer data into the indicated BufferedImage.
     *
     * @param bimg the BufferedImage that will be used as a destination for
     *             a {@code PixelReader<IntBuffer>#getPixels()} operation.
     * @return
     */
    private static WritablePixelFormat<IntBuffer>
    getAssociatedPixelFormat(BufferedImage bimg)
    {
        switch (bimg.getType()) {
            // We lie here for xRGB, but we vetted that the src data was opaque
            // so we can ignore the alpha.  We use ArgbPre instead of Argb
            // just to get a loop that does not have divides in it if the
            // PixelReader happens to not know the data is opaque.
            case BufferedImage.TYPE_INT_RGB:
            case BufferedImage.TYPE_INT_ARGB_PRE:
                return PixelFormat.getIntArgbPreInstance();
            case BufferedImage.TYPE_INT_ARGB:
                return PixelFormat.getIntArgbInstance();
            default:
                // Should not happen...
                throw new InternalError("Failed to validate BufferedImage type");
        }
    }

    private static boolean checkFXImageOpaque(PixelReader pr, int iw, int ih) {
        for (int x = 0; x < iw; x++) {
            for (int y = 0; y < ih; y++) {
                Color color = pr.getColor(x,y);
                if (color.getOpacity() != 1.0) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Snapshots the specified JavaFX {@link Image} object and stores a
     * copy of its pixels into a {@link BufferedImage} object, creating
     * a new object if needed.
     * The method will only convert a JavaFX {@code Image} that is readable
     * as per the conditions on the
     * {@link Image#getPixelReader() Image.getPixelReader()}
     * method.
     * If the {@code Image} is not readable, as determined by its
     * {@code getPixelReader()} method, then this method will return null.
     * If the {@code Image} is a writable, or other dynamic image, then
     * the {@code BufferedImage} will only be set to the current state of
     * the pixels in the image as determined by its {@link PixelReader}.
     * Further changes to the pixels of the {@code Image} will not be
     * reflected in the returned {@code BufferedImage}.
     * <p>
     * The optional {@code BufferedImage} parameter may be reused to store
     * the copy of the pixels.
     * A new {@code BufferedImage} will be created if the supplied object
     * is null, is too small or of a type which the image pixels cannot
     * be easily converted into.
     *
     * @param img the JavaFX {@code Image} to be converted
     * @param bimg an optional {@code BufferedImage} object that may be
     *        used to store the returned pixel data
     * @return a {@code BufferedImage} containing a snapshot of the JavaFX
     *         {@code Image}, or null if the {@code Image} is not readable.
     * @since JavaFX 2.2
     */
    public static BufferedImage fromFXImage(Image img, BufferedImage bimg) {
        PixelReader pr = img.getPixelReader();
        if (pr == null) {
            return null;
        }
        int iw = (int) img.getWidth();
        int ih = (int) img.getHeight();
        PixelFormat<?> fxFormat = pr.getPixelFormat();
        boolean srcPixelsAreOpaque = false;
        switch (fxFormat.getType()) {
            case INT_ARGB_PRE:
            case INT_ARGB:
            case BYTE_BGRA_PRE:
            case BYTE_BGRA:
                // Check fx image opacity only if
                // supplied BufferedImage is without alpha channel
                if (bimg != null &&
                        (bimg.getType() == BufferedImage.TYPE_INT_BGR ||
                                bimg.getType() == BufferedImage.TYPE_INT_RGB)) {
                    srcPixelsAreOpaque = checkFXImageOpaque(pr, iw, ih);
                }
                break;
            case BYTE_RGB:
                srcPixelsAreOpaque = true;
                break;
        }
        int prefBimgType = getBestBufferedImageType(pr.getPixelFormat(), bimg, srcPixelsAreOpaque);
        if (bimg != null) {
            int bw = bimg.getWidth();
            int bh = bimg.getHeight();
            if (bw < iw || bh < ih || bimg.getType() != prefBimgType) {
                bimg = null;
            } else if (iw < bw || ih < bh) {
                Graphics2D g2d = bimg.createGraphics();
                g2d.setComposite(AlphaComposite.Clear);
                g2d.fillRect(0, 0, bw, bh);
                g2d.dispose();
            }
        }
        if (bimg == null) {
            bimg = new BufferedImage(iw, ih, prefBimgType);
        }
        DataBufferInt db = (DataBufferInt)bimg.getRaster().getDataBuffer();
        int data[] = db.getData();
        int offset = bimg.getRaster().getDataBuffer().getOffset();
        int scan =  0;
        SampleModel sm = bimg.getRaster().getSampleModel();
        if (sm instanceof SinglePixelPackedSampleModel) {
            scan = ((SinglePixelPackedSampleModel)sm).getScanlineStride();
        }

        WritablePixelFormat<IntBuffer> pf = getAssociatedPixelFormat(bimg);
        pr.getPixels(0, 0, iw, ih, pf, data, offset, scan);
        return bimg;
    }
}
