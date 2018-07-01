/*
 * Hello Minecraft! Launcher.
 * Copyright (C) 2018  huangyuhui <huanghongxun2008@126.com>
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

import static org.jackhuang.hmcl.util.Logging.LOG;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

import java.io.File;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.logging.Level;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import javax.swing.JOptionPane;

public final class Main {

    public static void main(String[] args) {
        checkJavaFX();
        checkDirectoryPath();
        checkDSTRootCAX3();
        Launcher.main(args);
    }

    private static void checkDirectoryPath() {
        String currentDirectory = new File("").getAbsolutePath();
        if (currentDirectory.contains("!")) {
            // No Chinese translation because both Swing and JavaFX cannot render Chinese character properly when exclamation mark exists in the path.
            showErrorAndExit("Exclamation mark(!) is not allowed in the path where HMCL is in.\n"
                    + "The path is " + currentDirectory);
        }
    }

    private static void checkJavaFX() {
        try {
            Class.forName("javafx.application.Application");
        } catch (ClassNotFoundException e) {
            showErrorAndExit(i18n("fatal.missing_javafx"));
        }
    }

    private static void checkDSTRootCAX3() {
        TrustManagerFactory tmf;
        try {
            tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init((KeyStore) null);
        } catch (NoSuchAlgorithmException | KeyStoreException e) {
            LOG.log(Level.WARNING, "Failed to init TrustManagerFactory", e);
            // don't know what to do here
            return;
        }
        for (TrustManager tm : tmf.getTrustManagers()) {
            if (tm instanceof X509TrustManager) {
                for (X509Certificate cert : ((X509TrustManager) tm).getAcceptedIssuers()) {
                    if ("CN=DST Root CA X3, O=Digital Signature Trust Co.".equals((cert.getSubjectDN().getName()))) {
                        return;
                    }
                }
            }
        }
        showWarningAndContinue(i18n("fatal.missing_dst_root_ca_x3"));
    }

    /**
     * Indicates that a fatal error has occurred, and that the application cannot start.
     */
    private static void showErrorAndExit(String message) {
        System.err.println(message);
        System.err.println("A fatal error has occurred, forcibly exiting.");
        JOptionPane.showMessageDialog(null, message, "Error", JOptionPane.ERROR_MESSAGE);
        System.exit(1);
    }

    /**
     * Indicates that potential issues have been detected, and that the application may not function properly (but it can still run).
     */
    private static void showWarningAndContinue(String message) {
        System.err.println(message);
        System.err.println("Potential issues have been detected.");
        JOptionPane.showMessageDialog(null, message, "Warning", JOptionPane.WARNING_MESSAGE);
    }

}
