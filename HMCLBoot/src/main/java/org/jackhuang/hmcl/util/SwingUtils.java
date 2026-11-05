// SPDX-FileCopyrightText: Copyright (C) 2025 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util;

import javax.swing.*;

/**
 * @author Glavo
 */
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
        showInfoDialog(message, "Info");
    }

    public static void showInfoDialog(Object message, String title) {
        JOptionPane.showMessageDialog(null, message, title, JOptionPane.INFORMATION_MESSAGE);
    }

    public static void showWarningDialog(Object message) {
        showWarningDialog(message, "Warning");
    }

    public static void showWarningDialog(Object message, String title) {
        JOptionPane.showMessageDialog(null, message, title, JOptionPane.WARNING_MESSAGE);
    }

    public static void showErrorDialog(Object message) {
        showErrorDialog(message, "Error");
    }

    public static void showErrorDialog(Object message, String title) {
        JOptionPane.showMessageDialog(null, message, title, JOptionPane.ERROR_MESSAGE);
    }
}
