/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2013  huangyuhui <huanghongxun2008@126.com>
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
 * along with this program.  If not, see {http://www.gnu.org/licenses/}.
 */
package org.jackhuang.hellominecraft.utils.views;

import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.awt.image.ColorModel;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;

public abstract class AbstractFilter
    implements BufferedImageOp {

    @Override
    public abstract BufferedImage filter(BufferedImage paramBufferedImage1, BufferedImage paramBufferedImage2);

    @Override
    public Rectangle2D getBounds2D(BufferedImage src) {
        return new Rectangle(0, 0, src.getWidth(), src.getHeight());
    }

    @Override
    public BufferedImage createCompatibleDestImage(BufferedImage src, ColorModel destCM) {
        if (destCM == null)
            destCM = src.getColorModel();

        return new BufferedImage(destCM, destCM.createCompatibleWritableRaster(src.getWidth(), src.getHeight()), destCM.isAlphaPremultiplied(), null);
    }

    @Override
    public Point2D getPoint2D(Point2D srcPt, Point2D dstPt) {
        return (Point2D) srcPt.clone();
    }

    @Override
    public RenderingHints getRenderingHints() {
        return null;
    }

    protected int[] getPixels(BufferedImage img, int x, int y, int w, int h, int[] pixels) {
        if ((w == 0) || (h == 0))
            return new int[0];

        if (pixels == null)
            pixels = new int[w * h];
        else if (pixels.length < w * h)
            throw new IllegalArgumentException("pixels array must have a length >= w*h");

        int imageType = img.getType();
        if ((imageType == 2) || (imageType == 1)) {
            Raster raster = img.getRaster();
            return (int[]) (int[]) raster.getDataElements(x, y, w, h, pixels);
        }

        return img.getRGB(x, y, w, h, pixels, 0, w);
    }

    protected void setPixels(BufferedImage img, int x, int y, int w, int h, int[] pixels) {
        if ((pixels == null) || (w == 0) || (h == 0))
            return;
        if (pixels.length < w * h)
            throw new IllegalArgumentException("pixels array must have a length >= w*h");

        int imageType = img.getType();
        if ((imageType == 2) || (imageType == 1)) {
            WritableRaster raster = img.getRaster();
            raster.setDataElements(x, y, w, h, pixels);
        } else
            img.setRGB(x, y, w, h, pixels, 0, w);
    }
}
