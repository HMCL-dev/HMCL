/*
 * Copyright (C) 2015 Jack Jiang(cngeeker.com) The BeautyEye Project. 
 * All rights reserved.
 * Project URL:https://github.com/JackJiang2011/beautyeye
 * Version 3.6
 * 
 * Jack Jiang PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 * 
 * BETitlePane.java at 2015-2-1 20:25:36, original version by Jack Jiang.
 * You can contact author with jb2011@163.com.
 */
package org.jackhuang.hmcl.laf.titlepane;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.Image;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.Paint;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Locale;

import javax.swing.AbstractAction;
import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JRootPane;
import javax.swing.JSeparator;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.UIResource;

import org.jackhuang.hmcl.laf.BeautyEyeLNFHelper;
import org.jackhuang.hmcl.laf.BEUtils;
import org.jackhuang.hmcl.laf.utils.MySwingUtilities2;

/**
 * 窗体的标题栏UI实现.
 *
 * @author Jack Jiang(jb2011@163.com)
 * @see javax.swing.plaf.metal.MetalTitlePane//(Java 1.5)
 */
public class BETitlePane extends JComponent {
//	MetalTitlePane

//	MetalLookAndFeel
//	WindowsLookAndFeel
    /**
     * The Constant handyEmptyBorder.
     */
    private static final Border handyEmptyBorder = new EmptyBorder(0, 0, 0, 0);

    /**
     * The Constant IMAGE_HEIGHT.
     */
    private static final int IMAGE_HEIGHT = 16;

    /**
     * The Constant IMAGE_WIDTH.
     */
    private static final int IMAGE_WIDTH = 16;

    /**
     * PropertyChangeListener added to the JRootPane.
     */
    private PropertyChangeListener propertyChangeListener;

    /**
     * JMenuBar, typically renders the system menu items.
     */
    private JMenuBar menuBar;
    /**
     * Action used to close the Window.
     */
    private Action closeAction;

    /**
     * Action used to iconify the Frame.
     */
    private Action iconifyAction;

    /**
     * Action to restore the Frame size.
     */
    private Action restoreAction;

    /**
     * Action to restore the Frame size.
     */
    private Action maximizeAction;

    /**
     * 设置action（功能暂未实现！）.
     */
    private Action setupAction;

    /**
     * Button used to maximize or restore the Frame.
     */
    private JButton toggleButton;

    /**
     * Button used to maximize or restore the Frame.
     */
    private JButton iconifyButton;

    /**
     * Button used to maximize or restore the Frame.
     */
    private JButton closeButton;

    /**
     * Icon used for toggleButton when window is normal size.
     */
    private Icon maximizeIcon;
    private Icon maximizeIcon_rover;
    private Icon maximizeIcon_pressed;

    /**
     * Icon used for toggleButton when window is maximized.
     */
    private Icon minimizeIcon;
    private Icon minimizeIcon_rover;
    private Icon minimizeIcon_pressed;

    /**
     * 设置按钮（功能暂未实现！）.
     */
    private JButton setupButton;

    /**
     * Listens for changes in the state of the Window listener to update the
     * state of the widgets.
     */
    private WindowListener windowListener;

    /**
     * Window we're currently in.
     */
    private Window window;

    /**
     * JRootPane rendering for.
     */
    private JRootPane rootPane;

    /**
     * Room remaining in title for bumps.
     */
    private int buttonsWidth;

    /**
     * Buffered Frame.state property. As state isn't bound, this is kept to
     * determine when to avoid updating widgets.
     */
    private int state;

    /**
     * MetalRootPaneUI that created us.
     */
    private BERootPaneUI rootPaneUI;

    // Colors
    /**
     * The inactive background.
     */
    private Color inactiveBackground = UIManager.getColor("inactiveCaption");

    /**
     * The inactive foreground.
     */
    private Color inactiveForeground = UIManager.getColor("inactiveCaptionText");

    /**
     * The inactive shadow.
     */
    private Color inactiveShadow = UIManager.getColor("inactiveCaptionBorder");

    /**
     * The active background.
     */
    private Color activeBackground = null;

    /**
     * The active foreground.
     */
    private Color activeForeground = null;

    /**
     * The active shadow.
     */
    private Color activeShadow = null;

    /**
     * Instantiates a new bE title pane.
     *
     * @param root the root
     * @param ui the ui
     */
    public BETitlePane(JRootPane root, BERootPaneUI ui) {
        this.rootPane = root;
        rootPaneUI = ui;

        state = -1;

        installSubcomponents();
        determineColors();
        installDefaults();

        setLayout(createLayout());
    }

    /**
     * Uninstalls the necessary state.
     */
    private void uninstall() {
        uninstallListeners();
        window = null;
        removeAll();
    }

    /**
     * Installs the necessary listeners.
     */
    private void installListeners() {
        if (window != null) {
            windowListener = createWindowListener();
            window.addWindowListener(windowListener);
            propertyChangeListener = createWindowPropertyChangeListener();
            window.addPropertyChangeListener(propertyChangeListener);
        }
    }

