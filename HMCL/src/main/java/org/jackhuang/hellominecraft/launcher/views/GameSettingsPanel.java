/*
 * Copyright 2013 huangyuhui <huanghongxun2008@126.com>
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.
 */
package org.jackhuang.hellominecraft.launcher.views;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import javax.swing.JFileChooser;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import org.jackhuang.hellominecraft.C;
import org.jackhuang.hellominecraft.HMCLog;
import org.jackhuang.hellominecraft.launcher.settings.LauncherVisibility;
import org.jackhuang.hellominecraft.launcher.utils.MCUtils;
import org.jackhuang.hellominecraft.launcher.utils.assets.IAssetsHandler;
import org.jackhuang.hellominecraft.launcher.utils.installers.InstallerVersionList;
import org.jackhuang.hellominecraft.launcher.utils.installers.InstallerVersionList.InstallerVersion;
import org.jackhuang.hellominecraft.launcher.utils.installers.forge.ForgeInstaller;
import org.jackhuang.hellominecraft.launcher.utils.installers.liteloader.LiteLoaderInstaller;
import org.jackhuang.hellominecraft.launcher.utils.installers.liteloader.LiteLoaderVersionList.LiteLoaderInstallerVersion;
import org.jackhuang.hellominecraft.launcher.utils.installers.optifine.OptiFineInstaller;
import org.jackhuang.hellominecraft.launcher.utils.installers.optifine.vanilla.OptiFineDownloadFormatter;
import org.jackhuang.hellominecraft.launcher.utils.download.DownloadType;
import org.jackhuang.hellominecraft.launcher.settings.Profile;
import org.jackhuang.hellominecraft.launcher.settings.Settings;
import org.jackhuang.hellominecraft.launcher.version.GameDirType;
import org.jackhuang.hellominecraft.launcher.version.MinecraftVersion;
import org.jackhuang.hellominecraft.tasks.Task;
import org.jackhuang.hellominecraft.tasks.TaskRunnableArg1;
import org.jackhuang.hellominecraft.tasks.TaskWindow;
import org.jackhuang.hellominecraft.tasks.communication.DefaultPreviousResult;
import org.jackhuang.hellominecraft.tasks.download.FileDownloadTask;
import org.jackhuang.hellominecraft.tasks.download.HTTPGetTask;
import org.jackhuang.hellominecraft.utils.system.IOUtils;
import org.jackhuang.hellominecraft.utils.system.MessageBox;
import org.jackhuang.hellominecraft.version.MinecraftVersionRequest;
import org.jackhuang.hellominecraft.utils.system.OS;
import org.jackhuang.hellominecraft.utils.StrUtils;
import org.jackhuang.hellominecraft.utils.SwingUtils;
import org.jackhuang.hellominecraft.version.MinecraftRemoteVersion;
import org.jackhuang.hellominecraft.version.MinecraftRemoteVersions;
import org.jackhuang.hellominecraft.views.Selector;

/**
 *
 * @author huangyuhui
 */
public class GameSettingsPanel extends javax.swing.JPanel {

