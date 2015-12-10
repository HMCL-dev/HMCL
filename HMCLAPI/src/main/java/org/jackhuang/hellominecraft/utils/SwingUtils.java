/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2013  huangyuhui <huanghongxun2008@126.com>
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
 * along with this program.  If not, see {http://www.gnu.org/licenses/}.
 */
package org.jackhuang.hellominecraft.utils;

import java.awt.FontMetrics;
import java.net.URI;
import javax.swing.DefaultListModel;
import javax.swing.JLabel;
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

    /**
     * Make DefaultTableModel by overriding getColumnClass and isCellEditable of
     * DefaultTableModel.
     *
     * @param titleA   The title of each column.
     * @param typesA   The type of each column value.
     * @param canEditA Is column editable?
     *
     * @return
     */
    public static DefaultTableModel makeDefaultTableModel(String[] titleA, final Class[] typesA, final boolean[] canEditA) {
        return new javax.swing.table.DefaultTableModel(
        new Object[][] {},
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

    /**
     * Open URL by java.awt.Desktop
     *
     * @param link
     */
    public static void openLink(URI link) {
        try {
            java.awt.Desktop.getDesktop().browse(link);
        } catch (Throwable e) {
            HMCLog.warn("Failed to open link: " + link, e);
        }
    }

    /**
     * Move the cursor to the end of TextArea.
     *
     * @param tf the TextArea
     */
    public static void moveEnd(JTextArea tf) {
        int position = tf.getText().length();
        tf.setCaretPosition(position);
    }

    /**
     * Move the cursor to the end of ScrollPane.
     *
     * @param pane the ScrollPane
     */
    public static void moveEnd(JScrollPane pane) {
        JScrollBar bar = pane.getVerticalScrollBar();
        bar.setValue(bar.getMaximum());
    }

    /**
     * Get the DefaultListModel from JList.
     *
     * @param list
     *
     * @return Forcely Type casted to DefaultListModel
     */
    public static DefaultListModel getDefaultListModel(JList list) {
        return (DefaultListModel) list.getModel();
    }

    /**
     * Append new element to JList
     *
     * @param list    the JList
     * @param element the Element
     */
    public static void appendLast(JList list, Object element) {
        getDefaultListModel(list).addElement(element);
    }

    public static void replaceLast(JList list, Object element) {
        DefaultListModel model = getDefaultListModel(list);
        model.set(model.getSize() - 1, element);
    }

    public static void clear(JList list) {
        list.setModel(new DefaultListModel());
    }

    /**
     * Clear the JTable
     *
     * @param table JTable with DefaultTableModel.
     * 
     * @return To make the code succinct
     */
    public static DefaultTableModel clearDefaultTable(JTable table) {
        DefaultTableModel model = (DefaultTableModel) table.getModel();
        while (model.getRowCount() > 0)
            model.removeRow(0);
        table.updateUI();
        return model;
    }

    public static void appendLast(JTable table, Object... elements) {
        DefaultTableModel model = (DefaultTableModel) table.getModel();
        model.addRow(elements);
    }

    public static void setValueAt(JTable table, Object element, int row, int col) {
        DefaultTableModel model = (DefaultTableModel) table.getModel();
        model.setValueAt(element, row, col);
    }

    public static void removeRow(JTable table, int row) {
        DefaultTableModel model = (DefaultTableModel) table.getModel();
        model.removeRow(row);
    }

    public static String getParsedJPanelText(JLabel jLabel1, String longString) {
        if (StrUtils.isBlank(longString))
            return longString;
        StringBuilder builder = new StringBuilder();
        char[] chars = longString.toCharArray();
        FontMetrics fontMetrics = jLabel1.getFontMetrics(jLabel1.getFont());
        for (int beginIndex = 0, limit = 1;; limit++) {
            if (beginIndex + limit > chars.length)
                break;
            if (fontMetrics.charsWidth(chars, beginIndex, limit) < jLabel1.getWidth()) {
                if (beginIndex + limit < chars.length)
                    continue;
                builder.append(chars, beginIndex, limit);
                break;
            }
            builder.append(chars, beginIndex, limit - 1).append("<br/>");
            beginIndex += limit - 1;
            limit = 1;
        }
        return builder.toString();
    }
}