    /**
     * Uninstalls the necessary listeners.
     */
    private void uninstallListeners() {
        if (window != null) {
            window.removeWindowListener(windowListener);
            window.removePropertyChangeListener(propertyChangeListener);
        }
    }

    /**
     * Returns the <code>WindowListener</code> to add to the
     * <code>Window</code>.
     *
     * @return the window listener
     */
    private WindowListener createWindowListener() {
        return new WindowHandler();
    }

    /**
     * Returns the <code>PropertyChangeListener</code> to install on the
     * <code>Window</code>.
     *
     * @return the property change listener
     */
    private PropertyChangeListener createWindowPropertyChangeListener() {
        return new PropertyChangeHandler();
    }

    /**
     * Returns the <code>JRootPane</code> this was created for.
     *
     * @return the root pane
     */
    @Override
    public JRootPane getRootPane() {
        return rootPane;
    }

    /**
     * Returns the decoration style of the <code>JRootPane</code>.
     *
     * @return the window decoration style
     */
    private int getWindowDecorationStyle() {
        return getRootPane().getWindowDecorationStyle();
    }

    @Override
    public void addNotify() {
        try {
            super.addNotify();
        } catch (Exception e) {
        }
        uninstallListeners();

        window = SwingUtilities.getWindowAncestor(this);
        if (window != null) {
            if (window instanceof Frame) {
                setState(((Frame) window).getExtendedState());

                //* 说明请见：BeautyEyeLNFHelper.setMaximizedBoundForFrame
                if (BeautyEyeLNFHelper.setMaximizedBoundForFrame)
                    //* 此处设置窗口的最大边界是为了解决窗口最大化时覆盖
                    //* 操作系统的task bar 的问题，它是sun一直没有解决的问题
                    //* ，目前没有其它好方法，只能如此解决了
                    setFrameMaxBound((Frame) window);
            } else
                setState(0);
            setActive(window.isActive());
            installListeners();
        }
    }

    /**
     * 设置窗口的最大边界.
     * <p>
     * 本方法由Jack Jiang 于2012-09-20添加的。
     *
     * @param f the new frame max bound
     * @see
     * org.jb2011.lnf.beautyeye.BeautyEyeLNFHelper#setMaximizedBoundForFrame
     * @since 3.2
     */
    private void setFrameMaxBound(Frame f) {
        GraphicsConfiguration gc = f.getGraphicsConfiguration();
        Insets screenInsets = Toolkit.getDefaultToolkit().getScreenInsets(gc);
        Rectangle screenBounds = gc.getBounds();
        int x = Math.max(0, screenInsets.left);
        int y = Math.max(0, screenInsets.top);
        int w = screenBounds.width - (screenInsets.left + screenInsets.right);
        int h = screenBounds.height - (screenInsets.top + screenInsets.bottom);
        // Keep taskbar visible
        f.setMaximizedBounds(new Rectangle(x, y, w, h));
    }

    @Override
    public void removeNotify() {
        super.removeNotify();

        uninstallListeners();
        window = null;
    }

    /**
     * Adds any sub-Components contained in the <code>MetalTitlePane</code>.
     */
    private void installSubcomponents() {
        int decorationStyle = getWindowDecorationStyle();

        if (decorationStyle == JRootPane.FRAME || decorationStyle == JRootPane.PLAIN_DIALOG) {
            createActions();
            menuBar = createMenuBar();
            add(menuBar);
            createButtons();

            add(closeButton);

            //~* RootPane.setupButtonVisible是jb2011自定义的属性哦，目的是控制这个当前用于演示的设置按钮的可见性
            Object isSetupButtonVisibleObj = UIManager.get("RootPane.setupButtonVisible");
            //如果开发者没有设置此属性则默认认为是true(即显示该按钮)
            boolean isSetupButtonVisible = (isSetupButtonVisibleObj == null ? true : (Boolean) isSetupButtonVisibleObj);
            if (isSetupButtonVisible)
                //加入设置按钮
                add(setupButton);

            if (decorationStyle != JRootPane.PLAIN_DIALOG)//!
            {
                add(iconifyButton);
                add(toggleButton);
                menuBar.setEnabled(false);
            }
        } else if ( //				decorationStyle == JRootPane.PLAIN_DIALOG
                //				||
                decorationStyle == JRootPane.INFORMATION_DIALOG
                || decorationStyle == JRootPane.ERROR_DIALOG
                || decorationStyle == JRootPane.COLOR_CHOOSER_DIALOG
                || decorationStyle == JRootPane.FILE_CHOOSER_DIALOG
                || decorationStyle == JRootPane.QUESTION_DIALOG
                || decorationStyle == JRootPane.WARNING_DIALOG) {
            createActions();
            createButtons();
            add(closeButton);
        }
    }

