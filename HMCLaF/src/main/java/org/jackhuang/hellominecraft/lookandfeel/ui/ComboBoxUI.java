package org.jackhuang.hellominecraft.lookandfeel.ui;

import static org.jackhuang.hellominecraft.util.ui.GraphicsUtils.loadImage;

import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.JComponent;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.ListCellRenderer;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Container;
import java.awt.image.BufferedImage;
import java.awt.event.MouseListener;
import java.awt.event.MouseEvent;
import javax.swing.plaf.basic.BasicComboPopup;
import javax.swing.plaf.basic.ComboPopup;

/**
 * NimbusComboBoxUI
 *
 * @author Created by Jasper Potts (Feb 1, 2007)
 * @version 1.0
 */
public class ComboBoxUI extends BasicComboBoxUI implements MouseListener {

    private static final BufferedImage COMBO_NORMAL = loadImage("combo_normal.png");
    private static final BufferedImage COMBO_OVER = loadImage("combo_over.png");
    private static final BufferedImage COMBO_PRESSED = loadImage("combo_pressed.png");
    private static final BufferedImage COMBO_DISABLED = loadImage("combo_disabled.png");
    private static final Dimension BTN_SIZE = new Dimension(17, 20);
    private final Dimension btnSize = new Dimension(BTN_SIZE);

    /**
     * Creates a new UI deligate for the given component. It is a standard
     * method that all UI deligates must have.
     *
     * @param c The component that the UI is for
     *
     * @return a new instance of NimbusComboBoxUI
     */
    public static ComponentUI createUI(JComponent c) {
        return new ComboBoxUI();
    }

    @Override
    public void installUI(JComponent c) {
        super.installUI(c);
        c.setOpaque(false);
    }

    @Override
    protected void installListeners() {
        super.installListeners();
        comboBox.addMouseListener(this);
    }

    @Override
    protected void uninstallListeners() {
        super.uninstallListeners();
        comboBox.removeMouseListener(this);
    }

    /**
     * The minumum size is the size of the display area plus insets plus the
     * button.
     *
     * @return the size yeah.
     */
    @Override
    public Dimension getMinimumSize(JComponent c) {
        if (!isMinimumSizeDirty)
            return new Dimension(cachedMinimumSize);
        Dimension size = getDisplaySize();
        Insets insets = getInsets();
        btnSize.height = size.height = Math.max(size.height, BTN_SIZE.height);
        btnSize.width = (int) ((double) (BTN_SIZE.width / (double) BTN_SIZE.height) * btnSize.height);
        size.height += insets.top + insets.bottom;
        size.width += insets.left + insets.right + btnSize.width;

        cachedMinimumSize.setSize(size.width, size.height);
        isMinimumSizeDirty = false;

        return new Dimension(size);
    }

    @Override
    protected ComboPopup createPopup() {
        BasicComboPopup p = new BasicComboPopup(comboBox);
        //p.setPopupSize(100, comboBox.getPreferredSize().height);
        return p;
    }

    @Override
    protected JButton createArrowButton() {
        JButton button = new JButton() {
            @Override
            protected void paintComponent(Graphics g) {
                if (comboBox.isEditable()) {
                    BufferedImage img = COMBO_NORMAL;
                    if (mouseDown)
                        img = COMBO_PRESSED;
                    else if (!comboBox.isEnabled())
                        img = COMBO_NORMAL;
                    else if (mouseInside)
                        img = COMBO_OVER;
                    g.drawImage(img,
                            0, 0, getWidth(), getHeight(),
                            0, 0, img.getWidth(), img.getHeight(), comboBox);
                }
            }
        };
        button.addMouseListener(this);
        button.setMinimumSize(BTN_SIZE);
        button.setPreferredSize(BTN_SIZE);
        button.setMargin(new Insets(0, 0, 0, 0));
        return button;
    }

    @Override
    public void paint(Graphics g, JComponent c) {
        hasFocus = comboBox.hasFocus();
        ListCellRenderer renderer = comboBox.getRenderer();
        Rectangle r = new Rectangle(0, 0, comboBox.getWidth(), comboBox.getHeight());
        paintCurrentValueBackground(g, r, hasFocus);
        if (!comboBox.isEditable()) {
            if (renderer instanceof JComponent) {
                ((JComponent) renderer).setOpaque(false);
                ((JComponent) renderer).setForeground(comboBox.getForeground());
            }
            paintCurrentValue(g, rectangleForCurrentValue(), false);
            if (renderer instanceof JComponent)
                ((JComponent) renderer).setOpaque(true);
        }
    }

