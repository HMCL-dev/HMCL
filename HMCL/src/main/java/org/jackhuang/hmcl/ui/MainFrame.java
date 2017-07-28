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
package org.jackhuang.hmcl.ui;

import org.jackhuang.hmcl.util.ui.GaussionPanel;
import org.jackhuang.hmcl.util.ui.IRepaint;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.Transparency;
import java.awt.Window;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import org.jackhuang.hmcl.api.HMCLApi;
import org.jackhuang.hmcl.Main;
import org.jackhuang.hmcl.api.PluginManager;
import org.jackhuang.hmcl.api.event.config.ThemeChangedEvent;
import org.jackhuang.hmcl.setting.Settings;
import org.jackhuang.hmcl.util.C;
import org.jackhuang.hmcl.util.ui.MessageBox;
import org.jackhuang.hmcl.util.StrUtils;
import org.jackhuang.hmcl.api.HMCLog;
import org.jackhuang.hmcl.util.ui.BasicColors;
import org.jackhuang.hmcl.util.ui.DropShadowBorder;
import org.jackhuang.hmcl.util.ui.GraphicsUtils;
import org.jackhuang.hmcl.util.ui.SwingUtils;
import org.jackhuang.hmcl.util.ui.TintablePanel;
import org.jackhuang.hmcl.api.auth.IAuthenticator;
import org.jackhuang.hmcl.api.ui.Theme;
import org.jackhuang.hmcl.api.ui.TopTabPage;


/**
 *
 * @author huangyuhui
 */
public final class MainFrame extends DraggableFrame implements IRepaint {

    public static final MainFrame INSTANCE = new MainFrame();

    TintablePanel centralPanel;
    JPanel header, infoSwap, realPanel;
    CardLayout infoLayout;
    JLabel windowTitle;
    GaussionPanel backgroundLabel;
    DropShadowBorder border;
    boolean enableShadow;
    String defaultTitle;

    private int tempW, tempH;

    void setContentSize(int w, int h) {
        setSize(w, h);
        tempW = w;
        tempH = h;
    }

    MainFrame() {
        setUndecorated(!Settings.getInstance().isDecorated());
        defaultTitle = isUndecorated() ? Main.makeTitle() : "";
        enableShadow = Settings.getInstance().isEnableShadow() && isUndecorated();
        if (enableShadow)
            setContentSize(834, 542);
        else
            setContentSize(802, 511);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setTitle(Main.makeTitle());
        initComponents();
        loadBackground();

        setLocationRelativeTo(null);
        if (MainFrame.this.isUndecorated())
            setResizable(false);

        this.addWindowListener(new WindowListener() {
            @Override
            public void windowOpened(WindowEvent e) {
            }

            @Override
            public void windowClosing(WindowEvent e) {
                closing();
            }

            @Override
            public void windowClosed(WindowEvent e) {
            }

            @Override
            public void windowIconified(WindowEvent e) {
            }

            @Override
            public void windowDeiconified(WindowEvent e) {
            }

            @Override
            public void windowActivated(WindowEvent e) {
                if (!MainFrame.this.isUndecorated()) {
                    int w = tempW + getWidth() - getContentPane().getWidth(), h = tempH + getHeight() - getContentPane().getHeight();
                    setSize(w, h);
                    setResizable(false);
                    setLocationRelativeTo(null);
                }
            }

            @Override
            public void windowDeactivated(WindowEvent e) {
            }
        });

        if (enableShadow)
            try {
                setBackground(new Color(0, 0, 0, 0));
                getRootPane().setBorder(border = new DropShadowBorder(borderColor, 4));
            } catch (Throwable ex) {
                HMCLog.err("Failed to set window transparent.", ex);
                Settings.getInstance().setEnableShadow(false);
                setSize(802, 511);
            }
        ((JPanel) getContentPane()).setOpaque(true);

        HMCLApi.EVENT_BUS.channel(ThemeChangedEvent.class).register(x -> reloadColor(x.getValue()));

        SwingUtilities.invokeLater(() -> selectTab("main"));
    }