    /**
     * Determines the Colors to draw with.
     */
    private void determineColors() {
        switch (getWindowDecorationStyle()) {
            case JRootPane.FRAME:
                activeBackground = UIManager.getColor("activeCaption");
                activeForeground = UIManager.getColor("activeCaptionText");
                activeShadow = UIManager.getColor("activeCaptionBorder");
                break;
            case JRootPane.ERROR_DIALOG:
                activeBackground = UIManager
                        .getColor("OptionPane.errorDialog.titlePane.background");
                activeForeground = UIManager
                        .getColor("OptionPane.errorDialog.titlePane.foreground");
                activeShadow = UIManager
                        .getColor("OptionPane.errorDialog.titlePane.shadow");
                break;
            case JRootPane.QUESTION_DIALOG:
            case JRootPane.COLOR_CHOOSER_DIALOG:
            case JRootPane.FILE_CHOOSER_DIALOG:
                activeBackground = UIManager
                        .getColor("OptionPane.questionDialog.titlePane.background");
                activeForeground = UIManager
                        .getColor("OptionPane.questionDialog.titlePane.foreground");
                activeShadow = UIManager
                        .getColor("OptionPane.questionDialog.titlePane.shadow");
                break;
            case JRootPane.WARNING_DIALOG:
                activeBackground = UIManager
                        .getColor("OptionPane.warningDialog.titlePane.background");
                activeForeground = UIManager
                        .getColor("OptionPane.warningDialog.titlePane.foreground");
                activeShadow = UIManager
                        .getColor("OptionPane.warningDialog.titlePane.shadow");
                break;
            case JRootPane.PLAIN_DIALOG:
            case JRootPane.INFORMATION_DIALOG:
            default:
                activeBackground = UIManager.getColor("activeCaption");
                activeForeground = UIManager.getColor("activeCaptionText");
                activeShadow = UIManager.getColor("activeCaptionBorder");
                break;
        }
    }

    /**
     * Installs the fonts and necessary properties on the MetalTitlePane.
     */
    private void installDefaults() {
        setFont(UIManager.getFont("InternalFrame.titleFont", getLocale()));
    }

    /**
     * Uninstalls any previously installed UI values.
     */
    private void uninstallDefaults() {
    }

    /**
     * Returns the <code>JMenuBar</code> displaying the appropriate system menu
     * items.
     *
     * @return the j menu bar
     */
    protected JMenuBar createMenuBar() {
        menuBar = new SystemMenuBar();
        menuBar.setOpaque(false);
        menuBar.setFocusable(false);
        menuBar.setBorderPainted(true);
        menuBar.add(createMenu());
        return menuBar;
    }

    /**
     * Closes the Window.
     */
    private void close() {
        Window w = getWindow();

        if (w != null)
            w.dispatchEvent(new WindowEvent(w,
                    WindowEvent.WINDOW_CLOSING));
    }

    /**
     * Iconifies the Frame.
     */
    private void iconify() {
        Frame frame = getFrame();
        if (frame != null)
            frame.setExtendedState(state | Frame.ICONIFIED);
    }

    /**
     * Maximizes the Frame.
     */
    private void maximize() {
        Frame frame = getFrame();
        if (frame != null)
            frame.setExtendedState(state | Frame.MAXIMIZED_BOTH);
    }

    /**
     * Restores the Frame size.
     */
    private void restore() {
        Frame frame = getFrame();

        if (frame == null)
            return;

        if ((state & Frame.ICONIFIED) != 0)
            frame.setExtendedState(state & ~Frame.ICONIFIED);
        else
            frame.setExtendedState(state & ~Frame.MAXIMIZED_BOTH);
    }

    /**
     * Create the <code>Action</code>s that get associated with the buttons and
     * menu items.
     */
    private void createActions() {
        closeAction = new CloseAction();
        if (getWindowDecorationStyle() == JRootPane.FRAME) {
            iconifyAction = new IconifyAction();
            restoreAction = new RestoreAction();
            maximizeAction = new MaximizeAction();

            setupAction = new AbstractAction(UIManager.getString("BETitlePane.setupButtonText", getLocale())) {
                @Override
                public void actionPerformed(ActionEvent e) {
                    JOptionPane.showMessageDialog(rootPane, "This button just used for demo."
                            + "In the future,you can customize it.\n"
                            + "Now, you can set UIManager.put(\"RootPane.setupButtonVisible\", false) to hide it(detault is true).\n"
                            + "BeautyEye L&F developed by Jack Jiang, you can mail with jb2011@163.com.");
                }
            };
        }
    }

    /**
     * Returns the <code>JMenu</code> displaying the appropriate menu items for
     * manipulating the Frame.
     *
     * @return the j menu
     */
    private JMenu createMenu() {
        JMenu menu = new JMenu("");
        menu.setOpaque(false);//本行一定要，否则将导致窗口图标区会绘制Menu的背景！这是Java Metal主题的Bug! -- jack,2009-09-11
        if (getWindowDecorationStyle() == JRootPane.FRAME
                || getWindowDecorationStyle() == JRootPane.PLAIN_DIALOG//现在也给dialog加上菜单项（但只有关闭项）
                )
            addMenuItems(menu);
        return menu;
    }

