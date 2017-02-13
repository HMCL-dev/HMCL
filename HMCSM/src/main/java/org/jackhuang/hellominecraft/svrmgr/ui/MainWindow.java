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
package org.jackhuang.hellominecraft.svrmgr.ui;

import java.awt.Dimension;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;
import javax.swing.DefaultListModel;
import javax.swing.ImageIcon;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import org.jackhuang.hellominecraft.util.C;
import org.jackhuang.hellominecraft.util.log.HMCLog;
import org.jackhuang.hellominecraft.util.task.Task;
import org.jackhuang.hellominecraft.util.task.TaskWindow;
import org.jackhuang.hellominecraft.util.net.FileDownloadTask;
import org.jackhuang.hellominecraft.util.net.HTTPGetTask;
import org.jackhuang.hellominecraft.util.sys.FileUtils;
import org.jackhuang.hellominecraft.svrmgr.util.IMonitorService;
import org.jackhuang.hellominecraft.util.sys.IOUtils;
import org.jackhuang.hellominecraft.util.MessageBox;
import org.jackhuang.hellominecraft.svrmgr.util.ModType;
import org.jackhuang.hellominecraft.svrmgr.util.MonitorInfoBean;
import org.jackhuang.hellominecraft.svrmgr.util.MonitorServiceImpl;
import org.jackhuang.hellominecraft.svrmgr.util.version.MinecraftRemoteVersions;
import org.jackhuang.hellominecraft.svrmgr.Main;
import org.jackhuang.hellominecraft.svrmgr.plugin.BukkitPlugin;
import org.jackhuang.hellominecraft.svrmgr.plugin.Category;
import org.jackhuang.hellominecraft.svrmgr.plugin.PluginInfo;
import org.jackhuang.hellominecraft.svrmgr.plugin.PluginInformation;
import org.jackhuang.hellominecraft.svrmgr.plugin.PluginManager;
import org.jackhuang.hellominecraft.svrmgr.install.bukkit.BukkitFormatThread;
import org.jackhuang.hellominecraft.svrmgr.install.bukkit.BukkitVersion;
import org.jackhuang.hellominecraft.svrmgr.install.cauldron.ForgeFormatThread;
import org.jackhuang.hellominecraft.svrmgr.install.cauldron.ForgeInstaller;
import org.jackhuang.hellominecraft.svrmgr.install.cauldron.ForgeVersion;
import org.jackhuang.hellominecraft.svrmgr.server.ScheduleTranslator;
import org.jackhuang.hellominecraft.svrmgr.server.Server;
import org.jackhuang.hellominecraft.svrmgr.server.ServerChecker;
import org.jackhuang.hellominecraft.svrmgr.server.BackupManager;
import org.jackhuang.hellominecraft.svrmgr.setting.BannedPlayers;
import org.jackhuang.hellominecraft.svrmgr.setting.Op;
import org.jackhuang.hellominecraft.svrmgr.setting.Schedule;
import org.jackhuang.hellominecraft.svrmgr.setting.ServerProperties;
import org.jackhuang.hellominecraft.svrmgr.setting.SettingsManager;
import org.jackhuang.hellominecraft.svrmgr.setting.WhiteList;
import org.jackhuang.hellominecraft.svrmgr.util.MonitorThread;
import org.jackhuang.hellominecraft.svrmgr.util.FolderOpener;
import org.jackhuang.hellominecraft.svrmgr.util.IPGet;
import org.jackhuang.hellominecraft.svrmgr.util.Utilities;
import org.jackhuang.hellominecraft.util.ui.SwingUtils;
import org.jackhuang.hellominecraft.svrmgr.util.version.MinecraftRemoteVersion;
import org.jackhuang.hellominecraft.lookandfeel.ConstomButton;
import org.jackhuang.hellominecraft.api.HMCAPI;
import org.jackhuang.hellominecraft.api.SimpleEvent;
import org.jackhuang.hellominecraft.svrmgr.api.ServerStartedEvent;
import org.jackhuang.hellominecraft.svrmgr.api.ServerStoppedEvent;
import org.jackhuang.hellominecraft.util.StrUtils;
import org.jackhuang.hellominecraft.util.func.Consumer;

/**
 *
 * @author huangyuhui
 */
