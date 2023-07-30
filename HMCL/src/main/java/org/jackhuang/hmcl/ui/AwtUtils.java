package org.jackhuang.hmcl.ui;

import java.awt.*;
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
}
