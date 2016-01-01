/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraftlauncher.views;

import java.awt.Dimension;
import java.awt.Toolkit;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Vector;
import javax.swing.JFileChooser;
import javax.swing.JPanel;
import javax.swing.table.DefaultTableModel;
import org.jackhuang.hellominecraftlauncher.apis.utils.MessageBox;
import org.jackhuang.hellominecraftlauncher.apis.utils.MinecraftVersionRequest;
import org.jackhuang.hellominecraftlauncher.apis.utils.ModType;
import org.jackhuang.hellominecraftlauncher.apis.utils.Utils;
import org.jackhuang.hellominecraftlauncher.plugin.PluginHandler;
import org.jackhuang.hellominecraftlauncher.plugin.PluginManager;
import org.jackhuang.hellominecraftlauncher.settings.Version;
import org.jackhuang.hellominecraftlauncher.utilities.C;
import org.jackhuang.hellominecraftlauncher.utilities.FolderOpener;
import org.jackhuang.hellominecraftlauncher.utilities.SettingsManager;

/**
 *
 * @author hyh
 */
public class SetupWindow extends javax.swing.JDialog {

    // <editor-fold defaultstate="collapsed" desc="Constructor">
    public SetupWindow(java.awt.Frame parent, boolean modal) {
        super(parent, modal);
        initComponents();

        Dimension scrSize = Toolkit.getDefaultToolkit().getScreenSize();
        setLocation((scrSize.width - this.getWidth()) / 2,
                (scrSize.height - this.getHeight()) / 2);
        
        String[] titles = new String[]{C.I18N.getString("IsActive"), C.I18N.getString("Location"), C.I18N.getString("Type")};
        Class[] types = new Class[]{String.class, String.class, String.class};
        boolean[] canEdit = new boolean[]{false,false,false};
        lstCoreMods.setModel(Utils.makeDefaultTableModel(titles, types, canEdit));
        lstExternalMods.setModel(Utils.makeDefaultTableModel(titles, types, canEdit));
        lstFirstMod.setModel(Utils.makeDefaultTableModel(titles, types, canEdit));
        lstLastMod.setModel(Utils.makeDefaultTableModel(titles, types, canEdit));
        lstMinecraftJar.setModel(Utils.makeDefaultTableModel(titles, types, canEdit));
        
        for (JPanel panel : PluginHandler.editPanels) {
            tabVersionEdit.addTab(panel.getName(), panel);
        }
    }
    //</editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Load">
    void prepare(Version version) {
        if (version == null) {
            return;
        }
        this.version = version;
        txtName.setText(version.name);
        txtWidth.setText(version.width);
        txtHeight.setText(version.height);
        txtMaxMemory.setText(version.maxMemory);
        txtMaxPermGen.setText(version.maxPermGen);
        txtAssets.setText(version.gameAssets);
        txtGameDir.setText(version.gameDir);
        txtJavaArgs.setText(version.javaArgs);
        txtJavaDir.setText(version.javaDir);
        txtLibraries.setText(version.gameLibraries);
        txtMainClass.setText(version.mainClass);
        txtMinecraftArguments.setText(version.minecraftArguments);
        chkOver16.setSelected(version.isVer16);
        chkFullscreen.setSelected(version.fullscreen);

        if (version.lastLoadLibraries == null) {
            version.lastLoadLibraries = new ArrayList<String>();
        }
        if (version.firstLoadLibraries == null) {
            version.firstLoadLibraries = new ArrayList<String>();
        }

        loadLocalModsAndCoreMods();
        loadMinecraftVersion();
        //refreshForgeVersions();
        //refreshOptifineVersions();
        PluginManager.versionChanged();
    }

    void loadMinecraftVersion() {
        String minecraftJar = version.getMinecraftJar();
        minecraftVersion = Utils.minecraftVersion(new File(minecraftJar));
        String text = "";
        switch (minecraftVersion.type) {
            case MinecraftVersionRequest.Invaild:
                text = C.I18N.getString("invalid");
                break;
            case MinecraftVersionRequest.InvaildJar:
                text = C.I18N.getString("invalid_jar");
                break;
            case MinecraftVersionRequest.NotAFile:
                text = C.I18N.getString("not_a_file");
                break;
            case MinecraftVersionRequest.NotFound:
                text = C.I18N.getString("not_found");
                break;
            case MinecraftVersionRequest.NotReadable:
                text = C.I18N.getString("not_readable");
                break;
            case MinecraftVersionRequest.Modified:
                text = C.I18N.getString("modified") + " ";
            case MinecraftVersionRequest.OK:
                text += minecraftVersion.version;
                break;
            case MinecraftVersionRequest.Unkown:
            default:
                text = "???";
                break;
        }
        txtMinecraftVersion.setText(text);
    }

    void loadFirstMods() {
        DefaultTableModel model = (DefaultTableModel) lstFirstMod.getModel();
        for (int i = 0; i < version.firstLoadLibraries.size(); i++) {
            boolean e = true;
            if (version.firstLoadLibrariesIsActive != null) {
                e = version.firstLoadLibrariesIsActive.get(i);
            }
            model.addRow(new Object[]{
                e, version.firstLoadLibraries.get(i)
            });
        }
        lstFirstMod.updateUI();
    }

    void loadLastMods() {
        DefaultTableModel model = (DefaultTableModel) lstLastMod.getModel();
        for (int i = 0; i < version.lastLoadLibraries.size(); i++) {
            boolean e = true;
            if (version.lastLoadLibrariesIsActive != null) {
                e = version.lastLoadLibrariesIsActive.get(i);
            }
            model.addRow(new Object[]{
                e, version.lastLoadLibraries.get(i)
            });
        }
        lstLastMod.updateUI();
    }

