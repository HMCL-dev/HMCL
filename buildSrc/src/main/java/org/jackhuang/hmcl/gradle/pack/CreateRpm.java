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

import org.eclipse.packager.rpm.RpmTag;
import org.eclipse.packager.rpm.RpmVersion;
import org.eclipse.packager.rpm.build.BuilderContext;
import org.eclipse.packager.rpm.build.BuilderOptions;
import org.eclipse.packager.rpm.build.DigestAlgorithm;
import org.eclipse.packager.rpm.build.RpmBuilder;
import org.eclipse.packager.rpm.build.SimpleFileInformationCustomizer;
import org.eclipse.packager.rpm.coding.PayloadCoding;
import org.eclipse.packager.rpm.deps.RpmDependencyFlags;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Locale;

/// Creates a RHEL-compatible RPM package for the current HMCL channel.
///
/// The task writes the RPM entirely in Java so it can run on any host supported
/// by Gradle without Docker, `rpmbuild`, or POSIX file-system attributes.
@NotNullByDefault
public abstract class CreateRpm extends DefaultTask {
    /// Task logger used for RPM build progress messages.
    public static final Logger LOGGER = Logging.getLogger(CreateRpm.class);

    /// File mode used for executable files in the RPM payload.
    private static final int EXECUTABLE_MODE = 0755;

    /// File mode used for regular files in the RPM payload.
    private static final int REGULAR_FILE_MODE = 0644;

    /// RPM release value used for the first package revision of one HMCL version.
    private static final String RPM_RELEASE = "1";

    /// RPM architecture for the platform-independent HMCL payload.
    private static final String RPM_ARCHITECTURE = "noarch";

    /// RPM package license identifier.
    private static final String RPM_LICENSE = "GPL-3.0-or-later";

    /// RPM project URL.
    private static final String RPM_URL = "https://github.com/HMCL-dev/HMCL";

    /// RPM package summary.
    private static final String RPM_SUMMARY = "Hello Minecraft! Launcher";

    /// RPM package description.
    private static final String RPM_DESCRIPTION =
            "Hello Minecraft! Launcher is a Minecraft launcher with mod and instance management support.";

    /// Stable build host recorded in generated RPM metadata.
    private static final String RPM_BUILD_HOST = "hmcl-build";

    /// RPM version written into the package before RPM-specific sanitization.
    @Input
    public abstract Property<String> getVersion();

    /// Release type metadata that controls package name, launcher name, and alias priority.
    @Input
    public abstract Property<ReleaseType> getReleaseType();

    /// Launcher class name for the Linux desktop entry's `StartupWMClass` field.
    @Input
    public abstract Property<String> getLauncherClassName();

    /// Executable `.sh` artifact produced by `makeExecutables`.
    @InputFile
    public abstract RegularFileProperty getAppShFile();

    /// Desktop icon installed into the hicolor icon theme.
    @InputFile
    public abstract RegularFileProperty getIconFile();

    /// Final `.rpm` artifact written by this task.
    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    /// Optional reproducible-build timestamp recorded in RPM metadata and file entries.
    @Input
    @Optional
    public abstract Property<Long> getBuildTimestamp();

    /// Builds the RPM directly from the generated HMCL shell artifact and icon.
    @TaskAction
    public void run() throws IOException {
        buildRpm(
                getAppShFile().getAsFile().get().toPath(),
                getIconFile().getAsFile().get().toPath(),
                getOutputFile().get().getAsFile().toPath(),
                getVersion().get(),
                getReleaseType().get(),
                getLauncherClassName().get(),
                getBuildTimestamp().isPresent()
                        ? Instant.ofEpochSecond(getBuildTimestamp().get())
                        : Instant.now()
        );
    }

    /// Writes one RPM package with explicit metadata and a caller-provided build timestamp.
    static void buildRpm(Path appShFile, Path iconFile, Path outputFile, String version,
                         ReleaseType releaseType, String launcherClassName, Instant buildTime) throws IOException {
        assertRegularFile(appShFile, "app script");
        assertRegularFile(iconFile, "icon");

        @Nullable Path outputParent = outputFile.getParent();
        if (outputParent != null) {
            Files.createDirectories(outputParent);
        }

        int rpmTimestamp = getRpmTimestamp(buildTime);
        String rpmVersion = sanitizeRpmVersion(version);
        LinuxPackageFiles files = new LinuxPackageFiles(
                releaseType,
                appShFile.getFileName().toString(),
                "rpm",
                launcherClassName
        );

        BuilderOptions options = new BuilderOptions();
        options.setFileDigestAlgorithm(DigestAlgorithm.SHA256);
        options.setPayloadCoding(PayloadCoding.GZIP);

        LOGGER.lifecycle("Creating rpm file");
        try (RpmBuilder builder = new RpmBuilder(
                releaseType.getPackageName(),
                new RpmVersion(null, rpmVersion, RPM_RELEASE),
                RPM_ARCHITECTURE,
                outputFile,
                options
        )) {
            configurePackageInformation(builder, releaseType, rpmVersion, rpmTimestamp);
            addPayload(builder.newContext(), files, appShFile, iconFile, buildTime);
            addDependenciesAndScripts(builder, files, releaseType);
            builder.build();
        }
    }

