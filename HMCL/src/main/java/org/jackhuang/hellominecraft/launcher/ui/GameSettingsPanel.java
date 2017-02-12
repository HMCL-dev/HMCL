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
package org.jackhuang.hellominecraft.launcher.ui;

import org.jackhuang.hellominecraft.util.ui.Page;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DropTargetListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.util.List;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.TableModelEvent;
import javax.swing.table.DefaultTableModel;
import org.jackhuang.hellominecraft.api.HMCLAPI;
import org.jackhuang.hellominecraft.launcher.api.event.config.ProfileChangedEvent;
import org.jackhuang.hellominecraft.launcher.api.event.config.ProfileLoadingEvent;
import org.jackhuang.hellominecraft.launcher.api.event.version.RefreshedVersionsEvent;
import org.jackhuang.hellominecraft.launcher.core.GameException;
import org.jackhuang.hellominecraft.util.C;
import org.jackhuang.hellominecraft.util.log.HMCLog;
import org.jackhuang.hellominecraft.launcher.util.LauncherVisibility;
import org.jackhuang.hellominecraft.launcher.setting.Profile;
import org.jackhuang.hellominecraft.launcher.setting.Settings;
import org.jackhuang.hellominecraft.launcher.util.FileNameFilter;
import org.jackhuang.hellominecraft.launcher.core.mod.ModInfo;
import org.jackhuang.hellominecraft.launcher.core.install.InstallerType;
import org.jackhuang.hellominecraft.launcher.core.version.GameDirType;
import org.jackhuang.hellominecraft.launcher.core.version.MinecraftVersion;
import org.jackhuang.hellominecraft.launcher.setting.VersionSetting;
import org.jackhuang.hellominecraft.util.MessageBox;
import org.jackhuang.hellominecraft.util.AbstractSwingWorker;
import org.jackhuang.hellominecraft.util.MinecraftVersionRequest;
import org.jackhuang.hellominecraft.util.sys.OS;
import org.jackhuang.hellominecraft.util.StrUtils;
import org.jackhuang.hellominecraft.util.func.Consumer;
import org.jackhuang.hellominecraft.util.sys.FileUtils;
import org.jackhuang.hellominecraft.util.ui.SwingUtils;
import org.jackhuang.hellominecraft.util.sys.Java;
import org.jackhuang.hellominecraft.util.task.TaskWindow;
import org.jackhuang.hellominecraft.util.ui.JSystemFileChooser;
import org.jackhuang.hellominecraft.util.ui.LogWindow;

/**
 *
 * @author huangyuhui
 */
public final class GameSettingsPanel extends RepaintPage implements DropTargetListener {

    boolean isLoading = false;
    boolean showedNoVersion = false;
    public MinecraftVersionRequest minecraftVersion;
    String mcVersion;

    final InstallerPanel installerPanels[] = new InstallerPanel[InstallerType.values().length];

    public GameSettingsPanel() {
        HMCLAPI.EVENT_BUS.channel(RefreshedVersionsEvent.class).register(t -> {
            if (Settings.getLastProfile().service() == t.getValue())
                if (!showedNoVersion && Settings.getLastProfile().service().checkingModpack) {
                    showedNoVersion = true;
                    SwingUtilities.invokeLater(() -> {
                        if (MessageBox.show(C.i18n("mainwindow.no_version"), MessageBox.YES_NO_OPTION) == MessageBox.YES_OPTION) {
                            MainFrame.INSTANCE.selectTab("game");
                            showGameDownloads();
                        }
                    });
                }
        });

        setRepainter(this);
    }

    void initGui() {
        initComponents();

        animationEnabled = Settings.getInstance().isEnableAnimation();

        dropTarget = new DropTarget(this, DnDConstants.ACTION_COPY_OR_MOVE, this);

        for (int i = 0; i < InstallerType.values().length; i++)
            installerPanels[i] = new InstallerPanel(this, InstallerType.values()[i]);
        pnlGameDownloads = new GameDownloadPanel(this);

        initExplorationMenu();
        initManagementMenu();
        initExternalModsTable();
        initTabs();

        isLoading = true;
        for (Java j : Java.JAVA)
            cboJava.addItem(j.getLocalizedName());
        isLoading = false;

        HMCLAPI.EVENT_BUS.channel(ProfileLoadingEvent.class).register(onLoadingProfiles);
        HMCLAPI.EVENT_BUS.channel(ProfileChangedEvent.class).register(onSelectedProfilesChanged);
        HMCLAPI.EVENT_BUS.channel(RefreshedVersionsEvent.class).register(onRefreshedVersions);
    }

    void initExplorationMenu() {
        ppmExplore = new JPopupMenu();
        class ImplementedActionListener implements ActionListener {

            String a;

            ImplementedActionListener(String s) {
                a = s;
            }

            @Override
            public void actionPerformed(ActionEvent e) {
                Settings.getLastProfile().service().version().open(mcVersion, a);
            }
        }
        JMenuItem itm;
        itm = new JMenuItem(C.i18n("folder.game"));
        itm.addActionListener(new ImplementedActionListener(null));
        ppmExplore.add(itm);
        itm = new JMenuItem(C.i18n("folder.mod"));
        itm.addActionListener(new ImplementedActionListener("mods"));
        ppmExplore.add(itm);
        itm = new JMenuItem(C.i18n("folder.coremod"));
        itm.addActionListener(new ImplementedActionListener("coremods"));
        ppmExplore.add(itm);
        itm = new JMenuItem(C.i18n("folder.config"));
        itm.addActionListener(new ImplementedActionListener("config"));
        ppmExplore.add(itm);
        itm = new JMenuItem(C.i18n("folder.resourcepacks"));
        itm.addActionListener(new ImplementedActionListener("resourcepacks"));
        ppmExplore.add(itm);
        itm = new JMenuItem(C.i18n("folder.screenshots"));
        itm.addActionListener(new ImplementedActionListener("screenshots"));
        ppmExplore.add(itm);
    }

    void initManagementMenu() {
        ppmManage = new JPopupMenu();
        JMenuItem itm = new JMenuItem(C.i18n("versions.manage.rename"));
        itm.addActionListener((e) -> {
            if (mcVersion != null) {
                String newName = JOptionPane.showInputDialog(C.i18n("versions.manage.rename.message"), mcVersion);
                if (newName != null)
                    if (Settings.getLastProfile().service().version().renameVersion(mcVersion, newName))
                        refreshVersions();
            }
        });
        ppmManage.add(itm);
        itm = new JMenuItem(C.i18n("versions.manage.remove"));
        itm.addActionListener((e) -> {
            if (mcVersion != null && MessageBox.show(C.i18n("versions.manage.remove.confirm") + mcVersion, MessageBox.YES_NO_OPTION) == MessageBox.YES_OPTION)
                if (Settings.getLastProfile().service().version().removeVersionFromDisk(mcVersion))
                    refreshVersions();
        });
        ppmManage.add(itm);
        itm = new JMenuItem(C.i18n("versions.manage.redownload_json"));
        itm.addActionListener((e) -> {
            if (mcVersion != null)
                Settings.getLastProfile().service().download().downloadMinecraftVersionJson(mcVersion);
        });
        ppmManage.add(itm);
        itm = new JMenuItem(C.i18n("versions.manage.redownload_assets_index"));
        itm.addActionListener((e) -> {
            if (mcVersion != null)
                try {
                    Settings.getLastProfile().service().asset().refreshAssetsIndex(mcVersion);
                } catch (GameException ex) {
                    HMCLog.err("Failed to download assets", ex);
                    MessageBox.showLocalized("assets.failed_download");
                }
        });
        ppmManage.add(itm);
        itm = new JMenuItem(C.i18n("versions.mamage.remove_libraries"));
        itm.addActionListener((e) -> {
            if (mcVersion != null)
                FileUtils.deleteDirectoryQuietly(new File(Settings.getLastProfile().service().baseDirectory(), "libraries"));
        });
        ppmManage.add(itm);
    }