    void loadMinecraftJar() {
        DefaultTableModel model = (DefaultTableModel) lstMinecraftJar.getModel();
        for (int i = 0; i < version.minecraftJar.size(); i++) {
            boolean e = true;
            if (version.minecraftJarIsActive != null) {
                e = version.minecraftJarIsActive.get(i);
            }
            model.addRow(new Object[]{
                e, version.minecraftJar.get(i)
            });
        }
        lstMinecraftJar.updateUI();
    }

    void loadLocalModsAndCoreMods() {
        loadFirstMods();
        loadLastMods();
        loadLocalMods();
        loadLocalCoreMods();
        loadMinecraftJar();
    }


    void loadLocalMods() {
        String path = getPath("mods");
        if (path == null) {
            return;
        }
        ArrayList<String> sl = Utils.findAllFile(new File(path));
        DefaultTableModel model = (DefaultTableModel) lstExternalMods.getModel();
        while (model.getRowCount() > 0) {
            model.removeRow(0);
        }
        for (String s : sl) {
            model.addRow(new Object[]{!version.inactiveExtMods.contains(s), s, ModType.getModTypeShowName(ModType.getModType(Utils.addSeparator(path) + s))});
        }

        lstExternalMods.updateUI();
    }

    void loadLocalCoreMods() {
        String path = getPath("coremods");
        if (path == null) {
            return;
        }
        ArrayList<String> sl = Utils.findAllFile(new File(path));
        DefaultTableModel model = (DefaultTableModel) lstCoreMods.getModel();
        while (model.getRowCount() > 0) {
            model.removeRow(0);
        }
        for (String s : sl) {
            model.addRow(new Object[]{!version.inactiveExtMods.contains(s), s, ModType.getModTypeShowName(ModType.getModType(Utils.addSeparator(path) + s))});
        }


        lstCoreMods.updateUI();
    }
    //</editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Utilities">
    String getGameDir() {
        return Utils.getGameDir(version, SettingsManager.settings.publicSettings.gameDir);
    }

    String getPath(String lastFolder) {
        return Utils.getPath(version, lastFolder, SettingsManager.settings.publicSettings.gameDir);
    }

    String try2GetPath(String lastFolder) {
        return Utils.try2GetPath(version, lastFolder, SettingsManager.settings.publicSettings.gameDir);
    }
    // </editor-fold>

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jSplitPane1 = new javax.swing.JSplitPane();
        jPanel1 = new javax.swing.JPanel();
        btnSave = new javax.swing.JButton();
        jSplitPane2 = new javax.swing.JSplitPane();
        jPanel2 = new javax.swing.JPanel();
        txtName = new javax.swing.JTextField();
        jLabel1 = new javax.swing.JLabel();
        jPanel3 = new javax.swing.JPanel();
        jLabel2 = new javax.swing.JLabel();
        txtMinecraftVersion = new javax.swing.JTextField();
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
        jLabel26 = new javax.swing.JLabel();
        txtJavaArgs = new javax.swing.JTextField();
        jLabel27 = new javax.swing.JLabel();
        txtMaxMemory = new javax.swing.JTextField();
        chkOver16 = new javax.swing.JCheckBox();
        jLabel31 = new javax.swing.JLabel();
        txtMaxPermGen = new javax.swing.JTextField();
        jPanel23 = new javax.swing.JPanel();
        btnSetAssets = new javax.swing.JButton();
        btnSetLibraries = new javax.swing.JButton();
        jLabel28 = new javax.swing.JLabel();
        txtAssets = new javax.swing.JTextField();
        txtLibraries = new javax.swing.JTextField();
        jLabel29 = new javax.swing.JLabel();
        jLabel30 = new javax.swing.JLabel();
        txtMainClass = new javax.swing.JTextField();
        txtMinecraftArguments = new javax.swing.JTextField();
        jLabel19 = new javax.swing.JLabel();
        jPanel24 = new javax.swing.JPanel();
        jTabbedPane2 = new javax.swing.JTabbedPane();
        jPanel25 = new javax.swing.JPanel();
        btnAddFirstMod = new javax.swing.JButton();
        btnDeleteFirstMod = new javax.swing.JButton();
        btnUpFirstMod = new javax.swing.JButton();
        btnDownFirstMod = new javax.swing.JButton();
        jScrollPane10 = new javax.swing.JScrollPane();
        lstFirstMod = new javax.swing.JTable();
        jPanel5 = new javax.swing.JPanel();
        btnAddLastMod = new javax.swing.JButton();
        btnDeleteLastMod = new javax.swing.JButton();
        btnUpLastMod = new javax.swing.JButton();
        btnDownLastMod = new javax.swing.JButton();
        jScrollPane19 = new javax.swing.JScrollPane();
        lstLastMod = new javax.swing.JTable();
        jPanel6 = new javax.swing.JPanel();
        btnAddExternelMod = new javax.swing.JButton();
        btnDeleteExternelMod = new javax.swing.JButton();
        jScrollPane20 = new javax.swing.JScrollPane();
        lstExternalMods = new javax.swing.JTable();
        btnManageExtMods = new javax.swing.JButton();
        jPanel7 = new javax.swing.JPanel();
        btnAddExternelCoreMod = new javax.swing.JButton();
        btnDeleteExternelCoreMod = new javax.swing.JButton();
        jScrollPane21 = new javax.swing.JScrollPane();
        lstCoreMods = new javax.swing.JTable();
        btnManageCoreMods = new javax.swing.JButton();
        jPanel14 = new javax.swing.JPanel();
        btnAddMinecraftJar = new javax.swing.JButton();
        btnDeleteMinecraftJar = new javax.swing.JButton();
        btnUpMinecraftJar = new javax.swing.JButton();
        btnDownMinecraftJar = new javax.swing.JButton();
        jScrollPane22 = new javax.swing.JScrollPane();
        lstMinecraftJar = new javax.swing.JTable();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("org/jackhuang/hellominecraftlauncher/I18N"); // NOI18N
        setTitle(bundle.getString("Setup")); // NOI18N

