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
package org.jackhuang.hmcl.addon.mod;

import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jackhuang.hmcl.util.tree.ZipFileTree;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Verifies nested metadata, dependency, alias, conflict, and JarJar selection extraction.
@NotNullByDefault
public final class NestedJarInspectorTest {
    /// Preserves Fabric dependency predicates, aliases, and conflicts in a nested node.
    @Test
    public void testFabricRelations(@TempDir Path tempDirectory) throws IOException {
        byte[] child = zip(Map.of("fabric.mod.json", """
                {
                  "schemaVersion": 1,
                  "id": "child",
                  "name": "Child",
                  "version": "2.1.0",
                  "depends": {"library": ">=2 <3"},
                  "provides": ["child-api"],
                  "breaks": {"bad-mod": "<2"}
                }
                """.getBytes(StandardCharsets.UTF_8)));
        Map<String, byte[]> hostEntries = new LinkedHashMap<>();
        hostEntries.put("fabric.mod.json", """
                {"schemaVersion":1,"id":"host","version":"1.0.0","jars":[{"file":"META-INF/jars/child.jar"}]}
                """.getBytes(StandardCharsets.UTF_8));
        hostEntries.put("META-INF/jars/child.jar", child);
        Path host = tempDirectory.resolve("host.jar");
        Files.write(host, zip(hostEntries));

        try (ZipFileTree tree = CompressingUtils.openZipTree(host)) {
            NestedJarInspector.ScanResult result = NestedJarInspector.scan(tree);
            assertFalse(result.truncated());
            assertEquals(1, result.tree().size());
            NestedJarInspector.NestedJar node = result.tree().get(0);
            assertEquals("child", node.id());
            assertEquals("2.1.0", node.version());
            assertEquals(">=2 <3", node.dependencies().get(0).versionConstraint());
            assertEquals("2.1.0", node.providedVersions().get("child-api"));
            assertEquals("bad-mod", node.conflicts().get(0).id());
            assertTrue(node.conflicts().get(0).hard());
        }
    }

    /// Preserves Forge JarJar coordinate, allowed range, and actual artifact version.
    @Test
    public void testJarJarSelectionMetadata(@TempDir Path tempDirectory) throws IOException {
        byte[] child = zip(Map.of("fabric.mod.json", """
                {"schemaVersion":1,"id":"library","name":"Library","version":"2.4.0"}
                """.getBytes(StandardCharsets.UTF_8)));
        Map<String, byte[]> hostEntries = new LinkedHashMap<>();
        hostEntries.put("META-INF/jarjar/metadata.json", """
                {"jars":[{"identifier":{"group":"example","artifact":"library"},"version":{"range":"[2,3)","artifactVersion":"2.4.0"},"path":"META-INF/jarjar/library.jar"}]}
                """.getBytes(StandardCharsets.UTF_8));
        hostEntries.put("META-INF/jarjar/library.jar", child);
        Path host = tempDirectory.resolve("host.jar");
        Files.write(host, zip(hostEntries));

        try (ZipFileTree tree = CompressingUtils.openZipTree(host)) {
            NestedJarInspector.NestedJar node = NestedJarInspector.scan(tree).tree().get(0);
            assertEquals("example:library", node.jijIdentifier());
            assertEquals("[2,3)", node.jijVersionRange());
            assertEquals("2.4.0", node.jijArtifactVersion());
        }
    }