    @Override
    public void paintCurrentValueBackground(Graphics g, Rectangle bounds, boolean hasFocus) {
        if (!comboBox.isEditable()) {
            BufferedImage img = COMBO_NORMAL;
            if (!comboBox.isEnabled())
                img = COMBO_DISABLED;
            else if (mouseDown)
                img = COMBO_PRESSED;
            else if (mouseInside)
                img = COMBO_OVER;
            g.drawImage(img,
                    bounds.x, bounds.y, bounds.x + 4, bounds.y + bounds.height,
                    0, 0, 1, 26, comboBox);
            g.drawImage(img,
                    bounds.x + 1, bounds.y, bounds.x + bounds.width - 25, bounds.y + bounds.height,
                    1, 0, 3, 26, comboBox);
            g.drawImage(img,
                    bounds.x + bounds.width - 25, bounds.y, bounds.x + bounds.width, bounds.y + bounds.height,
                    4, 0, 29, 26, comboBox);
        } else {
            /*g.setColor(Color.WHITE);
            g.fillRect(bounds.x, bounds.y, bounds.width - btnSize.width, bounds.height - 1);
            int x = bounds.x, y = bounds.y, w = bounds.width - btnSize.width, h = bounds.height - 1;
            Insets insets = getInsets();
            g.setColor(new Color(141, 142, 143));
            g.drawLine(x, y, x + insets.left, y);
            g.setColor(new Color(203, 203, 204));
            g.drawLine(x + 1, y + 1, x + insets.left, y + 1);
            g.setColor(new Color(152, 152, 153));
            g.drawLine(x, y + 1, x, y + 1);
            g.setColor(new Color(242, 242, 242));
            g.drawLine(x + 1, y + 2, x + insets.left, y + 2);
            g.setColor(new Color(176, 176, 177));
            g.drawLine(x, y + 2, x, y + 2);
            g.setColor(new Color(192, 192, 193));
            g.drawLine(x, y + h, x + insets.left, y + h);
            g.setColor(new Color(184, 184, 185));
            g.drawLine(x, y + 3, x, y + h);*/
        }
    }

    @Override
    protected LayoutManager createLayoutManager() {
        return new ComboLayout();
    }

    @Override
    protected Insets getInsets() {
        return new Insets(0, 5, 0, 0);
    }
    // =================================================================================================================
    // MouseListener Methods
    private boolean mouseInside = false;
    private boolean mouseDown = false;

    @Override
    public void mouseClicked(MouseEvent e) {
    }

    @Override
    public void mouseEntered(MouseEvent e) {
        if (comboBox.isEditable()) {
            if (e.getComponent() == arrowButton)
                mouseInside = true;
        } else {
            mouseInside = true;
            comboBox.repaint();
        }
    }

    @Override
    public void mouseExited(MouseEvent e) {
        if (comboBox.isEditable()) {
            if (e.getComponent() == arrowButton)
                mouseInside = false;
        } else {
            mouseInside = false;
            comboBox.repaint();
        }
    }

    @Override
    public void mousePressed(MouseEvent e) {
        if (comboBox.isEditable()) {
            if (e.getComponent() == arrowButton)
                mouseDown = true;
        } else {
            mouseDown = true;
            comboBox.repaint();
        }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        if (comboBox.isEditable()) {
            if (e.getComponent() == arrowButton)
                mouseDown = false;
        } else {
            mouseDown = false;
            comboBox.repaint();
        }
    }

    // =================================================================================================================
    // LayoutManager
    private class ComboLayout implements LayoutManager {

        @Override
        public void addLayoutComponent(String name, Component comp) {
        }

        @Override
        public void removeLayoutComponent(Component comp) {
        }

        @Override
        public Dimension preferredLayoutSize(Container parent) {
            return parent.getPreferredSize();
        }

        @Override
        public Dimension minimumLayoutSize(Container parent) {
            return parent.getMinimumSize();
        }

        @Override
        public void layoutContainer(Container parent) {
            if (parent instanceof JComboBox) {
                JComboBox cb = (JComboBox) parent;
                int width = cb.getWidth();

                Insets insets = getInsets();
                Rectangle cvb;

                if (arrowButton != null)
                    if (cb.getComponentOrientation().isLeftToRight())
                        arrowButton.setBounds(width - (insets.right + btnSize.width),
                                insets.top,
                                btnSize.width, btnSize.height);
                    else
                        arrowButton.setBounds(insets.left, insets.top,
                                btnSize.width, btnSize.height);
                if (editor != null) {
                    cvb = rectangleForCurrentValue();
                    editor.setBounds(cvb.x, cvb.y, cvb.width, cvb.height);
                }
            }
        }
    }
}