        jSplitPane1.setOrientation(javax.swing.JSplitPane.VERTICAL_SPLIT);

        btnSave.setText(bundle.getString("Save")); // NOI18N
        btnSave.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnSaveActionPerformed(evt);
            }
        });

        jLabel1.setText(bundle.getString("Name")); // NOI18N

        javax.swing.GroupLayout jPanel2Layout = new javax.swing.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel1)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(txtName, javax.swing.GroupLayout.DEFAULT_SIZE, 208, Short.MAX_VALUE))
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel1)
                    .addComponent(txtName, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(0, 0, Short.MAX_VALUE))
        );

        jSplitPane2.setLeftComponent(jPanel2);

        jLabel2.setText(bundle.getString("Version")); // NOI18N

        javax.swing.GroupLayout jPanel3Layout = new javax.swing.GroupLayout(jPanel3);
        jPanel3.setLayout(jPanel3Layout);
        jPanel3Layout.setHorizontalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addGap(6, 6, 6)
                .addComponent(jLabel2)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(txtMinecraftVersion, javax.swing.GroupLayout.DEFAULT_SIZE, 180, Short.MAX_VALUE))
        );
        jPanel3Layout.setVerticalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(txtMinecraftVersion, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel2))
                .addGap(0, 0, Short.MAX_VALUE))
        );

        jSplitPane2.setRightComponent(jPanel3);

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel1Layout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addComponent(jSplitPane2, javax.swing.GroupLayout.PREFERRED_SIZE, 467, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(btnSave)
                .addContainerGap())
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(btnSave, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jSplitPane2))
                .addGap(0, 0, 0))
        );

        jSplitPane1.setTopComponent(jPanel1);

        tabVersionEdit.setName("tabVersionEdit"); // NOI18N

        jLabel24.setText(bundle.getString("游戏路径")); // NOI18N

        jLabel25.setText(bundle.getString("分辨率")); // NOI18N

        txtWidth.setToolTipText(bundle.getString("MCWidth")); // NOI18N

        txtHeight.setToolTipText(bundle.getString("MCHeight")); // NOI18N

        jLabel9.setText("x");

        chkFullscreen.setText(bundle.getString("全屏")); // NOI18N
        chkFullscreen.setToolTipText(bundle.getString("MakeMCFullscreen")); // NOI18N

        jLabel11.setText(bundle.getString("JAVA路径")); // NOI18N

        jLabel26.setText(bundle.getString("JAVA虚拟机参数")); // NOI18N

        jLabel27.setText(bundle.getString("最大内存")); // NOI18N

        chkOver16.setText(bundle.getString("1.6及以上版本以及导入的旧版本")); // NOI18N
        chkOver16.setToolTipText(bundle.getString("MCFolderFormat")); // NOI18N

        jLabel31.setText(bundle.getString("MaxPermGen")); // NOI18N

        javax.swing.GroupLayout jPanel22Layout = new javax.swing.GroupLayout(jPanel22);
        jPanel22.setLayout(jPanel22Layout);
        jPanel22Layout.setHorizontalGroup(
            jPanel22Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel22Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel22Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(txtGameDir)
                    .addComponent(txtJavaDir)
                    .addComponent(txtMaxMemory)
                    .addComponent(txtJavaArgs)
                    .addGroup(jPanel22Layout.createSequentialGroup()
                        .addComponent(jLabel24)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 258, Short.MAX_VALUE)
                        .addComponent(chkOver16))
                    .addGroup(jPanel22Layout.createSequentialGroup()
                        .addComponent(txtWidth, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(3, 3, 3)
                        .addComponent(jLabel9)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(txtHeight, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 243, Short.MAX_VALUE)
                        .addComponent(chkFullscreen))
                    .addComponent(txtMaxPermGen)
                    .addGroup(jPanel22Layout.createSequentialGroup()
                        .addGroup(jPanel22Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel11)
                            .addComponent(jLabel26)
                            .addComponent(jLabel27)
                            .addComponent(jLabel25)
                            .addComponent(jLabel31))
                        .addGap(0, 0, Short.MAX_VALUE)))
                .addContainerGap())
        );
        jPanel22Layout.setVerticalGroup(
            jPanel22Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel22Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel22Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel24)
                    .addComponent(chkOver16))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(txtGameDir, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jLabel11)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(txtJavaDir, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jLabel26)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(txtJavaArgs, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jLabel27)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(txtMaxMemory, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jLabel31)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(txtMaxPermGen, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(28, 28, 28)
                .addComponent(jLabel25)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel22Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(txtWidth, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(txtHeight, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel9)
                    .addComponent(chkFullscreen))
                .addContainerGap(236, Short.MAX_VALUE))
        );

        tabVersionEdit.addTab(bundle.getString("NormalSettings"), jPanel22); // NOI18N

        btnSetAssets.setText(bundle.getString("手动设置")); // NOI18N

        btnSetLibraries.setText(bundle.getString("手动设置")); // NOI18N

        jLabel28.setText(bundle.getString("自定义资源文件夹(ASSETS文件夹)")); // NOI18N

        txtAssets.setToolTipText(bundle.getString("DefaultAssetsFolder")); // NOI18N

        txtLibraries.setToolTipText(bundle.getString("DefaultLibrariesFolder")); // NOI18N

        jLabel29.setText(bundle.getString("主类")); // NOI18N

        jLabel30.setText(bundle.getString("自定义库文件夹(LIBRARIES文件夹)")); // NOI18N

        txtMainClass.setToolTipText(bundle.getString("MainClass")); // NOI18N

        txtMinecraftArguments.setToolTipText(bundle.getString("TagArguments")); // NOI18N

        jLabel19.setText(bundle.getString("附加启动参数")); // NOI18N

        javax.swing.GroupLayout jPanel23Layout = new javax.swing.GroupLayout(jPanel23);
        jPanel23.setLayout(jPanel23Layout);
        jPanel23Layout.setHorizontalGroup(
            jPanel23Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel23Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel23Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(txtLibraries)
                    .addGroup(jPanel23Layout.createSequentialGroup()
                        .addComponent(jLabel28, javax.swing.GroupLayout.PREFERRED_SIZE, 186, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 238, Short.MAX_VALUE)
                        .addComponent(btnSetAssets))
                    .addGroup(jPanel23Layout.createSequentialGroup()
                        .addComponent(jLabel30)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 238, Short.MAX_VALUE)
                        .addComponent(btnSetLibraries))
                    .addComponent(txtAssets)
                    .addComponent(txtMainClass)
                    .addComponent(txtMinecraftArguments)
                    .addGroup(jPanel23Layout.createSequentialGroup()
                        .addGroup(jPanel23Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel29)
                            .addComponent(jLabel19))
                        .addGap(0, 0, Short.MAX_VALUE)))
                .addContainerGap())
        );
        jPanel23Layout.setVerticalGroup(
            jPanel23Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel23Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel23Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel28)
                    .addComponent(btnSetAssets))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(txtAssets, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(8, 8, 8)
                .addGroup(jPanel23Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel30)
                    .addComponent(btnSetLibraries))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(txtLibraries, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jLabel29)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(txtMainClass, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jLabel19)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(txtMinecraftArguments, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(333, Short.MAX_VALUE))
        );

        tabVersionEdit.addTab(bundle.getString("AdvancedSettings"), jPanel23); // NOI18N

        btnAddFirstMod.setText(bundle.getString("增加")); // NOI18N
        btnAddFirstMod.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnAddFirstModActionPerformed(evt);
            }
        });

        btnDeleteFirstMod.setText(bundle.getString("删除")); // NOI18N
        btnDeleteFirstMod.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnDeleteFirstModActionPerformed(evt);
            }
        });

        btnUpFirstMod.setText(bundle.getString("上移")); // NOI18N
        btnUpFirstMod.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnUpFirstModActionPerformed(evt);
            }
        });

        btnDownFirstMod.setText(bundle.getString("下移")); // NOI18N
        btnDownFirstMod.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnDownFirstModActionPerformed(evt);
            }
        });

        lstFirstMod.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {
                "活动", "路径", "类型"
            }
        ) {
            Class[] types = new Class [] {
                java.lang.Boolean.class, java.lang.String.class, java.lang.String.class
            };
            boolean[] canEdit = new boolean [] {
                false, false, false
            };

            public Class getColumnClass(int columnIndex) {
                return types [columnIndex];
            }

            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return canEdit [columnIndex];
            }
        });
        jScrollPane10.setViewportView(lstFirstMod);

        javax.swing.GroupLayout jPanel25Layout = new javax.swing.GroupLayout(jPanel25);
        jPanel25.setLayout(jPanel25Layout);
        jPanel25Layout.setHorizontalGroup(
            jPanel25Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel25Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jScrollPane10, javax.swing.GroupLayout.DEFAULT_SIZE, 417, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel25Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(btnUpFirstMod, javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(btnDownFirstMod, javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(btnAddFirstMod, javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(btnDeleteFirstMod, javax.swing.GroupLayout.Alignment.TRAILING))
                .addContainerGap())
        );
        jPanel25Layout.setVerticalGroup(
            jPanel25Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel25Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel25Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel25Layout.createSequentialGroup()
                        .addComponent(jScrollPane10, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
                        .addContainerGap())
                    .addGroup(jPanel25Layout.createSequentialGroup()
                        .addComponent(btnAddFirstMod)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnDeleteFirstMod)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnUpFirstMod)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnDownFirstMod)
                        .addGap(0, 382, Short.MAX_VALUE))))
        );

        jTabbedPane2.addTab(bundle.getString("Libraries"), jPanel25); // NOI18N

        btnAddLastMod.setText(bundle.getString("增加")); // NOI18N
        btnAddLastMod.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnAddLastModActionPerformed(evt);
            }
        });

        btnDeleteLastMod.setText(bundle.getString("删除")); // NOI18N
        btnDeleteLastMod.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnDeleteLastModActionPerformed(evt);
            }
        });

        btnUpLastMod.setText(bundle.getString("上移")); // NOI18N
        btnUpLastMod.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnUpLastModActionPerformed(evt);
            }
        });

        btnDownLastMod.setText(bundle.getString("下移")); // NOI18N
        btnDownLastMod.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnDownLastModActionPerformed(evt);
            }
        });

        lstLastMod.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {
                "活动", "路径", "类型"
            }
        ) {
            Class[] types = new Class [] {
                java.lang.Boolean.class, java.lang.String.class, java.lang.String.class
            };

            public Class getColumnClass(int columnIndex) {
                return types [columnIndex];
            }
        });
        lstLastMod.setIntercellSpacing(new java.awt.Dimension(0, 0));
        jScrollPane19.setViewportView(lstLastMod);

        javax.swing.GroupLayout jPanel5Layout = new javax.swing.GroupLayout(jPanel5);
        jPanel5.setLayout(jPanel5Layout);
        jPanel5Layout.setHorizontalGroup(
            jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel5Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jScrollPane19, javax.swing.GroupLayout.DEFAULT_SIZE, 417, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addComponent(btnDeleteLastMod, javax.swing.GroupLayout.Alignment.TRAILING)
                        .addComponent(btnUpLastMod, javax.swing.GroupLayout.Alignment.TRAILING)
                        .addComponent(btnDownLastMod, javax.swing.GroupLayout.Alignment.TRAILING))
                    .addComponent(btnAddLastMod, javax.swing.GroupLayout.Alignment.TRAILING))
                .addContainerGap())
        );
        jPanel5Layout.setVerticalGroup(
            jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel5Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane19, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
                    .addGroup(jPanel5Layout.createSequentialGroup()
                        .addComponent(btnAddLastMod)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnDeleteLastMod)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnUpLastMod, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnDownLastMod)
                        .addGap(0, 372, Short.MAX_VALUE)))
                .addContainerGap())
        );

        jTabbedPane2.addTab(bundle.getString("DynamicLoadingMods"), jPanel5); // NOI18N

        btnAddExternelMod.setText(bundle.getString("增加")); // NOI18N
        btnAddExternelMod.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnAddExternelModActionPerformed(evt);
            }
        });

        btnDeleteExternelMod.setText(bundle.getString("删除")); // NOI18N
        btnDeleteExternelMod.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnDeleteExternelModActionPerformed(evt);
            }
        });

        lstExternalMods.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {
                "活动", "路径", "类型"
            }
        ) {
            Class[] types = new Class [] {
                java.lang.Boolean.class, java.lang.String.class, java.lang.String.class
            };
            boolean[] canEdit = new boolean [] {
                false, false, false
            };

            public Class getColumnClass(int columnIndex) {
                return types [columnIndex];
            }

            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return canEdit [columnIndex];
            }
        });
        jScrollPane20.setViewportView(lstExternalMods);

        btnManageExtMods.setText(bundle.getString("管理")); // NOI18N
        btnManageExtMods.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnManageExtModsActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel6Layout = new javax.swing.GroupLayout(jPanel6);
        jPanel6.setLayout(jPanel6Layout);
        jPanel6Layout.setHorizontalGroup(
            jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel6Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jScrollPane20, javax.swing.GroupLayout.DEFAULT_SIZE, 417, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addComponent(btnAddExternelMod, javax.swing.GroupLayout.Alignment.TRAILING)
                        .addComponent(btnDeleteExternelMod, javax.swing.GroupLayout.Alignment.TRAILING))
                    .addComponent(btnManageExtMods))
                .addContainerGap())
        );
        jPanel6Layout.setVerticalGroup(
            jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel6Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel6Layout.createSequentialGroup()
                        .addComponent(jScrollPane20, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
                        .addContainerGap())
                    .addGroup(jPanel6Layout.createSequentialGroup()
                        .addComponent(btnManageExtMods)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnAddExternelMod)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnDeleteExternelMod)
                        .addGap(0, 411, Short.MAX_VALUE))))
        );

        jTabbedPane2.addTab(bundle.getString("ExternalMods"), jPanel6); // NOI18N

        btnAddExternelCoreMod.setText(bundle.getString("增加")); // NOI18N
        btnAddExternelCoreMod.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnAddExternelCoreModActionPerformed(evt);
            }
        });

        btnDeleteExternelCoreMod.setText(bundle.getString("删除")); // NOI18N
        btnDeleteExternelCoreMod.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnDeleteExternelCoreModActionPerformed(evt);
            }
        });

        lstCoreMods.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {
                "活动", "路径", "类型"
            }
        ) {
            Class[] types = new Class [] {
                java.lang.Boolean.class, java.lang.String.class, java.lang.String.class
            };
            boolean[] canEdit = new boolean [] {
                false, false, false
            };

            public Class getColumnClass(int columnIndex) {
                return types [columnIndex];
            }

            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return canEdit [columnIndex];
            }
        });
        jScrollPane21.setViewportView(lstCoreMods);

        btnManageCoreMods.setText(bundle.getString("管理")); // NOI18N
        btnManageCoreMods.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnManageCoreModsActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel7Layout = new javax.swing.GroupLayout(jPanel7);
        jPanel7.setLayout(jPanel7Layout);
        jPanel7Layout.setHorizontalGroup(
            jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel7Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jScrollPane21, javax.swing.GroupLayout.DEFAULT_SIZE, 417, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addComponent(btnAddExternelCoreMod, javax.swing.GroupLayout.Alignment.TRAILING)
                        .addComponent(btnDeleteExternelCoreMod, javax.swing.GroupLayout.Alignment.TRAILING))
                    .addComponent(btnManageCoreMods))
                .addContainerGap())
        );
        jPanel7Layout.setVerticalGroup(
            jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel7Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel7Layout.createSequentialGroup()
                        .addComponent(btnManageCoreMods)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnAddExternelCoreMod)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnDeleteExternelCoreMod)
                        .addGap(0, 401, Short.MAX_VALUE))
                    .addComponent(jScrollPane21, javax.swing.GroupLayout.DEFAULT_SIZE, 482, Short.MAX_VALUE))
                .addContainerGap())
        );

        jTabbedPane2.addTab(bundle.getString("CoreMods"), jPanel7); // NOI18N

        btnAddMinecraftJar.setText(bundle.getString("增加")); // NOI18N
        btnAddMinecraftJar.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnAddMinecraftJarActionPerformed(evt);
            }
        });

        btnDeleteMinecraftJar.setText(bundle.getString("删除")); // NOI18N
        btnDeleteMinecraftJar.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnDeleteMinecraftJarActionPerformed(evt);
            }
        });

        btnUpMinecraftJar.setText(bundle.getString("上移")); // NOI18N
        btnUpMinecraftJar.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnUpMinecraftJarActionPerformed(evt);
            }
        });

        btnDownMinecraftJar.setText(bundle.getString("下移")); // NOI18N
        btnDownMinecraftJar.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnDownMinecraftJarActionPerformed(evt);
            }
        });

        lstMinecraftJar.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {
                "活动", "路径", "类型"
            }
        ) {
            Class[] types = new Class [] {
                java.lang.Boolean.class, java.lang.String.class, java.lang.String.class
            };
            boolean[] canEdit = new boolean [] {
                false, false, false
            };

            public Class getColumnClass(int columnIndex) {
                return types [columnIndex];
            }

            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return canEdit [columnIndex];
            }
        });
        jScrollPane22.setViewportView(lstMinecraftJar);

        javax.swing.GroupLayout jPanel14Layout = new javax.swing.GroupLayout(jPanel14);
        jPanel14.setLayout(jPanel14Layout);
        jPanel14Layout.setHorizontalGroup(
            jPanel14Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel14Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jScrollPane22, javax.swing.GroupLayout.DEFAULT_SIZE, 417, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel14Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(btnAddMinecraftJar, javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(btnDeleteMinecraftJar, javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(btnUpMinecraftJar, javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(btnDownMinecraftJar, javax.swing.GroupLayout.Alignment.TRAILING))
                .addContainerGap())
        );
        jPanel14Layout.setVerticalGroup(
            jPanel14Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel14Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel14Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane22, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
                    .addGroup(jPanel14Layout.createSequentialGroup()
                        .addComponent(btnAddMinecraftJar)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnDeleteMinecraftJar)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnUpMinecraftJar, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnDownMinecraftJar)
                        .addGap(0, 372, Short.MAX_VALUE)))
                .addContainerGap())
        );

        jTabbedPane2.addTab(bundle.getString("JoinMinecraftJar"), jPanel14); // NOI18N

        javax.swing.GroupLayout jPanel24Layout = new javax.swing.GroupLayout(jPanel24);
        jPanel24.setLayout(jPanel24Layout);
        jPanel24Layout.setHorizontalGroup(
            jPanel24Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel24Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jTabbedPane2)
                .addContainerGap())
        );
        jPanel24Layout.setVerticalGroup(
            jPanel24Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel24Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jTabbedPane2)
                .addContainerGap())
        );

        tabVersionEdit.addTab(bundle.getString("ModManaging"), jPanel24); // NOI18N

        jSplitPane1.setBottomComponent(tabVersionEdit);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jSplitPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 532, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jSplitPane1)
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    // <editor-fold defaultstate="collapsed" desc="Events">
    private void btnAddFirstModActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnAddFirstModActionPerformed
        JFileChooser fc = new JFileChooser();
        fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fc.setDialogTitle(C.I18N.getString("选择库/模组"));
        fc.setMultiSelectionEnabled(false);
        fc.showOpenDialog(this);
        try {
            DefaultTableModel model = (DefaultTableModel) lstFirstMod.getModel();
            String path = fc.getSelectedFile().getCanonicalPath();
            model.addRow(new Object[]{true, path});
            lstFirstMod.updateUI();
        } catch (IOException e) {
            MessageBox.Show(C.I18N.getString("添加失败"));
            e.printStackTrace();
        }
    }//GEN-LAST:event_btnAddFirstModActionPerformed

    private void btnDeleteFirstModActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnDeleteFirstModActionPerformed
        try {
            DefaultTableModel model = (DefaultTableModel) lstFirstMod.getModel();
            model.removeRow(lstFirstMod.getSelectedRow());
        } catch (Exception e) {
            MessageBox.Show(C.I18N.getString("没有选中任何一个库/模组。"));
            e.printStackTrace();
        }
    }//GEN-LAST:event_btnDeleteFirstModActionPerformed

    private void btnUpFirstModActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnUpFirstModActionPerformed
        int index = lstFirstMod.getSelectedRow();
        if (index <= 0) {
            return;
        }
        lstFirstMod.moveColumn(index, index - 1);
    }//GEN-LAST:event_btnUpFirstModActionPerformed

    private void btnDownFirstModActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnDownFirstModActionPerformed
        int index = lstFirstMod.getSelectedRow();
        if (index <= 0) {
            return;
        }
        lstFirstMod.moveColumn(index, index + 1);
    }//GEN-LAST:event_btnDownFirstModActionPerformed

    private void btnAddLastModActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnAddLastModActionPerformed
        JFileChooser fc = new JFileChooser();
        fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fc.setDialogTitle(C.I18N.getString("选择库/模组"));
        fc.setMultiSelectionEnabled(false);
        fc.showOpenDialog(this);
        try {
            DefaultTableModel model = (DefaultTableModel) lstLastMod.getModel();
            String path = fc.getSelectedFile().getCanonicalPath();
            model.addRow(new Object[]{true, path});
            lstLastMod.updateUI();
        } catch (Exception e) {
            MessageBox.Show(C.I18N.getString("添加失败"));
            e.printStackTrace();
        }
    }//GEN-LAST:event_btnAddLastModActionPerformed

    private void btnDeleteLastModActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnDeleteLastModActionPerformed
        try {
            DefaultTableModel model = (DefaultTableModel) lstLastMod.getModel();
            model.removeRow(lstLastMod.getSelectedRow());
            lstLastMod.updateUI();
        } catch (Exception e) {
            MessageBox.Show(C.I18N.getString("没有选中任何一个库/模组。"));
            e.printStackTrace();
        }
    }//GEN-LAST:event_btnDeleteLastModActionPerformed

    private void btnUpLastModActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnUpLastModActionPerformed
        int index = lstLastMod.getSelectedRow();
        if (index <= 0) {
            return;
        }
        lstLastMod.moveColumn(index, index - 1);
    }//GEN-LAST:event_btnUpLastModActionPerformed

    private void btnDownLastModActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnDownLastModActionPerformed
        int index = lstLastMod.getSelectedRow();
        if (index <= 0) {
            return;
        }
        lstLastMod.moveColumn(index, index + 1);
    }//GEN-LAST:event_btnDownLastModActionPerformed

    private void btnAddExternelModActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnAddExternelModActionPerformed
        JFileChooser fc = new JFileChooser();
        fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fc.setDialogTitle(C.I18N.getString("选择模组"));
        fc.setMultiSelectionEnabled(false);
        fc.showOpenDialog(this);
        try {
            String path = fc.getSelectedFile().getCanonicalPath();
            String path2 = try2GetPath("mods");
            File newf = new File(path2);
            newf.mkdirs();
            newf = new File(path2 + File.separator + fc.getSelectedFile().getName());
            Utils.copyFile(new File(path), newf);

            DefaultTableModel model = (DefaultTableModel) lstExternalMods.getModel();
            model.addRow(new Object[]{fc.getSelectedFile().getName(), ModType.getModTypeShowName(ModType.getModType(newf))});
            lstExternalMods.updateUI();
        } catch (Exception e) {
            MessageBox.Show(C.I18N.getString("添加失败"));
            e.printStackTrace();
        }
    }//GEN-LAST:event_btnAddExternelModActionPerformed

    private void btnDeleteExternelModActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnDeleteExternelModActionPerformed
        DefaultTableModel model = (DefaultTableModel) lstExternalMods.getModel();
        int idx = lstExternalMods.getSelectedRow();
        String selectedName = (String) model.getValueAt(idx, 0);
        model.removeRow(idx);
        String path = getPath("mods");
        if (path == null) {
            MessageBox.Show(C.I18N.getString("删除失败"));
            return;
        }
        File newf = new File(path + File.separator + selectedName);
        newf.delete();
    }//GEN-LAST:event_btnDeleteExternelModActionPerformed

    private void btnManageExtModsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnManageExtModsActionPerformed
        FolderOpener.open(try2GetPath("mods"));
    }//GEN-LAST:event_btnManageExtModsActionPerformed

    private void btnAddExternelCoreModActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnAddExternelCoreModActionPerformed
        JFileChooser fc = new JFileChooser();
        fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fc.setDialogTitle(C.I18N.getString("选择模组"));
        fc.setMultiSelectionEnabled(false);
        fc.showOpenDialog(this);
        try {
            String path = fc.getSelectedFile().getCanonicalPath();
            String path2 = try2GetPath("coremods");
            File newf = new File(path2);
            newf.mkdirs();
            newf = new File(path2 + File.separator + fc.getSelectedFile().getName());

            DefaultTableModel model = (DefaultTableModel) lstCoreMods.getModel();
            lstCoreMods.updateUI();
            model.addRow(new Object[]{fc.getSelectedFile().getName(), ModType.getModTypeShowName(ModType.getModType(newf))});
            Utils.copyFile(new File(path), newf);
        } catch (Exception e) {
            MessageBox.Show(C.I18N.getString("添加失败"));
            e.printStackTrace();
        }
    }//GEN-LAST:event_btnAddExternelCoreModActionPerformed

    private void btnDeleteExternelCoreModActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnDeleteExternelCoreModActionPerformed
        DefaultTableModel model = (DefaultTableModel) lstCoreMods.getModel();
        int idx = lstCoreMods.getSelectedRow();
        String selectedName = (String) model.getValueAt(idx, 0);
        model.removeRow(idx);
        lstCoreMods.updateUI();
        String path = getPath("coremods");
        if (path == null) {
            MessageBox.Show(C.I18N.getString("删除失败"));
            return;
        }
        File newf = new File(path + File.separator + selectedName);
        newf.delete();
    }//GEN-LAST:event_btnDeleteExternelCoreModActionPerformed

    private void btnManageCoreModsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnManageCoreModsActionPerformed
        FolderOpener.open(try2GetPath("coremods"));
    }//GEN-LAST:event_btnManageCoreModsActionPerformed

    private void btnAddMinecraftJarActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnAddMinecraftJarActionPerformed
        JFileChooser fc = new JFileChooser();
        fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fc.setDialogTitle(C.I18N.getString("选择库/模组"));
        fc.setMultiSelectionEnabled(false);
        fc.showOpenDialog(this);
        try {
            DefaultTableModel model = (DefaultTableModel) lstMinecraftJar.getModel();
            String path = fc.getSelectedFile().getCanonicalPath();
            model.addRow(new Object[]{true, path});
            lstMinecraftJar.updateUI();
        } catch (Exception e) {
            MessageBox.Show(C.I18N.getString("添加失败"));
            e.printStackTrace();
        }
    }//GEN-LAST:event_btnAddMinecraftJarActionPerformed

    private void btnDeleteMinecraftJarActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnDeleteMinecraftJarActionPerformed
        try {
            DefaultTableModel model = (DefaultTableModel) lstMinecraftJar.getModel();
            model.removeRow(lstMinecraftJar.getSelectedRow());
            lstMinecraftJar.updateUI();
        } catch (Exception e) {
            MessageBox.Show(C.I18N.getString("没有选中任何一个库/模组。"));
            e.printStackTrace();
        }
    }//GEN-LAST:event_btnDeleteMinecraftJarActionPerformed

    private void btnUpMinecraftJarActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnUpMinecraftJarActionPerformed
        int index = lstMinecraftJar.getSelectedRow();
        if (index <= 0) {
            return;
        }
        lstMinecraftJar.moveColumn(index, index - 1);
    }//GEN-LAST:event_btnUpMinecraftJarActionPerformed

    private void btnDownMinecraftJarActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnDownMinecraftJarActionPerformed
        int index = lstMinecraftJar.getSelectedRow();
        if (index <= 0) {
            return;
        }
        lstMinecraftJar.moveColumn(index, index + 1);
    }//GEN-LAST:event_btnDownMinecraftJarActionPerformed

    private void btnSaveActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnSaveActionPerformed
        save();
    }//GEN-LAST:event_btnSaveActionPerformed
    // </editor-fold>
    
    private void save() {
        version.name = txtName.getText();
        version.fullscreen = chkFullscreen.isSelected();
        version.gameAssets = txtAssets.getText();
        version.gameDir = txtGameDir.getText();
        version.gameLibraries = txtLibraries.getText();
        version.height = txtHeight.getText();
        version.isVer16 = chkOver16.isSelected();
        version.javaArgs = txtJavaArgs.getText();
        version.javaDir = txtJavaDir.getText();
        version.mainClass = txtMainClass.getText();
        version.maxMemory = txtMaxMemory.getText();
        version.maxPermGen = txtMaxPermGen.getText();
        version.minecraftArguments = txtMinecraftArguments.getText();
        version.width = txtWidth.getText();
        Vector strings;
        ArrayList<String> arrayList;
        ArrayList<Boolean> arrayListIsActive;
        strings = ((DefaultTableModel) lstFirstMod.getModel()).getDataVector();
        arrayList = new ArrayList<String>();
        arrayListIsActive = new ArrayList<Boolean>();
        for (Object s : strings) {
            Vector v = (Vector) s;
            arrayListIsActive.add((Boolean) v.elementAt(0));
            arrayList.add((String) v.elementAt(1));
        }
        version.firstLoadLibraries = arrayList;
        version.firstLoadLibrariesIsActive = arrayListIsActive;
        arrayList = new ArrayList<String>();
        arrayListIsActive = new ArrayList<Boolean>();
        strings = ((DefaultTableModel) lstLastMod.getModel()).getDataVector();
        for (Object s : strings) {
            Vector v = (Vector) s;
            arrayListIsActive.add((Boolean) v.elementAt(0));
            arrayList.add((String) v.elementAt(1));
        }
        version.lastLoadLibraries = arrayList;
        version.lastLoadLibrariesIsActive = arrayListIsActive;
        arrayList = new ArrayList<String>();
        arrayListIsActive = new ArrayList<Boolean>();
        strings = ((DefaultTableModel) lstMinecraftJar.getModel()).getDataVector();
        for (Object s : strings) {
            Vector v = (Vector) s;
            arrayListIsActive.add((Boolean) v.elementAt(0));
            arrayList.add((String) v.elementAt(1));
        }
        version.minecraftJar = arrayList;
        version.minecraftJarIsActive = arrayListIsActive;
        arrayList = new ArrayList<String>();
        strings = ((DefaultTableModel) lstExternalMods.getModel()).getDataVector();
        for (Object s : strings) {
            Vector v = (Vector) s;
            if (!(Boolean) v.elementAt(0)) {
                arrayList.add((String) v.elementAt(1));
            }
        }
        version.inactiveExtMods = arrayList;
        arrayList = new ArrayList<String>();
        strings = ((DefaultTableModel) lstCoreMods.getModel()).getDataVector();
        for (Object s : strings) {
            Vector v = (Vector) s;
            if (!(Boolean) v.elementAt(0)) {
                arrayList.add((String) v.elementAt(1));
            }
        }
        version.inactiveCoreMods = arrayList;

        SettingsManager.setVersion(version);
        SettingsManager.save();
    }
    
    // <editor-fold defaultstate="collapsed" desc="Variables">
    Version version;
    public MinecraftVersionRequest minecraftVersion;
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="Statics">
    private static SetupWindow instance;
    
    public static SetupWindow getInstance() {
        return instance;
    }
    
    static {
        instance = new SetupWindow(null, true);
    }
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="UI">
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton btnAddExternelCoreMod;
    private javax.swing.JButton btnAddExternelMod;
    private javax.swing.JButton btnAddFirstMod;
    private javax.swing.JButton btnAddLastMod;
    private javax.swing.JButton btnAddMinecraftJar;
    private javax.swing.JButton btnDeleteExternelCoreMod;
    private javax.swing.JButton btnDeleteExternelMod;
    private javax.swing.JButton btnDeleteFirstMod;
    private javax.swing.JButton btnDeleteLastMod;
    private javax.swing.JButton btnDeleteMinecraftJar;
    private javax.swing.JButton btnDownFirstMod;
    private javax.swing.JButton btnDownLastMod;
    private javax.swing.JButton btnDownMinecraftJar;
    private javax.swing.JButton btnManageCoreMods;
    private javax.swing.JButton btnManageExtMods;
    private javax.swing.JButton btnSave;
    private javax.swing.JButton btnSetAssets;
    private javax.swing.JButton btnSetLibraries;
    private javax.swing.JButton btnUpFirstMod;
    private javax.swing.JButton btnUpLastMod;
    private javax.swing.JButton btnUpMinecraftJar;
    private javax.swing.JCheckBox chkFullscreen;
    private javax.swing.JCheckBox chkOver16;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel11;
    private javax.swing.JLabel jLabel19;
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
    private javax.swing.JPanel jPanel14;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel22;
    private javax.swing.JPanel jPanel23;
    private javax.swing.JPanel jPanel24;
    private javax.swing.JPanel jPanel25;
    private javax.swing.JPanel jPanel3;
    private javax.swing.JPanel jPanel5;
    private javax.swing.JPanel jPanel6;
    private javax.swing.JPanel jPanel7;
    private javax.swing.JScrollPane jScrollPane10;
    private javax.swing.JScrollPane jScrollPane19;
    private javax.swing.JScrollPane jScrollPane20;
    private javax.swing.JScrollPane jScrollPane21;
    private javax.swing.JScrollPane jScrollPane22;
    private javax.swing.JSplitPane jSplitPane1;
    private javax.swing.JSplitPane jSplitPane2;
    private javax.swing.JTabbedPane jTabbedPane2;
    private javax.swing.JTable lstCoreMods;
    private javax.swing.JTable lstExternalMods;
    private javax.swing.JTable lstFirstMod;
    private javax.swing.JTable lstLastMod;
    private javax.swing.JTable lstMinecraftJar;
    private javax.swing.JTabbedPane tabVersionEdit;
    private javax.swing.JTextField txtAssets;
    private javax.swing.JTextField txtGameDir;
    private javax.swing.JTextField txtHeight;
    private javax.swing.JTextField txtJavaArgs;
    private javax.swing.JTextField txtJavaDir;
    private javax.swing.JTextField txtLibraries;
    private javax.swing.JTextField txtMainClass;
    private javax.swing.JTextField txtMaxMemory;
    private javax.swing.JTextField txtMaxPermGen;
    private javax.swing.JTextField txtMinecraftArguments;
    private javax.swing.JTextField txtMinecraftVersion;
    private javax.swing.JTextField txtName;
    private javax.swing.JTextField txtWidth;
    // End of variables declaration//GEN-END:variables
    // </editor-fold>
}
