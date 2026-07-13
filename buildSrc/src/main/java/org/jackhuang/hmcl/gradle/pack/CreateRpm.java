/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026 huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.gradle.pack;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Comparator;
import java.util.Locale;
import java.util.stream.Stream;

/// Creates a RHEL-compatible RPM package for the current HMCL channel.
///
/// The task stages the package payload and writes a small RPM spec, then uses
/// the platform `rpmbuild` tool. Keeping the RPM assembly in `rpmbuild` avoids
/// hand-writing RPM headers and makes the generated package easier for RPM
/// distribution users to inspect.
@NotNullByDefault
public abstract class CreateRpm extends DefaultTask {
    /// Task logger used for RPM build progress messages.
    public static final Logger LOGGER = Logging.getLogger(CreateRpm.class);

    /// File mode used for executable files in the staged payload.
    private static final String EXECUTABLE_PERMISSIONS = "rwxr-xr-x";

    /// File mode used for regular files in the staged payload.
    private static final String REGULAR_FILE_PERMISSIONS = "rw-r--r--";

    /// RPM version written into the spec before RPM-specific sanitization.
    @Input
    public abstract Property<String> getVersion();

    /// Release type metadata that controls package name, launcher name, and alias priority.
    @Input
    public abstract Property<ReleaseType> getReleaseType();

    /// Executable `.sh` artifact produced by `makeExecutables`.
    @InputFile
    public abstract RegularFileProperty getAppShFile();

    /// Desktop icon installed into the hicolor icon theme.
    @InputFile
    public abstract RegularFileProperty getIconFile();

    /// Final `.rpm` artifact copied out of the rpmbuild directory.
    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    /// Builds the staged payload, invokes `rpmbuild`, and copies the resulting RPM to `outputFile`.
    @TaskAction
    public void run() throws IOException, InterruptedException {
        assertRpmBuildAvailable();

        Path appShFile = getAppShFile().getAsFile().get().toPath();
        if (!Files.isRegularFile(appShFile))
            throw new IOException("Invalid app script file: " + appShFile);

        Path iconFile = getIconFile().getAsFile().get().toPath();
        if (!Files.isRegularFile(iconFile))
            throw new IOException("Invalid icon file: " + iconFile);

        Path outputFile = getOutputFile().get().getAsFile().toPath();
        @Nullable Path outputParent = outputFile.getParent();
        if (outputParent != null) {
            Files.createDirectories(outputParent);
        }

        Path workDir = getTemporaryDir().toPath();
        deleteRecursively(workDir);

        Path sourcesDir = workDir.resolve("SOURCES");
        Path specsDir = workDir.resolve("SPECS");
        Path payloadDir = sourcesDir.resolve("payload");
        Files.createDirectories(sourcesDir);
        Files.createDirectories(specsDir);
        stagePayload(payloadDir, appShFile, iconFile);

        ReleaseType releaseType = getReleaseType().get();
        String rpmVersion = sanitizeRpmVersion(getVersion().get());
        Path specFile = specsDir.resolve(releaseType.getPackageName() + ".spec");
        Files.writeString(specFile, getSpec(releaseType, rpmVersion), StandardCharsets.UTF_8);

        LOGGER.lifecycle("Creating rpm file");
        runCommand("rpmbuild", "-bb", "--define", "_topdir " + workDir, specFile.toString());

        Path builtRpm = workDir.resolve("RPMS").resolve("noarch")
                .resolve("%s-%s-1.noarch.rpm".formatted(releaseType.getPackageName(), rpmVersion));
        if (!Files.isRegularFile(builtRpm)) {
            throw new IOException("rpmbuild did not create expected file: " + builtRpm);
        }

        Files.copy(builtRpm, outputFile, StandardCopyOption.REPLACE_EXISTING);
    }

    /// Stages all installed files under an RPM build root payload directory.
    private void stagePayload(Path payloadDir, Path appShFile, Path iconFile) throws IOException {
        LinuxPackageFiles files = getPackageFiles(appShFile.getFileName().toString());

        copyFile(appShFile, payloadDir.resolve(stripRoot(files.targetPath())), EXECUTABLE_PERMISSIONS);
        writeFile(payloadDir.resolve(stripRoot(files.launcherPath())), files.launcherScript(), EXECUTABLE_PERMISSIONS);
        writeFile(payloadDir.resolve(stripRoot(files.desktopFilePath())), files.desktopInfo(), REGULAR_FILE_PERMISSIONS);
        copyFile(iconFile, payloadDir.resolve(stripRoot(files.iconTargetPath())), REGULAR_FILE_PERMISSIONS);
    }

    /// Creates the shared Linux package path helper for this task.
    private LinuxPackageFiles getPackageFiles(String appFileName) {
        return new LinuxPackageFiles(getReleaseType().get(), appFileName, "rpm");
    }

