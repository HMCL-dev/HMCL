package org.jackhuang.hellominecraftlauncher.views;

import org.jackhuang.hellominecraftlauncher.utilities.C;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Image;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.ImageIcon;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.Timer;
import org.jackhuang.hellominecraftlauncher.Main;

import org.jackhuang.hellominecraftlauncher.updater.CheckUpdate;
import org.jackhuang.hellominecraftlauncher.updater.UpdateRequest;
import org.jackhuang.hellominecraftlauncher.utilities.*;
import org.jackhuang.hellominecraftlauncher.models.VersionCopier;
import org.jackhuang.hellominecraftlauncher.apis.DoneListener;
import org.jackhuang.hellominecraftlauncher.GameLauncher;
import org.jackhuang.hellominecraftlauncher.JavaProcess;
import org.jackhuang.hellominecraftlauncher.Pair;
import org.jackhuang.hellominecraftlauncher.apis.DownloadWindow;
import org.jackhuang.hellominecraftlauncher.apis.IPluginHandler;
import org.jackhuang.hellominecraftlauncher.apis.handlers.Login;
import org.jackhuang.hellominecraftlauncher.apis.handlers.LoginInfo;
import org.jackhuang.hellominecraftlauncher.apis.PluginHandlerType;
import org.jackhuang.hellominecraftlauncher.plugin.PluginHandler;
import org.jackhuang.hellominecraftlauncher.plugin.PluginManager;
import org.jackhuang.hellominecraftlauncher.apis.utils.MessageBox;
import org.jackhuang.hellominecraftlauncher.utilities.SettingsManager;
import org.jackhuang.hellominecraftlauncher.apis.utils.Utils;
import org.jackhuang.hellominecraftlauncher.settings.Version;
import org.jackhuang.hellominecraftlauncher.utilities.FolderOpener;
import org.jackhuang.hellominecraftlauncher.utilities.ProcessThread;
import org.jackhuang.hellominecraftlauncher.apis.utils.Compressor;

/**
 *
 * @author hyh
 */
public class MainWindow extends javax.swing.JFrame implements ActionListener {
    // <editor-fold defaultstate="collapsed" desc="Private Variables">

