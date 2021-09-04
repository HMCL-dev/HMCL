/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
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
/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2021 Matthew Coley
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.jackhuang.hmcl.util;

import static java.lang.Class.forName;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toSet;
import static org.jackhuang.hmcl.Metadata.HMCL_DIRECTORY;
import static org.jackhuang.hmcl.util.Logging.LOG;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.platform.JavaVersion.CURRENT_JAVA;

import java.awt.Dialog;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;

import javax.swing.*;

import org.jackhuang.hmcl.util.io.ChecksumMismatchException;
import org.jackhuang.hmcl.util.io.IOUtils;
import org.jackhuang.hmcl.util.platform.Architecture;
import org.jackhuang.hmcl.util.platform.OperatingSystem;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

// From: https://github.com/Col-E/Recaf/blob/7378b397cee664ae81b7963b0355ef8ff013c3a7/src/main/java/me/coley/recaf/util/self/SelfDependencyPatcher.java
public final class SelfDependencyPatcher {
    private SelfDependencyPatcher() {
    }

    static class DependencyDescriptor {

        private static final Path DEPENDENCIES_DIR_PATH = HMCL_DIRECTORY.resolve("dependencies");

        public static final String CURRENT_ARCH_CLASSIFIER = currentArchClassifier();

        private static String currentArchClassifier() {
            if (OperatingSystem.CURRENT_OS == OperatingSystem.LINUX) {
                switch (Architecture.CURRENT) {
                    case X86_64:
                        return "linux";
                    case ARM64:
                        return "linux-aarch64";
                }
            } else if (OperatingSystem.CURRENT_OS == OperatingSystem.OSX) {
                switch (Architecture.CURRENT) {
                    case X86_64:
                        return "mac";
                    case ARM64:
                        return "mac-aarch64";
                }
            } else if (OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS) {
                switch (Architecture.CURRENT) {
                    case X86_64:
                        return "win";
                    case X86:
                        return "win-x86";
                }
            }
            return null;
        }

        public String module;
        public String groupId;
        public String artifactId;
        public String version;
        public Map<String, String> sha1;

        public String filename() {
            if (CURRENT_ARCH_CLASSIFIER == null) {
                return null;
            }
            return artifactId + "-" + version + "-" + CURRENT_ARCH_CLASSIFIER + ".jar";
        }

        public String sha1() {
            if (CURRENT_ARCH_CLASSIFIER == null) {
                return null;
            }
            return sha1.get(CURRENT_ARCH_CLASSIFIER);
        }

        public Path localPath() {
            if (CURRENT_ARCH_CLASSIFIER == null) {
                return null;
            }
            return DEPENDENCIES_DIR_PATH.resolve(filename());
        }
    }

    static final class Repository {
        public static final List<Repository> REPOSITORIES;

        public static final Repository CUSTOM;
        public static final Repository MAVEN_CENTRAL = new Repository(i18n("repositories.maven_central"), "https://repo1.maven.org/maven2");
        public static final Repository ALIYUN_MIRROR = new Repository(i18n("repositories.aliyun_mirror"), "https://maven.aliyun.com/repository/central");

        public static final Repository DEFAULT;

        static {
            final String customUrl = System.getProperty("hmcl.openjfx.repo");
            if (customUrl == null) {
                CUSTOM = null;
                if (System.getProperty("user.country", "").equalsIgnoreCase("CN")) {
                    DEFAULT = Repository.ALIYUN_MIRROR;
                } else {
                    DEFAULT = Repository.MAVEN_CENTRAL;
                }
                REPOSITORIES = Collections.unmodifiableList(Arrays.asList(MAVEN_CENTRAL, ALIYUN_MIRROR));
            } else {
                CUSTOM = new Repository(String.format(i18n("repositories.custom"), customUrl), customUrl);
                DEFAULT = CUSTOM;
                REPOSITORIES = Collections.unmodifiableList(Arrays.asList(MAVEN_CENTRAL, ALIYUN_MIRROR, CUSTOM));
            }
        }

        private final String name;
        private final String url;

        Repository(String name, String url) {
            this.name = name;
            this.url = url;
        }

        public String resolveDependencyURL(DependencyDescriptor descriptor) {
            return String.format("%s/%s/%s/%s/%s",
                    url,
                    descriptor.groupId.replace('.', '/'),
                    descriptor.artifactId, descriptor.version,
                    descriptor.filename());
        }
    }

    private static final String DEPENDENCIES_LIST_FILE = "/assets/openjfx-dependencies.json";