    void initExternalModsTable() {
        if (lstExternalMods.getColumnModel().getColumnCount() > 0) {
            lstExternalMods.getColumnModel().getColumn(0).setMinWidth(17);
            lstExternalMods.getColumnModel().getColumn(0).setPreferredWidth(17);
            lstExternalMods.getColumnModel().getColumn(0).setMaxWidth(17);
        }
        lstExternalMods.getSelectionModel().addListSelectionListener(e -> {
            int row = lstExternalMods.getSelectedRow();
            List<ModInfo> mods = Settings.getLastProfile().service().mod().getMods(Settings.getLastProfile().getSelectedVersion());
            if (mods != null && 0 <= row && row < mods.size()) {
                ModInfo m = mods.get(row);
                boolean hasLink = m.url != null;
                String text = "<html>" + (hasLink ? "<a href=\"" + m.url + "\">" : "") + m.getName() + (hasLink ? "</a>" : "");
                text += " by " + m.getAuthor();
                String description = "No mod description found";
                if (m.description != null) {
                    description = "";
                    for (String desc : m.description.split("\n"))
                        description += SwingUtils.getParsedJPanelText(lblModInfo, desc) + "<br/>";
                }
                text += "<br>" + description;
                lblModInfo.setText(text);
                lblModInfo.setCursor(new java.awt.Cursor(hasLink ? java.awt.Cursor.HAND_CURSOR : java.awt.Cursor.DEFAULT_CURSOR));
            }
        });
        ((DefaultTableModel) lstExternalMods.getModel()).addTableModelListener(e -> {
            if (e.getType() == TableModelEvent.UPDATE && e.getColumn() == 0) {
                int row = lstExternalMods.getSelectedRow();
                List<ModInfo> mods = Settings.getLastProfile().service().mod().getMods(Settings.getLastProfile().getSelectedVersion());
                if (mods != null && mods.size() > row && row >= 0)
                    mods.get(row).reverseModState();
            }
        });
    }