    String backgroundPath;
    ImageIcon background = new ImageIcon(getClass().getResource("/background.jpg"));
    JLabel backgroundLabel;
    JPopupMenu ppmManager, ppmInclude, ppmManage;
    Version version;
    boolean indexSelected = false;
    ArrayList<Pair<String, String>> unzippings;
    int progress, max;
    Timer changeSizeTimer = new Timer(1, this);
    int changeOnce = 10, maxChange = 190;
    int changedSize; boolean isChangedSize = false;
    //</editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Constructor">
    public MainWindow() {

        initComponents();

        instance = this;

        Dimension scrSize = Toolkit.getDefaultToolkit().getScreenSize();
        setLocation((scrSize.width - this.getWidth()) / 2,
                (scrSize.height - this.getHeight()) / 2);

        this.setIconImage(new ImageIcon(getClass().getResource("/icon.png")).getImage());

        //if (SettingsManager.settings.selfWidth == 0) {
        //    SettingsManager.settings.selfWidth = 406;
        //}
        //if (SettingsManager.settings.selfHeight == 0) {
        //    SettingsManager.settings.selfHeight = 300;
        //}
        this.setSize(new Dimension(410, 300));
        this.setTitle(Main.makeTitle());
        loadBackground();
        pnlMoreIn.setBackground(Color.LIGHT_GRAY);
        pnlMoreIn.setOpaque(true);
        btnShowMore.setForeground(Color.red);

        refreshMinecrafts(SettingsManager.settings.publicSettings.gameDir,
                SettingsManager.settings.last);

        txtPlayerName.setText(SettingsManager.settings.username);
        //refreshDownloads();
        SetupWindow.getInstance().prepare(version);
        prepareAuths();

        /*addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                resizeBackgroundLabel();
            }
        });*/

        java.util.ResourceBundle bundle = C.I18N;
        //<editor-fold defaultstate="collapsed" desc="Edit Menu">
        ppmManager = new JPopupMenu();
        JMenuItem itm = new JMenuItem(bundle.getString("删除"));
        itm.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                onDelete(e);
            }
        });
        ppmManager.add(itm);
        itm = new JMenuItem(bundle.getString("新建"));
        itm.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                onNew(e);
            }
        });
        ppmManager.add(itm);
        itm = new JMenuItem(bundle.getString("复制"));
        itm.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                onCopy(e);
            }
        });
        ppmManager.add(itm);
        //</editor-fold>
        //<editor-fold defaultstate="collapsed" desc="Include Menu">
        ppmInclude = new JPopupMenu();
        itm = new JMenuItem(bundle.getString("导入旧版本"));
        itm.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                onInclude(e);
            }
        });
        ppmInclude.add(itm);
        itm = new JMenuItem(bundle.getString("导入文件夹"));
        itm.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                onIncludeFolder(e);
            }
        });
        ppmInclude.add(itm);
        //</editor-fold>
        //<editor-fold defaultstate="collapsed" desc="Manage Menu">
        ppmManage = new JPopupMenu();
        itm = new JMenuItem(bundle.getString("Mods文件夹"));
        itm.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                Version v = getVersion();
                if (v != null) {
                    FolderOpener.openMods(v);
                }
            }
        });
        ppmManage.add(itm);
        itm = new JMenuItem(bundle.getString("Coremods文件夹"));
        itm.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                Version v = getVersion();
                if (v != null) {
                    FolderOpener.openCoreMods(v);
                }
            }
        });
        ppmManage.add(itm);
        itm = new JMenuItem(bundle.getString("配置文件夹"));
        itm.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                Version v = getVersion();
                if (v != null) {
                    FolderOpener.openConfig(v);
                }
            }
        });
        ppmManage.add(itm);
        itm = new JMenuItem(bundle.getString("Mod独立文件夹"));
        itm.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                Version v = getVersion();
                if (v != null) {
                    FolderOpener.openModDir(v);
                }
            }
        });
        ppmManage.add(itm);
        //</editor-fold>
        checkUpdate(true);

        for (JMenuItem item : PluginHandler.operationsMenuItems) {
            ppmManager.add(item);
        }
        for (JMenuItem item : PluginHandler.managingsMenuItems) {
            ppmManage.add(item);
        }
        for (JMenuItem item : PluginHandler.importingsMenuItems) {
            ppmInclude.add(item);
        }
    }
    //</editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Utilities">
    /*private ImageIcon getResizedImage() {
        Image image = background.getImage();
        //image = image.getScaledInstance(this.getWidth(), this.getHeight(), Image.SCALE_FAST);
        background.setImage(image);
        return new ImageIcon(image);
    }*/

    private void prepareAuths() {
        List<IPluginHandler> list = PluginHandler.getPluginHandlers(PluginHandlerType.getType("LOGIN"));
        for (IPluginHandler str : list) {
            try {
                cboLoginMode.addItem(((Login) str).getName());
            } catch (Exception ex) {
                Logger.getLogger(MainWindow.class.getName()).log(Level.SEVERE, null, ex);
                MessageBox.Show(C.I18N.getString("CannotLoadPluginsBecauseOfAnError"));
            }
        }
        if (SettingsManager.settings.logintype < list.size()) {
            indexSelected = true;
            cboLoginMode.setSelectedIndex(SettingsManager.settings.logintype);
        }
    }

    public Version getVersion() {
        return getVersion(true);
    }

    public Version getVersion(boolean show) {
        final String name = (String) cboMinecrafts.getSelectedItem();
        if (Utils.isEmpty(name)) {
            if (show) {
                MessageBox.Show(C.I18N.getString("没有选择任何一个MINECRAFT版本"));
            }
            return null;
        }
        return SettingsManager.getVersion(name);
    }

    void checkUpdate(final boolean isFirst) {
        CheckUpdate.check(new DoneListener<UpdateRequest, Object>() {
            @Override
            public void onDone(UpdateRequest value, Object value2) {
                if (!SettingsManager.settings.checkUpdate) {
                    return;
                }
                if (value == null) {
                    if (!isFirst) {
                        MessageBox.Show(C.I18N.getString("检查更新失败"));
                    }
                } else {
                    boolean isold = false;
                    if (Main.firstVer < value.firstVer) {
                        isold = true;
                    } else if (Main.firstVer == value.firstVer) {
                        if (Main.secondVer < value.secondVer) {
                            isold = true;
                        } else if (Main.secondVer == value.secondVer) {
                            if (Main.thirdVer < value.thirdVer) {
                                isold = true;
                            }
                        }
                    }
                    if (isold) {
                        if (MessageBox.Show(C.I18N.getString("最新版本为：") + value.firstVer + "." + value.secondVer + "." + value.thirdVer + "\n"
                                + C.I18N.getString("更新信息为: ") + value.updateNote + "\n"
                                + C.I18N.getString("是否前往发布页面更新？"),
                                MessageBox.YES_NO_OPTION) == MessageBox.YES_OPTION) {
                            boolean isBrowsed = false;
                            //判断当前系统是否支持Java AWT Desktop扩展
                            if (java.awt.Desktop.isDesktopSupported()) {
                                try {
//创建一个URI实例
                                    java.net.URI uri = java.net.URI.create(C.URL_PUBLISH);
//获取当前系统桌面扩展
                                    java.awt.Desktop dp = java.awt.Desktop.getDesktop();
//判断系统桌面是否支持要执行的功能
                                    if (dp.isSupported(java.awt.Desktop.Action.BROWSE)) {
//获取系统默认浏览器打开链接
                                        dp.browse(uri);
                                        isBrowsed = true;
                                    }
                                } catch (java.lang.NullPointerException e) {
//此为uri为空时抛出异常
                                } catch (java.io.IOException e) {
//此为无法获取系统默认浏览器
                                }
                            }
                            if (!isBrowsed) {
                                Clipboard cb = Toolkit.getDefaultToolkit().getSystemClipboard();
                                cb.setContents(new StringSelection(C.URL_PUBLISH), null);
                                MessageBox.Show(C.I18N.getString("无法打开浏览器，网址已经复制到剪贴板了，您可以手动粘贴网址打开页面"));
                            }
                        } else {
                            SettingsManager.settings.checkUpdate = false;
                            SettingsManager.save();
                        }
                    } else {
                        if (!isFirst) {
                            MessageBox.Show(C.I18N.getString("当前是最新版本"));
                        }
                    }
                }
            }
        });
    }
    
    @Override
    public void actionPerformed(ActionEvent e) {
        Point d = (Point) this.pnlMore.getLocation();
        if(isChangedSize) {
            d.x += changeOnce;
            changedSize += changeOnce;
            if (changedSize >= maxChange) {
                changeSizeTimer.stop();
            }
        } else {
            d.x -= changeOnce;
            changedSize -= changeOnce;
            if (changedSize <= 0) {
                changeSizeTimer.stop();
            }
        }
        pnlMore.setLocation(d);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Public Events">
    public void onRefreshMinecrafts() {
        refreshMinecrafts(SettingsManager.settings.publicSettings.gameDir,
                SettingsManager.settings.last);
    }

    public void onResizeBackgroundLabel(String path) {
        background = new ImageIcon(Toolkit.getDefaultToolkit().getImage(path));
        backgroundLabel.setIcon((background));
        //resizeBackgroundLabel();
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Game Launch">
    void generateLaunchString(final DoneListener<List, GameLauncher> listener) {
        File file = new File(SettingsManager.settings.publicSettings.gameDir);
        if (!file.exists()) {
            MessageBox.Show(C.I18N.getString("错误的MINECRAFT路径"));
            return;
        }
        final String name = (String) cboMinecrafts.getSelectedItem();
        if (Utils.isEmpty(name)) {
            MessageBox.Show(C.I18N.getString("没有选择任何一个MINECRAFT版本"));
            return;
        }
        SettingsManager.settings.last = name;
        SettingsManager.save();

        final Version get = SettingsManager.getVersion(name);

        String playerName = SettingsManager.settings.username;

        if (cboLoginMode.getItemCount() == 0) {
            MessageBox.Show(C.I18N.getString("没有登入方式..."));
            return;
        }
        final int index = cboLoginMode.getSelectedIndex();
        final LoginInfo li = ((Login) PluginHandler.getPluginHandlers(PluginHandlerType.getType("LOGIN")).get(index)).isLoggedIn()
                ? null
                : new LoginInfo(playerName, new String(txtPassword.getPassword()));
        Thread t = new Thread() {
            @Override
            public void run() {
                GameLauncher gl = new GameLauncher(get, li, index);
                gl.addListener(0, new DoneListener<List, GameLauncher>() {
                    @Override
                    public void onDone(List value, GameLauncher value2) {
                        MessageBox.Show((String) value.get(0));
                    }
                });
                gl.addListener(2, new DoneListener<List, GameLauncher>() {
                    @Override
                    public void onDone(List value, GameLauncher value2) {
                        DownloadWindow dw = new DownloadWindow();
                        for (Object o : value) {
                            String[] s = (String[]) o;
                            dw.addDownloadURL(s[0], s[1], s[2]);
                        }
                        dw.setVisible(true);
                    }
                });
                gl.addListener(3, new DoneListener<List, GameLauncher>() {
                    @Override
                    public void onDone(List value, GameLauncher value2) {
                        for (Object o : value) {
                            String[] p = (String[]) o;
                            Compressor.unzip(p[0], p[1]);
                        }
                    }
                });

                gl.addListener(100, listener);
                gl.makeLaunchCommand();
            }
        };
        t.start();
    }
    //</editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="Loads">
    HashMap<String, Boolean> visited;

    void loadFromSettings() {
        if (SettingsManager.settings == null) {
            return;
        }
        if (SettingsManager.settings.versions == null) {
            return;
        }
        for (int i = 0; i < SettingsManager.settings.versions.size(); i++) {
            Version v = SettingsManager.settings.versions.get(i);
            visited.put(v.name, true);
            cboMinecrafts.addItem(v.name);
        }
    }

    void loadFromGameDir(String mp) {
        File f = new File(Utils.addSeparator(mp) + "versions" + File.separator);
        ArrayList<String> s = Utils.findAllDir(f);
        for (int i = 0; i < s.size(); i++) {
            String name = s.get(i);
            if (!visited.containsKey(name)) {
                cboMinecrafts.addItem(name);
                Version v = new Version();
                v.name = name;
                v.gameDir = mp;
                SettingsManager.setVersion(v);
            }
        }
        SettingsManager.save();
    }

    /*private void resizeBackgroundLabel() {
        if (backgroundLabel == null) {
            return;
        }
        backgroundLabel.setIcon(getResizedImage());
        backgroundLabel.setBounds(0, 0, this.getWidth(), this.getHeight());
    }*/

    private void refreshMinecrafts(String gameDir, String last) {
        if (visited != null) {
            visited.clear();
        } else {
            visited = new HashMap<String, Boolean>();
        }
        cboMinecrafts.removeAllItems();
        loadFromSettings();
        loadFromGameDir(gameDir);
        for (int i = 0; i < cboMinecrafts.getItemCount(); i++) {
            String s = (String) cboMinecrafts.getItemAt(i);
            if (s.equals(last)) {
                cboMinecrafts.setSelectedIndex(i);
                break;
            }
        }
        version = getVersion(false);
    }

    private void loadBackground() {
        File backgroundImageFile = new File("background.png");
        if (backgroundImageFile.exists()) {
            backgroundPath = "background.png";
            background = new ImageIcon(Toolkit.getDefaultToolkit().getImage(backgroundImageFile.getAbsolutePath()));
        }

        if (!Utils.isEmpty(SettingsManager.settings.bgpath)) {
            backgroundPath = SettingsManager.settings.bgpath;
            background = new ImageIcon(Toolkit.getDefaultToolkit().getImage(SettingsManager.settings.bgpath));
        }
        backgroundLabel = new JLabel(background);
        backgroundLabel.setBounds(0, 0, this.getWidth(), this.getHeight());
        this.getContentPane().add(backgroundLabel, -1);
    }
    //</editor-fold>

    // <editor-fold defaultstate="collapsed" desc="PopupMenu">
    private void onDelete(java.awt.event.ActionEvent evt) {
        Object o = (cboMinecrafts.getSelectedItem());
        if (o == null) {
            MessageBox.Show(C.I18N.getString("未选中任何一个版本"));
            return;
        }
        cboMinecrafts.removeItem(o);

        String name = (String) o;
        SettingsManager.delVersion(SettingsManager.getVersion(name));
        SettingsManager.save();
    }

    private void onNew(java.awt.event.ActionEvent evt) {
        Version version = new Version();
        NewVersionWindow window = new NewVersionWindow(this, true, version);
        window.setVisible(true);
    }

    private void onCopy(java.awt.event.ActionEvent evt) {
        String toName = JOptionPane.showInputDialog(this, C.I18N.getString("输入新版本名称"), C.I18N.getString("提示"),
                JOptionPane.INFORMATION_MESSAGE);
        final String name = (String) cboMinecrafts.getSelectedItem();
        if (name == null || name.equals("")) {
            MessageBox.Show(C.I18N.getString("没有选择任何一个MINECRAFT版本"));
            return;
        }
        Version get = SettingsManager.getVersion(name);
        VersionCopier.copy(Utils.getGameDir(get, SettingsManager.settings.publicSettings.gameDir), name, toName);
    }

    private void onIncludeFolder(java.awt.event.ActionEvent evt) {
        JFileChooser fc = new JFileChooser();
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fc.setDialogTitle(C.I18N.getString("选择MINECRAFT路径"));
        fc.setMultiSelectionEnabled(false);
        fc.showOpenDialog(this);
        try {
            String path = fc.getSelectedFile().getCanonicalPath();
            if (path == null) {
                return;
            }
            if (Utils.is16Folder(path)) {
                refreshMinecrafts(path,
                        SettingsManager.settings.last);
            } else {
                path = Utils.removeLastSeparator(path);
                String realpa = Utils.addSeparator(path);
                File file = new File(realpa + "bin" + File.separator + "minecraft.jar");
                if (!file.exists()) {
                    MessageBox.Show(C.I18N.getString("这个MINECRAFT文件夹既不是1.6版也不是旧版的怎么导入嘛0V0。"));
                    return;
                }
                Version v = new Version();
                v.gameDir = path;
                v.isVer16 = false;
                NewVersionWindow window = new NewVersionWindow(this, true, v);
                window.setVisible(true);
            }
        } catch (Exception e) {
            MessageBox.Show(e.getMessage());
        }
    }

    private void onInclude(java.awt.event.ActionEvent evt) {
        JFileChooser fc = new JFileChooser();
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fc.setDialogTitle(C.I18N.getString("选择MINECRAFT路径"));
        fc.setMultiSelectionEnabled(false);
        fc.showOpenDialog(this);
        try {
            String mcp = fc.getSelectedFile().getCanonicalPath();
            if (mcp == null || mcp.trim().equals("")) {
                return;
            }
            File file = new File(mcp + File.separator + "bin" + File.separator + "minecraft.jar");
            if (!file.exists()) {
                MessageBox.Show(C.I18N.getString("找不到MINECRAFT.JAR"));
                return;
            }

            String name = JOptionPane.showInputDialog(C.I18N.getString("请输入该旧版本的名称"));
            file = new File(Utils.addSeparator(SettingsManager.settings.publicSettings.gameDir)
                    + "versions" + File.separator + name);
            if (file.exists()) {
                MessageBox.Show(C.I18N.getString("找到同名的MINECRAFT版本，请换用另一个名称"));
                return;
            }

            MinecraftOldVersionIncluder l = new MinecraftOldVersionIncluder(mcp, name);
            l.include();

            refreshMinecrafts(SettingsManager.settings.publicSettings.gameDir,
                    SettingsManager.settings.last);
        } catch (Exception e) {
            MessageBox.Show(
                    java.text.MessageFormat.format(
                    C.I18N.getString("导入失败: {0}"), new Object[]{e.getMessage()}));
        }
    }
    //</editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Statics">
    // <editor-fold defaultstate="collapsed" desc="Public Static Functions">
    public static void show(String args[]) {
        instance.setVisible(true);
    }

    public static MainWindow getInstance() {
        return instance;
    }

    static {
        instance = new MainWindow();
    }
    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Static Variables">
    private static MainWindow instance;
    // </editor-fold>
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="UI">
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        pnlMore = new javax.swing.JPanel();
        btnShowMore = new javax.swing.JButton();
        pnlMoreIn = new javax.swing.JPanel();
        jPanel1 = new javax.swing.JPanel();
        jLabel10 = new javax.swing.JLabel();
        cboMinecrafts = new javax.swing.JComboBox();
        btnRefresh = new javax.swing.JButton();
        btnShowManageMenu = new javax.swing.JButton();
        btnManage = new javax.swing.JButton();
        btnShowIncludeMenu = new javax.swing.JButton();
        btnVersionEdit = new javax.swing.JButton();
        jPanel2 = new javax.swing.JPanel();
        txtPlayerName = new javax.swing.JTextField();
        jLabel7 = new javax.swing.JLabel();
        cboLoginMode = new javax.swing.JComboBox();
        jLabel8 = new javax.swing.JLabel();
        pnlPassword = new javax.swing.JPanel();
        jPanel3 = new javax.swing.JPanel();
        txtPassword = new javax.swing.JPasswordField();
        lblPassword = new javax.swing.JLabel();
        jPanel20 = new javax.swing.JPanel();
        btnLogOut = new javax.swing.JButton();
        jPanel4 = new javax.swing.JPanel();
        btnMakeLauncher = new javax.swing.JButton();
        txtShowOptions = new javax.swing.JButton();
        btnRun = new javax.swing.JButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setBackground(new java.awt.Color(153, 153, 255));
        setResizable(false);
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent evt) {
                formWindowClosing(evt);
            }
        });
        getContentPane().setLayout(null);

        pnlMore.setOpaque(false);
        pnlMore.setLayout(null);

        btnShowMore.setText("更多");
        btnShowMore.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnShowMoreActionPerformed(evt);
            }
        });
        pnlMore.add(btnShowMore);
        btnShowMore.setBounds(190, 0, 60, 20);

        pnlMoreIn.setOpaque(false);
        pnlMoreIn.setLayout(null);

        java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("org/jackhuang/hellominecraftlauncher/I18N"); // NOI18N
        jLabel10.setText(bundle.getString("游戏版本")); // NOI18N

        cboMinecrafts.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                cboMinecraftsItemStateChanged(evt);
            }
        });

        btnRefresh.setText(bundle.getString("刷新")); // NOI18N
        btnRefresh.setToolTipText("刷新列表");
        btnRefresh.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnRefreshActionPerformed(evt);
            }
        });

        btnShowManageMenu.setText(bundle.getString("操作")); // NOI18N
        btnShowManageMenu.setToolTipText("一些对版本的操作");
        btnShowManageMenu.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                btnShowManageMenuMouseClicked(evt);
            }
        });

        btnManage.setText(bundle.getString("管理")); // NOI18N
        btnManage.setToolTipText("对MC版本的模组管理");
        btnManage.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                btnManageMouseClicked(evt);
            }
        });

        btnShowIncludeMenu.setText(bundle.getString("导入")); // NOI18N
        btnShowIncludeMenu.setToolTipText("导入不在全局设置中游戏路径的Minecraft");
        btnShowIncludeMenu.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                btnShowIncludeMenuMouseClicked(evt);
            }
        });

        btnVersionEdit.setText("版本管理");
        btnVersionEdit.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnVersionEditActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addGap(0, 0, 0)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(cboMinecrafts, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addComponent(btnRefresh)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 56, Short.MAX_VALUE)
                        .addComponent(btnShowManageMenu))
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addComponent(btnShowIncludeMenu)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(btnManage))
                    .addGroup(jPanel1Layout.createSequentialGroup()
                        .addComponent(jLabel10)
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addComponent(btnVersionEdit, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addGap(0, 0, 0))
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel10)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(cboMinecrafts, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(btnRefresh)
                    .addComponent(btnShowManageMenu))
                .addGap(6, 6, 6)
                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(btnShowIncludeMenu)
                    .addComponent(btnManage))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(btnVersionEdit)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        pnlMoreIn.add(jPanel1);
        jPanel1.setBounds(10, 10, 170, 150);

        jPanel2.setBackground(new java.awt.Color(204, 204, 204));
        jPanel2.setOpaque(false);

        txtPlayerName.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusLost(java.awt.event.FocusEvent evt) {
                txtPlayerNameFocusLost(evt);
            }
        });
        txtPlayerName.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyPressed(java.awt.event.KeyEvent evt) {
                txtPlayerNameKeyPressed(evt);
            }
        });

        jLabel7.setText(bundle.getString("登录模式")); // NOI18N

        cboLoginMode.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                cboLoginModeItemStateChanged(evt);
            }
        });

        jLabel8.setText(bundle.getString("游戏名称")); // NOI18N

        pnlPassword.setLayout(new java.awt.CardLayout());

        jPanel3.setMinimumSize(new java.awt.Dimension(0, 0));

        lblPassword.setText(bundle.getString("密码")); // NOI18N

        javax.swing.GroupLayout jPanel3Layout = new javax.swing.GroupLayout(jPanel3);
        jPanel3.setLayout(jPanel3Layout);
        jPanel3Layout.setHorizontalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addComponent(lblPassword)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(txtPassword, javax.swing.GroupLayout.DEFAULT_SIZE, 140, Short.MAX_VALUE))
        );
        jPanel3Layout.setVerticalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(lblPassword)
                    .addComponent(txtPassword, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(0, 2, Short.MAX_VALUE))
        );

        pnlPassword.add(jPanel3, "card2");

        jPanel20.setMinimumSize(new java.awt.Dimension(0, 0));

        btnLogOut.setText(bundle.getString("Logout")); // NOI18N
        btnLogOut.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnLogOutActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel20Layout = new javax.swing.GroupLayout(jPanel20);
        jPanel20.setLayout(jPanel20Layout);
        jPanel20Layout.setHorizontalGroup(
            jPanel20Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(btnLogOut, javax.swing.GroupLayout.DEFAULT_SIZE, 168, Short.MAX_VALUE)
        );
        jPanel20Layout.setVerticalGroup(
            jPanel20Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel20Layout.createSequentialGroup()
                .addComponent(btnLogOut)
                .addGap(0, 0, Short.MAX_VALUE))
        );

        pnlPassword.add(jPanel20, "card3");

        javax.swing.GroupLayout jPanel2Layout = new javax.swing.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addGap(2, 2, 2)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(pnlPassword, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(javax.swing.GroupLayout.Alignment.LEADING, jPanel2Layout.createSequentialGroup()
                        .addComponent(jLabel7)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(cboLoginMode, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(javax.swing.GroupLayout.Alignment.LEADING, jPanel2Layout.createSequentialGroup()
                        .addComponent(jLabel8)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(txtPlayerName)))
                .addGap(0, 0, 0))
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel7)
                    .addComponent(cboLoginMode, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(6, 6, 6)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel2Layout.createSequentialGroup()
                        .addGap(3, 3, 3)
                        .addComponent(jLabel8))
                    .addComponent(txtPlayerName, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(pnlPassword, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        pnlMoreIn.add(jPanel2);
        jPanel2.setBounds(10, 170, 170, 102);

        pnlMore.add(pnlMoreIn);
        pnlMoreIn.setBounds(0, 0, 190, 280);

        getContentPane().add(pnlMore);
        pnlMore.setBounds(-190, 0, 250, 290);

        btnMakeLauncher.setText(bundle.getString("制作一键启动")); // NOI18N
        btnMakeLauncher.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnMakeLauncherActionPerformed(evt);
            }
        });

        txtShowOptions.setText("设置");
        txtShowOptions.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                txtShowOptionsActionPerformed(evt);
            }
        });

        btnRun.setText(bundle.getString("开始MINECRAFT之旅!")); // NOI18N
        btnRun.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnRunActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel4Layout = new javax.swing.GroupLayout(jPanel4);
        jPanel4.setLayout(jPanel4Layout);
        jPanel4Layout.setHorizontalGroup(
            jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel4Layout.createSequentialGroup()
                .addGap(0, 4, Short.MAX_VALUE)
                .addComponent(btnRun, javax.swing.GroupLayout.PREFERRED_SIZE, 140, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(btnMakeLauncher, javax.swing.GroupLayout.PREFERRED_SIZE, 120, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(txtShowOptions, javax.swing.GroupLayout.PREFERRED_SIZE, 120, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(0, 0, 0))
        );
        jPanel4Layout.setVerticalGroup(
            jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel4Layout.createSequentialGroup()
                .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                    .addComponent(btnRun, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(jPanel4Layout.createSequentialGroup()
                        .addComponent(txtShowOptions)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnMakeLauncher, javax.swing.GroupLayout.PREFERRED_SIZE, 24, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        getContentPane().add(jPanel4);
        jPanel4.setBounds(130, 210, 270, 60);

        pack();
    }// </editor-fold>//GEN-END:initComponents

    // <editor-fold defaultstate="collapsed" desc="Events">
    private void btnRunActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnRunActionPerformed
        final java.util.ResourceBundle bundle = C.I18N;
        btnRun.setText(bundle.getString("准备中"));
        generateLaunchString(new DoneListener<List, GameLauncher>() {
            @Override
            public void onDone(List str, GameLauncher obj) {
                PluginManager.unloadPlugin();
                btnRun.setText(bundle.getString("开始MINECRAFT之旅!"));

                obj.addListener(4, new DoneListener<List, GameLauncher>() {
                    @Override
                    public void onDone(List value, GameLauncher value2) {
                        final JavaProcess p = (JavaProcess) value.get(0);
                        ProcessThread thread = new ProcessThread(p,
                                new ProcessThread.ProcessListener() {
                            @Override
                            public void onStop() {
                                int exitCode = p.getExitCode();
                                if (exitCode == 0) {
                                    if (!LogWindow.instance.isVisible()) {
                                        System.exit(0);
                                    }
                                } else {
                                    LogWindow.instance.clean();
                                    LogWindow.instance.log(java.text.MessageFormat.format(C.I18N.getString("MinecraftCrashed"), new Object[]{exitCode}));
                                    String errorText = null;
                                    String[] sysOut = (String[]) p.getSysOutLines().getItems();

                                    for (int i = sysOut.length - 1; i >= 0; i--) {
                                        String line = sysOut[i];
                                        String crashIdentifier = "#@!@#";
                                        int pos = line.lastIndexOf(crashIdentifier);

                                        if ((pos >= 0) && (pos < line.length() - crashIdentifier.length() - 1)) {
                                            errorText = line.substring(pos + crashIdentifier.length()).trim();
                                            break;
                                        }
                                    }

                                    if (errorText != null) {
                                        File file = new File(errorText);

                                        if (file.isFile()) {
                                            LogWindow.instance.log(C.I18N.getString("CRASH REPORT DETECTED, OPENING:") + ' ' + errorText);
                                            InputStream inputStream = null;
                                            try {
                                                inputStream = new FileInputStream(file);
                                                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                                                StringBuilder result = new StringBuilder();
                                                String line;
                                                while ((line = reader.readLine()) != null) {
                                                    if (result.length() > 0) {
                                                        result.append("\n");
                                                    }
                                                    result.append(line);
                                                }

                                                reader.close();
                                                LogWindow.instance.log(result.toString());
                                            } catch (IOException e) {
                                                LogWindow.instance.log(java.util.ResourceBundle.getBundle("org/jackhuang/hellominecraftlauncher/I18N").getString("COULDN'T OPEN CRASH REPORT"));
                                                LogWindow.instance.log(e.getMessage());
                                            } finally {
                                            }
                                        } else {
                                            LogWindow.instance.log(C.I18N.getString("CRASH REPORT DETECTED, BUT UNKNOWN FORMAT:") + ' ' + errorText);
                                        }
                                    }
                                    LogWindow.instance.setVisible(true);
                                }
                            }

                            @Override
                            public void onPrintln(String line) {
                                LogWindow.instance.log(line);
                            }
                        });
                        thread.start();
                    }
                });
                obj.launch(str);
                MainWindow.this.setVisible(false);
            }
        });
    }//GEN-LAST:event_btnRunActionPerformed

    private void btnRefreshActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnRefreshActionPerformed
        onRefreshMinecrafts();
    }//GEN-LAST:event_btnRefreshActionPerformed

    private void txtPlayerNameFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_txtPlayerNameFocusLost
        SettingsManager.settings.username = txtPlayerName.getText();
        SettingsManager.save();
    }//GEN-LAST:event_txtPlayerNameFocusLost

    private void btnMakeLauncherActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnMakeLauncherActionPerformed
        btnMakeLauncher.setText(C.I18N.getString("准备中"));
        btnMakeLauncher.updateUI();
        generateLaunchString(new DoneListener<List, GameLauncher>() {
            @Override
            public void onDone(List cmd, GameLauncher obj) {

                btnMakeLauncher.setText(C.I18N.getString("制作一键启动"));
                btnMakeLauncher.updateUI();

                obj.makeLauncher("launch", cmd);
            }
        });
    }//GEN-LAST:event_btnMakeLauncherActionPerformed

    private void cboMinecraftsItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_cboMinecraftsItemStateChanged
        if (cboMinecrafts.getSelectedIndex() != -1 && !Utils.isEmpty((String) cboMinecrafts.getSelectedItem())) {
            SettingsManager.settings.last = (String) cboMinecrafts.getSelectedItem();
            SettingsManager.save();
            version = getVersion(false);
            if (version != null) {
                SetupWindow.getInstance().prepare(version);
            }
        }
    }//GEN-LAST:event_cboMinecraftsItemStateChanged

    private void btnShowManageMenuMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_btnShowManageMenuMouseClicked
        ppmManager.show(evt.getComponent(), evt.getPoint().x, evt.getPoint().y);
    }//GEN-LAST:event_btnShowManageMenuMouseClicked

    private void btnShowIncludeMenuMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_btnShowIncludeMenuMouseClicked
        ppmInclude.show(evt.getComponent(), evt.getPoint().x, evt.getPoint().y);
    }//GEN-LAST:event_btnShowIncludeMenuMouseClicked

    private void cboLoginModeItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_cboLoginModeItemStateChanged
        if (!PluginManager.preparedPlugins || !indexSelected) {
            return;
        }
        int index = cboLoginMode.getSelectedIndex();
        SettingsManager.settings.logintype = index;

        Login l = (Login) PluginHandler.getPluginHandlers(PluginHandlerType.getType("LOGIN")).get(index);
        if (l.isHidePasswordBox()) {
            lblPassword.setVisible(false);
            pnlPassword.setVisible(false);
        } else {
            lblPassword.setVisible(true);
            pnlPassword.setVisible(true);
        }

        CardLayout cl = (CardLayout) pnlPassword.getLayout();
        if (l.isLoggedIn()) {
            cl.last(pnlPassword);
        } else {
            cl.first(pnlPassword);
        }

        String username = SettingsManager.settings.username;
        if (!Utils.isEmpty(username)) {
            txtPlayerName.setText(username);
        }

        SettingsManager.save();
    }//GEN-LAST:event_cboLoginModeItemStateChanged

    private void btnManageMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_btnManageMouseClicked
        ppmManage.show(evt.getComponent(), evt.getPoint().x, evt.getPoint().y);
    }//GEN-LAST:event_btnManageMouseClicked

    private void formWindowClosing(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowClosing
        /*SettingsManager.settings.selfHeight = this.getHeight();
        SettingsManager.settings.selfWidth = this.getWidth();
        System.out.println("Saving size: " + this.getSize());
        SettingsManager.save();*/
    }//GEN-LAST:event_formWindowClosing

    private void txtPlayerNameKeyPressed(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_txtPlayerNameKeyPressed
        if (!txtPassword.isEnabled()) {
            txtPassword.setEnabled(true);
        }
    }//GEN-LAST:event_txtPlayerNameKeyPressed

    private void btnLogOutActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnLogOutActionPerformed
        if (!PluginManager.preparedPlugins) {
            return;
        }
        int index = cboLoginMode.getSelectedIndex();
        Login l = (Login) PluginHandler.getPluginHandlers(PluginHandlerType.getType("LOGIN")).get(index);
        l.logout();
        CardLayout cl = (CardLayout) pnlPassword.getLayout();
        cl.first(pnlPassword);
    }//GEN-LAST:event_btnLogOutActionPerformed

    private void txtShowOptionsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_txtShowOptionsActionPerformed
        OptionsWindow.getInstance().setVisible(true);
    }//GEN-LAST:event_txtShowOptionsActionPerformed

    private void btnShowMoreActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnShowMoreActionPerformed
        isChangedSize = !isChangedSize;
        changeSizeTimer.start();
    }//GEN-LAST:event_btnShowMoreActionPerformed

    private void btnVersionEditActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnVersionEditActionPerformed
        SetupWindow.getInstance().setVisible(true);
    }//GEN-LAST:event_btnVersionEditActionPerformed
    //</editor-fold>
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton btnLogOut;
    private javax.swing.JButton btnMakeLauncher;
    private javax.swing.JButton btnManage;
    private javax.swing.JButton btnRefresh;
    private javax.swing.JButton btnRun;
    private javax.swing.JButton btnShowIncludeMenu;
    private javax.swing.JButton btnShowManageMenu;
    private javax.swing.JButton btnShowMore;
    private javax.swing.JButton btnVersionEdit;
    private javax.swing.JComboBox cboLoginMode;
    private javax.swing.JComboBox cboMinecrafts;
    private javax.swing.JLabel jLabel10;
    private javax.swing.JLabel jLabel7;
    private javax.swing.JLabel jLabel8;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel20;
    private javax.swing.JPanel jPanel3;
    private javax.swing.JPanel jPanel4;
    private javax.swing.JLabel lblPassword;
    private javax.swing.JPanel pnlMore;
    private javax.swing.JPanel pnlMoreIn;
    private javax.swing.JPanel pnlPassword;
    private javax.swing.JPasswordField txtPassword;
    private javax.swing.JTextField txtPlayerName;
    private javax.swing.JButton txtShowOptions;
    // End of variables declaration//GEN-END:variables
    // </editor-fold>
}