    private static List<DependencyDescriptor> readDependencies() {
        String content;
        try (InputStream in = SelfDependencyPatcher.class.getResourceAsStream(DEPENDENCIES_LIST_FILE)) {
            content = IOUtils.readFullyAsString(in, UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new Gson().fromJson(content, TypeToken.getParameterized(List.class, DependencyDescriptor.class).getType());
    }

    private static final List<DependencyDescriptor> JFX_DEPENDENCIES = readDependencies();

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

        // We can only self-patch JavaFX on specific platform.
        if (DependencyDescriptor.CURRENT_ARCH_CLASSIFIER == null) {
            throw new IncompatibleVersionException();
        }

        // Otherwise we're free to download in Java 11+
        LOG.info("Missing JavaFX dependencies, attempting to patch in missing classes");

        // Download missing dependencies
        List<DependencyDescriptor> missingDependencies = checkMissingDependencies();
        if (!missingDependencies.isEmpty()) {
            final Repository repository = showChooseRepositoryDialog();

            try {
                fetchDependencies(repository, missingDependencies);
            } catch (IOException e) {
                throw new PatchException("Failed to download dependencies", e);
            }
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

    private static Repository showChooseRepositoryDialog() {
        final JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        for (String line : i18n("repositories.chooser").split("\n")) {
            panel.add(new JLabel(line));
        }

        final ButtonGroup buttonGroup = new ButtonGroup();

        for (Repository repository : Repository.REPOSITORIES) {
            final JRadioButton button = new JRadioButton(repository.name);
            button.putClientProperty("repository", repository);
            buttonGroup.add(button);
            panel.add(button);
            if (repository == Repository.DEFAULT) {
                button.setSelected(true);
            }
        }

        int res = JOptionPane.showConfirmDialog(null, panel, i18n("repositories.chooser.title"), JOptionPane.YES_NO_OPTION);

        if (res == JOptionPane.YES_OPTION) {
            final Enumeration<AbstractButton> buttons = buttonGroup.getElements();
            while (buttons.hasMoreElements()) {
                final AbstractButton button = buttons.nextElement();
                if (button.isSelected()) {
                    return (Repository) button.getClientProperty("repository");
                }
            }
        } else {
            LOG.info("User choose not to download JavaFX");
            System.exit(0);
        }
        throw new AssertionError();
    }

    /**
     * Inject them into the current classpath.
     *
     * @throws IOException                  When the locally cached dependency urls cannot be resolved.
     * @throws ReflectiveOperationException When the call to add these urls to the system classpath failed.
     */
    private static void loadFromCache() throws IOException, ReflectiveOperationException {
        LOG.info(" - Loading dependencies...");

        Set<String> modules = JFX_DEPENDENCIES.stream()
                .map(it -> it.module)
                .collect(toSet());

        Path[] jars = JFX_DEPENDENCIES.stream()
                .map(DependencyDescriptor::localPath)
                .toArray(Path[]::new);

        JavaFXPatcher.patch(modules, jars);
    }

    /**
     * Download dependencies.
     *
     * @throws IOException When the files cannot be fetched or saved.
     */
    private static void fetchDependencies(Repository repository, List<DependencyDescriptor> dependencies) throws IOException {
        ProgressFrame dialog = new ProgressFrame(i18n("download.javafx"));
        dialog.setVisible(true);

        int progress = 0;
        for (DependencyDescriptor dependency : dependencies) {
            int currentProgress = ++progress;
            final String url = repository.resolveDependencyURL(dependency);
            SwingUtilities.invokeLater(() -> {
                dialog.setStatus(url);
                dialog.setProgress(currentProgress, dependencies.size());
            });

            LOG.info("Downloading " + url);
            Files.createDirectories(dependency.localPath().getParent());
            try (InputStream is = new URL(url).openStream()) {
                Files.copy(is, dependency.localPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            verifyChecksum(dependency);
        }

        dialog.dispose();
    }

    private static List<DependencyDescriptor> checkMissingDependencies() {
        List<DependencyDescriptor> missing = new ArrayList<>();

        for (DependencyDescriptor dependency : JFX_DEPENDENCIES) {
            if (!Files.exists(dependency.localPath())) {
                missing.add(dependency);
                continue;
            }

            try {
                verifyChecksum(dependency);
            } catch (ChecksumMismatchException e) {
                LOG.warning("Corrupted dependency " + dependency.filename() + ": " + e.getMessage());
                missing.add(dependency);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        return missing;
    }

    private static void verifyChecksum(DependencyDescriptor dependency) throws IOException, ChecksumMismatchException {
        String expectedHash = dependency.sha1();
        String actualHash = Hex.encodeHex(DigestUtils.digest("SHA-1", dependency.localPath()));
        if (!expectedHash.equalsIgnoreCase(actualHash)) {
            throw new ChecksumMismatchException("SHA-1", expectedHash, actualHash);
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

        private final JProgressBar progressBar;
        private final JLabel progressText;

        public ProgressFrame(String title) {
            super((Dialog) null);

            JPanel panel = new JPanel();

            setResizable(false);
            setTitle(title);
            setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            setBounds(100, 100, 600, 150);
            setContentPane(panel);
            setLocationRelativeTo(null);

            GridBagLayout gridBagLayout = new GridBagLayout();
            gridBagLayout.columnWidths = new int[] { 600, 0 };
            gridBagLayout.rowHeights = new int[] { 0, 0, 0, 200 };
            gridBagLayout.columnWeights = new double[] { 1.0, Double.MIN_VALUE };
            gridBagLayout.rowWeights = new double[] { 0.0, 0.0, 0.0, 1.0 };
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
