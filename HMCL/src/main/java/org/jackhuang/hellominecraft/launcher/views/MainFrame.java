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
package org.jackhuang.hellominecraft.launcher.views;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Transparency;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.image.BufferedImage;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.jackhuang.hellominecraft.C;
import org.jackhuang.hellominecraft.HMCLog;
import org.jackhuang.hellominecraft.launcher.Main;
import org.jackhuang.hellominecraft.launcher.settings.Settings;
import org.jackhuang.hellominecraft.utils.Utils;
import org.jackhuang.hellominecraft.views.DropShadowBorder;
import org.jackhuang.hellominecraft.views.TintablePanel;
import org.jackhuang.hellominecraft.views.BasicColors;

/**
 *
 * @author huangyuhui
 */
public final class MainFrame extends DraggableFrame {

    public static final MainFrame INSTANCE = new MainFrame();

    HeaderTab mainTab, gameTab, launcherTab;
    TintablePanel centralPanel;
    JPanel header;
    MainPagePanel mainPanel;
    GameSettingsPanel gamePanel;
    LauncherSettingsPanel launcherPanel;
    CardLayout infoLayout;
    JPanel infoSwap;
    JPanel mainPanelWrapper, launcherPanelWrapper, gamePanelWrapper;
    JLabel backgroundLabel, windowTitle;
    JPanel realPanel;
    DropShadowBorder border;
    boolean enableShadow;
    String defaultTitle;

    MainFrame() {
        defaultTitle = Main.makeTitle();
        enableShadow = Settings.getInstance().isEnableShadow();
        if (enableShadow)
            setSize(834, 542);
        else
            setSize(802, 511);
        setDefaultCloseOperation(3);
        setTitle(Main.makeTitle());
        initComponents();
        selectTab("main");
        loadBackground();

        setLocationRelativeTo(null);

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
    }

