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
package org.jackhuang.hellominecraft.launcher.views;

import java.awt.EventQueue;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import javax.swing.BorderFactory;
import javax.swing.DefaultButtonModel;
import javax.swing.JLabel;

/**
 *
 * @author hyh
 */
public class HeaderTab extends JLabel
        implements MouseListener {

    private boolean isActive;
    private final DefaultButtonModel model;

    public HeaderTab(String text) {
        super(text);

        this.model = new DefaultButtonModel();
        setIsActive(false);

        setBorder(BorderFactory.createEmptyBorder(6, 18, 7, 18));
        addMouseListener(this);
    }

    public boolean isActive() {
        return this.isActive;
    }

    public void setIsActive(boolean isActive) {
        this.isActive = isActive;
        setOpaque(isActive);

        EventQueue.invokeLater(HeaderTab.this::repaint);
    }

    public void addActionListener(ActionListener listener) {
        this.model.addActionListener(listener);
    }

    public String getActionCommand() {
        return this.model.getActionCommand();
    }

    public ActionListener[] getActionListeners() {
        return this.model.getActionListeners();
    }

    public void removeActionListener(ActionListener listener) {
        this.model.removeActionListener(listener);
    }

    public void setActionCommand(String command) {
        this.model.setActionCommand(command);
    }

    @Override
    public void mouseClicked(MouseEvent e) {
    }

    @Override
    public void mousePressed(MouseEvent e) {
        this.model.setPressed(true);
        this.model.setArmed(true);
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        this.model.setPressed(false);
        this.model.setArmed(false);
    }

    @Override
    public void mouseEntered(MouseEvent e) {
        this.model.setRollover(true);
    }

    @Override
    public void mouseExited(MouseEvent e) {
        this.model.setRollover(false);
    }
}
