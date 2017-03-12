/*
 * Copyright (C) 2015 Jack Jiang(cngeeker.com) The BeautyEye Project. 
 * All rights reserved.
 * Project URL:https://github.com/JackJiang2011/beautyeye
 * Version 3.6
 * 
 * Jack Jiang PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 * 
 * BEButtonUI.java at 2015-2-1 20:25:36, original version by Jack Jiang.
 * You can contact author with jb2011@163.com.
 */
package org.jackhuang.hmcl.laf.button;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;

import javax.swing.AbstractButton;
import javax.swing.ButtonModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JRadioButton;
import javax.swing.JToolBar;
import javax.swing.LookAndFeel;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.InsetsUIResource;
import javax.swing.plaf.UIResource;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.text.JTextComponent;
import org.jackhuang.hmcl.laf.utils.AnimationController;

import org.jackhuang.hmcl.laf.utils.Icon9Factory;
import org.jackhuang.hmcl.laf.utils.Skin;
import org.jackhuang.hmcl.laf.utils.TMSchema;
import org.jackhuang.hmcl.laf.utils.TMSchema.Part;
import org.jackhuang.hmcl.laf.utils.TMSchema.State;

/**
 * JButton的UI实现类.
 *
 * @author Jack Jiang(jb2011@163.com)
 * @version 1.0
 * @see com.sun.java.swing.plaf.windows.WindowsButtonUI
 */
public class BEButtonUI extends BasicButtonUI implements Skin {

    private static final Icon9Factory ICON_9 = new Icon9Factory("button");

    private final static BEButtonUI INSTANCE = new BEButtonUI();

    /**
     * The dashed rect gap x.
     */
    protected int dashedRectGapX;

    /**
     * The dashed rect gap y.
     */
    protected int dashedRectGapY;

    /**
     * The dashed rect gap width.
     */
    protected int dashedRectGapWidth;

    /**
     * The dashed rect gap height.
     */
    protected int dashedRectGapHeight;

    /**
     * The focus color.
     */
    protected Color focusColor;

    /**
     * The defaults_initialized.
     */
    private boolean defaults_initialized = false;

    // ********************************
    //          Create PLAF
    // ********************************
    public static ComponentUI createUI(JComponent c) {
        if (c instanceof CustomButton)
            return new CustomButtonUI();
        return INSTANCE;
    }

    // ********************************
    //            Defaults
    // ********************************
    @Override
    protected void installDefaults(AbstractButton b) {
        super.installDefaults(b);
        LookAndFeel.installProperty(b, "opaque", Boolean.FALSE);

        if (!defaults_initialized) {
            String pp = getPropertyPrefix();
            dashedRectGapX = UIManager.getInt(pp + "dashedRectGapX");
            dashedRectGapY = UIManager.getInt(pp + "dashedRectGapY");
            dashedRectGapWidth = UIManager.getInt(pp + "dashedRectGapWidth");
            dashedRectGapHeight = UIManager.getInt(pp + "dashedRectGapHeight");
            focusColor = UIManager.getColor(pp + "focus");
            defaults_initialized = true;
        }

        {
            LookAndFeel.installProperty(b, "rolloverEnabled", Boolean.TRUE);
        }
    }

    @Override
    protected void uninstallDefaults(AbstractButton b) {
        super.uninstallDefaults(b);
        defaults_initialized = false;
    }

    /**
     * Gets the focus color.
     *
     * @return the focus color
     */
    protected Color getFocusColor() {
        return focusColor;
    }

    // ********************************
    //          Layout Methods
    // ********************************
    @Override
    public Dimension getPreferredSize(JComponent c) {
        Dimension d = super.getPreferredSize(c);

        /* Ensure that the width and height of the button is odd,
		 * to allow for the focus line if focus is painted
         */
        AbstractButton b = (AbstractButton) c;
        if (d != null && b.isFocusPainted()) {
            if (d.width % 2 == 0)
                d.width += 1;
            if (d.height % 2 == 0)
                d.height += 1;
        }
        return d;
    }


    /* These rectangles/insets are allocated once for all 
	 * ButtonUI.paint() calls.  Re-using rectangles rather than 
	 * allocating them in each paint call substantially reduced the time
	 * it took paint to run.  Obviously, this method can't be re-entered.
     */
//	private static Rectangle viewRect = new Rectangle();
    @Override
    public void paint(Graphics g, JComponent c) {
        paintXPButtonBackground(g, c);
        super.paint(g, c);
    }

    /**
     * Paint xp button background.
     *
     * @param nomalColor the nomal color
     * @param g the g
     * @param c the c
     */
    public static void paintXPButtonBackground(Graphics g, JComponent c) {
        AbstractButton b = (AbstractButton) c;
        boolean toolbar = b.getParent() instanceof JToolBar;

        if (b.isContentAreaFilled()) {
            ButtonModel model = b.getModel();
            Dimension d = c.getSize();
            int dx = 0;
            int dy = 0;
            int dw = d.width;
            int dh = d.height;

            Border border = c.getBorder();
            Insets insets;
            if (border != null)
                // Note: The border may be compound, containing an outer
                // opaque border (supplied by the application), plus an
                // inner transparent margin border. We want to size the
                // background to fill the transparent part, but stay
                // inside the opaque part.
                insets = BEButtonUI.getOpaqueInsets(border, c);
            else
                insets = c.getInsets();
            if (insets != null) {
                dx += insets.left;
                dy += insets.top;
                dw -= (insets.left + insets.right);
                dh -= (insets.top + insets.bottom);
            }

            AnimationController.paintSkin(c, INSTANCE, g, dx, dy, dw, dh, getXPButtonState(b));
        }
    }

