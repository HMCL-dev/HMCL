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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

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

        private static final String REPOSITORY_URL = "https://maven.aliyun.com/repository/central/";
        private static final Path DEPENDENCIES_DIR_PATH = HMCL_DIRECTORY.resolve("dependencies");

        private static String currentArchClassifier() {
            switch (OperatingSystem.CURRENT_OS) {
                case LINUX:
                    return "linux";
                case OSX:
                    return "mac";
                default:
                    return "win";
            }
        }

        public String module;
        public String groupId;
        public String artifactId;
        public String version;
        public Map<String, String> sha1;

        public String filename() {
            return artifactId + "-" + version + "-" + currentArchClassifier() + ".jar";
        }

        public String sha1() {
            return sha1.get(currentArchClassifier());
        }

        public String url() {
            return REPOSITORY_URL + groupId.replace('.', '/') + "/" + artifactId + "/" + version + "/" + filename();
        }

        public Path localPath() {
            return DEPENDENCIES_DIR_PATH.resolve(filename());
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

        // We can only self-patch JavaFX on x86-64 platform.
        // For ARM support, user's manual patch is required.
        if (Architecture.CURRENT != Architecture.X86_64) {
            throw new IncompatibleVersionException();
        }

        // Otherwise we're free to download in Java 11+
        LOG.info("Missing JavaFX dependencies, attempting to patch in missing classes");

        // Download missing dependencies
        List<DependencyDescriptor> missingDependencies = checkMissingDependencies();
        if (!missingDependencies.isEmpty()) {
            try {
                fetchDependencies(missingDependencies);
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
                .map(it -> it.localPath())
                .toArray(Path[]::new);

        JavaFXPatcher.patch(modules, jars);
    }

    /**
     * Download dependencies.
     *
     * @throws IOException When the files cannot be fetched or saved.
     */
    private static void fetchDependencies(List<DependencyDescriptor> dependencies) throws IOException {
        ProgressFrame dialog = new ProgressFrame(i18n("download.javafx"));
        dialog.setVisible(true);

        int progress = 0;
        for (DependencyDescriptor dependency : dependencies) {
            int currentProgress = ++progress;
            SwingUtilities.invokeLater(() -> {
                dialog.setStatus(dependency.url());
                dialog.setProgress(currentProgress, dependencies.size());
            });

            LOG.info("Downloading " + dependency.url());
            Files.createDirectories(dependency.localPath().getParent());
            Files.copy(new URL(dependency.url()).openStream(), dependency.localPath(), StandardCopyOption.REPLACE_EXISTING);
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