    /**
     * Adds the necessary <code>JMenuItem</code>s to the passed in menu.
     *
     * @param menu the menu
     */
    private void addMenuItems(JMenu menu) {
        Locale locale = getRootPane().getLocale();
        menu.setToolTipText(//"窗口相关操作.");
                UIManager.getString("BETitlePane.titleMenuToolTipText", getLocale()));

        JMenuItem mi;
        int mnemonic;
        if (getWindowDecorationStyle() == JRootPane.FRAME)//! 只有frame才有这些菜单项
        {
            mi = menu.add(restoreAction);
            mnemonic = BEUtils.getInt("MetalTitlePane.restoreMnemonic", -1);
            if (mnemonic != -1)
                mi.setMnemonic(mnemonic);

            mi = menu.add(iconifyAction);
            mnemonic = BEUtils.getInt("MetalTitlePane.iconifyMnemonic", -1);
            if (mnemonic != -1)
                mi.setMnemonic(mnemonic);

            if (Toolkit.getDefaultToolkit().isFrameStateSupported(
                    Frame.MAXIMIZED_BOTH)) {
                mi = menu.add(maximizeAction);
                mnemonic = BEUtils.getInt("MetalTitlePane.maximizeMnemonic",
                        -1);
                if (mnemonic != -1)
                    mi.setMnemonic(mnemonic);
            }

            menu.add(new JSeparator());
        }

        mi = menu.add(closeAction);
        mnemonic = BEUtils.getInt("MetalTitlePane.closeMnemonic", -1);
        if (mnemonic != -1)
            mi.setMnemonic(mnemonic);
    }

    /**
     * Returns a <code>JButton</code> appropriate for placement on the
     * TitlePane.
     *
     * @return the j button
     */
    private JButton createTitleButton() {
        JButton button = new JButton();

        button.setFocusPainted(false);
        button.setFocusable(false);
        button.setOpaque(true);
        return button;
    }

    /**
     * Creates the Buttons that will be placed on the TitlePane.
     */
    private void createButtons() {

        setupButton = createTitleButton();
        setupButton.setAction(setupAction);
//		setupButton.setText("设置 ");
//		setupButton.putClientProperty("paintActive", Boolean.TRUE);
        setupButton.setBorder(handyEmptyBorder);
//		setupButton.getAccessibleContext().setAccessibleName("Close");
        setupButton.setIcon(UIManager.getIcon("Frame.setupIcon"));
        setButtonIcon(setupButton, setupButton.getIcon());// @since 3.5：同时设置rover和pressed时的icon效果
        setupButton.setContentAreaFilled(false);
//		setupButton.setToolTipText("设置");

        closeButton = createTitleButton();
        closeButton.setAction(closeAction);
        closeButton.setText(null);
        closeButton.putClientProperty("paintActive", Boolean.TRUE);
        closeButton.setBorder(handyEmptyBorder);
        closeButton.getAccessibleContext().setAccessibleName("Close");
        closeButton.setIcon(UIManager.getIcon("Frame.closeIcon"));
//		setButtonIcon(closeButton, closeButton.getIcon());// @since 3.5：同时设置rover和pressed时的icon效果
        closeButton.setRolloverIcon(UIManager.getIcon("Frame.closeIcon_rover"));
        closeButton.setPressedIcon(UIManager.getIcon("Frame.closeIcon_pressed"));
        closeButton.setContentAreaFilled(false);
        closeButton.setToolTipText(//"关闭");
                UIManager.getString("BETitlePane.closeButtonToolTipext", getLocale()));

        if (getWindowDecorationStyle() == JRootPane.FRAME) {
            maximizeIcon = UIManager.getIcon("Frame.maximizeIcon");
            maximizeIcon_rover = UIManager.getIcon("Frame.maximizeIcon_rover");
            maximizeIcon_pressed = UIManager.getIcon("Frame.maximizeIcon_pressed");

            minimizeIcon = UIManager.getIcon("Frame.minimizeIcon");
            minimizeIcon_rover = UIManager.getIcon("Frame.minimizeIcon_rover");
            minimizeIcon_pressed = UIManager.getIcon("Frame.minimizeIcon_pressed");

            iconifyButton = createTitleButton();
            iconifyButton.setAction(iconifyAction);
            iconifyButton.setText(null);
            iconifyButton.putClientProperty("paintActive", Boolean.TRUE);
            iconifyButton.setBorder(handyEmptyBorder);
            iconifyButton.getAccessibleContext().setAccessibleName("Iconify");
            iconifyButton.setIcon(UIManager.getIcon("Frame.iconifyIcon"));
//			setButtonIcon(iconifyButton, iconifyButton.getIcon());// @since 3.5：同时设置rover和pressed时的icon效果
            iconifyButton.setRolloverIcon(UIManager.getIcon("Frame.iconifyIcon_rover"));
            iconifyButton.setPressedIcon(UIManager.getIcon("Frame.iconifyIcon_pressed"));
            iconifyButton.setContentAreaFilled(false);
            iconifyButton.setToolTipText(//"最小化");
                    UIManager.getString("BETitlePane.iconifyButtonToolTipText", getLocale()));

            toggleButton = createTitleButton();
            toggleButton.setAction(restoreAction);
            toggleButton.putClientProperty("paintActive", Boolean.TRUE);
            toggleButton.setBorder(handyEmptyBorder);
            toggleButton.getAccessibleContext().setAccessibleName("Maximize");
            toggleButton.setIcon(maximizeIcon);
//			setButtonIcon(toggleButton, toggleButton.getIcon());// @since 3.5：同时设置rover和pressed时的icon效果
            toggleButton.setRolloverIcon(maximizeIcon_rover);
            toggleButton.setPressedIcon(maximizeIcon_pressed);
            toggleButton.setContentAreaFilled(false);
            toggleButton.setToolTipText(//"最大化");
                    UIManager.getString("BETitlePane.toggleButtonToolTipText", getLocale()));
        }
    }