    void initTabs() {
        tabVersionEdit.addChangeListener(new ChangeListener() {
            boolean b = false;

            @Override
            public void stateChanged(ChangeEvent e) {
                if (tabVersionEdit.getSelectedComponent() == pnlAutoInstall && !b) {
                    b = true;
                    TaskWindow.factory().execute(installerPanels[0].refreshVersionsTask());
                }
            }
        });

        ((NewTabPane) tabVersionEdit).initializing = true;
        tabVersionEdit.addTab(C.i18n("settings.tabs.game_download"), pnlGameDownloads);
        ((NewTabPane) tabVersionEdit).initializing = false;

        ((NewTabPane) tabInstallers).initializing = true;
        for (int i = 0; i < InstallerType.values().length; i++)
            tabInstallers.addTab(InstallerType.values()[i].getLocalizedName(), installerPanels[i]);
        ((NewTabPane) tabInstallers).initializing = false;
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        tabVersionEdit = new NewTabPane();
        ((NewTabPane)tabVersionEdit).initializing = true;
        pnlSettings = new Page();
        lblGameDir = new javax.swing.JLabel();
        txtGameDir = new javax.swing.JTextField();
        lblDimension = new javax.swing.JLabel();
        txtWidth = new javax.swing.JTextField();
        txtHeight = new javax.swing.JTextField();
        lblDimensionX = new javax.swing.JLabel();
        chkFullscreen = new javax.swing.JCheckBox();
        txtJavaDir = new javax.swing.JTextField();
        lblJavaDir = new javax.swing.JLabel();
        lblMaxMemory = new javax.swing.JLabel();
        txtMaxMemory = new javax.swing.JTextField();
        lblMaxMemorySize = new javax.swing.JLabel();
        btnDownloadAllAssets = new javax.swing.JButton();
        cboLauncherVisibility = new javax.swing.JComboBox();
        lblLauncherVisibility = new javax.swing.JLabel();
        lblRunDirectory = new javax.swing.JLabel();
        cboRunDirectory = new javax.swing.JComboBox();
        btnChoosingJavaDir = new javax.swing.JButton();
        cboJava = new javax.swing.JComboBox();
        btnChoosingGameDir = new javax.swing.JButton();
        btnCleanGame = new javax.swing.JButton();
        lblUsesGlobal = new javax.swing.JLabel();
        pnlAdvancedSettings = new Page();
        lblJavaArgs = new javax.swing.JLabel();
        txtJavaArgs = new javax.swing.JTextField();
        txtMinecraftArgs = new javax.swing.JTextField();
        lblMinecraftArgs = new javax.swing.JLabel();
        lblPermSize = new javax.swing.JLabel();
        txtPermSize = new javax.swing.JTextField();
        chkNoJVMArgs = new javax.swing.JCheckBox();
        lblPrecalledCommand = new javax.swing.JLabel();
        txtPrecalledCommand = new javax.swing.JTextField();
        lblServerIP = new javax.swing.JLabel();
        txtServerIP = new javax.swing.JTextField();
        lblPrecalledCommand1 = new javax.swing.JLabel();
        txtWrapperLauncher = new javax.swing.JTextField();
        chkDontCheckGame = new javax.swing.JCheckBox();
        pnlModManagement = new Page();
        pnlModManagementContent = new Page();
        jScrollPane1 = new javax.swing.JScrollPane();
        lstExternalMods = new javax.swing.JTable();
        btnAddMod = new javax.swing.JButton();
        btnRemoveMod = new javax.swing.JButton();
        lblModInfo = new javax.swing.JLabel();
        pnlAutoInstall = new javax.swing.JPanel();
        tabInstallers = new NewTabPane();
        pnlTop = new javax.swing.JPanel();
        pnlSelection = new javax.swing.JPanel();
        lblProfile = new javax.swing.JLabel();
        cboProfiles = new javax.swing.JComboBox();
        cboVersions = new javax.swing.JComboBox();
        lblVersions = new javax.swing.JLabel();
        pnlManagement = new javax.swing.JPanel();
        btnModify = new javax.swing.JButton();
        btnRefreshVersions = new javax.swing.JButton();
        txtMinecraftVersion = new javax.swing.JTextField();
        btnNewProfile = new javax.swing.JButton();
        btnRemoveProfile = new javax.swing.JButton();
        btnExplore = new javax.swing.JButton();
        btnTestGame = new javax.swing.JButton();
        btnShowLog = new javax.swing.JButton();
        btnMakeLaunchScript = new javax.swing.JButton();
        btnIncludeMinecraft = new javax.swing.JButton();

        setBackground(new java.awt.Color(255, 255, 255));
        setOpaque(false);

        tabVersionEdit.setName("tabVersionEdit"); // NOI18N

        lblGameDir.setText(C.i18n("settings.game_directory")); // NOI18N

        txtGameDir.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                txtGameDirFocusLost(evt);
            }
        });

        lblDimension.setText(C.i18n("settings.dimension")); // NOI18N

        txtWidth.setToolTipText("");
        txtWidth.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                txtWidthFocusLost(evt);
            }
        });

        txtHeight.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                txtHeightFocusLost(evt);
            }
        });

        lblDimensionX.setText("x");

        chkFullscreen.setText(C.i18n("settings.fullscreen")); // NOI18N
        chkFullscreen.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                chkFullscreenItemStateChanged(evt);
            }
        });

        txtJavaDir.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                txtJavaDirFocusLost(evt);
            }
        });

        lblJavaDir.setText(C.i18n("settings.java_dir")); // NOI18N

        lblMaxMemory.setText(C.i18n("settings.max_memory")); // NOI18N

        txtMaxMemory.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                txtMaxMemoryFocusLost(evt);
            }
        });

        lblMaxMemorySize.setText(C.i18n("settings.physical_memory") + ": " + OS.getTotalPhysicalMemory() / 1024 / 1024 + "MB");

        btnDownloadAllAssets.setText(C.i18n("assets.download_all")); // NOI18N
        btnDownloadAllAssets.setToolTipText("");
        btnDownloadAllAssets.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnDownloadAllAssetsActionPerformed(evt);
            }
        });

        cboLauncherVisibility.setModel(new javax.swing.DefaultComboBoxModel(new String[] { C.i18n("advancedsettings.launcher_visibility.close"), C.i18n("advancedsettings.launcher_visibility.hide"), C.i18n("advancedsettings.launcher_visibility.keep") }));
        cboLauncherVisibility.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                cboLauncherVisibilityItemStateChanged(evt);
            }
        });

        lblLauncherVisibility.setText(C.i18n("advancedsettings.launcher_visible")); // NOI18N

        lblRunDirectory.setText(C.i18n("settings.run_directory")); // NOI18N

        cboRunDirectory.setModel(new javax.swing.DefaultComboBoxModel(new String[] { C.i18n("advancedsettings.game_dir.default"), C.i18n("advancedsettings.game_dir.independent") }));
        cboRunDirectory.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                cboRunDirectoryItemStateChanged(evt);
            }
        });

        btnChoosingJavaDir.setText(C.i18n("ui.button.explore")); // NOI18N
        btnChoosingJavaDir.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnChoosingJavaDirActionPerformed(evt);
            }
        });

        cboJava.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                cboJavaItemStateChanged(evt);
            }
        });

        btnChoosingGameDir.setText(C.i18n("ui.button.explore")); // NOI18N
        btnChoosingGameDir.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnChoosingGameDirActionPerformed(evt);
            }
        });

        btnCleanGame.setText(C.i18n("setupwindow.clean")); // NOI18N
        btnCleanGame.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnCleanGameActionPerformed(evt);
            }
        });

        lblUsesGlobal.setText("jLabel1");
        lblUsesGlobal.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        lblUsesGlobal.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                lblUsesGlobalMouseClicked(evt);
            }
        });

        javax.swing.GroupLayout pnlSettingsLayout = new javax.swing.GroupLayout(pnlSettings);
        pnlSettings.setLayout(pnlSettingsLayout);
        pnlSettingsLayout.setHorizontalGroup(
            pnlSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pnlSettingsLayout.createSequentialGroup()
                .addGroup(pnlSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(lblJavaDir)
                    .addComponent(lblMaxMemory)
                    .addComponent(lblGameDir)
                    .addComponent(lblRunDirectory)
                    .addComponent(lblLauncherVisibility)
                    .addComponent(lblDimension))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(pnlSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(cboLauncherVisibility, javax.swing.GroupLayout.Alignment.TRAILING, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(pnlSettingsLayout.createSequentialGroup()
                        .addComponent(txtWidth, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(lblDimensionX)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(txtHeight, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 88, Short.MAX_VALUE)
                        .addComponent(chkFullscreen))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, pnlSettingsLayout.createSequentialGroup()
                        .addComponent(txtMaxMemory)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(lblMaxMemorySize))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, pnlSettingsLayout.createSequentialGroup()
                        .addComponent(txtGameDir)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnChoosingGameDir))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, pnlSettingsLayout.createSequentialGroup()
                        .addComponent(cboJava, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(txtJavaDir)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnChoosingJavaDir))
                    .addComponent(cboRunDirectory, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
            .addGroup(pnlSettingsLayout.createSequentialGroup()
                .addComponent(btnDownloadAllAssets)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(btnCleanGame))
            .addGroup(pnlSettingsLayout.createSequentialGroup()
                .addComponent(lblUsesGlobal)
                .addGap(0, 0, Short.MAX_VALUE))
        );
        pnlSettingsLayout.setVerticalGroup(
            pnlSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pnlSettingsLayout.createSequentialGroup()
                .addComponent(lblUsesGlobal)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(pnlSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(txtGameDir, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(lblGameDir)
                    .addComponent(btnChoosingGameDir, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(pnlSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(txtJavaDir, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(lblJavaDir)
                    .addComponent(btnChoosingJavaDir, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(cboJava, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(pnlSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lblMaxMemorySize)
                    .addComponent(txtMaxMemory, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(lblMaxMemory))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(pnlSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(cboLauncherVisibility, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(lblLauncherVisibility))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(pnlSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(cboRunDirectory, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(lblRunDirectory))
                .addGap(4, 4, 4)
                .addGroup(pnlSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(txtHeight, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(chkFullscreen, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(lblDimensionX, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(lblDimension)
                    .addComponent(txtWidth, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 39, Short.MAX_VALUE)
                .addGroup(pnlSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(btnDownloadAllAssets)
                    .addComponent(btnCleanGame))
                .addContainerGap())
        );

        tabVersionEdit.addTab(C.i18n("settings"), pnlSettings); // NOI18N

        lblJavaArgs.setText(C.i18n("advancedsettings.jvm_args")); // NOI18N

        txtJavaArgs.setToolTipText(C.i18n("advancedsettings.java_args_default")); // NOI18N
        txtJavaArgs.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                txtJavaArgsFocusLost(evt);
            }
        });

        txtMinecraftArgs.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                txtMinecraftArgsFocusLost(evt);
            }
        });

        lblMinecraftArgs.setText(C.i18n("advancedsettings.Minecraft_arguments")); // NOI18N

        lblPermSize.setText(C.i18n("advancedsettings.java_permanent_generation_space")); // NOI18N

        txtPermSize.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                txtPermSizeFocusLost(evt);
            }
        });

        chkNoJVMArgs.setText(C.i18n("advancedsettings.no_jvm_args")); // NOI18N
        chkNoJVMArgs.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                chkNoJVMArgsItemStateChanged(evt);
            }
        });

        lblPrecalledCommand.setText(C.i18n("advancedsettings.precall_command")); // NOI18N

        txtPrecalledCommand.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                txtPrecalledCommandFocusLost(evt);
            }
        });

        lblServerIP.setText(C.i18n("advancedsettings.server_ip")); // NOI18N

        txtServerIP.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                txtServerIPFocusLost(evt);
            }
        });

        lblPrecalledCommand1.setText(C.i18n("advancedsettings.wrapper_launcher")); // NOI18N

        txtWrapperLauncher.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                txtWrapperLauncherFocusLost(evt);
            }
        });

        chkDontCheckGame.setText(C.i18n("advancedsettings.dont_check_game_completeness")); // NOI18N
        chkDontCheckGame.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                chkDontCheckGameItemStateChanged(evt);
            }
        });

        javax.swing.GroupLayout pnlAdvancedSettingsLayout = new javax.swing.GroupLayout(pnlAdvancedSettings);
        pnlAdvancedSettings.setLayout(pnlAdvancedSettingsLayout);
        pnlAdvancedSettingsLayout.setHorizontalGroup(
            pnlAdvancedSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(txtPrecalledCommand)
            .addComponent(txtServerIP)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, pnlAdvancedSettingsLayout.createSequentialGroup()
                .addGroup(pnlAdvancedSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(lblMinecraftArgs)
                    .addComponent(lblPermSize)
                    .addComponent(lblJavaArgs))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(pnlAdvancedSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(txtJavaArgs, javax.swing.GroupLayout.DEFAULT_SIZE, 325, Short.MAX_VALUE)
                    .addComponent(txtMinecraftArgs)
                    .addComponent(txtPermSize, javax.swing.GroupLayout.Alignment.TRAILING)))
            .addGroup(pnlAdvancedSettingsLayout.createSequentialGroup()
                .addGroup(pnlAdvancedSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(txtWrapperLauncher)
                    .addGroup(pnlAdvancedSettingsLayout.createSequentialGroup()
                        .addContainerGap()
                        .addComponent(chkNoJVMArgs)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(chkDontCheckGame))
                    .addGroup(pnlAdvancedSettingsLayout.createSequentialGroup()
                        .addGroup(pnlAdvancedSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(lblPrecalledCommand1)
                            .addComponent(lblPrecalledCommand)
                            .addComponent(lblServerIP))
                        .addGap(0, 0, Short.MAX_VALUE)))
                .addContainerGap())
        );
        pnlAdvancedSettingsLayout.setVerticalGroup(
            pnlAdvancedSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pnlAdvancedSettingsLayout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addGroup(pnlAdvancedSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(txtJavaArgs, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(lblJavaArgs))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(pnlAdvancedSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(txtMinecraftArgs, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(lblMinecraftArgs))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(pnlAdvancedSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(txtPermSize, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(lblPermSize))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(lblPrecalledCommand1)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(txtWrapperLauncher, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(lblPrecalledCommand)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(txtPrecalledCommand, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(lblServerIP)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(txtServerIP, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addGroup(pnlAdvancedSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(chkNoJVMArgs)
                    .addComponent(chkDontCheckGame))
                .addContainerGap())
        );

        tabVersionEdit.addTab(C.i18n("advancedsettings"), pnlAdvancedSettings); // NOI18N

        lstExternalMods.setModel(SwingUtils.makeDefaultTableModel(new String[]{"", "Mod", C.i18n("ui.label.version")}, new Class[]{Boolean.class,String.class,String.class}, new boolean[]{true,false,false}));
        lstExternalMods.setColumnSelectionAllowed(true);
        lstExternalMods.getTableHeader().setReorderingAllowed(false);
        lstExternalMods.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyPressed(java.awt.event.KeyEvent evt) {
                lstExternalModsKeyPressed(evt);
            }
        });
        jScrollPane1.setViewportView(lstExternalMods);
        lstExternalMods.getColumnModel().getSelectionModel().setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);

        btnAddMod.setText(C.i18n("mods.add")); // NOI18N
        btnAddMod.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnAddModActionPerformed(evt);
            }
        });

        btnRemoveMod.setText(C.i18n("mods.remove")); // NOI18N
        btnRemoveMod.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnRemoveModActionPerformed(evt);
            }
        });

        lblModInfo.setText(C.i18n("mods.default_information")); // NOI18N
        lblModInfo.setCursor(new java.awt.Cursor(java.awt.Cursor.DEFAULT_CURSOR));
        lblModInfo.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                lblModInfoMouseClicked(evt);
            }
        });

        javax.swing.GroupLayout pnlModManagementContentLayout = new javax.swing.GroupLayout(pnlModManagementContent);
        pnlModManagementContent.setLayout(pnlModManagementContentLayout);
        pnlModManagementContentLayout.setHorizontalGroup(
            pnlModManagementContentLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pnlModManagementContentLayout.createSequentialGroup()
                .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 550, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(pnlModManagementContentLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(btnRemoveMod, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(btnAddMod, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, pnlModManagementContentLayout.createSequentialGroup()
                .addComponent(lblModInfo, javax.swing.GroupLayout.DEFAULT_SIZE, 646, Short.MAX_VALUE)
                .addContainerGap())
        );
        pnlModManagementContentLayout.setVerticalGroup(
            pnlModManagementContentLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pnlModManagementContentLayout.createSequentialGroup()
                .addGroup(pnlModManagementContentLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(pnlModManagementContentLayout.createSequentialGroup()
                        .addComponent(btnAddMod)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnRemoveMod)
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 259, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(lblModInfo))
        );

        javax.swing.GroupLayout pnlModManagementLayout = new javax.swing.GroupLayout(pnlModManagement);
        pnlModManagement.setLayout(pnlModManagementLayout);
        pnlModManagementLayout.setHorizontalGroup(
            pnlModManagementLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(pnlModManagementContent, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        pnlModManagementLayout.setVerticalGroup(
            pnlModManagementLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(pnlModManagementContent, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );

        tabVersionEdit.addTab(C.i18n("mods"), pnlModManagement); // NOI18N

        javax.swing.GroupLayout pnlAutoInstallLayout = new javax.swing.GroupLayout(pnlAutoInstall);
        pnlAutoInstall.setLayout(pnlAutoInstallLayout);
        pnlAutoInstallLayout.setHorizontalGroup(
            pnlAutoInstallLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(tabInstallers)
        );
        pnlAutoInstallLayout.setVerticalGroup(
            pnlAutoInstallLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(tabInstallers)
        );

        tabVersionEdit.addTab(C.i18n("settings.tabs.installers"), pnlAutoInstall); // NOI18N

        lblProfile.setText(C.i18n("ui.label.profile")); // NOI18N

        cboProfiles.setMinimumSize(new java.awt.Dimension(32, 23));
        cboProfiles.setPreferredSize(new java.awt.Dimension(32, 23));
        cboProfiles.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                cboProfilesItemStateChanged(evt);
            }
        });

        cboVersions.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                cboVersionsItemStateChanged(evt);
            }
        });

        lblVersions.setText(C.i18n("ui.label.version")); // NOI18N

        javax.swing.GroupLayout pnlSelectionLayout = new javax.swing.GroupLayout(pnlSelection);
        pnlSelection.setLayout(pnlSelectionLayout);
        pnlSelectionLayout.setHorizontalGroup(
            pnlSelectionLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pnlSelectionLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(pnlSelectionLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(lblVersions)
                    .addComponent(lblProfile))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(pnlSelectionLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(cboProfiles, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(cboVersions, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
        );
        pnlSelectionLayout.setVerticalGroup(
            pnlSelectionLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pnlSelectionLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(pnlSelectionLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(cboProfiles, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(lblProfile))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(pnlSelectionLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(cboVersions, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(lblVersions))
                .addContainerGap(11, Short.MAX_VALUE))
        );

        btnModify.setText(C.i18n("settings.manage")); // NOI18N
        btnModify.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                btnModifyMouseClicked(evt);
            }
        });

        btnRefreshVersions.setText(C.i18n("ui.button.refresh")); // NOI18N
        btnRefreshVersions.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnRefreshVersionsActionPerformed(evt);
            }
        });

        txtMinecraftVersion.setEditable(false);

        btnNewProfile.setText(C.i18n("setupwindow.new")); // NOI18N
        btnNewProfile.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnNewProfileActionPerformed(evt);
            }
        });

        btnRemoveProfile.setText(C.i18n("ui.button.delete")); // NOI18N
        btnRemoveProfile.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnRemoveProfileActionPerformed(evt);
            }
        });

        btnExplore.setText(C.i18n("settings.explore")); // NOI18N
        btnExplore.setToolTipText("");
        btnExplore.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                btnExploreMouseClicked(evt);
            }
        });

        javax.swing.GroupLayout pnlManagementLayout = new javax.swing.GroupLayout(pnlManagement);
        pnlManagement.setLayout(pnlManagementLayout);
        pnlManagementLayout.setHorizontalGroup(
            pnlManagementLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pnlManagementLayout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addGroup(pnlManagementLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(btnNewProfile, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(txtMinecraftVersion))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(pnlManagementLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(btnRemoveProfile, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(btnRefreshVersions, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(pnlManagementLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(btnModify, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(btnExplore, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        pnlManagementLayout.setVerticalGroup(
            pnlManagementLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pnlManagementLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(pnlManagementLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(btnNewProfile, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(btnRemoveProfile, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(btnExplore, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(pnlManagementLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(txtMinecraftVersion, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(btnRefreshVersions, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(btnModify, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        javax.swing.GroupLayout pnlTopLayout = new javax.swing.GroupLayout(pnlTop);
        pnlTop.setLayout(pnlTopLayout);
        pnlTopLayout.setHorizontalGroup(
            pnlTopLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pnlTopLayout.createSequentialGroup()
                .addComponent(pnlSelection, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(pnlManagement, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
        );
        pnlTopLayout.setVerticalGroup(
            pnlTopLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pnlTopLayout.createSequentialGroup()
                .addGroup(pnlTopLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(pnlSelection, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(pnlManagement, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addGap(0, 0, Short.MAX_VALUE))
        );

        btnTestGame.setText(C.i18n("settings.test_game")); // NOI18N
        btnTestGame.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnTestGameActionPerformed(evt);
            }
        });

        btnShowLog.setText(C.i18n("mainwindow.show_log")); // NOI18N
        btnShowLog.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnShowLogActionPerformed(evt);
            }
        });

        btnMakeLaunchScript.setText(C.i18n("mainwindow.make_launch_script")); // NOI18N
        btnMakeLaunchScript.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnMakeLaunchScriptActionPerformed(evt);
            }
        });

        btnIncludeMinecraft.setText(C.i18n("setupwindow.include_minecraft")); // NOI18N
        btnIncludeMinecraft.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnIncludeMinecraftActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(pnlTop, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(tabVersionEdit, javax.swing.GroupLayout.Alignment.TRAILING)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                        .addGap(0, 0, Short.MAX_VALUE)
                        .addComponent(btnMakeLaunchScript)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnShowLog)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnTestGame)))
                .addContainerGap())
            .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(layout.createSequentialGroup()
                    .addContainerGap()
                    .addComponent(btnIncludeMinecraft)
                    .addContainerGap(577, Short.MAX_VALUE)))
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(pnlTop, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(tabVersionEdit, javax.swing.GroupLayout.DEFAULT_SIZE, 309, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(btnTestGame)
                    .addComponent(btnShowLog)
                    .addComponent(btnMakeLaunchScript))
                .addContainerGap())
            .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                    .addContainerGap(400, Short.MAX_VALUE)
                    .addComponent(btnIncludeMinecraft)
                    .addContainerGap()))
        );

        ((NewTabPane)tabVersionEdit).initializing = false;
    }// </editor-fold>//GEN-END:initComponents

    private void btnIncludeMinecraftActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnIncludeMinecraftActionPerformed
        JSystemFileChooser fc = new JSystemFileChooser(new File("."));
        fc.setFileSelectionMode(JSystemFileChooser.DIRECTORIES_ONLY);
        if (fc.showOpenDialog(this) == JSystemFileChooser.APPROVE_OPTION) {
            File newGameDir = fc.getSelectedFile();
            String name = JOptionPane.showInputDialog(C.i18n("setupwindow.give_a_name"));
            if (StrUtils.isBlank(name)) {
                MessageBox.show(C.i18n("setupwindow.no_empty_name"));
                return;
            }
            Settings.putProfile(new Profile(name).setGameDir(newGameDir.getAbsolutePath()));
            MessageBox.show(C.i18n("setupwindow.find_in_configurations"));
            loadProfiles();
        }
    }//GEN-LAST:event_btnIncludeMinecraftActionPerformed

    private void btnMakeLaunchScriptActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnMakeLaunchScriptActionPerformed
        MainFrame.INSTANCE.daemon.makeLaunchScript(Settings.getLastProfile());
    }//GEN-LAST:event_btnMakeLaunchScriptActionPerformed

    private void btnShowLogActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnShowLogActionPerformed
        LogWindow.INSTANCE.setVisible(true);
    }//GEN-LAST:event_btnShowLogActionPerformed

    private void btnTestGameActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnTestGameActionPerformed
        LogWindow.INSTANCE.setVisible(true);
        MainFrame.INSTANCE.daemon.runGame(Settings.getLastProfile());
    }//GEN-LAST:event_btnTestGameActionPerformed

    private void btnExploreMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_btnExploreMouseClicked
        ppmExplore.show(evt.getComponent(), evt.getPoint().x, evt.getPoint().y);
    }//GEN-LAST:event_btnExploreMouseClicked

    private void btnRemoveProfileActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnRemoveProfileActionPerformed
        if (MessageBox.show(C.i18n("ui.message.sure_remove", Settings.getLastProfile().getName()), MessageBox.YES_NO_OPTION) == MessageBox.NO_OPTION)
            return;
        Settings.delProfile(Settings.getLastProfile());
    }//GEN-LAST:event_btnRemoveProfileActionPerformed

    private void btnNewProfileActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnNewProfileActionPerformed
        new NewProfileWindow(null).setVisible(true);
        loadProfiles();
    }//GEN-LAST:event_btnNewProfileActionPerformed

    private void btnRefreshVersionsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnRefreshVersionsActionPerformed
        refreshVersions();
    }//GEN-LAST:event_btnRefreshVersionsActionPerformed

    private void btnModifyMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_btnModifyMouseClicked
        ppmManage.show(evt.getComponent(), evt.getPoint().x, evt.getPoint().y);
    }//GEN-LAST:event_btnModifyMouseClicked

    private void cboVersionsItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_cboVersionsItemStateChanged
        if (isLoading || evt.getStateChange() != ItemEvent.SELECTED || cboVersions.getSelectedIndex() < 0 || StrUtils.isBlank((String) cboVersions.getSelectedItem()))
            return;
        Settings.getLastProfile().setSelectedMinecraftVersion((String) cboVersions.getSelectedItem());
    }//GEN-LAST:event_cboVersionsItemStateChanged

    // <editor-fold defaultstate="collapsed" desc="UI Events">
    private void cboProfilesItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_cboProfilesItemStateChanged
        if (!isLoading)
            Settings.getInstance().setLast((String) cboProfiles.getSelectedItem());
    }//GEN-LAST:event_cboProfilesItemStateChanged

    private void lblModInfoMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_lblModInfoMouseClicked
        int idx = lstExternalMods.getSelectedRow();
        if (idx > 0 && idx < Settings.getLastProfile().service().mod().getMods(Settings.getLastProfile().getSelectedVersion()).size())
            SwingUtils.openLink(Settings.getLastProfile().service().mod().getMods(Settings.getLastProfile().getSelectedVersion()).get(idx).url);
    }//GEN-LAST:event_lblModInfoMouseClicked

    private void btnRemoveModActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnRemoveModActionPerformed
        Settings.getLastProfile().service().mod().removeMod(Settings.getLastProfile().getSelectedVersion(), SwingUtils.getValueBySelectedRow(lstExternalMods, lstExternalMods.getSelectedRows(), 1));
        reloadMods();
    }//GEN-LAST:event_btnRemoveModActionPerformed

    private void btnAddModActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnAddModActionPerformed
        JSystemFileChooser fc = new JSystemFileChooser();
        fc.setFileSelectionMode(JSystemFileChooser.FILES_ONLY);
        fc.setDialogTitle(C.i18n("mods.choose_mod"));
        fc.setMultiSelectionEnabled(true);
        if (fc.showOpenDialog(this) != JSystemFileChooser.APPROVE_OPTION)
            return;
        boolean flag = true;
        for (File f : fc.getSelectedFiles())
            flag &= Settings.getLastProfile().service().mod().addMod(Settings.getLastProfile().getSelectedVersion(), f);
        reloadMods();
        if (!flag)
            MessageBox.show(C.i18n("mods.failed"));
    }//GEN-LAST:event_btnAddModActionPerformed

    private void lstExternalModsKeyPressed(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_lstExternalModsKeyPressed
        if (evt.getKeyCode() == KeyEvent.VK_DELETE)
            btnRemoveModActionPerformed(null);
    }//GEN-LAST:event_lstExternalModsKeyPressed

    private void chkDontCheckGameItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_chkDontCheckGameItemStateChanged
        if (!isLoading)
            Settings.getLastProfile().getSelectedVersionSetting().setNotCheckGame(chkDontCheckGame.isSelected());
    }//GEN-LAST:event_chkDontCheckGameItemStateChanged

    private void txtWrapperLauncherFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_txtWrapperLauncherFocusLost
        Settings.getLastProfile().getSelectedVersionSetting().setWrapper(txtWrapperLauncher.getText());
    }//GEN-LAST:event_txtWrapperLauncherFocusLost

    private void txtServerIPFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_txtServerIPFocusLost
        Settings.getLastProfile().getSelectedVersionSetting().setServerIp(txtServerIP.getText());
    }//GEN-LAST:event_txtServerIPFocusLost

    private void txtPrecalledCommandFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_txtPrecalledCommandFocusLost
        Settings.getLastProfile().getSelectedVersionSetting().setPrecalledCommand(txtPrecalledCommand.getText());
    }//GEN-LAST:event_txtPrecalledCommandFocusLost

    private void chkNoJVMArgsItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_chkNoJVMArgsItemStateChanged
        if (!isLoading)
            Settings.getLastProfile().getSelectedVersionSetting().setNoJVMArgs(chkNoJVMArgs.isSelected());
    }//GEN-LAST:event_chkNoJVMArgsItemStateChanged

    private void txtPermSizeFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_txtPermSizeFocusLost
        Settings.getLastProfile().getSelectedVersionSetting().setPermSize(txtPermSize.getText());
    }//GEN-LAST:event_txtPermSizeFocusLost

    private void txtMinecraftArgsFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_txtMinecraftArgsFocusLost
        Settings.getLastProfile().getSelectedVersionSetting().setMinecraftArgs(txtMinecraftArgs.getText());
    }//GEN-LAST:event_txtMinecraftArgsFocusLost

    private void txtJavaArgsFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_txtJavaArgsFocusLost
        Settings.getLastProfile().getSelectedVersionSetting().setJavaArgs(txtJavaArgs.getText());
    }//GEN-LAST:event_txtJavaArgsFocusLost

    private void btnCleanGameActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnCleanGameActionPerformed
        Settings.getLastProfile().service().version().cleanFolder();
    }//GEN-LAST:event_btnCleanGameActionPerformed

    private void btnChoosingGameDirActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnChoosingGameDirActionPerformed
        JSystemFileChooser fc = new JSystemFileChooser();
        fc.setFileSelectionMode(JSystemFileChooser.DIRECTORIES_ONLY);
        fc.setDialogTitle(C.i18n("settings.choose_gamedir"));
        fc.setMultiSelectionEnabled(false);
        fc.showOpenDialog(this);
        if (fc.getSelectedFile() == null)
            return;
        try {
            String path = fc.getSelectedFile().getCanonicalPath();
            txtGameDir.setText(path);
            Settings.getLastProfile().setGameDir(path);
        } catch (IOException e) {
            HMCLog.warn("Failed to set game dir.", e);
            MessageBox.show(C.i18n("ui.label.failed_set") + e.getMessage());
        }
    }//GEN-LAST:event_btnChoosingGameDirActionPerformed

    private void cboJavaItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_cboJavaItemStateChanged
        if (evt.getStateChange() != ItemEvent.SELECTED || cboJava.getSelectedIndex() < 0 || StrUtils.isBlank((String) cboJava.getSelectedItem()))
            return;
        int idx = cboJava.getSelectedIndex();
        if (idx != -1) {
            Java j = Java.JAVA.get(idx);
            txtJavaDir.setText(j.getHome() == null ? Settings.getLastProfile().getSelectedVersionSetting().getSettingsJavaDir() : j.getJava());
            txtJavaDir.setEnabled(idx == 1);
            if (!isLoading)
                Settings.getLastProfile().getSelectedVersionSetting().setJava(j);
        }
    }//GEN-LAST:event_cboJavaItemStateChanged

    private void btnChoosingJavaDirActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnChoosingJavaDirActionPerformed
        if (cboJava.getSelectedIndex() != 1)
            return;
        JSystemFileChooser fc = new JSystemFileChooser();
        fc.setFileSelectionMode(JSystemFileChooser.FILES_ONLY);
        fc.setDialogTitle(C.i18n("settings.choose_javapath"));
        fc.setMultiSelectionEnabled(false);
        fc.setFileFilter(new FileNameFilter("javaw.exe"));
        fc.addChoosableFileFilter(new FileNameFilter("java.exe"));
        fc.addChoosableFileFilter(new FileNameFilter("java"));
        fc.showOpenDialog(this);
        if (fc.getSelectedFile() == null)
            return;
        try {
            String path = fc.getSelectedFile().getCanonicalPath();
            txtJavaDir.setText(path);
            Settings.getLastProfile().getSelectedVersionSetting().setJavaDir(txtJavaDir.getText());
        } catch (IOException e) {
            HMCLog.warn("Failed to set java path.", e);
            MessageBox.show(C.i18n("ui.label.failed_set") + e.getMessage());
        }
    }//GEN-LAST:event_btnChoosingJavaDirActionPerformed

    private void cboRunDirectoryItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_cboRunDirectoryItemStateChanged
        if (!isLoading && cboRunDirectory.getSelectedIndex() >= 0)
            Settings.getLastProfile().getSelectedVersionSetting().setGameDirType(GameDirType.values()[cboRunDirectory.getSelectedIndex()]);
    }//GEN-LAST:event_cboRunDirectoryItemStateChanged

    private void cboLauncherVisibilityItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_cboLauncherVisibilityItemStateChanged
        if (!isLoading && cboLauncherVisibility.getSelectedIndex() >= 0)
            Settings.getLastProfile().getSelectedVersionSetting().setLauncherVisibility(LauncherVisibility.values()[cboLauncherVisibility.getSelectedIndex()]);
    }//GEN-LAST:event_cboLauncherVisibilityItemStateChanged

    private void btnDownloadAllAssetsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnDownloadAllAssetsActionPerformed
        if (mcVersion != null)
            try {
                TaskWindow.factory().execute(Settings.getLastProfile().service().asset().downloadAssets(mcVersion));
            } catch (GameException ex) {
                HMCLog.err("Failed to download assets", ex);
                MessageBox.showLocalized("assets.failed_download");
            }
    }//GEN-LAST:event_btnDownloadAllAssetsActionPerformed

    private void txtMaxMemoryFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_txtMaxMemoryFocusLost
        Settings.getLastProfile().getSelectedVersionSetting().setMaxMemory(txtMaxMemory.getText());
    }//GEN-LAST:event_txtMaxMemoryFocusLost

    private void txtJavaDirFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_txtJavaDirFocusLost
        Settings.getLastProfile().getSelectedVersionSetting().setJavaDir(txtJavaDir.getText());
    }//GEN-LAST:event_txtJavaDirFocusLost

    private void chkFullscreenItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_chkFullscreenItemStateChanged
        if (!isLoading)
            Settings.getLastProfile().getSelectedVersionSetting().setFullscreen(chkFullscreen.isSelected());
    }//GEN-LAST:event_chkFullscreenItemStateChanged

    private void txtHeightFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_txtHeightFocusLost
        Settings.getLastProfile().getSelectedVersionSetting().setHeight(txtHeight.getText());
    }//GEN-LAST:event_txtHeightFocusLost

    private void txtWidthFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_txtWidthFocusLost
        Settings.getLastProfile().getSelectedVersionSetting().setWidth(txtWidth.getText());
    }//GEN-LAST:event_txtWidthFocusLost

    private void txtGameDirFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_txtGameDirFocusLost
        Settings.getLastProfile().setGameDir(txtGameDir.getText());
        loadVersions();
    }//GEN-LAST:event_txtGameDirFocusLost

    private void lblUsesGlobalMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_lblUsesGlobalMouseClicked
        if (mcVersion == null)
            return;
        Profile profile = Settings.getLastProfile();
        if (profile.isVersionSettingGlobe(mcVersion))
            profile.makeVersionSettingSpecial(mcVersion);
        else
            profile.makeVersionSettingGlobal(mcVersion);
    }//GEN-LAST:event_lblUsesGlobalMouseClicked

    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Load">
    void prepareVersionSetting(VersionSetting profile) {
        if (profile == null)
            return;
        isLoading = true;
        txtWidth.setText(profile.getWidth());
        txtHeight.setText(profile.getHeight());
        txtMaxMemory.setText(profile.getMaxMemory());
        txtPermSize.setText(profile.getPermSize());
        txtJavaArgs.setText(profile.getJavaArgs());
        txtMinecraftArgs.setText(profile.getMinecraftArgs());
        txtPrecalledCommand.setText(profile.getPrecalledCommand());
        txtServerIP.setText(profile.getServerIp());
        chkNoJVMArgs.setSelected(profile.isNoJVMArgs());
        chkDontCheckGame.setSelected(profile.isNotCheckGame());
        chkFullscreen.setSelected(profile.isFullscreen());
        cboLauncherVisibility.setSelectedIndex(profile.getLauncherVisibility().ordinal());
        cboRunDirectory.setSelectedIndex(profile.getGameDirType().ordinal());
        cboJava.setSelectedIndex(profile.getJavaIndexInAllJavas());
        isLoading = false;
    }

    /**
     * Anaylze the jar of selected minecraft version of current getProfile() to
     * get the version.
     */
    void loadMinecraftVersion(String id) {
        txtMinecraftVersion.setText("");
        if (id == null)
            return;
        minecraftVersion = MinecraftVersionRequest.minecraftVersion(Settings.getLastProfile().service().version().getMinecraftJar(id));
        txtMinecraftVersion.setText(MinecraftVersionRequest.getResponse(minecraftVersion));
    }

    //</editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Mod">
    String getMinecraftVersionFormatted() {
        return minecraftVersion == null ? "" : (StrUtils.formatVersion(minecraftVersion.version) == null) ? mcVersion : minecraftVersion.version;
    }

    @Override
    public void dragEnter(DropTargetDragEvent dtde) {
    }

    @Override
    public void dragOver(DropTargetDragEvent dtde) {
    }

    @Override
    public void dropActionChanged(DropTargetDragEvent dtde) {
    }

    @Override
    public void dragExit(DropTargetEvent dte) {
    }

    @Override
    public void drop(DropTargetDropEvent dtde) {
        DataFlavor[] f = dtde.getCurrentDataFlavors();
        if (f[0].match(DataFlavor.javaFileListFlavor))
            try {
                dtde.acceptDrop(DnDConstants.ACTION_COPY_OR_MOVE);
                Transferable tr = dtde.getTransferable();
                List<File> files = (List<File>) tr.getTransferData(DataFlavor.javaFileListFlavor);
                for (File file : files)
                    Settings.getLastProfile().service().mod().addMod(Settings.getLastProfile().getSelectedVersion(), file);
                reloadMods();
            } catch (UnsupportedFlavorException | IOException ex) {
                HMCLog.warn("Failed to drop file.", ex);
            }
    }

    void refreshVersions() {
        Settings.getLastProfile().service().version().refreshVersions();
    }

    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Mods">
    private boolean reloadingMods = false;
    private final Object modLock = new Object();

    private void reloadMods() {
        synchronized (modLock) {
            if (reloadingMods)
                return;
            reloadingMods = true;
            DefaultTableModel model = SwingUtils.clearDefaultTable(lstExternalMods);
            new OverridableSwingWorkerImpl().reg(t -> {
                synchronized (modLock) {
                    for (ModInfo x : t)
                        model.addRow(new Object[] { x.isActive(), x, x.version });
                    reloadingMods = false;
                }
            }).execute();
        }
    }

    private static class OverridableSwingWorkerImpl extends AbstractSwingWorker<List<ModInfo>> {

        @Override
        protected void work() throws Exception {
            publish(Settings.getLastProfile().service().mod().recacheMods(Settings.getLastProfile().getSelectedVersion()));
        }
    }

    // </editor-fold>
    void save() {
        VersionSetting vs = Settings.getLastProfile().getSelectedVersionSetting();
        if (txtServerIP.hasFocus())
            vs.setServerIp(txtServerIP.getText());
        if (txtPrecalledCommand.hasFocus())
            vs.setPrecalledCommand(txtPrecalledCommand.getText());
        if (txtPermSize.hasFocus())
            vs.setPermSize(txtPermSize.getText());
        if (txtMinecraftArgs.hasFocus())
            vs.setMinecraftArgs(txtMinecraftArgs.getText());
        if (txtJavaArgs.hasFocus())
            vs.setJavaArgs(txtJavaArgs.getText());
        if (txtJavaDir.hasFocus())
            vs.setJavaDir(txtJavaDir.getText());
        if (txtHeight.hasFocus())
            vs.setHeight(txtHeight.getText());
        if (txtWidth.hasFocus())
            vs.setWidth(txtWidth.getText());
        if (txtMaxMemory.hasFocus())
            vs.setMaxMemory(txtMaxMemory.getText());
    }

    @Override
    public void onCreate() {
        initGui();

        super.onCreate();
        Settings.onProfileLoading();
    }

    @Override
    public void onLeave() {
        super.onLeave();
        save();
    }

    public void showGameDownloads() {
        tabVersionEdit.setSelectedComponent(pnlGameDownloads);
    }

    // <editor-fold defaultstate="collapsed" desc="UI Definations">
    JPopupMenu ppmManage, ppmExplore;

    DropTarget dropTarget;
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton btnAddMod;
    private javax.swing.JButton btnChoosingGameDir;
    private javax.swing.JButton btnChoosingJavaDir;
    private javax.swing.JButton btnCleanGame;
    private javax.swing.JButton btnDownloadAllAssets;
    private javax.swing.JButton btnExplore;
    private javax.swing.JButton btnIncludeMinecraft;
    private javax.swing.JButton btnMakeLaunchScript;
    private javax.swing.JButton btnModify;
    private javax.swing.JButton btnNewProfile;
    private javax.swing.JButton btnRefreshVersions;
    private javax.swing.JButton btnRemoveMod;
    private javax.swing.JButton btnRemoveProfile;
    private javax.swing.JButton btnShowLog;
    private javax.swing.JButton btnTestGame;
    private javax.swing.JComboBox cboJava;
    private javax.swing.JComboBox cboLauncherVisibility;
    private javax.swing.JComboBox cboProfiles;
    private javax.swing.JComboBox cboRunDirectory;
    private javax.swing.JComboBox cboVersions;
    private javax.swing.JCheckBox chkDontCheckGame;
    private javax.swing.JCheckBox chkFullscreen;
    private javax.swing.JCheckBox chkNoJVMArgs;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JLabel lblDimension;
    private javax.swing.JLabel lblDimensionX;
    private javax.swing.JLabel lblGameDir;
    private javax.swing.JLabel lblJavaArgs;
    private javax.swing.JLabel lblJavaDir;
    private javax.swing.JLabel lblLauncherVisibility;
    private javax.swing.JLabel lblMaxMemory;
    private javax.swing.JLabel lblMaxMemorySize;
    private javax.swing.JLabel lblMinecraftArgs;
    private javax.swing.JLabel lblModInfo;
    private javax.swing.JLabel lblPermSize;
    private javax.swing.JLabel lblPrecalledCommand;
    private javax.swing.JLabel lblPrecalledCommand1;
    private javax.swing.JLabel lblProfile;
    private javax.swing.JLabel lblRunDirectory;
    private javax.swing.JLabel lblServerIP;
    private javax.swing.JLabel lblUsesGlobal;
    private javax.swing.JLabel lblVersions;
    private javax.swing.JTable lstExternalMods;
    private javax.swing.JPanel pnlAdvancedSettings;
    private javax.swing.JPanel pnlAutoInstall;
    private javax.swing.JPanel pnlManagement;
    private javax.swing.JPanel pnlModManagement;
    private javax.swing.JPanel pnlModManagementContent;
    private javax.swing.JPanel pnlSelection;
    private javax.swing.JPanel pnlSettings;
    private javax.swing.JPanel pnlTop;
    private javax.swing.JTabbedPane tabInstallers;
    private javax.swing.JTabbedPane tabVersionEdit;
    private javax.swing.JTextField txtGameDir;
    private javax.swing.JTextField txtHeight;
    private javax.swing.JTextField txtJavaArgs;
    private javax.swing.JTextField txtJavaDir;
    private javax.swing.JTextField txtMaxMemory;
    private javax.swing.JTextField txtMinecraftArgs;
    private javax.swing.JTextField txtMinecraftVersion;
    private javax.swing.JTextField txtPermSize;
    private javax.swing.JTextField txtPrecalledCommand;
    private javax.swing.JTextField txtServerIP;
    private javax.swing.JTextField txtWidth;
    private javax.swing.JTextField txtWrapperLauncher;
    // End of variables declaration//GEN-END:variables

    private javax.swing.JPanel pnlGameDownloads;
