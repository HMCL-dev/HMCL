package org.jackhuang.hellominecraft.util.ui;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
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

}
