/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2025 huangyuhui <huanghongxun2008@126.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
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
