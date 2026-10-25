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
package org.jackhuang.hmcl.download.forge;

import org.jackhuang.hmcl.download.forge.ForgeNewInstallProfile.Processor;
import org.jackhuang.hmcl.game.Artifact;
import org.jackhuang.hmcl.util.DigestUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Verifies the reuse decisions shared by the Forge and NeoForge install tasks.
@NotNullByDefault
public class ProcessorReuseTest {

    /// Accepts artifacts recorded by a successful run of the same installer.
    @Test
    public void testSidecarRecordsSuccessfulRun(@TempDir Path tempDirectory) throws IOException {
        Path patched = writeFile(tempDirectory.resolve("patched.jar"), "patched");
        Path installer = writeFile(tempDirectory.resolve("installer.jar"), "installer");
        List<Processor> processors = List.of(binaryPatcher());

        ProcessorReuse.writeSidecar(processors, Map.of("PATCHED", patched.toString()), installer);

        assertTrue(ProcessorReuse.canReuseRecordedArtifacts(
                processors, Map.of("PATCHED", patched.toString()), installer));
    }

    /// Rejects the sidecar once an artifact it recorded has changed.
    @Test
    public void testSidecarInvalidatedWhenArtifactChanges(@TempDir Path tempDirectory) throws IOException {
        Path patched = writeFile(tempDirectory.resolve("patched.jar"), "patched");
        Path installer = writeFile(tempDirectory.resolve("installer.jar"), "installer");
        List<Processor> processors = List.of(binaryPatcher());
        ProcessorReuse.writeSidecar(processors, Map.of("PATCHED", patched.toString()), installer);

        writeFile(patched, "changed");

        assertFalse(ProcessorReuse.canReuseRecordedArtifacts(
                processors, Map.of("PATCHED", patched.toString()), installer));
    }

    /// Rejects the sidecar when the artifacts were produced by another installer.
    @Test
    public void testSidecarInvalidatedWhenInstallerChanges(@TempDir Path tempDirectory) throws IOException {
        Path patched = writeFile(tempDirectory.resolve("patched.jar"), "patched");
        Path installer = writeFile(tempDirectory.resolve("installer.jar"), "installer");
        List<Processor> processors = List.of(binaryPatcher());
        ProcessorReuse.writeSidecar(processors, Map.of("PATCHED", patched.toString()), installer);

        writeFile(installer, "another installer");

        assertFalse(ProcessorReuse.canReuseRecordedArtifacts(
                processors, Map.of("PATCHED", patched.toString()), installer));
    }

    /// Refuses to skip a chain containing a processor with unknown side effects.
    @Test
    public void testSidecarRejectedForUnknownProcessor(@TempDir Path tempDirectory) throws IOException {
        Path patched = writeFile(tempDirectory.resolve("patched.jar"), "patched");
        Path installer = writeFile(tempDirectory.resolve("installer.jar"), "installer");
        List<Processor> processors = List.of(binaryPatcher());
        ProcessorReuse.writeSidecar(processors, Map.of("PATCHED", patched.toString()), installer);

        List<Processor> unknown = List.of(new Processor(
                List.of("client"),
                Artifact.fromDescriptor("example:processor:1.0"),
                List.of(),
                List.of(),
                Map.of()));

        assertFalse(ProcessorReuse.canReuseRecordedArtifacts(
                unknown, Map.of("PATCHED", patched.toString()), installer));
    }

    /// Accepts a patched jar matching the checksum declared by the install profile.
    @Test
    public void testDeclaredChecksum(@TempDir Path tempDirectory) throws IOException {
        Path patched = writeFile(tempDirectory.resolve("patched.jar"), "patched");
        List<Processor> processors = List.of(binaryPatcher());

        Map<String, String> matching = Map.of(
                "PATCHED", patched.toString(),
                "PATCHED_SHA", DigestUtils.digestToString("SHA-1", patched));
        assertTrue(ProcessorReuse.canReuseDeclaredArtifacts(processors, matching));

        Map<String, String> mismatching = Map.of(
                "PATCHED", patched.toString(),
                "PATCHED_SHA", "0000000000000000000000000000000000000000");
        assertFalse(ProcessorReuse.canReuseDeclaredArtifacts(processors, mismatching));
    }

