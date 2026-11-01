// SPDX-FileCopyrightText: Copyright (C) 2026 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.gradle.component;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.gradle.testfixtures.ProjectBuilder;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/// Verifies CSV generation and preservation of existing output when source metadata is unusable.
@NotNullByDefault
final class UpdateClientSha1Test {
    /// Valid client hash shared by multiple fixture versions.
    private static final String SHA1 = "0123456789abcdef0123456789abcdef01234567";

    /// Isolates metadata fixtures and task output for each test.
    @TempDir
    Path directory;

    /// Creates a task using local metadata instead of the public manifest.
    private UpdateClientSha1 createTask(String manifest) throws IOException {
        Path source = directory.resolve("manifest.json");
        Files.writeString(source, manifest);
        var task = ProjectBuilder.builder().withProjectDir(directory.toFile()).build().getTasks()
                .register("updateClientSha1", UpdateClientSha1.class).get();
        task.getManifestUrl().set(source.toUri().toString());
        task.getOutputFile().set(directory.resolve("output/client-sha1.csv").toFile());
        return task;
    }

    /// Writes metadata for a fixture version and returns its manifest entry.
    private JsonObject version(String id, String metadata) throws IOException {
        Path source = Files.createTempFile(directory, "version-", ".json");
        Files.writeString(source, metadata);
        var entry = new JsonObject();
        entry.addProperty("id", id);
        entry.addProperty("url", source.toUri().toString());
        // The manifest hash identifies the JSON, not the client JAR.
        entry.addProperty("sha1", "f".repeat(40));
        return entry;
    }

    /// Creates a manifest containing the supplied entries in their original order.
    private static String manifest(JsonObject... entries) {
        var versions = new JsonArray();
        for (var entry : entries) {
            versions.add(entry);
        }
        var manifest = new JsonObject();
        manifest.add("versions", versions);
        return manifest.toString();
    }

    /// Sorts version identifiers, retains shared client hashes, and refreshes an existing output.
    @Test
    void generatesAndRefreshesCsv() throws Exception {
        var beta = version("b1.0", "{\"id\":\"b1.0\",\"downloads\":{\"client\":{\"sha1\":\"" + SHA1 + "\"}}}");
        var alpha = version("a1.0", "{\"id\":\"a1.0\",\"downloads\":{\"client\":{\"sha1\":\"" + SHA1 + "\"}}}");
        var task = createTask(manifest(beta, alpha));
        Path output = task.getOutputFile().get().getAsFile().toPath();

        task.run();
        assertEquals("a1.0," + SHA1 + "\nb1.0," + SHA1 + "\n", Files.readString(output));

        Files.writeString(directory.resolve("manifest.json"), manifest(beta));
        task.run();
        assertEquals("b1.0," + SHA1 + "\n", Files.readString(output));
    }

    /// Rejects malformed or incomplete version metadata without replacing a previously generated table.
    @ParameterizedTest
    @ValueSource(strings = {
            "not json", "null", "{}", "{\"id\":\"other\"}",
            "{\"id\":\"test\",\"downloads\":{}}",
            "{\"id\":\"test\",\"downloads\":{\"client\":{\"sha1\":null}}}",
            "{\"id\":\"test\",\"downloads\":{\"client\":{\"sha1\":123}}}",
            "{\"id\":\"test\",\"downloads\":{\"client\":{\"sha1\":\"invalid\"}}}"
    })
    void preservesOutputOnInvalidMetadata(String metadata) throws Exception {
        var task = createTask(manifest(version("test", metadata)));
        assertPreservesOutput(task);
    }

    /// Rejects missing files, duplicate identifiers, and identifiers that cannot form unquoted CSV fields.
    @Test
    void rejectsInvalidEntries() throws Exception {
        var entry = new JsonObject();
        entry.addProperty("id", "test");
        entry.addProperty("url", directory.resolve("missing.json").toUri().toString());
        var task = createTask(manifest(entry));
        assertPreservesOutput(task);

        Files.writeString(directory.resolve("manifest.json"), manifest(entry, entry));
        assertPreservesOutput(task);

        entry.addProperty("id", "invalid,identifier");
        Files.writeString(directory.resolve("manifest.json"), manifest(entry));
        assertPreservesOutput(task);
    }

    /// Rejects an absent, empty, or wrongly typed version list without replacing existing output.
    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"versions\":[]}", "{\"versions\":null}", "{\"versions\":[null]}"})
    void rejectsInvalidManifest(String manifest) throws Exception {
        assertPreservesOutput(createTask(manifest));
    }

    /// Checks failure leaves the old table intact and no temporary output files behind.
    private static void assertPreservesOutput(UpdateClientSha1 task) throws Exception {
        Path output = task.getOutputFile().get().getAsFile().toPath();
        Files.createDirectories(output.getParent());
        Files.writeString(output, "previous contents\n");
        assertThrows(IOException.class, task::run);
        assertEquals("previous contents\n", Files.readString(output));
        try (var files = Files.list(output.getParent())) {
            assertEquals(1, files.count());
        }
    }
}
