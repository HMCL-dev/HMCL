/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraft.launcher.views;

import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import javax.swing.JFrame;

/**
 *
 * @author hyh
 */
public class DraggableFrame extends JFrame
        implements MouseListener, MouseMotionListener {

    private int dragGripX;
    private int dragGripY;

    public DraggableFrame() {
        setUndecorated(true);
        addMouseListener(this);
        addMouseMotionListener(this);
    }

    @Override
    public void mouseClicked(MouseEvent e) {
    }

    @Override
    public void mousePressed(MouseEvent e) {
        if (e.getButton() == 1) {
            this.dragGripX = e.getX();
            this.dragGripY = e.getY();
        }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
    }

    @Override
    public void mouseEntered(MouseEvent e) {
    }

    @Override
    public void mouseExited(MouseEvent e) {
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        if ((e.getModifiersEx() & 0x400) != 0) {
            setLocation(e.getXOnScreen() - this.dragGripX, e.getYOnScreen() - this.dragGripY);
        }
    }

    @Override
    public void mouseMoved(MouseEvent e) {
    }
}
