/*
 * Copyright (C) 2015 Jack Jiang(cngeeker.com) The BeautyEye Project. 
 * All rights reserved.
 * Project URL:https://github.com/JackJiang2011/beautyeye
 * Version 3.6
 * 
 * Jack Jiang PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 * 
 * BERootPaneUI.java at 2015-2-1 20:25:40, original version by Jack Jiang.
 * You can contact author with jb2011@163.com.
 */
package org.jackhuang.hmcl.laf.titlepane;

import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.HeadlessException;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.LayoutManager2;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.beans.PropertyChangeEvent;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;

import javax.swing.JComponent;
import javax.swing.JLayeredPane;
import javax.swing.JRootPane;
import javax.swing.LookAndFeel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.event.MouseInputListener;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.UIResource;
import javax.swing.plaf.basic.BasicRootPaneUI;

import org.jackhuang.hmcl.laf.BeautyEyeLNFHelper;
import org.jackhuang.hmcl.laf.WindowTranslucencyHelper;

/**
 * 窗体的UI实现.
 *
 * @author Jack Jiang(jb2011@163.com)
 * @see javax.swing.plaf.metal.MetalRootPaneUI
 */
public class BERootPaneUI extends BasicRootPaneUI {

    /**
     * Keys to lookup borders in defaults table.
     */
    private static final String[] BORDER_KEYS = new String[] {
        null,
        "RootPane.frameBorder",
        "RootPane.plainDialogBorder",
        "RootPane.informationDialogBorder",
        "RootPane.errorDialogBorder",
        "RootPane.colorChooserDialogBorder",
        "RootPane.fileChooserDialogBorder",
        "RootPane.questionDialogBorder",
        "RootPane.warningDialogBorder"
    };

    //* 2012-09-19 在BeautyEye v3.2中此常量被Jack Jiang取消了，因为
    //* v3.2中启用了相比原MetalRootPaneUI中更精确更好的边框拖放算法
//	/**
//	 * The amount of space (in pixels) that the cursor is changed on.
//	 */
//	//MetalLookAndFeel中默认是16
//	private static final int CORNER_DRAG_WIDTH = 16; 
//		//BeautyEyeLNFHelper.__getFrameBorder_CORNER_DRAG_WIDTH();//为了便 得用户的敏感触点区更大，提高用户体验，此值可加大
    /**
     * Region from edges that dragging is active from.
     */
    //窗口可拖动敏感触点区域大小要设置多大取决于你知定义border的insets，默认是 5;
    private static final int BORDER_DRAG_THICKNESS = 5;
    //BeautyEyeLNFHelper.__getFrameBorder_BORDER_DRAG_THICKNESS();//为了便 得用户的敏感触点区更大，提高用户体验，此值可加大

    /**
     * Window the <code>JRootPane</code> is in.
     */
    private Window window;

    /**
     * <code>JComponent</code> providing window decorations. This will be null
     * if not providing window decorations.
     */
    private JComponent titlePane;

    /**
     * <code>MouseInputListener</code> that is added to the parent
     * <code>Window</code> the <code>JRootPane</code> is contained in.
     */
    private MouseInputListener mouseInputListener;

    /**
     * The <code>LayoutManager</code> that is set on the <code>JRootPane</code>.
     */
    private LayoutManager layoutManager;

    /**
     * <code>LayoutManager</code> of the <code>JRootPane</code> before we
     * replaced it.
     */
    private LayoutManager savedOldLayout;

    /**
     * <code>JRootPane</code> providing the look and feel for.
     */
    private JRootPane root;

