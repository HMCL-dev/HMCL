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

import java.awt.Color;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
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
import javax.swing.JFileChooser;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.TableModelEvent;
import javax.swing.table.DefaultTableModel;
import org.jackhuang.hellominecraft.util.C;
import org.jackhuang.hellominecraft.util.logging.HMCLog;
import org.jackhuang.hellominecraft.launcher.core.LauncherVisibility;
import org.jackhuang.hellominecraft.launcher.setting.Profile;
import org.jackhuang.hellominecraft.launcher.setting.Settings;
import org.jackhuang.hellominecraft.launcher.util.FileNameFilter;
import org.jackhuang.hellominecraft.launcher.core.ModInfo;
import org.jackhuang.hellominecraft.launcher.core.install.InstallerType;
import org.jackhuang.hellominecraft.launcher.core.version.GameDirType;
import org.jackhuang.hellominecraft.launcher.core.version.MinecraftVersion;
import org.jackhuang.hellominecraft.launcher.setting.DefaultMinecraftService;
import org.jackhuang.hellominecraft.launcher.setting.VersionSetting;
import org.jackhuang.hellominecraft.util.Event;
import org.jackhuang.hellominecraft.util.MessageBox;
import org.jackhuang.hellominecraft.util.OverridableSwingWorker;
import org.jackhuang.hellominecraft.util.version.MinecraftVersionRequest;
import org.jackhuang.hellominecraft.util.system.OS;
import org.jackhuang.hellominecraft.util.StrUtils;
import org.jackhuang.hellominecraft.util.ui.SwingUtils;
import org.jackhuang.hellominecraft.util.system.Java;

/**
 *
 * @author huangyuhui
 */
public final class GameSettingsPanel extends AnimatedPanel implements DropTargetListener {

    boolean isLoading = false;
    public MinecraftVersionRequest minecraftVersion;
    String mcVersion;

    final InstallerPanel installerPanels[] = new InstallerPanel[InstallerType.values().length];

