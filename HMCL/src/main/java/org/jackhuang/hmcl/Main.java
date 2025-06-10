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
import org.jackhuang.hmcl.util.FileSaver;
import org.jackhuang.hmcl.ui.AwtUtils;
import org.jackhuang.hmcl.util.ModuleHelper;
import org.jackhuang.hmcl.util.SelfDependencyPatcher;
import org.jackhuang.hmcl.ui.SwingUtils;
import org.jackhuang.hmcl.java.JavaRuntime;
import org.jackhuang.hmcl.util.platform.OperatingSystem;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
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

        createHMCLDirectories();
        LOG.start(Metadata.HMCL_CURRENT_DIRECTORY.resolve("logs"));

        checkDirectoryPath();

        if (JavaRuntime.CURRENT_VERSION < 9)
            // This environment check will take ~300ms
            thread(Main::fixLetsEncrypt, "CA Certificate Check", true);

        if (OperatingSystem.CURRENT_OS == OperatingSystem.MACOS)
            initIcon();

        checkJavaFX();
        verifyJavaFX();
        addEnableNativeAccess();
        enableUnsafeMemoryAccess();

        Launcher.main(args);
    }

    public static void exit(int exitCode) {
        FileSaver.shutdown();
        LOG.shutdown();
        System.exit(exitCode);
    }

    private static void createHMCLDirectories() {
        if (!Files.isDirectory(Metadata.HMCL_CURRENT_DIRECTORY)) {
            try {
                Files.createDirectories(Metadata.HMCL_CURRENT_DIRECTORY);
                if (OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS) {
                    try {
                        Files.setAttribute(Metadata.HMCL_CURRENT_DIRECTORY, "dos:hidden", true);
                    } catch (IOException e) {
                        LOG.warning("Failed to set hidden attribute of " + Metadata.HMCL_CURRENT_DIRECTORY, e);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace(System.err);
                showErrorAndExit(i18n("fatal.create_hmcl_current_directory_failure", Metadata.HMCL_CURRENT_DIRECTORY));
            }
        }

        if (!Files.isDirectory(Metadata.HMCL_GLOBAL_DIRECTORY)) {
            try {
                Files.createDirectories(Metadata.HMCL_GLOBAL_DIRECTORY);
            } catch (IOException e) {
                LOG.warning("Failed to create HMCL global directory " + Metadata.HMCL_GLOBAL_DIRECTORY, e);
            }
        }
    }

    private static void initIcon() {
        java.awt.Image image = java.awt.Toolkit.getDefaultToolkit().getImage(Main.class.getResource("/assets/img/icon-mac.png"));
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
            e.printStackTrace(System.err);
            showErrorAndExit(i18n("fatal.javafx.incomplete"));
        }
    }

    private static void addEnableNativeAccess() {
        if (JavaRuntime.CURRENT_VERSION > 21) {
            try {
                ModuleHelper.addEnableNativeAccess(Class.forName("javafx.stage.Stage")); // javafx.graphics
            } catch (ClassNotFoundException e) {
                e.printStackTrace(System.err);
                showErrorAndExit(i18n("fatal.javafx.incomplete"));
            }
        }
    }

    private static void enableUnsafeMemoryAccess() {
        // https://openjdk.org/jeps/498
        if (JavaRuntime.CURRENT_VERSION == 24 || JavaRuntime.CURRENT_VERSION == 25) {
            try {
                Class<?> clazz = Class.forName("sun.misc.Unsafe");
                Method trySetMemoryAccessWarned = clazz.getDeclaredMethod("trySetMemoryAccessWarned");
                trySetMemoryAccessWarned.setAccessible(true);
                trySetMemoryAccessWarned.invoke(null);
            } catch (Throwable e) {
                e.printStackTrace(System.err);
            }
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
