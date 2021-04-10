package org.jackhuang.hmcl.util;

import org.jackhuang.hmcl.util.io.ChecksumMismatchException;
import org.jackhuang.hmcl.util.io.NetworkUtils;
import org.jackhuang.hmcl.util.platform.OperatingSystem;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.*;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.logging.Level;

import static java.lang.Class.forName;
import static org.jackhuang.hmcl.Metadata.HMCL_DIRECTORY;
import static org.jackhuang.hmcl.util.Logging.LOG;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.platform.JavaVersion.CURRENT_JAVA;

/**
 * Utility for patching self when missing dependencies.
 * Copy from https://github.com/Col-E/Recaf/blob/master/src/main/java/me/coley/recaf/util/self/SelfDependencyPatcher.java
 *
 * @author Matt
 */
public class SelfDependencyPatcher {
    private static final Path DEPENDENCIES_DIR_PATH = HMCL_DIRECTORY.resolve("dependencies");
    private static final Map<Integer, List<String>> JFX_DEPENDENCIES = new LinkedHashMap<Integer, List<String>>(5, 1F) {
        {
            put(15, Arrays.asList(
                    jfxUrl("swing", "15.0.1"),
                    jfxUrl("media", "15.0.1"),
                    jfxUrl("fxml", "15.0.1"),
                    jfxUrl("web", "15.0.1"),
                    jfxUrl("controls", "15.0.1"),
                    jfxUrl("graphics", "15.0.1"),
                    jfxUrl("base", "15.0.1")
            ));
            put(14, Arrays.asList(
                    jfxUrl("swing", "14.0.2"),
                    jfxUrl("media", "14.0.2"),
                    jfxUrl("fxml", "14.0.2"),
                    jfxUrl("web", "14.0.2"),
                    jfxUrl("controls", "14.0.2"),
                    jfxUrl("graphics", "14.0.2"),
                    jfxUrl("base", "14.0.2")
            ));
            put(13, Arrays.asList(
                    jfxUrl("swing", "13.0.2"),
                    jfxUrl("media", "13.0.2"),
                    jfxUrl("fxml", "13.0.2"),
                    jfxUrl("web", "13.0.2"),
                    jfxUrl("controls", "13.0.2"),
                    jfxUrl("graphics", "13.0.2"),
                    jfxUrl("base", "13.0.2")
            ));
            put(12, Arrays.asList(
                    jfxUrl("swing", "12.0.2"),
                    jfxUrl("media", "12.0.2"),
                    jfxUrl("fxml", "12.0.2"),
                    jfxUrl("web", "12.0.2"),
                    jfxUrl("controls", "12.0.2"),
                    jfxUrl("graphics", "12.0.2"),
                    jfxUrl("base", "12.0.2")
            ));
            put(11, Arrays.asList(
                    jfxUrl("swing", "11.0.2"),
                    jfxUrl("media", "11.0.2"),
                    jfxUrl("fxml", "11.0.2"),
                    jfxUrl("web", "11.0.2"),
                    jfxUrl("controls", "11.0.2"),
                    jfxUrl("graphics", "11.0.2"),
                    jfxUrl("base", "11.0.2")
            ));
        }
    };

    /**
     * Patch in any missing dependencies, if any.
     */
    public static void patch() throws PatchException, IncompatibleVersionException {
        // Do nothing if JavaFX is detected
        try {
            try {
                forName("javafx.application.Application");
                return;
            } catch (Exception ignored) {
            }
        } catch (UnsupportedClassVersionError error) {
            // Loading the JavaFX class was unsupported.
            // We are probably on 8 and its on 11
            throw new IncompatibleVersionException();
        }
        // So the problem with Java 8 is that some distributions DO NOT BUNDLE JAVAFX
        // Why is this a problem? OpenJFX does not come in public bundles prior to Java 11
        // So you're out of luck unless you change your JDK or update Java.
        if (CURRENT_JAVA.getParsedVersion() < 11) {
            throw new IncompatibleVersionException();
        }

        // We can only self-patch JavaFX on x86 platform.
        // For ARM support, user's manual patch is required.
        switch (System.getProperty("os.arch")) {
            case "amd64":
            case "x86":
                break;
            default:
                throw new IncompatibleVersionException();
        }

        // Otherwise we're free to download in Java 11+
        LOG.info("Missing JavaFX dependencies, attempting to patch in missing classes");
        // Check if dependencies need to be downloaded
        if (!hasCachedDependencies()) {
            LOG.info(" - No local cache, downloading dependencies...");
            try {
                fetchDependencies();
            } catch (Exception ex) {
                throw new PatchException("Failed to download dependencies", ex);
            }
        } else {
            LOG.info(" - Local cache found!");
        }
        // Add the dependencies
        try {
            loadFromCache();
        } catch (IOException ex) {
            throw new PatchException("Failed to load JavaFX cache", ex);
        } catch (ReflectiveOperationException | NoClassDefFoundError ex) {
            throw new PatchException("Failed to add dependencies to classpath!", ex);
        }
        LOG.info(" - Done!");
    }