public final class MainWindow extends javax.swing.JFrame
        implements MonitorThread.MonitorThreadListener, Consumer<SimpleEvent<Integer>> {

    ImageIcon background = new ImageIcon(getClass().getResource("/background.jpg"));
    JLabel backgroundLabel;

    ImageIcon getResizedImage() {
        Image image = background.getImage();
        image = image.getScaledInstance(this.getWidth(), this.getHeight(), Image.SCALE_FAST);
        background.setImage(image);
        return new ImageIcon(image);
    }

    void resizeBackgroundLabel() {
        backgroundLabel.setIcon(getResizedImage());
        backgroundLabel.setBounds(0, 0, this.getWidth(), this.getHeight());
    }

    ArrayList<String> commandSet;
    int commandIndex;
    JPopupMenu ppmBasically;
    Timer getPlayerNumberTimer;
    Timer tmrSystem;

    private boolean outOfCommandSet() {
        return outOfCommandSet(commandIndex);
    }

    private boolean outOfCommandSet(int commandIndex) {
        return (commandIndex < 0 || commandIndex >= commandSet.size());
    }

    /**
     * Creates new form MainWindow
     */
    public MainWindow() {
        initComponents();

        Dimension scrSize = Toolkit.getDefaultToolkit().getScreenSize();
        setLocation((scrSize.width - this.getWidth()) / 2,
                (scrSize.height - this.getHeight()) / 2);

        this.setIconImage(new ImageIcon(getClass().getResource("/icon.png")).getImage());

        if (StrUtils.isNotBlank(SettingsManager.settings.bgPath)) {
            txtBackgroundPath.setText(SettingsManager.settings.bgPath);
            background = new ImageIcon(Toolkit.getDefaultToolkit().getImage(SettingsManager.settings.bgPath));
        }
        backgroundLabel = new JLabel(getResizedImage());
        backgroundLabel.setBounds(0, 0, this.getWidth(), this.getHeight());
        this.getContentPane().add(backgroundLabel, -1);

        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                resizeBackgroundLabel();
            }
        });

        setTitle(Main.makeTitle());
        String mainjar = SettingsManager.settings.mainjar;
        if (StrUtils.isNotBlank(mainjar))
            ServerProperties.init(new File(mainjar).getParent());
        txtMainJar.setText(mainjar);
        commandSet = new ArrayList<>();
        btnStop.setEnabled(false);
        btnShutdown.setEnabled(false);
        btnCommand.setEnabled(false);
        loadFromSettings();
        loadFromServerProperties();
        loadFromOPs();
        loadFromWhiteList();
        loadFromBannedPlayers();
        loadLocalMods();
        loadLocalCoreMods();
        loadLocalPlugins();
        loadWorlds();
        loadBackups();
        loadSchedules();
        refreshInfos();
        refreshReports();
        getIP();
        //lblMinecraftVersion.setText("服务端版本:" + MCUtils.minecraftVersion(new File(SettingsManager.settings.mainjar)).version);

        tmrSystem = new Timer();
        pgsCPURatio.setMinimum(0);
        pgsCPURatio.setMaximum(100);
        pgsMemoryRatio.setMinimum(0);
        pgsMemoryRatio.setMaximum(100);
        tmrSystem.schedule(new TimerTask() {

            @Override
            public void run() {
                try {
                    IMonitorService service = new MonitorServiceImpl();
                    MonitorInfoBean monitorInfo = service.getMonitorInfoBean();
                    pgsCPURatio.setValue((int) Math.round(monitorInfo.getCpuRatio()));
                    lblTotalMemory.setText(monitorInfo.getTotalMemory() / 1024 + "");
                    lblMaxMemory.setText(monitorInfo.getMaxMemory() / 1024 + "");
                    lblOSName.setText(monitorInfo.getOsName());
                    lblTotalMemorySize.setText(monitorInfo.getTotalMemorySize() / 1024 + "MB");
                    lblFreeMemory.setText(monitorInfo.getFreeMemory() / 1024 + "MB");
                    lblUsedMemory.setText(monitorInfo.getUsedMemory() / 1024 + "MB");
                    lblTotalThread.setText(monitorInfo.getTotalThread() / 1024 + "MB");
                    pgsMemoryRatio.setValue((int) (monitorInfo.getUsedMemory() * 100 / monitorInfo.getTotalMemorySize()));
                } catch (Exception e) {
                    HMCLog.warn("Failed to get system information.", e);
                }
            }

        }, 0, 2000);

        //<editor-fold defaultstate="collapsed" desc="基本信息菜单">
        class ActionListenerImpl implements ActionListener {

            String s, q;

            public ActionListenerImpl(String s) {
                this(s, null);
            }

            public ActionListenerImpl(String s, String question) {
                this.s = s;
                q = question;
            }

            @Override
            public void actionPerformed(ActionEvent e) {
                String ans = q == null ? "" : JOptionPane.showInputDialog(q);
                if (ans == null)
                    return;
                s = s.replace("{}", ans);
                Server.getInstance().sendCommand(s);
            }

        }
        JMenuItem itm;
        ppmBasically = new JPopupMenu();
        itm = new JMenuItem("重置插件");
        itm.addActionListener(new ActionListenerImpl("reload"));
        ppmBasically = new JPopupMenu();
        itm = new JMenuItem("午夜");
        itm.addActionListener(new ActionListenerImpl("time set 18000"));
        ppmBasically.add(itm);
        itm = new JMenuItem("凌晨");
        itm.addActionListener(new ActionListenerImpl("time set 0"));
        ppmBasically.add(itm);
        itm = new JMenuItem("广播");
        itm.addActionListener(new ActionListenerImpl("say {}", "广播讯息"));
        itm = new JMenuItem("红字广播");
        itm.addActionListener(new ActionListenerImpl("me {}", "广播讯息"));
        itm = new JMenuItem("私聊");
        itm.addActionListener(e -> {
            InputDialog id = new InputDialog(MainWindow.this, true, new String[] { "玩家", "讯息" });
            id.setVisible(true);
            Server.getInstance().sendCommand("tell " + id.result[0] + " " + id.result[1]);
        });
        ppmBasically.add(itm);
        itm = new JMenuItem("给予OP");
        itm.addActionListener(new ActionListenerImpl("op {}", "新OP的游戏名"));
        ppmBasically.add(itm);
        itm = new JMenuItem("卸除OP");
        itm.addActionListener(new ActionListenerImpl("deop {}", "要卸除OP的游戏名"));
        ppmBasically.add(itm);
        itm = new JMenuItem("给予玩家白名单");
        itm.addActionListener(new ActionListenerImpl("whitelist add {}", "要添入白名单的游戏名"));
        ppmBasically.add(itm);
        itm = new JMenuItem("解除玩家白名单");
        itm.addActionListener(new ActionListenerImpl("whitelist remove {}", "要解除白名单的游戏名"));
        ppmBasically.add(itm);
        itm = new JMenuItem("启用白名单");
        itm.addActionListener(new ActionListenerImpl("whitelist on"));
        ppmBasically.add(itm);
        itm = new JMenuItem("禁用白名单");
        itm.addActionListener(new ActionListenerImpl("whitelist off"));
        ppmBasically.add(itm);
        itm = new JMenuItem("列出白名单");
        itm.addActionListener(new ActionListenerImpl("whitelist list"));
        ppmBasically.add(itm);
        itm = new JMenuItem("封禁玩家");
        itm.addActionListener(new ActionListenerImpl("ban {}", "要封禁玩家的游戏名"));
        ppmBasically.add(itm);
        itm = new JMenuItem("封禁玩家IP");
        itm.addActionListener(new ActionListenerImpl("ban-ip {}", "要封禁玩家IP的游戏名"));
        itm = new JMenuItem("解封玩家");
        itm.addActionListener(new ActionListenerImpl("pardon {}", "要解封玩家的游戏名"));
        ppmBasically.add(itm);
        itm = new JMenuItem("解封玩家IP");
        itm.addActionListener(new ActionListenerImpl("pardon-ip {}", "要解封玩家IP的游戏名"));
        itm = new JMenuItem("封禁玩家");
        itm.addActionListener(new ActionListenerImpl("ban {}", "要封禁玩家的游戏名"));
        ppmBasically.add(itm);
        itm = new JMenuItem("封禁玩家IP");
        itm.addActionListener(new ActionListenerImpl("ban-ip {}", "要封禁玩家IP的游戏名"));
        itm = new JMenuItem("封禁玩家列表");
        itm.addActionListener(new ActionListenerImpl("banlist"));
        ppmBasically.add(itm);
        itm = new JMenuItem("修改时间");
        itm.addActionListener(new ActionListenerImpl("time set {}", "要调整的时间值"));
        ppmBasically.add(itm);
        itm = new JMenuItem("往后调整时间");
        itm.addActionListener(new ActionListenerImpl("time add {}", "要往后调整的时间值"));
        ppmBasically.add(itm);
        itm = new JMenuItem("调整天气");
        itm.addActionListener(new ActionListenerImpl("weather {}", "要调整的天气（只能填：clear[意思是取消所有天气]或rain[意思是下雨]或thunder[意思是打雷]"));
        ppmBasically.add(itm);
        itm = new JMenuItem("调整一定时间的天气");
        itm.addActionListener(e -> {
            InputDialog id = new InputDialog(MainWindow.this, true, new String[] {
                "要调整的天气（只能填：clear[意思是取消所有天气]或rain[意思是下雨]或thunder[意思是打雷]",
                "时间"
            });
            id.setVisible(true);
            if (id.result != null) {
                String s = JOptionPane.showInputDialog("");
                Server.getInstance().sendCommand("weather " + id.result[0] + " " + id.result[1]);
            }
        });
        ppmBasically.add(itm);
        itm = new JMenuItem("清除背包");
        itm.addActionListener(new ActionListenerImpl("clear {}", "要被清除背包的玩家"));
        ppmBasically.add(itm);
        itm = new JMenuItem("踢出玩家");
        itm.addActionListener(new ActionListenerImpl("kick {}", "要被踢出的玩家"));
        ppmBasically.add(itm);
        itm = new JMenuItem("在线玩家");
        itm.addActionListener(new ActionListenerImpl("list"));
        ppmBasically.add(itm);
        itm = new JMenuItem("插件列表");
        itm.addActionListener(new ActionListenerImpl("plugins"));
        ppmBasically.add(itm);
        itm = new JMenuItem("给予玩家物品");
        itm.addActionListener(e -> {
            InputDialog id = new InputDialog(MainWindow.this, true, new String[] { "玩家", "物品ID", "数量" });
            id.setVisible(true);
            if (id.result != null)
                Server.getInstance().sendCommand("give " + id.result[0] + " " + id.result[1] + " " + id.result[2]);
        });
        ppmBasically.add(itm);
        itm = new JMenuItem("保存所有");
        itm.addActionListener(new ActionListenerImpl("save-all"));
        ppmBasically.add(itm);
        itm = new JMenuItem("开启bukkit自动保存");
        itm.addActionListener(new ActionListenerImpl("save-on"));
        ppmBasically.add(itm);
        itm = new JMenuItem("取消bukkit自动保存");
        itm.addActionListener(new ActionListenerImpl("save-off"));
        ppmBasically.add(itm);
        itm = new JMenuItem("难度");
        itm.addActionListener(new ActionListenerImpl("difficulty {}", "难度"));
        ppmBasically.add(itm);
        itm = new JMenuItem("默认游戏模式");
        itm.addActionListener(new ActionListenerImpl("defaultgamemode {}", "默认游戏模式"));
        ppmBasically.add(itm);
        itm = new JMenuItem("地图种子");
        itm.addActionListener(new ActionListenerImpl("seed"));
        ppmBasically.add(itm);
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

        jTabbedPane1 = new javax.swing.JTabbedPane();
        jPanel1 = new javax.swing.JPanel();
        btnLaunch = new ConstomButton();
        jScrollPane4 = new javax.swing.JScrollPane();
        txtMain = new javax.swing.JTextArea();
        jLabel17 = new javax.swing.JLabel();
        txtMainJar = new javax.swing.JTextField();
        btnSetJar = new ConstomButton();
        txtCommand = new javax.swing.JTextField();
        btnSendCommand = new ConstomButton();
        jLabel1 = new javax.swing.JLabel();
        btnSave = new ConstomButton();
        jButton3 = new ConstomButton();
        btnRestart = new ConstomButton();
        btnShutdown = new ConstomButton();
        btnAutoSearch = new ConstomButton();
        lblIPAddress = new javax.swing.JLabel();
        btnStop = new ConstomButton();
        btnCommand = new ConstomButton();
        jPanel29 = new javax.swing.JPanel();
        jLabel29 = new javax.swing.JLabel();
        jLabel30 = new javax.swing.JLabel();
        jLabel32 = new javax.swing.JLabel();
        jLabel33 = new javax.swing.JLabel();
        jLabel34 = new javax.swing.JLabel();
        jLabel35 = new javax.swing.JLabel();
        jLabel36 = new javax.swing.JLabel();
        jLabel37 = new javax.swing.JLabel();
        pgsCPURatio = new javax.swing.JProgressBar();
        lblTotalMemory = new javax.swing.JLabel();
        lblMaxMemory = new javax.swing.JLabel();
        lblOSName = new javax.swing.JLabel();
        lblTotalMemorySize = new javax.swing.JLabel();
        lblFreeMemory = new javax.swing.JLabel();
        lblUsedMemory = new javax.swing.JLabel();
        lblTotalThread = new javax.swing.JLabel();
        pgsMemoryRatio = new javax.swing.JProgressBar();
        jLabel31 = new javax.swing.JLabel();
        jPanel2 = new javax.swing.JPanel();
        jTabbedPane2 = new javax.swing.JTabbedPane();
        jPanel24 = new javax.swing.JPanel();
        jTabbedPane6 = new javax.swing.JTabbedPane();
        jPanel3 = new javax.swing.JPanel();
        jLabel5 = new javax.swing.JLabel();
        txtServerPort = new javax.swing.JSpinner();
        jLabel6 = new javax.swing.JLabel();
        txtMaxPlayer = new javax.swing.JSpinner();
        txtViewDistance = new javax.swing.JSpinner();
        jLabel7 = new javax.swing.JLabel();
        jLabel8 = new javax.swing.JLabel();
        txtWorldName = new javax.swing.JTextField();
        jLabel9 = new javax.swing.JLabel();
        cboWorldType = new javax.swing.JComboBox();
        jLabel10 = new javax.swing.JLabel();
        txtServerMOTD = new javax.swing.JTextField();
        jLabel11 = new javax.swing.JLabel();
        cboDifficulty = new javax.swing.JComboBox();
        jLabel12 = new javax.swing.JLabel();
        cboGameMode = new javax.swing.JComboBox();
        chkEnalbleAnimals = new javax.swing.JCheckBox();
        chkEnableMonsters = new javax.swing.JCheckBox();
        chkEnableNPCs = new javax.swing.JCheckBox();
        chkAllowFlight = new javax.swing.JCheckBox();
        chkPVP = new javax.swing.JCheckBox();
        chkAllowNether = new javax.swing.JCheckBox();
        chkWhiteList = new javax.swing.JCheckBox();
        txtServerName = new javax.swing.JTextField();
        jLabel15 = new javax.swing.JLabel();
        jLabel16 = new javax.swing.JLabel();
        txtMaxBuildHeight = new javax.swing.JSpinner();
        jLabel18 = new javax.swing.JLabel();
        txtServerGeneratorSettings = new javax.swing.JTextField();
        txtWorldSeed = new javax.swing.JTextField();
        jLabel19 = new javax.swing.JLabel();
        chkGenerateStructures = new javax.swing.JCheckBox();
        chkOnlineMode = new javax.swing.JCheckBox();
        jPanel25 = new javax.swing.JPanel();
        jLabel14 = new javax.swing.JLabel();
        jLabel25 = new javax.swing.JLabel();
        txtJavaDir = new javax.swing.JTextField();
        txtJavaArgs = new javax.swing.JTextField();
        jLabel26 = new javax.swing.JLabel();
        txtMaxMemory = new javax.swing.JTextField();
        jPanel12 = new javax.swing.JPanel();
        jLabel20 = new javax.swing.JLabel();
        txtBackgroundPath = new javax.swing.JTextField();
        btnSetBackgroundPath = new ConstomButton();
        jPanel4 = new javax.swing.JPanel();
        jTabbedPane3 = new javax.swing.JTabbedPane();
        jPanel5 = new javax.swing.JPanel();
        jScrollPane1 = new javax.swing.JScrollPane();
        lstOP = new javax.swing.JList();
        btnAddOP = new ConstomButton();
        jLabel2 = new javax.swing.JLabel();
        txtOPName = new javax.swing.JTextField();
        btnDeleteOP = new ConstomButton();
        jLabel40 = new javax.swing.JLabel();
        jPanel6 = new javax.swing.JPanel();
        btnDeleteWhite = new ConstomButton();
        jScrollPane3 = new javax.swing.JScrollPane();
        lstWhiteList = new javax.swing.JList();
        jLabel4 = new javax.swing.JLabel();
        txtWhiteName = new javax.swing.JTextField();
        btnAddWhite = new ConstomButton();
        jLabel41 = new javax.swing.JLabel();
        jPanel7 = new javax.swing.JPanel();
        btnUnban = new ConstomButton();
        jScrollPane2 = new javax.swing.JScrollPane();
        lstBanned = new javax.swing.JList();
        jLabel3 = new javax.swing.JLabel();
        txtBanName = new javax.swing.JTextField();
        btnAddBan = new ConstomButton();
        jLabel42 = new javax.swing.JLabel();
        jPanel8 = new javax.swing.JPanel();
        jTabbedPane4 = new javax.swing.JTabbedPane();
        jPanel9 = new javax.swing.JPanel();
        jScrollPane5 = new javax.swing.JScrollPane();
        lstExternalMods = new javax.swing.JTable();
        btnManageExtMods = new ConstomButton();
        btnAddExternelMod = new ConstomButton();
        btnDeleteExternelMod = new ConstomButton();
        btnSaveExtMod = new ConstomButton();
        jPanel10 = new javax.swing.JPanel();
        jScrollPane6 = new javax.swing.JScrollPane();
        lstCoreMods = new javax.swing.JTable();
        btnManageCoreMods = new ConstomButton();
        btnAddExternelCoreMod = new ConstomButton();
        btnDeleteExternelCoreMod = new ConstomButton();
        btnSaveCoreMod = new ConstomButton();
        jPanel11 = new javax.swing.JPanel();
        jScrollPane7 = new javax.swing.JScrollPane();
        lstPlugins = new javax.swing.JTable();
        btnManagePlugins = new ConstomButton();
        btnAddPlugins = new ConstomButton();
        btnDeletePlugins = new ConstomButton();
        btnSavePlugins = new ConstomButton();
        jPanel13 = new javax.swing.JPanel();
        jPanel14 = new javax.swing.JPanel();
        jLabel21 = new javax.swing.JLabel();
        cboTimerTask = new javax.swing.JComboBox();
        txtTimerTaskPeriod = new javax.swing.JTextField();
        jLabel22 = new javax.swing.JLabel();
        btnNewTask = new ConstomButton();
        jLabel13 = new javax.swing.JLabel();
        txtTimerTaskContent = new javax.swing.JTextField();
        jLabel28 = new javax.swing.JLabel();
        cboTimeType = new javax.swing.JComboBox();
        btnDelSelectedSchedule = new ConstomButton();
        jScrollPane9 = new javax.swing.JScrollPane();
        lstSchedules = new javax.swing.JTable();
        jPanel15 = new javax.swing.JPanel();
        jPanel16 = new javax.swing.JPanel();
        jPanel17 = new javax.swing.JPanel();
        jLabel23 = new javax.swing.JLabel();
        cboBackupTypes = new javax.swing.JComboBox();
        btnBackup = new ConstomButton();
        jButton1 = new ConstomButton();
        btnDeleteBackup = new ConstomButton();
        btnRestoreBackup = new ConstomButton();
        jScrollPane11 = new javax.swing.JScrollPane();
        lstBackups = new javax.swing.JTable();
        jPanel18 = new javax.swing.JPanel();
        jScrollPane10 = new javax.swing.JScrollPane();
        lstWorlds = new javax.swing.JTable();
        btnRefreshWorlds = new ConstomButton();
        btnSaveWorld = new ConstomButton();
        jPanel19 = new javax.swing.JPanel();
        jTabbedPane5 = new javax.swing.JTabbedPane();
        jPanel20 = new javax.swing.JPanel();
        jScrollPane18 = new javax.swing.JScrollPane();
        lstCraftbukkit = new javax.swing.JTable();
        btnDownloadCraftbukkit = new ConstomButton();
        lstRefreshCraftbukkit = new ConstomButton();
        cboBukkitType = new javax.swing.JComboBox();
        jLabel39 = new javax.swing.JLabel();
        jPanel30 = new javax.swing.JPanel();
        jScrollPane19 = new javax.swing.JScrollPane();
        lstMCPC = new javax.swing.JTable();
        btnDownloadMCPC = new ConstomButton();
        lstRefreshMCPC = new ConstomButton();
        jLabel38 = new javax.swing.JLabel();
        cboCauldronMinecraft = new javax.swing.JComboBox();
        btnInstallMCPC = new ConstomButton();
        jPanel21 = new javax.swing.JPanel();
        btnRefreshDownloads = new ConstomButton();
        jScrollPane12 = new javax.swing.JScrollPane();
        lstDownloads = new javax.swing.JTable();
        btnMinecraftServerDownload = new ConstomButton();
        jPanel23 = new javax.swing.JPanel();
        jScrollPane15 = new javax.swing.JScrollPane();
        lstBukkitPlugins = new javax.swing.JTable();
        cboCategory = new javax.swing.JComboBox();
        jLabel24 = new javax.swing.JLabel();
        btnShowPluginInfo = new ConstomButton();
        jButton11 = new javax.swing.JButton();
        jPanel22 = new javax.swing.JPanel();
        jScrollPane13 = new javax.swing.JScrollPane();
        lstInfos = new javax.swing.JTable();
        btnRefreshInfos = new ConstomButton();
        jScrollPane14 = new javax.swing.JScrollPane();
        txtInfo = new javax.swing.JTextArea();
        btnShowInfo = new ConstomButton();
        jPanel26 = new javax.swing.JPanel();
        btnRefreshReports = new ConstomButton();
        jScrollPane17 = new javax.swing.JScrollPane();
        txtCrashReport = new javax.swing.JTextArea();
        btnShowReport = new ConstomButton();
        jScrollPane8 = new javax.swing.JScrollPane();
        lstReports = new javax.swing.JList();
        jPanel27 = new javax.swing.JPanel();
        lblPlayers = new javax.swing.JLabel();
        btnRefreshPlayers = new ConstomButton();
        jScrollPane16 = new javax.swing.JScrollPane();
        lstPlayers = new javax.swing.JList();
        jLabel27 = new javax.swing.JLabel();
        jPanel28 = new javax.swing.JPanel();
        jButton2 = new ConstomButton();
        jButton4 = new ConstomButton();
        jButton5 = new ConstomButton();
        jButton6 = new ConstomButton();
        jButton7 = new ConstomButton();
        jButton8 = new ConstomButton();
        jButton9 = new ConstomButton();
        jButton10 = new ConstomButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setTitle("Hello Minecraft! Server Manager 0.1");
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosed(java.awt.event.WindowEvent evt) {
                formWindowClosed(evt);
            }
            public void windowClosing(java.awt.event.WindowEvent evt) {
                formWindowClosing(evt);
            }
        });

        jTabbedPane1.setTabPlacement(javax.swing.JTabbedPane.LEFT);

        btnLaunch.setText("启动");
        btnLaunch.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnLaunchActionPerformed(evt);
            }
        });

        txtMain.setEditable(false);
        txtMain.setColumns(20);
        txtMain.setRows(5);
        jScrollPane4.setViewportView(txtMain);

        jLabel17.setText("启动jar");

        txtMainJar.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                txtMainJarFocusLost(evt);
            }
        });

        btnSetJar.setText("手动设置");
        btnSetJar.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnSetJarActionPerformed(evt);
            }
        });

        txtCommand.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyPressed(java.awt.event.KeyEvent evt) {
                txtCommandKeyPressed(evt);
            }
        });

        btnSendCommand.setText("发送命令");
        btnSendCommand.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnSendCommandActionPerformed(evt);
            }
        });

        jLabel1.setText("指令");

        btnSave.setText("保存");
        btnSave.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnSaveActionPerformed(evt);
            }
        });

        jButton3.setText("清除记录");
        jButton3.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton3ActionPerformed(evt);
            }
        });

        btnRestart.setText("重启");
        btnRestart.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnRestartActionPerformed(evt);
            }
        });

        btnShutdown.setText("强制关闭");
        btnShutdown.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnShutdownActionPerformed(evt);
            }
        });

        btnAutoSearch.setText("自动搜寻");
        btnAutoSearch.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnAutoSearchActionPerformed(evt);
            }
        });

        lblIPAddress.setText("获取IP地址...");
        lblIPAddress.setToolTipText("");

        btnStop.setText("停止");
        btnStop.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnStopActionPerformed(evt);
            }
        });

        btnCommand.setText("基本命令");
        btnCommand.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                btnCommandMouseClicked(evt);
            }
        });

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addGroup(jPanel1Layout.createSequentialGroup()
                                .addComponent(jLabel17)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(txtMainJar))
                            .addGroup(jPanel1Layout.createSequentialGroup()
                                .addComponent(lblIPAddress)
                                .addGap(0, 265, Short.MAX_VALUE)))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnSetJar)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnAutoSearch))
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(jPanel1Layout.createSequentialGroup()
                                .addComponent(jLabel1)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(txtCommand))
                            .addComponent(jScrollPane4))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(btnLaunch, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(btnRestart, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(btnSave, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(btnShutdown, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(btnCommand, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(jButton3, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(btnStop, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(btnSendCommand, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))))
                .addContainerGap())
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(txtMainJar, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(btnSetJar)
                    .addComponent(jLabel17)
                    .addComponent(btnAutoSearch))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(lblIPAddress)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addComponent(jButton3)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnCommand)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 147, Short.MAX_VALUE)
                        .addComponent(btnShutdown)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnSave)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnRestart)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnStop)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnLaunch))
                    .addComponent(jScrollPane4))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(txtCommand, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(btnSendCommand)
                    .addComponent(jLabel1))
                .addContainerGap())
        );

        jTabbedPane1.addTab("主页", jPanel1);

        jLabel29.setText("cpu占有率");

        jLabel30.setText("可使用内存");

        jLabel32.setText("最大可使用内存");

        jLabel33.setText("操作系统");

        jLabel34.setText("总的物理内存");

        jLabel35.setText("剩余的物理内存");

        jLabel36.setText("已使用的物理内存");

        jLabel37.setText("线程总数");

        lblTotalMemory.setText("jLabel38");

        lblMaxMemory.setText("jLabel40");

        lblOSName.setText("jLabel41");

        lblTotalMemorySize.setText("jLabel42");

        lblFreeMemory.setText("jLabel43");

        lblUsedMemory.setText("jLabel44");

        lblTotalThread.setText("jLabel45");

        jLabel31.setText("内存占有率");

        javax.swing.GroupLayout jPanel29Layout = new javax.swing.GroupLayout(jPanel29);
        jPanel29.setLayout(jPanel29Layout);
        jPanel29Layout.setHorizontalGroup(
            jPanel29Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel29Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel29Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel29)
                    .addComponent(jLabel30)
                    .addComponent(jLabel32)
                    .addComponent(jLabel33)
                    .addComponent(jLabel34)
                    .addComponent(jLabel35)
                    .addComponent(jLabel36)
                    .addComponent(jLabel37)
                    .addComponent(jLabel31))
                .addGap(34, 34, 34)
                .addGroup(jPanel29Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(pgsMemoryRatio, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(lblTotalThread)
                    .addComponent(lblUsedMemory)
                    .addComponent(lblFreeMemory)
                    .addComponent(lblTotalMemorySize)
                    .addComponent(lblOSName)
                    .addComponent(lblMaxMemory)
                    .addComponent(lblTotalMemory)
                    .addComponent(pgsCPURatio, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(247, Short.MAX_VALUE))
        );
        jPanel29Layout.setVerticalGroup(
            jPanel29Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel29Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel29Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel29)
                    .addComponent(pgsCPURatio, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel29Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel30)
                    .addComponent(lblTotalMemory))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel29Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel32)
                    .addComponent(lblMaxMemory))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel29Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel33)
                    .addComponent(lblOSName))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel29Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel34)
                    .addComponent(lblTotalMemorySize))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel29Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel35)
                    .addComponent(lblFreeMemory))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel29Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel36)
                    .addComponent(lblUsedMemory))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel29Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(jLabel31)
                    .addComponent(pgsMemoryRatio, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel29Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel37)
                    .addComponent(lblTotalThread))
                .addContainerGap(267, Short.MAX_VALUE))
        );

        jTabbedPane1.addTab("状态", jPanel29);

        jLabel5.setText("服务器端口");

        txtServerPort.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                txtServerPortFocusLost(evt);
            }
        });

        jLabel6.setText("最大人数");

        txtMaxPlayer.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                txtMaxPlayerFocusLost(evt);
            }
        });

        txtViewDistance.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                txtViewDistanceFocusLost(evt);
            }
        });

        jLabel7.setText("视线距离");

        jLabel8.setText("地图名称");

        txtWorldName.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                txtWorldNameFocusLost(evt);
            }
        });

        jLabel9.setText("地图类型");

        cboWorldType.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "默认", "超平坦", "巨型生物群系" }));
        cboWorldType.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                cboWorldTypeItemStateChanged(evt);
            }
        });

        jLabel10.setText("服务器介绍");

        txtServerMOTD.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                txtServerMOTDFocusLost(evt);
            }
        });

        jLabel11.setText("游戏模式");

        cboDifficulty.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "和平", "简单", "普通", "困难" }));
        cboDifficulty.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                cboDifficultyItemStateChanged(evt);
            }
        });

        jLabel12.setText("难度");

        cboGameMode.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "生存", "创造", "极限" }));
        cboGameMode.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                cboGameModeItemStateChanged(evt);
            }
        });

        chkEnalbleAnimals.setText("生成动物");
        chkEnalbleAnimals.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                chkEnalbleAnimalsActionPerformed(evt);
            }
        });

        chkEnableMonsters.setText("生成怪物");
        chkEnableMonsters.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                chkEnableMonstersActionPerformed(evt);
            }
        });

        chkEnableNPCs.setText("生成村民");
        chkEnableNPCs.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                chkEnableNPCsActionPerformed(evt);
            }
        });

        chkAllowFlight.setText("允许飞行");
        chkAllowFlight.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                chkAllowFlightActionPerformed(evt);
            }
        });

        chkPVP.setText("允许pvp");
        chkPVP.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                chkPVPActionPerformed(evt);
            }
        });

        chkAllowNether.setText("开放地狱");
        chkAllowNether.setToolTipText("");
        chkAllowNether.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                chkAllowNetherActionPerformed(evt);
            }
        });

        chkWhiteList.setText("开启白名单");
        chkWhiteList.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                chkWhiteListActionPerformed(evt);
            }
        });

        txtServerName.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                txtServerNameFocusLost(evt);
            }
        });

        jLabel15.setText("服务器名称");

        jLabel16.setText("最大建筑高度");

        txtMaxBuildHeight.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                txtMaxBuildHeightFocusLost(evt);
            }
        });

        jLabel18.setText("地图生成器设置");

        txtServerGeneratorSettings.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                txtServerGeneratorSettingsFocusLost(evt);
            }
        });

        txtWorldSeed.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                txtWorldSeedFocusLost(evt);
            }
        });

        jLabel19.setText("地图种子");

        chkGenerateStructures.setText("生成建筑");
        chkGenerateStructures.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                chkGenerateStructuresActionPerformed(evt);
            }
        });

        chkOnlineMode.setText("开启正版");
        chkOnlineMode.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                chkOnlineModeActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel3Layout = new javax.swing.GroupLayout(jPanel3);
        jPanel3.setLayout(jPanel3Layout);
        jPanel3Layout.setHorizontalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel3Layout.createSequentialGroup()
                        .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel5)
                            .addComponent(jLabel6)
                            .addComponent(jLabel7)
                            .addComponent(jLabel16)
                            .addComponent(jLabel8)
                            .addComponent(jLabel18))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(txtServerGeneratorSettings)
                            .addComponent(txtWorldName)
                            .addComponent(txtMaxBuildHeight)
                            .addComponent(txtViewDistance)
                            .addComponent(txtMaxPlayer)
                            .addComponent(txtServerPort)))
                    .addGroup(jPanel3Layout.createSequentialGroup()
                        .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel15)
                            .addComponent(jLabel10)
                            .addComponent(jLabel11)
                            .addComponent(jLabel9)
                            .addComponent(jLabel12)
                            .addComponent(jLabel19))
                        .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(jPanel3Layout.createSequentialGroup()
                                .addGap(26, 26, 26)
                                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(cboDifficulty, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                    .addComponent(cboGameMode, javax.swing.GroupLayout.Alignment.TRAILING, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                    .addComponent(txtServerMOTD, javax.swing.GroupLayout.Alignment.TRAILING)))
                            .addGroup(jPanel3Layout.createSequentialGroup()
                                .addGap(28, 28, 28)
                                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(txtServerName, javax.swing.GroupLayout.Alignment.TRAILING)
                                    .addComponent(cboWorldType, javax.swing.GroupLayout.Alignment.TRAILING, 0, 418, Short.MAX_VALUE)
                                    .addComponent(txtWorldSeed)))))
                    .addGroup(jPanel3Layout.createSequentialGroup()
                        .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(jPanel3Layout.createSequentialGroup()
                                .addComponent(chkEnalbleAnimals)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(chkEnableMonsters))
                            .addGroup(jPanel3Layout.createSequentialGroup()
                                .addComponent(chkAllowFlight)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(chkPVP)))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(jPanel3Layout.createSequentialGroup()
                                .addComponent(chkEnableNPCs)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(chkGenerateStructures)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(chkOnlineMode))
                            .addGroup(jPanel3Layout.createSequentialGroup()
                                .addComponent(chkAllowNether)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(chkWhiteList)))))
                .addContainerGap())
        );
        jPanel3Layout.setVerticalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel5)
                    .addComponent(txtServerPort, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel6)
                    .addComponent(txtMaxPlayer, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel7)
                    .addComponent(txtViewDistance, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel16)
                    .addComponent(txtMaxBuildHeight, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel8)
                    .addComponent(txtWorldName, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel18)
                    .addComponent(txtServerGeneratorSettings, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel19)
                    .addComponent(txtWorldSeed, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel9)
                    .addComponent(cboWorldType, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel15)
                    .addComponent(txtServerName, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel10)
                    .addComponent(txtServerMOTD, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel11)
                    .addComponent(cboGameMode, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel12)
                    .addComponent(cboDifficulty, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(chkEnalbleAnimals)
                    .addComponent(chkEnableMonsters)
                    .addComponent(chkEnableNPCs)
                    .addComponent(chkGenerateStructures)
                    .addComponent(chkOnlineMode))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(chkAllowFlight)
                    .addComponent(chkPVP)
                    .addComponent(chkAllowNether)
                    .addComponent(chkWhiteList))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        jTabbedPane6.addTab("游戏设置", jPanel3);

        jLabel14.setText("内存大小");

        jLabel25.setText("Java路径");

        jLabel26.setText("Java参数");

        txtMaxMemory.setText("jTextField1");
        txtMaxMemory.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                txtMaxMemoryFocusLost(evt);
            }
        });

        javax.swing.GroupLayout jPanel25Layout = new javax.swing.GroupLayout(jPanel25);
        jPanel25.setLayout(jPanel25Layout);
        jPanel25Layout.setHorizontalGroup(
            jPanel25Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel25Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel25Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel25Layout.createSequentialGroup()
                        .addComponent(jLabel14)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(txtMaxMemory))
                    .addGroup(jPanel25Layout.createSequentialGroup()
                        .addComponent(jLabel25)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(txtJavaDir, javax.swing.GroupLayout.DEFAULT_SIZE, 468, Short.MAX_VALUE))
                    .addGroup(jPanel25Layout.createSequentialGroup()
                        .addComponent(jLabel26)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(txtJavaArgs, javax.swing.GroupLayout.DEFAULT_SIZE, 446, Short.MAX_VALUE)))
                .addContainerGap())
        );
        jPanel25Layout.setVerticalGroup(
            jPanel25Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel25Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel25Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel14)
                    .addComponent(txtMaxMemory, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel25Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel25)
                    .addComponent(txtJavaDir, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel25Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel26)
                    .addComponent(txtJavaArgs, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(342, Short.MAX_VALUE))
        );

        jTabbedPane6.addTab("Java设置", jPanel25);

        javax.swing.GroupLayout jPanel24Layout = new javax.swing.GroupLayout(jPanel24);
        jPanel24.setLayout(jPanel24Layout);
        jPanel24Layout.setHorizontalGroup(
            jPanel24Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jTabbedPane6)
        );
        jPanel24Layout.setVerticalGroup(
            jPanel24Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jTabbedPane6)
        );

        jTabbedPane6.getAccessibleContext().setAccessibleName("");

        jTabbedPane2.addTab("服务器设置", jPanel24);

        jLabel20.setText("背景");

        txtBackgroundPath.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                txtBackgroundPathFocusLost(evt);
            }
        });

        btnSetBackgroundPath.setText("浏览");
        btnSetBackgroundPath.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnSetBackgroundPathActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel12Layout = new javax.swing.GroupLayout(jPanel12);
        jPanel12.setLayout(jPanel12Layout);
        jPanel12Layout.setHorizontalGroup(
            jPanel12Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel12Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel20)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(txtBackgroundPath, javax.swing.GroupLayout.DEFAULT_SIZE, 412, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(btnSetBackgroundPath)
                .addContainerGap())
        );
        jPanel12Layout.setVerticalGroup(
            jPanel12Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel12Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel12Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel20)
                    .addComponent(txtBackgroundPath, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(btnSetBackgroundPath))
                .addContainerGap(419, Short.MAX_VALUE))
        );

        jTabbedPane2.addTab("开服器设置", jPanel12);

        jScrollPane1.setViewportView(lstOP);

        btnAddOP.setText("添加");
        btnAddOP.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnAddOPActionPerformed(evt);
            }
        });

        jLabel2.setText("玩家名");

        btnDeleteOP.setText("删除选中");
        btnDeleteOP.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnDeleteOPActionPerformed(evt);
            }
        });

        jLabel40.setText("<html>\n在没有开服的<br/>\n情况下使用本<br/>\n功能会直接覆<br/>\n盖json文件，<br/>\n这会导致在<br/>\n1.7.10服务端中<br/>\n的设置被抹除<br/>\n，而且不支持<br/>\n正版验证，非<br/>\n1.7.10或更高<br/>\n版本服务端无<br/>\n此问题。\n</html>");

        javax.swing.GroupLayout jPanel5Layout = new javax.swing.GroupLayout(jPanel5);
        jPanel5.setLayout(jPanel5Layout);
        jPanel5Layout.setHorizontalGroup(
            jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel5Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jScrollPane1)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                        .addComponent(jLabel2)
                        .addComponent(btnAddOP, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(txtOPName)
                        .addComponent(btnDeleteOP, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .addComponent(jLabel40, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap())
        );
        jPanel5Layout.setVerticalGroup(
            jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel5Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 405, Short.MAX_VALUE)
                    .addGroup(jPanel5Layout.createSequentialGroup()
                        .addComponent(jLabel2)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(txtOPName, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnAddOP)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jLabel40, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(btnDeleteOP)))
                .addContainerGap())
        );

        jTabbedPane3.addTab("管理员", jPanel5);

        btnDeleteWhite.setText("删除选中");
        btnDeleteWhite.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnDeleteWhiteActionPerformed(evt);
            }
        });

        jScrollPane3.setViewportView(lstWhiteList);

        jLabel4.setText("玩家名");

        btnAddWhite.setText("添加");
        btnAddWhite.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnAddWhiteActionPerformed(evt);
            }
        });

        jLabel41.setText("<html>\n在没有开服的<br/>\n情况下使用本<br/>\n功能会直接覆<br/>\n盖json文件，<br/>\n这会导致在<br/>\n1.7.10服务端中<br/>\n的设置被抹除<br/>\n，而且不支持<br/>\n正版验证，非<br/>\n1.7.10或更高<br/>\n版本服务端无<br/>\n此问题。\n</html>");

        javax.swing.GroupLayout jPanel6Layout = new javax.swing.GroupLayout(jPanel6);
        jPanel6.setLayout(jPanel6Layout);
        jPanel6Layout.setHorizontalGroup(
            jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel6Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jScrollPane3, javax.swing.GroupLayout.DEFAULT_SIZE, 413, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                        .addComponent(jLabel4)
                        .addComponent(btnDeleteWhite, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(btnAddWhite, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(txtWhiteName))
                    .addComponent(jLabel41, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap())
        );
        jPanel6Layout.setVerticalGroup(
            jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel6Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(jScrollPane3, javax.swing.GroupLayout.DEFAULT_SIZE, 405, Short.MAX_VALUE)
                    .addGroup(jPanel6Layout.createSequentialGroup()
                        .addComponent(jLabel4)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(txtWhiteName, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnAddWhite)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jLabel41, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(btnDeleteWhite)))
                .addContainerGap())
        );

        jTabbedPane3.addTab("白名单", jPanel6);

        btnUnban.setText("删除选中");
        btnUnban.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnUnbanActionPerformed(evt);
            }
        });

        jScrollPane2.setViewportView(lstBanned);

        jLabel3.setText("玩家名");

        btnAddBan.setText("添加");
        btnAddBan.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnAddBanActionPerformed(evt);
            }
        });

        jLabel42.setText("<html>\n在没有开服的<br/>\n情况下使用本<br/>\n功能会直接覆<br/>\n盖json文件，<br/>\n这会导致在<br/>\n1.7.10服务端中<br/>\n的设置被抹除<br/>\n，而且不支持<br/>\n正版验证，非<br/>\n1.7.10或更高<br/>\n版本服务端无<br/>\n此问题。\n</html>");

        javax.swing.GroupLayout jPanel7Layout = new javax.swing.GroupLayout(jPanel7);
        jPanel7.setLayout(jPanel7Layout);
        jPanel7Layout.setHorizontalGroup(
            jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel7Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 413, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                        .addComponent(jLabel3)
                        .addComponent(btnUnban, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(btnAddBan, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(txtBanName))
                    .addComponent(jLabel42, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap())
        );
        jPanel7Layout.setVerticalGroup(
            jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel7Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 405, Short.MAX_VALUE)
                    .addGroup(jPanel7Layout.createSequentialGroup()
                        .addComponent(jLabel3)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(txtBanName, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnAddBan)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jLabel42, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(btnUnban)))
                .addContainerGap())
        );

        jTabbedPane3.addTab("黑名单", jPanel7);

        javax.swing.GroupLayout jPanel4Layout = new javax.swing.GroupLayout(jPanel4);
        jPanel4.setLayout(jPanel4Layout);
        jPanel4Layout.setHorizontalGroup(
            jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jTabbedPane3)
        );
        jPanel4Layout.setVerticalGroup(
            jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jTabbedPane3, javax.swing.GroupLayout.Alignment.TRAILING)
        );

        jTabbedPane2.addTab("玩家管理", jPanel4);

        javax.swing.GroupLayout jPanel2Layout = new javax.swing.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jTabbedPane2)
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jTabbedPane2)
        );

        jTabbedPane1.addTab("设置", jPanel2);

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

            public Class getColumnClass(int columnIndex) {
                return types [columnIndex];
            }
        });
        jScrollPane5.setViewportView(lstExternalMods);

        btnManageExtMods.setText("管理");
        btnManageExtMods.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnManageExtModsActionPerformed(evt);
            }
        });

        btnAddExternelMod.setText(C.i18n("mods.add")); // NOI18N
        btnAddExternelMod.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnAddExternelModActionPerformed(evt);
            }
        });

        btnDeleteExternelMod.setText(C.i18n("mods.remove")); // NOI18N
        btnDeleteExternelMod.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnDeleteExternelModActionPerformed(evt);
            }
        });

        btnSaveExtMod.setText("保存");
        btnSaveExtMod.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnSaveExtModActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel9Layout = new javax.swing.GroupLayout(jPanel9);
        jPanel9.setLayout(jPanel9Layout);
        jPanel9Layout.setHorizontalGroup(
            jPanel9Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel9Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jScrollPane5, javax.swing.GroupLayout.DEFAULT_SIZE, 450, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel9Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel9Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addComponent(btnAddExternelMod, javax.swing.GroupLayout.Alignment.TRAILING)
                        .addComponent(btnDeleteExternelMod, javax.swing.GroupLayout.Alignment.TRAILING))
                    .addComponent(btnManageExtMods)
                    .addComponent(btnSaveExtMod, javax.swing.GroupLayout.Alignment.TRAILING))
                .addContainerGap())
        );
        jPanel9Layout.setVerticalGroup(
            jPanel9Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel9Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel9Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel9Layout.createSequentialGroup()
                        .addComponent(jScrollPane5, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
                        .addContainerGap())
                    .addGroup(jPanel9Layout.createSequentialGroup()
                        .addComponent(btnManageExtMods)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnAddExternelMod)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnDeleteExternelMod)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnSaveExtMod)
                        .addGap(0, 317, Short.MAX_VALUE))))
        );

        jTabbedPane4.addTab("Mod管理", jPanel9);

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

            public Class getColumnClass(int columnIndex) {
                return types [columnIndex];
            }
        });
        jScrollPane6.setViewportView(lstCoreMods);

        btnManageCoreMods.setText("管理");
        btnManageCoreMods.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnManageCoreModsActionPerformed(evt);
            }
        });

        btnAddExternelCoreMod.setText(C.i18n("mods.add")); // NOI18N
        btnAddExternelCoreMod.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnAddExternelCoreModActionPerformed(evt);
            }
        });

        btnDeleteExternelCoreMod.setText(C.i18n("mods.remove")); // NOI18N
        btnDeleteExternelCoreMod.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnDeleteExternelCoreModActionPerformed(evt);
            }
        });

        btnSaveCoreMod.setText("保存");
        btnSaveCoreMod.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnSaveCoreModActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel10Layout = new javax.swing.GroupLayout(jPanel10);
        jPanel10.setLayout(jPanel10Layout);
        jPanel10Layout.setHorizontalGroup(
            jPanel10Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel10Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jScrollPane6, javax.swing.GroupLayout.DEFAULT_SIZE, 450, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel10Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(btnSaveCoreMod)
                    .addGroup(jPanel10Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addComponent(btnManageCoreMods)
                        .addComponent(btnAddExternelCoreMod, javax.swing.GroupLayout.Alignment.TRAILING))
                    .addComponent(btnDeleteExternelCoreMod))
                .addContainerGap())
        );
        jPanel10Layout.setVerticalGroup(
            jPanel10Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel10Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel10Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane6, javax.swing.GroupLayout.DEFAULT_SIZE, 435, Short.MAX_VALUE)
                    .addGroup(jPanel10Layout.createSequentialGroup()
                        .addComponent(btnManageCoreMods)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnAddExternelCoreMod)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnDeleteExternelCoreMod)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnSaveCoreMod)
                        .addGap(0, 0, Short.MAX_VALUE)))
                .addContainerGap())
        );

        jTabbedPane4.addTab("核心Mod管理", jPanel10);

        lstPlugins.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {
                "活动", "路径", "名称", "版本", "作者", "描述"
            }
        ) {
            Class[] types = new Class [] {
                java.lang.Boolean.class, java.lang.String.class, java.lang.String.class, java.lang.String.class, java.lang.String.class, java.lang.String.class
            };

            public Class getColumnClass(int columnIndex) {
                return types [columnIndex];
            }
        });
        jScrollPane7.setViewportView(lstPlugins);

        btnManagePlugins.setText("管理");
        btnManagePlugins.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnManagePluginsActionPerformed(evt);
            }
        });

        btnAddPlugins.setText(C.i18n("mods.add")); // NOI18N
        btnAddPlugins.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnAddPluginsActionPerformed(evt);
            }
        });

        btnDeletePlugins.setText(C.i18n("mods.remove")); // NOI18N
        btnDeletePlugins.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnDeletePluginsActionPerformed(evt);
            }
        });

        btnSavePlugins.setText("保存");
        btnSavePlugins.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnSavePluginsActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel11Layout = new javax.swing.GroupLayout(jPanel11);
        jPanel11.setLayout(jPanel11Layout);
        jPanel11Layout.setHorizontalGroup(
            jPanel11Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel11Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jScrollPane7, javax.swing.GroupLayout.DEFAULT_SIZE, 450, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel11Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel11Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addComponent(btnAddPlugins, javax.swing.GroupLayout.Alignment.TRAILING)
                        .addComponent(btnDeletePlugins, javax.swing.GroupLayout.Alignment.TRAILING))
                    .addComponent(btnSavePlugins)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel11Layout.createSequentialGroup()
                        .addComponent(btnManagePlugins)
                        .addContainerGap())))
        );
        jPanel11Layout.setVerticalGroup(
            jPanel11Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel11Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel11Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel11Layout.createSequentialGroup()
                        .addComponent(btnManagePlugins)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnAddPlugins)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnDeletePlugins)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnSavePlugins)
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addComponent(jScrollPane7, javax.swing.GroupLayout.DEFAULT_SIZE, 435, Short.MAX_VALUE))
                .addContainerGap())
        );

        jTabbedPane4.addTab("插件管理", jPanel11);

        javax.swing.GroupLayout jPanel8Layout = new javax.swing.GroupLayout(jPanel8);
        jPanel8.setLayout(jPanel8Layout);
        jPanel8Layout.setHorizontalGroup(
            jPanel8Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jTabbedPane4)
        );
        jPanel8Layout.setVerticalGroup(
            jPanel8Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jTabbedPane4)
        );

        jTabbedPane1.addTab("模组管理", jPanel8);

        jLabel21.setText("新建计划任务");

        cboTimerTask.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "自动保存", "自动重启", "自动备份", "自动广播", "自动发送命令" }));

        jLabel22.setText("间隔时间（分钟）");

        btnNewTask.setText("新建计划任务");
        btnNewTask.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnNewTaskActionPerformed(evt);
            }
        });

        jLabel13.setText("内容");

        jLabel28.setText("时间类型");

        cboTimeType.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "每x分钟", "整点过去x分钟", "服务器启动", "服务器关闭", "服务器崩溃" }));

        btnDelSelectedSchedule.setText("删除选中");
        btnDelSelectedSchedule.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnDelSelectedScheduleActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel14Layout = new javax.swing.GroupLayout(jPanel14);
        jPanel14.setLayout(jPanel14Layout);
        jPanel14Layout.setHorizontalGroup(
            jPanel14Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel14Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel14Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel14Layout.createSequentialGroup()
                        .addGroup(jPanel14Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(cboTimerTask, javax.swing.GroupLayout.PREFERRED_SIZE, 95, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(jLabel21))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel14Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel28)
                            .addComponent(cboTimeType, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addGroup(jPanel14Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(txtTimerTaskPeriod, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 96, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(jLabel22, javax.swing.GroupLayout.Alignment.TRAILING)))
                    .addComponent(jLabel13)
                    .addComponent(txtTimerTaskContent, javax.swing.GroupLayout.PREFERRED_SIZE, 197, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGroup(jPanel14Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel14Layout.createSequentialGroup()
                        .addGap(73, 73, 73)
                        .addComponent(btnNewTask))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel14Layout.createSequentialGroup()
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnDelSelectedSchedule)))
                .addContainerGap())
        );
        jPanel14Layout.setVerticalGroup(
            jPanel14Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel14Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel14Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel14Layout.createSequentialGroup()
                        .addGroup(jPanel14Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(jLabel21)
                            .addComponent(jLabel22)
                            .addComponent(jLabel28))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel14Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(cboTimerTask, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(txtTimerTaskPeriod, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(cboTimeType, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jLabel13)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(txtTimerTaskContent, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(jPanel14Layout.createSequentialGroup()
                        .addComponent(btnNewTask)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnDelSelectedSchedule)))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        lstSchedules.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {
                "类型", "时间类型", "间隔时间（分钟）", "内容"
            }
        ) {
            Class[] types = new Class [] {
                java.lang.String.class, java.lang.String.class, java.lang.Double.class, java.lang.String.class
            };
            boolean[] canEdit = new boolean [] {
                true, false, false, true
            };

            public Class getColumnClass(int columnIndex) {
                return types [columnIndex];
            }

            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return canEdit [columnIndex];
            }
        });
        jScrollPane9.setViewportView(lstSchedules);

        javax.swing.GroupLayout jPanel13Layout = new javax.swing.GroupLayout(jPanel13);
        jPanel13.setLayout(jPanel13Layout);
        jPanel13Layout.setHorizontalGroup(
            jPanel13Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jPanel14, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addComponent(jScrollPane9)
        );
        jPanel13Layout.setVerticalGroup(
            jPanel13Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel13Layout.createSequentialGroup()
                .addComponent(jScrollPane9, javax.swing.GroupLayout.DEFAULT_SIZE, 356, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanel14, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
        );

        jTabbedPane1.addTab("计划任务", jPanel13);

        jLabel23.setText("新建备份");

        cboBackupTypes.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "地图备份", "插件备份" }));

        btnBackup.setText("备份");
        btnBackup.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnBackupActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel17Layout = new javax.swing.GroupLayout(jPanel17);
        jPanel17.setLayout(jPanel17Layout);
        jPanel17Layout.setHorizontalGroup(
            jPanel17Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel17Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel17Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel17Layout.createSequentialGroup()
                        .addComponent(jLabel23)
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addComponent(cboBackupTypes, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel17Layout.createSequentialGroup()
                        .addGap(0, 0, Short.MAX_VALUE)
                        .addComponent(btnBackup)))
                .addContainerGap())
        );
        jPanel17Layout.setVerticalGroup(
            jPanel17Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel17Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel23)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(cboBackupTypes, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(btnBackup)
                .addContainerGap())
        );

        jButton1.setText("刷新备份列表");
        jButton1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton1ActionPerformed(evt);
            }
        });

        btnDeleteBackup.setText("删除选中备份");
        btnDeleteBackup.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnDeleteBackupActionPerformed(evt);
            }
        });

        btnRestoreBackup.setText("恢复选中备份");
        btnRestoreBackup.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnRestoreBackupActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel16Layout = new javax.swing.GroupLayout(jPanel16);
        jPanel16.setLayout(jPanel16Layout);
        jPanel16Layout.setHorizontalGroup(
            jPanel16Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jPanel17, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addGroup(jPanel16Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel16Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jButton1)
                    .addComponent(btnDeleteBackup)
                    .addComponent(btnRestoreBackup))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        jPanel16Layout.setVerticalGroup(
            jPanel16Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel16Layout.createSequentialGroup()
                .addComponent(jPanel17, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 272, Short.MAX_VALUE)
                .addComponent(btnRestoreBackup)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(btnDeleteBackup)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jButton1)
                .addContainerGap())
        );

        lstBackups.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {
                "类型", "时间", "名称"
            }
        ) {
            Class[] types = new Class [] {
                java.lang.String.class, java.lang.String.class, java.lang.String.class
            };

            public Class getColumnClass(int columnIndex) {
                return types [columnIndex];
            }
        });
        jScrollPane11.setViewportView(lstBackups);

        javax.swing.GroupLayout jPanel15Layout = new javax.swing.GroupLayout(jPanel15);
        jPanel15.setLayout(jPanel15Layout);
        jPanel15Layout.setHorizontalGroup(
            jPanel15Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel15Layout.createSequentialGroup()
                .addComponent(jPanel16, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane11, javax.swing.GroupLayout.DEFAULT_SIZE, 391, Short.MAX_VALUE)
                .addContainerGap())
        );
        jPanel15Layout.setVerticalGroup(
            jPanel15Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jPanel16, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel15Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jScrollPane11, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
                .addContainerGap())
        );

        jTabbedPane1.addTab("备份", jPanel15);

        lstWorlds.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {
                "地图名", "路径", "是否允许备份"
            }
        ) {
            Class[] types = new Class [] {
                java.lang.String.class, java.lang.String.class, java.lang.Boolean.class
            };

            public Class getColumnClass(int columnIndex) {
                return types [columnIndex];
            }
        });
        jScrollPane10.setViewportView(lstWorlds);

        btnRefreshWorlds.setText("刷新");
        btnRefreshWorlds.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnRefreshWorldsActionPerformed(evt);
            }
        });

        btnSaveWorld.setText("保存修改");
        btnSaveWorld.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnSaveWorldActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel18Layout = new javax.swing.GroupLayout(jPanel18);
        jPanel18.setLayout(jPanel18Layout);
        jPanel18Layout.setHorizontalGroup(
            jPanel18Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel18Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel18Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane10, javax.swing.GroupLayout.DEFAULT_SIZE, 521, Short.MAX_VALUE)
                    .addGroup(jPanel18Layout.createSequentialGroup()
                        .addComponent(btnRefreshWorlds)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(btnSaveWorld)))
                .addContainerGap())
        );
        jPanel18Layout.setVerticalGroup(
            jPanel18Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel18Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jScrollPane10, javax.swing.GroupLayout.DEFAULT_SIZE, 431, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel18Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(btnRefreshWorlds)
                    .addComponent(btnSaveWorld))
                .addContainerGap())
        );

        jTabbedPane1.addTab("地图管理", jPanel18);

        lstCraftbukkit.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {
                "编译号", "版本"
            }
        ) {
            Class[] types = new Class [] {
                java.lang.String.class, java.lang.String.class
            };

            public Class getColumnClass(int columnIndex) {
                return types [columnIndex];
            }
        });
        lstCraftbukkit.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        jScrollPane18.setViewportView(lstCraftbukkit);

        btnDownloadCraftbukkit.setText(C.i18n("download")); // NOI18N
        btnDownloadCraftbukkit.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnDownloadCraftbukkitActionPerformed(evt);
            }
        });

        lstRefreshCraftbukkit.setText(C.i18n("ui.button.refresh")); // NOI18N
        lstRefreshCraftbukkit.setToolTipText("");
        lstRefreshCraftbukkit.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                lstRefreshCraftbukkitActionPerformed(evt);
            }
        });

        cboBukkitType.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "稳定版", "测试版", "最新开发版" }));
        cboBukkitType.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                cboBukkitTypeItemStateChanged(evt);
            }
        });

        jLabel39.setText("Bukkit类型");

        javax.swing.GroupLayout jPanel20Layout = new javax.swing.GroupLayout(jPanel20);
        jPanel20.setLayout(jPanel20Layout);
        jPanel20Layout.setHorizontalGroup(
            jPanel20Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel20Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel20Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel20Layout.createSequentialGroup()
                        .addComponent(jLabel39)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(cboBukkitType, 0, 442, Short.MAX_VALUE))
                    .addGroup(jPanel20Layout.createSequentialGroup()
                        .addComponent(jScrollPane18, javax.swing.GroupLayout.DEFAULT_SIZE, 450, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel20Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(btnDownloadCraftbukkit, javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(lstRefreshCraftbukkit, javax.swing.GroupLayout.Alignment.TRAILING))))
                .addContainerGap())
        );
        jPanel20Layout.setVerticalGroup(
            jPanel20Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel20Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel20Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel39)
                    .addComponent(cboBukkitType, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel20Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel20Layout.createSequentialGroup()
                        .addComponent(btnDownloadCraftbukkit)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(lstRefreshCraftbukkit)
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addComponent(jScrollPane18, javax.swing.GroupLayout.DEFAULT_SIZE, 402, Short.MAX_VALUE))
                .addContainerGap())
        );

        jTabbedPane5.addTab("CraftBukkit", jPanel20);

        lstMCPC.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {
                "MC版本", "Cauldron版本", "释放时间"
            }
        ) {
            Class[] types = new Class [] {
                java.lang.String.class, java.lang.String.class, java.lang.String.class
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
        lstMCPC.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        jScrollPane19.setViewportView(lstMCPC);

        btnDownloadMCPC.setText(C.i18n("download")); // NOI18N
        btnDownloadMCPC.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnDownloadMCPCActionPerformed(evt);
            }
        });

        lstRefreshMCPC.setText(C.i18n("ui.button.refresh")); // NOI18N
        lstRefreshMCPC.setToolTipText("");
        lstRefreshMCPC.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                lstRefreshMCPCActionPerformed(evt);
            }
        });

        jLabel38.setText("Minecraft版本");

        cboCauldronMinecraft.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                cboCauldronMinecraftItemStateChanged(evt);
            }
        });

        btnInstallMCPC.setText(C.i18n("ui.button.retry")); // NOI18N
        btnInstallMCPC.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnInstallMCPCActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel30Layout = new javax.swing.GroupLayout(jPanel30);
        jPanel30.setLayout(jPanel30Layout);
        jPanel30Layout.setHorizontalGroup(
            jPanel30Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel30Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel30Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel30Layout.createSequentialGroup()
                        .addComponent(jScrollPane19, javax.swing.GroupLayout.DEFAULT_SIZE, 450, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel30Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(jPanel30Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                .addComponent(lstRefreshMCPC)
                                .addComponent(btnInstallMCPC, javax.swing.GroupLayout.Alignment.TRAILING))
                            .addComponent(btnDownloadMCPC, javax.swing.GroupLayout.Alignment.TRAILING)))
                    .addGroup(jPanel30Layout.createSequentialGroup()
                        .addComponent(jLabel38)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(cboCauldronMinecraft, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                .addContainerGap())
        );
        jPanel30Layout.setVerticalGroup(
            jPanel30Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel30Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel30Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel38)
                    .addComponent(cboCauldronMinecraft, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel30Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane19, javax.swing.GroupLayout.DEFAULT_SIZE, 402, Short.MAX_VALUE)
                    .addGroup(jPanel30Layout.createSequentialGroup()
                        .addComponent(lstRefreshMCPC)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnDownloadMCPC)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnInstallMCPC)
                        .addGap(0, 0, Short.MAX_VALUE)))
                .addContainerGap())
        );

        jTabbedPane5.addTab("Cauldron", jPanel30);

        btnRefreshDownloads.setText(C.i18n("ui.button.refresh")); // NOI18N
        btnRefreshDownloads.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnRefreshDownloadsActionPerformed(evt);
            }
        });

        lstDownloads.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {
                "版本", "发布时间", "释放时间", "类型"
            }
        ) {
            Class[] types = new Class [] {
                java.lang.String.class, java.lang.String.class, java.lang.String.class, java.lang.String.class
            };
            boolean[] canEdit = new boolean [] {
                false, false, false, false
            };

            public Class getColumnClass(int columnIndex) {
                return types [columnIndex];
            }

            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return canEdit [columnIndex];
            }
        });
        lstDownloads.setToolTipText("");
        jScrollPane12.setViewportView(lstDownloads);

        btnMinecraftServerDownload.setText(C.i18n("download")); // NOI18N
        btnMinecraftServerDownload.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnMinecraftServerDownloadActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel21Layout = new javax.swing.GroupLayout(jPanel21);
        jPanel21.setLayout(jPanel21Layout);
        jPanel21Layout.setHorizontalGroup(
            jPanel21Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel21Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jScrollPane12, javax.swing.GroupLayout.DEFAULT_SIZE, 501, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel21Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(btnRefreshDownloads, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(btnMinecraftServerDownload, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap())
        );
        jPanel21Layout.setVerticalGroup(
            jPanel21Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel21Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel21Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane12, javax.swing.GroupLayout.DEFAULT_SIZE, 435, Short.MAX_VALUE)
                    .addGroup(jPanel21Layout.createSequentialGroup()
                        .addComponent(btnRefreshDownloads)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnMinecraftServerDownload)
                        .addGap(0, 0, Short.MAX_VALUE)))
                .addContainerGap())
        );

        jTabbedPane5.addTab("官方服务器", jPanel21);

        lstBukkitPlugins.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {
                "名字", "描述", "版本", "Bukkit版本"
            }
        ) {
            Class[] types = new Class [] {
                java.lang.String.class, java.lang.String.class, java.lang.String.class, java.lang.String.class
            };
            boolean[] canEdit = new boolean [] {
                false, false, false, false
            };

            public Class getColumnClass(int columnIndex) {
                return types [columnIndex];
            }

            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return canEdit [columnIndex];
            }
        });
        jScrollPane15.setViewportView(lstBukkitPlugins);

        cboCategory.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                cboCategoryItemStateChanged(evt);
            }
        });

        jLabel24.setText("分类");

        btnShowPluginInfo.setText("选中插件信息");
        btnShowPluginInfo.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnShowPluginInfoActionPerformed(evt);
            }
        });

        jButton11.setText(C.i18n("ui.button.refresh")); // NOI18N
        jButton11.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton11ActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel23Layout = new javax.swing.GroupLayout(jPanel23);
        jPanel23.setLayout(jPanel23Layout);
        jPanel23Layout.setHorizontalGroup(
            jPanel23Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel23Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel23Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane15, javax.swing.GroupLayout.DEFAULT_SIZE, 516, Short.MAX_VALUE)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel23Layout.createSequentialGroup()
                        .addComponent(btnShowPluginInfo)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jButton11)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(jLabel24)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(cboCategory, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap())
        );
        jPanel23Layout.setVerticalGroup(
            jPanel23Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel23Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel23Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(cboCategory, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel24)
                    .addComponent(btnShowPluginInfo)
                    .addComponent(jButton11))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane15, javax.swing.GroupLayout.DEFAULT_SIZE, 401, Short.MAX_VALUE)
                .addContainerGap())
        );

        jTabbedPane5.addTab("服务器插件", jPanel23);

        javax.swing.GroupLayout jPanel19Layout = new javax.swing.GroupLayout(jPanel19);
        jPanel19.setLayout(jPanel19Layout);
        jPanel19Layout.setHorizontalGroup(
            jPanel19Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jTabbedPane5, javax.swing.GroupLayout.Alignment.TRAILING)
        );
        jPanel19Layout.setVerticalGroup(
            jPanel19Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jTabbedPane5)
        );

        jTabbedPane5.getAccessibleContext().setAccessibleName("CraftBukkit");

        jTabbedPane1.addTab("下载中心", jPanel19);

        lstInfos.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {

            },
            new String [] {
                "名字", "时间"
            }
        ) {
            Class[] types = new Class [] {
                java.lang.String.class, java.lang.String.class
            };
            boolean[] canEdit = new boolean [] {
                false, false
            };

            public Class getColumnClass(int columnIndex) {
                return types [columnIndex];
            }

            public boolean isCellEditable(int rowIndex, int columnIndex) {
                return canEdit [columnIndex];
            }
        });
        jScrollPane13.setViewportView(lstInfos);

        btnRefreshInfos.setText("刷新");
        btnRefreshInfos.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnRefreshInfosActionPerformed(evt);
            }
        });

        txtInfo.setEditable(false);
        txtInfo.setColumns(20);
        txtInfo.setRows(5);
        jScrollPane14.setViewportView(txtInfo);

        btnShowInfo.setText("显示");
        btnShowInfo.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnShowInfoActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel22Layout = new javax.swing.GroupLayout(jPanel22);
        jPanel22.setLayout(jPanel22Layout);
        jPanel22Layout.setHorizontalGroup(
            jPanel22Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel22Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel22Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addGroup(jPanel22Layout.createSequentialGroup()
                        .addComponent(btnRefreshInfos)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(btnShowInfo))
                    .addComponent(jScrollPane13, javax.swing.GroupLayout.PREFERRED_SIZE, 141, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane14, javax.swing.GroupLayout.DEFAULT_SIZE, 374, Short.MAX_VALUE)
                .addContainerGap())
        );
        jPanel22Layout.setVerticalGroup(
            jPanel22Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel22Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel22Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane14, javax.swing.GroupLayout.DEFAULT_SIZE, 465, Short.MAX_VALUE)
                    .addGroup(jPanel22Layout.createSequentialGroup()
                        .addComponent(jScrollPane13, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel22Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(btnRefreshInfos)
                            .addComponent(btnShowInfo))))
                .addContainerGap())
        );

        jTabbedPane1.addTab("信息记录", jPanel22);

        btnRefreshReports.setText("刷新");
        btnRefreshReports.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnRefreshReportsActionPerformed(evt);
            }
        });

        txtCrashReport.setEditable(false);
        txtCrashReport.setColumns(20);
        txtCrashReport.setRows(5);
        jScrollPane17.setViewportView(txtCrashReport);

        btnShowReport.setText("显示");
        btnShowReport.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnShowReportActionPerformed(evt);
            }
        });

        jScrollPane8.setViewportView(lstReports);

        javax.swing.GroupLayout jPanel26Layout = new javax.swing.GroupLayout(jPanel26);
        jPanel26.setLayout(jPanel26Layout);
        jPanel26Layout.setHorizontalGroup(
            jPanel26Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel26Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel26Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addGroup(jPanel26Layout.createSequentialGroup()
                        .addComponent(btnRefreshReports)
                        .addGap(27, 27, 27)
                        .addComponent(btnShowReport))
                    .addComponent(jScrollPane8, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane17, javax.swing.GroupLayout.DEFAULT_SIZE, 368, Short.MAX_VALUE)
                .addContainerGap())
        );
        jPanel26Layout.setVerticalGroup(
            jPanel26Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel26Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel26Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane17, javax.swing.GroupLayout.DEFAULT_SIZE, 465, Short.MAX_VALUE)
                    .addGroup(jPanel26Layout.createSequentialGroup()
                        .addComponent(jScrollPane8)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel26Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                            .addComponent(btnRefreshReports)
                            .addComponent(btnShowReport))))
                .addContainerGap())
        );

        jTabbedPane1.addTab("崩溃记录", jPanel26);

        lblPlayers.setText("在线人数");

        btnRefreshPlayers.setText("刷新在线人数");
        btnRefreshPlayers.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnRefreshPlayersActionPerformed(evt);
            }
        });

        jScrollPane16.setViewportView(lstPlayers);

        jLabel27.setText("此列表每一分钟刷新一次");

        javax.swing.GroupLayout jPanel27Layout = new javax.swing.GroupLayout(jPanel27);
        jPanel27.setLayout(jPanel27Layout);
        jPanel27Layout.setHorizontalGroup(
            jPanel27Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel27Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel27Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane16, javax.swing.GroupLayout.DEFAULT_SIZE, 521, Short.MAX_VALUE)
                    .addGroup(jPanel27Layout.createSequentialGroup()
                        .addComponent(lblPlayers)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(btnRefreshPlayers))
                    .addGroup(jPanel27Layout.createSequentialGroup()
                        .addComponent(jLabel27)
                        .addGap(0, 0, Short.MAX_VALUE)))
                .addContainerGap())
        );
        jPanel27Layout.setVerticalGroup(
            jPanel27Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel27Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel27Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lblPlayers)
                    .addComponent(btnRefreshPlayers))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane16, javax.swing.GroupLayout.DEFAULT_SIZE, 407, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jLabel27)
                .addContainerGap())
        );

        jTabbedPane1.addTab("在线玩家", jPanel27);

        jButton2.setText("花生壳6.5工程版(无需公网IP，无需路由端口映射)");
        jButton2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton2ActionPerformed(evt);
            }
        });

        jButton4.setText("mcbbs");
        jButton4.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton4ActionPerformed(evt);
            }
        });

        jButton5.setText("minecraft贴吧");
        jButton5.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton5ActionPerformed(evt);
            }
        });

        jButton6.setText("mcbbs发布帖");
        jButton6.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton6ActionPerformed(evt);
            }
        });

        jButton7.setText("minecraft官网");
        jButton7.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton7ActionPerformed(evt);
            }
        });

        jButton8.setText("craftbukkit官网");
        jButton8.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton8ActionPerformed(evt);
            }
        });

        jButton9.setText("MCPC+下载");
        jButton9.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton9ActionPerformed(evt);
            }
        });

        jButton10.setText("指令大全");
        jButton10.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                jButton10ActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel28Layout = new javax.swing.GroupLayout(jPanel28);
        jPanel28.setLayout(jPanel28Layout);
        jPanel28Layout.setHorizontalGroup(
            jPanel28Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel28Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel28Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jButton2)
                    .addGroup(jPanel28Layout.createSequentialGroup()
                        .addComponent(jButton4)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jButton5)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jButton6)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jButton7))
                    .addGroup(jPanel28Layout.createSequentialGroup()
                        .addComponent(jButton8)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jButton9))
                    .addComponent(jButton10))
                .addContainerGap(100, Short.MAX_VALUE))
        );
        jPanel28Layout.setVerticalGroup(
            jPanel28Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel28Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jButton2)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jButton10)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 341, Short.MAX_VALUE)
                .addGroup(jPanel28Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jButton8)
                    .addComponent(jButton9))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel28Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jButton4)
                    .addComponent(jButton5)
                    .addComponent(jButton6)
                    .addComponent(jButton7))
                .addContainerGap())
        );

        jTabbedPane1.addTab("应用中心", jPanel28);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jTabbedPane1)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jTabbedPane1)
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    void loadFromSettings() {
        txtJavaArgs.setText(SettingsManager.settings.javaArgs);
        txtJavaDir.setText(SettingsManager.settings.javaDir);
        txtMaxMemory.setText(SettingsManager.settings.maxMemory);
    }

    void loadFromServerProperties() {
        ServerProperties sp = ServerProperties.getInstance();
        if (sp == null)
            return;
        txtServerPort.setValue(sp.getPropertyInt("server-port", 25565));
        txtServerName.setText(sp.getProperty("server-name"));
        cboGameMode.setSelectedIndex(sp.getPropertyInt("gamemode", 0));
        cboDifficulty.setSelectedIndex(sp.getPropertyInt("difficulty", 1));
        String wt = sp.getProperty("level-type");
        switch (wt) {
            case "LARGEBIOMES":
                cboWorldType.setSelectedIndex(2);
                break;
            case "FLAT":
                cboWorldType.setSelectedIndex(1);
                break;
            case "DEFAULT":
                cboWorldType.setSelectedIndex(0);
                break;
            default:
                cboWorldType.setSelectedIndex(0);
                break;
        }
        txtMaxPlayer.setValue(sp.getPropertyInt("max-players", 20));
        chkAllowFlight.setSelected(sp.getPropertyBoolean("allow-flight", false));
        chkAllowNether.setSelected(sp.getPropertyBoolean("allow-nether", true));
        chkEnableMonsters.setSelected(sp.getPropertyBoolean("spawn-monsters", true));
        chkEnableNPCs.setSelected(sp.getPropertyBoolean("spawn-npcs", true));
        chkEnalbleAnimals.setSelected(sp.getPropertyBoolean("spawn-animals", true));
        chkPVP.setSelected(sp.getPropertyBoolean("pvp", true));
        chkWhiteList.setSelected(sp.getPropertyBoolean("white-list", false));
        chkGenerateStructures.setSelected(sp.getPropertyBoolean("generate-structures", true));
        chkOnlineMode.setSelected(sp.getPropertyBoolean("online-mode", false));
        txtViewDistance.setValue(sp.getPropertyInt("view-distance", 10));
        txtMaxBuildHeight.setValue(sp.getPropertyInt("max-build-height", 256));
        txtServerGeneratorSettings.setText(sp.getProperty("generator-settings"));
        txtWorldSeed.setText(sp.getProperty("level-seed"));
        txtWorldName.setText(sp.getProperty("level-name"));
    }

    void loadFromOPs() {
        File mainjar = new File(SettingsManager.settings.mainjar);
        if (!mainjar.exists())
            return;
        File folder = mainjar.getParentFile();
        op = new Op();
        op.initByBoth(new File(folder, "ops.txt"), new File(folder, "ops.json"));
        for (Op.Operator ss : op.op)
            lstOPModel.addElement(ss.name);
        lstOP.setModel(lstOPModel);
    }

    void loadFromWhiteList() {
        File mainjar = new File(SettingsManager.settings.mainjar);
        if (!mainjar.exists())
            return;
        File folder = mainjar.getParentFile();
        whitelist = new WhiteList();
        whitelist.initByBoth(new File(folder, "white-list.txt"), new File(folder, "white-list.json"));
        for (WhiteList.WhiteListPlayer ss : whitelist.op)
            lstWhiteListModel.addElement(ss.name);
        lstWhiteList.setModel(lstWhiteListModel);
    }

    void loadFromBannedPlayers() {
        File mainjar = new File(SettingsManager.settings.mainjar);
        if (!mainjar.exists())
            return;
        File folder = mainjar.getParentFile();
        banned = new BannedPlayers();
        banned.initByBoth(new File(folder, "banned-players.txt"), new File(folder, "banned-players.json"));
        for (BannedPlayers.BannedPlayer ss : banned.op)
            lstBannedModel.addElement(ss.name);
        lstBanned.setModel(lstBannedModel);
    }

    void loadLocalMods() {
        String path = Utilities.getPath("mods");
        if (path == null)
            return;
        DefaultTableModel model = (DefaultTableModel) lstExternalMods.getModel();
        while (model.getRowCount() > 0)
            model.removeRow(0);
        IOUtils.findAllFile(new File(path), s
                -> model.addRow(new Object[] { !SettingsManager.settings.inactiveExtMods.contains(s), s, ModType.getModTypeShowName(ModType.getModType(IOUtils.addSeparator(path) + s)) })
        );
        lstExternalMods.updateUI();
    }

    void loadLocalPlugins() {
        String path = Utilities.getPath("plugins");
        if (path == null)
            return;
        DefaultTableModel model = (DefaultTableModel) lstPlugins.getModel();
        while (model.getRowCount() > 0)
            model.removeRow(0);
        IOUtils.findAllFile(new File(path), s -> {
            PluginInformation p = PluginManager.getPluginYML(new File(Utilities.getGameDir() + "plugins" + File.separator + s));
            if (p == null)
                model.addRow(new Object[] { !SettingsManager.settings.inactivePlugins.contains(s), s,
                    "", "", "", "" });
            else
                model.addRow(new Object[] { !SettingsManager.settings.inactivePlugins.contains(s), s,
                    p.name, p.version, p.author, p.description });
        });

        lstPlugins.updateUI();
    }

    void loadLocalCoreMods() {
        String path = Utilities.getPath("coremods");
        if (path == null)
            return;
        DefaultTableModel model = (DefaultTableModel) lstCoreMods.getModel();
        while (model.getRowCount() > 0)
            model.removeRow(0);
        IOUtils.findAllFile(new File(path), s
                -> model.addRow(new Object[] { !SettingsManager.settings.inactiveCoreMods.contains(s), s, ModType.getModTypeShowName(ModType.getModType(IOUtils.addSeparator(path) + s)) }));

        lstCoreMods.updateUI();
    }

    void loadWorlds() {
        DefaultTableModel model = (DefaultTableModel) lstWorlds.getModel();
        if (SettingsManager.settings.inactiveWorlds == null)
            SettingsManager.settings.inactiveWorlds = new ArrayList<>();
        BackupManager.findAllWorlds(world -> {
            model.addRow(new Object[] {
                world, Utilities.getGameDir() + world, !SettingsManager.settings.inactiveWorlds.contains(world)
            });
        });
        lstWorlds.updateUI();
    }

    void loadBackups() {
        DefaultTableModel model = (DefaultTableModel) lstBackups.getModel();
        BackupManager.getBackupList(backup -> {
            String[] names = FileUtils.getExtension(backup).split("\\+");
            model.addRow(new Object[] {
                names[0], names[1], names[2]
            });
        });
        lstBackups.updateUI();
    }

    void loadSchedules() {
        if (SettingsManager.settings.schedules == null)
            SettingsManager.settings.schedules = new ArrayList<>();
        DefaultTableModel model = (DefaultTableModel) lstSchedules.getModel();
        for (Schedule s : SettingsManager.settings.schedules)
            model.addRow(ScheduleTranslator.getRow(s));
        lstSchedules.updateUI();
    }

    void clearListDownloads() {
        SwingUtils.clearDefaultTable(lstDownloads);
    }

    void loadBukkits() {
        int idx = cboBukkitType.getSelectedIndex();
        if (idx == -1)
            return;
        BukkitFormatThread thread;
        switch (idx) {
            case 1:
                thread = new BukkitFormatThread(
                        "http://dl.bukkit.org/downloads/craftbukkit/list/beta/", value -> {
                            craftBukkitBeta = value;
                            reloadBukkitList();
                        });
                thread.start();
                break;
            case 0:
                thread = new BukkitFormatThread(
                        "http://dl.bukkit.org/downloads/craftbukkit/list/rb/", value -> {
                            craftBukkitRecommended = value;
                            reloadBukkitList();
                        });
                thread.start();
                break;
            case 2:
                thread = new BukkitFormatThread(
                        "http://dl.bukkit.org/downloads/craftbukkit/list/dev/", value -> {
                            craftBukkitDev = value;
                            reloadBukkitList();
                        });
                thread.start();
                break;
            default:
                break;
        }
    }

    void loadMCPCs() {
        ForgeFormatThread thread = new ForgeFormatThread(value -> {
            mcpcPackages = value;
            reloadMCPCList();
        });
        thread.start();
    }

    public void reloadMCPCList() {
        if (mcpcPackages == null)
            return;
        int cnt = cboCauldronMinecraft.getItemCount();
        cboCauldronMinecraft.removeAllItems();
        for (String s : mcpcPackages.keySet())
            cboCauldronMinecraft.addItem(s);

        String mcver = (String) cboCauldronMinecraft.getSelectedItem();
        useMCPCVersions(mcver);
    }

    public void useMCPCVersions(String ver) {
        DefaultTableModel model = (DefaultTableModel) lstMCPC.getModel();
        while (model.getRowCount() > 0)
            model.removeRow(0);
        for (ForgeVersion v : mcpcPackages.get(ver)) {
            Object[] row = new Object[] {
                v.mcver, v.ver, v.releasetime
            };
            model.addRow(row);
        }
        lstMCPC.updateUI();
    }

    public void reloadBukkitList() {
        int idx = cboBukkitType.getSelectedIndex();
        if (idx == -1)
            return;
        switch (idx) {
            case 1:
                useBukkitVersions(craftBukkitBeta);
                break;
            case 0:
                useBukkitVersions(craftBukkitRecommended);
                break;
            case 2:
                useBukkitVersions(craftBukkitDev);
                break;
            default:
                break;
        }
    }

    public void useBukkitVersions(List<BukkitVersion> list) {
        if (list == null)
            return;
        DefaultTableModel model = (DefaultTableModel) lstCraftbukkit.getModel();
        while (model.getRowCount() > 0)
            model.removeRow(0);
        for (BukkitVersion v : list) {
            Object[] row = new Object[] {
                v.buildNumber, v.version
            };
            model.addRow(row);
        }
        lstCraftbukkit.updateUI();
    }

    class RefreshDownloadsDone extends Task {

        HTTPGetTask task;

        RefreshDownloadsDone() {
            task = new HTTPGetTask("https://s3.amazonaws.com/Minecraft.Download/versions/versions.json");
        }

        @Override
        public Collection<Task> getDependTasks() {
            return Arrays.<Task>asList(task);
        }

        @Override
        public void executeTask(boolean areDependTasksSucceeded) {
            javax.swing.JTable table = MainWindow.this.lstDownloads;
            DefaultTableModel model = (DefaultTableModel) table.getModel();

            MinecraftRemoteVersions v = C.GSON.fromJson(task.getResult(), MinecraftRemoteVersions.class);
            for (MinecraftRemoteVersion ver : v.versions) {
                String[] line = new String[4];
                line[0] = ver.id;
                line[1] = ver.releaseTime;
                line[2] = ver.time;
                line[3] = ver.type;
                model.addRow(line);
            }
        }

        @Override
        public String getInfo() {
            return "Get Version List";
        }

    }

    void refreshDownloads() {
        clearListDownloads();
        TaskWindow.factory().append(new RefreshDownloadsDone()).execute();
    }

    void refreshInfos() {
        DefaultTableModel model = (DefaultTableModel) lstInfos.getModel();
        while (model.getRowCount() > 0)
            model.removeRow(0);
        IOUtils.findAllFile(new File(Utilities.getGameDir() + "infos-HMCSM"), s -> model.addRow(new Object[] { s, FileUtils.getExtension(s) }));
        lstInfos.updateUI();
    }

    void refreshReports() {
        lstCrashReportsModel.clear();
        IOUtils.findAllFile(new File(Utilities.getGameDir() + "crash-reports"), s -> lstCrashReportsModel.addElement(s));
        lstReports.setModel(lstCrashReportsModel);
    }

    void getIP() {
        IPGet get = new IPGet();
        get.dl = a -> lblIPAddress.setText("IP: " + a);
        get.start();
    }

    void loadBukkitPlugins() {
        final DefaultTableModel model = (DefaultTableModel) lstBukkitPlugins.getModel();
        while (model.getRowCount() > 0)
            model.removeRow(0);
        lstBukkitPlugins.updateUI();
        Thread t = new Thread() {
            @Override
            public void run() {
                try {
                    List<BukkitPlugin> l;
                    if (cboCategory.getSelectedIndex() == 0)
                        l = PluginManager.getPlugins();
                    else
                        l = PluginManager.getPluginsByCategory(cboCategory.getSelectedItem().toString());
                    plugins = l;
                    for (BukkitPlugin p : l)
                        model.addRow(new Object[] {
                            p.plugin_name, p.description, p.getLatestVersion(), p.getLatestBukkit()
                        });
                    lstBukkitPlugins.updateUI();
                } catch (Exception ex) {
                    HMCLog.warn("Failed to get plugins", ex);
                }
            }
        };
        t.start();
    }

    void loadBukkitCategory() {
        Thread t = new Thread() {
            @Override
            public void run() {
                try {
                    List<Category> l = PluginManager.getCategories();
                    cboCategory.removeAllItems();
                    cboCategory.addItem("所有");
                    for (Category c : l)
                        cboCategory.addItem(c.name);
                } catch (Exception ex) {
                    HMCLog.warn("Failed to load bukkit categories.");
                }
            }
        };
        t.start();
    }

    class MyTask extends TimerTask {

        @Override
        public void run() {
            Server s = Server.getInstance();
            if (s != null && !s.isRunning) {
                System.out.println("AutoSave world");
                s.sendCommand("save-all");
            }
        }

    }

    void loadPlayers() {
        Server s = Server.getInstance();
        if (s != null && s.isRunning)
            s.getPlayerNumber(t -> {
                lblPlayers.setText("在线人数" + t.key);
                lstPlayersModel.clear();
                for (String s1 : t.value)
                    lstPlayersModel.addElement(s1);
                lstPlayers.setModel(lstPlayersModel);
            });
        else
            MessageBox.show("服务器未开启！");
    }

    class ServerBeginListener implements Runnable {

        @Override
        public void run() {
            commandSet = new ArrayList<>();
            txtMain.setText("");
            btnLaunch.setEnabled(false);
            btnStop.setEnabled(true);
            btnShutdown.setEnabled(true);
            btnCommand.setEnabled(true);
        }

    }

    class ServerDoneListener implements Runnable {

        @Override
        public void run() {
            getPlayerNumberTimer = new Timer();
            getPlayerNumberTimer.schedule(new TimerTask() {

                @Override
                public void run() {
                    loadPlayers();
                }
            }, 1000 * 60 * 10, 1000 * 60 * 10);
        }

    }

    private void btnLaunchActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnLaunchActionPerformed
        File eula = new File(new File(SettingsManager.settings.mainjar).getParentFile(), "eula.txt");
        if (!eula.exists()) {
            int option = JOptionPane.showConfirmDialog(null, "您是否确认新的EULA(https://account.mojang.com/documents/minecraft_eula)？如果拒绝会导致无法启动Minecraft 1.7.10或更高版本的服务端。");
            try {
                if (option == JOptionPane.YES_OPTION)
                    FileUtils.write(eula, "eula=true");
                else if (option == JOptionPane.NO_OPTION)
                    FileUtils.write(eula, "eula=false");
            } catch (IOException e) {
                MessageBox.show("确认rula失败");
            }
        }
        File serverproperties = new File(new File(SettingsManager.settings.mainjar).getParentFile(), "server.properties");

        if (!serverproperties.exists())
            try {
                FileUtils.write(serverproperties, ServerProperties.getDefault());
            } catch (IOException ex) {
                HMCLog.warn("Failed to save server.properties", ex);
            }

        Server.init(SettingsManager.settings.mainjar, String.valueOf(SettingsManager.settings.maxMemory));
        Server.getInstance()
                .addListener((MonitorThread.MonitorThreadListener) this);
        Server.getInstance()
                .addListener((Consumer<SimpleEvent<Integer>>) this);
        Server.getInstance()
                .clearSchedule();
        for (Schedule s : SettingsManager.settings.schedules)
            Server.getInstance().addSchedule(s);

        HMCAPI.EVENT_BUS.channel(ServerStartedEvent.class).register(new ServerBeginListener());
        HMCAPI.EVENT_BUS.channel(ServerStoppedEvent.class).register(new ServerDoneListener());
        try {
            Server.getInstance().run();
        } catch (IOException ex) {
            MessageBox.show("启动服务端失败！");
            HMCLog.err("Failed to launch!", ex);
        }
    }//GEN-LAST:event_btnLaunchActionPerformed

    private void txtMainJarFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_txtMainJarFocusLost
        SettingsManager.settings.mainjar = txtMainJar.getText();
        SettingsManager.save();
    }//GEN-LAST:event_txtMainJarFocusLost

    public void stopServer() {
        Server.getInstance().stop();
        btnStop.setEnabled(false);
        btnCommand.setEnabled(false);
    }

    private void txtCommandKeyPressed(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_txtCommandKeyPressed
        int newCommandIndex = commandIndex;
        int type = 0;
        switch (evt.getKeyCode()) {
            case KeyEvent.VK_UP:
                newCommandIndex--;
                type = 1;
                break;
            case KeyEvent.VK_DOWN:
                newCommandIndex++;
                type = 1;
                break;
            case KeyEvent.VK_ENTER:
                type = 2;
                break;
            default:
                break;
        }
        if (type == 1) {
            if (outOfCommandSet(newCommandIndex))
                return;
            commandIndex = newCommandIndex;
            txtCommand.setText(commandSet.get(commandIndex));
        } else if (type == 2)
            sendCommand();
    }//GEN-LAST:event_txtCommandKeyPressed

    private void btnSendCommandActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnSendCommandActionPerformed
        sendCommand();
    }//GEN-LAST:event_btnSendCommandActionPerformed

    private void btnSetJarActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnSetJarActionPerformed
        JFileChooser fc = new JFileChooser();
        fc.setFileFilter(new FileNameExtensionFilter("Jar file", "jar"));
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File f = fc.getSelectedFile();
            if (f != null) {
                txtMainJar.setText(f.getAbsolutePath());
                SettingsManager.settings.mainjar = f.getAbsolutePath();
                SettingsManager.save();
                loadFromServerProperties();
                loadFromOPs();
            }
        }
    }//GEN-LAST:event_btnSetJarActionPerformed

    private void cboDifficultyItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_cboDifficultyItemStateChanged
        ServerProperties.getInstance().setDifficulty(cboDifficulty.getSelectedIndex());
    }//GEN-LAST:event_cboDifficultyItemStateChanged

    private void txtMaxBuildHeightFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_txtMaxBuildHeightFocusLost
        ServerProperties.getInstance().setMaxBuildHeight(Integer.parseInt(txtMaxBuildHeight.getValue().toString()));
    }//GEN-LAST:event_txtMaxBuildHeightFocusLost

    private void txtServerNameFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_txtServerNameFocusLost
        ServerProperties.getInstance().setServerName(txtServerName.getText());
    }//GEN-LAST:event_txtServerNameFocusLost

    private void txtServerMOTDFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_txtServerMOTDFocusLost
        ServerProperties.getInstance().setMotd(txtServerMOTD.getText());
    }//GEN-LAST:event_txtServerMOTDFocusLost

    private void cboGameModeItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_cboGameModeItemStateChanged
        ServerProperties.getInstance().setGameMode(cboGameMode.getSelectedIndex());
    }//GEN-LAST:event_cboGameModeItemStateChanged

    private void chkEnalbleAnimalsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_chkEnalbleAnimalsActionPerformed
        ServerProperties.getInstance().setSpawnAnimals(chkEnalbleAnimals.isSelected());
    }//GEN-LAST:event_chkEnalbleAnimalsActionPerformed

    private void chkEnableMonstersActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_chkEnableMonstersActionPerformed
        ServerProperties.getInstance().setSpawnMonsters(chkEnableMonsters.isSelected());
    }//GEN-LAST:event_chkEnableMonstersActionPerformed

    private void chkEnableNPCsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_chkEnableNPCsActionPerformed
        ServerProperties.getInstance().setSpawnNPCs(chkEnableNPCs.isSelected());
    }//GEN-LAST:event_chkEnableNPCsActionPerformed

    private void chkAllowFlightActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_chkAllowFlightActionPerformed
        ServerProperties.getInstance().setAllowFlight(chkAllowFlight.isSelected());
    }//GEN-LAST:event_chkAllowFlightActionPerformed

    private void chkPVPActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_chkPVPActionPerformed
        ServerProperties.getInstance().setPVP(chkPVP.isSelected());
    }//GEN-LAST:event_chkPVPActionPerformed

    private void chkAllowNetherActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_chkAllowNetherActionPerformed
        ServerProperties.getInstance().setAllowNether(chkAllowNether.isSelected());
    }//GEN-LAST:event_chkAllowNetherActionPerformed

    private void chkWhiteListActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_chkWhiteListActionPerformed
        ServerProperties.getInstance().setWhiteList(chkWhiteList.isSelected());
    }//GEN-LAST:event_chkWhiteListActionPerformed

    private void txtServerPortFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_txtServerPortFocusLost
        ServerProperties.getInstance().setServerPort(Integer.parseInt(txtServerPort.getValue().toString()));
    }//GEN-LAST:event_txtServerPortFocusLost

    private void txtMaxPlayerFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_txtMaxPlayerFocusLost
        ServerProperties.getInstance().setMaxPlayers(Integer.parseInt(txtMaxPlayer.getValue().toString()));
    }//GEN-LAST:event_txtMaxPlayerFocusLost

    private void txtViewDistanceFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_txtViewDistanceFocusLost
        ServerProperties.getInstance().setViewDistence(Integer.parseInt(txtViewDistance.getValue().toString()));
    }//GEN-LAST:event_txtViewDistanceFocusLost

    private void cboWorldTypeItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_cboWorldTypeItemStateChanged
        int OAO = cboWorldType.getSelectedIndex();
        String type = "DEFAULT";
        switch (OAO) {
            case 0:
                type = "DEFAULT";
                break;
            case 1:
                type = "FLAT";
                break;
            case 2:
                type = "LARGEBIMOES";
                break;
            default:
                break;
        }
        ServerProperties.getInstance().setLevelType(type);
    }//GEN-LAST:event_cboWorldTypeItemStateChanged

    private void txtWorldSeedFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_txtWorldSeedFocusLost
        ServerProperties.getInstance().setLevelSeed(txtWorldSeed.getText());
    }//GEN-LAST:event_txtWorldSeedFocusLost

    private void txtServerGeneratorSettingsFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_txtServerGeneratorSettingsFocusLost
        ServerProperties.getInstance().setGeneratorSettings(txtServerGeneratorSettings.getText());
    }//GEN-LAST:event_txtServerGeneratorSettingsFocusLost

    private void txtWorldNameFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_txtWorldNameFocusLost
        ServerProperties.getInstance().setLevelName(txtWorldName.getText());
    }//GEN-LAST:event_txtWorldNameFocusLost

    private void chkGenerateStructuresActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_chkGenerateStructuresActionPerformed
        ServerProperties.getInstance().setGenerateStructures(chkGenerateStructures.isSelected());
    }//GEN-LAST:event_chkGenerateStructuresActionPerformed

    private void btnAddOPActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnAddOPActionPerformed
        lstOPModel.addElement(txtOPName.getText());
        lstOP.updateUI();

        if (Server.isInstanceRunning())
            Server.getInstance().sendCommand("op " + txtOPName.getText());
        else {
            Op.Operator operator = new Op.Operator(txtOPName.getText());
            op.op.add(operator);
            File dir = new File(SettingsManager.settings.mainjar).getParentFile();
            try {
                op.saveAsBoth(new File(dir, "ops.txt"), new File(dir, "ops.json"));
            } catch (IOException ex) {
                HMCLog.warn("Failed to save ops", ex);
                MessageBox.show("添加失败。。。");
            }
        }
    }//GEN-LAST:event_btnAddOPActionPerformed

    private void btnDeleteOPActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnDeleteOPActionPerformed
        String s = lstOP.getSelectedValue().toString();
        lstOPModel.removeElement(lstOP.getSelectedIndex());
        lstOP.updateUI();

        if (Server.isInstanceRunning())
            Server.getInstance().sendCommand("deop " + txtOPName.getText());
        else {
            Op.Operator operator = new Op.Operator(s);
            op.op.remove(operator);
            File dir = new File(SettingsManager.settings.mainjar).getParentFile();
            try {
                op.saveAsBoth(new File(dir, "ops.txt"), new File(dir, "ops.json"));
            } catch (IOException ex) {
                HMCLog.warn("Failed to save ops", ex);
                MessageBox.show("删除失败。。。");
            }
        }
    }//GEN-LAST:event_btnDeleteOPActionPerformed

    private void btnAddWhiteActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnAddWhiteActionPerformed
        lstWhiteListModel.addElement(txtWhiteName.getText());
        lstWhiteList.updateUI();

        if (Server.isInstanceRunning())
            Server.getInstance().sendCommand("whitelist add " + txtWhiteName.getText());
        else {
            WhiteList.WhiteListPlayer player = new WhiteList.WhiteListPlayer(txtWhiteName.getText());
            whitelist.op.add(player);
            File dir = new File(SettingsManager.settings.mainjar).getParentFile();
            try {
                whitelist.saveAsBoth(new File(dir, "white-list.txt"), new File(dir, "white-list.json"));
            } catch (IOException ex) {
                HMCLog.warn("Failed to save white-list", ex);
                MessageBox.show("添加失败。。。");
            }
        }
    }//GEN-LAST:event_btnAddWhiteActionPerformed

    private void btnDeleteWhiteActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnDeleteWhiteActionPerformed
        String name = lstWhiteList.getSelectedValue().toString();
        lstWhiteListModel.removeElement(lstWhiteList.getSelectedIndex());
        lstWhiteList.updateUI();

        if (Server.isInstanceRunning())
            Server.getInstance().sendCommand("whitelist remove " + txtWhiteName.getText());
        else {
            WhiteList.WhiteListPlayer player = new WhiteList.WhiteListPlayer(name);
            whitelist.op.remove(player);
            File dir = new File(SettingsManager.settings.mainjar).getParentFile();
            try {
                whitelist.saveAsBoth(new File(dir, "white-list.txt"), new File(dir, "white-list.json"));
            } catch (IOException ex) {
                HMCLog.warn("Failed to save white-list", ex);
                MessageBox.show("删除失败。。。");
            }
        }
    }//GEN-LAST:event_btnDeleteWhiteActionPerformed

    private void btnManageExtModsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnManageExtModsActionPerformed
        FolderOpener.openMods();
    }//GEN-LAST:event_btnManageExtModsActionPerformed

    private void btnAddExternelModActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnAddExternelModActionPerformed
        JFileChooser fc = new JFileChooser();
        fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fc.setDialogTitle(C.i18n("mods.choose_mod"));
        fc.setMultiSelectionEnabled(false);
        fc.showOpenDialog(this);
        try {
            String path = fc.getSelectedFile().getCanonicalPath();
            String path2 = Utilities.try2GetPath("mods");
            File newf = new File(path2);
            newf.mkdirs();
            newf = new File(path2 + File.separator + fc.getSelectedFile().getName());
            FileUtils.copyFile(new File(path), newf);

            DefaultTableModel model = (DefaultTableModel) lstExternalMods.getModel();
            model.addRow(new Object[] { fc.getSelectedFile().getName(), ModType.getModTypeShowName(ModType.getModType(newf)) });
            lstExternalMods.updateUI();
        } catch (IOException e) {
            MessageBox.show(C.i18n("mods.failed"));
            HMCLog.warn("Failed to add ext mods", e);
        }
    }//GEN-LAST:event_btnAddExternelModActionPerformed

    private void btnDeleteExternelModActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnDeleteExternelModActionPerformed
        DefaultTableModel model = (DefaultTableModel) lstExternalMods.getModel();
        int idx = lstExternalMods.getSelectedRow();
        String selectedName = (String) model.getValueAt(idx, 0);
        model.removeRow(idx);
        String path = Utilities.getPath("mods");
        if (path == null)
            return;
        File newf = new File(path + File.separator + selectedName);
        newf.delete();
    }//GEN-LAST:event_btnDeleteExternelModActionPerformed

    private void btnManageCoreModsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnManageCoreModsActionPerformed
        FolderOpener.openCoreMods();
    }//GEN-LAST:event_btnManageCoreModsActionPerformed

    private void btnAddExternelCoreModActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnAddExternelCoreModActionPerformed
        JFileChooser fc = new JFileChooser();
        fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fc.setDialogTitle(C.i18n("mods.choose_mod"));
        fc.setMultiSelectionEnabled(false);
        fc.showOpenDialog(this);
        try {
            String path = fc.getSelectedFile().getCanonicalPath();
            String path2 = Utilities.try2GetPath("coremods");
            File newf = new File(path2);
            newf.mkdirs();
            newf = new File(path2 + File.separator + fc.getSelectedFile().getName());

            DefaultTableModel model = (DefaultTableModel) lstCoreMods.getModel();
            lstCoreMods.updateUI();
            model.addRow(new Object[] { fc.getSelectedFile().getName(), ModType.getModTypeShowName(ModType.getModType(newf)) });
            FileUtils.copyFile(new File(path), newf);
        } catch (IOException e) {
            MessageBox.show(C.i18n("mods.failed"));
            HMCLog.warn("Failed to add ext core mod.", e);
        }
    }//GEN-LAST:event_btnAddExternelCoreModActionPerformed

    private void btnDeleteExternelCoreModActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnDeleteExternelCoreModActionPerformed
        DefaultTableModel model = (DefaultTableModel) lstCoreMods.getModel();
        int idx = lstCoreMods.getSelectedRow();
        String selectedName = (String) model.getValueAt(idx, 0);
        model.removeRow(idx);
        lstCoreMods.updateUI();
        String path = Utilities.getPath("coremods");
        if (path == null)
            return;
        File newf = new File(path + File.separator + selectedName);
        newf.delete();
    }//GEN-LAST:event_btnDeleteExternelCoreModActionPerformed

    private void btnManagePluginsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnManagePluginsActionPerformed
        FolderOpener.openPlugins();
    }//GEN-LAST:event_btnManagePluginsActionPerformed

    private void btnAddPluginsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnAddPluginsActionPerformed
        JFileChooser fc = new JFileChooser();
        fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fc.setDialogTitle(C.i18n("mods.choose_mod"));
        fc.setMultiSelectionEnabled(false);
        fc.showOpenDialog(this);
        try {
            String path = fc.getSelectedFile().getCanonicalPath();
            String path2 = Utilities.try2GetPath("plugins");
            File newf = new File(path2);
            newf.mkdirs();
            newf = new File(path2 + File.separator + fc.getSelectedFile().getName());

            DefaultTableModel model = (DefaultTableModel) lstPlugins.getModel();
            lstPlugins.updateUI();
            model.addRow(new Object[] { fc.getSelectedFile().getName(), ModType.getModTypeShowName(ModType.getModType(newf)) });
            FileUtils.copyFile(new File(path), newf);
        } catch (IOException e) {
            MessageBox.show(C.i18n("mods.failed"));
            HMCLog.warn("Failed to add plugin", e);
        }
    }//GEN-LAST:event_btnAddPluginsActionPerformed

    private void btnDeletePluginsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnDeletePluginsActionPerformed
        DefaultTableModel model = (DefaultTableModel) lstPlugins.getModel();
        int idx = lstPlugins.getSelectedRow();
        String selectedName = (String) model.getValueAt(idx, 0);
        model.removeRow(idx);
        lstPlugins.updateUI();
        String path = Utilities.getPath("plugins");
        if (path == null)
            return;
        File newf = new File(path + File.separator + selectedName);
        newf.delete();
    }//GEN-LAST:event_btnDeletePluginsActionPerformed

    private void btnShutdownActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnShutdownActionPerformed
        Server.getInstance().shutdown();
    }//GEN-LAST:event_btnShutdownActionPerformed

    private void btnSaveActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnSaveActionPerformed
        Server.getInstance().sendCommand("save-all");
    }//GEN-LAST:event_btnSaveActionPerformed

    private void btnSaveExtModActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnSaveExtModActionPerformed
        ArrayList<String> arrayList = new ArrayList<>();
        Vector strings = ((DefaultTableModel) lstExternalMods.getModel()).getDataVector();
        for (Object s : strings) {
            Vector v = (Vector) s;
            if (!(Boolean) v.elementAt(0))
                arrayList.add((String) v.elementAt(1));
        }
        SettingsManager.settings.inactiveExtMods = arrayList;
        SettingsManager.save();
    }//GEN-LAST:event_btnSaveExtModActionPerformed

    private void btnSavePluginsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnSavePluginsActionPerformed
        ArrayList<String> arrayList = new ArrayList<>();
        Vector strings = ((DefaultTableModel) lstCoreMods.getModel()).getDataVector();
        for (Object s : strings) {
            Vector v = (Vector) s;
            if (!(Boolean) v.elementAt(0))
                arrayList.add((String) v.elementAt(1));
        }
        SettingsManager.settings.inactiveCoreMods = arrayList;
        SettingsManager.save();
    }//GEN-LAST:event_btnSavePluginsActionPerformed

    private void btnAddBanActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnAddBanActionPerformed
        lstBannedModel.addElement(txtBanName.getText());
        lstBanned.updateUI();

        if (Server.isInstanceRunning())
            Server.getInstance().sendCommand("ban " + txtBanName.getText());
        else {
            BannedPlayers.BannedPlayer player = new BannedPlayers.BannedPlayer(txtBanName.getText());
            banned.op.add(player);
            File dir = new File(SettingsManager.settings.mainjar).getParentFile();
            try {
                whitelist.saveAsBoth(new File(dir, "banned-players.txt"), new File(dir, "banned-players.json"));
            } catch (IOException ex) {
                HMCLog.warn("Failed to save banned-players", ex);
                MessageBox.show(C.i18n("mods.failed"));
            }
        }
    }//GEN-LAST:event_btnAddBanActionPerformed

    private void btnUnbanActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnUnbanActionPerformed
        String s = lstBanned.getSelectedValue().toString();
        lstBannedModel.removeElement(lstBanned.getSelectedIndex());
        lstBanned.updateUI();

        if (Server.isInstanceRunning())
            Server.getInstance().sendCommand("pardon " + txtBanName.getText());
        else {
            BannedPlayers.BannedPlayer player = new BannedPlayers.BannedPlayer(s);
            banned.op.remove(player);
            File dir = new File(SettingsManager.settings.mainjar).getParentFile();
            try {
                whitelist.saveAsBoth(new File(dir, "banned-players.txt"), new File(dir, "banned-players.json"));
            } catch (IOException ex) {
                HMCLog.warn("Failed to save white-list", ex);
            }
        }
    }//GEN-LAST:event_btnUnbanActionPerformed

    private void btnSetBackgroundPathActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnSetBackgroundPathActionPerformed
        JFileChooser fc = new JFileChooser();
        fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fc.setDialogTitle(C.i18n("launcher.choose_bgpath"));
        fc.setMultiSelectionEnabled(false);
        fc.showOpenDialog(this);
        try {
            String path = fc.getSelectedFile().getCanonicalPath();
            path = IOUtils.removeLastSeparator(path);
            txtBackgroundPath.setText(path);
            SettingsManager.settings.bgPath = path;
            SettingsManager.save();
            background = new ImageIcon(Toolkit.getDefaultToolkit().getImage(path));
            resizeBackgroundLabel();
        } catch (IOException e) {
            HMCLog.warn("Failed to set background path", e);
            MessageBox.show(C.i18n("ui.label.failed_set") + e.getMessage());
        }
    }//GEN-LAST:event_btnSetBackgroundPathActionPerformed

    private void txtBackgroundPathFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_txtBackgroundPathFocusLost
        String path = txtBackgroundPath.getText();
        SettingsManager.settings.bgPath = path;
        SettingsManager.save();
        background = new ImageIcon(Toolkit.getDefaultToolkit().getImage(path));
        resizeBackgroundLabel();
    }//GEN-LAST:event_txtBackgroundPathFocusLost

    private void btnNewTaskActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnNewTaskActionPerformed
        Schedule s = new Schedule();
        s.type = cboTimerTask.getSelectedIndex();
        s.type2 = 0;
        s.timeType = cboTimeType.getSelectedIndex();
        s.content = txtTimerTaskContent.getText();
        try {
            s.per = Double.parseDouble(txtTimerTaskPeriod.getText());
        } catch (NumberFormatException e) {
            HMCLog.warn("Failed to parse double: " + txtTimerTaskPeriod.getText(), e);
            MessageBox.show("错误的间隔时间");
            return;
        }
        SettingsManager.settings.schedules.add(s);
        SettingsManager.save();
        DefaultTableModel model = (DefaultTableModel) lstSchedules.getModel();
        model.addRow(ScheduleTranslator.getRow(s));
        lstSchedules.updateUI();
    }//GEN-LAST:event_btnNewTaskActionPerformed

    private void jButton3ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton3ActionPerformed
        txtMain.setText("");
    }//GEN-LAST:event_jButton3ActionPerformed

    private void btnDelSelectedScheduleActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnDelSelectedScheduleActionPerformed
        int index = lstSchedules.getSelectedRow();
        SettingsManager.settings.schedules.remove(index);
        SettingsManager.save();
        DefaultTableModel model = (DefaultTableModel) lstSchedules.getModel();
        model.removeRow(index);
        lstSchedules.updateUI();
    }//GEN-LAST:event_btnDelSelectedScheduleActionPerformed

    private void btnRestartActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnRestartActionPerformed
        Server.getInstance().restart();
    }//GEN-LAST:event_btnRestartActionPerformed

    private void btnRefreshWorldsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnRefreshWorldsActionPerformed
        loadWorlds();
    }//GEN-LAST:event_btnRefreshWorldsActionPerformed

    private void btnSaveWorldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnSaveWorldActionPerformed

        DefaultTableModel model = (DefaultTableModel) lstWorlds.getModel();
        SettingsManager.settings.inactiveWorlds = new ArrayList<>();
        for (int i = 0; i < model.getRowCount(); i++)
            if ((Boolean) model.getValueAt(i, 2) == false)
                SettingsManager.settings.inactiveWorlds.add((String) model.getValueAt(i, 0));
        SettingsManager.save();
    }//GEN-LAST:event_btnSaveWorldActionPerformed

    private void btnBackupActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnBackupActionPerformed
        switch (cboBackupTypes.getSelectedIndex()) {
            case 0:
                BackupManager.backupAllWorlds();
                break;
            case 1:
                BackupManager.backupAllPlugins();
                break;
        }
    }//GEN-LAST:event_btnBackupActionPerformed

    private void jButton1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton1ActionPerformed
        loadBackups();
    }//GEN-LAST:event_jButton1ActionPerformed

    File getBackupFile(int index) {
        DefaultTableModel model = (DefaultTableModel) lstBackups.getModel();
        return new File(BackupManager.backupDir()
                + model.getValueAt(index, 0) + "+"
                + model.getValueAt(index, 1) + "+"
                + model.getValueAt(index, 2) + ".zip");
    }

    private void btnDeleteBackupActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnDeleteBackupActionPerformed
        int index = lstBackups.getSelectedRow();
        if (index == -1)
            return;
        FileUtils.deleteDirectoryQuietly(getBackupFile(index));
        DefaultTableModel model = (DefaultTableModel) lstBackups.getModel();
        model.removeRow(index);
    }//GEN-LAST:event_btnDeleteBackupActionPerformed

    private void btnRestoreBackupActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnRestoreBackupActionPerformed
        int index = lstBackups.getSelectedRow();
        if (index == -1)
            return;
        BackupManager.restoreBackup(getBackupFile(index));
    }//GEN-LAST:event_btnRestoreBackupActionPerformed

    private void btnRefreshDownloadsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnRefreshDownloadsActionPerformed
        refreshDownloads();
    }//GEN-LAST:event_btnRefreshDownloadsActionPerformed

    private void btnMinecraftServerDownloadActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnMinecraftServerDownloadActionPerformed
        String id = (String) lstDownloads.getModel().getValueAt(lstDownloads.getSelectedRow(), 0);
        final String MC_DOWNLOAD_URL = "https://s3.amazonaws.com/Minecraft.Download/versions/";
        String url = MC_DOWNLOAD_URL + id + "/";
        File serverjar = new File("minecraft_server." + id + ".jar");
        serverjar.delete();

        String downloadURL = url + "minecraft_server." + id + ".jar";
        TaskWindow.factory().append(new FileDownloadTask(downloadURL, serverjar).setTag(id)).execute();
    }//GEN-LAST:event_btnMinecraftServerDownloadActionPerformed

    private void btnRefreshInfosActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnRefreshInfosActionPerformed
        refreshInfos();
    }//GEN-LAST:event_btnRefreshInfosActionPerformed

    private void btnShowInfoActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnShowInfoActionPerformed
        try {
            DefaultTableModel model = (DefaultTableModel) lstInfos.getModel();
            int index = lstInfos.getSelectedRow();
            if (index == -1)
                return;
            String path = Utilities.getGameDir() + "infos-HMCSM" + File.separator + model.getValueAt(index, 0);
            String content = FileUtils.read(new File(path));
            txtInfo.setText(content);
        } catch (IOException ex) {
            HMCLog.warn("Failed to read info.", ex);
        }
    }//GEN-LAST:event_btnShowInfoActionPerformed

    private void btnAutoSearchActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnAutoSearchActionPerformed
        IOUtils.findAllFile(new File("."), s -> {
            if (ServerChecker.isServerJar(new File(s))) {
                String path = IOUtils.tryGetCanonicalFilePath(new File(s));
                txtMainJar.setText(path);
                SettingsManager.settings.mainjar = path;
                SettingsManager.save();
            }
        });
    }//GEN-LAST:event_btnAutoSearchActionPerformed

    private void cboCategoryItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_cboCategoryItemStateChanged
        loadBukkitPlugins();
    }//GEN-LAST:event_cboCategoryItemStateChanged

    private void btnShowPluginInfoActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnShowPluginInfoActionPerformed
        try {
            int index = lstBukkitPlugins.getSelectedRow();
            if (index == -1)
                return;
            PluginInfo pi = PluginManager.getPluginInfo(plugins.get(index).slug);
            PluginInfoDialog w = new PluginInfoDialog(this, true, pi);
            w.setVisible(true);
        } catch (Exception ex) {
            HMCLog.warn("Failed to get plugin info", ex);
        }
    }//GEN-LAST:event_btnShowPluginInfoActionPerformed

    private void btnRefreshReportsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnRefreshReportsActionPerformed
        refreshReports();
    }//GEN-LAST:event_btnRefreshReportsActionPerformed

    private void btnShowReportActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnShowReportActionPerformed
        try {
            int index = lstReports.getSelectedIndex();
            if (index == -1)
                return;
            String path = Utilities.getGameDir() + "crash-reports" + File.separator + lstCrashReportsModel.get(index);
            String content = FileUtils.read(new File(path));
            txtCrashReport.setText(content);
        } catch (IOException ex) {
            HMCLog.warn("Failed to get crash-report.", ex);
            MessageBox.show("无法获取崩溃报告");
        }
    }//GEN-LAST:event_btnShowReportActionPerformed

    private void btnRefreshPlayersActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnRefreshPlayersActionPerformed
        loadPlayers();
    }//GEN-LAST:event_btnRefreshPlayersActionPerformed

    private void chkOnlineModeActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_chkOnlineModeActionPerformed
        ServerProperties.getInstance().setOnlineMode(chkOnlineMode.isSelected());
    }//GEN-LAST:event_chkOnlineModeActionPerformed

    private void jButton4ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton4ActionPerformed
        SwingUtils.openLink("http://www.mcbbs.net/");
    }//GEN-LAST:event_jButton4ActionPerformed

    private void jButton5ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton5ActionPerformed
        SwingUtils.openLink("http://tieba.baidu.com/minecraft");
    }//GEN-LAST:event_jButton5ActionPerformed

    private void jButton6ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton6ActionPerformed
        SwingUtils.openLink("http://www.mcbbs.net/thread-171239-1-1.html");
    }//GEN-LAST:event_jButton6ActionPerformed

    private void jButton7ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton7ActionPerformed
        SwingUtils.openLink("http://www.minecraft.net/");
    }//GEN-LAST:event_jButton7ActionPerformed

    private void jButton2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton2ActionPerformed
        SwingUtils.openLink("http://www.oray.com/peanuthull/download_ddns_6.5.php");
    }//GEN-LAST:event_jButton2ActionPerformed

    private void jButton8ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton8ActionPerformed
        SwingUtils.openLink("http://www.bukkit.org/");
    }//GEN-LAST:event_jButton8ActionPerformed

    private void jButton9ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton9ActionPerformed
        SwingUtils.openLink("http://ci.md-5.net/job/MCPC-Plus/");
    }//GEN-LAST:event_jButton9ActionPerformed

    private void jButton10ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton10ActionPerformed
        new CommandsWindow(this, true).setVisible(true);
    }//GEN-LAST:event_jButton10ActionPerformed

    private void lstRefreshCraftbukkitActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_lstRefreshCraftbukkitActionPerformed
        loadBukkits();
    }//GEN-LAST:event_lstRefreshCraftbukkitActionPerformed

    private void btnDownloadCraftbukkitActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnDownloadCraftbukkitActionPerformed
        int idx = lstCraftbukkit.getSelectedRow();
        if (idx == -1)
            return;
        String ext;
        List<BukkitVersion> cb;
        int idx2 = cboBukkitType.getSelectedIndex();
        if (idx2 == -1)
            return;
        switch (idx2) {
            case 0:
                ext = "rb";
                cb = craftBukkitRecommended;
                break;
            case 1:
                ext = "beta";
                cb = craftBukkitBeta;
                break;
            default:
                return;
        }
        BukkitVersion v = cb.get(idx);
        File file = new File("craftbukkit-" + ext + "-" + v.version + ".jar");
        TaskWindow.factory().append(new FileDownloadTask(v.downloadLink, IOUtils.tryGetCanonicalFile(file)).setTag("bukkit-" + ext + "-" + v.version))
                .execute();
    }//GEN-LAST:event_btnDownloadCraftbukkitActionPerformed

    private void btnDownloadMCPCActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnDownloadMCPCActionPerformed
        int idx = lstMCPC.getSelectedRow();
        if (idx == -1)
            return;
        ForgeVersion v = mcpcPackages.get(cboCauldronMinecraft.getSelectedItem().toString()).get(idx);
        String url;
        File filepath = new File("forge-installer.jar");
        url = v.installer[1];
        if (!TaskWindow.factory().append(new FileDownloadTask(url, filepath).setTag("cauldron-" + v.ver)).execute())
            MessageBox.show(C.i18n("install.failed_download_forge"));
        else
            installMCPC(filepath);
    }//GEN-LAST:event_btnDownloadMCPCActionPerformed

    private void installMCPC(final File filepath) {
        try {
            ForgeInstaller installer = new ForgeInstaller(new File("."), filepath);
            installer.install();
            MessageBox.show(C.i18n("install.success"));
        } catch (Exception e) {
            HMCLog.warn("Failed to install liteloader", e);
            MessageBox.show(C.i18n("install.failed_forge"));
        }
    }

    private void lstRefreshMCPCActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_lstRefreshMCPCActionPerformed
        loadMCPCs();
    }//GEN-LAST:event_lstRefreshMCPCActionPerformed

    private void btnSaveCoreModActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnSaveCoreModActionPerformed
        ArrayList<String> arrayList = new ArrayList<>();
        Vector strings = ((DefaultTableModel) lstPlugins.getModel()).getDataVector();
        for (Object s : strings) {
            Vector v = (Vector) s;
            if (!(Boolean) v.elementAt(0))
                arrayList.add((String) v.elementAt(1));
        }
        SettingsManager.settings.inactivePlugins = arrayList;
        SettingsManager.save();
    }//GEN-LAST:event_btnSaveCoreModActionPerformed

    private void cboCauldronMinecraftItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_cboCauldronMinecraftItemStateChanged
        if (cboCauldronMinecraft.getItemCount() > 0 && mcpcPackages != null && mcpcPackages.containsKey(cboCauldronMinecraft.getSelectedItem().toString()))
            useMCPCVersions(cboCauldronMinecraft.getSelectedItem().toString());
    }//GEN-LAST:event_cboCauldronMinecraftItemStateChanged

    private void btnInstallMCPCActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnInstallMCPCActionPerformed
        File filepath = new File("forge-installer.jar");
        if (!filepath.exists()) {
            MessageBox.show("您还未下载Cauldron！请点击下载按钮下载并自动安装！");
            return;
        }
        installMCPC(filepath);
    }//GEN-LAST:event_btnInstallMCPCActionPerformed

    private void cboBukkitTypeItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_cboBukkitTypeItemStateChanged
        reloadBukkitList();
    }//GEN-LAST:event_cboBukkitTypeItemStateChanged

    private void jButton11ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_jButton11ActionPerformed
        loadBukkitPlugins();
        loadBukkitCategory();
    }//GEN-LAST:event_jButton11ActionPerformed

    private void btnCommandMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_btnCommandMouseClicked
        if (Server.getInstance() == null || !Server.getInstance().isRunning || !btnCommand.isEnabled())
            return;
        ppmBasically.show(evt.getComponent(), evt.getPoint().x, evt.getPoint().y);
    }//GEN-LAST:event_btnCommandMouseClicked

    private void btnStopActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnStopActionPerformed
        Server.getInstance().stop();
    }//GEN-LAST:event_btnStopActionPerformed

    private void formWindowClosed(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowClosed

    }//GEN-LAST:event_formWindowClosed

    private void formWindowClosing(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowClosing
        if (Server.getInstance() != null && Server.getInstance().isRunning)
            Server.getInstance().stop();
    }//GEN-LAST:event_formWindowClosing

    private void txtMaxMemoryFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_txtMaxMemoryFocusLost
        SettingsManager.settings.maxMemory = txtMaxMemory.getText();
        SettingsManager.save();
    }//GEN-LAST:event_txtMaxMemoryFocusLost

    @Override
    public void onStatus(String status) {
        String text = txtMain.getText();
        text += status + System.getProperty("line.separator");
        int position = text.length();
        txtMain.setText(text);
        txtMain.setCaretPosition(position);
    }

    @Override
    public void accept(SimpleEvent<Integer> event) {
        btnLaunch.setEnabled(true);
        btnStop.setEnabled(false);
        btnShutdown.setEnabled(false);
        btnCommand.setEnabled(false);
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH-mm-ss");
        try {
            FileUtils.write(new File(Utilities.getGameDir() + "infos-HMCSM"
                    + File.separator + format.format(new Date()) + ".txt"),
                    txtMain.getText());
        } catch (IOException ex) {
            HMCLog.warn("Failed to save info", ex);
        }
        if (getPlayerNumberTimer != null) {
            getPlayerNumberTimer.cancel();
            getPlayerNumberTimer = null;
        }
    }

    private void sendCommand() {
        String command = txtCommand.getText();
        boolean append = false;
        if (outOfCommandSet())
            append = true;
        else if (!command.equals(commandSet.get(commandIndex)))
            append = true;
        if (Server.getInstance() != null)
            Server.getInstance().sendCommand(command);
        else
            System.err.println("Server is null.");
        System.out.println("Send command: " + command);
        onStatus(">" + command);
        txtCommand.setText("");
        if (append) {
            commandSet.add(command);
            commandIndex = commandSet.size();
        } else
            commandIndex++;
    }

    MonitorThread mainThread;
    DefaultListModel lstOPModel = new DefaultListModel(),
            lstWhiteListModel = new DefaultListModel(),
            lstBannedModel = new DefaultListModel(),
            lstCrashReportsModel = new DefaultListModel(),
            lstPlayersModel = new DefaultListModel();
    List<BukkitPlugin> plugins;
    Map<String, List<ForgeVersion>> mcpcPackages;
    List<BukkitVersion> craftBukkitRecommended, craftBukkitBeta, craftBukkitDev;
    WhiteList whitelist;
    Op op;
    BannedPlayers banned;

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton btnAddBan;
    private javax.swing.JButton btnAddExternelCoreMod;
    private javax.swing.JButton btnAddExternelMod;
    private javax.swing.JButton btnAddOP;
    private javax.swing.JButton btnAddPlugins;
    private javax.swing.JButton btnAddWhite;
    private javax.swing.JButton btnAutoSearch;
    private javax.swing.JButton btnBackup;
    private javax.swing.JButton btnCommand;
    private javax.swing.JButton btnDelSelectedSchedule;
    private javax.swing.JButton btnDeleteBackup;
    private javax.swing.JButton btnDeleteExternelCoreMod;
    private javax.swing.JButton btnDeleteExternelMod;
    private javax.swing.JButton btnDeleteOP;
    private javax.swing.JButton btnDeletePlugins;
    private javax.swing.JButton btnDeleteWhite;
    private javax.swing.JButton btnDownloadCraftbukkit;
    private javax.swing.JButton btnDownloadMCPC;
    private javax.swing.JButton btnInstallMCPC;
    private javax.swing.JButton btnLaunch;
    private javax.swing.JButton btnManageCoreMods;
    private javax.swing.JButton btnManageExtMods;
    private javax.swing.JButton btnManagePlugins;
    private javax.swing.JButton btnMinecraftServerDownload;
    private javax.swing.JButton btnNewTask;
    private javax.swing.JButton btnRefreshDownloads;
    private javax.swing.JButton btnRefreshInfos;
    private javax.swing.JButton btnRefreshPlayers;
    private javax.swing.JButton btnRefreshReports;
    private javax.swing.JButton btnRefreshWorlds;
    private javax.swing.JButton btnRestart;
    private javax.swing.JButton btnRestoreBackup;
    private javax.swing.JButton btnSave;
    private javax.swing.JButton btnSaveCoreMod;
    private javax.swing.JButton btnSaveExtMod;
    private javax.swing.JButton btnSavePlugins;
    private javax.swing.JButton btnSaveWorld;
    private javax.swing.JButton btnSendCommand;
    private javax.swing.JButton btnSetBackgroundPath;
    private javax.swing.JButton btnSetJar;
    private javax.swing.JButton btnShowInfo;
    private javax.swing.JButton btnShowPluginInfo;
    private javax.swing.JButton btnShowReport;
    private javax.swing.JButton btnShutdown;
    private javax.swing.JButton btnStop;
    private javax.swing.JButton btnUnban;
    private javax.swing.JComboBox cboBackupTypes;
    private javax.swing.JComboBox cboBukkitType;
    private javax.swing.JComboBox cboCategory;
    private javax.swing.JComboBox cboCauldronMinecraft;
    private javax.swing.JComboBox cboDifficulty;
    private javax.swing.JComboBox cboGameMode;
    private javax.swing.JComboBox cboTimeType;
    private javax.swing.JComboBox cboTimerTask;
    private javax.swing.JComboBox cboWorldType;
    private javax.swing.JCheckBox chkAllowFlight;
    private javax.swing.JCheckBox chkAllowNether;
    private javax.swing.JCheckBox chkEnableMonsters;
    private javax.swing.JCheckBox chkEnableNPCs;
    private javax.swing.JCheckBox chkEnalbleAnimals;
    private javax.swing.JCheckBox chkGenerateStructures;
    private javax.swing.JCheckBox chkOnlineMode;
    private javax.swing.JCheckBox chkPVP;
    private javax.swing.JCheckBox chkWhiteList;
    private javax.swing.JButton jButton1;
    private javax.swing.JButton jButton10;
    private javax.swing.JButton jButton11;
    private javax.swing.JButton jButton2;
    private javax.swing.JButton jButton3;
    private javax.swing.JButton jButton4;
    private javax.swing.JButton jButton5;
    private javax.swing.JButton jButton6;
    private javax.swing.JButton jButton7;
    private javax.swing.JButton jButton8;
    private javax.swing.JButton jButton9;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel10;
    private javax.swing.JLabel jLabel11;
    private javax.swing.JLabel jLabel12;
    private javax.swing.JLabel jLabel13;
    private javax.swing.JLabel jLabel14;
    private javax.swing.JLabel jLabel15;
    private javax.swing.JLabel jLabel16;
    private javax.swing.JLabel jLabel17;
    private javax.swing.JLabel jLabel18;
    private javax.swing.JLabel jLabel19;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel20;
    private javax.swing.JLabel jLabel21;
    private javax.swing.JLabel jLabel22;
    private javax.swing.JLabel jLabel23;
    private javax.swing.JLabel jLabel24;
    private javax.swing.JLabel jLabel25;
    private javax.swing.JLabel jLabel26;
    private javax.swing.JLabel jLabel27;
    private javax.swing.JLabel jLabel28;
    private javax.swing.JLabel jLabel29;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel30;
    private javax.swing.JLabel jLabel31;
    private javax.swing.JLabel jLabel32;
    private javax.swing.JLabel jLabel33;
    private javax.swing.JLabel jLabel34;
    private javax.swing.JLabel jLabel35;
    private javax.swing.JLabel jLabel36;
    private javax.swing.JLabel jLabel37;
    private javax.swing.JLabel jLabel38;
    private javax.swing.JLabel jLabel39;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel40;
    private javax.swing.JLabel jLabel41;
    private javax.swing.JLabel jLabel42;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JLabel jLabel7;
    private javax.swing.JLabel jLabel8;
    private javax.swing.JLabel jLabel9;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel10;
    private javax.swing.JPanel jPanel11;
    private javax.swing.JPanel jPanel12;
    private javax.swing.JPanel jPanel13;
    private javax.swing.JPanel jPanel14;
    private javax.swing.JPanel jPanel15;
    private javax.swing.JPanel jPanel16;
    private javax.swing.JPanel jPanel17;
    private javax.swing.JPanel jPanel18;
    private javax.swing.JPanel jPanel19;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel20;
    private javax.swing.JPanel jPanel21;
    private javax.swing.JPanel jPanel22;
    private javax.swing.JPanel jPanel23;
    private javax.swing.JPanel jPanel24;
    private javax.swing.JPanel jPanel25;
    private javax.swing.JPanel jPanel26;
    private javax.swing.JPanel jPanel27;
    private javax.swing.JPanel jPanel28;
    private javax.swing.JPanel jPanel29;
    private javax.swing.JPanel jPanel3;
    private javax.swing.JPanel jPanel30;
    private javax.swing.JPanel jPanel4;
    private javax.swing.JPanel jPanel5;
    private javax.swing.JPanel jPanel6;
    private javax.swing.JPanel jPanel7;
    private javax.swing.JPanel jPanel8;
    private javax.swing.JPanel jPanel9;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane10;
    private javax.swing.JScrollPane jScrollPane11;
    private javax.swing.JScrollPane jScrollPane12;
    private javax.swing.JScrollPane jScrollPane13;
    private javax.swing.JScrollPane jScrollPane14;
    private javax.swing.JScrollPane jScrollPane15;
    private javax.swing.JScrollPane jScrollPane16;
    private javax.swing.JScrollPane jScrollPane17;
    private javax.swing.JScrollPane jScrollPane18;
    private javax.swing.JScrollPane jScrollPane19;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JScrollPane jScrollPane3;
    private javax.swing.JScrollPane jScrollPane4;
    private javax.swing.JScrollPane jScrollPane5;
    private javax.swing.JScrollPane jScrollPane6;
    private javax.swing.JScrollPane jScrollPane7;
    private javax.swing.JScrollPane jScrollPane8;
    private javax.swing.JScrollPane jScrollPane9;
    private javax.swing.JTabbedPane jTabbedPane1;
    private javax.swing.JTabbedPane jTabbedPane2;
    private javax.swing.JTabbedPane jTabbedPane3;
    private javax.swing.JTabbedPane jTabbedPane4;
    private javax.swing.JTabbedPane jTabbedPane5;
    private javax.swing.JTabbedPane jTabbedPane6;
    private javax.swing.JLabel lblFreeMemory;
    private javax.swing.JLabel lblIPAddress;
    private javax.swing.JLabel lblMaxMemory;
    private javax.swing.JLabel lblOSName;
    private javax.swing.JLabel lblPlayers;
    private javax.swing.JLabel lblTotalMemory;
    private javax.swing.JLabel lblTotalMemorySize;
    private javax.swing.JLabel lblTotalThread;
    private javax.swing.JLabel lblUsedMemory;
    private javax.swing.JTable lstBackups;
    private javax.swing.JList lstBanned;
    private javax.swing.JTable lstBukkitPlugins;
    private javax.swing.JTable lstCoreMods;
    private javax.swing.JTable lstCraftbukkit;
    private javax.swing.JTable lstDownloads;
    private javax.swing.JTable lstExternalMods;
    private javax.swing.JTable lstInfos;
    private javax.swing.JTable lstMCPC;
    private javax.swing.JList lstOP;
    private javax.swing.JList lstPlayers;
    private javax.swing.JTable lstPlugins;
    private javax.swing.JButton lstRefreshCraftbukkit;
    private javax.swing.JButton lstRefreshMCPC;
    private javax.swing.JList lstReports;
    private javax.swing.JTable lstSchedules;
    private javax.swing.JList lstWhiteList;
    private javax.swing.JTable lstWorlds;
    private javax.swing.JProgressBar pgsCPURatio;
    private javax.swing.JProgressBar pgsMemoryRatio;
    private javax.swing.JTextField txtBackgroundPath;
    private javax.swing.JTextField txtBanName;
    private javax.swing.JTextField txtCommand;
    private javax.swing.JTextArea txtCrashReport;
    private javax.swing.JTextArea txtInfo;
    private javax.swing.JTextField txtJavaArgs;
    private javax.swing.JTextField txtJavaDir;
    private javax.swing.JTextArea txtMain;
    private javax.swing.JTextField txtMainJar;
    private javax.swing.JSpinner txtMaxBuildHeight;
    private javax.swing.JTextField txtMaxMemory;
    private javax.swing.JSpinner txtMaxPlayer;
    private javax.swing.JTextField txtOPName;
    private javax.swing.JTextField txtServerGeneratorSettings;
    private javax.swing.JTextField txtServerMOTD;
    private javax.swing.JTextField txtServerName;
    private javax.swing.JSpinner txtServerPort;
    private javax.swing.JTextField txtTimerTaskContent;
    private javax.swing.JTextField txtTimerTaskPeriod;
    private javax.swing.JSpinner txtViewDistance;
    private javax.swing.JTextField txtWhiteName;
    private javax.swing.JTextField txtWorldName;
    private javax.swing.JTextField txtWorldSeed;
    // End of variables declaration//GEN-END:variables

}