    /**
     * <code>Cursor</code> used to track the cursor set by the user. This is
     * initially <code>Cursor.DEFAULT_CURSOR</code>.
     */
    private Cursor lastCursor
            = Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR);

    /**
     * 用于在窗口被激活与不激活时自动设置它的透明度（不激活时设为半透明）.
     */
    private WindowListener windowsListener = null;

    public static ComponentUI createUI(JComponent c) {
        return new BERootPaneUI();
    }

    /**
     * Invokes supers implementation of <code>installUI</code> to install the
     * necessary state onto the passed in <code>JRootPane</code> to render the
     * metal look and feel implementation of <code>RootPaneUI</code>. If the
     * <code>windowDecorationStyle</code> property of the <code>JRootPane</code>
     * is other than <code>JRootPane.NONE</code>, this will add a custom
     * <code>Component</code> to render the widgets to <code>JRootPane</code>,
     * as well as installing a custom <code>Border</code> and
     * <code>LayoutManager</code> on the <code>JRootPane</code>.
     *
     * @param c the JRootPane to install state onto
     */
    @Override
    public void installUI(JComponent c) {
        super.installUI(c);

        root = (JRootPane) c;
        int style = root.getWindowDecorationStyle();

        if (style != JRootPane.NONE)
            installClientDecorations(root);
    }

    /**
     * Invokes supers implementation to uninstall any of its state. This will
     * also reset the <code>LayoutManager</code> of the <code>JRootPane</code>.
     * If a <code>Component</code> has been added to the <code>JRootPane</code>
     * to render the window decoration style, this method will remove it.
     * Similarly, this will revert the Border and LayoutManager of the
     * <code>JRootPane</code> to what it was before <code>installUI</code> was
     * invoked.
     *
     * @param c the JRootPane to uninstall state from
     */
    @Override
    public void uninstallUI(JComponent c) {
        super.uninstallUI(c);
        uninstallClientDecorations(root);

        layoutManager = null;
        mouseInputListener = null;
        root = null;
    }

    /**
     * Installs the appropriate <code>Border</code> onto the
     * <code>JRootPane</code>.
     *
     * @param root the root
     */
    void installBorder(JRootPane root) {
        int style = root.getWindowDecorationStyle();

        if (style == JRootPane.NONE)
            LookAndFeel.uninstallBorder(root);
        else {
            Border b = root.getBorder();
            if (b == null || b instanceof UIResource) {
                root.setBorder(null);
                root.setBorder(UIManager.getBorder(BORDER_KEYS[style]));
            }
        }
    }

    /**
     * Removes any border that may have been installed.
     *
     * @param root the root
     */
    private void uninstallBorder(JRootPane root) {
        LookAndFeel.uninstallBorder(root);
    }

    /**
     * Installs the necessary Listeners on the parent <code>Window</code>, if
     * there is one.
     * <p>
     * This takes the parent so that cleanup can be done from
     * <code>removeNotify</code>, at which point the parent hasn't been reset
     * yet.
     *
     * @param root the root
     * @param parent The parent of the JRootPane
     */
    private void installWindowListeners(JRootPane root, Component parent) {
        if (parent instanceof Window)
            window = (Window) parent;
        else
            window = SwingUtilities.getWindowAncestor(parent);
        if (window != null) {
            if (mouseInputListener == null)
                mouseInputListener = createWindowMouseInputListener(root);

            window.addMouseListener(mouseInputListener);
            window.addMouseMotionListener(mouseInputListener);

            //* add by JS 2011-12-27,给窗口增加监听器：在不活动时设置窗口半透明，活动时还原
            if (BeautyEyeLNFHelper.translucencyAtFrameInactive) {
                if (windowsListener == null)
                    windowsListener = new WindowAdapter() {
                        @Override
                        public void windowActivated(WindowEvent e) {
                            if (window != null)
                                window.setOpacity(1);
                        }

                        @Override
                        public void windowDeactivated(WindowEvent e) {
                            if (window != null)
                                window.setOpacity(0.94f);
                        }
                    };
                window.addWindowListener(windowsListener);
            }
        }
    }

    /**
     * Uninstalls the necessary Listeners on the <code>Window</code> the
     * Listeners were last installed on.
     *
     * @param root the root
     */
    private void uninstallWindowListeners(JRootPane root) {
        if (window != null) {
            window.removeMouseListener(mouseInputListener);
            window.removeMouseMotionListener(mouseInputListener);
        }
    }

    /**
     * Installs the appropriate LayoutManager on the <code>JRootPane</code> to
     * render the window decorations.
     *
     * @param root the root
     */
    private void installLayout(JRootPane root) {
        if (layoutManager == null)
            layoutManager = createLayoutManager();
        savedOldLayout = root.getLayout();
        root.setLayout(layoutManager);
    }

    /**
     * Uninstalls the previously installed <code>LayoutManager</code>.
     *
     * @param root the root
     */
    private void uninstallLayout(JRootPane root) {
        if (savedOldLayout != null) {
            root.setLayout(savedOldLayout);
            savedOldLayout = null;
        }
    }

    /**
     * Installs the necessary state onto the JRootPane to render client
     * decorations. This is ONLY invoked if the <code>JRootPane</code> has a
     * decoration style other than <code>JRootPane.NONE</code>.
     *
     * @param root the root
     */
    private void installClientDecorations(JRootPane root) {
        installBorder(root);

        JComponent p = createTitlePane(root);

        setTitlePane(root, p);
        installWindowListeners(root, root.getParent());
        installLayout(root);

        //只有在窗口边框是半透明的情况下，以下才需要设置窗口透明
        //* 注意：本类中的此处代码的目的就是为了实现半透明边框窗口的
        //* 正常显示，而且仅针对此目的。如果该边框不为透明，则此处也就不需要设置
        //* 窗口透明了，那么如果你的程序其它地方需要窗口透明的话，自行.setWindowOpaque(..)
        //* 就行了，由开发者自先决定，此处就不承载过多的要求了
        if (!BeautyEyeLNFHelper.isFrameBorderOpaque()
                && window != null) {
            //** 20111222 by jb2011，让窗口全透明（用以实现窗口的透明边框效果）
//			AWTUtilities.setWindowOpaque(window, false);
            // TODO BUG：1）目前可知，在jdk1.7.0_u6下，JDialog的半透明边框的透明度比原设计深一倍
            // TODO BUG：2）目前可知，在jdk1.6.0_u33下+win7平台下，JFrame窗口被调置成透明后，
            //				该窗口内所在文本都会被反走样（不管你要没有要求反走样），真悲具，这应该
            //				是官方AWTUtilities.setWindowOpaque(..)bug导致的,1.7.0_u6同样存在该问题，
            //				使用BeautyEye时，遇到这样的问题只能自行使用__isFrameBorderOpaque中指定的
            //				不透明边框才行（这样此类的以下代码就不用执行，也就不用触发该bug了），但
            //				JDialog不受此bug影响，诡异！
            WindowTranslucencyHelper.setWindowOpaque(window, false);
            root.revalidate();
            root.repaint();
        }
    }

    /**
     * Uninstalls any state that <code>installClientDecorations</code> has
     * installed.
     * <p>
     * NOTE: This may be called if you haven't installed client decorations yet
     * (ie before <code>installClientDecorations</code> has been invoked).
     *
     * @param root the root
     */
    private void uninstallClientDecorations(JRootPane root) {
        uninstallBorder(root);
        uninstallWindowListeners(root);
        setTitlePane(root, null);
        uninstallLayout(root);
        // We have to revalidate/repaint root if the style is JRootPane.NONE
        // only. When we needs to call revalidate/repaint with other styles
        // the installClientDecorations is always called after this method
        // imediatly and it will cause the revalidate/repaint at the proper
        // time.
        int style = root.getWindowDecorationStyle();
        if (style == JRootPane.NONE) {
            root.repaint();
            root.revalidate();
        }
        // Reset the cursor, as we may have changed it to a resize cursor
        if (window != null)
            window.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        window = null;
    }

    /**
     * Returns the <code>JComponent</code> to render the window decoration
     * style.
     *
     * @param root the root
     * @return the j component
     */
    private JComponent createTitlePane(JRootPane root) {
        return new BETitlePane(root, this);
    }

    /**
     * Returns a <code>MouseListener</code> that will be added to the
     * <code>Window</code> containing the <code>JRootPane</code>.
     *
     * @param root the root
     * @return the mouse input listener
     */
    private MouseInputListener createWindowMouseInputListener(JRootPane root) {
        return new MouseInputHandler();
    }

    /**
     * Returns a <code>LayoutManager</code> that will be set on the
     * <code>JRootPane</code>.
     *
     * @return the layout manager
     */
    private LayoutManager createLayoutManager() {
        return new XMetalRootLayout();
    }

    /**
     * Sets the window title pane -- the JComponent used to provide a plaf a way
     * to override the native operating system's window title pane with one
     * whose look and feel are controlled by the plaf. The plaf creates and sets
     * this value; the default is null, implying a native operating system
     * window title pane.
     *
     * @param root the root
     * @param titlePane the title pane
     */
    private void setTitlePane(JRootPane root, JComponent titlePane) {
        JLayeredPane layeredPane = root.getLayeredPane();
        JComponent oldTitlePane = getTitlePane();

        if (oldTitlePane != null) {
            oldTitlePane.setVisible(false);
            layeredPane.remove(oldTitlePane);
        }
        if (titlePane != null) {
            layeredPane.add(titlePane, JLayeredPane.FRAME_CONTENT_LAYER);
            titlePane.setVisible(true);
        }
        this.titlePane = titlePane;
    }

    /**
     * Returns the <code>JComponent</code> rendering the title pane. If this
     * returns null, it implies there is no need to render window decorations.
     *
     * @return the current window title pane, or null
     * @see #setTitlePane
     */
    private JComponent getTitlePane() {
        return titlePane;
    }

    /**
     * Returns the <code>JRootPane</code> we're providing the look and feel for.
     *
     * @return the root pane
     */
    private JRootPane getRootPane() {
        return root;
    }

    /**
     * Invoked when a property changes. <code>MetalRootPaneUI</code> is
     * primarily interested in events originating from the
     * <code>JRootPane</code> it has been installed on identifying the property
     * <code>windowDecorationStyle</code>. If the
     * <code>windowDecorationStyle</code> has changed to a value other than
     * <code>JRootPane.NONE</code>, this will add a <code>Component</code> to
     * the <code>JRootPane</code> to render the window decorations, as well as
     * installing a <code>Border</code> on the <code>JRootPane</code>. On the
     * other hand, if the <code>windowDecorationStyle</code> has changed to
     * <code>JRootPane.NONE</code>, this will remove the <code>Component</code>
     * that has been added to the <code>JRootPane</code> as well resetting the
     * Border to what it was before <code>installUI</code> was invoked.
     *
     * @param e A PropertyChangeEvent object describing the event source and the
     * property that has changed.
     */
    @Override
    public void propertyChange(PropertyChangeEvent e) {
        super.propertyChange(e);

        String propertyName = e.getPropertyName();
        if (propertyName == null)
            return;

        if (propertyName.equals("windowDecorationStyle")) {
            JRootPane r = (JRootPane) e.getSource();
            int style = r.getWindowDecorationStyle();

            // This is potentially more than needs to be done,
            // but it rarely happens and makes the install/uninstall process
            // simpler. MetalTitlePane also assumes it will be recreated if
            // the decoration style changes.
            uninstallClientDecorations(r);
            if (style != JRootPane.NONE)
                installClientDecorations(r);
        } else if (propertyName.equals("ancestor")) {
            uninstallWindowListeners(root);
            if (((JRootPane) e.getSource()).getWindowDecorationStyle()
                    != JRootPane.NONE)
                installWindowListeners(root, root.getParent());
        }
    }

    /**
     * A custom layout manager that is responsible for the layout of
     * layeredPane, glassPane, menuBar and titlePane, if one has been installed.
     */
    // NOTE: Ideally this would extends JRootPane.RootLayout, but that
    //       would force this to be non-static.
    private static class XMetalRootLayout implements LayoutManager2 {

        /**
         * Returns the amount of space the layout would like to have.
         *
         * @param parent the parent
         * @return a Dimension object containing the layout's preferred size
         */
        @Override
        public Dimension preferredLayoutSize(Container parent) {
            Dimension cpd, mbd, tpd;
            int cpWidth = 0;
            int cpHeight = 0;
            int mbWidth = 0;
            int mbHeight = 0;
            int tpWidth = 0;
            int tpHeight = 0;
            Insets i = parent.getInsets();
            JRootPane root = (JRootPane) parent;

            if (root.getContentPane() != null)
                cpd = root.getContentPane().getPreferredSize();
            else
                cpd = root.getSize();
            if (cpd != null) {
                cpWidth = cpd.width;
                cpHeight = cpd.height;
            }

            if (root.getMenuBar() != null) {
                mbd = root.getMenuBar().getPreferredSize();
                if (mbd != null) {
                    mbWidth = mbd.width;
                    mbHeight = mbd.height;
                }
            }

            if (root.getWindowDecorationStyle() != JRootPane.NONE
                    && (root.getUI() instanceof BERootPaneUI)) {
                JComponent titlePane = ((BERootPaneUI) root.getUI()).
                        getTitlePane();
                if (titlePane != null) {
                    tpd = titlePane.getPreferredSize();
                    if (tpd != null) {
                        tpWidth = tpd.width;
                        tpHeight = tpd.height;
                    }
                }
            }

            return new Dimension(Math.max(Math.max(cpWidth, mbWidth), tpWidth) + i.left + i.right,
                    cpHeight + mbHeight + tpWidth + i.top + i.bottom);
        }

        /**
         * Returns the minimum amount of space the layout needs.
         *
         * @param parent the parent
         * @return a Dimension object containing the layout's minimum size
         */
        @Override
        public Dimension minimumLayoutSize(Container parent) {
            Dimension cpd, mbd, tpd;
            int cpWidth = 0;
            int cpHeight = 0;
            int mbWidth = 0;
            int mbHeight = 0;
            int tpWidth = 0;
            int tpHeight = 0;
            Insets i = parent.getInsets();
            JRootPane root = (JRootPane) parent;

            if (root.getContentPane() != null)
                cpd = root.getContentPane().getMinimumSize();
            else
                cpd = root.getSize();
            if (cpd != null) {
                cpWidth = cpd.width;
                cpHeight = cpd.height;
            }

            if (root.getMenuBar() != null) {
                mbd = root.getMenuBar().getMinimumSize();
                if (mbd != null) {
                    mbWidth = mbd.width;
                    mbHeight = mbd.height;
                }
            }
            if (root.getWindowDecorationStyle() != JRootPane.NONE
                    && (root.getUI() instanceof BERootPaneUI)) {
                JComponent titlePane = ((BERootPaneUI) root.getUI()).
                        getTitlePane();
                if (titlePane != null) {
                    tpd = titlePane.getMinimumSize();
                    if (tpd != null) {
                        tpWidth = tpd.width;
                        tpHeight = tpd.height;
                    }
                }
            }

            return new Dimension(Math.max(Math.max(cpWidth, mbWidth), tpWidth) + i.left + i.right,
                    cpHeight + mbHeight + tpWidth + i.top + i.bottom);
        }

        /**
         * Returns the maximum amount of space the layout can use.
         *
         * @param target the target
         * @return a Dimension object containing the layout's maximum size
         */
        @Override
        public Dimension maximumLayoutSize(Container target) {
            Dimension cpd, mbd, tpd;
            int cpWidth = Integer.MAX_VALUE;
            int cpHeight = Integer.MAX_VALUE;
            int mbWidth = Integer.MAX_VALUE;
            int mbHeight = Integer.MAX_VALUE;
            int tpWidth = Integer.MAX_VALUE;
            int tpHeight = Integer.MAX_VALUE;
            Insets i = target.getInsets();
            JRootPane root = (JRootPane) target;

            if (root.getContentPane() != null) {
                cpd = root.getContentPane().getMaximumSize();
                if (cpd != null) {
                    cpWidth = cpd.width;
                    cpHeight = cpd.height;
                }
            }

            if (root.getMenuBar() != null) {
                mbd = root.getMenuBar().getMaximumSize();
                if (mbd != null) {
                    mbWidth = mbd.width;
                    mbHeight = mbd.height;
                }
            }

            if (root.getWindowDecorationStyle() != JRootPane.NONE
                    && (root.getUI() instanceof BERootPaneUI)) {
                JComponent titlePane = ((BERootPaneUI) root.getUI()).
                        getTitlePane();
                if (titlePane != null) {
                    tpd = titlePane.getMaximumSize();
                    if (tpd != null) {
                        tpWidth = tpd.width;
                        tpHeight = tpd.height;
                    }
                }
            }

            int maxHeight = Math.max(Math.max(cpHeight, mbHeight), tpHeight);
            // Only overflows if 3 real non-MAX_VALUE heights, sum to > MAX_VALUE
            // Only will happen if sums to more than 2 billion units.  Not likely.
            if (maxHeight != Integer.MAX_VALUE)
                maxHeight = cpHeight + mbHeight + tpHeight + i.top + i.bottom;

            int maxWidth = Math.max(Math.max(cpWidth, mbWidth), tpWidth);
            // Similar overflow comment as above
            if (maxWidth != Integer.MAX_VALUE)
                maxWidth += i.left + i.right;

            return new Dimension(maxWidth, maxHeight);
        }

        /**
         * Instructs the layout manager to perform the layout for the specified
         * container.
         *
         * @param parent the parent
         */
        @Override
        public void layoutContainer(Container parent) {
            JRootPane root = (JRootPane) parent;
            Rectangle b = root.getBounds();
            Insets i = root.getInsets();
            int nextY = 0;
            int w = b.width - i.right - i.left;
            int h = b.height - i.top - i.bottom;

            if (root.getLayeredPane() != null)
                root.getLayeredPane().setBounds(i.left, i.top, w, h);
            if (root.getGlassPane() != null)
                root.getGlassPane().setBounds(i.left, i.top, w, h);
            // Note: This is laying out the children in the layeredPane,
            // technically, these are not our children.
            if (root.getWindowDecorationStyle() != JRootPane.NONE
                    && (root.getUI() instanceof BERootPaneUI)) {
                JComponent titlePane = ((BERootPaneUI) root.getUI()).
                        getTitlePane();
                if (titlePane != null) {
                    Dimension tpd = titlePane.getPreferredSize();
                    if (tpd != null) {
                        int tpHeight = tpd.height;
                        titlePane.setBounds(0, 0, w, tpHeight);
                        nextY += tpHeight;
                    }
                }
            }
            if (root.getJMenuBar() != null
                    //* 该 行代码由Jack Jiang于2012-10-20增加：目的是为解决当
                    //* MebuBar被设置不可见时任然被错误地当作可视组件占据布局空间，这
                    //* 在BE LNF中的表现就是当menuBar不可见，它占据的那块空间将会是全透明
                    //* 的空白区。这个问题在Metal主题中仍然存在(就是设置JFrame.setDefaultLookAndFeelDecorated(true);
                    //* JDialog.setDefaultLookAndFeelDecorated(true);后的Metal主题状态)，
                    //* 可能官方不认为这是个bug吧。
                    //* 为什么无论什么外观当在使用系统窗口边框类型时不会出现这样的情况呢？它
                    //* 可能是由于窗口外观的实现原理决定的吧（按理说是同一原理），有待深究！！！
                    && root.getJMenuBar().isVisible()) {
                Dimension mbd = root.getJMenuBar().getPreferredSize();
                root.getJMenuBar().setBounds(0, nextY, w, mbd.height);
                nextY += mbd.height;
            }
            if (root.getContentPane() != null
                    //* 该 行代码由Jack Jiang于2012-10-20增加：目的是为解决与menubar在设置可见性时遇难到的一样的问题
                    && root.getContentPane().isVisible()) {
                Dimension cpd = root.getContentPane().getPreferredSize();
                root.getContentPane().setBounds(0, nextY, w,
                        h < nextY ? 0 : h - nextY);
            }
        }

        @Override
        public void addLayoutComponent(String name, Component comp) {
        }

        @Override
        public void removeLayoutComponent(Component comp) {
        }

        @Override
        public void addLayoutComponent(Component comp, Object constraints) {
        }

        @Override
        public float getLayoutAlignmentX(Container target) {
            return 0.0f;
        }

        @Override
        public float getLayoutAlignmentY(Container target) {
            return 0.0f;
        }

        @Override
        public void invalidateLayout(Container target) {
        }
    }

    /**
     * Maps from positions to cursor type. Refer to calculateCorner and
     * calculatePosition for details of this.
     */
    private static final int[] cursorMapping = new int[] { Cursor.NW_RESIZE_CURSOR,
        Cursor.NW_RESIZE_CURSOR,
        Cursor.N_RESIZE_CURSOR,
        Cursor.NE_RESIZE_CURSOR,
        Cursor.NE_RESIZE_CURSOR,
        Cursor.NW_RESIZE_CURSOR,
        0,
        0,
        0,
        Cursor.NE_RESIZE_CURSOR,
        Cursor.W_RESIZE_CURSOR,
        0,
        0,
        0,
        Cursor.E_RESIZE_CURSOR,
        Cursor.SW_RESIZE_CURSOR,
        0,
        0,
        0,
        Cursor.SE_RESIZE_CURSOR,
        Cursor.SW_RESIZE_CURSOR,
        Cursor.SW_RESIZE_CURSOR,
        Cursor.S_RESIZE_CURSOR,
        Cursor.SE_RESIZE_CURSOR,
        Cursor.SE_RESIZE_CURSOR
    };

    /**
     * MouseInputHandler is responsible for handling resize/moving of the
     * Window. It sets the cursor directly on the Window when then mouse moves
     * over a hot spot.
     */
    private class MouseInputHandler implements MouseInputListener {

        /**
         * Set to true if the drag operation is moving the window.
         */
        private boolean isMovingWindow;

        /**
         * Used to determine the corner the resize is occuring from.
         */
        private int dragCursor;

        /**
         * X location the mouse went down on for a drag operation.
         */
        private int dragOffsetX;

        /**
         * Y location the mouse went down on for a drag operation.
         */
        private int dragOffsetY;

        /**
         * Width of the window when the drag started.
         */
        private int dragWidth;

        /**
         * Height of the window when the drag started.
         */
        private int dragHeight;

        /*
		 * PrivilegedExceptionAction needed by mouseDragged method to
		 * obtain new location of window on screen during the drag.
         */
        /**
         * The get location action.
         */
        private final PrivilegedExceptionAction getLocationAction = new PrivilegedExceptionAction() {
            @Override
            public Object run() throws HeadlessException {
                return MouseInfo.getPointerInfo().getLocation();
            }
        };

        @Override
        public void mousePressed(MouseEvent ev) {
            JRootPane rootPane = getRootPane();

            if (rootPane.getWindowDecorationStyle() == JRootPane.NONE)
                return;
            Point dragWindowOffset = ev.getPoint();
            Window w = (Window) ev.getSource();
            if (w != null)
                w.toFront();
            Point convertedDragWindowOffset = SwingUtilities.convertPoint(w,
                    dragWindowOffset, getTitlePane());

            Frame f = null;
            Dialog d = null;

            if (w instanceof Frame)
                f = (Frame) w;
            else if (w instanceof Dialog)
                d = (Dialog) w;

            int frameState = (f != null) ? f.getExtendedState() : 0;

            if (getTitlePane() != null
                    && getTitlePane().contains(convertedDragWindowOffset)) {
                if ((f != null && ((frameState & Frame.MAXIMIZED_BOTH) == 0) || (d != null))
                        && dragWindowOffset.y >= BORDER_DRAG_THICKNESS
                        && dragWindowOffset.x >= BORDER_DRAG_THICKNESS
                        && dragWindowOffset.x < w.getWidth()
                        - BORDER_DRAG_THICKNESS) {
                    isMovingWindow = true;
                    dragOffsetX = dragWindowOffset.x;
                    dragOffsetY = dragWindowOffset.y;
                }
            } else if (f != null && f.isResizable()
                    && ((frameState & Frame.MAXIMIZED_BOTH) == 0)
                    || (d != null && d.isResizable())) {
//				System.out.println("dragOffsetX="+dragOffsetX+" dragOffsetY="+dragOffsetY); TODO
                dragOffsetX = dragWindowOffset.x;
                dragOffsetY = dragWindowOffset.y;
                dragWidth = w.getWidth();
                dragHeight = w.getHeight();
                dragCursor
                        = //				getCursor(calculateCorner(w, dragWindowOffset.x,dragWindowOffset.y)); // TODO TEST
                        getCursor_new(w, dragWindowOffset.x, dragWindowOffset.y);
            }
        }

        @Override
        public void mouseReleased(MouseEvent ev) {
            if (dragCursor != 0 && window != null && !window.isValid()) {
                // Some Window systems validate as you resize, others won't,
                // thus the check for validity before repainting.
                window.validate();
                getRootPane().repaint();
            }
            isMovingWindow = false;
            dragCursor = 0;
        }

        @Override
        public void mouseMoved(MouseEvent ev) {
            JRootPane root = getRootPane();

            if (root.getWindowDecorationStyle() == JRootPane.NONE)
                return;

            Window w = (Window) ev.getSource();

            Frame f = null;
            Dialog d = null;

            if (w instanceof Frame)
                f = (Frame) w;
            else if (w instanceof Dialog)
                d = (Dialog) w;

            // Update the cursor
            int cursor
                    = //接下来1）测试算法的正确性 2）测试极端情况：即border小于或部分小于BORDER_THINNESS，3）写注释、整理代码！
                    //				getCursor(calculateCorner(w, ev.getX(), ev.getY()));// TODO Test!!
                    getCursor_new(w, ev.getX(), ev.getY());

            if (cursor != 0
                    && ((f != null && (f.isResizable() && (f.getExtendedState() & Frame.MAXIMIZED_BOTH) == 0)) || (d != null && d
                    .isResizable())))
                w.setCursor(Cursor.getPredefinedCursor(cursor));
            else
                w.setCursor(lastCursor);
        }

        /**
         * Adjust.
         *
         * @param bounds the bounds
         * @param min the min
         * @param deltaX the delta x
         * @param deltaY the delta y
         * @param deltaWidth the delta width
         * @param deltaHeight the delta height
         */
        private void adjust(Rectangle bounds, Dimension min, int deltaX,
                int deltaY, int deltaWidth, int deltaHeight) {
            bounds.x += deltaX;
            bounds.y += deltaY;
            bounds.width += deltaWidth;
            bounds.height += deltaHeight;
            if (min != null) {
                if (bounds.width < min.width) {
                    int correction = min.width - bounds.width;
                    if (deltaX != 0)
                        bounds.x -= correction;
                    bounds.width = min.width;
                }
                if (bounds.height < min.height) {
                    int correction = min.height - bounds.height;
                    if (deltaY != 0)
                        bounds.y -= correction;
                    bounds.height = min.height;
                }
            }
        }

        @Override
        public void mouseDragged(MouseEvent ev) {
            Window w = (Window) ev.getSource();
            Point pt = ev.getPoint();

            if (isMovingWindow) {
                Point windowPt;
                try {
                    windowPt = (Point) AccessController
                            .doPrivileged(getLocationAction);
                    windowPt.x = windowPt.x - dragOffsetX;
                    windowPt.y = windowPt.y - dragOffsetY;
                    w.setLocation(windowPt);
                } catch (PrivilegedActionException e) {
                }
            } else if (dragCursor != 0) {
                Rectangle r = w.getBounds();
                Rectangle startBounds = new Rectangle(r);
                Dimension min = w.getMinimumSize();

                switch (dragCursor) {
                    case Cursor.E_RESIZE_CURSOR:
                        adjust(r, min, 0, 0, pt.x + (dragWidth - dragOffsetX)
                                - r.width, 0);
                        break;
                    case Cursor.S_RESIZE_CURSOR:
                        adjust(r, min, 0, 0, 0, pt.y
                                + (dragHeight - dragOffsetY) - r.height);
                        break;
                    case Cursor.N_RESIZE_CURSOR:
                        adjust(r, min, 0, pt.y - dragOffsetY, 0,
                                -(pt.y - dragOffsetY));
                        break;
                    case Cursor.W_RESIZE_CURSOR:
                        adjust(r, min, pt.x - dragOffsetX, 0,
                                -(pt.x - dragOffsetX), 0);
                        break;
                    case Cursor.NE_RESIZE_CURSOR:
                        adjust(r, min, 0, pt.y - dragOffsetY, pt.x
                                + (dragWidth - dragOffsetX) - r.width,
                                -(pt.y - dragOffsetY));
                        break;
                    case Cursor.SE_RESIZE_CURSOR:
                        adjust(r, min, 0, 0, pt.x + (dragWidth - dragOffsetX)
                                - r.width, pt.y + (dragHeight - dragOffsetY)
                                - r.height);
                        break;
                    case Cursor.NW_RESIZE_CURSOR:
                        adjust(r, min, pt.x - dragOffsetX, pt.y - dragOffsetY,
                                -(pt.x - dragOffsetX), -(pt.y - dragOffsetY));
                        break;
                    case Cursor.SW_RESIZE_CURSOR:
                        adjust(r, min, pt.x - dragOffsetX, 0,
                                -(pt.x - dragOffsetX), pt.y
                                + (dragHeight - dragOffsetY) - r.height);
                        break;
                    default:
                        break;
                }
                if (!r.equals(startBounds)) {
                    w.setBounds(r);
                    // Defer repaint/validate on mouseReleased unless dynamic
                    // layout is active.
                    if (Toolkit.getDefaultToolkit().isDynamicLayoutActive()) {
                        w.validate();
                        getRootPane().repaint();
                    }
                }
            }
        }

        @Override
        public void mouseEntered(MouseEvent ev) {
            Window w = (Window) ev.getSource();
            lastCursor = w.getCursor();
            mouseMoved(ev);
        }

        @Override
        public void mouseExited(MouseEvent ev) {
            Window w = (Window) ev.getSource();
            // TODO ###### Hack：因Swing鼠标事件问题，拖动过快的话很多时候没法正常地保留和设置lastCursor
            //					从而导致经常性的退出拖动后，拖动时的鼠标样式还在，这样很不爽，这应该是swing
            //					的鼠标事件不精确导致的或其它问题。目前不如干脃在退出拖动时强制还原到默认鼠标，
            //					虽然在极少情况下可能回不到用户真正的lastCursor，但起码能解决目前在BueatyEye中
            //					因大border而频繁出现的这个问题了，先这么滴吧！
//			w.setCursor(lastCursor);
            w.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        }

        @Override
        public void mouseClicked(MouseEvent ev) {
            Window w = (Window) ev.getSource();
            Frame f;

            if (w instanceof Frame)
                f = (Frame) w;
            else
                return;

            Point convertedPoint = SwingUtilities.convertPoint(w,
                    ev.getPoint(), getTitlePane());

            int state = f.getExtendedState();
            if (getTitlePane() != null
                    && getTitlePane().contains(convertedPoint))
                if ((ev.getClickCount() % 2) == 0
                        && ((ev.getModifiers() & InputEvent.BUTTON1_MASK) != 0))
                    if (f.isResizable())
                        if ((state & Frame.MAXIMIZED_BOTH) != 0)
                            f.setExtendedState(state & ~Frame.MAXIMIZED_BOTH);
                        else
                            f.setExtendedState(state | Frame.MAXIMIZED_BOTH);
        }

        //*************************************************************** v3.2前参考自MetalRootPaneUI中的老边框拖放核心算法 START
        //** 老算法说明：Metal中的算法是假设窗口边框的border是规划的，即上下左右的inset都是一样的，它假定可拖动范围是整个
        //** 			窗体大小（包括border在内的大小）的BORDER_DRAG_THICKNESS常量范围内的上下左右区域，所以它的 算法在
        //** 			此前题下通过较巧妙的方法简单实现没有问题。
        //** 老算法缺陷：当窗口的边框不规划，如FrameBorderStyle.translucencyAppleLik这种时（上=17,左=27,右=27,下=37），
        //** 			此情况下只能假定一个最小值了，以前是取的17作为统一边框范围距离，那么像下部原本是37的inset，现在拖动
        //** 			范围是17，余下的原本是border里insets的10个像素也被算进窗口内容面板了，这样导致移动到下方时，明明
        //**			是在边缘位置，却不是处于拖动范围内（要再往下移10像素到达inset的第10~27像素范围内才行），这样就严重
        //** 			影响了用户体验。
        //* 2012-09-19 在BeautyEye v3.2中的BERootPaneUI，Jack Jiang启用了相比
        //* 原MetalRootPaneUI中更精确更好的边框拖放算法，以下方法暂时弃用，以后可以删除了！ START
//		/**
//		 * Returns the corner that contains the point <code>x</code>,
//		 * <code>y</code>, or -1 if the position doesn't match a corner.
//		 */
//		private int calculateCorner(Window w, int x, int y)
//		{
//			Insets insets = w.getInsets();
//			int xPosition = calculatePosition(x - insets.left, w.getWidth()
//					- insets.left - insets.right);
//			int yPosition = calculatePosition(y - insets.top, w.getHeight()
//					- insets.top - insets.bottom);
//			
//			if (xPosition == -1 || yPosition == -1)
//			{
//				return -1;
//			}
//			return yPosition * 5 + xPosition;
//		}
//		/**
//		 * Returns the Cursor to render for the specified corner. This returns
//		 * 0 if the corner doesn't map to a valid Cursor
//		 */
//		private int getCursor(int corner)
//		{
//			if (corner == -1)
//			{
//				return 0;
//			}
//			return cursorMapping[corner];
//		}
//		/**
//		 * Returns an integer indicating the position of <code>spot</code>
//		 * in <code>width</code>. The return value will be:
//		 * 0 if < BORDER_DRAG_THICKNESS
//		 * 1 if < CORNER_DRAG_WIDTH
//		 * 2 if >= CORNER_DRAG_WIDTH && < width - BORDER_DRAG_THICKNESS
//		 * 3 if >= width - CORNER_DRAG_WIDTH
//		 * 4 if >= width - BORDER_DRAG_THICKNESS
//		 * 5 otherwise
//		 */
//		private int calculatePosition(int spot, int width)
//		{
////			Insets iss = getRootPane().getInsets();
////			System.out.println("ississ="+iss); //TODO
//			
//			if (spot < BORDER_DRAG_THICKNESS)
//			{
//				return 0;
//			}
//			if (spot < CORNER_DRAG_WIDTH)
//			{
//				return 1;
//			}
//			if (spot >= (width - BORDER_DRAG_THICKNESS))
//			{
//				return 4;
//			}
//			if (spot >= (width - CORNER_DRAG_WIDTH))
//			{
//				return 3;
//			}
//			return 2;
//		}//********************************************************************* v3.2前的老边框拖放核心算法 END
        //********************************************************************* v3.2版启用的新边框拖放核心算法 SART
        //** 新算法说明：v3.2中启用的新算法的原理是把可拖动范围限定在内容区（即整个窗体大小减去Border后的真正工作区）
        //**			往外的一个固定的BORDER_DRAG_THICKNESS区域内，即不管理你把窗口的border设置多么不规划，我的用户拖
        //**			动区永远是这一个范围内，这就保证用户体验，较好的解决了老算法的缺陷。
        /**
         * Gets the cursor_new.
         *
         * @param w the w
         * @param x the x
         * @param y the y
         * @return the cursor_new
         */
        public int getCursor_new(Window w, int x, int y) {
            Insets insets = w.getInsets();
            return getCursor_new(x - insets.left, y - insets.top,
                    w.getWidth() - insets.left - insets.right,
                    w.getHeight() - insets.top - insets.bottom);
        }

        /**
         * 新的窗口边框拖动算法的实现是把可拖动区分成8个距形区，当鼠标动到对应
         * 的区里就计算出是是向哪个方向拖动，比MetalRootPaneUI中的简易方法要明确和精确。
         * <p>
         * 可拖动判断区示意图：<br>
         * <u>红色到蓝色的整个区域是窗口的border范围，红色到灰色的区域是固定的可拖动区，红色到灰色的区域是固定的，
         * 红色到蓝色的区域因border不同而不一样。</u><br>
         * <b>注意：</b>算法中要注意一种极端情况，就是Border的一部分或全部都小于可拖动区的情况，以下算法应该也
         * 是没有问题的，无非算出的8可拖动距形区坐标有负的情况，初步测试过没影响，以后还是注意一下！
         * <table border="1" width="28%" cellpadding="10" height="185" bordercolor="#000080">
         * <tr>
         * <td align="center">
         * <table border="1" width="88%" id="table1" height="148" bordercolor="#808080">
         * <tr>
         * <td width="27" height="25" align="center">R1</td>
         * <td height="25" align="center">R2</td>
         * <td width="25" height="25" align="center">R3</td>
         * </tr>
         * <tr>
         * <td width="27" align="center">R8</td>
         * <td align="center" bordercolor="#FF0000">可视工作区</td>
         * <td width="25" align="center">R4</td>
         * </tr>
         * <tr>
         * <td width="27" height="25" align="center">R7</td>
         * <td height="25" align="center">R6</td>
         * <td width="25" height="25" align="center">R5</td>
         * </tr>
         * </table>
         * </td>
         * </tr>
         * </table>.
         *
         * @param x the x
         * @param y the y
         * @param w the w
         * @param h the h
         * @return the cursor_new
         */
        public int getCursor_new(int x, int y, int w, int h) {
            int B = BORDER_DRAG_THICKNESS;

            Insets iss = getRootPane().getInsets();
            int topI = iss.top, bottomI = iss.bottom, leftI = iss.left, rightI = iss.right;

            //8个拖动检测距形区
            Rectangle r1 = new Rectangle(leftI - B, topI - B, B, B);
            Rectangle r2 = new Rectangle(leftI, topI - B, w - leftI - rightI, B);
            Rectangle r3 = new Rectangle(w - rightI, topI - B, B, B);
            Rectangle r4 = new Rectangle(w - rightI, topI, B, h - topI - bottomI);
            Rectangle r5 = new Rectangle(w - rightI, h - bottomI, B, B);
            Rectangle r6 = new Rectangle(leftI, h - bottomI, w - leftI - rightI, B);
            Rectangle r7 = new Rectangle(leftI - B, h - bottomI, B, B);
            Rectangle r8 = new Rectangle(leftI - B, topI, B, h - topI - bottomI);

            Point p = new Point(x, y);
            int cc = 0;

            if (r1.contains(p))
                cc = Cursor.NW_RESIZE_CURSOR;
            else if (r3.contains(p))
                cc = Cursor.NE_RESIZE_CURSOR;
            else if (r5.contains(p))
                cc = Cursor.SE_RESIZE_CURSOR;
            else if (r7.contains(p))
                cc = Cursor.SW_RESIZE_CURSOR;
            else if (r2.contains(p))
                cc = Cursor.N_RESIZE_CURSOR;
            else if (r4.contains(p))
                cc = Cursor.E_RESIZE_CURSOR;
            else if (r6.contains(p))
                cc = Cursor.S_RESIZE_CURSOR;
            else if (r8.contains(p))
                cc = Cursor.W_RESIZE_CURSOR;

            return cc;
        }
    }//********************************************************************* v3.2版启用的新边框拖放核心算法 END
}
