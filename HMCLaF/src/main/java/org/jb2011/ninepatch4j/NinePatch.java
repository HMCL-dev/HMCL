/*
 * Decompiled with CFR 0_118.
 */
package org.jb2011.ninepatch4j;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;

public class NinePatch {

    public static final String EXTENSION_9PATCH = ".9.png";
    private final BufferedImage mImage;
    private final NinePatchChunk mChunk;

    public BufferedImage getImage() {
        return this.mImage;
    }

    public NinePatchChunk getChunk() {
        return this.mChunk;
    }

    public static NinePatch load(URL fileUrl, boolean convert) throws IOException {
        try {
            boolean is9Patch = fileUrl.getPath().toLowerCase().endsWith(".9.png");
            return NinePatch.load(GraphicsUtilities.loadCompatibleImage(fileUrl), is9Patch, convert);
        } catch (MalformedURLException e) {
            return null;
        }
    }

    public static NinePatch load(InputStream stream, boolean is9Patch, boolean convert) throws IOException {
        try {
            return NinePatch.load(GraphicsUtilities.loadCompatibleImage(stream), is9Patch, convert);
        } catch (MalformedURLException e) {
            return null;
        }
    }

    /*
     * Enabled force condition propagation
     * Lifted jumps to return sites
     */
    public static NinePatch load(BufferedImage image, boolean is9Patch, boolean convert) {
        if (!is9Patch) {
            if (!convert)
                return null;
            image = NinePatch.convertTo9Patch(image);
            return new NinePatch(image);
        } else
            NinePatch.ensure9Patch(image);
        return new NinePatch(image);
    }

    public int getWidth() {
        return this.mImage.getWidth();
    }

    public int getHeight() {
        return this.mImage.getHeight();
    }

    public boolean getPadding(int[] padding) {
        this.mChunk.getPadding(padding);
        return true;
    }

    public void draw(Graphics2D graphics2D, int x, int y, int scaledWidth, int scaledHeight) {
        this.mChunk.draw(this.mImage, graphics2D, x, y, scaledWidth, scaledHeight, 0, 0);
    }

    private NinePatch(BufferedImage image) {
        this.mChunk = NinePatchChunk.create(image);
        this.mImage = this.extractBitmapContent(image);
    }

    private static void ensure9Patch(BufferedImage image) {
        int pixel;
        int width = image.getWidth();
        int height = image.getHeight();
        int i = 0;
        while (i < width) {
            pixel = image.getRGB(i, 0);
            if (pixel != 0 && pixel != -16777216)
                image.setRGB(i, 0, 0);
            if ((pixel = image.getRGB(i, height - 1)) != 0 && pixel != -16777216)
                image.setRGB(i, height - 1, 0);
            ++i;
        }
        i = 0;
        while (i < height) {
            pixel = image.getRGB(0, i);
            if (pixel != 0 && pixel != -16777216)
                image.setRGB(0, i, 0);
            if ((pixel = image.getRGB(width - 1, i)) != 0 && pixel != -16777216)
                image.setRGB(width - 1, i, 0);
            ++i;
        }
    }

    private static BufferedImage convertTo9Patch(BufferedImage image) {
        BufferedImage buffer = GraphicsUtilities.createTranslucentCompatibleImage(image.getWidth() + 2, image.getHeight() + 2);
        Graphics2D g2 = buffer.createGraphics();
        g2.drawImage(image, 1, 1, null);
        g2.setColor(Color.black);
        g2.fillRect(0, 1, 1, 1);
        g2.fillRect(1, 0, 1, 1);
        g2.fillRect(1, image.getHeight() + 1, 1, 1);
        g2.fillRect(image.getWidth() + 1, 1, 1, 1);
        g2.dispose();
        return buffer;
    }

    private BufferedImage extractBitmapContent(BufferedImage image) {
        return image.getSubimage(1, 1, image.getWidth() - 2, image.getHeight() - 2);
    }
}