    /**
     * Inject them into the current classpath.
     *
     * @throws IOException
     * 		When the locally cached dependency urls cannot be resolved.
     * @throws ReflectiveOperationException
     * 		When the call to add these urls to the system classpath failed.
     */
    private static void loadFromCache() throws IOException, ReflectiveOperationException {
        LOG.info(" - Loading dependencies...");
        // Get Jar URLs
        List<URL> jarUrls = new ArrayList<>();
        Files.walk(DEPENDENCIES_DIR_PATH).forEach(path -> {
            try {
                jarUrls.add(path.toUri().toURL());
            } catch(MalformedURLException ex) {
                LOG.log(Level.WARNING, "Failed to convert '" + path.toFile().getAbsolutePath() + "' to URL", ex);
            }
        });
        // Fetch UCP of application's ClassLoader
        // - ((ClassLoaders.AppClassLoader) ClassLoaders.appClassLoader()).ucp
        Class<?> clsClassLoaders = Class.forName("jdk.internal.loader.ClassLoaders");
        Object appClassLoader = clsClassLoaders.getDeclaredMethod("appClassLoader").invoke(null);
        Class<?> ucpOwner = appClassLoader.getClass();
        // Field removed in 16, but still exists in parent class "BuiltinClassLoader"
        if (CURRENT_JAVA.getParsedVersion() >= 16)
            ucpOwner = ucpOwner.getSuperclass();
        Field fieldUCP = ucpOwner.getDeclaredField("ucp");
        fieldUCP.setAccessible(true);
        Object ucp = fieldUCP.get(appClassLoader);
        Class<?> clsUCP = ucp.getClass();
        Method addURL = clsUCP.getDeclaredMethod("addURL", URL.class);
        addURL.setAccessible(true);
        // Add each jar.
        for(URL url : jarUrls)
            addURL.invoke(ucp, url);
    }

    /**
     * Download dependencies.
     *
     * @throws IOException When the files cannot be fetched or saved.
     */
    private static void fetchDependencies() throws Exception {
        // Get dir to store dependencies in
        Path dependenciesDir = DEPENDENCIES_DIR_PATH;
        if (!Files.isDirectory(dependenciesDir)) {
            Files.createDirectories(dependenciesDir);
        }
        ProgressFrame dialog = new ProgressFrame(i18n("download.javafx"));

        ForkJoinTask<Void> task = ForkJoinPool.commonPool().submit(() -> {
            // Download each dependency
            List<String> dependencies = getLatestDependencies();
            for (int i = 0; i < dependencies.size(); i++) {
                String dependencyUrlPath = dependencies.get(i);
                URL depURL = new URL(dependencyUrlPath);
                Path dependencyFilePath = DEPENDENCIES_DIR_PATH.resolve(getFileName(dependencyUrlPath));
                int finalI = i;
                SwingUtilities.invokeLater(() -> {
                    dialog.setStatus(dependencyUrlPath);
                    dialog.setProgress(finalI, dependencies.size());
                });
                String expectedHash = NetworkUtils.doGet(NetworkUtils.toURL(dependencyUrlPath + ".sha1"));
                Files.copy(depURL.openStream(), dependencyFilePath, StandardCopyOption.REPLACE_EXISTING);
                String actualHash = Hex.encodeHex(DigestUtils.digest("SHA-1", dependencyFilePath));
                if (!expectedHash.equalsIgnoreCase(actualHash)) {
                    throw new ChecksumMismatchException("SHA-1", expectedHash, actualHash);
                }
            }

            return null;
        });

        dialog.setVisible(true);

        task.get();
    }

