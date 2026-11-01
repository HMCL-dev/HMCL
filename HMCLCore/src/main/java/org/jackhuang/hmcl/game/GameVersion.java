// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.game;

import com.google.gson.JsonParseException;
import org.jackhuang.hmcl.util.DigestUtils;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jenkinsci.constant_pool_scanner.ConstantPool;
import org.jenkinsci.constant_pool_scanner.ConstantPoolScanner;
import org.jenkinsci.constant_pool_scanner.ConstantType;
import org.jenkinsci.constant_pool_scanner.StringConstant;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.StreamSupport;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/**
 * @author huangyuhui
 */
final class GameVersion {
    private GameVersion() {
    }

    /// Maps client JAR SHA-1 hashes to versions with missing, ambiguous, or special version strings.
    private static final @Unmodifiable Map<String, String> KNOWN_VERSIONS = loadKnownVersions();

    /// Reads the embedded client hash table into an immutable hash-to-version mapping.
    /// Ignores blank lines and comment lines beginning with `#`.
    /// If multiple versions share a hash, the first CSV entry determines its version name.
    ///
    /// @return the mappings from `/assets/game/client-sha1.csv`
    /// @throws IllegalStateException if the resource is missing, unreadable, or malformed
    private static @Unmodifiable Map<String, String> loadKnownVersions() {
        @Nullable InputStream input = GameVersion.class.getResourceAsStream("/assets/game/client-sha1.csv");
        if (input == null) {
            throw new IllegalStateException("Missing client SHA-1 table");
        }
        var versions = new HashMap<String, String>();
        try (var reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            for (@Nullable String line; (line = reader.readLine()) != null; ) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                String[] fields = line.split(",", -1);
                if (fields.length != 2 || fields[0].isBlank() || !fields[1].matches("[0-9a-f]{40}")) {
                    throw new IllegalStateException("Invalid client SHA-1 record: " + line);
                }
                versions.putIfAbsent(fields[1], fields[0]);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read client SHA-1 table", e);
        }
        return Map.copyOf(versions);
    }

    private static Optional<String> getVersionFromJson(InputStream versionJson) {
        try {
            Map<?, ?> version = JsonUtils.fromNonNullJsonFully(versionJson, Map.class);
            String id = (String) version.get("id");
            if (id != null && id.contains(" / "))
                id = id.substring(0, id.indexOf(" / "));
            return Optional.ofNullable(id);
        } catch (IOException | JsonParseException | ClassCastException e) {
            LOG.warning("Failed to parse version.json", e);
            return Optional.empty();
        }
    }

    private static Optional<String> getVersionOfClassMinecraft(InputStream bytecode) throws IOException {
        final String constantPrefix = "Minecraft Minecraft ";
        ConstantPool pool = ConstantPoolScanner.parse(bytecode, ConstantType.STRING);
        for (StringConstant constant : pool.list(StringConstant.class)) {
            String value = constant.get();
            if (value.startsWith(constantPrefix)) {
                return Optional.of(value.substring(constantPrefix.length()));
            }
        }
        return Optional.empty();
    }

    private static Optional<String> getVersionFromClassMinecraftServer(InputStream bytecode) throws IOException {
        ConstantPool pool = ConstantPoolScanner.parse(bytecode, ConstantType.STRING);

        List<String> list = StreamSupport.stream(pool.list(StringConstant.class).spliterator(), false)
                .map(StringConstant::get)
                .toList();

        int idx = -1;

        for (int i = 0; i < list.size(); ++i)
            if (list.get(i).startsWith("Can't keep up!")) {
                idx = i;
                break;
            }

        Pattern pattern = Pattern.compile(".*[0-9].*");
        for (int i = idx - 1; i >= 0; --i)
            if (pattern.matcher(list.get(i)).matches())
                return Optional.of(list.get(i));

        return Optional.empty();
    }

    /// Detects the Minecraft version from a JAR's metadata, class constants, or known SHA-1 hash.
    ///
    /// A client version string of `2.0` is refined to a color variant when the JAR's hash is known;
    /// otherwise, `2.0` is retained, including when the hash cannot be read.
    ///
    /// @param file the JAR to inspect, or `null`
    /// @return the detected version, or an empty optional if the path is null, is not a regular file,
    ///         or no version can be identified
    public static Optional<String> minecraftVersion(@Nullable Path file) {
        if (file == null || !Files.isRegularFile(file))
            return Optional.empty();

        try (var gameJar = new ZipFile(file.toFile())) {
            @Nullable ZipEntry versionJson = gameJar.getEntry("version.json");
            if (versionJson != null) {
                Optional<String> result = getVersionFromJson(gameJar.getInputStream(versionJson));
                if (result.isPresent())
                    return result;
            }

            @Nullable ZipEntry minecraft = gameJar.getEntry("net/minecraft/client/Minecraft.class");
            if (minecraft != null) {
                try (InputStream is = gameJar.getInputStream(minecraft)) {
                    Optional<String> result = getVersionOfClassMinecraft(is);
                    if (result.isPresent()) {
                        String version = result.get();
                        // For Minecraft 1.0 rc1/rc2-1/rc2-2, this value is "RC1"
                        // For Minecraft 1.0 rc2-3, this value is "RC2"
                        if (!version.equals("RC1") && !version.equals("RC2")) {
                            if (version.equals("2.0")) {
                                return getKnownVersion(file).or(() -> Optional.of(version));
                            }
                            if (version.startsWith("Beta ")) {
                                result = Optional.of("b" + version.substring("Beta ".length()));
                            } else if (version.startsWith("Alpha v")) {
                                result = Optional.of("a" + version.substring("Alpha v".length()));
                            }
                            return result;
                        }
                    }
                }
            }

            @Nullable ZipEntry minecraftServer = gameJar.getEntry("net/minecraft/server/MinecraftServer.class");
            if (minecraftServer != null) {
                try (InputStream is = gameJar.getInputStream(minecraftServer)) {
                    return getVersionFromClassMinecraftServer(is);
                }
            }
        } catch (IOException ignored) {
        }

        return getKnownVersion(file);
    }

    /// Looks up a JAR's version by its SHA-1 hash.
    ///
    /// @param file the JAR to hash
    /// @return the known version, or an empty optional if the hash is unknown or an I/O error occurs
    private static Optional<String> getKnownVersion(Path file) {
        try {
            String digest = DigestUtils.digestToString("SHA-1", file);
            return Optional.ofNullable(KNOWN_VERSIONS.get(digest));
        } catch (IOException e) {
            return Optional.empty();
        }
    }
}
