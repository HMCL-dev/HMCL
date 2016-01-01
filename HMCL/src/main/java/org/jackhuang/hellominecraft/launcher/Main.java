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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;
import java.text.ParseException;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.X509TrustManager;
import javax.swing.ImageIcon;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import org.jackhuang.hellominecraft.HMCLog;
import org.jackhuang.hellominecraft.launcher.api.PluginManager;
import org.jackhuang.hellominecraft.launcher.launch.GameLauncher;
import org.jackhuang.hellominecraft.launcher.utils.CrashReporter;
import org.jackhuang.hellominecraft.logging.Configuration;
import org.jackhuang.hellominecraft.logging.appender.ConsoleAppender;
import org.jackhuang.hellominecraft.logging.layout.DefaultLayout;
import org.jackhuang.hellominecraft.views.LogWindow;
import org.jackhuang.hellominecraft.launcher.settings.Settings;
import org.jackhuang.hellominecraft.launcher.utils.upgrade.IUpgrader;
import org.jackhuang.hellominecraft.launcher.views.MainFrame;
import org.jackhuang.hellominecraft.lookandfeel.HelloMinecraftLookAndFeel;
import org.jackhuang.hellominecraft.utils.MathUtils;
import org.jackhuang.hellominecraft.utils.StrUtils;
import org.jackhuang.hellominecraft.utils.VersionNumber;
import rx.concurrency.Schedulers;

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
            X509TrustManager[] xtmArray = new X509TrustManager[] { XTM };
            sslContext.init(null, xtmArray, new java.security.SecureRandom());
        } catch (GeneralSecurityException gse) {
        }
        if (sslContext != null)
            HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());

        HttpsURLConnection.setDefaultHostnameVerifier(HNV);
    }

    public static final String LAUNCHER_NAME = "Hello Minecraft! Launcher";
    public static final byte VERSION_FIRST = 2, VERSION_SECOND = 3, VERSION_THIRD = 5, VERSION_FORTH = 7;
    public static final int MINIMUM_LAUNCHER_VERSION = 16;
    //public static Proxy PROXY;

    /**
     * Make the version of HMCL.
     *
     * @return the version: firstVer.secondVer.thirdVer
     */
    public static String makeVersion() {
        return "" + VERSION_FIRST + '.' + VERSION_SECOND + '.' + VERSION_THIRD + '.' + VERSION_FORTH;
    }

    /**
     * Make the main window title.
     *
     * @return the MainWindow title.
     */
    public static String makeTitle() {
        return LAUNCHER_NAME + ' ' + makeVersion();
    }

    public static final Main INSTANCE = new Main();
    public static HelloMinecraftLookAndFeel LOOK_AND_FEEL;

    @SuppressWarnings({ "CallToPrintStackTrace", "UseSpecificCatch" })
    public static void main(String[] args) {
        {
            //PluginManager.getServerPlugin();

            if (IUpgrader.NOW_UPGRADER.parseArguments(new VersionNumber(VERSION_FIRST, VERSION_SECOND, VERSION_THIRD), args))
                return;

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

            LogWindow.INSTANCE.clean();
            LogWindow.INSTANCE.setTerminateGame(GameLauncher.PROCESS_MANAGER::stopAllProcesses);

            try {
                LOOK_AND_FEEL = new HelloMinecraftLookAndFeel(Settings.getInstance().getTheme().settings);
                UIManager.setLookAndFeel(LOOK_AND_FEEL);
            } catch (ParseException | UnsupportedLookAndFeelException ex) {
                HMCLog.warn("Failed to set look and feel...", ex);
            }

            Settings.UPDATE_CHECKER.outdated.register(IUpgrader.NOW_UPGRADER);
            Settings.UPDATE_CHECKER.process(false).subscribeOn(Schedulers.newThread()).subscribe(t
                -> Main.invokeUpdate());

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

            try {
                PluginManager.NOW_PLUGIN.showUI();
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
