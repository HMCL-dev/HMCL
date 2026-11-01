// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.gradle.component;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/// Updates selected client hashes from Mojang and the embedded unlisted version manifest.
/// The task downloads fresh metadata on every invocation, independently of normal resource processing.
@NotNullByDefault
@DisableCachingByDefault(because = "Downloads mutable remote metadata to update a checked-in resource")
public abstract class UpdateClientSha1 extends DefaultTask {
    /// April Fools version identifiers included even when their manifest type is snapshot.
    private static final @Unmodifiable Set<String> APRIL_FOOLS_VERSIONS = Set.of(
            "2.0_blue", "2.0_red", "2.0_purple", "15w14a", "1.RV-Pre1", "3D Shareware v1.34", "20w14infinite",
            "22w13oneblockatatime", "23w13a_or_b", "24w14potato", "25w14craftmine", "26w14a"
    );

    /// Configures the official manifest endpoint and disables up-to-date skipping.
    public UpdateClientSha1() {
        getManifestUrl().convention("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json");
        getOutputs().upToDateWhen(task -> false);
    }

    /// Returns the absolute URI of the version manifest to read.
    @Input
    public abstract Property<String> getManifestUrl();

    /// Returns the local manifest supplying version metadata URLs omitted from the official manifest.
    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getUnlistedVersionsFile();

    /// Returns the destination of the UTF-8 CSV, with SPDX comment lines and LF-terminated `version,sha1` rows.
    /// There is no column header.
    /// Rows follow manifest order, with official versions preceding unlisted versions.
    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    /// Generates the CSV from the official and unlisted manifests and freshly downloaded metadata.
    /// Includes releases, old alpha and beta versions, and the snapshots in [#APRIL_FOOLS_VERSIONS].
    /// Does not read the destination file. Different version identifiers
    /// may have the same client hash; all such entries are retained.
    /// Downloads at most eight version metadata files concurrently. Existing output is left unchanged
    /// if downloading, validation, or writing the temporary file fails.
    ///
    /// @throws IOException if the manifest is empty, metadata is malformed or duplicated,
    ///                     a download fails, or the output cannot be written or replaced
    /// @throws InterruptedException if interrupted while waiting for version metadata
    @TaskAction
    public void run() throws IOException, InterruptedException {
        var output = getOutputFile().get().getAsFile().toPath().toAbsolutePath();
        var records = new LinkedHashMap<String, String>();
        JsonObject manifest = readJson(getManifestUrl().get());
        @Nullable JsonElement versions = manifest.get("versions");
        if (versions == null || !versions.isJsonArray() || versions.getAsJsonArray().isEmpty()) {
            throw new IOException("Missing or empty version list");
        }

        var entries = versions.getAsJsonArray();
        JsonObject unlisted = readJson(getUnlistedVersionsFile().get().getAsFile().toURI().toString());
        @Nullable JsonElement unlistedVersions = unlisted.get("versions");
        if (unlistedVersions == null || !unlistedVersions.isJsonArray()) {
            throw new IOException("Missing or invalid unlisted version list");
        }
        entries.addAll(unlistedVersions.getAsJsonArray());

        var executor = Executors.newFixedThreadPool(8);
        var hashes = new LinkedHashMap<String, Future<String>>();
        var seen = new HashSet<String>();
        try {
            for (JsonElement element : entries) {
                if (!element.isJsonObject()) {
                    throw new IOException("Invalid version manifest entry: " + element);
                }
                JsonObject version = element.getAsJsonObject();
                String id = requireString(version, "id");
                validateId(id);
                if (!seen.add(id)) {
                    throw new IOException("Duplicate version identifier: " + id);
                }
                String type = requireString(version, "type");
                if (!Set.of("release", "old_alpha", "old_beta").contains(type)
                        && !APRIL_FOOLS_VERSIONS.contains(id)) {
                    continue;
                }
                String url = requireString(version, "url");
                hashes.put(id, executor.submit(() -> readClientSha1(id, url)));
            }

            for (var entry : hashes.entrySet()) {
                try {
                    records.put(entry.getKey(), entry.getValue().get());
                } catch (ExecutionException e) {
                    throw new IOException("Failed to read client SHA-1 for " + entry.getKey(), e.getCause());
                }
            }
        } finally {
            executor.shutdownNow();
        }

        var csv = new StringBuilder("""
                # SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
                # SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
                """);
        for (var entry : records.entrySet()) {
            csv.append(entry.getKey()).append(',').append(entry.getValue()).append('\n');
        }
        var directory = output.getParent();
        Files.createDirectories(directory);
        var temporary = Files.createTempFile(directory, "client-sha1-", ".csv.tmp");
        try {
            Files.writeString(temporary, csv, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, output, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
        getLogger().lifecycle("Updated {} client SHA-1 entries in {}", records.size(), output);
    }

    /// Validates a nonblank version identifier that can be written as an unquoted CSV field.
    ///
    /// @throws IOException if the identifier is blank or contains a comma, quote, or line break
    private static void validateId(String id) throws IOException {
        if (id.isBlank() || id.indexOf(',') >= 0 || id.indexOf('"') >= 0
                || id.indexOf('\r') >= 0 || id.indexOf('\n') >= 0) {
            throw new IOException("Invalid version identifier: " + id);
        }
    }

    /// Reads a version's client hash, verifying its identifier and the 40-digit lowercase hexadecimal hash.
    ///
    /// @throws IOException if downloading fails, the identifier differs, or required metadata is invalid
    private static String readClientSha1(String id, String url) throws IOException {
        JsonObject version = readJson(url);
        if (!id.equals(requireString(version, "id"))) {
            throw new IOException("Version identifier mismatch for " + id);
        }
        JsonObject client = requireObject(requireObject(version, "downloads"), "client");
        String sha1 = requireString(client, "sha1");
        if (!sha1.matches("[0-9a-f]{40}")) {
            throw new IOException("Invalid client SHA-1 for " + id + ": " + sha1);
        }
        return sha1;
    }

    /// Reads a UTF-8 JSON object with a 15-second connection timeout and a 30-second read timeout.
    /// Closes the input stream before returning or propagating an error.
    ///
    /// @throws IOException if the URI is invalid, reading fails, or the document is not a JSON object
    private static JsonObject readJson(String url) throws IOException {
        try {
            var connection = URI.create(url).toURL().openConnection();
            connection.setConnectTimeout(15_000);
            connection.setReadTimeout(30_000);
            try (var input = connection.getInputStream()) {
                return JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            }
        } catch (IOException | RuntimeException e) {
            throw new IOException("Failed to read version metadata from " + url, e);
        }
    }

    /// Returns a required JSON object member.
    ///
    /// @throws IOException if the member is absent, null, or not an object
    private static JsonObject requireObject(JsonObject object, String name) throws IOException {
        @Nullable JsonElement value = object.get(name);
        if (value == null || !value.isJsonObject()) {
            throw new IOException("Missing or invalid object: " + name);
        }
        return value.getAsJsonObject();
    }

    /// Returns a required JSON string member without coercing other primitive types.
    ///
    /// @throws IOException if the member is absent, null, or not a string
    private static String requireString(JsonObject object, String name) throws IOException {
        @Nullable JsonElement value = object.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IOException("Missing or invalid string: " + name);
        }
        return value.getAsString();
    }
}