    /**
     * 为按钮设置图标，并据原图icon自动调用方法
     * {@link #filterWithRescaleOp(ImageIcon, float, float, float, float)}
     * 使用滤镜分别生成RolloverIcon、PressedIcon并设置之.
     *
     * @param btn
     * @param ico
     * @since 3.5
     * @deprecated since 3.6
     */
    public static void setButtonIcon(AbstractButton btn, Icon ico) {
        //* 图片设定
        btn.setIcon(ico);
        if (ico != null && ico instanceof ImageIcon) {
            //rover时图片颜色变成红色
            btn.setRolloverIcon(BEUtils.filterWithRescaleOp((ImageIcon) ico, 2f, 1f, 1f, 1f));
            //press时图片颜色变成淡红色
            btn.setPressedIcon(BEUtils.filterWithRescaleOp((ImageIcon) ico, 2f, 1f, 1f, 0.5f));
        }
    }

    /**
     * Returns the <code>LayoutManager</code> that should be installed on the
     * <code>MetalTitlePane</code>.
     *
     * @return the layout manager
     */
    private LayoutManager createLayout() {
        return new TitlePaneLayout();
    }

    /**
     * Updates state dependant upon the Window's active state.
     *
     * @param isActive the new active
     */
    private void setActive(boolean isActive) {
        Boolean activeB = isActive ? Boolean.TRUE : Boolean.FALSE;
        closeButton.putClientProperty("paintActive", activeB);
        if (getWindowDecorationStyle() == JRootPane.FRAME) {
            iconifyButton.putClientProperty("paintActive", activeB);
            toggleButton.putClientProperty("paintActive", activeB);
        }
        // Repaint the whole thing as the Borders that are used have
        // different colors for active vs inactive
        getRootPane().repaint();
    }

    /**
     * Sets the state of the Window.
     *
     * @param state the new state
     */
    private void setState(int state) {
        setState(state, false);
    }

    /**
     * Sets the state of the window. If <code>updateRegardless</code> is true
     * and the state has not changed, this will update anyway.
     *
     * @param state the state
     * @param updateRegardless the update regardless
     */
    private void setState(int state, boolean updateRegardless) {
        Window w = getWindow();

        if (w != null && getWindowDecorationStyle() == JRootPane.FRAME) {
            if (this.state == state && !updateRegardless)
                return;
            Frame frame = getFrame();

            if (frame != null) {
                JRootPane p = getRootPane();

                if (((state & Frame.MAXIMIZED_BOTH) != 0)
                        && (p.getBorder() == null || (p
                        .getBorder() instanceof UIResource))
                        && frame.isShowing())
                    p.setBorder(null);
                else if ((state & Frame.MAXIMIZED_BOTH) == 0)
                    // This is a croak, if state becomes bound, this can
                    // be nuked.
                    rootPaneUI.installBorder(p);
                if (frame.isResizable()) {
                    if ((state & Frame.MAXIMIZED_BOTH) != 0) {
                        updateToggleButton(restoreAction, minimizeIcon, minimizeIcon_rover, minimizeIcon_pressed);
                        maximizeAction.setEnabled(false);
                        restoreAction.setEnabled(true);
                    } else {
                        updateToggleButton(maximizeAction, maximizeIcon, maximizeIcon_rover, maximizeIcon_pressed);
                        maximizeAction.setEnabled(true);
                        restoreAction.setEnabled(false);
                    }
                    if (toggleButton.getParent() == null
                            || iconifyButton.getParent() == null) {
                        add(toggleButton);
                        add(iconifyButton);
                        revalidate();
                        repaint();
                    }
                    toggleButton.setText(null);
                } else {
                    maximizeAction.setEnabled(false);
                    restoreAction.setEnabled(false);
                    if (toggleButton.getParent() != null) {
                        remove(toggleButton);
                        revalidate();
                        repaint();
                    }
                }
            } else {
                // Not contained in a Frame
                maximizeAction.setEnabled(false);
                restoreAction.setEnabled(false);
                iconifyAction.setEnabled(false);
                remove(toggleButton);
                remove(iconifyButton);
                revalidate();
                repaint();
            }
            closeAction.setEnabled(true);
            this.state = state;
        }
    }

