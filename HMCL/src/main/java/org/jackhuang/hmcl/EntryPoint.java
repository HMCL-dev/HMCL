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

import org.jackhuang.hmcl.util.FileSaver;
import org.jackhuang.hmcl.ui.AwtUtils;
import org.jackhuang.hmcl.util.ModuleHelper;
import org.jackhuang.hmcl.util.SelfDependencyPatcher;
import org.jackhuang.hmcl.util.SwingUtils;
import org.jackhuang.hmcl.java.JavaRuntime;
import org.jackhuang.hmcl.util.platform.OperatingSystem;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
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

public final class EntryPoint {

    private EntryPoint() {
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
            thread(EntryPoint::fixLetsEncrypt, "CA Certificate Check", true);

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
                // Logger has not been started yet, so print directly to System.err
                System.err.println("Failed to create HMCL directory: " + Metadata.HMCL_CURRENT_DIRECTORY);
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
        java.awt.Image image = java.awt.Toolkit.getDefaultToolkit().getImage(EntryPoint.class.getResource("/assets/img/icon-mac.png"));
        AwtUtils.setAppleIcon(image);
    }

    private static void checkDirectoryPath() {
        String currentDir = System.getProperty("user.dir", "");
        if (currentDir.contains("!")) {
            LOG.error("The current working path contains an exclamation mark: " + currentDir);
            // No Chinese translation because both Swing and JavaFX cannot render Chinese character properly when exclamation mark exists in the path.
            showErrorAndExit("Exclamation mark(!) is not allowed in the path where HMCL is in.\n"
                    + "The path is " + currentDir);
        }
    }

    private static void checkJavaFX() {
        try {
            SelfDependencyPatcher.patch();
        } catch (SelfDependencyPatcher.PatchException e) {
            LOG.error("Unable to patch JVM", e);
            showErrorAndExit(i18n("fatal.javafx.missing"));
        } catch (SelfDependencyPatcher.IncompatibleVersionException e) {
            LOG.error("Unable to patch JVM", e);
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
            LOG.warning("JavaFX is incomplete or not found", e);
            showErrorAndExit(i18n("fatal.javafx.incomplete"));
        }
    }

    private static void addEnableNativeAccess() {
        if (JavaRuntime.CURRENT_VERSION > 21) {
            try {
                ModuleHelper.addEnableNativeAccess(Class.forName("javafx.stage.Stage")); // javafx.graphics
            } catch (ClassNotFoundException e) {
                LOG.error("Failed to add enable native access for JavaFX", e);
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
                LOG.warning("Failed to enable unsafe memory access", e);
            }
        }
    }

    /**
     * Indicates that a fatal error has occurred, and that the application cannot start.
     */
    private static void showErrorAndExit(String message) {
        SwingUtils.showErrorDialog(message);
        exit(1);
    }

    private static void fixLetsEncrypt() {
        try {
            KeyStore defaultKeyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            Path ksPath = Paths.get(System.getProperty("java.home"), "lib", "security", "cacerts");

            try (InputStream ksStream = Files.newInputStream(ksPath)) {
                defaultKeyStore.load(ksStream, "changeit".toCharArray());
            }

            KeyStore letsEncryptKeyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            try (InputStream letsEncryptFile = EntryPoint.class.getResourceAsStream("/assets/lekeystore.jks")) {
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
