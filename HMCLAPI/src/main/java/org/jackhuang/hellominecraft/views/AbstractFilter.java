/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraft.views;

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
  implements BufferedImageOp
{
  public abstract BufferedImage filter(BufferedImage paramBufferedImage1, BufferedImage paramBufferedImage2);

  public Rectangle2D getBounds2D(BufferedImage src)
  {
    return new Rectangle(0, 0, src.getWidth(), src.getHeight());
  }

  public BufferedImage createCompatibleDestImage(BufferedImage src, ColorModel destCM)
  {
    if (destCM == null) {
      destCM = src.getColorModel();
    }

    return new BufferedImage(destCM, destCM.createCompatibleWritableRaster(src.getWidth(), src.getHeight()), destCM.isAlphaPremultiplied(), null);
  }

  public Point2D getPoint2D(Point2D srcPt, Point2D dstPt)
  {
    return (Point2D)srcPt.clone();
  }

  public RenderingHints getRenderingHints()
  {
    return null;
  }

  protected int[] getPixels(BufferedImage img, int x, int y, int w, int h, int[] pixels)
  {
    if ((w == 0) || (h == 0)) {
      return new int[0];
    }

    if (pixels == null)
      pixels = new int[w * h];
    else if (pixels.length < w * h) {
      throw new IllegalArgumentException("pixels array must have a length >= w*h");
    }

    int imageType = img.getType();
    if ((imageType == 2) || (imageType == 1))
    {
      Raster raster = img.getRaster();
      return (int[])(int[])raster.getDataElements(x, y, w, h, pixels);
    }

    return img.getRGB(x, y, w, h, pixels, 0, w);
  }

  protected void setPixels(BufferedImage img, int x, int y, int w, int h, int[] pixels)
  {
    if ((pixels == null) || (w == 0) || (h == 0))
      return;
    if (pixels.length < w * h) {
      throw new IllegalArgumentException("pixels array must have a length >= w*h");
    }

    int imageType = img.getType();
    if ((imageType == 2) || (imageType == 1))
    {
      WritableRaster raster = img.getRaster();
      raster.setDataElements(x, y, w, h, pixels);
    }
    else {
      img.setRGB(x, y, w, h, pixels, 0, w);
    }
  }
}