// </editor-fold>

    //<editor-fold defaultstate="collapesd" desc="Profiles & Versions Loading">
    final Runnable onLoadingProfiles = this::loadProfiles;

    private void loadProfiles() {
        isLoading = true;
        DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
        for (Profile s : Settings.getProfilesFiltered())
            model.addElement(s.getName());
        cboProfiles.setModel(model);
        isLoading = false;
    }

    final Consumer<RefreshedVersionsEvent> onRefreshedVersions = t -> {
        if (Settings.getLastProfile().service() == t.getValue())
            loadVersions();
    };

    void loadVersions() {
        DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
        for (MinecraftVersion each : Settings.getLastProfile().service().version().getVersions()) {
            if (each.hidden)
                continue;
            model.addElement(each.id);
        }
        cboVersions.setModel(model);
        if (Settings.getLastProfile().getSelectedVersion() != null)
            versionChanged(Settings.getLastProfile().getSelectedVersion());
    }

    public void versionChanged(String version) {
        isLoading = true;
        DefaultComboBoxModel<String> model = (DefaultComboBoxModel<String>) cboVersions.getModel();
        for (int i = 0; i < model.getSize(); ++i)
            if (model.getElementAt(i).equals(version)) {
                model.setSelectedItem(version);
                break;
            }
        cboVersions.setToolTipText(version);

        this.mcVersion = version;
        reloadMods();
        prepareVersionSetting(Settings.getLastProfile().getVersionSetting(version));
        loadMinecraftVersion(version);

        lblUsesGlobal.setText(C.i18n(Settings.getLastProfile().isVersionSettingGlobe(version) ? "settings.type.global" : "settings.type.special"));
        for (InstallerPanel p : installerPanels)
            p.loadVersions();
        isLoading = false;
    }

    final Consumer<ProfileChangedEvent> onSelectedProfilesChanged = event -> {
        Profile t = Settings.getProfile(event.getValue());
        t.propertyChanged.register(e -> {
            if ("selectedMinecraftVersion".equals(e.getPropertyName()))
                versionChanged(e.getNewValue());
        });

        txtGameDir.setText(t.getGameDir());

        isLoading = true;
        DefaultComboBoxModel<String> model = (DefaultComboBoxModel<String>) cboProfiles.getModel();
        for (int i = 0; i < model.getSize(); ++i)
            if (model.getElementAt(i).equals(t.getName())) {
                model.setSelectedItem(t.getName());
                break;
            }
        isLoading = false;
    };
    //</editor-fold>
}
