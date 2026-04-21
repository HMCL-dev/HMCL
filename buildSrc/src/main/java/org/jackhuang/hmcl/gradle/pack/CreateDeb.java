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

import kala.compress.archivers.ar.ArArchiveEntry;
import kala.compress.archivers.ar.ArArchiveOutputStream;
import kala.compress.archivers.tar.TarArchiveEntry;
import kala.compress.archivers.tar.TarArchiveOutputStream;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.GZIPOutputStream;

/// Creates a Debian package for the current HMCL channel.
///
/// ## Package layout
///
/// The generated `data.tar.gz` contains four installed artifacts:
///
/// - the bundled HMCL shell launcher under `/usr/share/java/hmcl/`
/// - a channel-specific command under `/usr/bin/`
/// - a desktop entry under `/usr/share/applications/`
/// - the HMCL icon under `/usr/share/icons/hicolor/256x256/apps/`
///
/// ## Channel commands and aliases
///
/// Every package installs a channel-specific executable such as `hmcl-stable`
/// or `hmcl-dev`. The generic `hmcl` command is intentionally not shipped as a
/// plain file. Instead, maintainer scripts register the channel command into
/// the shared `hmcl` alternatives group so multiple channel packages can
/// coexist without file conflicts.
///
/// @author Glavo
public abstract class CreateDeb extends DefaultTask {
    public static final Logger LOGGER = Logging.getLogger(CreateDeb.class);

    private static final int DIRECTORY_MODE = 0755;
    private static final int EXECUTABLE_MODE = 0755;
    private static final int REGULAR_FILE_MODE = 0644;

    /// Debian version written into the `control` file and output filename.
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

    /// Final `.deb` archive written by this task.
    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    private ReleaseType getCurrentChannel() {
        return getReleaseType().get();
    }

    private String getLauncherPath() {
        return "/usr/bin/" + getCurrentChannel().getLauncherCommandName();
    }

    private String getTargetPath() {
        return "/usr/share/java/hmcl/" + getAppShFile().getAsFile().get().getName();
    }

    private String getDesktopFilePath() {
        return "/usr/share/applications/" + getCurrentChannel().getDesktopFileName();
    }

    private String getIconTargetPath() {
        return "/usr/share/icons/hicolor/256x256/apps/hmcl.png";
    }

    /// Ensures parent directories exist in the tar stream before child entries are written.
    private static void makeDirectories(Set<String> directories, TarArchiveOutputStream output, String dirName) throws IOException {
        if (dirName.isEmpty() || ".".equals(dirName) || directories.contains(dirName))
            return;

        int idx = dirName.lastIndexOf('/');
        if (idx > 0) {
            makeDirectories(directories, output, dirName.substring(0, idx));
        }

        TarArchiveEntry entry = new TarArchiveEntry(dirName + "/", true);
        entry.setMode(DIRECTORY_MODE);
        output.putArchiveEntry(entry);
        output.closeArchiveEntry();
        directories.add(dirName);
    }

    /// Writes UTF-8 text content as a tar entry with the requested file mode.
    private static void putEntry(Set<String> directories, TarArchiveOutputStream output, String name, String content, int mode) throws IOException {
        putEntry(directories, output, name, content.getBytes(StandardCharsets.UTF_8), mode);
    }

    /// Writes binary content as a tar entry and creates parent directories on demand.
    private static void putEntry(Set<String> directories, TarArchiveOutputStream output, String name, byte[] content, int mode) throws IOException {
        int idx = name.lastIndexOf('/');
        if (idx > 0) {
            makeDirectories(directories, output, name.substring(0, idx));
        }

        TarArchiveEntry entry = new TarArchiveEntry(name);
        entry.setMode(mode);
        entry.setSize(content.length);
        output.putArchiveEntry(entry);
        output.write(content);
        output.closeArchiveEntry();
    }

    /// Builds a valid `.deb` archive with `debian-binary`, `control.tar.gz`, and `data.tar.gz`.
    public void run() throws IOException {
        Path appShFile = getAppShFile().getAsFile().get().toPath();
        if (!Files.isRegularFile(appShFile))
            throw new IOException("Invalid app script file: " + appShFile);

        Path iconFile = getIconFile().getAsFile().get().toPath();
        if (!Files.isRegularFile(iconFile))
            throw new IOException("Invalid icon file: " + iconFile);

        byte[] appShBytes = Files.readAllBytes(appShFile);
        if (appShBytes.length == 0)
            throw new IOException("Empty app script file: " + appShFile);

        byte[] iconBytes = Files.readAllBytes(iconFile);
        if (iconBytes.length == 0)
            throw new IOException("Empty icon file: " + iconFile);

        byte[] launcherScriptBytes = getLauncherScript().getBytes(StandardCharsets.UTF_8);
        byte[] desktopInfoBytes = getDesktopInfo().getBytes(StandardCharsets.UTF_8);

        LOGGER.lifecycle("Creating control.tar.gz");
        var controlData = new ByteArrayOutputStream();
        try (var output = new TarArchiveOutputStream(new GZIPOutputStream(controlData))) {
            output.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);

            Set<String> directories = new HashSet<>();
            putEntry(directories, output, "./control", getControl(appShBytes.length, launcherScriptBytes.length, desktopInfoBytes.length, iconBytes.length), REGULAR_FILE_MODE);
            putEntry(directories, output, "./postinst", getPostinst(), EXECUTABLE_MODE);
            putEntry(directories, output, "./prerm", getPrerm(), EXECUTABLE_MODE);
        }

