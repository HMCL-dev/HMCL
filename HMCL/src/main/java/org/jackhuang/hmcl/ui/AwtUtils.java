package org.jackhuang.hmcl.ui;

import javafx.scene.image.PixelReader;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.lang.reflect.Method;

public final class AwtUtils {
    private AwtUtils() {
    }

    public static void setAppleIcon(Image image) {
        try {
            Class<?> taskbarClass = Class.forName("java.awt.Taskbar");
            Method getTaskBarMethod = taskbarClass.getDeclaredMethod("getTaskbar");
            Object taskBar = getTaskBarMethod.invoke(null);
            Method setIconImageMethod = taskbarClass.getDeclaredMethod("setIconImage", Image.class);
            setIconImageMethod.invoke(taskBar, image);
        } catch (Throwable ignore) {
        }
    }

    public static Image getImage(String url) {
        javafx.scene.image.Image javafxImage = new javafx.scene.image.Image(url);

        BufferedImage awtImage = new BufferedImage((int) javafxImage.getWidth(), (int) javafxImage.getHeight(), BufferedImage.TYPE_INT_ARGB);

        PixelReader pixelReader = javafxImage.getPixelReader();
        WritableRaster writableRaster = awtImage.getRaster();

        byte[] rgba = new byte[4];
        for (int y = 0; y < (int) javafxImage.getHeight(); y++) {
            for (int x = 0; x < (int) javafxImage.getWidth(); x++) {
                int argb = pixelReader.getArgb(x, y);

                rgba[0] = (byte) (argb >> 16); // R
                rgba[1] = (byte) (argb >> 8); // G
                rgba[2] = (byte) argb; // B
                rgba[3] = (byte) (argb >> 24); // A

                writableRaster.setDataElements(x, y, rgba);
            }
        }

        return awtImage;
    }
}