    @Override
    public Part getPart(JComponent c) {
        return getXPButtonType((AbstractButton) c);
    }

    static Part getXPButtonType(AbstractButton b) {
        if (b instanceof JCheckBox)
            return Part.BP_CHECKBOX;
        if (b instanceof JRadioButton)
            return Part.BP_RADIOBUTTON;
        boolean toolbar = (b.getParent() instanceof JToolBar);
        return toolbar ? Part.TP_BUTTON : Part.BP_PUSHBUTTON;
    }

    public static State getXPButtonState(AbstractButton b) {
        Part part = getXPButtonType(b);
        ButtonModel model = b.getModel();
        State state = State.NORMAL;
        switch (part) {
            case BP_RADIOBUTTON:
            case BP_CHECKBOX:
                if (!model.isEnabled())
                    state = (model.isSelected()) ? State.CHECKEDDISABLED
                            : State.UNCHECKEDDISABLED;
                else if (model.isPressed() && model.isArmed())
                    state = (model.isSelected()) ? State.CHECKEDPRESSED
                            : State.UNCHECKEDPRESSED;
                else if (model.isRollover())
                    state = (model.isSelected()) ? State.CHECKEDHOT
                            : State.UNCHECKEDHOT;
                else
                    state = (model.isSelected()) ? State.CHECKEDNORMAL
                            : State.UNCHECKEDNORMAL;
                break;
            case BP_PUSHBUTTON:
            case TP_BUTTON:
                boolean toolbar = (b.getParent() instanceof JToolBar);
                if (toolbar) {
                    if (model.isArmed() && model.isPressed())
                        state = State.PRESSED;
                    else if (!model.isEnabled())
                        state = State.DISABLED;
                    else if (model.isSelected() && model.isRollover())
                        state = State.ROLLOVERCHECKED;
                    else if (model.isSelected())
                        state = State.CHECKED;
                    else if (model.isRollover())
                        state = State.ROLLOVER;
                    else if (b.hasFocus())
                        state = State.ROLLOVER;
                } else
                    if ((model.isArmed() && model.isPressed())
                            || model.isSelected())
                        state = State.PRESSED;
                    else if (!model.isEnabled())
                        state = State.DISABLED;
                    else if (model.isRollover() || model.isPressed())
                        state = State.ROLLOVER;
                    else if (b instanceof JButton
                            && ((JButton) b).isDefaultButton())
                        state = State.DEFAULT;
                break;
            default:
                state = State.NORMAL;
        }

        return state;
    }

    @Override
    public void paintSkinRaw(Graphics g, int dx, int dy, int dw, int dh, TMSchema.State state) {
        ICON_9.get(state.toString()).draw((Graphics2D) g, dx, dy, dw, dh);
    }

    /**
     * returns - b.getBorderInsets(c) if border is opaque - null if border is
     * completely non-opaque - somewhere inbetween if border is compound and
     * outside border is opaque and inside isn't
     *
     * @param b the b
     * @param c the c
     * @return the opaque insets
     */
    private static Insets getOpaqueInsets(Border b, Component c) {
        if (b == null)
            return null;
        if (b.isBorderOpaque())
            return b.getBorderInsets(c);
        else if (b instanceof CompoundBorder) {
            CompoundBorder cb = (CompoundBorder) b;
            Insets iOut = getOpaqueInsets(cb.getOutsideBorder(), c);
            if (iOut != null && iOut.equals(cb.getOutsideBorder().getBorderInsets(c))) {
                // Outside border is opaque, keep looking
                Insets iIn = getOpaqueInsets(cb.getInsideBorder(), c);
                if (iIn == null)
                    // Inside is non-opaque, use outside insets
                    return iOut;
                else
                    // Found non-opaque somewhere in the inside (which is
                    // also compound).
                    return new Insets(iOut.top + iIn.top, iOut.left + iIn.left,
                            iOut.bottom + iIn.bottom, iOut.right + iIn.right);
            } else
                // Outside is either all non-opaque or has non-opaque
                // border inside another compound border
                return iOut;
        } else
            return null;
    }

    public static class BEEmptyBorder extends EmptyBorder implements UIResource {

        public BEEmptyBorder(Insets m) {
            super(m.top + 2, m.left + 2, m.bottom + 2, m.right + 2);
        }

        @Override
        public Insets getBorderInsets(Component c) {
            return getBorderInsets(c, getBorderInsets());
        }

        @Override
        public Insets getBorderInsets(Component c, Insets insets) {
            insets = super.getBorderInsets(c, insets);

            Insets margin = null;
            if (c instanceof AbstractButton) {
                Insets m = ((AbstractButton) c).getMargin();
                // if this is a toolbar button then ignore getMargin()
                // and subtract the padding added by the constructor
                if (c.getParent() instanceof JToolBar
                        && !(c instanceof JRadioButton)
                        && !(c instanceof JCheckBox)
                        && m instanceof InsetsUIResource) {
                    insets.top -= 2;
                    insets.left -= 2;
                    insets.bottom -= 2;
                    insets.right -= 2;
                } else
                    margin = m;
            } else if (c instanceof JToolBar)
                margin = ((JToolBar) c).getMargin();
            else if (c instanceof JTextComponent)
                margin = ((JTextComponent) c).getMargin();
            if (margin != null) {
                insets.top = margin.top + 2;
                insets.left = margin.left + 2;
                insets.bottom = margin.bottom + 2;
                insets.right = margin.right + 2;
            }
            return insets;
        }
    }
}
