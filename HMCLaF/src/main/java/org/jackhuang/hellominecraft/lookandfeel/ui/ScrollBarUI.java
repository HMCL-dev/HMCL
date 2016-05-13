/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraft.lookandfeel.ui;

import static org.jackhuang.hellominecraft.util.ui.GraphicsUtils.loadImage;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JScrollBar;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.metal.MetalScrollBarUI;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;

/**
 * NimbusScrollBarUI - A custom scrollbar ui for nimbus. It is special as it handles all the painting for the buttons as
 * well so that it can cope with the buttons being non-recangular.
 *
 * @author Created by Jasper Potts (Jan 17, 2007)
 * @version 1.0
 */
public class ScrollBarUI extends MetalScrollBarUI {

    private static final BufferedImage BACKGROUND_ENABLED = loadImage("scroll_enabled.png");
    private static final BufferedImage BACKGROUND_DISABLED = loadImage("scroll_disabled.png");
    private static final BufferedImage SCROLL_DEC_NORMAL = loadImage("scroll_dec_normal.png");
    private static final BufferedImage SCROLL_DEC_OVER = loadImage("scroll_dec_over.png");
    private static final BufferedImage SCROLL_DEC_PRESSED = loadImage("scroll_dec_pressed.png");
    private static final BufferedImage SCROLL_INC_NORMAL = loadImage("scroll_inc_normal.png");
    private static final BufferedImage SCROLL_INC_OVER = loadImage("scroll_inc_over.png");
    private static final BufferedImage SCROLL_INC_PRESSED = loadImage("scroll_inc_pressed.png");
    private static final BufferedImage SCROLL_THUMB_NORMAL = loadImage("scroll_thumb_normal.png");
    private static final BufferedImage SCROLL_THUMB_OVER = loadImage("scroll_thumb_over.png");
    private static final BufferedImage SCROLL_THUMB_PRESSED = loadImage("scroll_thumb_pressed.png");

    private boolean incBtnMouseOver, incBtnMousePressed;
    private boolean decBtnMouseOver, decBtnMousePressed;
    private boolean thumbMousePressed;

    /**
     * Creates a new UI deligate for the given component. It is a standard method that all UI deligates must have.
     *
     * @param c The component that the UI is for
     * @return a new instance of NimbusScrollBarUI
     */
    public static ComponentUI createUI(JComponent c) {
        return new ScrollBarUI();
    }

    /** {@inheritDoc} */
    @Override public void installUI(JComponent c) {
        super.installUI(c);
        c.setOpaque(true);
        c.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                if (isThumbRollover()) {
                    thumbMousePressed = true;
                    scrollbar.repaint();
                }
            }

            @Override public void mouseReleased(MouseEvent e) {
                thumbMousePressed = false;
            }
        });
    }

    /** {@inheritDoc} */
    @Override protected Dimension getMinimumThumbSize() {
        return new Dimension(15, 15);
    }

    /** {@inheritDoc} */
    @Override protected JButton createDecreaseButton(int orientation) {
        decreaseButton = new ScrollButton(orientation, scrollBarWidth, isFreeStanding);
        decreaseButton.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) {
                decBtnMouseOver = true;
            }

            @Override public void mouseExited(MouseEvent e) {
                decBtnMouseOver = false;
            }

            @Override public void mousePressed(MouseEvent e) {
                decBtnMousePressed = true;
            }

            @Override public void mouseReleased(MouseEvent e) {
                decBtnMousePressed = false;
            }
        });
        return decreaseButton;
    }

    /** {@inheritDoc} */
    @Override protected JButton createIncreaseButton(int orientation) {
        increaseButton = new ScrollButton(orientation, scrollBarWidth, isFreeStanding);
        increaseButton.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) {
                incBtnMouseOver = true;
            }

            @Override public void mouseExited(MouseEvent e) {
                incBtnMouseOver = false;
            }

            @Override public void mousePressed(MouseEvent e) {
                incBtnMousePressed = true;
            }

            @Override public void mouseReleased(MouseEvent e) {
                incBtnMousePressed = false;
            }
        });
        return increaseButton;
    }

    /** {@inheritDoc} */
    @Override protected void paintTrack(Graphics g, JComponent c, Rectangle trackBounds) {
        BufferedImage decImg =
                decBtnMousePressed ? SCROLL_DEC_PRESSED : decBtnMouseOver ? SCROLL_DEC_OVER : SCROLL_DEC_NORMAL;
        BufferedImage incImg =
                incBtnMousePressed ? SCROLL_INC_PRESSED : incBtnMouseOver ? SCROLL_INC_OVER : SCROLL_INC_NORMAL;
        Graphics2D g2 = (Graphics2D) g;
        AffineTransform origTransform = g2.getTransform();
        int scrollWidth = scrollbar.getWidth();
        if (scrollbar.getOrientation() == JScrollBar.VERTICAL) {
            scrollWidth = scrollbar.getHeight();
            g2.scale(1, -1);
            g2.rotate(-Math.PI / 2, 0, 0);
        }
        // draw track & bottons
        if (scrollbar.isEnabled()) {
            g.drawImage(decImg, 0, 0, scrollbar);
            //g.drawImage(BACKGROUND_ENABLED, 15, 0, scrollWidth - 15, 15, 0, 0, 1, 15, scrollbar);
            g.drawImage(incImg, scrollWidth - 15, 0, scrollbar);
        } else {
            //g.drawImage(BACKGROUND_DISABLED, 0, 0, scrollWidth, 15, 0, 0, 1, 15, scrollbar);
        }
        // undo any transform
        g2.setTransform(origTransform);
    }

    /** {@inheritDoc} */
    @Override protected void paintThumb(Graphics g, JComponent c, Rectangle thumbBounds) {
        if (scrollbar.isEnabled()) {
            BufferedImage thumbImg = thumbMousePressed ? SCROLL_THUMB_PRESSED :
                    isThumbRollover() ? SCROLL_THUMB_OVER : SCROLL_THUMB_NORMAL;
            Graphics2D g2 = (Graphics2D) g;
            AffineTransform origTransform = g2.getTransform();
            Rectangle b = thumbBounds;
            if (scrollbar.getOrientation() == JScrollBar.VERTICAL) {
                b = new Rectangle(thumbBounds.y, thumbBounds.x, thumbBounds.height, thumbBounds.width);
                g2.scale(1, -1);
                g2.rotate(-Math.PI / 2, 0, 0);
            }
            g.drawImage(thumbImg,
                    b.x, b.y, b.x + 14, b.y + 15,
                    0, 0, 14, 15, scrollbar);
            g.drawImage(thumbImg,
                    b.x + 14, b.y, b.x + b.width - 14, b.y + 15,
                    16, 0, 17, 15, scrollbar);
            g.drawImage(thumbImg,
                    b.x + b.width - 14, b.y, b.x + b.width, b.y + 15,
                    24, 0, 38, 15, scrollbar);
            // undo any transform
            g2.setTransform(origTransform);
        }
    }
}
