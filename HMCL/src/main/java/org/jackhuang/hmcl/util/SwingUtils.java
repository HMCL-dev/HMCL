package org.jackhuang.hmcl.util;

import javax.swing.*;

public final class SwingUtils {
    static {
        if (System.getProperty("swing.defaultlaf") == null) {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Throwable ignored) {
            }
        }
    }

    private SwingUtils() {
    }

    public static void initLookAndFeel() {
        // Make sure the static constructor is called
    }

    public static void showInfoDialog(Object message) {
        JOptionPane.showMessageDialog(null, message, "Info", JOptionPane.INFORMATION_MESSAGE);
    }

    public static void showWarningDialog(Object message) {
        JOptionPane.showMessageDialog(null, message, "Warning", JOptionPane.WARNING_MESSAGE);
    }

    public static void showErrorDialog(Object message) {
        JOptionPane.showMessageDialog(null, message, "Error", JOptionPane.ERROR_MESSAGE);
    }
}
