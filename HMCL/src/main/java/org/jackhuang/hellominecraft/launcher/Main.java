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
package org.jackhuang.hellominecraft.launcher;

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.jar.JarFile;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.X509TrustManager;
import javax.swing.ImageIcon;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import org.jackhuang.hellominecraft.C;
import org.jackhuang.hellominecraft.HMCLog;
import org.jackhuang.hellominecraft.launcher.launch.GameLauncher;
import org.jackhuang.hellominecraft.launcher.utils.CrashReporter;
import org.jackhuang.hellominecraft.logging.Configuration;
import org.jackhuang.hellominecraft.logging.appender.ConsoleAppender;
import org.jackhuang.hellominecraft.logging.layout.DefaultLayout;
import org.jackhuang.hellominecraft.views.LogWindow;
import org.jackhuang.hellominecraft.launcher.settings.Settings;
import org.jackhuang.hellominecraft.launcher.utils.upgrade.Upgrader;
import org.jackhuang.hellominecraft.launcher.views.MainFrame;
import org.jackhuang.hellominecraft.lookandfeel.HelloMinecraftLookAndFeel;
import org.jackhuang.hellominecraft.tasks.TaskWindow;
import org.jackhuang.hellominecraft.utils.ArrayUtils;
import org.jackhuang.hellominecraft.utils.MathUtils;
import org.jackhuang.hellominecraft.utils.MessageBox;
import org.jackhuang.hellominecraft.utils.StrUtils;
import org.jackhuang.hellominecraft.utils.VersionNumber;
import org.jackhuang.hellominecraft.utils.system.FileUtils;
import org.jackhuang.hellominecraft.utils.system.IOUtils;
import org.jackhuang.hellominecraft.utils.system.OS;

/**
 *
 * @author huangyuhui
 */
public final class Main implements Runnable {