    /**
     * Updates the toggle button to contain the Icon <code>icon</code>, and
     * Action <code>action</code>.
     *
     * @param action the action
     * @param icon the icon
     */
    private void updateToggleButton(Action action, Icon icon,
            Icon iconRover, Icon iconPressed) {
        toggleButton.setAction(action);
        toggleButton.setIcon(icon);
//		setButtonIcon(toggleButton, toggleButton.getIcon());// @since 3.5：同时设置rover和pressed时的icon效果：同时设置rover和pressed时的icon效果
        toggleButton.setRolloverIcon(iconRover);
        toggleButton.setPressedIcon(iconPressed);
        toggleButton.setText(null);
    }

    /**
     * Returns the Frame rendering in. This will return null if the
     * <code>JRootPane</code> is not contained in a <code>Frame</code>.
     *
     * @return the frame
     */
    private Frame getFrame() {
        Window w = getWindow();

        if (w instanceof Frame)
            return (Frame) w;
        return null;
    }

    /**
     * Returns the <code>Window</code> the <code>JRootPane</code> is contained
     * in. This will return null if there is no parent ancestor of the
     * <code>JRootPane</code>.
     *
     * @return the window
     */
    private Window getWindow() {
        return window;
    }

    /**
     * Returns the String to display as the title.
     *
     * @return the title
     */
    private String getTitle() {
        Window w = getWindow();

        if (w instanceof Frame)
            return ((Frame) w).getTitle();
        else if (w instanceof Dialog)
            return ((Dialog) w).getTitle();
        return null;
    }

    /**
     * Renders the TitlePane.
     *
     * @param g the g
     */
    @Override
    public void paintComponent(Graphics g) {
        // As state isn't bound, we need a convenience place to check
        // if it has changed. Changing the state typically changes the
        if (getFrame() != null)
            setState(getFrame().getExtendedState());
        JRootPane p = getRootPane();
        Window w = getWindow();
        boolean leftToRight = (w == null) ? p
                .getComponentOrientation().isLeftToRight() : w
                        .getComponentOrientation().isLeftToRight();
        boolean isSelected = (w == null) ? true : w.isActive();
        int width = getWidth();
        int height = getHeight();

        Color background;
        Color foreground;
        Color darkShadow;

        if (isSelected) {
            background = activeBackground;
            foreground = activeForeground;
            darkShadow = activeShadow;
        } else {
            background = inactiveBackground;
            foreground = inactiveForeground;
            darkShadow = inactiveShadow;
//			bumps = inactiveBumps;
        }
        //----------------------------------------------- 标题背景
        paintTitlePane(g, 0, 0, width, height, isSelected);

        //----------------------------------------------- 标题文字和图片
        int xOffset = leftToRight ? 5 : width - 5;

        if (getWindowDecorationStyle() == JRootPane.FRAME || getWindowDecorationStyle() == JRootPane.PLAIN_DIALOG)
            xOffset += leftToRight ? IMAGE_WIDTH + 5 : -IMAGE_WIDTH - 5;

        String theTitle = getTitle();
        if (theTitle != null) {
            FontMetrics fm = MySwingUtilities2.getFontMetrics(p, g);
            int yOffset = ((height - fm.getHeight()) / 2) + fm.getAscent();

            Rectangle rect = new Rectangle(0, 0, 0, 0);
            if (iconifyButton != null && iconifyButton.getParent() != null)
                rect = iconifyButton.getBounds();
            int titleW;

            if (leftToRight) {
                if (rect.x == 0)
                    rect.x = w.getWidth() - w.getInsets().right - 2;
                titleW = rect.x - xOffset - 4;
                theTitle = MySwingUtilities2.clipStringIfNecessary(p, fm,
                        theTitle, titleW);
            } else {
                titleW = xOffset - rect.x - rect.width - 4;
                theTitle = MySwingUtilities2.clipStringIfNecessary(p, fm,
                        theTitle, titleW);
                xOffset -= MySwingUtilities2.stringWidth(p, fm, theTitle);
            }

            int titleLength = MySwingUtilities2.stringWidth(p, fm, theTitle);
            g.setColor(foreground);
            MySwingUtilities2.drawString(p, g, theTitle, xOffset, yOffset);
            xOffset += leftToRight ? titleLength + 5 : -5;
        }
    }

