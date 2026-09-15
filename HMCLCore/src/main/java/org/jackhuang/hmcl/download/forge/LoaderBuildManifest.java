/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.download.forge;

import org.jackhuang.hmcl.game.GameRepository;
import org.jackhuang.hmcl.util.DigestUtils;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Records what one mod loader build produced, so that installing the same loader build again can
/// skip the build.
///
/// Loader installers declare checksums for only a minority of their processors. In Forge
/// 1.20.1-47.3.22, for instance, `jarsplitter` carries an `outputs` section while `installertools`,
/// `ForgeAutoRenamingTool` and `binarypatcher` declare none at all. A launcher that trusts the
/// profile alone therefore has no way to tell whether those steps still have to run, so it rebuilds
/// them on every installation even when the artifacts of an earlier one are still sitting in the
/// shared libraries directory. This record supplies the missing anchors: the checksum of every file
/// a processor wrote, measured right after it wrote it.
///
/// The record also stores the installer profile and the loader manifest verbatim, so a repeat
/// installation does not have to fetch and unpack the installer JAR to reach them.
///
/// Paths are stored relative to the repository base directory. Processors run under the launcher's
/// working directory, so their arguments name files relative to it, whereas the artifacts
/// themselves live in the shared libraries directory. Storing the relative form keeps the record
/// valid whatever the working directory happens to be when it is read back.
public final class LoaderBuildManifest {

    /// Stored shape version, bumped whenever the fields below change incompatibly.
    private static final int FORMAT = 1;

    private final int format;
    private final String key;
    private final String installerSha1;
    private final String installProfile;
    private final String versionJson;
    private final Map<String, String> outputs;

    private LoaderBuildManifest(
            int format,
            String key,
            String installerSha1,
            String installProfile,
            String versionJson,
            Map<String, String> outputs) {
        this.format = format;
        this.key = key;
        this.installerSha1 = installerSha1;
        this.installProfile = installProfile;
        this.versionJson = versionJson;
        this.outputs = outputs == null ? Map.of() : new TreeMap<>(outputs);
    }

    /// Describes a build that has just finished.
    ///
    /// @param key           the build key
    /// @param installerSha1 checksum of the installer the build ran from
    /// @param installProfile raw `install_profile.json` the build was made from
    /// @param versionJson   raw loader manifest the build was made from
    /// @param outputs       checksum of every file the build produced, repository-relative
    /// @return the record, ready to be stored
    public static LoaderBuildManifest of(
            @NotNull String key,
            @NotNull String installerSha1,
            @NotNull String installProfile,
            @NotNull String versionJson,
            @NotNull Map<String, String> outputs) {
        return new LoaderBuildManifest(FORMAT, key, installerSha1, installProfile, versionJson, outputs);
    }

    public int getFormat() {
        return format;
    }

    public String getKey() {
        return key;
    }

    public String getInstallerSha1() {
        return installerSha1;
    }

    /// Returns the raw `install_profile.json` this build was made from.
    ///
    /// @return the installer profile text
    public String getInstallProfile() {
        return installProfile;
    }

    /// Returns the raw loader manifest this build was made from.
    ///
    /// @return the loader manifest text
    public String getVersionJson() {
        return versionJson;
    }

    /// Returns the checksum of every file the build produced.
    ///
    /// @return repository-relative path to SHA-1
    public Map<String, String> getOutputs() {
        return outputs;
    }

    /// Returns the key identifying one loader build.
    ///
    /// The loader version string already pins both the Minecraft version and the loader, and it is
    /// known before the installer is fetched, which is what lets a repeat installation avoid the
    /// download altogether. The loader name keeps the keyspaces apart.
    ///
    /// @param loader      the loader being installed, such as `forge` or `neoforge`
    /// @param selfVersion the loader version being installed
    /// @param side        the installation side, currently always `client`
    /// @return the build key
    public static String buildKey(@NotNull String loader, @NotNull String selfVersion, @NotNull String side) {
        return loader + "|" + selfVersion + "|" + side;
    }

