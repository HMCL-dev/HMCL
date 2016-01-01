/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.jackhuang.hellominecraftlauncher.apis.utils;

import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.Window;
import java.net.URI;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;
import org.jackhuang.hellominecraftlauncher.apis.HMCLLog;

/**
 *
 * @author huang
 */
public class SwingUtils {

    public static void resetWindowLocation(Window c) {
        Dimension scrSize = Toolkit.getDefaultToolkit().getScreenSize();
        c.setLocation((scrSize.width - c.getWidth()) / 2,
                (scrSize.height - c.getHeight()) / 2);
    }

    public static DefaultTableModel makeDefaultTableModel(String[] titleA, final Class[] typesA, final boolean[] canEditA) {
        return new javax.swing.table.DefaultTableModel(
                new Object[][]{},
                titleA) {
                    Class[] types = typesA;
                    boolean[] canEdit = canEditA;

                    @Override
                    public Class getColumnClass(int columnIndex) {
                        return types[columnIndex];
                    }

                    @Override
                    public boolean isCellEditable(int rowIndex, int columnIndex) {
                        return canEdit[columnIndex];
                    }
                };
    }

    public static void openLink(URI link) {
        try {
            java.awt.Desktop.getDesktop().browse(link);
        } catch (Throwable e) {
            HMCLLog.warn("Failed to open link: " + link, e);
        }
    }
    
    public static void moveEnd(JTextArea tf) {
        int position = tf.getText().length();
        tf.setCaretPosition(position);
    }
    
}
