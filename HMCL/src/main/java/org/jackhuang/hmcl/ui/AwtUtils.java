package org.jackhuang.hmcl.ui;

import net.burningtnt.webp.awt.AWTImageLoader;
import org.jackhuang.hmcl.util.ResourceNotFoundError;

import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
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

    public static Image loadBuiltinWebpImage(String url) {
        try (InputStream inputStream = ResourceNotFoundError.getResourceAsStream(url)) {
            return AWTImageLoader.decode(inputStream);
        } catch (IOException e) {
            throw new ResourceNotFoundError("Cannot load builtin resource '" + url + "'.", e);
        }
    }
}