    /**
     * Paint title pane.
     *
     * @param g the g
     * @param x the x
     * @param y the y
     * @param width the width
     * @param height the height
     * @param actived the actived
     */
    public static void paintTitlePane(Graphics g, int x, int y, int width,
            int height, boolean actived) {
        Graphics2D g2 = (Graphics2D) g;

        //是用图形进行填充的
        Paint oldpaint = g2.getPaint();
        g2.setPaint(BEUtils.createTexturePaint(
                __UI__.ICON.get("title", actived ? "active" : "inactive").getImage()));
        g2.fillRect(x, y, width, height);
        g2.setPaint(oldpaint);
    }

    /**
     * Actions used to <code>close</code> the <code>Window</code>.
     */
    private class CloseAction extends AbstractAction {

        /**
         * Instantiates a new close action.
         */
        public CloseAction() {
            super(UIManager.getString("BETitlePane.closeButtonToolTipext", getLocale()));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            close();
        }
    }

    /**
     * Actions used to <code>iconfiy</code> the <code>Frame</code>.
     */
    private class IconifyAction extends AbstractAction {

        /**
         * Instantiates a new iconify action.
         */
        public IconifyAction() {
            super(UIManager.getString("BETitlePane.iconifyButtonText", getLocale()));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            iconify();
        }
    }

    /**
     * Actions used to <code>restore</code> the <code>Frame</code>.
     */
    private class RestoreAction extends AbstractAction {

        /**
         * Instantiates a new restore action.
         */
        public RestoreAction() {
            super(UIManager.getString("BETitlePane.restoreButtonText", getLocale()));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            restore();
        }
    }

    /**
     * Actions used to <code>restore</code> the <code>Frame</code>.
     */
    private class MaximizeAction extends AbstractAction {

        public MaximizeAction() {
            super(UIManager.getString("BETitlePane.maximizeButtonText", getLocale()));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            maximize();
        }
    }

    /**
     * Class responsible for drawing the system menu. Looks up the image to draw
     * from the Frame associated with the <code>JRootPane</code>.
     */
    private class SystemMenuBar extends JMenuBar {

        @Override
        public void paint(Graphics g) {
            //## Bug FIX: Issue BELNF-10, 之前是用的MetalLNF中的代码（
            //## 它设计为不显示Dialog及其子类的图标）,此处就是要加上Dialog及其子类
            //## 窗口图标的处理
//			Frame frame = getFrame();// getFrame()方法意味只处理Frame及其子类图标（Dialog的就错误地跳过了）
            Window frame = getWindow();

            if (isOpaque()) {
                g.setColor(getBackground());
                g.fillRect(0, 0, getWidth(), getHeight());
            }

            //## Bug FIX: Issue BELNF-10
//			Image image = (frame != null) ? frame.getIconImage() : null;
            Image image = null;
            if (frame != null)
                // 如果是Frame及其子类则调用getIconImage取回最合适
                // 图标（跨平台时可能尺寸不一样，此方法返回最合适的）
                if (frame instanceof Frame)
                    image = ((Frame) frame).getIconImage();
                // 其它情况那就Dialog及其子类了，因它们没有getIconImage方法，
                // 那就只取frame.getIconImages()里的第1个图标吧（如蛤存在多个图标的话），
                // 它样处理虽跨平台考虑不足但总比MetalTitlePane里不显示Dialog图标强
                else if (frame.getIconImages() != null && frame.getIconImages().size() > 0)
                    image = frame.getIconImages().get(0);

            if (image != null)
                g.drawImage(image, 0, 0, IMAGE_WIDTH, IMAGE_HEIGHT, null);
            else {
                Icon icon = UIManager.getIcon("Frame.icon");//"InternalFrame.icon");
                if (icon != null)
                    icon.paintIcon(this, g, 0, 0);
            }
        }

        @Override
        public Dimension getMinimumSize() {
            return getPreferredSize();
        }

        @Override
        public Dimension getPreferredSize() {
            Dimension size = super.getPreferredSize();

            return new Dimension(Math.max(IMAGE_WIDTH, size.width), Math.max(
                    size.height, IMAGE_HEIGHT));
        }
    }

    private class TitlePaneLayout implements LayoutManager {

        @Override
        public void addLayoutComponent(String name, Component c) {
        }

        @Override
        public void removeLayoutComponent(Component c) {
        }

        @Override
        public Dimension preferredLayoutSize(Container c) {
            int height = computeHeight();
            return new Dimension(height, height);
        }

        @Override
        public Dimension minimumLayoutSize(Container c) {
            return preferredLayoutSize(c);
        }