    /// Records the slim, srg and patched jars even though the profile declares no outputs.
    @Test
    public void testSidecarRecordsUndeclaredArtifacts(@TempDir Path tempDirectory) throws IOException {
        Path patched = writeFile(tempDirectory.resolve("patched.jar"), "patched");
        Path slim = writeFile(tempDirectory.resolve("slim.jar"), "slim");
        Path extra = writeFile(tempDirectory.resolve("extra.jar"), "extra");
        Path srg = writeFile(tempDirectory.resolve("srg.jar"), "srg");
        Path installer = writeFile(tempDirectory.resolve("installer.jar"), "installer");
        List<Processor> processors = List.of(jarsplitter(), autoRenamingTool(), binaryPatcher());
        Map<String, String> vars = Map.of(
                "PATCHED", patched.toString(),
                "MC_SLIM", slim.toString(),
                "MC_EXTRA", extra.toString(),
                "MC_SRG", srg.toString());

        ProcessorReuse.writeSidecar(processors, vars, installer);
        assertTrue(ProcessorReuse.canReuseRecordedArtifacts(processors, vars, installer));

        Files.delete(srg);
        assertFalse(ProcessorReuse.canReuseRecordedArtifacts(processors, vars, installer));
    }

    /// Rejects a chain whose artifacts are not all covered by a declared checksum.
    @Test
    public void testDeclaredChecksumRequiresEveryArtifact(@TempDir Path tempDirectory) throws IOException {
        Path patched = writeFile(tempDirectory.resolve("patched.jar"), "patched");
        Path mappings = writeFile(tempDirectory.resolve("mappings.txt"), "mappings");
        List<Processor> processors = List.of(mcpData(), binaryPatcher());

        assertFalse(ProcessorReuse.canReuseDeclaredArtifacts(processors, Map.of(
                "PATCHED", patched.toString(),
                "PATCHED_SHA", DigestUtils.digestToString("SHA-1", patched),
                "MAPPINGS", mappings.toString())));
    }

    /// Verifies an artifact reached through an `--output` argument against its declared checksum.
    @Test
    public void testDeclaredChecksumOfDerivedArtifact(@TempDir Path tempDirectory) throws IOException {
        Path patched = writeFile(tempDirectory.resolve("patched.jar"), "patched");
        Path srg = writeFile(tempDirectory.resolve("srg.jar"), "srg");
        List<Processor> processors = List.of(autoRenamingTool(), binaryPatcher());
        Map<String, String> vars = Map.of(
                "PATCHED", patched.toString(),
                "PATCHED_SHA", DigestUtils.digestToString("SHA-1", patched),
                "MC_SRG", srg.toString(),
                "MC_SRG_SHA", DigestUtils.digestToString("SHA-1", srg));

        assertTrue(ProcessorReuse.canReuseDeclaredArtifacts(processors, vars));

        Files.delete(srg);
        assertFalse(ProcessorReuse.canReuseDeclaredArtifacts(processors, vars));
    }

    /// Creates a jarsplitter processor, which writes the slim and extra jars through options.
    ///
    /// @return the processor
    private static Processor jarsplitter() {
        return new Processor(
                List.of("client"),
                Artifact.fromDescriptor("net.neoforged.installertools:jarsplitter:2.1.2"),
                List.of(),
                List.of("--input", "{MINECRAFT_JAR}", "--slim", "{MC_SLIM}", "--extra", "{MC_EXTRA}",
                        "--srg", "{MERGED_MAPPINGS}"),
                Map.of());
    }

    /// Creates a renamer processor, which writes the srg jar through an option.
    ///
    /// @return the processor
    private static Processor autoRenamingTool() {
        return new Processor(
                List.of(),
                Artifact.fromDescriptor("net.neoforged:AutoRenamingTool:2.0.3:all"),
                List.of(),
                List.of("--input", "{MC_SLIM}", "--output", "{MC_SRG}", "--names", "{MERGED_MAPPINGS}"),
                Map.of());
    }

    /// Creates an installer-tools processor, which writes its artifact through an option.
    ///
    /// @return the processor
    private static Processor mcpData() {
        return new Processor(
                List.of(),
                Artifact.fromDescriptor("net.neoforged.installertools:installertools:2.1.2"),
                List.of(),
                List.of("--task", "MCP_DATA", "--input", "{INSTALLER}", "--output", "{MAPPINGS}",
                        "--key", "mappings"),
                Map.of());
    }

    /// Creates a client processor whose only output is the patched jar.
    ///
    /// @return the processor
    private static Processor binaryPatcher() {
        return new Processor(
                List.of("client"),
                Artifact.fromDescriptor("net.minecraftforge:binarypatcher:1.1.1"),
                List.of(),
                List.of("--clean", "{MC_SRG}", "--output", "{PATCHED}"),
                Map.of());
    }

    /// Writes the file, replacing its previous content.
    ///
    /// @param file    the destination file
    /// @param content the file content
    /// @return the destination file
    private static Path writeFile(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
        return file;
    }
}