    /// Preserves an optional Quilt Minecraft declaration without treating it as a load filter.
    @Test
    public void testOptionalMinecraftConstraint(@TempDir Path tempDirectory) throws IOException {
        byte[] child = zip(Map.of("quilt.mod.json", """
                {"schema_version":1,"quilt_loader":{"id":"child","version":"1.0.0","metadata":{"name":"Child","description":"","contributors":{},"contact":{}},"depends":[{"id":"minecraft","versions":"<1.20","optional":true}]}}
                """.getBytes(StandardCharsets.UTF_8)));
        Map<String, byte[]> hostEntries = new LinkedHashMap<>();
        hostEntries.put("fabric.mod.json", """
                {"schemaVersion":1,"id":"host","version":"1.0.0","jars":[{"file":"child.jar"}]}
                """.getBytes(StandardCharsets.UTF_8));
        hostEntries.put("child.jar", child);
        Path host = tempDirectory.resolve("host.jar");
        Files.write(host, zip(hostEntries));

        try (ZipFileTree tree = CompressingUtils.openZipTree(host)) {
            NestedJarInspector.NestedJar node = NestedJarInspector.scan(tree).tree().get(0);
            assertEquals("<1.20", node.minecraftVersion());
            assertFalse(node.minecraftConstraintRequired());
        }
    }

    /// Selects the descriptor matching the instance loader when a nested jar contains both formats.
    @Test
    public void testPreferredLoaderForDualDescriptor(@TempDir Path tempDirectory) throws IOException {
        Map<String, byte[]> childEntries = new LinkedHashMap<>();
        childEntries.put("fabric.mod.json", """
                {"schemaVersion":1,"id":"fabric-child","name":"Fabric Child","version":"1.0.0"}
                """.getBytes(StandardCharsets.UTF_8));
        childEntries.put("META-INF/mods.toml", """
                modLoader="javafml"
                loaderVersion="[1,)"
                license="MIT"
                [[mods]]
                modId="forge_child"
                version="1.0.0"
                displayName="Forge Child"
                """.getBytes(StandardCharsets.UTF_8));
        Map<String, byte[]> hostEntries = new LinkedHashMap<>();
        hostEntries.put("fabric.mod.json", """
                {"schemaVersion":1,"id":"host","version":"1.0.0","jars":[{"file":"child.jar"}]}
                """.getBytes(StandardCharsets.UTF_8));
        hostEntries.put("child.jar", zip(childEntries));
        Path host = tempDirectory.resolve("host.jar");
        Files.write(host, zip(hostEntries));

        try (ZipFileTree tree = CompressingUtils.openZipTree(host)) {
            assertEquals(
                    "forge_child",
                    NestedJarInspector.scan(tree, Set.of(ModLoaderType.FORGE)).tree().get(0).id());
        }
        try (ZipFileTree tree = CompressingUtils.openZipTree(host)) {
            assertEquals(
                    "fabric-child",
                    NestedJarInspector.scan(tree, Set.of(ModLoaderType.FABRIC)).tree().get(0).id());
        }
    }

    /// Marks a tree incomplete when declarations continue beyond the configured depth budget.
    @Test
    public void testDepthLimitIsTruncated(@TempDir Path tempDirectory) throws IOException {
        Path host = tempDirectory.resolve("deep.jar");
        Files.write(host, nestedFabricJar("level0", NestedJarInspector.MAX_DEPTH + 1));
        try (ZipFileTree tree = CompressingUtils.openZipTree(host)) {
            assertTrue(NestedJarInspector.scan(tree).truncated());
        }
    }

    /// Marks a declaration incomplete when its referenced child entry is missing.
    @Test
    public void testMissingChildIsIncomplete(@TempDir Path tempDirectory) throws IOException {
        Path host = tempDirectory.resolve("missing.jar");
        Files.write(host, zip(Map.of("fabric.mod.json", """
                {"schemaVersion":1,"id":"host","version":"1.0.0","jars":[{"file":"missing-child.jar"}]}
                """.getBytes(StandardCharsets.UTF_8))));
        try (ZipFileTree tree = CompressingUtils.openZipTree(host)) {
            assertTrue(NestedJarInspector.scan(tree).truncated());
        }
    }