    /**
     * Creates new form GameSettingsPanel
     */
    public GameSettingsPanel() {
        initComponents();
        setBackground(Color.white);
        setOpaque(true);

        for (int i = 0; i < InstallerType.values().length; i++)
            installerPanels[i] = new InstallerPanel(this, InstallerType.values()[i]);
        pnlGameDownloads = new GameDownloadPanel(this);

        initExplorationMenu();
        initManagementMenu();
        initExternalModsTable();
        initTabs();

        for (Java j : Java.JAVA)
            cboJava.addItem(j.getLocalizedName());

        dropTarget = new DropTarget(lstExternalMods, DnDConstants.ACTION_COPY_OR_MOVE, this);
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
                getProfile().service().version().open(mcVersion, a);
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
                    if (getProfile().service().version().renameVersion(mcVersion, newName))
                        refreshVersions();
            }
        });
        ppmManage.add(itm);
        itm = new JMenuItem(C.i18n("versions.manage.remove"));
        itm.addActionListener((e) -> {
            if (mcVersion != null && MessageBox.Show(C.i18n("versions.manage.remove.confirm") + mcVersion, MessageBox.YES_NO_OPTION) == MessageBox.YES_OPTION)
                if (getProfile().service().version().removeVersionFromDisk(mcVersion))
                    refreshVersions();
        });
        ppmManage.add(itm);
        itm = new JMenuItem(C.i18n("versions.manage.redownload_json"));
        itm.addActionListener((e) -> {
            if (mcVersion != null)
                getProfile().service().download().downloadMinecraftVersionJson(mcVersion);
        });
        ppmManage.add(itm);
        itm = new JMenuItem(C.i18n("versions.manage.redownload_assets_index"));
        itm.addActionListener((e) -> {
            if (mcVersion != null)
                getProfile().service().asset().refreshAssetsIndex(mcVersion);
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
            List<ModInfo> mods = getProfile().service().mod().getMods(getProfile().getSelectedVersion());
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
                List<ModInfo> mods = getProfile().service().mod().getMods(getProfile().getSelectedVersion());
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
                    installerPanels[0].refreshVersions();
                }
            }
        });
        ((NewTabPane) tabVersionEdit).initializing = true;
        tabVersionEdit.addTab(C.i18n("settings.tabs.game_download"), pnlGameDownloads); // NOI18N
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
        pnlSettings = new AnimatedPanel();
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
        pnlAdvancedSettings = new AnimatedPanel();
        chkDebug = new javax.swing.JCheckBox();
        lblJavaArgs = new javax.swing.JLabel();
        txtJavaArgs = new javax.swing.JTextField();
        txtMinecraftArgs = new javax.swing.JTextField();
        lblMinecraftArgs = new javax.swing.JLabel();
        lblPermSize = new javax.swing.JLabel();
        txtPermSize = new javax.swing.JTextField();
        chkNoJVMArgs = new javax.swing.JCheckBox();
        chkCancelWrapper = new javax.swing.JCheckBox();
        lblPrecalledCommand = new javax.swing.JLabel();
        txtPrecalledCommand = new javax.swing.JTextField();
        lblServerIP = new javax.swing.JLabel();
        txtServerIP = new javax.swing.JTextField();
        pnlModManagement = new AnimatedPanel();
        pnlModManagementContent = new javax.swing.JPanel();
        jScrollPane1 = new javax.swing.JScrollPane();
        lstExternalMods = new javax.swing.JTable();
        btnAddMod = new javax.swing.JButton();
        btnRemoveMod = new javax.swing.JButton();
        lblModInfo = new javax.swing.JLabel();
        pnlAutoInstall = new AnimatedPanel();
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
        chkFullscreen.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                chkFullscreenFocusLost(evt);
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
        cboLauncherVisibility.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                cboLauncherVisibilityFocusLost(evt);
            }
        });

        lblLauncherVisibility.setText(C.i18n("advancedsettings.launcher_visible")); // NOI18N

        lblRunDirectory.setText(C.i18n("settings.run_directory")); // NOI18N

        cboRunDirectory.setModel(new javax.swing.DefaultComboBoxModel(new String[] { C.i18n("advancedsettings.game_dir.default"), C.i18n("advancedsettings.game_dir.independent") }));
        cboRunDirectory.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                cboRunDirectoryFocusLost(evt);
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

        javax.swing.GroupLayout pnlSettingsLayout = new javax.swing.GroupLayout(pnlSettings);
        pnlSettings.setLayout(pnlSettingsLayout);
        pnlSettingsLayout.setHorizontalGroup(
            pnlSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pnlSettingsLayout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addGroup(pnlSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
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
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 402, Short.MAX_VALUE)
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
                        .addComponent(btnCleanGame)))
                .addGap(0, 0, 0))
        );
        pnlSettingsLayout.setVerticalGroup(
            pnlSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pnlSettingsLayout.createSequentialGroup()
                .addGap(0, 0, 0)
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
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 84, Short.MAX_VALUE)
                .addGroup(pnlSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(btnDownloadAllAssets)
                    .addComponent(btnCleanGame))
                .addContainerGap())
        );

        tabVersionEdit.addTab(C.i18n("settings"), pnlSettings); // NOI18N

        chkDebug.setText(C.i18n("advancedsettings.debug_mode")); // NOI18N
        chkDebug.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                chkDebugFocusLost(evt);
            }
        });

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
        chkNoJVMArgs.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                chkNoJVMArgsFocusLost(evt);
            }
        });

        chkCancelWrapper.setText(C.i18n("advancedsettings.cancel_wrapper_launcher")); // NOI18N
        chkCancelWrapper.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                chkCancelWrapperFocusLost(evt);
            }
        });

        lblPrecalledCommand.setText(C.i18n("advancedsettings.wrapper_launcher")); // NOI18N

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

        javax.swing.GroupLayout pnlAdvancedSettingsLayout = new javax.swing.GroupLayout(pnlAdvancedSettings);
        pnlAdvancedSettings.setLayout(pnlAdvancedSettingsLayout);
        pnlAdvancedSettingsLayout.setHorizontalGroup(
            pnlAdvancedSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(pnlAdvancedSettingsLayout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addGroup(pnlAdvancedSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(txtPrecalledCommand)
                    .addComponent(txtServerIP)
                    .addGroup(pnlAdvancedSettingsLayout.createSequentialGroup()
                        .addGroup(pnlAdvancedSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(lblPrecalledCommand)
                            .addGroup(pnlAdvancedSettingsLayout.createSequentialGroup()
                                .addComponent(chkDebug)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(chkCancelWrapper)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(chkNoJVMArgs))
                            .addComponent(lblServerIP))
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, pnlAdvancedSettingsLayout.createSequentialGroup()
                        .addGroup(pnlAdvancedSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(lblMinecraftArgs)
                            .addComponent(lblPermSize)
                            .addComponent(lblJavaArgs))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(pnlAdvancedSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(txtJavaArgs)
                            .addComponent(txtMinecraftArgs)
                            .addComponent(txtPermSize, javax.swing.GroupLayout.Alignment.TRAILING))))
                .addGap(0, 0, 0))
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
                .addComponent(lblPrecalledCommand)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(txtPrecalledCommand, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(lblServerIP)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(txtServerIP, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 76, Short.MAX_VALUE)
                .addGroup(pnlAdvancedSettingsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(chkDebug)
                    .addComponent(chkNoJVMArgs)
                    .addComponent(chkCancelWrapper))
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
                .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 889, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(pnlModManagementContentLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(btnRemoveMod, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(btnAddMod, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, pnlModManagementContentLayout.createSequentialGroup()
                .addComponent(lblModInfo, javax.swing.GroupLayout.DEFAULT_SIZE, 985, Short.MAX_VALUE)
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
                    .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 284, Short.MAX_VALUE))
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
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
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

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(pnlTop, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(tabVersionEdit)
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addComponent(pnlTop, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(tabVersionEdit)
                .addContainerGap())
        );

        ((NewTabPane)tabVersionEdit).initializing = false;
    }// </editor-fold>//GEN-END:initComponents
    // <editor-fold defaultstate="collapsed" desc="UI Events">
    private void cboProfilesItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_cboProfilesItemStateChanged
        if (!isLoading) {
            Settings.getInstance().setLast((String) cboProfiles.getSelectedItem());
            if (getProfile().service().version().getVersionCount() <= 0)
                versionChanged(null);
            prepareProfile(getProfile());
        }
    }//GEN-LAST:event_cboProfilesItemStateChanged

    private void btnNewProfileActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnNewProfileActionPerformed
        new NewProfileWindow(null).setVisible(true);
        loadProfiles();
    }//GEN-LAST:event_btnNewProfileActionPerformed

    private void btnRemoveProfileActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnRemoveProfileActionPerformed
        if (MessageBox.Show(C.i18n("ui.message.sure_remove", getProfile().getName()), MessageBox.YES_NO_OPTION) == MessageBox.NO_OPTION)
            return;
        if (Settings.delProfile(getProfile()))
            loadProfiles();
    }//GEN-LAST:event_btnRemoveProfileActionPerformed

    private void cboVersionsItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_cboVersionsItemStateChanged
        if (isLoading || evt.getStateChange() != ItemEvent.SELECTED || cboVersions.getSelectedIndex() < 0 || StrUtils.isBlank((String) cboVersions.getSelectedItem()))
            return;
        String mcv = (String) cboVersions.getSelectedItem();
        getProfile().setSelectedMinecraftVersion(mcv);
    }//GEN-LAST:event_cboVersionsItemStateChanged

    private void btnRefreshVersionsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnRefreshVersionsActionPerformed
        refreshVersions();
    }//GEN-LAST:event_btnRefreshVersionsActionPerformed

    private void btnExploreMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_btnExploreMouseClicked
        ppmExplore.show(evt.getComponent(), evt.getPoint().x, evt.getPoint().y);
    }//GEN-LAST:event_btnExploreMouseClicked

    private void btnModifyMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_btnModifyMouseClicked
        ppmManage.show(evt.getComponent(), evt.getPoint().x, evt.getPoint().y);
    }//GEN-LAST:event_btnModifyMouseClicked

    private void txtJavaArgsFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_txtJavaArgsFocusLost
        getProfile().getSelectedVersionSetting().setJavaArgs(txtJavaArgs.getText());
    }//GEN-LAST:event_txtJavaArgsFocusLost

    private void txtMinecraftArgsFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_txtMinecraftArgsFocusLost
        getProfile().getSelectedVersionSetting().setMinecraftArgs(txtMinecraftArgs.getText());
    }//GEN-LAST:event_txtMinecraftArgsFocusLost

    private void txtPermSizeFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_txtPermSizeFocusLost
        getProfile().getSelectedVersionSetting().setPermSize(txtPermSize.getText());
    }//GEN-LAST:event_txtPermSizeFocusLost

    private void chkDebugFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_chkDebugFocusLost
        getProfile().getSelectedVersionSetting().setDebug(chkDebug.isSelected());
    }//GEN-LAST:event_chkDebugFocusLost

    private void chkNoJVMArgsFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_chkNoJVMArgsFocusLost
        getProfile().getSelectedVersionSetting().setNoJVMArgs(chkNoJVMArgs.isSelected());
    }//GEN-LAST:event_chkNoJVMArgsFocusLost

    private void chkCancelWrapperFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_chkCancelWrapperFocusLost
        getProfile().getSelectedVersionSetting().setCanceledWrapper(chkCancelWrapper.isSelected());
    }//GEN-LAST:event_chkCancelWrapperFocusLost

    private void txtPrecalledCommandFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_txtPrecalledCommandFocusLost
        getProfile().getSelectedVersionSetting().setPrecalledCommand(txtPrecalledCommand.getText());
    }//GEN-LAST:event_txtPrecalledCommandFocusLost

    private void txtServerIPFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_txtServerIPFocusLost
        getProfile().getSelectedVersionSetting().setServerIp(txtServerIP.getText());
    }//GEN-LAST:event_txtServerIPFocusLost

    private void cboRunDirectoryFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_cboRunDirectoryFocusLost
        if (cboRunDirectory.getSelectedIndex() >= 0)
            getProfile().getSelectedVersionSetting().setGameDirType(GameDirType.values()[cboRunDirectory.getSelectedIndex()]);
    }//GEN-LAST:event_cboRunDirectoryFocusLost

    private void cboLauncherVisibilityFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_cboLauncherVisibilityFocusLost
        if (cboLauncherVisibility.getSelectedIndex() >= 0)
            getProfile().getSelectedVersionSetting().setLauncherVisibility(LauncherVisibility.values()[cboLauncherVisibility.getSelectedIndex()]);
    }//GEN-LAST:event_cboLauncherVisibilityFocusLost

    private void btnDownloadAllAssetsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnDownloadAllAssetsActionPerformed
        if (mcVersion != null)
            getProfile().service().asset().downloadAssets(mcVersion).run();
    }//GEN-LAST:event_btnDownloadAllAssetsActionPerformed

    private void txtMaxMemoryFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_txtMaxMemoryFocusLost
        getProfile().getSelectedVersionSetting().setMaxMemory(txtMaxMemory.getText());
    }//GEN-LAST:event_txtMaxMemoryFocusLost

    private void txtJavaDirFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_txtJavaDirFocusLost
        getProfile().getSelectedVersionSetting().setJavaDir(txtJavaDir.getText());
    }//GEN-LAST:event_txtJavaDirFocusLost

    private void chkFullscreenFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_chkFullscreenFocusLost
        getProfile().getSelectedVersionSetting().setFullscreen(chkFullscreen.isSelected());
    }//GEN-LAST:event_chkFullscreenFocusLost

    private void txtHeightFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_txtHeightFocusLost
        getProfile().getSelectedVersionSetting().setHeight(txtHeight.getText());
    }//GEN-LAST:event_txtHeightFocusLost

    private void txtWidthFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_txtWidthFocusLost
        getProfile().getSelectedVersionSetting().setWidth(txtWidth.getText());
    }//GEN-LAST:event_txtWidthFocusLost

    private void txtGameDirFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_txtGameDirFocusLost
        getProfile().setGameDir(txtGameDir.getText());
        loadVersions();
    }//GEN-LAST:event_txtGameDirFocusLost

    private void btnChoosingJavaDirActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnChoosingJavaDirActionPerformed
        if (cboJava.getSelectedIndex() != 1)
            return;
        JFileChooser fc = new JFileChooser();
        fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
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
            getProfile().getSelectedVersionSetting().setJavaDir(txtJavaDir.getText());
        } catch (IOException e) {
            HMCLog.warn("Failed to set java path.", e);
            MessageBox.Show(C.i18n("ui.label.failed_set") + e.getMessage());
        }
    }//GEN-LAST:event_btnChoosingJavaDirActionPerformed

    private void cboJavaItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_cboJavaItemStateChanged
        if (isLoading || evt.getStateChange() != ItemEvent.SELECTED || cboJava.getSelectedIndex() < 0 || StrUtils.isBlank((String) cboJava.getSelectedItem()))
            return;
        int idx = cboJava.getSelectedIndex();
        if (idx != -1) {
            Java j = Java.JAVA.get(idx);
            getProfile().getSelectedVersionSetting().setJava(j);
            txtJavaDir.setEnabled(idx == 1);
            txtJavaDir.setText(j.getHome() == null ? getProfile().getSelectedVersionSetting().getSettingsJavaDir() : j.getJava());
        }
    }//GEN-LAST:event_cboJavaItemStateChanged

    private void btnAddModActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnAddModActionPerformed
        JFileChooser fc = new JFileChooser();
        fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fc.setDialogTitle(C.i18n("mods.choose_mod"));
        fc.setMultiSelectionEnabled(true);
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION)
            return;
        boolean flag = true;
        for (File f : fc.getSelectedFiles())
            flag &= getProfile().service().mod().addMod(getProfile().getSelectedVersion(), f);
        reloadMods();
        if (!flag)
            MessageBox.Show(C.i18n("mods.failed"));
    }//GEN-LAST:event_btnAddModActionPerformed

    private void btnRemoveModActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnRemoveModActionPerformed
        getProfile().service().mod().removeMod(getProfile().getSelectedVersion(), SwingUtils.getValueBySelectedRow(lstExternalMods, lstExternalMods.getSelectedRows(), 1));
        reloadMods();
    }//GEN-LAST:event_btnRemoveModActionPerformed

    private void lstExternalModsKeyPressed(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_lstExternalModsKeyPressed
        if (evt.getKeyCode() == KeyEvent.VK_DELETE)
            btnRemoveModActionPerformed(null);
    }//GEN-LAST:event_lstExternalModsKeyPressed

    private void lblModInfoMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_lblModInfoMouseClicked
        int idx = lstExternalMods.getSelectedRow();
        if (idx > 0 && idx < getProfile().service().mod().getMods(getProfile().getSelectedVersion()).size())
            SwingUtils.openLink(getProfile().service().mod().getMods(getProfile().getSelectedVersion()).get(idx).url);
    }//GEN-LAST:event_lblModInfoMouseClicked

    private void btnChoosingGameDirActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnChoosingGameDirActionPerformed
        JFileChooser fc = new JFileChooser();
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fc.setDialogTitle(C.i18n("settings.choose_gamedir"));
        fc.setMultiSelectionEnabled(false);
        fc.showOpenDialog(this);
        if (fc.getSelectedFile() == null)
            return;
        try {
            String path = fc.getSelectedFile().getCanonicalPath();
            txtGameDir.setText(path);
            getProfile().setGameDir(path);
        } catch (IOException e) {
            HMCLog.warn("Failed to set game dir.", e);
            MessageBox.Show(C.i18n("ui.label.failed_set") + e.getMessage());
        }
    }//GEN-LAST:event_btnChoosingGameDirActionPerformed

    private void btnCleanGameActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnCleanGameActionPerformed
        getProfile().service().version().cleanFolder();
    }//GEN-LAST:event_btnCleanGameActionPerformed

    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Load">
    private void loadProfiles() {
        isLoading = true;
        cboProfiles.removeAllItems();
        int index = 0, i = 0;
        for (Profile s : Settings.getProfilesFiltered()) {
            cboProfiles.addItem(s.getName());
            if (StrUtils.isEquals(s.getName(), Settings.getInstance().getLast()))
                index = i;
            i++;
        }

        isLoading = false;
        if (index < cboProfiles.getItemCount()) {
            isLoading = true;
            cboProfiles.setSelectedIndex(index);
            isLoading = false;
            prepareProfile(getProfile());
        }
    }

    final Profile getProfile() {
        return Settings.getProfile((String) cboProfiles.getSelectedItem());
    }

    final String mcVersion() {
        return getProfile().getSelectedVersion();
    }

    Event<Void> onRefreshedVersions = (sender, e) -> {
        loadVersions();
        return true;
    };

    void prepareProfile(Profile profile) {
        if (profile == null)
            return;
        profile.selectedVersionChangedEvent.register(selectedVersionChangedEvent);
        profile.service().version().onRefreshedVersions.register(onRefreshedVersions);
        txtGameDir.setText(profile.getGameDir());

        loadVersions();
    }

    void prepareVersionSetting(VersionSetting profile) {
        if (profile == null)
            return;
        txtWidth.setText(profile.getWidth());
        txtHeight.setText(profile.getHeight());
        txtMaxMemory.setText(profile.getMaxMemory());
        txtPermSize.setText(profile.getPermSize());
        txtJavaArgs.setText(profile.getJavaArgs());
        txtMinecraftArgs.setText(profile.getMinecraftArgs());
        txtJavaDir.setText(profile.getSettingsJavaDir());
        txtPrecalledCommand.setText(profile.getPrecalledCommand());
        txtServerIP.setText(profile.getServerIp());
        chkDebug.setSelected(profile.isDebug());
        chkNoJVMArgs.setSelected(profile.isNoJVMArgs());
        chkFullscreen.setSelected(profile.isFullscreen());
        chkCancelWrapper.setSelected(profile.isCanceledWrapper());
        cboLauncherVisibility.setSelectedIndex(profile.getLauncherVisibility().ordinal());
        cboRunDirectory.setSelectedIndex(profile.getGameDirType().ordinal());

        isLoading = true;
        cboJava.setSelectedIndex(profile.getJavaIndexInAllJavas());
        isLoading = false;
        cboJavaItemStateChanged(new ItemEvent(cboJava, 0, cboJava.getSelectedItem(), ItemEvent.SELECTED));
    }

    void loadVersions() {
        isLoading = true;
        cboVersions.removeAllItems();
        int index = 0, i = 0;
        String selectedMC = getProfile().getSelectedVersion();
        for (MinecraftVersion each : getProfile().service().version().getVersions()) {
            cboVersions.addItem(each.id);
            if (StrUtils.isEquals(each.id, selectedMC))
                index = i;
            i++;
        }
        isLoading = false;
        if (index < cboVersions.getItemCount())
            cboVersions.setSelectedIndex(index);

        reloadMods();
        prepareVersionSetting(((DefaultMinecraftService) getProfile().service()).getVersionSetting(getProfile().getSelectedVersion()));
        loadMinecraftVersion(getProfile().getSelectedVersion());
    }

    /**
     * Anaylze the jar of selected minecraft version of current getProfile() to
     * get the version.
     */
    void loadMinecraftVersion(String id) {
        txtMinecraftVersion.setText("");
        if (id == null)
            return;
        minecraftVersion = MinecraftVersionRequest.minecraftVersion(getProfile().service().version().getMinecraftJar(id));
        txtMinecraftVersion.setText(MinecraftVersionRequest.getResponse(minecraftVersion));
    }

    //</editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Installer">
    String getMinecraftVersionFormatted() {
        return minecraftVersion == null ? "" : (StrUtils.formatVersion(minecraftVersion.version) == null) ? mcVersion : minecraftVersion.version;
    }

    @Override
    public void dragEnter(DropTargetDragEvent dtde) {
        DataFlavor[] f = dtde.getCurrentDataFlavors();
        if (f[0].match(DataFlavor.javaFileListFlavor))
            try {
                Transferable tr = dtde.getTransferable();
                List<File> files = (List<File>) tr.getTransferData(DataFlavor.javaFileListFlavor);
                for (File file : files)
                    getProfile().service().mod().addMod(getProfile().getSelectedVersion(), file);
            } catch (Exception ex) {
                HMCLog.warn("Failed to drop file.", ex);
            }
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
    }

    void refreshVersions() {
        getProfile().service().version().refreshVersions();
    }

    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Mods">
    private boolean reloadingMods = false;

    private synchronized void reloadMods() {
        if (reloadingMods)
            return;
        reloadingMods = true;
        DefaultTableModel model = SwingUtils.clearDefaultTable(lstExternalMods);
        new OverridableSwingWorker<List<ModInfo>>() {
            @Override
            protected void work() throws Exception {
                publish(getProfile().service().mod().recacheMods(getProfile().getSelectedVersion()));
            }
        }.reg(t -> {
            for (ModInfo x : t)
                model.addRow(new Object[] { x.isActive(), x, x.version });
            reloadingMods = false;
        }).execute();
    }

    // </editor-fold>
    public void versionChanged(String version) {
        this.mcVersion = version;
        prepareVersionSetting(getProfile().getVersionSetting(version));
        loadMinecraftVersion(version);
        for (InstallerPanel p : installerPanels)
            p.loadVersions();
    }

    @Override
    public void onSelected() {
        loadProfiles();
        if (getProfile().service().version().getVersionCount() <= 0)
            versionChanged(null);
        else
            versionChanged((String) cboVersions.getSelectedItem());
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
    private javax.swing.JButton btnModify;
    private javax.swing.JButton btnNewProfile;
    private javax.swing.JButton btnRefreshVersions;
    private javax.swing.JButton btnRemoveMod;
    private javax.swing.JButton btnRemoveProfile;
    private javax.swing.JComboBox cboJava;
    private javax.swing.JComboBox cboLauncherVisibility;
    private javax.swing.JComboBox cboProfiles;
    private javax.swing.JComboBox cboRunDirectory;
    private javax.swing.JComboBox cboVersions;
    private javax.swing.JCheckBox chkCancelWrapper;
    private javax.swing.JCheckBox chkDebug;
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
    private javax.swing.JLabel lblProfile;
    private javax.swing.JLabel lblRunDirectory;
    private javax.swing.JLabel lblServerIP;
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
    // End of variables declaration//GEN-END:variables

    private final javax.swing.JPanel pnlGameDownloads;
// </editor-fold>

    Event<String> selectedVersionChangedEvent = (Object sender, String e) -> {
        versionChanged(e);
        cboVersions.setToolTipText(e);
        return true;
    };
}