        Path outputFile = getOutputFile().get().getAsFile().toPath();
        Files.createDirectories(outputFile.getParent());

        LOGGER.lifecycle("Creating data.tar.gz");
        var dataTarBuffer = new ByteArrayOutputStream(12 * 1024 * 1024);
        try (var output = new TarArchiveOutputStream(new GZIPOutputStream(dataTarBuffer))) {
            output.setLongFileMode(TarArchiveOutputStream.LONGFILE_GNU);

            Set<String> directories = new HashSet<>();
            putEntry(directories, output, "." + getTargetPath(), appShBytes, EXECUTABLE_MODE);
            putEntry(directories, output, "." + getLauncherPath(), launcherScriptBytes, EXECUTABLE_MODE);
            putEntry(directories, output, "." + getDesktopFilePath(), desktopInfoBytes, REGULAR_FILE_MODE);
            putEntry(directories, output, "." + getIconTargetPath(), iconBytes, REGULAR_FILE_MODE);
        }

        LOGGER.lifecycle("Creating deb file");
        try (var output = new ArArchiveOutputStream(Files.newOutputStream(outputFile,
                StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE))) {
            putArEntry(output, "debian-binary", "2.0\n".getBytes(StandardCharsets.UTF_8));
            putArEntry(output, "control.tar.gz", controlData.toByteArray());
            putArEntry(output, "data.tar.gz", dataTarBuffer.toByteArray());
        }
    }

    /// Adds one member to the outer ar container used by Debian packages.
    private static void putArEntry(ArArchiveOutputStream output, String name, byte[] content) throws IOException {
        ArArchiveEntry entry = new ArArchiveEntry(name, content.length);
        output.putArchiveEntry(entry);
        output.write(content);
        output.closeArchiveEntry();
    }

    /// Generates the package metadata file.
    ///
    /// `Depends` is deliberately omitted so the package stays policy-light and
    /// leaves runtime dependency resolution to the target environment.
    private String getControl(long appSize, long launcherScriptSize, long desktopInfoSize, long iconSize) {
        long installedSize = (appSize + launcherScriptSize + desktopInfoSize + iconSize + 1023) / 1024;

        return """
                Package: %s
                Version: %s
                Section: games
                Priority: optional
                Architecture: all
                Installed-Size: %d
                Maintainer: Glavo <zjx001202@gmail.com>
                Description: Hello Minecraft! Launcher
                Homepage: https://github.com/HMCL-dev/HMCL
                """.formatted(getCurrentChannel().getPackageName(), getVersion().get(), Math.max(installedSize, 1)) + "\n";
    }

    private static final String COMMON_LAUNCHER_PATH = "/usr/bin/hmcl";

    /// Registers the channel command into the shared `hmcl` alternatives group.
    private String getPostinst() {
        return """
                #!/bin/sh
                set -e
                
                if [ "$1" = configure ]; then
                    update-alternatives --install %s hmcl %s %d
                fi
                """.formatted(COMMON_LAUNCHER_PATH, getLauncherPath(), getCurrentChannel().getAlternativesPriority());
    }

    /// Removes the channel command from the shared `hmcl` alternatives group.
    private String getPrerm() {
        return """
                #!/bin/sh
                set -e
                
                if [ "$1" = remove ] || [ "$1" = deconfigure ]; then
                    update-alternatives --remove %s %s
                fi
                """.formatted(COMMON_LAUNCHER_PATH, getLauncherPath());
    }

    /// Creates a tiny wrapper that launches the bundled shell script from the user's home directory.
    private String getLauncherScript() {
        return """
                #!/bin/sh
                cd "$HOME"
                export HMCL_HOME="$HOME/.hmcl"
                export HMCL_DATA_DIR="$HOME/.hmcl"
                exec %s "$@"
                """.formatted(getTargetPath());
    }

    /// Generates the desktop entry that points to the channel-specific launcher command.
    private String getDesktopInfo() {
        return """
                [Desktop Entry]
                Type=Application
                Name=HMCL
                Comment=Hello Minecraft! Launcher
                Exec=%s
                Icon=hmcl
                Terminal=false
                StartupNotify=false
                Categories=Game;
                Keywords=mc;minecraft;
                """.formatted(getLauncherPath());
    }
}
