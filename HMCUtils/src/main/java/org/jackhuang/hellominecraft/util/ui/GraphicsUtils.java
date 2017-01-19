package org.jackhuang.hellominecraft.util.ui;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.swing.plaf.synth.SynthContext;
import javax.swing.plaf.synth.SynthGraphicsUtils;

/**
 * NimbusGraphicsUtils - extends SynthGraphicsUtils to force all Synth painted
 * text to be antialiased and provides some static helper methods.
 *
 * @author Created by Jasper Potts (Jan 4, 2007)
 * @version 1.0
 */
public class GraphicsUtils extends SynthGraphicsUtils {

    private Map<?, ?> desktopHints;

    /**
     * Get rendering hints from a Graphics instance. "hintsToSave" is a Map of
     * RenderingHint key-values. For each hint key present in that map, the
     * value of that hint is obtained from the Graphics and stored as the value
     * for the key in savedHints.
     *
     * @param g2d         the graphics surface
     * @param hintsToSave the list of rendering hints to set on the graphics
     * @param savedHints  a set where to save the previous rendering hints,
     *                    might
     *                    be null
     *
     * @return the previous set of rendering hints
     */
    public static RenderingHints getRenderingHints(Graphics2D g2d,
                                                   Map<?, ?> hintsToSave,
                                                   RenderingHints savedHints) {
        if (savedHints == null)
            savedHints = new RenderingHints(null);
        else
            savedHints.clear();
        if (hintsToSave.isEmpty())
            return savedHints;
        /* RenderingHints.keySet() returns Set */
        for (Object o : hintsToSave.keySet()) {
            RenderingHints.Key key = (RenderingHints.Key) o;
            Object value = g2d.getRenderingHint(key);
            savedHints.put(key, value);
        }
        return savedHints;
    }

