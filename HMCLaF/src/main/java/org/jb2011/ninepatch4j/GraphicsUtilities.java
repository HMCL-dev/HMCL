/*
 * Decompiled with CFR 0_118.
 */
package org.jb2011.ninepatch4j;

import java.awt.Graphics;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.ImageObserver;
import java.awt.image.WritableRaster;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import javax.imageio.ImageIO;

public class GraphicsUtilities {
    public static BufferedImage loadCompatibleImage(URL resource) throws IOException {
        BufferedImage image = ImageIO.read(resource);
        return GraphicsUtilities.toCompatibleImage(image);
    }

    public static BufferedImage loadCompatibleImage(InputStream stream) throws IOException {
        BufferedImage image = ImageIO.read(stream);
        return GraphicsUtilities.toCompatibleImage(image);
    }

    public static BufferedImage createCompatibleImage(int width, int height) {
        return GraphicsUtilities.getGraphicsConfiguration().createCompatibleImage(width, height);
    }

    public static BufferedImage toCompatibleImage(BufferedImage image) {
        if (GraphicsUtilities.isHeadless()) {
            return image;
        }
        if (image.getColorModel().equals(GraphicsUtilities.getGraphicsConfiguration().getColorModel())) {
            return image;
        }
        BufferedImage compatibleImage = GraphicsUtilities.getGraphicsConfiguration().createCompatibleImage(image.getWidth(), image.getHeight(), image.getTransparency());
        Graphics g = compatibleImage.getGraphics();
        g.drawImage(image, 0, 0, null);
        g.dispose();
        return compatibleImage;
    }

    public static BufferedImage createCompatibleImage(BufferedImage image, int width, int height) {
        return GraphicsUtilities.getGraphicsConfiguration().createCompatibleImage(width, height, image.getTransparency());
    }

    private static GraphicsConfiguration getGraphicsConfiguration() {
        GraphicsEnvironment environment = GraphicsEnvironment.getLocalGraphicsEnvironment();
        return environment.getDefaultScreenDevice().getDefaultConfiguration();
    }

    private static boolean isHeadless() {
        return GraphicsEnvironment.isHeadless();
    }

    public static BufferedImage createTranslucentCompatibleImage(int width, int height) {
        return GraphicsUtilities.getGraphicsConfiguration().createCompatibleImage(width, height, 3);
    }

    public static int[] getPixels(BufferedImage img, int x, int y, int w, int h, int[] pixels) {
        if (w == 0 || h == 0) {
            return new int[0];
        }
        if (pixels == null) {
            pixels = new int[w * h];
        } else if (pixels.length < w * h) {
            throw new IllegalArgumentException("Pixels array must have a length >= w * h");
        }
        int imageType = img.getType();
        if (imageType == 2 || imageType == 1) {
            WritableRaster raster = img.getRaster();
            return (int[])raster.getDataElements(x, y, w, h, pixels);
        }
        return img.getRGB(x, y, w, h, pixels, 0, w);
    }
}

