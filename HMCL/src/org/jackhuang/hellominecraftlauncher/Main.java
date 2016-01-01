/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jackhuang.hellominecraftlauncher;

import com.seaglasslookandfeel.SeaGlassLookAndFeel;
import java.awt.Font;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URLEncoder;
import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.X509TrustManager;
import javax.swing.UIManager;
import org.jackhuang.hellominecraftlauncher.apis.PluginHandlerType;
import org.jackhuang.hellominecraftlauncher.apis.handlers.Login;
import org.jackhuang.hellominecraftlauncher.apis.utils.MonitorInfoBean;
import org.jackhuang.hellominecraftlauncher.apis.utils.MonitorServiceImpl;
import org.jackhuang.hellominecraftlauncher.apis.utils.Utils;
import org.jackhuang.hellominecraftlauncher.download.URLGet;
import org.jackhuang.hellominecraftlauncher.installers.forge.BaseLauncherProfile;
import org.jackhuang.hellominecraftlauncher.plugin.PluginManager;
import org.jackhuang.hellominecraftlauncher.utilities.SettingsManager;
import org.jackhuang.hellominecraftlauncher.views.MainWindow;
import org.jackhuang.metro.MetroLookAndFeel;

/**
 *
 * @author hyh
 */
public class Main {

    // Create an anonymous class to trust all certificates.
    // This is bad style, you should create a separate class.
    private static X509TrustManager xtm = new X509TrustManager() {
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) {
            //System.out.println("---cert: " + chain[0].toString() + ", 认证方式: " + authType);
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return null;
        }
    };
    // Create an class to trust all hosts
    private static HostnameVerifier hnv = new HostnameVerifier() {
        @Override
        public boolean verify(String hostname, SSLSession session) {
            //System.out.println("hostname:111111111111111111111        " + hostname);
            return true;
        }
    };

    static {
        // 初始化TLS协议SSLContext
        // TrustManager
        SSLContext sslContext = null;

        try {
            sslContext = SSLContext.getInstance("TLS");
            X509TrustManager[] xtmArray = new X509TrustManager[]{xtm};
            sslContext.init(null, xtmArray, new java.security.SecureRandom());
        } catch (GeneralSecurityException gse) {
            //打印出一些错误信息和处理这个异常
        }

        //设置默认的SocketFactory和HostnameVerifier
        //为javax.net.ssl.HttpsURLConnection
        if (sslContext != null) {
            HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
        }

        HttpsURLConnection.setDefaultHostnameVerifier(hnv);
    }
    public static String launcherName = "Hello Minecraft! Launcher";
    public static int firstVer = 1, secondVer = 9, thirdVer = 4;
    public static int minimumLauncherVersion = 13;
    public static int pluginVersion = 1;
    
    public static String makeVersion() {
        return "" + firstVer + '.' + secondVer + '.' + thirdVer;
    }

    public static String makeTitle() {
        return launcherName + ' ' + makeVersion();
    }

    public static void main(String[] args) {

        try {
            PrintStream localPrintStream = System.out;
            File file = new File(Utils.addSeparator(Utils.currentDir()) + "hmcl.log");
            FileOutputStream fos = new FileOutputStream(file, false);
            LauncherOutputStream los = new LauncherOutputStream(localPrintStream, fos);
            System.setOut(new LauncherPrintStream(los));
            System.setErr(new LauncherPrintStream(los));
        } catch (FileNotFoundException ex) {
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
        }

        try {
            //UIManager.setLookAndFeel(new SeaGlassLookAndFeel());
            UIManager.setLookAndFeel(new MetroLookAndFeel());
            UIManager.getLookAndFeelDefaults().put("defaultFont", new Font("微软雅黑", Font.PLAIN, 12));
        } catch (Exception ex) {
            java.util.logging.Logger.getLogger(MainWindow.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        }
        //</editor-fold>

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                PluginManager.unloadPlugin();
            }
        });

        PluginHandlerType.registerPluginHandlerType("LOGIN", Login.class);

        SettingsManager.load();
        try {
            PluginManager.preparePlugins();
            PluginManager.loadPlugin();
        } catch (IOException ex) {
            Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        BaseLauncherProfile.tryWriteProfile(SettingsManager.settings.publicSettings.gameDir);
        
        Thread t = new Thread() {

            @Override
            public void run() {
                long m = 0;
                try {
                    MonitorInfoBean bean = new MonitorServiceImpl().getMonitorInfoBean();
                    m = bean.getTotalMemorySize() / 1024;
                } catch (Exception ex) {
                    Logger.getLogger(Main.class.getName()).log(Level.SEVERE, null, ex);
                }
                String url = "http://hellominecraftlauncher.duapp.com/count.php?os=" + URLEncoder.encode(System.getProperty("os.name")) + "&mac=" + Utils.getLocalMAC() +
                        "&memory=" + m + "&ver=" + makeVersion();
                URLGet.get(url);
            }
            
        };
        t.start();
        
        MainWindow.show(args);
    }
}