    private static final X509TrustManager XTM = new X509TrustManager() {
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) {
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return null;
        }
    };
    private static final HostnameVerifier HNV = (hostname, session) -> true;

    static {
        SSLContext sslContext = null;

        try {
            sslContext = SSLContext.getInstance("TLS");
            X509TrustManager[] xtmArray = new X509TrustManager[] {XTM};
            sslContext.init(null, xtmArray, new java.security.SecureRandom());
        } catch (GeneralSecurityException gse) {
        }
        if (sslContext != null)
            HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());

        HttpsURLConnection.setDefaultHostnameVerifier(HNV);
    }

    public static String launcherName = "Hello Minecraft! Launcher";
    public static byte firstVer = 2, secondVer = 3, thirdVer = 5, forthVer = 5;
    public static int minimumLauncherVersion = 16;
    public static Proxy PROXY;

    /**
     * Make the version of HMCL.
     *
     * @return the version: firstVer.secondVer.thirdVer
     */
    public static String makeVersion() {
        return "" + firstVer + '.' + secondVer + '.' + thirdVer + '.' + forthVer;
    }

    /**
     * Make the main window title.
     *
     * @return the MainWindow title.
     */
    public static String makeTitle() {
        return launcherName + ' ' + makeVersion();
    }

    public static final Main INSTANCE = new Main();

    @SuppressWarnings( {"CallToPrintStackTrace", "UseSpecificCatch"})
    public static void main(String[] args) {
        {
            if (!ArrayUtils.contains(args, "nofound"))
                try {
                    File f = Upgrader.HMCL_VER_FILE;
                    if (f.exists()) {
                        Map<String, String> m = C.gson.fromJson(FileUtils.readFileToString(f), Map.class);
                        String s = m.get("ver");
                        if (s != null && VersionNumber.check(s).compareTo(new VersionNumber(firstVer, secondVer, thirdVer)) > 0) {
                            String j = m.get("loc");
                            if (j != null) {
                                File jar = new File(j);
                                if (jar.exists()) {
                                    JarFile jarFile = new JarFile(jar);
                                    String mainClass = jarFile.getManifest().getMainAttributes().getValue("Main-Class");
                                    if (mainClass != null) {
                                        ArrayList<String> al = new ArrayList<>(Arrays.asList(args));
                                        al.add("notfound");
                                        new URLClassLoader(new URL[] {jar.toURI().toURL()},
                                                           URLClassLoader.getSystemClassLoader().getParent()).loadClass(mainClass)
                                        .getMethod("main", String[].class).invoke(null, new Object[] {al.toArray(new String[0])});
                                        return;
                                    }
                                }
                            }
                        }
                    }
                } catch (Throwable t) {
                    t.printStackTrace();
                }

            System.setProperty("sun.java2d.noddraw", "true");

            Thread.setDefaultUncaughtExceptionHandler(new CrashReporter(true));

            try {
                File file = new File("hmcl.log");
                if (!file.exists())
                    file.createNewFile();
                Configuration.DEFAULT.appenders.add(new ConsoleAppender("File", new DefaultLayout(), true, new FileOutputStream(file), true));
            } catch (IOException ex) {
                System.err.println("Failed to add log appender File because an error occurred while creating or opening hmcl.log");
                ex.printStackTrace();
            }

            HMCLog.log("*** " + Main.makeTitle() + " ***");

            LogWindow.instance.clean();
            LogWindow.instance.setTerminateGame(GameLauncher.PROCESS_MANAGER::stopAllProcesses);

            try {
                UIManager.setLookAndFeel(new HelloMinecraftLookAndFeel());
            } catch (ParseException | UnsupportedLookAndFeelException ex) {
                HMCLog.warn("Failed to set look and feel...", ex);
            }

            Settings.UPDATE_CHECKER.start();

            if (StrUtils.isNotBlank(Settings.getInstance().getProxyHost()) && StrUtils.isNotBlank(Settings.getInstance().getProxyPort()) && MathUtils.canParseInt(Settings.getInstance().getProxyPort())) {
                HMCLog.log("Initializing customized proxy");
                System.setProperty("http.proxyHost", Settings.getInstance().getProxyHost());
                System.setProperty("http.proxyPort", Settings.getInstance().getProxyPort());
                if (StrUtils.isNotBlank(Settings.getInstance().getProxyUserName()) && StrUtils.isNotBlank(Settings.getInstance().getProxyPassword()))
                    Authenticator.setDefault(new Authenticator() {
                        @Override
                        protected PasswordAuthentication getPasswordAuthentication() {
                            return new PasswordAuthentication(Settings.getInstance().getProxyUserName(), Settings.getInstance().getProxyPassword().toCharArray());
                        }
                    });
                PROXY = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(Settings.getInstance().getProxyHost(), Integer.parseInt(Settings.getInstance().getProxyPort())));
            } else {
                PROXY = Proxy.NO_PROXY;
            }

            try {
                MainFrame.showMainFrame(Settings.isFirstLoad());
            } catch (Throwable t) {
                new CrashReporter(false).uncaughtException(Thread.currentThread(), t);
                System.exit(1);
            }
        }
    }

    @Override
    public void run() {
        GameLauncher.PROCESS_MANAGER.stopAllProcesses();
    }

    public static void update() {
        Settings.UPDATE_CHECKER.requestDownloadLink(() -> {
            SwingUtilities.invokeLater(() -> {
                Map<String, String> map = Settings.UPDATE_CHECKER.download_link;
                if (map != null && map.containsKey("pack"))
                    try {
                        if (TaskWindow.getInstance().addTask(new Upgrader(map.get("pack"), Settings.UPDATE_CHECKER.versionString)).start()) {
                            new ProcessBuilder(new String[] {IOUtils.getJavaDir(), "-jar", Upgrader.getSelf(Settings.UPDATE_CHECKER.versionString).getAbsolutePath()}).directory(new File(".")).start();
                            System.exit(0);
                        }
                    } catch (IOException ex) {
                        HMCLog.warn("Failed to create upgrader", ex);
                    }
                if (MessageBox.Show(C.i18n("update.newest_version") + Settings.UPDATE_CHECKER.getNewVersion().firstVer + "." + Settings.UPDATE_CHECKER.getNewVersion().secondVer + "." + Settings.UPDATE_CHECKER.getNewVersion().thirdVer + "\n"
                                    + C.i18n("update.should_open_link"),
                                    MessageBox.YES_NO_OPTION) == MessageBox.YES_OPTION) {
                    String url = C.URL_PUBLISH;
                    if (map != null)
                        if (map.containsKey(OS.os().checked_name))
                            url = map.get(OS.os().checked_name);
                        else if (map.containsKey(OS.UNKOWN.checked_name))
                            url = map.get(OS.UNKOWN.checked_name);
                    if (url == null)
                        url = C.URL_PUBLISH;
                    try {
                        java.awt.Desktop.getDesktop().browse(new URI(url));
                    } catch (URISyntaxException | IOException e) {
                        HMCLog.warn("Failed to browse uri: " + url, e);

                        Clipboard cb = Toolkit.getDefaultToolkit().getSystemClipboard();
                        cb.setContents(new StringSelection(url), null);
                        MessageBox.Show(C.i18n("update.no_browser"));
                    }
                }
            });
        });
    }

    public static void invokeUpdate() {
        MainFrame.INSTANCE.invokeUpdate();
    }

    public static ImageIcon getIcon(String path) {
        try {
            return new ImageIcon(Main.class.getResource("/org/jackhuang/hellominecraft/launcher/" + path));
        } catch (Exception e) {
            HMCLog.err("Failed to load icon", e);
            return null;
        }
    }
}