    /**
     * Creates new form GameSettingsPanel
     */
    public GameSettingsPanel() {
        initComponents();
        setBackground(Color.white);
        setOpaque(true);

        forge = new InstallerHelper(lstForge, "forge");
        liteloader = new InstallerHelper(lstLiteLoader, "liteloader");
        optifine = new InstallerHelper(lstOptifine, "optifine");
        //<editor-fold defaultstate="collapsed" desc="Explore Menu">
        ppmExplore = new JPopupMenu();
        class ImplementedActionListener implements ActionListener {

            ImplementedActionListener(String s) {
                a = s;
            }
            String a;

            @Override
            public void actionPerformed(ActionEvent e) {
                Profile v = getProfile();
                if (v != null)
                    v.getMinecraftProvider().open(mcVersion, a);
            }
        }
        JMenuItem itm;
        itm = new JMenuItem(C.i18n("folder.game"));
        itm.addActionListener((e) -> {
            Profile v = getProfile();
            if (v != null) v.getMinecraftProvider().openSelf(mcVersion);
        });
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
        //</editor-fold>
        //<editor-fold defaultstate="collapsed" desc="Manage Menu">
        ppmManage = new JPopupMenu();
        itm = new JMenuItem(C.i18n("versions.manage.rename"));
        itm.addActionListener((e) -> {
            Profile v = getProfile();
            if (v != null && mcVersion != null) {
                String newName = JOptionPane.showInputDialog(C.i18n("versions.manage.rename.message"), mcVersion);
                if (newName != null)
                    if (v.getMinecraftProvider().renameVersion(mcVersion, newName))
                        refreshVersions();
            }
        });
        ppmManage.add(itm);
        itm = new JMenuItem(C.i18n("versions.manage.remove"));
        itm.addActionListener((e) -> {
            Profile v = getProfile();
            if (v != null && mcVersion != null && MessageBox.Show(C.i18n("versions.manage.remove.confirm") + mcVersion, MessageBox.YES_NO_OPTION) == MessageBox.YES_OPTION)
                if (v.getMinecraftProvider().removeVersionFromDisk(mcVersion))
                    refreshVersions();
        });
        ppmManage.add(itm);
        itm = new JMenuItem(C.i18n("versions.manage.redownload_json"));
        itm.addActionListener((e) -> {
            Profile v = getProfile();
            if (v != null && mcVersion != null)
                v.getMinecraftProvider().refreshJson(mcVersion);
        });
        ppmManage.add(itm);
        itm = new JMenuItem(C.i18n("versions.manage.redownload_assets_index"));
        itm.addActionListener((e) -> {
            Profile v = getProfile();
            if (v != null && mcVersion != null)
                v.getMinecraftProvider().refreshAssetsIndex(mcVersion);
        });
        ppmManage.add(itm);
        //</editor-fold>
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jPanel1 = new javax.swing.JPanel();
        jPanel4 = new javax.swing.JPanel();
        jLabel1 = new javax.swing.JLabel();
        cboProfiles = new javax.swing.JComboBox();
        cboVersions = new javax.swing.JComboBox();
        jLabel2 = new javax.swing.JLabel();
        jPanel5 = new javax.swing.JPanel();
        btnModify = new javax.swing.JButton();
        btnRefreshVersions = new javax.swing.JButton();
        txtMinecraftVersion = new javax.swing.JTextField();
        btnNewProfile = new javax.swing.JButton();
        btnRemoveProfile = new javax.swing.JButton();
        btnExplore = new javax.swing.JButton();
        tabVersionEdit = new javax.swing.JTabbedPane();
        jPanel22 = new javax.swing.JPanel();
        jLabel24 = new javax.swing.JLabel();
        txtGameDir = new javax.swing.JTextField();
        jLabel25 = new javax.swing.JLabel();
        txtWidth = new javax.swing.JTextField();
        txtHeight = new javax.swing.JTextField();
        jLabel9 = new javax.swing.JLabel();
        chkFullscreen = new javax.swing.JCheckBox();
        txtJavaDir = new javax.swing.JTextField();
        jLabel11 = new javax.swing.JLabel();
        jLabel27 = new javax.swing.JLabel();
        txtMaxMemory = new javax.swing.JTextField();
        lblMaxMemory = new javax.swing.JLabel();
        btnDownloadAllAssets = new javax.swing.JButton();
        cboLauncherVisibility = new javax.swing.JComboBox();
        jLabel10 = new javax.swing.JLabel();
        jLabel12 = new javax.swing.JLabel();
        cboGameDirType = new javax.swing.JComboBox();
        jPanel2 = new javax.swing.JPanel();
        chkDebug = new javax.swing.JCheckBox();
        jLabel26 = new javax.swing.JLabel();
        txtJavaArgs = new javax.swing.JTextField();
        txtMinecraftArgs = new javax.swing.JTextField();
        jLabel28 = new javax.swing.JLabel();
        jLabel29 = new javax.swing.JLabel();
        txtPermSize = new javax.swing.JTextField();
        chkNoJVMArgs = new javax.swing.JCheckBox();
        chkCancelWrapper = new javax.swing.JCheckBox();
        jLabel30 = new javax.swing.JLabel();
        txtWrapperLauncher = new javax.swing.JTextField();
        jLabel31 = new javax.swing.JLabel();
        txtServerIP = new javax.swing.JTextField();
        pnlAutoInstall = new javax.swing.JPanel();
        tabInstallers = new javax.swing.JTabbedPane();
        jPanel16 = new javax.swing.JPanel();
        jScrollPane11 = new javax.swing.JScrollPane();
        lstForge = new javax.swing.JTable();
        btnRefreshForge = new javax.swing.JButton();
        btnDownloadForge = new javax.swing.JButton();
        btnRetryForge = new javax.swing.JButton();
        pnlOptifine = new javax.swing.JPanel();
        jScrollPane13 = new javax.swing.JScrollPane();
        lstOptifine = new javax.swing.JTable();
        btnRefreshOptifine = new javax.swing.JButton();
        btnDownloadOptifine = new javax.swing.JButton();
        jPanel3 = new javax.swing.JPanel();
        btnInstallLiteLoader = new javax.swing.JButton();
        jScrollPane12 = new javax.swing.JScrollPane();
        lstLiteLoader = new javax.swing.JTable();
        btnRefreshLiteLoader = new javax.swing.JButton();
        btnRetryLiteLoader = new javax.swing.JButton();
        pnlGameDownloads = new javax.swing.JPanel();
        btnDownload = new javax.swing.JButton();
        jScrollPane2 = new javax.swing.JScrollPane();
        lstDownloads = new javax.swing.JTable();
        btnRefreshGameDownloads = new javax.swing.JButton();
        btnIncludeMinecraft = new javax.swing.JButton();

        setBackground(new java.awt.Color(255, 255, 255));

        java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("org/jackhuang/hellominecraft/launcher/I18N"); // NOI18N
        jLabel1.setText(bundle.getString("ui.label.profile")); // NOI18N

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

        jLabel2.setText(bundle.getString("ui.label.version")); // NOI18N

        javax.swing.GroupLayout jPanel4Layout = new javax.swing.GroupLayout(jPanel4);
        jPanel4.setLayout(jPanel4Layout);
        jPanel4Layout.setHorizontalGroup(
            jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel4Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel4Layout.createSequentialGroup()
                        .addComponent(jLabel1)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(cboProfiles, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .addGroup(jPanel4Layout.createSequentialGroup()
                        .addComponent(jLabel2)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(cboVersions, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))))
        );
        jPanel4Layout.setVerticalGroup(
            jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel4Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(cboProfiles, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel1))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(cboVersions, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel2))
                .addContainerGap(11, Short.MAX_VALUE))
        );

        btnModify.setText(bundle.getString("settings.manage")); // NOI18N
        btnModify.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                btnModifyMouseClicked(evt);
            }
        });

        btnRefreshVersions.setText(bundle.getString("ui.button.refresh")); // NOI18N
        btnRefreshVersions.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnRefreshVersionsActionPerformed(evt);
            }
        });

        txtMinecraftVersion.setEditable(false);

        btnNewProfile.setText(bundle.getString("setupwindow.new")); // NOI18N
        btnNewProfile.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnNewProfileActionPerformed(evt);
            }
        });

        btnRemoveProfile.setText(bundle.getString("ui.button.delete")); // NOI18N
        btnRemoveProfile.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnRemoveProfileActionPerformed(evt);
            }
        });

        btnExplore.setText(bundle.getString("settings.explore")); // NOI18N
        btnExplore.setToolTipText("");
        btnExplore.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                btnExploreMouseClicked(evt);
            }
        });

        javax.swing.GroupLayout jPanel5Layout = new javax.swing.GroupLayout(jPanel5);
        jPanel5.setLayout(jPanel5Layout);
        jPanel5Layout.setHorizontalGroup(
            jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel5Layout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(btnNewProfile, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(txtMinecraftVersion))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(btnRemoveProfile, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(btnRefreshVersions, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(btnModify, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(btnExplore, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        jPanel5Layout.setVerticalGroup(
            jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel5Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(btnNewProfile, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(btnRemoveProfile, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(btnExplore, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(txtMinecraftVersion, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(btnRefreshVersions, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(btnModify, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addComponent(jPanel4, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanel5, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(jPanel4, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jPanel5, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addGap(0, 0, Short.MAX_VALUE))
        );

        tabVersionEdit.setName("tabVersionEdit"); // NOI18N

        jLabel24.setText(bundle.getString("settings.game_directory")); // NOI18N

        txtGameDir.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                txtGameDirFocusLost(evt);
            }
        });

        jLabel25.setText(bundle.getString("settings.dimension")); // NOI18N

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

        jLabel9.setText("x");

        chkFullscreen.setText(bundle.getString("settings.fullscreen")); // NOI18N
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

        jLabel11.setText(bundle.getString("settings.java_dir")); // NOI18N

        jLabel27.setText(bundle.getString("settings.max_memory")); // NOI18N

        txtMaxMemory.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                txtMaxMemoryFocusLost(evt);
            }
        });

        lblMaxMemory.setText(C.i18n("settings.physical_memory") + ": " + OS.getTotalPhysicalMemory() / 1024 / 1024 + "MB");

        btnDownloadAllAssets.setText(bundle.getString("assets.download_all")); // NOI18N
        btnDownloadAllAssets.setToolTipText("");
        btnDownloadAllAssets.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnDownloadAllAssetsActionPerformed(evt);
            }
        });

        cboLauncherVisibility.setModel(new javax.swing.DefaultComboBoxModel(new String[] { C.I18N.getString("advancedsettings.launcher_visibility.close"), C.I18N.getString("advancedsettings.launcher_visibility.hide"), C.I18N.getString("advancedsettings.launcher_visibility.keep") }));
        cboLauncherVisibility.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                cboLauncherVisibilityFocusLost(evt);
            }
        });

        jLabel10.setText(bundle.getString("advancedsettings.launcher_visible")); // NOI18N

        jLabel12.setText(bundle.getString("advancedsettings.run_directory")); // NOI18N

        cboGameDirType.setModel(new javax.swing.DefaultComboBoxModel(new String[] { C.I18N.getString("advancedsettings.game_dir.default"), C.I18N.getString("advancedsettings.game_dir.independent") }));
        cboGameDirType.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                cboGameDirTypeFocusLost(evt);
            }
        });

        javax.swing.GroupLayout jPanel22Layout = new javax.swing.GroupLayout(jPanel22);
        jPanel22.setLayout(jPanel22Layout);
        jPanel22Layout.setHorizontalGroup(
            jPanel22Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel22Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel22Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel22Layout.createSequentialGroup()
                        .addComponent(btnDownloadAllAssets)
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addGroup(jPanel22Layout.createSequentialGroup()
                        .addGroup(jPanel22Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel11)
                            .addComponent(jLabel27)
                            .addComponent(jLabel24)
                            .addComponent(jLabel12)
                            .addComponent(jLabel10)
                            .addComponent(jLabel25))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel22Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(cboGameDirType, javax.swing.GroupLayout.Alignment.TRAILING, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(cboLauncherVisibility, javax.swing.GroupLayout.Alignment.TRAILING, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(txtJavaDir, javax.swing.GroupLayout.Alignment.TRAILING)
                            .addGroup(jPanel22Layout.createSequentialGroup()
                                .addComponent(txtMaxMemory)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(lblMaxMemory))
                            .addComponent(txtGameDir)
                            .addGroup(jPanel22Layout.createSequentialGroup()
                                .addComponent(txtWidth, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(jLabel9)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(txtHeight, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 306, Short.MAX_VALUE)
                                .addComponent(chkFullscreen)))))
                .addContainerGap())
        );
        jPanel22Layout.setVerticalGroup(
            jPanel22Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel22Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel22Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(txtGameDir, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel24))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel22Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(txtJavaDir, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel11))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel22Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lblMaxMemory)
                    .addComponent(txtMaxMemory, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel27))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel22Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(cboLauncherVisibility, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel10))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel22Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(cboGameDirType, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel12))
                .addGap(4, 4, 4)
                .addGroup(jPanel22Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(txtHeight, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(chkFullscreen, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel9, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel25)
                    .addComponent(txtWidth, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 92, Short.MAX_VALUE)
                .addComponent(btnDownloadAllAssets)
                .addContainerGap())
        );

        tabVersionEdit.addTab(bundle.getString("settings"), jPanel22); // NOI18N

        chkDebug.setText(bundle.getString("advencedsettings.debug_mode")); // NOI18N
        chkDebug.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                chkDebugFocusLost(evt);
            }
        });

        jLabel26.setText(bundle.getString("advancedsettings.jvm_args")); // NOI18N

        txtJavaArgs.setToolTipText(bundle.getString("advancedsettings.java_args_default")); // NOI18N
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

        jLabel28.setText(bundle.getString("advancedsettings.Minecraft_arguments")); // NOI18N

        jLabel29.setText(bundle.getString("advancedsettings.java_permanent_generation_space")); // NOI18N

        txtPermSize.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                txtPermSizeFocusLost(evt);
            }
        });

        chkNoJVMArgs.setText(bundle.getString("advancedsettings.no_jvm_args")); // NOI18N
        chkNoJVMArgs.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                chkNoJVMArgsFocusLost(evt);
            }
        });

        chkCancelWrapper.setText("取消包裹启动器（出现奇怪问题时可尝试使用,与调试模式冲突）");
        chkCancelWrapper.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                chkCancelWrapperFocusLost(evt);
            }
        });

        jLabel30.setText(bundle.getString("advancedsettings.wrapper_launcher")); // NOI18N

        txtWrapperLauncher.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                txtWrapperLauncherFocusLost(evt);
            }
        });

        jLabel31.setText(bundle.getString("advancedsettings.server_ip")); // NOI18N

        txtServerIP.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                txtServerIPFocusLost(evt);
            }
        });

        javax.swing.GroupLayout jPanel2Layout = new javax.swing.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(txtWrapperLauncher)
                    .addComponent(txtServerIP)
                    .addGroup(jPanel2Layout.createSequentialGroup()
                        .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel30)
                            .addGroup(jPanel2Layout.createSequentialGroup()
                                .addComponent(chkDebug)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(chkCancelWrapper)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(chkNoJVMArgs))
                            .addComponent(jLabel31))
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel2Layout.createSequentialGroup()
                        .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel28)
                            .addComponent(jLabel29)
                            .addComponent(jLabel26))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(txtJavaArgs)
                            .addComponent(txtMinecraftArgs)
                            .addComponent(txtPermSize, javax.swing.GroupLayout.Alignment.TRAILING))))
                .addContainerGap())
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(txtJavaArgs, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel26))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(txtMinecraftArgs, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel28))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(txtPermSize, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel29))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jLabel30)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(txtWrapperLauncher, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jLabel31)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(txtServerIP, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 85, Short.MAX_VALUE)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(chkDebug)
                    .addComponent(chkNoJVMArgs)
                    .addComponent(chkCancelWrapper))
                .addContainerGap())
        );

        tabVersionEdit.addTab(bundle.getString("advancedsettings"), jPanel2); // NOI18N

        lstForge.setModel(SwingUtils.makeDefaultTableModel(new String[]{C.I18N.getString("install.version"), C.I18N.getString("install.mcversion")},
            new Class[]{String.class, String.class}, new boolean[]{false, false}));
    lstForge.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
    jScrollPane11.setViewportView(lstForge);

    btnRefreshForge.setText(bundle.getString("ui.button.refresh")); // NOI18N
    btnRefreshForge.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            btnRefreshForgeActionPerformed(evt);
        }
    });

    btnDownloadForge.setText(bundle.getString("ui.button.install")); // NOI18N
    btnDownloadForge.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            btnDownloadForgeActionPerformed(evt);
        }
    });

    btnRetryForge.setText(bundle.getString("ui.button.retry")); // NOI18N
    btnRetryForge.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            btnRetryForgeActionPerformed(evt);
        }
    });

    javax.swing.GroupLayout jPanel16Layout = new javax.swing.GroupLayout(jPanel16);
    jPanel16.setLayout(jPanel16Layout);
    jPanel16Layout.setHorizontalGroup(
        jPanel16Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
        .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel16Layout.createSequentialGroup()
            .addComponent(jScrollPane11, javax.swing.GroupLayout.DEFAULT_SIZE, 587, Short.MAX_VALUE)
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addGroup(jPanel16Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                .addComponent(btnRetryForge, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(btnDownloadForge, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(btnRefreshForge, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
            .addContainerGap())
    );
    jPanel16Layout.setVerticalGroup(
        jPanel16Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
        .addComponent(jScrollPane11, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
        .addGroup(jPanel16Layout.createSequentialGroup()
            .addComponent(btnDownloadForge)
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addComponent(btnRetryForge)
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addComponent(btnRefreshForge)
            .addGap(0, 210, Short.MAX_VALUE))
    );

    tabInstallers.addTab("Forge", jPanel16);

    lstOptifine.setModel(SwingUtils.makeDefaultTableModel(new String[]{C.I18N.getString("install.version"), C.I18N.getString("install.mcversion")},
        new Class[]{String.class, String.class}, new boolean[]{false, false}));
lstOptifine.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
jScrollPane13.setViewportView(lstOptifine);

btnRefreshOptifine.setText(bundle.getString("ui.button.refresh")); // NOI18N
btnRefreshOptifine.addActionListener(new java.awt.event.ActionListener() {
    public void actionPerformed(java.awt.event.ActionEvent evt) {
        btnRefreshOptifineActionPerformed(evt);
    }
    });

    btnDownloadOptifine.setText(bundle.getString("ui.button.install")); // NOI18N
    btnDownloadOptifine.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            btnDownloadOptifineActionPerformed(evt);
        }
    });

    javax.swing.GroupLayout pnlOptifineLayout = new javax.swing.GroupLayout(pnlOptifine);
    pnlOptifine.setLayout(pnlOptifineLayout);
    pnlOptifineLayout.setHorizontalGroup(
        pnlOptifineLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
        .addGroup(pnlOptifineLayout.createSequentialGroup()
            .addComponent(jScrollPane13, javax.swing.GroupLayout.DEFAULT_SIZE, 587, Short.MAX_VALUE)
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addGroup(pnlOptifineLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                .addComponent(btnDownloadOptifine, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(btnRefreshOptifine))
            .addContainerGap())
    );
    pnlOptifineLayout.setVerticalGroup(
        pnlOptifineLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
        .addComponent(jScrollPane13, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
        .addGroup(pnlOptifineLayout.createSequentialGroup()
            .addComponent(btnDownloadOptifine)
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addComponent(btnRefreshOptifine)
            .addGap(0, 239, Short.MAX_VALUE))
    );

    tabInstallers.addTab("OptiFine", pnlOptifine);

    btnInstallLiteLoader.setText(bundle.getString("ui.button.install")); // NOI18N
    btnInstallLiteLoader.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            btnInstallLiteLoaderActionPerformed(evt);
        }
    });

    lstLiteLoader.setModel(SwingUtils.makeDefaultTableModel(new String[]{C.I18N.getString("install.version"), C.I18N.getString("install.mcversion")},
        new Class[]{String.class, String.class}, new boolean[]{false, false}));
