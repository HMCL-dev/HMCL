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
package org.jackhuang.hmcl;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.X509TrustManager;
import javax.swing.ImageIcon;
import javax.swing.RepaintManager;
import org.jackhuang.hmcl.api.HMCLApi;
import org.jackhuang.hmcl.api.HMCLog;
import org.jackhuang.hmcl.api.ILogger;
import org.jackhuang.hmcl.api.PluginManager;
import org.jackhuang.hmcl.api.VersionNumber;
import org.jackhuang.hmcl.setting.Settings;
import org.jackhuang.hmcl.ui.LogWindow;
import org.jackhuang.hmcl.ui.MainFrame;
import org.jackhuang.hmcl.util.CrashReporter;
import org.jackhuang.hmcl.util.DefaultPlugin;
import org.jackhuang.hmcl.util.MathUtils;
import org.jackhuang.hmcl.util.MessageBox;
import org.jackhuang.hmcl.util.StrUtils;
import org.jackhuang.hmcl.util.lang.SupportedLocales;
import org.jackhuang.hmcl.util.log.Configuration;
import org.jackhuang.hmcl.util.log.appender.ConsoleAppender;
import org.jackhuang.hmcl.util.log.layout.DefaultLayout;
import org.jackhuang.hmcl.util.ui.MyRepaintManager;
import org.jackhuang.hmcl.util.upgrade.IUpgrader;
import org.jackhuang.hmcl.laf.BeautyEyeLNFHelper;
import org.jackhuang.hmcl.util.sys.JavaProcess;

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
            return new X509Certificate[0];
        }
    };
    private static final HostnameVerifier HNV = (hostname, session) -> true;

    public static final String LAUNCHER_NAME = "Hello Minecraft! Launcher";
    public static final String LAUNCHER_VERSION = "@HELLO_MINECRAFT_LAUNCHER_VERSION_FOR_GRADLE_REPLACING@";
    public static final int MINIMUM_LAUNCHER_VERSION = 16;

    /**
     * Make the main window title.
     *
     * @return the MainWindow title.
     */
    public static String makeTitle() {
        return LAUNCHER_NAME + ' ' + LAUNCHER_VERSION;
    }

    public static String shortTitle() {
        return "HMCL" + ' ' + LAUNCHER_VERSION;
    }

    public static final Logger LOGGER = Logger.getLogger(Main.class.getName());

    public static void main(String[] args) throws IOException {
        {
            try {
                File file = new File("hmcl.log").getAbsoluteFile();
                if (!file.exists() && !file.createNewFile())
                    LOGGER.log(Level.WARNING, "Failed to create log file {0}", file);
                Configuration.DEFAULT.appenders.add(new ConsoleAppender("File", new DefaultLayout(), true, new FileOutputStream(file), true));
            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE, "Failed to add log appender File because an error occurred while creating or opening hmcl.log", ex);
            }
            
            org.jackhuang.hmcl.util.log.logger.Logger logger = new org.jackhuang.hmcl.util.log.logger.Logger("HMCL");
            HMCLog.LOGGER = new ILogger() {
                @Override
                public void log(String msg) {
                    logger.info(msg);
                }

                @Override
                public void warn(String msg) {
                    logger.warn(msg);
                }

                @Override
                public void warn(String msg, Throwable t) {
                    logger.warn(msg, t);
                }

                @Override
                public void err(String msg) {
                    logger.error(msg);
                }

                @Override
                public void err(String msg, Throwable t) {
                    logger.error(msg, t);
                }
            };

            HMCLApi.HMCL_VERSION = VersionNumber.check(LAUNCHER_VERSION);

            PluginManager.getPlugin(DefaultPlugin.class);
            for (String s : args)
                if (s.startsWith("--plugin=")) {
                    String c = s.substring("--plugin=".length());
                    try {
                        PluginManager.getPlugin(Class.forName(c));
                    } catch (ClassNotFoundException ex) {
                        LOGGER.log(Level.WARNING, "Class: " + c + " not found, please add your plugin jar to class path.", ex);
                    }
                } else if (s.startsWith("--help")) {
                    System.out.println("HMCL command line help");
                    System.out.println("--noupdate: this arg will prevent HMCL from initializing the newest app version in %appdata%/.hmcl");
                    System.out.println("--plugin=<your plugin class>: this arg will allow a new plugin to be loaded, please keep your jar in system class path and this class extends IPlugin.");
                    return;
                }
            PluginManager.loadPlugins();

            IUpgrader.NOW_UPGRADER.parseArguments(HMCLApi.HMCL_VERSION, args);

            System.setProperty("awt.useSystemAAFontSettings", "on");
            System.setProperty("swing.aatext", "true");
            System.setProperty("sun.java2d.noddraw", "true");
            System.setProperty("sun.java2d.dpiaware", "false");
            System.setProperty("https.protocols", "SSLv3,TLSv1");

            try {
                SSLContext c = SSLContext.getInstance("SSL");
                c.init(null, new X509TrustManager[] { XTM }, new java.security.SecureRandom());
                HttpsURLConnection.setDefaultSSLSocketFactory(c.getSocketFactory());
            } catch (GeneralSecurityException ignore) {
            }
            HttpsURLConnection.setDefaultHostnameVerifier(HNV);

            Thread.setDefaultUncaughtExceptionHandler(new CrashReporter(true));

            HMCLog.log("*** " + Main.makeTitle() + " ***");

            String s = Settings.getInstance().getLocalization();
            for (SupportedLocales sl : SupportedLocales.values())
                if (sl.name().equals(s)) {
                    SupportedLocales.NOW_LOCALE = sl;
                    Locale.setDefault(sl.self);
                }

            if (System.getProperty("java.vm.name").contains("Open")) // OpenJDK
                MessageBox.showLocalized("ui.message.open_jdk");

            try {
                BeautyEyeLNFHelper.launchBeautyEyeLNF();
                RepaintManager.setCurrentManager(new MyRepaintManager());
            } catch (Exception ex) {
                HMCLog.warn("Failed to set look and feel...", ex);
            }

            LogWindow.INSTANCE.clean();
            LogWindow.INSTANCE.setTerminateGame(new Main()::run);

            Settings.UPDATE_CHECKER.upgrade.register(IUpgrader.NOW_UPGRADER);
            Settings.UPDATE_CHECKER.process(false).reg(t -> Main.invokeUpdate()).execute();

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
            }

            MainFrame.showMainFrame();
        }
    }

    @Override
    public void run() {
        for (Process p : JavaProcess.processes)
            p.destroy();
    }

    public static void invokeUpdate() {
        MainFrame.INSTANCE.invokeUpdate();
    }

    public static ImageIcon getIcon(String path) {
        try {
            return new ImageIcon(Main.class.getResource("/org/jackhuang/hmcl/" + path));
        } catch (Exception e) {
            HMCLog.err("Failed to load icon", e);
            return null;
        }
    }
}