    /**
     * @return {@code true} when the dependencies directory has files in it.
     */
    private static boolean hasCachedDependencies() {
        String[] files = DEPENDENCIES_DIR_PATH.toFile().list();
        if (files == null)
            return false;
        return files.length >= getLatestDependencies().size();
    }

    /**
     * @param url Full url path.
     * @return Name of file at url.
     */
    private static String getFileName(String url) {
        return url.substring(url.lastIndexOf('/') + 1);
    }

    /**
     * @param component Name of the component.
     * @return Formed URL for the component.
     */
    private static String jfxUrl(String component, String version) {
        // https://repo1.maven.org/maven2/org/openjfx/javafx-%s/%s/javafx-%s-%s-%s.jar
        return String.format("https://maven.aliyun.com/repository/central/org/openjfx/javafx-%s/%s/javafx-%s-%s-%s.jar",
                component, version, component, version, getMvnName());
//        return String.format("https://bmclapi.bangbang93.com/maven/org/openjfx/javafx-%s/%s/javafx-%s-%s-%s.jar",
//                component, version, component, version, getMvnName());
    }

    /**
     * @return Latest JavaFX supported version for.
     */
    private static int getLatestSupportedJfxVersion() {
        int version = CURRENT_JAVA.getParsedVersion();
        while (version >= 11) {
            List<String> dependencies = JFX_DEPENDENCIES.get(version);
            if (dependencies != null)
                return version;
            version--;
        }
        throw new AssertionError("Failed to get latest supported JFX version");
    }

    /**
     * @return JavaFX dependencies list for the current VM version.
     */
    private static List<String> getLatestDependencies() {
        int version = getLatestSupportedJfxVersion();
        if (version >= 11) {
            return JFX_DEPENDENCIES.get(version);
        }
        throw new AssertionError("Failed to get latest JFX artifact urls");
    }

    private static String getMvnName() {
        switch (OperatingSystem.CURRENT_OS) {
            case LINUX:
                return "linux";
            case OSX:
                return "mac";
            default:
                return "win";
        }
    }

    public static class PatchException extends Exception {
        PatchException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class IncompatibleVersionException extends Exception {
    }

    public static class ProgressFrame extends JDialog {
        private int totalTasks = 0;
        private int finishedTasks = 0;

        private final JProgressBar progressBar;
        private final JLabel progressText;

        public ProgressFrame(String title) {
            super();

            JPanel panel = new JPanel();

            setResizable(false);
            setTitle(title);
            setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            setBounds(100, 100, 600, 150);
            setContentPane(panel);
            setLocationRelativeTo(null);

            GridBagLayout gridBagLayout = new GridBagLayout();
            gridBagLayout.columnWidths = new int[]{600, 0};
            gridBagLayout.rowHeights = new int[]{0, 0, 0, 200};
            gridBagLayout.columnWeights = new double[]{1.0, Double.MIN_VALUE};
            gridBagLayout.rowWeights = new double[]{0.0, 0.0, 0.0, 1.0};
            panel.setLayout(gridBagLayout);

            progressText = new JLabel("");
            GridBagConstraints gbc_lblProgressText = new GridBagConstraints();
            gbc_lblProgressText.insets = new Insets(10, 0, 5, 0);
            gbc_lblProgressText.gridx = 0;
            gbc_lblProgressText.gridy = 0;
            panel.add(progressText, gbc_lblProgressText);

            progressBar = new JProgressBar();
            GridBagConstraints gbc_progressBar = new GridBagConstraints();
            gbc_progressBar.insets = new Insets(0, 25, 5, 25);
            gbc_progressBar.fill = GridBagConstraints.HORIZONTAL;
            gbc_progressBar.gridx = 0;
            gbc_progressBar.gridy = 1;
            panel.add(progressBar, gbc_progressBar);

            JButton btnCancel = new JButton(i18n("button.cancel"));
            btnCancel.addActionListener(e -> {
                System.exit(-1);
            });
            GridBagConstraints gbc_btnCancel = new GridBagConstraints();
            gbc_btnCancel.insets = new Insets(0, 25, 5, 25);
            gbc_btnCancel.fill = GridBagConstraints.HORIZONTAL;
            gbc_btnCancel.gridx = 0;
            gbc_btnCancel.gridy = 2;
            panel.add(btnCancel, gbc_btnCancel);
        }

        public void setStatus(String status) {
            progressText.setText(status);
        }

        public void setProgress(int current, int total) {
            progressBar.setValue(current);
            progressBar.setMaximum(total);
        }
    }
}
