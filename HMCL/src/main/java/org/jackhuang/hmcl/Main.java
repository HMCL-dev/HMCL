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

import javafx.application.Platform;
import javafx.scene.control.Alert;
import org.jackhuang.hmcl.ui.AwtUtils;
import org.jackhuang.hmcl.util.FractureiserDetector;
import org.jackhuang.hmcl.util.SelfDependencyPatcher;
import org.jackhuang.hmcl.ui.SwingUtils;
import org.jackhuang.hmcl.util.platform.JavaVersion;
import org.jackhuang.hmcl.util.platform.OperatingSystem;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
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

import static org.jackhuang.hmcl.util.Lang.thread;
import static org.jackhuang.hmcl.util.logging.Logger.LOG;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        System.getProperties().putIfAbsent("java.net.useSystemProxies", "true");
        System.getProperties().putIfAbsent("javafx.autoproxy.disable", "true");
        System.getProperties().putIfAbsent("http.agent", "HMCL/" + Metadata.VERSION);

        LOG.start(Metadata.HMCL_DIRECTORY.resolve("logs"));

        checkDirectoryPath();

        if (JavaVersion.CURRENT_JAVA.getParsedVersion() < 9)
            // This environment check will take ~300ms
            thread(Main::fixLetsEncrypt, "CA Certificate Check", true);

        if (OperatingSystem.CURRENT_OS == OperatingSystem.OSX)
            initIcon();

        checkJavaFX();
        verifyJavaFX();
        detectFractureiser();

        Launcher.main(args);
    }

    public static void exit(int exitCode) {
        LOG.shutdown();
        System.exit(exitCode);
    }

    private static void initIcon() {
        java.awt.Image image = java.awt.Toolkit.getDefaultToolkit().getImage(Main.class.getResource("/assets/img/icon@8x.png"));
        AwtUtils.setAppleIcon(image);
    }

    private static void checkDirectoryPath() {
        String currentDirectory = new File("").getAbsolutePath();
        if (currentDirectory.contains("!")) {
            // No Chinese translation because both Swing and JavaFX cannot render Chinese character properly when exclamation mark exists in the path.
            showErrorAndExit("Exclamation mark(!) is not allowed in the path where HMCL is in.\n"
                    + "The path is " + currentDirectory);
        }
    }

    private static void detectFractureiser() {
        if (FractureiserDetector.detect()) {
            LOG.error("Detected that this computer is infected by fractureiser");
            showErrorAndExit(i18n("fatal.fractureiser"));
        }
    }

    private static void checkJavaFX() {
        try {
            SelfDependencyPatcher.patch();
        } catch (SelfDependencyPatcher.PatchException e) {
            LOG.error("unable to patch JVM", e);
            showErrorAndExit(i18n("fatal.javafx.missing"));
        } catch (SelfDependencyPatcher.IncompatibleVersionException e) {
            LOG.error("unable to patch JVM", e);
            showErrorAndExit(i18n("fatal.javafx.incompatible"));
        } catch (CancellationException e) {
            LOG.error("User cancels downloading JavaFX", e);
            exit(0);
        }
    }

    /**
     * Check if JavaFX exists but is incomplete
     */
    private static void verifyJavaFX() {
        try {
            Class.forName("javafx.beans.binding.Binding"); // javafx.base
            Class.forName("javafx.stage.Stage");           // javafx.graphics
            Class.forName("javafx.scene.control.Skin");    // javafx.controls
        } catch (Exception e) {
            showErrorAndExit(i18n("fatal.javafx.incomplete"));
        }
    }

    /**
     * Indicates that a fatal error has occurred, and that the application cannot start.
     */
    static void showErrorAndExit(String message) {
        System.err.println(message);
        System.err.println("A fatal error has occurred, forcibly exiting.");

        try {
            if (Platform.isFxApplicationThread()) {
                new Alert(Alert.AlertType.ERROR, message).showAndWait();
                exit(1);
            }
        } catch (Throwable ignored) {
        }

        SwingUtils.showErrorDialog(message);
        exit(1);
    }

    /**
     * Indicates that potential issues have been detected, and that the application may not function properly (but it can still run).
     */
    static void showWarningAndContinue(String message) {
        System.err.println(message);
        System.err.println("Potential issues have been detected.");

        try {
            if (Platform.isFxApplicationThread()) {
                new Alert(Alert.AlertType.WARNING, message).showAndWait();
                return;
            }
        } catch (Throwable ignored) {
        }

        SwingUtils.showWarningDialog(message);
    }

    private static void fixLetsEncrypt() {
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
        } catch (KeyStoreException | IOException | NoSuchAlgorithmException | CertificateException |
                 KeyManagementException e) {
            LOG.error("Failed to load lets encrypt certificate. Expect problems", e);
        }
    }
}
