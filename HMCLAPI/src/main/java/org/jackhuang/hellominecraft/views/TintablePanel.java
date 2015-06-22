/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraft.views;

/**
 *
 * @author huangyuhui
 */
import java.awt.Color;
import java.awt.EventQueue;
import java.awt.Graphics;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;

public class TintablePanel extends JPanel {

    private Color tintColor;
    private boolean tintActive;
    private JLabel overIcon = null;

    public TintablePanel() {
        this.tintColor = new Color(0, 0, 0, 0);
    }

    public Color getTintColor() {
        return this.tintColor;
    }

    public void setTintColor(Color color) {
        this.tintColor = color;
    }

    public void setOverIcon(ImageIcon image) {
        if (this.overIcon != null) {
            remove(this.overIcon);
        }

        this.overIcon = new JLabel(image);
        this.overIcon.setVisible(false);
        add(this.overIcon);
        revalidate();
    }

    public boolean isTintActive() {
        return this.tintActive;
    }

    public void setTintActive(boolean tintActive) {
        this.tintActive = tintActive;

        if (this.overIcon != null) {
            this.overIcon.setVisible(tintActive);
            EventQueue.invokeLater(new Runnable() {
                @Override
                public void run() {
                    TintablePanel.this.revalidate();
                }
            });
        }
        EventQueue.invokeLater(new Runnable() {
            @Override
            public void run() {
                TintablePanel.this.repaint();
            }
        });
    }

    @Override
    public void doLayout() {
        super.doLayout();

        if (this.overIcon != null) {
            int width = this.overIcon.getIcon().getIconWidth();
            int height = this.overIcon.getIcon().getIconHeight();
            this.overIcon.setBounds(getWidth() / 2 - width / 2, getHeight() / 2 - height / 2, width, height);
        }
    }

    @Override
    public void paint(Graphics graphics) {
        super.paint(graphics);

        if (this.tintActive) {
            graphics.setColor(getTintColor());
            graphics.fillRect(0, 0, getWidth(), getHeight());
        }
    }
}
