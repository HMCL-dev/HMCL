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

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.jackhuang.hmcl.ui.SwingUtils;
import org.jackhuang.hmcl.util.io.ChecksumMismatchException;
import org.jackhuang.hmcl.util.io.IOUtils;
import org.jackhuang.hmcl.util.platform.Platform;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.List;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toSet;
import static org.jackhuang.hmcl.Metadata.HMCL_DIRECTORY;
import static org.jackhuang.hmcl.util.Logging.LOG;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;
import static org.jackhuang.hmcl.util.platform.JavaVersion.CURRENT_JAVA;

// From: https://github.com/Col-E/Recaf/blob/7378b397cee664ae81b7963b0355ef8ff013c3a7/src/main/java/me/coley/recaf/util/self/SelfDependencyPatcher.java
public final class SelfDependencyPatcher {
    private final List<DependencyDescriptor> dependencies = DependencyDescriptor.readDependencies();
    private final List<Repository> repositories;
    private final Repository defaultRepository;
    private final byte[] buffer = new byte[IOUtils.DEFAULT_BUFFER_SIZE];
    private final MessageDigest digest = DigestUtils.getDigest("SHA-1");

    private SelfDependencyPatcher() throws IncompatibleVersionException {
        // We can only self-patch JavaFX on specific platform.
        if (dependencies == null) {
            throw new IncompatibleVersionException();
        }

        final String customUrl = System.getProperty("hmcl.openjfx.repo");
        if (customUrl == null) {
            if (System.getProperty("user.country", "").equalsIgnoreCase("CN")) {
                defaultRepository = Repository.ALIYUN_MIRROR;
            } else {
                defaultRepository = Repository.MAVEN_CENTRAL;
            }
            repositories = Collections.unmodifiableList(Arrays.asList(Repository.MAVEN_CENTRAL, Repository.ALIYUN_MIRROR));
        } else {
            defaultRepository = new Repository(String.format(i18n("repositories.custom"), customUrl), customUrl);
            repositories = Collections.unmodifiableList(Arrays.asList(Repository.MAVEN_CENTRAL, Repository.ALIYUN_MIRROR, defaultRepository));
        }
    }

    private static final class DependencyDescriptor {
        private static final String DEPENDENCIES_LIST_FILE = "/assets/openjfx-dependencies.json";
        private static final Path DEPENDENCIES_DIR_PATH = HMCL_DIRECTORY.resolve("dependencies").resolve(Platform.getPlatform().toString()).resolve("openjfx");

        static List<DependencyDescriptor> readDependencies() {
            ArrayList<DependencyDescriptor> dependencies;
            //noinspection ConstantConditions
            try (Reader reader = new InputStreamReader(SelfDependencyPatcher.class.getResourceAsStream(DEPENDENCIES_LIST_FILE), UTF_8)) {
                Map<String, ArrayList<DependencyDescriptor>> allDependencies =
                        new Gson().fromJson(reader, new TypeToken<Map<String, ArrayList<DependencyDescriptor>>>(){}.getType());
                dependencies = allDependencies.get(Platform.getPlatform().toString());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }

            try {
                ClassLoader classLoader = SelfDependencyPatcher.class.getClassLoader();
                Class.forName("netscape.javascript.JSObject", false, classLoader);
                Class.forName("org.w3c.dom.html.HTMLDocument", false, classLoader);
            } catch (Throwable e) {
                LOG.log(Level.WARNING, "Disable javafx.web because JRE is incomplete", e);
                dependencies.removeIf(it -> "javafx.web".equals(it.module) || "javafx.media".equals(it.module));
            }

            return dependencies;
        }

        public String module;
        public String groupId;
        public String artifactId;
        public String version;
        public String classifier;
        public String sha1;

        public String filename() {
            return artifactId + "-" + version + "-" + classifier + ".jar";
        }

        public String sha1() {
            return sha1;
        }

