// SPDX-FileCopyrightText: Copyright (C) 2020-2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.util.versioning;

import com.google.gson.JsonParseException;
import org.jackhuang.hmcl.util.ByteArrayKey;
import org.jackhuang.hmcl.util.DigestUtils;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jenkinsci.constant_pool_scanner.ConstantPool;
import org.jenkinsci.constant_pool_scanner.ConstantPoolScanner;
import org.jenkinsci.constant_pool_scanner.ConstantType;
import org.jenkinsci.constant_pool_scanner.StringConstant;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.StreamSupport;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Detects version identifiers from JAR contents independently of version-number parsing.
@NotNullByDefault
final class JarVersionDetector {
    /// Prevents instantiation of the detector.
    private JarVersionDetector() {
    }

    /// Maps binary SHA-1 hashes to the first corresponding version identifier in the CSV.
    static final @Unmodifiable Map<ByteArrayKey, String> VERSIONS = loadKnownVersions();

    /// Reads the embedded client hash table into an immutable hash-to-version mapping.
    /// Ignores blank lines and comment lines beginning with `#`.
    /// Logs and skips records without exactly two fields, with a blank version identifier,
    /// or whose hash cannot be decoded by [ByteArrayKey#fromHexString(String)].
    /// Hash length is not validated. If multiple valid records share a hash, the first one determines its version name.
    ///
    /// Closes the resource stream after reading. If reading or closing throws [IOException],
    /// logs the failure and returns an empty map.
    ///
    /// @return the immutable mappings from `/assets/game/client-sha1.csv`, possibly empty
    /// @throws IllegalStateException if the resource is missing
    private static @Unmodifiable Map<ByteArrayKey, String> loadKnownVersions() {
        String csvContent;
        try (@Nullable InputStream input = JarVersionDetector.class.getResourceAsStream("/assets/game/client-sha1.csv")) {
            if (input == null) {
                throw new IllegalStateException("Missing client SHA-1 table");
            }
            csvContent = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOG.warning("Failed to read client SHA-1 table", e);
            return Map.of();
        }

        var versions = new HashMap<ByteArrayKey, String>();
        csvContent.lines().forEach(line -> {
            if (line.isBlank() || line.startsWith("#")) {
                return;
            }
            String[] fields = line.split(",", -1);
            if (fields.length != 2 || fields[0].isBlank()) {
                LOG.warning("Invalid client SHA-1 record: " + line);
                return;
            }

            try {
                versions.putIfAbsent(ByteArrayKey.fromHexString(fields[1]), fields[0]);
            } catch (Exception e) {
                LOG.warning("Invalid SHA-1 hash in client SHA-1 record: " + line, e);
            }
        });

        return Map.copyOf(versions);
    }

    /// Reads and closes a version.json stream, returning its string identifier before any ` / ` suffix.
    /// Returns `null` for a missing identifier or an I/O, JSON, or type error.
    private static @Nullable String getVersionFromJson(InputStream versionJson) {
        try {
            Map<?, @Nullable ?> version = JsonUtils.fromNonNullJsonFully(versionJson, Map.class);
            @Nullable String id = (String) version.get("id");
            if (id != null && id.contains(" / "))
                id = id.substring(0, id.indexOf(" / "));
            return id;
        } catch (IOException | JsonParseException | ClassCastException e) {
            LOG.warning("Failed to parse version.json", e);
            return null;
        }
    }

    /// Scans client class constants for the first legacy Minecraft version marker without closing the stream.
    ///
    /// @return the text after the marker, or `null` if no marker is found
    /// @throws IOException if the class cannot be read
    private static @Nullable String getVersionOfClassMinecraft(InputStream bytecode) throws IOException {
        final String constantPrefix = "Minecraft Minecraft ";
        ConstantPool pool = ConstantPoolScanner.parse(bytecode, ConstantType.STRING);
        for (StringConstant constant : pool.list(StringConstant.class)) {
            String value = constant.get();
            if (value.startsWith(constantPrefix)) {
                return value.substring(constantPrefix.length());
            }
        }
        return null;
    }

    /// Scans server class constants preceding the first lag-warning string for a string containing a digit.
    /// Leaves the stream open and returns `null` if no candidate is found.
    ///
    /// @throws IOException if the class cannot be read
    private static @Nullable String getVersionFromClassMinecraftServer(InputStream bytecode) throws IOException {
        ConstantPool pool = ConstantPoolScanner.parse(bytecode, ConstantType.STRING);

        @Unmodifiable List<String> list = StreamSupport.stream(pool.list(StringConstant.class).spliterator(), false)
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
                return list.get(i);

        return null;
    }

    /// Detects an identifier before conversion to [GameVersionNumber].
    ///
    /// @see GameVersionNumber#detectFromJar(Path)
    static @Nullable String detect(@Nullable Path file) {
        if (file == null || !Files.isRegularFile(file))
            return null;

        try (var gameJar = new ZipFile(file.toFile())) {
            @Nullable ZipEntry versionJson = gameJar.getEntry("version.json");
            if (versionJson != null) {
                @Nullable String result = getVersionFromJson(gameJar.getInputStream(versionJson));
                if (result != null)
                    return result;
            }

            @Nullable ZipEntry minecraft = gameJar.getEntry("net/minecraft/client/Minecraft.class");
            if (minecraft != null) {
                try (InputStream is = gameJar.getInputStream(minecraft)) {
                    @Nullable String version = getVersionOfClassMinecraft(is);
                    if (version != null) {
                        // For Minecraft 1.0 rc1/rc2-1/rc2-2, this value is "RC1"
                        // For Minecraft 1.0 rc2-3, this value is "RC2"
                        if (!version.equals("RC1") && !version.equals("RC2")) {
                            if (version.equals("2.0")) {
                                @Nullable String knownVersion = getKnownVersion(file);
                                return knownVersion != null ? knownVersion : version;
                            }
                            if (version.startsWith("Beta ")) {
                                return "b" + version.substring("Beta ".length());
                            } else if (version.startsWith("Alpha v")) {
                                return "a" + version.substring("Alpha v".length());
                            }
                            return version;
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
    /// @return the known version, or `null` if the hash is unknown or an I/O error occurs
    private static @Nullable String getKnownVersion(Path file) {
        try {
            ByteArrayKey digest = ByteArrayKey.wrap(DigestUtils.digest("SHA-1", file));
            return VERSIONS.get(digest);
        } catch (Exception e) {
            return null;
        }
    }

}
