/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.jackhuang.hellominecraft.utils;

import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.Window;
import java.net.URI;
import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.table.DefaultTableModel;
import org.jackhuang.hellominecraft.HMCLog;

/**
 *
 * @author huang
 */
public class SwingUtils {

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
            HMCLog.warn("Failed to open link: " + link, e);
        }
    }
    
    public static void moveEnd(JTextArea tf) {
        int position = tf.getText().length();
        tf.setCaretPosition(position);
    }
    
    public static void moveEnd(JScrollPane pane) {
        JScrollBar bar = pane.getVerticalScrollBar();
        bar.setValue(bar.getMaximum());
    }
    
    public static DefaultListModel getDefaultListModel(JList list) {
        return (DefaultListModel)list.getModel();
    }
    
    public static void appendLast(JList list, Object element) {
        getDefaultListModel(list).addElement(element);
    }
    
    public static void replaceLast(JList list, Object element) {
        DefaultListModel model = getDefaultListModel(list);
        model.set(model.getSize()-1, element);
    }
    
    public static void clear(JList list) {
        list.setModel(new DefaultListModel());
    }
    
    public static void clearDefaultTable(JTable table) {
        DefaultTableModel model = (DefaultTableModel)table.getModel();
        while(model.getRowCount() > 0) {
            model.removeRow(0);
        }
        table.updateUI();
    }
    
}