        public Path localPath() {
            return DEPENDENCIES_DIR_PATH.resolve(filename());
        }
    }

    private static final class Repository {
        public static final Repository MAVEN_CENTRAL = new Repository(i18n("repositories.maven_central"), "https://repo1.maven.org/maven2");
        public static final Repository ALIYUN_MIRROR = new Repository(i18n("repositories.aliyun_mirror"), "https://maven.aliyun.com/repository/central");

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

    /**
     * Patch in any missing dependencies, if any.
     */
    public static void patch() throws PatchException, IncompatibleVersionException, CancellationException {
        // Do nothing if JavaFX is detected
        try {
            try {
                Class.forName("javafx.application.Application");
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

        SelfDependencyPatcher patcher = new SelfDependencyPatcher();

        // Otherwise we're free to download in Java 11+
        LOG.info("Missing JavaFX dependencies, attempting to patch in missing classes");

        // Download missing dependencies
        List<DependencyDescriptor> missingDependencies = patcher.checkMissingDependencies();
        if (!missingDependencies.isEmpty()) {
            try {
                patcher.fetchDependencies(missingDependencies);
            } catch (IOException e) {
                throw new PatchException("Failed to download dependencies", e);
            }
        }

        // Add the dependencies
        try {
            patcher.loadFromCache();
        } catch (IOException ex) {
            throw new PatchException("Failed to load JavaFX cache", ex);
        } catch (ReflectiveOperationException | NoClassDefFoundError ex) {
            throw new PatchException("Failed to add dependencies to classpath!", ex);
        }
        LOG.info(" - Done!");
    }

    private Repository showChooseRepositoryDialog() {
        final JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        for (String line : i18n("repositories.chooser").split("\n")) {
            panel.add(new JLabel(line));
        }

        final ButtonGroup buttonGroup = new ButtonGroup();

        for (Repository repository : repositories) {
            final JRadioButton button = new JRadioButton(repository.name);
            button.putClientProperty("repository", repository);
            buttonGroup.add(button);
            panel.add(button);
            if (repository == defaultRepository) {
                button.setSelected(true);
            }
        }

        int res = JOptionPane.showConfirmDialog(null, panel, i18n("repositories.chooser.title"), JOptionPane.OK_CANCEL_OPTION);

        if (res == JOptionPane.OK_OPTION) {
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
    private void loadFromCache() throws IOException, ReflectiveOperationException {
        LOG.info(" - Loading dependencies...");

        Set<String> modules = dependencies.stream()
                .map(it -> it.module)
                .collect(toSet());

        Path[] jars = dependencies.stream()
                .map(DependencyDescriptor::localPath)
                .toArray(Path[]::new);

        JavaFXPatcher.patch(modules, jars);
    }

    /**
     * Download dependencies.
     *
     * @throws IOException When the files cannot be fetched or saved.
     */
    private void fetchDependencies(List<DependencyDescriptor> dependencies) throws IOException {
        SwingUtils.initLookAndFeel();

        boolean isFirstTime = true;

        Repository repository = defaultRepository;

        int count = 0;
        while (true) {
            AtomicBoolean isCancelled = new AtomicBoolean();
            AtomicBoolean showDetails = new AtomicBoolean();

            ProgressFrame dialog = new ProgressFrame(i18n("download.javafx"));
            dialog.setProgressMaximum(dependencies.size() + 1);
            dialog.setProgress(count);
            dialog.setOnCancel(() -> isCancelled.set(true));
            dialog.setOnChangeSource(() -> {
                isCancelled.set(true);
                showDetails.set(true);
            });
            dialog.setVisible(true);
            try {
                if (isFirstTime) {
                    isFirstTime = false;
                    try {
                        //noinspection BusyWait
                        Thread.sleep(1000);
                    } catch (InterruptedException ignored) {
                    }
                }
                Files.createDirectories(DependencyDescriptor.DEPENDENCIES_DIR_PATH);
                for (int i = count; i < dependencies.size(); i++) {
                    if (isCancelled.get()) {
                        throw new CancellationException();
                    }

                    DependencyDescriptor dependency = dependencies.get(i);

                    final String url = repository.resolveDependencyURL(dependency);
                    SwingUtilities.invokeLater(() -> {
                        dialog.setCurrent(dependency.module);
                        dialog.incrementProgress();
                    });

                    LOG.info("Downloading " + url);

                    try (InputStream is = new URL(url).openStream();
                         OutputStream os = Files.newOutputStream(dependency.localPath())) {

                        int read;
                        while ((read = is.read(buffer, 0, IOUtils.DEFAULT_BUFFER_SIZE)) >= 0) {
                            if (isCancelled.get()) {
                                try {
                                    os.close();
                                } finally {
                                    Files.deleteIfExists(dependency.localPath());
                                }
                                throw new CancellationException();
                            }
                            os.write(buffer, 0, read);
                        }
                    }
                    verifyChecksum(dependency);
                    count++;
                }
            } catch (CancellationException e) {
                dialog.dispose();
                if (showDetails.get()) {
                    repository = showChooseRepositoryDialog();
                    continue;
                } else {
                    throw e;
                }
            }
            dialog.dispose();
            return;
        }
    }

    private List<DependencyDescriptor> checkMissingDependencies() {
        List<DependencyDescriptor> missing = new ArrayList<>();

        for (DependencyDescriptor dependency : dependencies) {
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

    private void verifyChecksum(DependencyDescriptor dependency) throws IOException, ChecksumMismatchException {
        digest.reset();
        try (InputStream is = Files.newInputStream(dependency.localPath())) {
            int read;
            while ((read = is.read(buffer, 0, IOUtils.DEFAULT_BUFFER_SIZE)) > -1) {
                digest.update(buffer, 0, read);
            }
        }

        String sha1 = Hex.encodeHex(digest.digest());
        if (!dependency.sha1().equalsIgnoreCase(sha1))
            throw new ChecksumMismatchException("SHA-1", dependency.sha1(), sha1);
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
        private final JButton btnChangeSource;
        private final JButton btnCancel;

        public ProgressFrame(String title) {
            JPanel panel = new JPanel();

            setResizable(false);
            setTitle(title);
            setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            setBounds(100, 100, 500, 200);
            setContentPane(panel);
            setLocationRelativeTo(null);

            JPanel content = new JPanel();
            content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

            for (String note : i18n("download.javafx.notes").split("\n")) {
                content.add(new JLabel(note));
            }
            content.add(new JLabel("<html><br/></html>"));
            progressText = new JLabel(i18n("download.javafx.prepare"));
            content.add(progressText);
            progressBar = new JProgressBar();
            content.add(progressBar);

            final JPanel buttonBar = new JPanel();
            btnChangeSource = new JButton(i18n("button.change_source"));
            btnCancel = new JButton(i18n("button.cancel"));
            buttonBar.add(btnChangeSource);
            buttonBar.add(btnCancel);

            panel.setLayout(new BorderLayout());
            panel.setBorder(BorderFactory.createEmptyBorder(10, 5, 0, 5));
            panel.add(content, BorderLayout.CENTER);
            panel.add(buttonBar, BorderLayout.SOUTH);
        }

        public void setCurrent(String component) {
            progressText.setText(i18n("download.javafx.component", component));
        }

        public void setProgressMaximum(int total) {
            progressBar.setMaximum(total);
        }

        public void setProgress(int n) {
            progressBar.setValue(n);
        }

        public void incrementProgress() {
            progressBar.setValue(progressBar.getValue() + 1);
        }

        public void setOnCancel(Runnable action) {
            btnCancel.addActionListener(e -> action.run());
            addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    action.run();
                }
            });
        }

        public void setOnChangeSource(Runnable action) {
            btnChangeSource.addActionListener(e -> action.run());
        }
    }
}
