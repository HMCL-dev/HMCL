/*
 * Copyright 2013 huangyuhui <huanghongxun2008@126.com>
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.
 */
package org.jackhuang.hellominecraft.utils;

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