    /// Returns where the record for `key` is stored.
    ///
    /// @param repository the repository the record belongs to
    /// @param key        the build key
    /// @return the record path below the repository
    public static Path fileFor(@NotNull GameRepository repository, @NotNull String key) {
        StringBuilder name = new StringBuilder(key.length());
        for (int i = 0; i < key.length(); i++) {
            char ch = key.charAt(i);
            boolean safe = (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z')
                    || (ch >= '0' && ch <= '9') || ch == '.' || ch == '-' || ch == '_';
            name.append(safe ? ch : '_');
        }
        return repository.getBaseDirectory().resolve(".hmcl").resolve("loader-build").resolve(name + ".json");
    }

    /// Reads the record for `key`.
    ///
    /// @param repository the repository the record belongs to
    /// @param key        the build key
    /// @return the record, or `null` when it is absent, unreadable, or written by another format
    ///         version
    public static @Nullable LoaderBuildManifest load(@NotNull GameRepository repository, @NotNull String key) {
        Path file = fileFor(repository, key);
        if (Files.notExists(file))
            return null;

        try {
            LoaderBuildManifest manifest = JsonUtils.fromJsonFile(file, LoaderBuildManifest.class);
            if (manifest == null || manifest.format != FORMAT || !key.equals(manifest.key))
                return null;
            return manifest;
        } catch (Exception e) {
            LOG.warning("Failed to read loader build record " + file, e);
            return null;
        }
    }

    /// Writes the record, replacing any previous one for the same key.
    ///
    /// @param repository the repository the record belongs to
    /// @throws IOException if the record cannot be written
    public void save(@NotNull GameRepository repository) throws IOException {
        Path file = fileFor(repository, key);
        Files.createDirectories(file.getParent());
        JsonUtils.writeToJsonFile(file, this);
    }

    /// Checks that every artifact this build produced is still present and unchanged.
    ///
    /// @param repository the repository the recorded paths are relative to
    /// @return `true` when the build can be skipped
    public boolean verify(@NotNull GameRepository repository) {
        if (outputs.isEmpty())
            return false;

        Path base = repository.getBaseDirectory().toAbsolutePath().normalize();
        for (Map.Entry<String, String> entry : outputs.entrySet()) {
            Path file = resolveOutput(base, entry.getKey());
            if (!Files.isRegularFile(file))
                return false;

            try {
                if (!entry.getValue().equals(DigestUtils.digestToString("SHA-1", file)))
                    return false;
            } catch (Exception e) {
                return false;
            }
        }
        return true;
    }

    /// Looks up the recorded checksum of one processor output.
    ///
    /// @param repository the repository the recorded paths are relative to
    /// @param file       the file the processor named, already resolved against the working directory
    /// @return the recorded checksum, or `null` when this record does not cover the file
    public @Nullable String sha1Of(@NotNull GameRepository repository, @NotNull Path file) {
        String key = keyOf(repository, file);
        return key == null ? null : outputs.get(key);
    }

    /// Converts a path named by a processor into the repository-relative form used by [#outputs].
    ///
    /// @param repository the repository the path should be named relative to
    /// @param file       the file the processor named, already resolved against the working directory
    /// @return the key, or `null` when the file lies outside the repository
    public static @Nullable String keyOf(@NotNull GameRepository repository, @NotNull Path file) {
        Path base = repository.getBaseDirectory().toAbsolutePath().normalize();
        Path absolute = file.toAbsolutePath().normalize();
        if (!absolute.startsWith(base))
            return null;

        return base.relativize(absolute).toString().replace(File.separatorChar, '/');
    }

    /// Resolves a recorded path against the repository it was recorded in.
    private static Path resolveOutput(Path base, String key) {
        return base.resolve(key.replace('/', File.separatorChar)).normalize();
    }
}