        /**
         * Compute height.
         *
         * @return the int
         */
        private int computeHeight() {
            FontMetrics fm = rootPane.getFontMetrics(getFont());
            int fontHeight = fm.getHeight();
            fontHeight += 7;
            int iconHeight = 0;
            if (getWindowDecorationStyle() == JRootPane.FRAME) //					||getWindowDecorationStyle() == JRootPane.PLAIN_DIALOG)//

                iconHeight = IMAGE_HEIGHT;

            int finalHeight = Math.max(fontHeight, iconHeight);
            return finalHeight + 2;//* 改变此处的返回结果将直接改变窗口标题的高度哦
        }

        @Override
        public void layoutContainer(Container c) {
            boolean leftToRight = (window == null) ? getRootPane()
                    .getComponentOrientation().isLeftToRight() : window
                            .getComponentOrientation().isLeftToRight();

            int w = getWidth();
            int x;
            int y = 3;
            int spacing;
            int buttonHeight;
            int buttonWidth;

            if (closeButton != null && closeButton.getIcon() != null) {
                buttonHeight = closeButton.getIcon().getIconHeight();
                buttonWidth = closeButton.getIcon().getIconWidth();
            } else {
                buttonHeight = IMAGE_HEIGHT;
                buttonWidth = IMAGE_WIDTH;
            }

            // assumes all buttons have the same dimensions
            // these dimensions include the borders
            x = leftToRight ? w : 0;

            spacing = 5;
            x = leftToRight ? spacing : w - buttonWidth - spacing;
            if (menuBar != null)
                //* js 2010-03-30
                //* 原MetalTitledPane的Bug:当存在关闭按钮时，窗口图标的大小是已关闭按钮的大小来决定的，这样不合理
                menuBar.setBounds(x,
                        y + 2//+2偏移量由Jack Jiang于2012-09-24日加上，目的是使得图标与文本保持在同一中心位置
                        // TODO 目前BueautyEye和MetalLookAndFeel的标题图标位置算法都有优化的余地：y轴坐标自动按title高度居中会
                        //	        适用更多场景，现在的算法如果title高度变的很大，则这些位置都是固定。不过按MetalLNF的思路，这些高度
                        //	        是与整体美感一样，不应被随意改动的，也可以说不需要优化，目前就这么的吧，没有关系。
                        ,
                         IMAGE_HEIGHT, IMAGE_WIDTH);//buttonWidth, buttonHeight);

            x = leftToRight ? w : 0;
            spacing = 4;
            x += leftToRight ? -spacing - buttonWidth : spacing;
            if (closeButton != null)
                closeButton.setBounds(x, y, buttonWidth, buttonHeight);

            if (!leftToRight)
                x += buttonWidth;

            if (getWindowDecorationStyle() == JRootPane.FRAME) {
                if (Toolkit.getDefaultToolkit().isFrameStateSupported(
                        Frame.MAXIMIZED_BOTH))
                    if (toggleButton.getParent() != null) {
                        spacing = 2;//10!!!
                        x += leftToRight ? -spacing - buttonWidth : spacing;
                        toggleButton.setBounds(x, y, buttonWidth, buttonHeight);
                        if (!leftToRight)
                            x += buttonWidth;
                    }

                if (iconifyButton != null && iconifyButton.getParent() != null) {
                    spacing = 2;
                    x += leftToRight ? -spacing - buttonWidth : spacing;
                    iconifyButton.setBounds(x, y, buttonWidth, buttonHeight);
                    if (!leftToRight)
                        x += buttonWidth;
                }

                //“设置”按钮
                if (setupButton != null) {
                    spacing = 2;
                    int stringWidth = BEUtils.getStrPixWidth(setupButton.getFont(), setupButton.getText());
                    x += leftToRight ? -spacing - buttonWidth - stringWidth : spacing;
                    setupButton.setBounds(x, y, buttonWidth + stringWidth, buttonHeight);
                    if (!leftToRight)
                        x += buttonWidth;
                }
            }
            buttonsWidth = leftToRight ? w - x : x;
        }
    }

    /**
     * PropertyChangeListener installed on the Window. Updates the necessary
     * state as the state of the Window changes.
     */
    private class PropertyChangeHandler implements PropertyChangeListener {

        @Override
        public void propertyChange(PropertyChangeEvent pce) {
            String name = pce.getPropertyName();

            if (null != name) // Frame.state isn't currently bound.
                switch (name) {
                    case "resizable":
                    case "state":
                        Frame frame = getFrame();
                        if (frame != null)
                            setState(frame.getExtendedState(), true);
                        if ("resizable".equals(name))
                            getRootPane().repaint();
                        break;
                    case "title":
                        repaint();
                        break;
                    case "componentOrientation":
                    case "iconImage":
                        revalidate();
                        repaint();
                        break;
                    default:
                        break;
                }
        }
    }

    /**
     * WindowListener installed on the Window, updates the state as necessary.
     */
    private class WindowHandler extends WindowAdapter {

        @Override
        public void windowActivated(WindowEvent ev) {
            setActive(true);
        }

        @Override
        public void windowDeactivated(WindowEvent ev) {
            setActive(false);
        }
    }
}