    /**
     * Overrides paintText in SynthGraphicsUtils to force all Synth painted text
     * to be antialiased
     */
    @Override
    public void paintText(SynthContext ss, Graphics g, String text, int x, int y, int mnemonicIndex) {
        Graphics2D g2 = (Graphics2D) g;

        // XXX: In Java SE 6, Synth already uses the desktop hints, this code should just check whether java.version < 1.6
        if (desktopHints == null) {
            Toolkit toolkit = Toolkit.getDefaultToolkit();
            desktopHints = (Map<?, ?>) (toolkit.getDesktopProperty("awt.font.desktophints"));
        }

        Object oldAA = null;
        RenderingHints oldHints = null;

        if (desktopHints != null) {
            oldHints = getRenderingHints(g2, desktopHints, null);
            g2.addRenderingHints(desktopHints);
        } else {
            oldAA = g2.getRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        }

        super.paintText(ss, g, text, x, y, mnemonicIndex);

        if (oldHints != null)
            g2.addRenderingHints(oldHints);
        else if (oldAA != null)
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                                oldAA);
    }

    /**
     * Load an image using ImageIO from resource in
     * org.jdesktop.swingx.plaf.nimbus.images. Catches and prints all Exceptions
     * so that it can safely be used in a static context.
     *
     * @param imgName The name of the image to load, eg. "border.png"
     *
     * @return The loaded image
     */
    public static BufferedImage loadImage(String imgName) {
        try {
            return ImageIO.read(GraphicsUtils.class.getClassLoader().getResource("org/jackhuang/hellominecraft/lookandfeel/images/" + imgName));
        } catch (Exception e) {
            System.err.println("Error loading image \"org/jackhuang/hellominecraft/lookandfeel/images/" + imgName + "\"");
            e.printStackTrace();
        }
        return null;
    }

    public static String getColor(Color c) {
        return Integer.toHexString(c.getRGB() & 0xFFFFFF);
    }

    /**
     * Get a Color object from a web color string of the form "FF00AB" or
     * "#FF00AB".
     *
     * @param c The color string
     *
     * @return The Color described
     */
    public static Color getWebColor(String c) {
        if (c.startsWith("#"))
            c = c.substring(1);
        return new Color(
            Integer.parseInt(c.substring(0, 2), 16),
            Integer.parseInt(c.substring(2, 4), 16),
            Integer.parseInt(c.substring(4, 6), 16)
        );
    }

    /**
     * Get a Color object from a web color string of the form "FF00AB" or
     * "#FF00AB".
     *
     * @param c The color string
     *
     * @return The Color described
     */
    public static Color getWebColorWithAlpha(String c) {
        if (c.startsWith("#"))
            c = c.substring(1);
        return new Color(
            Integer.parseInt(c.substring(0, 2), 16),
            Integer.parseInt(c.substring(2, 4), 16),
            Integer.parseInt(c.substring(4, 6), 16),
            Integer.parseInt(c.substring(6, 8), 16)
        );
    }

    /**
     * Get a Color that is 50% inbetween the two web colors given. The Web
     * colors are of the form "FF00AB" or "#FF00AB".
     *
     * @param c1 The first color string
     * @param c2 The second color string
     *
     * @return The Color middle color
     */
    public static Color getMidWebColor(String c1, String c2) {
        return getMidWebColor(c1, c2, 50);
    }

    /**
     * Get a Color that is 50% inbetween the two web colors given. The Web
     * colors are of the form "FF00AB" or "#FF00AB".
     *
     * @param c1 The first color string
     * @param c2 The second color string
     *
     * @return The Color middle color
     */
    public static Color getMidWebColor(String c1, String c2, int percent) {
        if (c1.startsWith("#"))
            c1 = c1.substring(1);
        if (c2.startsWith("#"))
            c2 = c2.substring(1);
        int rTop = Integer.parseInt(c1.substring(0, 2), 16);
        int gTop = Integer.parseInt(c1.substring(2, 4), 16);
        int bTop = Integer.parseInt(c1.substring(4, 6), 16);
        int rBot = Integer.parseInt(c2.substring(0, 2), 16);
        int gBot = Integer.parseInt(c2.substring(2, 4), 16);
        int bBot = Integer.parseInt(c2.substring(4, 6), 16);
        int rMid = rTop + ((rBot - rTop) * percent / 100);
        int gMid = gTop + ((gBot - gTop) * percent / 100);
        int bMid = bTop + ((bBot - bTop) * percent / 100);
        return new Color(rMid, gMid, bMid);
    }

    public static Color getMidWebColor(Color c1, Color c2, int percent) {
        int rTop = c1.getRed();
        int gTop = c1.getGreen();
        int bTop = c1.getBlue();
        int aTop = c1.getAlpha();
        int rBot = c2.getRed();
        int gBot = c2.getGreen();
        int bBot = c2.getBlue();
        int aBot = c2.getAlpha();
        int rMid = rTop + ((rBot - rTop) * percent / 100);
        int gMid = gTop + ((gBot - gTop) * percent / 100);
        int bMid = bTop + ((bBot - bTop) * percent / 100);
        int aMid = aTop + ((aBot - aTop) * percent / 100);
        return new Color(rMid, gMid, bMid, aMid);
    }
    
    /**
     * <p>Returns an array of pixels, stored as integers, from a
     * <code>BufferedImage</code>. The pixels are grabbed from a rectangular
     * area defined by a location and two dimensions. Calling this method on
     * an image of type different from <code>BufferedImage.TYPE_INT_ARGB</code>
     * and <code>BufferedImage.TYPE_INT_RGB</code> will unmanage the image.</p>
     *
     * @param img the source image
     * @param x the x location at which to start grabbing pixels
     * @param y the y location at which to start grabbing pixels
     * @param w the width of the rectangle of pixels to grab
     * @param h the height of the rectangle of pixels to grab
     * @param pixels a pre-allocated array of pixels of size w*h; can be null
     * @return <code>pixels</code> if non-null, a new array of integers
     *   otherwise
     * @throws IllegalArgumentException is <code>pixels</code> is non-null and
     *   of length &lt; w*h
     */
    public static int[] getPixels(BufferedImage img,
                                  int x, int y, int w, int h, int[] pixels) {
        if (w == 0 || h == 0) {
            return new int[0];
        }

        if (pixels == null) {
            pixels = new int[w * h];
        } else if (pixels.length < w * h) {
            throw new IllegalArgumentException("pixels array must have a length" +
                                               " >= w*h");
        }

        int imageType = img.getType();
        if (imageType == BufferedImage.TYPE_INT_ARGB ||
            imageType == BufferedImage.TYPE_INT_RGB) {
            Raster raster = img.getRaster();
            return (int[]) raster.getDataElements(x, y, w, h, pixels);
        }

        // Unmanages the image
        return img.getRGB(x, y, w, h, pixels, 0, w);
    }

    /**
     * <p>Writes a rectangular area of pixels in the destination
     * <code>BufferedImage</code>. Calling this method on
     * an image of type different from <code>BufferedImage.TYPE_INT_ARGB</code>
     * and <code>BufferedImage.TYPE_INT_RGB</code> will unmanage the image.</p>
     *
     * @param img the destination image
     * @param x the x location at which to start storing pixels
     * @param y the y location at which to start storing pixels
     * @param w the width of the rectangle of pixels to store
     * @param h the height of the rectangle of pixels to store
     * @param pixels an array of pixels, stored as integers
     * @throws IllegalArgumentException is <code>pixels</code> is non-null and
     *   of length &lt; w*h
     */
    public static void setPixels(BufferedImage img,
                                 int x, int y, int w, int h, int[] pixels) {
        if (pixels == null || w == 0 || h == 0) {
            return;
        } else if (pixels.length < w * h) {
            throw new IllegalArgumentException("pixels array must have a length" +
                                               " >= w*h");
        }

        int imageType = img.getType();
        if (imageType == BufferedImage.TYPE_INT_ARGB ||
            imageType == BufferedImage.TYPE_INT_RGB) {
            WritableRaster raster = img.getRaster();
            raster.setDataElements(x, y, w, h, pixels);
        } else {
            // Unmanages the image
            img.setRGB(x, y, w, h, pixels, 0, w);
        }
    }
}