    private void initComponents() {
        setLayout(null);

        realPanel = new JPanel();
        realPanel.setLayout(null);

        header = new JPanel();
        header.setBounds(0, 0, 800, 30);
        realPanel.add(header);
        header.setOpaque(true);
        header.setLayout(new BoxLayout(header, BoxLayout.LINE_AXIS));
        header.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 5));
        header.setBackground(borderColor);
        header.setForeground(BasicColors.COLOR_WHITE_TEXT);

        ImageIcon headerIcon = Main.getIcon("icon.png");
        this.setIconImage(headerIcon.getImage());
        headerIcon = SwingUtils.scaleImage(headerIcon, 16, 16);
        JLabel headerLabel = new JLabel(headerIcon);
        headerLabel.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));
        header.add(headerLabel);

        header.add(Box.createRigidArea(new Dimension(8, 0)));

        PluginManager.fireAddTab(this, this::initializeTab);

        header.add(Box.createHorizontalGlue());

        JPanel rightHeaderPanel = new JPanel();
        rightHeaderPanel.setOpaque(false);
        rightHeaderPanel.setLayout(new BoxLayout(rightHeaderPanel, BoxLayout.PAGE_AXIS));
        rightHeaderPanel.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));

        JPanel windowGadgetPanel = new JPanel();
        windowGadgetPanel.setOpaque(false);
        windowGadgetPanel.setLayout(new BoxLayout(windowGadgetPanel, BoxLayout.LINE_AXIS));
        windowGadgetPanel.setAlignmentX(1.0F);

        if (!Settings.getInstance().isDecorated()) {
            ImageIcon minimizeIcon = Main.getIcon("minimize.png");
            JButton minimizeButton = new JButton(minimizeIcon);
            minimizeButton.setBorder(BorderFactory.createEmptyBorder());
            minimizeButton.setContentAreaFilled(false);
            minimizeButton.setCursor(new Cursor(12));
            minimizeButton.setFocusable(false);
            minimizeButton.addActionListener((e) -> MainFrame.this.minimizeWindow());
            windowGadgetPanel.add(minimizeButton);

            ImageIcon closeIcon = Main.getIcon("close.png");
            JButton closeButton = new JButton(closeIcon);
            closeButton.setBorder(BorderFactory.createEmptyBorder());
            closeButton.setContentAreaFilled(false);
            closeButton.addActionListener((e) -> MainFrame.this.closeWindow());
            closeButton.setCursor(new Cursor(12));
            closeButton.setFocusable(false);
            windowGadgetPanel.add(closeButton);
        }
        rightHeaderPanel.add(windowGadgetPanel);

        windowTitle = new JLabel(defaultTitle);
        windowTitle.setForeground(BasicColors.COLOR_WHITE_TEXT);
        header.add(windowTitle);
        header.add(Box.createHorizontalGlue());
        header.add(rightHeaderPanel);

        this.centralPanel = new TintablePanel();
        this.centralPanel.setTintColor(BasicColors.COLOR_CENTRAL_BACK);
        realPanel.add(this.centralPanel);
        JPanel truePanel = new JPanel();
        truePanel.setLayout(new BorderLayout());

        this.infoSwap = new JPanel();
        this.infoLayout = new CardLayout();
        this.infoSwap.setLayout(infoLayout);
        this.infoSwap.setOpaque(false);

        tabWrapper = new JPanel[tabHeader.size()];
        for (int i = 0; i < tabHeader.size(); i++) {
            tabWrapper[i] = new JPanel();
            tabWrapper[i].setLayout(new GridLayout());
            infoSwap.add(tabWrapper[i], tabHeader.get(i).getActionCommand());
        }

        truePanel.add(this.infoSwap, "Center");
        centralPanel.setLayout(null);
        centralPanel.add(truePanel);
        truePanel.setBounds(0, 0, 800, 480);
        centralPanel.setBounds(0, 30, 800, 480);

        realPanel.setBounds(1, 0, 800, 511);
        add(realPanel);
        
        reloadColor(Settings.getInstance().getTheme());
    }

    private transient final ActionListener tabListener = e -> MainFrame.this.selectTab(e.getActionCommand());

    private void initializeTab(TopTabPage inst, String cmd, String title) {
        HeaderTab tab = new HeaderTab(title);
        tab.setActionCommand(cmd);
        tab.setForeground(BasicColors.COLOR_WHITE_TEXT);
        tab.setBackground(borderColorDarker);
        tab.setLayout(null);
        tab.addActionListener(tabListener);
        header.add(tab);
        tabHeader.add(tab);
        tabContent.add(inst);
    }

    private final List<HeaderTab> tabHeader = new ArrayList<>();
    private JPanel tabWrapper[];
    private final List<TopTabPage> tabContent = new ArrayList<>();

    public void selectTab(String tabName) {
        int chosen = -1;
        TopTabPage onCreate = null, onSelect = null, lastPage = null;
        for (int i = 0; i < tabHeader.size(); i++)
            if (tabName.equalsIgnoreCase(tabHeader.get(i).getActionCommand())) {
                if (!tabContent.get(i).isCreated()) {
                    onCreate = tabContent.get(i);
                    onCreate.setId(i);
                    tabWrapper[i].add(tabContent.get(i));
                } else if (tabContent.get(i).isSelected())
                    continue;
                chosen = i;
                break;
            }
        if (chosen != -1) {
            for (int i = 0; i < tabHeader.size(); i++)
                if (i != chosen && tabContent.get(i) != null && tabContent.get(i).isSelected()) {
                    lastPage = tabContent.get(i);
                    lastPage.onLeave();
                }
            for (int i = 0; i < tabHeader.size(); i++)
                if (i == chosen) {
                    for (int j = 0; j < tabHeader.size(); j++)
                        if (j != i)
                            tabHeader.get(j).setIsActive(false);
                    tabHeader.get(i).setIsActive(true);
                    onSelect = tabContent.get(i);
                }

            this.infoLayout.show(this.infoSwap, tabName);
            if (onCreate != null)
                onCreate.onCreate();
            if (onSelect != null)
                onSelect.onSelect(lastPage);
        }
    }

    protected void closing() {
        for (int i = 0; i < tabHeader.size(); i++)
            if (tabContent.get(i) != null && tabContent.get(i).isSelected())
                tabContent.get(i).onLeave();
    }

    protected void closeWindow() {
        closing();
        System.exit(0);
    }

    protected void minimizeWindow() {
        setState(1);
    }

    ImageIcon background;

    public void loadBackground() {
        background = SwingUtils.searchBackgroundImage(Main.getIcon(Settings.getInstance().getTheme().settings.get("Customized.MainFrame.background_image")), Settings.getInstance().getBgpath(), 800, 480);
        if (background != null) {
            if (backgroundLabel == null) {
                backgroundLabel = new GaussionPanel();
                backgroundLabel.setDrawBackgroundLayer(true);
                backgroundLabel.addAeroObject(backgroundLabel);
                backgroundLabel.setBounds(0, 0, 800, 480);
                centralPanel.add(backgroundLabel, -1);
            }
            backgroundLabel.setBackgroundImage(background.getImage());
        } else
            HMCLog.warn("No background image here! The background will be empty!");
    }

    public JPanel getTitleBar() {
        return header;
    }

    boolean isShowedMessage = false;

    public void closeMessage() {
        if (isShowedMessage) {
            isShowedMessage = false;
            reloadColor(Settings.getInstance().getTheme());
            windowTitle.setText(defaultTitle);
            windowTitle.setForeground(Settings.UPDATE_CHECKER.isOutOfDate() ? Color.red : Color.white);
        }
    }

    public void showMessage(String message) {
        isShowedMessage = true;
        borderColor = BasicColors.COLOR_RED;
        borderColorDarker = BasicColors.COLOR_RED_DARKER;
        header.setBackground(borderColor);
        for (HeaderTab tab : tabHeader)
            tab.setBackground(borderColorDarker);
        if (border != null)
            border.setColor(borderColor);
        repaint();
        windowTitle.setText(message);
        windowTitle.setForeground(Color.white);
    }

    public static void showMainFrame() {
        IAuthenticator l = Settings.getInstance().getAuthenticator();
        if (StrUtils.isBlank(l.getUserName()))
            SwingUtilities.invokeLater(() -> MainFrame.INSTANCE.showMessage(C.i18n("ui.message.first_load")));
        if (l.hasPassword() && !l.isLoggedIn())
            SwingUtilities.invokeLater(() -> MainFrame.INSTANCE.showMessage(C.i18n("ui.message.enter_password")));
        INSTANCE.setVisible(true);
    }

    Color borderColor;
    Color borderColorDarker;

    private void initBorderColor() {
        borderColor = UIManager.getColor("Customized.MainFrame.background");
        borderColorDarker = UIManager.getColor("Customized.MainFrame.selected_background");
    }

    public void reloadColor(Theme t) {
        if (isShowedMessage)
            return;
        for (Map.Entry<String, String> entry : t.settings.entrySet()) {
            if (entry.getValue().startsWith("#"))
                UIManager.put(entry.getKey(), GraphicsUtils.getWebColor(entry.getValue()));
        }
        
        initBorderColor();
        if (border != null)
            border.setColor(borderColor);
        header.setBackground(borderColor);
        for (HeaderTab tab : tabHeader)
            tab.setBackground(borderColorDarker);
        repaint();
    }

    private void paintImpl(Graphics g) {
        int off = enableShadow ? 16 : 0, yoff = getInsets().top + off, xoff = getInsets().left + off;
        int width = 800;
        int height = header.getHeight() + 480 - 1;
        super.paint(g);
        g.setColor(borderColor);
        g.drawLine(xoff, yoff, xoff, height + yoff + 1);
        g.drawLine(xoff + width + 1, yoff, xoff + width + 1, height + yoff + 1);
        g.drawLine(xoff, height + yoff + 1, xoff + width + 1, height + yoff + 1);
    }

    @Override
    public void paint(Graphics g) {
        if (!enableShadow)
            paintImpl(g);
        else {
            int off = enableShadow ? 16 : 0;
            int width = this.getWidth();
            int height = this.getHeight();
            int contentWidth = width - off - off;
            int contentHeight = height - off - off;
            BufferedImage contentImage = new BufferedImage(contentWidth,
                    contentHeight, Transparency.OPAQUE);
            Graphics2D contentG2d = contentImage.createGraphics();
            contentG2d.translate(-off, -off);
            paintImpl(g);
            paintImpl(contentG2d);
            contentG2d.dispose();
            g.drawImage(contentImage, off, off, this);
        }
    }

    public void invokeUpdate() {
        defaultTitle = Main.makeTitle() + C.i18n("update.found");
        windowTitle.addMouseListener(MouseListenerImpl.INSTANCE);
        if (!isShowedMessage) {
            windowTitle.setText(defaultTitle);
            windowTitle.setForeground(Color.red);
        }
    }

    @Override
    public JComponent getRepaintComponent() {
        return null;
    }

    @Override
    public Window getRepaintWindow() {
        return this;
    }

    @Override
    public Collection<Rectangle> getRepaintRects() {
        int off = MainFrame.INSTANCE.enableShadow ? 16 : 0, yoff = MainFrame.INSTANCE.getInsets().top + off, xoff = MainFrame.INSTANCE.getInsets().left + off;
        int width = 800, height = MainFrame.INSTANCE.header.getHeight() + 480 - 1;
        return Arrays.asList(new Rectangle(xoff, yoff, xoff, height + yoff + 1),
                new Rectangle(xoff + width + 1, yoff, xoff + width + 1, height + yoff + 1),
                new Rectangle(xoff, height + yoff + 1, xoff + width + 1, height + yoff + 1));
    }

    private static class MouseListenerImpl implements MouseListener {

        public static final MouseListenerImpl INSTANCE = new MouseListenerImpl();

        @Override
        public void mouseClicked(MouseEvent e) {
            Settings.UPDATE_CHECKER.checkOutdate();
        }

        @Override
        public void mousePressed(MouseEvent e) {
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
    }

    public void failed(String s) {
        if (s != null)
            MessageBox.show(s);
        closeMessage();
    }

    transient LaunchingUIDaemon daemon = new LaunchingUIDaemon();
}
