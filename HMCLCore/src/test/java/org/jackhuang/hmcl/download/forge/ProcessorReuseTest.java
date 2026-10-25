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
import java.util.ArrayList;
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
        Map<String, String> vars = sidecarVars(patched, minecraftJar(tempDirectory));

        ProcessorReuse.writeSidecar(processors, vars, installer);

        assertTrue(ProcessorReuse.canReuseRecordedArtifacts(processors, vars, installer));
    }

    /// Rejects the sidecar once an artifact it recorded has changed.
    @Test
    public void testSidecarInvalidatedWhenArtifactChanges(@TempDir Path tempDirectory) throws IOException {
        Path patched = writeFile(tempDirectory.resolve("patched.jar"), "patched");
        Path installer = writeFile(tempDirectory.resolve("installer.jar"), "installer");
        List<Processor> processors = List.of(binaryPatcher());
        Map<String, String> vars = sidecarVars(patched, minecraftJar(tempDirectory));
        ProcessorReuse.writeSidecar(processors, vars, installer);

        writeFile(patched, "changed");

        assertFalse(ProcessorReuse.canReuseRecordedArtifacts(processors, vars, installer));
    }

    /// Rejects the sidecar when the client jar it was produced from has changed.
    @Test
    public void testSidecarInvalidatedWhenMinecraftJarChanges(@TempDir Path tempDirectory) throws IOException {
        Path patched = writeFile(tempDirectory.resolve("patched.jar"), "patched");
        Path installer = writeFile(tempDirectory.resolve("installer.jar"), "installer");
        Path clientJar = minecraftJar(tempDirectory);
        List<Processor> processors = List.of(binaryPatcher());
        Map<String, String> vars = sidecarVars(patched, clientJar);
        ProcessorReuse.writeSidecar(processors, vars, installer);

        writeFile(clientJar, "another minecraft");

        assertFalse(ProcessorReuse.canReuseRecordedArtifacts(processors, vars, installer));
    }

    /// Rejects the sidecar when the artifacts were produced by another installer.
    @Test
    public void testSidecarInvalidatedWhenInstallerChanges(@TempDir Path tempDirectory) throws IOException {
        Path patched = writeFile(tempDirectory.resolve("patched.jar"), "patched");
        Path installer = writeFile(tempDirectory.resolve("installer.jar"), "installer");
        List<Processor> processors = List.of(binaryPatcher());
        Map<String, String> vars = sidecarVars(patched, minecraftJar(tempDirectory));
        ProcessorReuse.writeSidecar(processors, vars, installer);

        writeFile(installer, "another installer");

        assertFalse(ProcessorReuse.canReuseRecordedArtifacts(processors, vars, installer));
    }

    /// Refuses to skip a chain containing a processor with unknown side effects.
    @Test
    public void testSidecarRejectedForUnknownProcessor(@TempDir Path tempDirectory) throws IOException {
        Path patched = writeFile(tempDirectory.resolve("patched.jar"), "patched");
        Path installer = writeFile(tempDirectory.resolve("installer.jar"), "installer");
        List<Processor> processors = List.of(binaryPatcher());
        Map<String, String> vars = sidecarVars(patched, minecraftJar(tempDirectory));
        ProcessorReuse.writeSidecar(processors, vars, installer);

        List<Processor> unknown = List.of(new Processor(
                List.of("client"),
                Artifact.fromDescriptor("example:processor:1.0"),
                List.of(),
                List.of(),
                Map.of()));

        assertFalse(ProcessorReuse.canReuseRecordedArtifacts(unknown, vars, installer));
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
                "MINECRAFT_JAR", minecraftJar(tempDirectory).toString(),
                "MC_SLIM", slim.toString(),
                "MC_EXTRA", extra.toString(),
                "MC_SRG", srg.toString());

        ProcessorReuse.writeSidecar(processors, vars, installer);
        assertTrue(ProcessorReuse.canReuseRecordedArtifacts(processors, vars, installer));

        Files.delete(srg);
        assertFalse(ProcessorReuse.canReuseRecordedArtifacts(processors, vars, installer));
    }

    /// Records the srg jar written by SpecialSource through `--out-jar`, which Forge 1.13–1.16.5
    /// profiles declare no outputs for.
    @Test
    public void testSidecarRecordsSpecialSourceOutput(@TempDir Path tempDirectory) throws IOException {
        Path patched = writeFile(tempDirectory.resolve("patched.jar"), "patched");
        Path slim = writeFile(tempDirectory.resolve("slim.jar"), "slim");
        Path extra = writeFile(tempDirectory.resolve("extra.jar"), "extra");
        Path srg = writeFile(tempDirectory.resolve("srg.jar"), "srg");
        Path installer = writeFile(tempDirectory.resolve("installer.jar"), "installer");
        List<Processor> processors = List.of(jarsplitter(), specialSource(), binaryPatcher());
        Map<String, String> vars = Map.of(
                "PATCHED", patched.toString(),
                "MINECRAFT_JAR", minecraftJar(tempDirectory).toString(),
                "MC_SLIM", slim.toString(),
                "MC_EXTRA", extra.toString(),
                "MC_SRG", srg.toString());

        ProcessorReuse.writeSidecar(processors, vars, installer);
        assertTrue(ProcessorReuse.canReuseRecordedArtifacts(processors, vars, installer));

        Files.delete(srg);
        assertFalse(ProcessorReuse.canReuseRecordedArtifacts(processors, vars, installer));
    }

    /// Records the srg jar written by vignette through `--jar-out`, which Forge 1.17.1 profiles
    /// declare no outputs for. Uses the `--option=value` form.
    @Test
    public void testSidecarRecordsVignetteOutput(@TempDir Path tempDirectory) throws IOException {
        Path patched = writeFile(tempDirectory.resolve("patched.jar"), "patched");
        Path slim = writeFile(tempDirectory.resolve("slim.jar"), "slim");
        Path extra = writeFile(tempDirectory.resolve("extra.jar"), "extra");
        Path srg = writeFile(tempDirectory.resolve("srg.jar"), "srg");
        Path installer = writeFile(tempDirectory.resolve("installer.jar"), "installer");
        List<Processor> processors = List.of(jarsplitter(), vignette(), binaryPatcher());
        Map<String, String> vars = Map.of(
                "PATCHED", patched.toString(),
                "MINECRAFT_JAR", minecraftJar(tempDirectory).toString(),
                "MC_SLIM", slim.toString(),
                "MC_EXTRA", extra.toString(),
                "MC_SRG", srg.toString());

        ProcessorReuse.writeSidecar(processors, vars, installer);
        assertTrue(ProcessorReuse.canReuseRecordedArtifacts(processors, vars, installer));

        Files.delete(srg);
        assertFalse(ProcessorReuse.canReuseRecordedArtifacts(processors, vars, installer));
    }

    /// Refuses to skip a chain whose remapping output has no declared checksum, even when every
    /// other declared artifact still matches.
    @Test
    public void testDeclaredChecksumRejectsUnverifiableSpecialSourceOutput(@TempDir Path tempDirectory) throws IOException {
        Path patched = writeFile(tempDirectory.resolve("patched.jar"), "patched");
        Path slim = writeFile(tempDirectory.resolve("slim.jar"), "slim");
        Path extra = writeFile(tempDirectory.resolve("extra.jar"), "extra");
        Path srg = writeFile(tempDirectory.resolve("srg.jar"), "srg");
        List<Processor> processors = List.of(jarsplitter(), specialSource(), binaryPatcher());
        Map<String, String> vars = Map.of(
                "PATCHED", patched.toString(),
                "PATCHED_SHA", DigestUtils.digestToString("SHA-1", patched),
                "MC_SLIM", slim.toString(),
                "MC_SLIM_SHA", DigestUtils.digestToString("SHA-1", slim),
                "MC_EXTRA", extra.toString(),
                "MC_EXTRA_SHA", DigestUtils.digestToString("SHA-1", extra),
                "MC_SRG", srg.toString());

        assertFalse(ProcessorReuse.canReuseDeclaredArtifacts(processors, vars));
    }

    /// Refuses to skip the chain when the patched jar is missing, even though the profile declares
    /// no checksum for it.
    @Test
    public void testDeclaredChecksumRequiresPatchedJar(@TempDir Path tempDirectory) throws IOException {
        Path patched = tempDirectory.resolve("patched.jar");
        Path slim = writeFile(tempDirectory.resolve("slim.jar"), "slim");
        Path extra = writeFile(tempDirectory.resolve("extra.jar"), "extra");
        List<Processor> processors = List.of(jarsplitter(), binaryPatcher());
        Map<String, String> vars = Map.of(
                "PATCHED", patched.toString(),
                "MC_SLIM", slim.toString(),
                "MC_SLIM_SHA", DigestUtils.digestToString("SHA-1", slim),
                "MC_EXTRA", extra.toString(),
                "MC_EXTRA_SHA", DigestUtils.digestToString("SHA-1", extra));

        assertFalse(ProcessorReuse.canReuseDeclaredArtifacts(processors, vars));
    }

    /// Accepts the raw mappings HMCL downloads in place of the sanitized file the profile
    /// describes.
    @Test
    public void testDeclaredChecksumAcceptsRawMojmaps(@TempDir Path tempDirectory) throws IOException {
        Path mojmaps = writeFile(tempDirectory.resolve("mojmaps.tsrg"), "raw mappings");
        Path patched = writeFile(tempDirectory.resolve("patched.jar"), "patched");
        List<Processor> processors = List.of(downloadMojmaps(true), binaryPatcher());
        Map<String, String> vars = Map.of(
                "MOJMAPS", mojmaps.toString(),
                "MOJMAPS_SHA", "0000000000000000000000000000000000000000",
                "PATCHED", patched.toString(),
                "PATCHED_SHA", DigestUtils.digestToString("SHA-1", patched));

        assertTrue(ProcessorReuse.canReuseDeclaredArtifacts(processors, vars));
    }

    /// Requires the mappings file to exist even when its declared checksum cannot be used.
    @Test
    public void testDeclaredChecksumRequiresSanitizedMojmapsFile(@TempDir Path tempDirectory) throws IOException {
        Path patched = writeFile(tempDirectory.resolve("patched.jar"), "patched");
        List<Processor> processors = List.of(downloadMojmaps(true), binaryPatcher());
        Map<String, String> vars = Map.of(
                "MOJMAPS", tempDirectory.resolve("missing.tsrg").toString(),
                "MOJMAPS_SHA", "0000000000000000000000000000000000000000",
                "PATCHED", patched.toString(),
                "PATCHED_SHA", DigestUtils.digestToString("SHA-1", patched));

        assertFalse(ProcessorReuse.canReuseDeclaredArtifacts(processors, vars));
    }

    /// Verifies the mappings file against its declared checksum when the processor does not
    /// sanitize it.
    @Test
    public void testDeclaredChecksumVerifiesMojmapsWithoutSanitize(@TempDir Path tempDirectory) throws IOException {
        Path mojmaps = writeFile(tempDirectory.resolve("mojmaps.tsrg"), "raw mappings");
        Path patched = writeFile(tempDirectory.resolve("patched.jar"), "patched");
        List<Processor> processors = List.of(downloadMojmaps(false), binaryPatcher());
        Map<String, String> vars = Map.of(
                "MOJMAPS", mojmaps.toString(),
                "MOJMAPS_SHA", DigestUtils.digestToString("SHA-1", mojmaps),
                "PATCHED", patched.toString(),
                "PATCHED_SHA", DigestUtils.digestToString("SHA-1", patched));

        assertTrue(ProcessorReuse.canReuseDeclaredArtifacts(processors, vars));

        writeFile(mojmaps, "changed");
        assertFalse(ProcessorReuse.canReuseDeclaredArtifacts(processors, vars));
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

    /// Refuses to skip a chain that writes a path which is not an artifact variable.
    @Test
    public void testRejectedForUnnamedWriteTarget(@TempDir Path tempDirectory) throws IOException {
        Path patched = writeFile(tempDirectory.resolve("patched.jar"), "patched");
        Path installer = writeFile(tempDirectory.resolve("installer.jar"), "installer");
        List<Processor> processors = List.of(new Processor(
                List.of("client"),
                Artifact.fromDescriptor("net.minecraftforge:binarypatcher:1.1.1"),
                List.of(),
                List.of("--clean", "{MC_SRG}", "--output", patched.toString()),
                Map.of()));

        Map<String, String> vars = sidecarVars(patched, minecraftJar(tempDirectory));

        ProcessorReuse.writeSidecar(processors, vars, installer);

        assertFalse(Files.exists(tempDirectory.resolve("patched.jar.hmcl-cache")));
        assertFalse(ProcessorReuse.canReuseRecordedArtifacts(processors, vars, installer));
    }

    /// Never skips a chain that extracts libraries from a bundler client jar.
    @Test
    public void testBundlerExtractionPreventsReuse(@TempDir Path tempDirectory) throws IOException {
        Path patched = writeFile(tempDirectory.resolve("patched.jar"), "patched");
        Path clientJar = ProcessorTestSupport.writeZip(tempDirectory.resolve("client.jar"),
                Map.of("META-INF/versions.list", ""));
        Path installer = writeFile(tempDirectory.resolve("installer.jar"), "installer");
        List<Processor> processors = List.of(processMinecraftJar());
        Map<String, String> vars = Map.of(
                "MINECRAFT_JAR", clientJar.toString(),
                "PATCHED", patched.toString(),
                "PATCHED_SHA", DigestUtils.digestToString("SHA-1", patched));

        assertFalse(ProcessorReuse.canReuseDeclaredArtifacts(processors, vars));

        ProcessorReuse.writeSidecar(processors, vars, installer);
        assertFalse(Files.exists(Path.of(patched + ".hmcl-cache")));
        assertFalse(ProcessorReuse.canReuseRecordedArtifacts(processors, vars, installer));
    }

    /// Allows reuse when the extraction option is a no-op for a plain client jar.
    @Test
    public void testPlainClientJarWithExtractionOptionCanBeReused(@TempDir Path tempDirectory) throws IOException {
        Path patched = writeFile(tempDirectory.resolve("patched.jar"), "patched");
        Path clientJar = ProcessorTestSupport.writeZip(tempDirectory.resolve("client.jar"),
                Map.of("version.json", "{}"));
        Path installer = writeFile(tempDirectory.resolve("installer.jar"), "installer");
        List<Processor> processors = List.of(processMinecraftJar());
        Map<String, String> vars = Map.of(
                "MINECRAFT_JAR", clientJar.toString(),
                "PATCHED", patched.toString(),
                "PATCHED_SHA", DigestUtils.digestToString("SHA-1", patched));

        assertTrue(ProcessorReuse.canReuseDeclaredArtifacts(processors, vars));

        ProcessorReuse.writeSidecar(processors, vars, installer);
        assertTrue(ProcessorReuse.canReuseRecordedArtifacts(processors, vars, installer));
    }

    /// Records hashes the processor tasks already verified instead of reading the files again.
    @Test
    public void testSidecarUsesKnownHashes(@TempDir Path tempDirectory) throws IOException {
        Path patched = writeFile(tempDirectory.resolve("patched.jar"), "patched");
        Path installer = writeFile(tempDirectory.resolve("installer.jar"), "installer");
        List<Processor> processors = List.of(binaryPatcher());
        Map<String, String> vars = sidecarVars(patched, minecraftJar(tempDirectory));

        // A hash that does not match the file proves the recorded value was used.
        ProcessorReuse.writeSidecar(processors, vars, installer,
                Map.of(patched, "0000000000000000000000000000000000000000"));

        assertFalse(ProcessorReuse.canReuseRecordedArtifacts(processors, vars, installer));
    }

    /// Recognizes the `--option=value` form used by newer processors.
    @Test
    public void testEqualsOptionForm(@TempDir Path tempDirectory) throws IOException {
        Path srg = writeFile(tempDirectory.resolve("srg.jar"), "srg");
        List<Processor> processors = List.of(new Processor(
                List.of("client"),
                Artifact.fromDescriptor("net.neoforged:AutoRenamingTool:2.0.3:all"),
                List.of(),
                List.of("--input", "{MC_SLIM}", "--output={MC_SRG}"),
                Map.of()));

        assertTrue(ProcessorReuse.canReuseDeclaredArtifacts(processors, Map.of(
                "MC_SRG", srg.toString(),
                "MC_SRG_SHA", DigestUtils.digestToString("SHA-1", srg))));
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

    /// Creates a SpecialSource processor, which writes the srg jar through `--out-jar`.
    ///
    /// @return the processor
    private static Processor specialSource() {
        return new Processor(
                List.of("client"),
                Artifact.fromDescriptor("net.md-5:SpecialSource:1.8.5"),
                List.of(),
                List.of("--in-jar", "{MC_SLIM}", "--out-jar", "{MC_SRG}", "--srg-in", "{MAPPINGS}"),
                Map.of());
    }

    /// Creates a vignette processor, which writes the srg jar through `--jar-out`.
    ///
    /// @return the processor
    private static Processor vignette() {
        return new Processor(
                List.of("client"),
                Artifact.fromDescriptor("net.minecraftforge.lex:vignette:0.2.0.16"),
                List.of(),
                List.of("--jar-in", "{MC_SLIM}", "--jar-out={MC_SRG}", "--mapping-format", "tsrg2",
                        "--mappings", "{MERGED_MAPPINGS}"),
                Map.of());
    }

    /// Creates a DOWNLOAD_MOJMAPS processor, optionally with the `--sanitize` flag.
    ///
    /// @param sanitize whether the processor sanitizes Mojang's mappings
    /// @return the processor
    private static Processor downloadMojmaps(boolean sanitize) {
        List<String> args = new ArrayList<>(List.of(
                "--task", "DOWNLOAD_MOJMAPS",
                "--version", "1.16.5",
                "--side", "{SIDE}",
                "--output", "{MOJMAPS}"));
        if (sanitize) {
            args.add("--sanitize");
        }
        return new Processor(
                List.of("client"),
                Artifact.fromDescriptor("net.minecraftforge:installertools:1.4.6"),
                List.of(),
                args,
                Map.of());
    }

    /// Creates a PROCESS_MINECRAFT_JAR processor that extracts libraries when the input is a
    /// bundler.
    ///
    /// @return the processor
    private static Processor processMinecraftJar() {
        return new Processor(
                List.of("client"),
                Artifact.fromDescriptor("net.neoforged.installertools:installertools:4.0.12:fatjar"),
                List.of(),
                List.of(
                        "--task", "PROCESS_MINECRAFT_JAR",
                        "--input", "{MINECRAFT_JAR}",
                        "--output", "{PATCHED}",
                        "--extract-libraries-to", "{ROOT}/libraries/"),
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

    /// Writes the client jar a sidecar records as the chain input.
    ///
    /// @param directory the test directory
    /// @return the client jar path
    private static Path minecraftJar(Path directory) throws IOException {
        return writeFile(directory.resolve("minecraft.jar"), "minecraft");
    }

    /// Returns the variables a sidecar records for a chain that only writes the patched jar.
    ///
    /// @param patched   the patched client jar
    /// @param clientJar the client jar the chain reads
    /// @return the install-profile variables
    private static Map<String, String> sidecarVars(Path patched, Path clientJar) {
        return Map.of(
                "PATCHED", patched.toString(),
                "MINECRAFT_JAR", clientJar.toString());
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