    private void initComponents() {
        borderColor = BasicColors.bgcolors[Settings.getInstance().getTheme()];
        borderColorDarker = BasicColors.bgcolors_darker[Settings.getInstance().getTheme()];

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
        headerIcon = Utils.scaleImage(headerIcon, 16, 16);
        JLabel headerLabel = new JLabel(headerIcon);
        headerLabel.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));
        header.add(headerLabel);

        header.add(Box.createRigidArea(new Dimension(8, 0)));

        ActionListener tabListener = (e) -> MainFrame.this.selectTab(e.getActionCommand());

        this.mainTab = new HeaderTab(C.i18n("launcher.title.main"));
        this.mainTab.setForeground(BasicColors.COLOR_WHITE_TEXT);
        this.mainTab.setBackground(borderColorDarker);
        this.mainTab.setActionCommand("main");
        this.mainTab.addActionListener(tabListener);
        header.add(this.mainTab);

        this.gameTab = new HeaderTab(C.i18n("launcher.title.game"));
        this.gameTab.setForeground(BasicColors.COLOR_WHITE_TEXT);
        this.gameTab.setBackground(borderColorDarker);
        this.gameTab.setIsActive(true);
        this.gameTab.setHorizontalTextPosition(10);
        this.gameTab.addActionListener(tabListener);
        this.gameTab.setActionCommand("game");
        header.add(this.gameTab);

        this.launcherTab = new HeaderTab(C.i18n("launcher.title.launcher"));
        this.launcherTab.setForeground(BasicColors.COLOR_WHITE_TEXT);
        this.launcherTab.setBackground(borderColorDarker);
        this.launcherTab.setLayout(null);
        this.launcherTab.addActionListener(tabListener);
        this.launcherTab.setActionCommand("launcher");
        header.add(this.launcherTab);

        header.add(Box.createHorizontalGlue());

        JPanel rightHeaderPanel = new JPanel();
        rightHeaderPanel.setOpaque(false);
        rightHeaderPanel.setLayout(new BoxLayout(rightHeaderPanel, BoxLayout.PAGE_AXIS));
        rightHeaderPanel.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));

        JPanel windowGadgetPanel = new JPanel();
        windowGadgetPanel.setOpaque(false);
        windowGadgetPanel.setLayout(new BoxLayout(windowGadgetPanel, BoxLayout.LINE_AXIS));
        windowGadgetPanel.setAlignmentX(1.0F);

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

        rightHeaderPanel.add(windowGadgetPanel);

        windowTitle = new JLabel(defaultTitle);
        windowTitle.setForeground(BasicColors.COLOR_WHITE_TEXT);
        windowTitle.addMouseListener(new MouseListener() {

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
        });
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

        mainPanelWrapper = new JPanel();
        mainPanelWrapper.setLayout(new GridLayout());
        this.infoSwap.add(mainPanelWrapper, "main");
        gamePanelWrapper = new JPanel();
        gamePanelWrapper.setLayout(new GridLayout());
        this.infoSwap.add(gamePanelWrapper, "game");
        launcherPanelWrapper = new JPanel();
        launcherPanelWrapper.setLayout(new GridLayout());
        this.infoSwap.add(launcherPanelWrapper, "launcher");

        truePanel.add(this.infoSwap, "Center");
        centralPanel.setLayout(null);
        centralPanel.add(truePanel);
        truePanel.setBounds(0, 0, 800, 480);
        centralPanel.setBounds(0, 30, 800, 480);

        setLayout(null);
        realPanel.setBounds(1, 0, 800, 511);
        add(realPanel);
    }

    public void selectTab(String tabName) {
        boolean a = mainTab.isActive(), b = gameTab.isActive(), c = launcherTab.isActive();
        this.mainTab.setIsActive(false);
        this.gameTab.setIsActive(false);
        this.launcherTab.setIsActive(false);

        if (tabName.equalsIgnoreCase("main")) {
            if (mainPanel == null) {
                mainPanel = new MainPagePanel();
                mainPanelWrapper.add(mainPanel);
            }
            this.mainTab.setIsActive(true);
            this.mainPanel.onSelected();
            if (!a)
                mainPanel.animate();
        } else if (tabName.equalsIgnoreCase("game")) {
            if (gamePanel == null) {
                gamePanel = new GameSettingsPanel();
                gamePanelWrapper.add(gamePanel);
            }
            this.gameTab.setIsActive(true);
            this.gamePanel.onSelected();
            if (!b)
                gamePanel.animate();
        } else if (tabName.equalsIgnoreCase("launcher")) {
            if (launcherPanel == null) {
                launcherPanel = new LauncherSettingsPanel();
                launcherPanelWrapper.add(launcherPanel);
            }
            this.launcherTab.setIsActive(true);
            if (!c)
                launcherPanel.animate();
        }

        this.infoLayout.show(this.infoSwap, tabName);
    }

    protected void closeWindow() {
        System.exit(0);
    }

    protected void minimizeWindow() {
        setState(1);
    }

    ImageIcon background;

    public void loadBackground() {
        background = Utils.searchBackgroundImage(Main.getIcon("background.jpg"), Settings.getInstance().getBgpath(), 800, 480);
        if (background != null) {
            if (backgroundLabel == null) {
                backgroundLabel = new JLabel(background);
                backgroundLabel.setBounds(0, 0, 800, 480);
            } else
                backgroundLabel.setIcon(background);
            centralPanel.add(backgroundLabel, -1);
        } else
            HMCLog.warn("No Background Image, the background will be empty!");
    }

    public JPanel getTitleBar() {
        return header;
    }

    boolean isShowedMessage = false;

    public void closeMessage() {
        if (isShowedMessage) {
            isShowedMessage = false;
            reloadColor();
            windowTitle.setText(defaultTitle);
            windowTitle.setForeground(Settings.UPDATE_CHECKER.OUT_DATED ? Color.red : Color.white);
        }
    }

    public void showMessage(String message) {
        isShowedMessage = true;
        borderColor = BasicColors.COLOR_RED;
        borderColorDarker = BasicColors.COLOR_RED_DARKER;
        header.setBackground(borderColor);
        mainTab.setBackground(borderColorDarker);
        gameTab.setBackground(borderColorDarker);
        launcherTab.setBackground(borderColorDarker);
        if (border != null)
            border.setColor(borderColor);
        repaint();
        windowTitle.setText(message);
        windowTitle.setForeground(Color.white);
    }

    public static void showMainFrame(boolean firstLoad) {
        INSTANCE.mainPanel.onShow(firstLoad);
        INSTANCE.show();
    }

    Color borderColor = BasicColors.COLOR_BLUE;
    Color borderColorDarker = BasicColors.COLOR_BLUE_DARKER;

    public void reloadColor() {
        borderColor = BasicColors.bgcolors[Settings.getInstance().getTheme()];
        borderColorDarker = BasicColors.bgcolors_darker[Settings.getInstance().getTheme()];
        if (border != null)
            border.setColor(borderColor);
        header.setBackground(borderColor);
        mainTab.setBackground(borderColorDarker);
        gameTab.setBackground(borderColorDarker);
        launcherTab.setBackground(borderColorDarker);
        repaint();
    }

    private void paintImpl(Graphics g) {
        int off = enableShadow ? 16 : 0;
        int width = 800;
        int height = header.getHeight() + 480 - 1;
        super.paint(g);
        g.setColor(borderColor);
        g.drawLine(off, off, off, height + off + 1);
        g.drawLine(off + width + 1, off, off + width + 1, height + off + 1);
        g.drawLine(off, height + off + 1, off + width + 1, height + off + 1);
        g.dispose();
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
        if (!isShowedMessage) {
            windowTitle.setText(defaultTitle);
            windowTitle.setForeground(Color.red);
        }
    }

}
