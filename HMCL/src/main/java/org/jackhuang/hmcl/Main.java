/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2020  huangyuhui <huanghongxun2008@126.com> and contributors
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
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.jackhuang.hmcl;

import org.jackhuang.hmcl.util.Logging;
import org.jackhuang.hmcl.util.SelfDependencyPatcher;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.Collections;
import java.util.concurrent.CancellationException;
import java.util.logging.Level;

import static org.jackhuang.hmcl.util.Lang.thread;
import static org.jackhuang.hmcl.util.Logging.LOG;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        System.setProperty("java.net.useSystemProxies", "true");
        System.setProperty("http.agent", "HMCL/" + Metadata.VERSION);
        System.setProperty("javafx.autoproxy.disable", "true");
        // Fix title bar not displaying in GTK systems
        System.setProperty("jdk.gtk.version", "2");

        // Use System look and feel
        initLookAndFeel();

        checkDirectoryPath();

        // This environment check will take ~300ms
        thread(Main::fixLetsEncrypt, "CA Certificate Check", true);

        Logging.start(Metadata.HMCL_DIRECTORY.resolve("logs"));

        checkJavaFX();


        Launcher.main(args);
    }

    private static void initLookAndFeel() {
        if (System.getProperty("swing.defaultlaf") == null) {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Throwable ignored) {
            }
        }
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
            SelfDependencyPatcher.patch();
        } catch (SelfDependencyPatcher.PatchException e) {
            LOG.log(Level.SEVERE, "unable to patch JVM", e);
            showErrorAndExit(i18n("fatal.javafx.missing"));
        } catch (SelfDependencyPatcher.IncompatibleVersionException e) {
            LOG.log(Level.SEVERE, "unable to patch JVM", e);
            showErrorAndExit(i18n("fatal.javafx.incompatible"));
        } catch (CancellationException e) {
            LOG.log(Level.SEVERE, "User cancels downloading JavaFX", e);
            System.exit(0);
        }
    }

    /**
     * Indicates that a fatal error has occurred, and that the application cannot start.
     */
    static void showErrorAndExit(String message) {
        System.err.println(message);
        System.err.println("A fatal error has occurred, forcibly exiting.");
        JOptionPane.showMessageDialog(null, message, "Error", JOptionPane.ERROR_MESSAGE);
        System.exit(1);
    }

    /**
     * Indicates that potential issues have been detected, and that the application may not function properly (but it can still run).
     */
    static void showWarningAndContinue(String message) {
        System.err.println(message);
        System.err.println("Potential issues have been detected.");
        JOptionPane.showMessageDialog(null, message, "Warning", JOptionPane.WARNING_MESSAGE);
    }

    static void fixLetsEncrypt() {
        try {
            KeyStore defaultKeyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            Path ksPath = Paths.get(System.getProperty("java.home"), "lib", "security", "cacerts");

            try (InputStream ksStream = Files.newInputStream(ksPath)) {
                defaultKeyStore.load(ksStream, "changeit".toCharArray());
            }

            KeyStore letsEncryptKeyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            try (InputStream letsEncryptFile = Main.class.getResourceAsStream("/assets/lekeystore.jks")) {
                letsEncryptKeyStore.load(letsEncryptFile, "supersecretpassword".toCharArray());
            }

            KeyStore merged = KeyStore.getInstance(KeyStore.getDefaultType());
            merged.load(null, new char[0]);
            for (String alias : Collections.list(letsEncryptKeyStore.aliases()))
                merged.setCertificateEntry(alias, letsEncryptKeyStore.getCertificate(alias));
            for (String alias : Collections.list(defaultKeyStore.aliases()))
                merged.setCertificateEntry(alias, defaultKeyStore.getCertificate(alias));

            TrustManagerFactory instance = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            instance.init(merged);
            SSLContext tls = SSLContext.getInstance("TLS");
            tls.init(null, instance.getTrustManagers(), null);
            HttpsURLConnection.setDefaultSSLSocketFactory(tls.getSocketFactory());
            LOG.info("Added Lets Encrypt root certificates as additional trust");
        } catch (KeyStoreException | IOException | NoSuchAlgorithmException | CertificateException | KeyManagementException e) {
            LOG.log(Level.SEVERE, "Failed to load lets encrypt certificate. Expect problems", e);
        }
    }
}