lstLiteLoader.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
jScrollPane12.setViewportView(lstLiteLoader);

btnRefreshLiteLoader.setText(bundle.getString("ui.button.refresh")); // NOI18N
btnRefreshLiteLoader.addActionListener(new java.awt.event.ActionListener() {
    public void actionPerformed(java.awt.event.ActionEvent evt) {
        btnRefreshLiteLoaderActionPerformed(evt);
    }
    });

    btnRetryLiteLoader.setText(bundle.getString("ui.button.retry")); // NOI18N
    btnRetryLiteLoader.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            btnRetryLiteLoaderActionPerformed(evt);
        }
    });

    javax.swing.GroupLayout jPanel3Layout = new javax.swing.GroupLayout(jPanel3);
    jPanel3.setLayout(jPanel3Layout);
    jPanel3Layout.setHorizontalGroup(
        jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
        .addGroup(jPanel3Layout.createSequentialGroup()
            .addComponent(jScrollPane12, javax.swing.GroupLayout.DEFAULT_SIZE, 587, Short.MAX_VALUE)
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                .addComponent(btnInstallLiteLoader, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(btnRetryLiteLoader, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(btnRefreshLiteLoader))
            .addContainerGap())
    );
    jPanel3Layout.setVerticalGroup(
        jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
        .addComponent(jScrollPane12, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
        .addGroup(jPanel3Layout.createSequentialGroup()
            .addComponent(btnInstallLiteLoader)
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addComponent(btnRetryLiteLoader)
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addComponent(btnRefreshLiteLoader)
            .addGap(0, 210, Short.MAX_VALUE))
    );

    tabInstallers.addTab("LiteLoader", jPanel3);

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

    tabVersionEdit.addTab(bundle.getString("settings.tabs.installers"), pnlAutoInstall); // NOI18N

    btnDownload.setText(bundle.getString("ui.button.download")); // NOI18N
    btnDownload.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            btnDownloadActionPerformed(evt);
        }
    });

    lstDownloads.setModel(SwingUtils.makeDefaultTableModel(new String[]{C.I18N.getString("install.version"), C.I18N.getString("install.time"), C.I18N.getString("install.type")},new Class[]{String.class, String.class, String.class}, new boolean[]{false, false, false}));
    lstDownloads.setToolTipText("");
    lstDownloads.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
    jScrollPane2.setViewportView(lstDownloads);

    btnRefreshGameDownloads.setText(bundle.getString("ui.button.refresh")); // NOI18N
    btnRefreshGameDownloads.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            btnRefreshGameDownloadsActionPerformed(evt);
        }
    });

    javax.swing.GroupLayout pnlGameDownloadsLayout = new javax.swing.GroupLayout(pnlGameDownloads);
    pnlGameDownloads.setLayout(pnlGameDownloadsLayout);
    pnlGameDownloadsLayout.setHorizontalGroup(
        pnlGameDownloadsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
        .addGroup(pnlGameDownloadsLayout.createSequentialGroup()
            .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 592, Short.MAX_VALUE)
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addGroup(pnlGameDownloadsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                .addComponent(btnRefreshGameDownloads, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(btnDownload))
            .addContainerGap())
    );
    pnlGameDownloadsLayout.setVerticalGroup(
        pnlGameDownloadsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
        .addGroup(pnlGameDownloadsLayout.createSequentialGroup()
            .addComponent(btnRefreshGameDownloads)
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(btnDownload))
        .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 320, Short.MAX_VALUE)
    );

    tabVersionEdit.addTab(bundle.getString("settings.tabs.game_download"), pnlGameDownloads); // NOI18N

    btnIncludeMinecraft.setText(bundle.getString("setupwindow.include_minecraft")); // NOI18N
    btnIncludeMinecraft.addActionListener(new java.awt.event.ActionListener() {
        public void actionPerformed(java.awt.event.ActionEvent evt) {
            btnIncludeMinecraftActionPerformed(evt);
        }
    });

    javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
    this.setLayout(layout);
    layout.setHorizontalGroup(
        layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
        .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
            .addContainerGap()
            .addComponent(btnIncludeMinecraft)
            .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        .addComponent(tabVersionEdit)
    );
    layout.setVerticalGroup(
        layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
        .addGroup(layout.createSequentialGroup()
            .addComponent(jPanel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addComponent(tabVersionEdit)
            .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
            .addComponent(btnIncludeMinecraft)
            .addContainerGap())
    );
    }// </editor-fold>//GEN-END:initComponents
    // <editor-fold defaultstate="collapsed" desc="UI Events">    
    private void cboProfilesItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_cboProfilesItemStateChanged
        if (isLoading) return;
        profile = getProfile();
        if (profile.getMinecraftProvider().getVersionCount() <= 0)
            versionChanged(profile, null);
        prepare(profile);
    }//GEN-LAST:event_cboProfilesItemStateChanged

    private void btnNewProfileActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnNewProfileActionPerformed
        NewProfileWindow window = new NewProfileWindow(null);
        window.setVisible(true);
        loadProfiles();
    }//GEN-LAST:event_btnNewProfileActionPerformed

    private void btnRemoveProfileActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnRemoveProfileActionPerformed
        if (profile == null) return;
        if (MessageBox.Show(C.i18n("ui.message.sure_remove", profile.getName()), MessageBox.YES_NO_OPTION) == MessageBox.NO_OPTION) return;
        if(Settings.delVersion(profile)) {
            cboProfiles.removeItem(profile.getName());
            profile = Settings.getOneProfile();
            if (profile != null) {
                prepare(profile);
                loadVersions();
            }
        }
    }//GEN-LAST:event_btnRemoveProfileActionPerformed

    private void cboVersionsItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_cboVersionsItemStateChanged
        
        if (isLoading || evt.getStateChange() != ItemEvent.SELECTED || cboVersions.getSelectedIndex() < 0 || StrUtils.isBlank((String) cboVersions.getSelectedItem()) || getProfile() == null)
            return;
        loadMinecraftVersion((String) cboVersions.getSelectedItem());
        versionChanged(getProfile(), (String) cboVersions.getSelectedItem());
        
        getProfile().setSelectedMinecraftVersion(cboVersions.getSelectedItem().toString());
        cboVersions.setToolTipText(cboVersions.getSelectedItem().toString());
        Settings.save();
    }//GEN-LAST:event_cboVersionsItemStateChanged

    private void btnRefreshVersionsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnRefreshVersionsActionPerformed
        refreshVersions();
    }//GEN-LAST:event_btnRefreshVersionsActionPerformed

    private void btnRefreshForgeActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnRefreshForgeActionPerformed
        forge.refreshVersions();
    }//GEN-LAST:event_btnRefreshForgeActionPerformed

    private void btnDownloadForgeActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnDownloadForgeActionPerformed
        int idx = lstForge.getSelectedRow();
        if (idx == -1) {
            MessageBox.Show(C.i18n("install.not_refreshed"));
            return;
        }
        InstallerVersion v = forge.getVersion(idx);//forgeVersions.get(idx);
        String url;
        File filepath = IOUtils.tryGetCanonicalFile(IOUtils.currentDirWithSeparator() + "forge-installer.jar");
        if (v.installer != null) {
            url = v.installer;
            TaskWindow.getInstance()
                    .addTask(new FileDownloadTask(url, filepath).setTag("forge"))
                    .addTask(new ForgeInstaller(profile.getMinecraftProvider(), filepath))
                    .start();
        }
    }//GEN-LAST:event_btnDownloadForgeActionPerformed

    private void btnRetryForgeActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnRetryForgeActionPerformed
        if (profile == null) return;
        MinecraftVersion v = profile.getMinecraftProvider().getVersionById(mcVersion);
        if (v == null) return;
        TaskWindow.getInstance().addTask(new ForgeInstaller(profile.getMinecraftProvider(), IOUtils.tryGetCanonicalFile(IOUtils.currentDirWithSeparator() + "forge-installer.jar"))).start();
    }//GEN-LAST:event_btnRetryForgeActionPerformed

    private void btnRefreshOptifineActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnRefreshOptifineActionPerformed
        optifine.refreshVersions();
    }//GEN-LAST:event_btnRefreshOptifineActionPerformed

    private void btnDownloadOptifineActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnDownloadOptifineActionPerformed
        int idx = lstOptifine.getSelectedRow();
        if (idx == -1) {
            MessageBox.Show(C.i18n("install.not_refreshed"));
            return;
        }
        InstallerVersion v = optifine.getVersion(idx);
        File filepath = IOUtils.tryGetCanonicalFile(IOUtils.currentDirWithSeparator() + "optifine-installer.jar");
        if (v.installer != null) {
            OptiFineDownloadFormatter task = new OptiFineDownloadFormatter(v.installer);
            TaskWindow.getInstance().addTask(task)
                    .addTask(new FileDownloadTask(filepath).registerPreviousResult(task).setTag("optifine"))
                    .addTask(new OptiFineInstaller(profile, v.selfVersion, filepath))
                    .start();
        }
    }//GEN-LAST:event_btnDownloadOptifineActionPerformed

    private void btnInstallLiteLoaderActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnInstallLiteLoaderActionPerformed
        int idx = lstLiteLoader.getSelectedRow();
        if (idx == -1) {
            MessageBox.Show(C.i18n("install.not_refreshed"));
            return;
        }
        InstallerVersion v = liteloader.getVersion(idx);
        String url;
        File filepath = IOUtils.tryGetCanonicalFile(IOUtils.currentDirWithSeparator() + "liteloader-universal.jar");
        url = v.universal;
        FileDownloadTask task = (FileDownloadTask) new FileDownloadTask(url, filepath).setTag("LiteLoader");
        TaskWindow.getInstance()
                .addTask(task).addTask(new LiteLoaderInstaller(profile, (LiteLoaderInstallerVersion) v).registerPreviousResult(task))
                .start();
    }//GEN-LAST:event_btnInstallLiteLoaderActionPerformed

    private void btnRefreshLiteLoaderActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnRefreshLiteLoaderActionPerformed
        liteloader.refreshVersions();
    }//GEN-LAST:event_btnRefreshLiteLoaderActionPerformed

    private void btnRetryLiteLoaderActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnRetryLiteLoaderActionPerformed
        if (profile == null) return;
        int idx = lstLiteLoader.getSelectedRow();
        if (idx == -1) return;
        InstallerVersion v = liteloader.getVersion(idx);
        File filepath = new File(IOUtils.currentDir(), "liteloader-universal.jar");
        TaskWindow.getInstance().addTask(new LiteLoaderInstaller(profile, (LiteLoaderInstallerVersion) v, filepath)).start();
    }//GEN-LAST:event_btnRetryLiteLoaderActionPerformed

    private void btnDownloadActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnDownloadActionPerformed
        downloadMinecraft(Settings.getInstance().getDownloadSource());
        refreshVersions();
    }//GEN-LAST:event_btnDownloadActionPerformed

    private void btnRefreshGameDownloadsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnRefreshGameDownloadsActionPerformed
        refreshDownloads(Settings.getInstance().getDownloadSource());
    }//GEN-LAST:event_btnRefreshGameDownloadsActionPerformed

    private void btnExploreMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_btnExploreMouseClicked
        ppmExplore.show(evt.getComponent(), evt.getPoint().x, evt.getPoint().y);
    }//GEN-LAST:event_btnExploreMouseClicked

    private void btnIncludeMinecraftActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnIncludeMinecraftActionPerformed
        JFileChooser fc = new JFileChooser(IOUtils.currentDir());
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        int ret = fc.showOpenDialog(this);
        if (ret == JFileChooser.APPROVE_OPTION) {
            File newGameDir = fc.getSelectedFile();
            String name = JOptionPane.showInputDialog(C.i18n("setupwindow.give_a_name"));
            if (StrUtils.isBlank(name)) {
                MessageBox.Show(C.i18n("setupwindow.no_empty_name"));
                return;
            }
            Settings.trySetVersion(new Profile(name).setGameDir(newGameDir.getAbsolutePath()));
            MessageBox.Show(C.i18n("setupwindow.find_in_configurations"));
            loadProfiles();
        }
    }//GEN-LAST:event_btnIncludeMinecraftActionPerformed

    private void btnModifyMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_btnModifyMouseClicked
        ppmManage.show(evt.getComponent(), evt.getPoint().x, evt.getPoint().y);
    }//GEN-LAST:event_btnModifyMouseClicked

    private void txtJavaArgsFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_txtJavaArgsFocusLost
        profile.setJavaArgs(txtJavaArgs.getText());
    }//GEN-LAST:event_txtJavaArgsFocusLost

    private void txtMinecraftArgsFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_txtMinecraftArgsFocusLost
        profile.setMinecraftArgs(txtMinecraftArgs.getText());
    }//GEN-LAST:event_txtMinecraftArgsFocusLost

    private void txtPermSizeFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_txtPermSizeFocusLost
        profile.setPermSize(txtPermSize.getText());
    }//GEN-LAST:event_txtPermSizeFocusLost

    private void chkDebugFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_chkDebugFocusLost
        profile.setDebug(chkDebug.isSelected());
    }//GEN-LAST:event_chkDebugFocusLost

    private void chkNoJVMArgsFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_chkNoJVMArgsFocusLost
        profile.setNoJVMArgs(chkNoJVMArgs.isSelected());
    }//GEN-LAST:event_chkNoJVMArgsFocusLost

    private void chkCancelWrapperFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_chkCancelWrapperFocusLost
        profile.setCanceledWrapper(chkCancelWrapper.isSelected());
    }//GEN-LAST:event_chkCancelWrapperFocusLost

    private void txtWrapperLauncherFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_txtWrapperLauncherFocusLost
        profile.setWrapperLauncher(txtWrapperLauncher.getText());
    }//GEN-LAST:event_txtWrapperLauncherFocusLost

    private void txtServerIPFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_txtServerIPFocusLost
        profile.setServerIp(txtServerIP.getText());
    }//GEN-LAST:event_txtServerIPFocusLost

    private void cboGameDirTypeFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_cboGameDirTypeFocusLost
        if (cboGameDirType.getSelectedIndex() >= 0)
            profile.setGameDirType(GameDirType.values()[cboGameDirType.getSelectedIndex()]);
    }//GEN-LAST:event_cboGameDirTypeFocusLost

    private void cboLauncherVisibilityFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_cboLauncherVisibilityFocusLost
        if (cboLauncherVisibility.getSelectedIndex() >= 0)
            profile.setLauncherVisibility(LauncherVisibility.values()[cboLauncherVisibility.getSelectedIndex()]);
    }//GEN-LAST:event_cboLauncherVisibilityFocusLost

    private void btnDownloadAllAssetsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnDownloadAllAssetsActionPerformed
        boolean flag = false;
        ArrayList<String> al = new ArrayList<>();
        if (minecraftVersion == null) {
            MessageBox.Show(C.i18n("mainwindow.no_version"));
            return;
        }
        String s = StrUtils.formatVersion(minecraftVersion.version);
        if (StrUtils.isBlank(s)) return;
        for (IAssetsHandler a : IAssetsHandler.getAssetsHandlers()) {
            if (a.isVersionAllowed(s)) {
                downloadAssets(a);
                return;
            }
            al.add(a.getName());
        }
        if (!flag) {
            Selector selector = new Selector(MainFrame.instance, al.toArray(new String[0]), C.i18n("assets.unkown_type_select_one", mcVersion));
            selector.setVisible(true);
            if (selector.sel != -1)
                downloadAssets(IAssetsHandler.getAssetsHandler(selector.sel));
        }
    }//GEN-LAST:event_btnDownloadAllAssetsActionPerformed

    private void txtMaxMemoryFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_txtMaxMemoryFocusLost
        profile.setMaxMemory(txtMaxMemory.getText());
    }//GEN-LAST:event_txtMaxMemoryFocusLost

    private void txtJavaDirFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_txtJavaDirFocusLost
        profile.setJavaDir(txtJavaDir.getText());
    }//GEN-LAST:event_txtJavaDirFocusLost

    private void chkFullscreenFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_chkFullscreenFocusLost
        profile.setFullscreen(chkFullscreen.isSelected());
    }//GEN-LAST:event_chkFullscreenFocusLost

    private void txtHeightFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_txtHeightFocusLost
        profile.setHeight(txtHeight.getText());
    }//GEN-LAST:event_txtHeightFocusLost

    private void txtWidthFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_txtWidthFocusLost
        profile.setWidth(txtWidth.getText());
    }//GEN-LAST:event_txtWidthFocusLost

    private void txtGameDirFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_txtGameDirFocusLost
        if (profile == null) return;
        profile.setGameDir(txtGameDir.getText());
        loadVersions();
    }//GEN-LAST:event_txtGameDirFocusLost

    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Load">
    private void loadProfiles() {
        isLoading = true;
        cboProfiles.removeAllItems();
        Profile firstProfile = null, selectedProfile = null;
        int index = 0, i = 0;
        for (Profile s : Settings.getProfiles()) {
            if (firstProfile == null) firstProfile = s;
            cboProfiles.addItem(s.getName());
            if (Settings.getInstance().getLast() != null && Settings.getInstance().getLast().equals(s.getName())) {
                index = i;
                selectedProfile = s;
            }
            i++;
        }
        if (selectedProfile == null) selectedProfile = Settings.getOneProfile();

        isLoading = false;
        if (index < cboProfiles.getItemCount()) {
            cboProfiles.setSelectedIndex(index);
            profile = selectedProfile;
            if (profile == null) profile = firstProfile;
            prepare(profile);
            loadVersions();
        }
    }

    final Profile getProfile() {
        if (cboProfiles.getSelectedIndex() >= 0)
            return Settings.getVersion(cboProfiles.getSelectedItem().toString());
        else return null;
    }

    void prepare(Profile profile) {
        if (profile == null) return;
        txtWidth.setText(profile.getWidth());
        txtHeight.setText(profile.getHeight());
        txtMaxMemory.setText(profile.getMaxMemory());
        txtPermSize.setText(profile.getPermSize());
        txtGameDir.setText(profile.getGameDir());
        txtJavaArgs.setText(profile.getJavaArgs());
        txtMinecraftArgs.setText(profile.getMinecraftArgs());
        txtJavaDir.setText(profile.getJavaDir());
        txtWrapperLauncher.setText(profile.getWrapperLauncher());
        txtServerIP.setText(profile.getServerIp());
        chkDebug.setSelected(profile.isDebug());
        chkNoJVMArgs.setSelected(profile.isNoJVMArgs());
        chkFullscreen.setSelected(profile.isFullscreen());
        chkCancelWrapper.setSelected(profile.isCanceledWrapper());
        cboLauncherVisibility.setSelectedIndex(profile.getLauncherVisibility().ordinal());
        cboGameDirType.setSelectedIndex(profile.getGameDirType().ordinal());

        loadVersions();
        loadMinecraftVersion();
    }

    void loadVersions() {
        if (profile == null) return;
        isLoading = true;
        cboVersions.removeAllItems();
        int index = 0, i = 0;
        MinecraftVersion selVersion = profile.getSelectedMinecraftVersion();
        String selectedMC = selVersion == null ? null : selVersion.id;
        for (MinecraftVersion each : profile.getMinecraftProvider().getVersions()) {
            cboVersions.addItem(each.id);
            if (each.id.equals(selectedMC)) index = i;
            i++;
        }
        isLoading = false;
        if (index < cboVersions.getItemCount()) cboVersions.setSelectedIndex(index);
    }

    void loadMinecraftVersion() {
        loadMinecraftVersion(profile.getSelectedMinecraftVersion());
    }

    void loadMinecraftVersion(String v) {
        loadMinecraftVersion(profile.getMinecraftProvider().getVersionById(v));
    }

    /**
     * Anaylze the jar of selected minecraft version of current profile to get
     * the version.
     *
     * @param v
     */
    void loadMinecraftVersion(MinecraftVersion v) {
        txtMinecraftVersion.setText("");
        if (v == null) return;
        File minecraftJar = v.getJar(profile.getGameDirFile());
        minecraftVersion = MCUtils.minecraftVersion(minecraftJar);
        txtMinecraftVersion.setText(MinecraftVersionRequest.getResponse(minecraftVersion));
    }
    //</editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Assets">
    public int assetsType;

    private void downloadAssets(final IAssetsHandler type) {
        if (mcVersion == null || profile == null) return;
        type.getList((value) -> {
            if (value != null)
                TaskWindow.getInstance().addTask(type.getDownloadTask(Settings.getInstance().getDownloadSource().getProvider())).start();
        });
    }

    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Game Download">
    public void refreshDownloads(final DownloadType provider) {
        TaskWindow.getInstance().addTask(new Task() {
            HTTPGetTask tsk = new HTTPGetTask(provider.getProvider().getVersionsListDownloadURL());

            @Override
            public boolean executeTask() {
                final MinecraftRemoteVersions v = MinecraftRemoteVersions.fromJson(tsk.getResult());
                if (v == null || v.versions == null)
                    return true;
                SwingUtilities.invokeLater(() -> {
                    DefaultTableModel model = (DefaultTableModel) lstDownloads.getModel();
                    while (model.getRowCount() > 0) model.removeRow(0);
                    for (MinecraftRemoteVersion ver : v.versions) {
                        Object[] line = new Object[3];
                        line[0] = ver.id;
                        line[1] = ver.time;
                        if (StrUtils.equalsOne(ver.type, "old_beta", "old_alpha", "release", "snapshot"))
                            line[2] = C.i18n("versions." + ver.type);
                        else line[2] = ver.type;
                        model.addRow(line);
                    }
                    lstDownloads.updateUI();
                });
                return true;
            }

            @Override
            public String getInfo() {
                return "Format list.";
            }

            @Override
            public Collection<Task> getDependTasks() {
                return Arrays.asList((Task) tsk);
            }
        }).start();
    }

    void downloadMinecraft(DownloadType index) {
        if (profile == null) return;
        if (lstDownloads.getSelectedRow() < 0)
            refreshDownloads(Settings.getInstance().getDownloadSource());
        if (lstDownloads.getSelectedRow() < 0) {
            MessageBox.Show(C.i18n("gamedownload.not_refreshed"));
            return;
        }
        String id = (String) lstDownloads.getModel().getValueAt(lstDownloads.getSelectedRow(), 0);
        MCUtils.downloadMinecraft(profile.getGameDirFile(), id, index);
    }

    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Installer">
    private String getMinecraftVersionFormatted() {
        return minecraftVersion == null ? "" : (StrUtils.formatVersion(minecraftVersion.version) == null) ? mcVersion : minecraftVersion.version;
    }

    class InstallerHelper {

        List<InstallerVersionList.InstallerVersion> versions;
        InstallerVersionList list;
        JTable jt;
        String id;

        public InstallerHelper(JTable jt, String id) {
            this.jt = jt;
            this.id = id;
        }

        public void loadVersions() {
            versions = loadVersions(list, jt);
        }

        void refreshVersions() {
            list = Settings.getInstance().getDownloadSource().getProvider().getInstallerByType(id);
            if (TaskWindow.getInstance().addTask(new TaskRunnableArg1<>(C.i18n("install." + id + ".get_list"), list)
                    .registerPreviousResult(new DefaultPreviousResult<>(new String[]{getMinecraftVersionFormatted()})))
                    .start())
                loadVersions();
        }

        public InstallerVersion getVersion(int idx) {
            return versions.get(idx);
        }

        private List<InstallerVersionList.InstallerVersion> loadVersions(InstallerVersionList list, JTable table) {
            if (list == null)
                return null;
            DefaultTableModel model = (DefaultTableModel) table.getModel();
            while (model.getRowCount() > 0)
                model.removeRow(0);
            String mcver = StrUtils.formatVersion(getMinecraftVersionFormatted());
            List<InstallerVersionList.InstallerVersion> ver = list.getVersions(mcver);
            if (ver != null) {
                for (InstallerVersionList.InstallerVersion v : ver) {
                    Object a = v.selfVersion == null ? "null" : v.selfVersion;
                    Object b = v.mcVersion == null ? "null" : v.mcVersion;
                    Object[] row = new Object[]{a, b};
                    model.addRow(row);
                }
                table.updateUI();
            }
            return ver;
        }
    }

    private void refreshVersions() {
        getProfile().getMinecraftProvider().refreshVersions();
        loadVersions();
    }

    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Variables">
    boolean isLoading = false;
    Profile profile;
    public MinecraftVersionRequest minecraftVersion;
    InstallerHelper forge, optifine, liteloader;
    String mcVersion;

    // </editor-fold>
    // </editor-fold>
    public void versionChanged(Profile profile, String version) {
        this.mcVersion = version;
        forge.loadVersions();
        optifine.loadVersions();
        liteloader.loadVersions();

        MinecraftVersion v = profile.getMinecraftProvider().getVersionById(version);
        if (v != null)
            for (IAssetsHandler ph : IAssetsHandler.getAssetsHandlers())
                try {
                    ph.setAssets(profile.getMinecraftProvider(), v);
                } catch (Exception e) {
                    HMCLog.warn("Failed to load assets", e);
                }
    }

    public void onSelected() {
        loadProfiles();
        if (profile == null) return;
        if (profile.getMinecraftProvider().getVersionCount() <= 0)
            versionChanged(profile, null);
        else versionChanged(getProfile(), (String) cboVersions.getSelectedItem());
    }

    // <editor-fold defaultstate="collapsed" desc="UI Definations">
    JPopupMenu ppmManage, ppmExplore;
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton btnDownload;
    private javax.swing.JButton btnDownloadAllAssets;
    private javax.swing.JButton btnDownloadForge;
    private javax.swing.JButton btnDownloadOptifine;
    private javax.swing.JButton btnExplore;
    private javax.swing.JButton btnIncludeMinecraft;
    private javax.swing.JButton btnInstallLiteLoader;
    private javax.swing.JButton btnModify;
    private javax.swing.JButton btnNewProfile;
    private javax.swing.JButton btnRefreshForge;
    private javax.swing.JButton btnRefreshGameDownloads;
    private javax.swing.JButton btnRefreshLiteLoader;
    private javax.swing.JButton btnRefreshOptifine;
    private javax.swing.JButton btnRefreshVersions;
    private javax.swing.JButton btnRemoveProfile;
    private javax.swing.JButton btnRetryForge;
    private javax.swing.JButton btnRetryLiteLoader;
    private javax.swing.JComboBox cboGameDirType;
    private javax.swing.JComboBox cboLauncherVisibility;
    private javax.swing.JComboBox cboProfiles;
    private javax.swing.JComboBox cboVersions;
    private javax.swing.JCheckBox chkCancelWrapper;
    private javax.swing.JCheckBox chkDebug;
    private javax.swing.JCheckBox chkFullscreen;
    private javax.swing.JCheckBox chkNoJVMArgs;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel10;
    private javax.swing.JLabel jLabel11;
    private javax.swing.JLabel jLabel12;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel24;
    private javax.swing.JLabel jLabel25;
    private javax.swing.JLabel jLabel26;
    private javax.swing.JLabel jLabel27;
    private javax.swing.JLabel jLabel28;
    private javax.swing.JLabel jLabel29;
    private javax.swing.JLabel jLabel30;
    private javax.swing.JLabel jLabel31;
    private javax.swing.JLabel jLabel9;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel16;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel22;
    private javax.swing.JPanel jPanel3;
    private javax.swing.JPanel jPanel4;
    private javax.swing.JPanel jPanel5;
    private javax.swing.JScrollPane jScrollPane11;
    private javax.swing.JScrollPane jScrollPane12;
    private javax.swing.JScrollPane jScrollPane13;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JLabel lblMaxMemory;
    private javax.swing.JTable lstDownloads;
    private javax.swing.JTable lstForge;
    private javax.swing.JTable lstLiteLoader;
    private javax.swing.JTable lstOptifine;
    private javax.swing.JPanel pnlAutoInstall;
    private javax.swing.JPanel pnlGameDownloads;
    private javax.swing.JPanel pnlOptifine;
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
    private javax.swing.JTextField txtServerIP;
    private javax.swing.JTextField txtWidth;
    private javax.swing.JTextField txtWrapperLauncher;
    // End of variables declaration//GEN-END:variables
    // </editor-fold>
}