    /// Stops bounded extraction when the shared instance-wide byte budget is exhausted.
    @Test
    public void testSharedByteBudgetIsEnforced(@TempDir Path tempDirectory) throws IOException {
        byte[] child = zip(Map.of("fabric.mod.json", """
                {"schemaVersion":1,"id":"child","version":"1.0.0"}
                """.getBytes(StandardCharsets.UTF_8)));
        Path host = tempDirectory.resolve("budget.jar");
        Files.write(host, zip(Map.of(
                "fabric.mod.json", """
                        {"schemaVersion":1,"id":"host","version":"1.0.0","jars":[{"file":"child.jar"}]}
                        """.getBytes(StandardCharsets.UTF_8),
                "child.jar", child)));

        try (ZipFileTree tree = CompressingUtils.openZipTree(host)) {
            NestedJarInspector.ScanResult result = NestedJarInspector.scan(
                    tree, Set.of(), new NestedJarInspector.ScanContext(32));
            assertTrue(result.truncated());
        }
    }

    /// Stops extraction when one nested entry exceeds its own uncompressed-byte allowance.
    @Test
    public void testPerEntryByteBudgetIsEnforced(@TempDir Path tempDirectory) throws IOException {
        byte[] child = zip(Map.of("fabric.mod.json", """
                {"schemaVersion":1,"id":"child","version":"1.0.0"}
                """.getBytes(StandardCharsets.UTF_8)));
        Path host = tempDirectory.resolve("entry-budget.jar");
        Files.write(host, zip(Map.of(
                "fabric.mod.json", """
                        {"schemaVersion":1,"id":"host","version":"1.0.0","jars":[{"file":"child.jar"}]}
                        """.getBytes(StandardCharsets.UTF_8),
                "child.jar", child)));

        try (ZipFileTree tree = CompressingUtils.openZipTree(host)) {
            NestedJarInspector.ScanResult result = NestedJarInspector.scan(
                    tree, Set.of(), new NestedJarInspector.ScanContext(4096), 32, 4096);
            assertTrue(result.truncated());
        }
    }

    /// Counts actual stream bytes even when no trustworthy ZIP entry size is available.
    @Test
    public void testStreamingCopyCannotBypassByteBudget() {
        byte[] content = new byte[64];
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertThrows(IOException.class, () -> NestedJarInspector.copyWithBudget(
                new ByteArrayInputStream(content),
                output,
                new long[]{4096},
                new NestedJarInspector.ScanContext(4096),
                32));
        assertTrue(output.size() <= 32);
    }

    /// Aborts before extraction when a superseding caller cancels the scan context.
    @Test
    public void testCancellation(@TempDir Path tempDirectory) throws IOException {
        Path host = tempDirectory.resolve("cancelled.jar");
        Files.write(host, nestedFabricJar("host", 1));
        NestedJarInspector.ScanContext context = new NestedJarInspector.ScanContext();
        context.cancel();

        try (ZipFileTree tree = CompressingUtils.openZipTree(host)) {
            assertThrows(java.util.concurrent.CancellationException.class,
                    () -> NestedJarInspector.scan(tree, Set.of(), context));
        }
    }

    /// Creates a recursively nested Fabric jar with the requested remaining child depth.
    private static byte[] nestedFabricJar(String id, int remainingChildren) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        if (remainingChildren > 0) {
            entries.put("fabric.mod.json", ("""
                    {"schemaVersion":1,"id":"%s","version":"1.0.0","jars":[{"file":"child.jar"}]}
                    """).formatted(id).getBytes(StandardCharsets.UTF_8));
            entries.put("child.jar", nestedFabricJar(id + "x", remainingChildren - 1));
        } else {
            entries.put("fabric.mod.json", ("""
                    {"schemaVersion":1,"id":"%s","version":"1.0.0"}
                    """).formatted(id).getBytes(StandardCharsets.UTF_8));
        }
        return zip(entries);
    }

    /// Creates ZIP bytes from entry names and contents.
    private static byte[] zip(Map<String, byte[]> entries) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }
}