    /// Converts a build timestamp to the signed 32-bit timestamp supported by RPM file tags.
    private static int getRpmTimestamp(Instant buildTime) {
        long epochSecond = buildTime.getEpochSecond();
        if (epochSecond < 0 || epochSecond > Integer.MAX_VALUE) {
            throw new GradleException("RPM build timestamp is outside the supported range: " + epochSecond);
        }
        return (int) epochSecond;
    }

    /// Rejects missing or non-regular input files before RPM assembly begins.
    private static void assertRegularFile(Path path, String description) throws IOException {
        if (!Files.isRegularFile(path)) {
            throw new IOException("Invalid " + description + " file: " + path);
        }
    }

    /// Configures package metadata that does not belong to an individual payload entry.
    private static void configurePackageInformation(RpmBuilder builder, ReleaseType releaseType,
                                                    String rpmVersion, int rpmTimestamp) {
        RpmBuilder.PackageInformation information = builder.getInformation();
        information.setLicense(RPM_LICENSE);
        information.setSummary(RPM_SUMMARY);
        information.setDescription(RPM_DESCRIPTION);
        information.setUrl(RPM_URL);
        information.setBuildHost(RPM_BUILD_HOST);
        information.setSourcePackage("%s-%s-%s.src.rpm".formatted(
                releaseType.getPackageName(), rpmVersion, RPM_RELEASE));
        builder.setHeaderCustomizer(header -> header.putInt(RpmTag.BUILDTIME, rpmTimestamp));
    }

    /// Adds installed directories and files in a stable order with explicit RPM ownership and modes.
    private static void addPayload(BuilderContext context, LinuxPackageFiles files, Path appShFile,
                                   Path iconFile, Instant buildTime) throws IOException {
        context.addDirectory(LinuxPackageFiles.INSTALL_DIRECTORY, fileInformation(EXECUTABLE_MODE, buildTime));
        context.addFile(files.targetPath(), appShFile, fileInformation(EXECUTABLE_MODE, buildTime));
        context.addFile(files.launcherPath(), files.launcherScript().getBytes(StandardCharsets.UTF_8),
                fileInformation(EXECUTABLE_MODE, buildTime));
        context.addFile(files.desktopFilePath(), files.desktopInfo().getBytes(StandardCharsets.UTF_8),
                fileInformation(REGULAR_FILE_MODE, buildTime));
        context.addFile(files.iconTargetPath(), iconFile, fileInformation(REGULAR_FILE_MODE, buildTime));
    }

    /// Creates an RPM file-information customizer with stable owner, group, mode, and timestamp values.
    private static SimpleFileInformationCustomizer fileInformation(int mode, Instant buildTime) {
        return information -> {
            information.setUser("root");
            information.setGroup("root");
            information.setMode((short) mode);
            information.setTimestamp(buildTime);
        };
    }

    /// Adds runtime dependencies and alternatives registration scripts to the package header.
    private static void addDependenciesAndScripts(RpmBuilder builder, LinuxPackageFiles files,
                                                  ReleaseType releaseType) {
        builder.addRequirement("bash", "");
        builder.addRequirement("/usr/bin/bash", "");
        builder.addRequirement("/usr/bin/env", "");
        builder.addRequirement("/usr/sbin/alternatives", "", RpmDependencyFlags.SCRIPT_POST);
        builder.addRequirement("/usr/sbin/alternatives", "", RpmDependencyFlags.SCRIPT_PREUN);
        builder.setPostInstallationScript(postInstallationScript(files, releaseType));
        builder.setPreRemoveScript(preRemoveScript(files));
    }

    /// Creates the post-install script that registers the channel launcher in the alternatives group.
    private static String postInstallationScript(LinuxPackageFiles files, ReleaseType releaseType) {
        return """
                if [ "$1" -eq 1 ] || [ "$1" -eq 2 ]; then
                    /usr/sbin/alternatives --install %s hmcl %s %d || :
                fi
                """.formatted(
                LinuxPackageFiles.COMMON_LAUNCHER_PATH,
                files.launcherPath(),
                releaseType.getAlternativesPriority()
        );
    }

    /// Creates the pre-remove script that unregisters the channel launcher during package removal.
    private static String preRemoveScript(LinuxPackageFiles files) {
        return """
                if [ "$1" -eq 0 ]; then
                    /usr/sbin/alternatives --remove hmcl %s || :
                fi
                """.formatted(files.launcherPath());
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
}
