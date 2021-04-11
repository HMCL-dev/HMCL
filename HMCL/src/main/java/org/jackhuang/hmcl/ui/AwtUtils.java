package org.jackhuang.hmcl.ui;

import java.awt.*;
import java.lang.reflect.Method;

public class AwtUtils {
    public static void setAppleIcon(Image image) {
        try {
            Class<?> taskbarClass = Class.forName("java.awt.TaskBar");
            Method getTaskBarMethod = taskbarClass.getDeclaredMethod("getTaskBar");
            Object taskBar = getTaskBarMethod.invoke(null);
            Method setIconImageMethod = taskbarClass.getDeclaredMethod("setIconImage", Image.class);
            setIconImageMethod.invoke(taskBar, image);
        } catch (Throwable ignore) {
        }
    }
}