    /// Writes an RPM spec that installs the pre-staged payload and registers alternatives.
    private String getSpec(ReleaseType releaseType, String rpmVersion) {
        LinuxPackageFiles files = getPackageFiles(getAppShFile().getAsFile().get().getName());
        return """
                Name:           %s
                Version:        %s
                Release:        1
                Summary:        Hello Minecraft! Launcher
                License:        GPL-3.0-or-later
                URL:            https://github.com/HMCL-dev/HMCL
                BuildArch:      noarch
                Requires:       bash
                Requires(post): %%{_sbindir}/alternatives
                Requires(preun): %%{_sbindir}/alternatives
                
                %%description
                Hello Minecraft! Launcher is a Minecraft launcher with mod and instance management support.
                
                %%prep
                
                %%build
                
                %%install
                rm -rf "%%{buildroot}"
                mkdir -p "%%{buildroot}"
                cp -a "%%{_sourcedir}/payload/." "%%{buildroot}/"
                
                %%post
                if [ "$1" -eq 1 ] || [ "$1" -eq 2 ]; then
                    %%{_sbindir}/alternatives --install %s hmcl %s %d || :
                fi
                
                %%preun
                if [ "$1" -eq 0 ]; then
                    %%{_sbindir}/alternatives --remove hmcl %s || :
                fi
                
                %%files
                %%attr(0755,root,root) %%dir /usr/share/java/hmcl
                %%attr(0755,root,root) %s
                %%attr(0755,root,root) %s
                %%attr(0644,root,root) %s
                %%attr(0644,root,root) %s
                """.formatted(
                releaseType.getPackageName(),
                rpmVersion,
                LinuxPackageFiles.COMMON_LAUNCHER_PATH,
                files.launcherPath(),
                releaseType.getAlternativesPriority(),
                files.launcherPath(),
                files.targetPath(),
                files.launcherPath(),
                files.desktopFilePath(),
                files.iconTargetPath()
        );
    }

    /// Converts an absolute install path into a relative path below the staged payload directory.
    private static String stripRoot(String path) {
        if (!path.startsWith("/")) {
            throw new IllegalArgumentException("Expected absolute path: " + path);
        }
        return path.substring(1);
    }

    /// Copies a file into the payload and applies POSIX permissions when supported.
    private static void copyFile(Path source, Path target, String permissions) throws IOException {
        @Nullable Path targetParent = target.getParent();
        if (targetParent != null) {
            Files.createDirectories(targetParent);
        }
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        setPermissions(target, permissions);
    }

    /// Writes a UTF-8 file into the payload and applies POSIX permissions when supported.
    private static void writeFile(Path target, String content, String permissions) throws IOException {
        @Nullable Path targetParent = target.getParent();
        if (targetParent != null) {
            Files.createDirectories(targetParent);
        }
        Files.writeString(target, content, StandardCharsets.UTF_8);
        setPermissions(target, permissions);
    }

    /// Applies POSIX permissions when the target file system exposes the POSIX attribute view.
    private static void setPermissions(Path target, String permissions) throws IOException {
        if (target.getFileSystem().supportedFileAttributeViews().contains("posix")) {
            Files.setPosixFilePermissions(target, PosixFilePermissions.fromString(permissions));
        }
    }

    /// Removes the previous temporary build directory, if one exists.
    private static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }

        try (Stream<Path> paths = Files.walk(path)) {
            paths.sorted(Comparator.reverseOrder()).forEach(CreateRpm::deletePath);
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    /// Deletes one path during recursive cleanup.
    private static void deletePath(Path path) {
        try {
            Files.delete(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /// Converts HMCL versions to RPM-compatible versions by replacing unsupported characters.
    private static String sanitizeRpmVersion(String version) {
        if (version.isBlank()) {
            throw new GradleException("RPM version must not be blank");
        }

        StringBuilder result = new StringBuilder(version.length());
        for (int i = 0; i < version.length(); i++) {
            char ch = version.charAt(i);
            if (Character.isLetterOrDigit(ch) || ch == '.' || ch == '_' || ch == '+' || ch == '~') {
                result.append(ch);
            } else {
                result.append('_');
            }
        }

        return result.toString().toLowerCase(Locale.ROOT);
    }

    /// Verifies that `rpmbuild` is available before staging package files.
    private static void assertRpmBuildAvailable() throws IOException, InterruptedException {
        try {
            runCommand("rpmbuild", "--version");
        } catch (IOException e) {
            throw new IOException("rpmbuild is required to create RPM packages. Install rpm-build or run this task in the RPM CI container.", e);
        }
    }

    /// Runs an external command and fails the task when it exits unsuccessfully.
    private static void runCommand(String... command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command)
                .inheritIO()
                .start();
        try {
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new GradleException("Command failed with exit code %d: %s".formatted(exitCode, String.join(" ", command)));
            }
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw e;
        }
    }
}
