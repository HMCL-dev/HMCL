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

import com.google.gson.JsonParseException;
import org.jackhuang.hmcl.download.forge.ForgeNewInstallProfile.Processor;
import org.jackhuang.hmcl.game.Artifact;
import org.jackhuang.hmcl.util.DigestUtils;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Decides whether a Forge-family processor chain can be skipped because its artifacts are current.
///
/// A chain is only skipped when every artifact it writes is known and still current: the files
/// named by the processors themselves, plus the patched jar recorded in the install profile data.
/// Profiles that declare a SHA-1 for each of those artifacts are verified by those declarations.
/// NeoForge declares none, and some older Forge profiles declare only a few, so the artifacts of
/// the last successful run are additionally recorded in a sidecar file next to the patched jar,
/// together with the installer and client jar they were produced from.
@NotNullByDefault
public final class ProcessorReuse {

    /// Processor jars whose only side effect is writing the artifact named by their arguments.
    private static final @Unmodifiable Set<String> ARTIFACT_ONLY_JARS = Set.of(
            "net.minecraftforge:jarsplitter",
            "net.minecraftforge:ForgeAutoRenamingTool",
            "net.minecraftforge:binarypatcher",
            "net.md-5:SpecialSource",
            "net.minecraftforge.lex:vignette",
            "net.neoforged.installertools:jarsplitter",
            "net.neoforged.installertools:binarypatcher",
            "net.neoforged:AutoRenamingTool");

    /// Installer-tools tasks that only produce files in the library directory, observed in
    /// client-side install profiles. `EXTRACT_FILES` is absent on purpose: it writes launch scripts
    /// into the instance directory, so skipping it would leave an instance without them.
    private static final @Unmodifiable Set<String> ARTIFACT_ONLY_INSTALLER_TOOLS_TASKS = Set.of(
            "MCP_DATA", "DOWNLOAD_MOJMAPS", "MERGE_MAPPING", "CREATE_PARENTS", "PROCESS_MINECRAFT_JAR");

    /// Format version of the sidecar file. Bump it whenever the recorded data or its meaning changes.
    private static final int SIDECAR_VERSION = 2;

    /// Suffix appended to the patched jar path to locate the sidecar file.
    private static final String SIDECAR_SUFFIX = ".hmcl-cache";

    /// Utility class: not instantiable.
    private ProcessorReuse() {
    }

    /// Returns whether the chain can be skipped because every artifact it declares is present with
    /// the checksum the install profile declares for it.
    ///
    /// Only a chain made exclusively of artifact-only processors qualifies, and the chain is only
    /// skipped when at least one declared artifact was actually verified.
    ///
    /// @param processors the client-side processors
    /// @param vars       the resolved install-profile variables
    /// @return `true` when running the processors would not change any file they are responsible for
    /// @throws IOException if an artifact cannot be read
    public static boolean canReuseDeclaredArtifacts(List<Processor> processors, Map<String, String> vars) throws IOException {
        if (!isArtifactOnlyChain(processors)) {
            return false;
        }
        @Nullable Set<String> artifacts = outputArtifacts(processors, vars);
        if (artifacts == null) {
            return false;
        }

        int verified = 0;
        for (String artifact : artifacts) {
            @Nullable String path = vars.get(artifact);
            if (path == null) {
                return false;
            }
            @Nullable String expected = vars.get(artifact + "_SHA");
            if (expected == null) {
                // Only the patched jar may lack a declared checksum in a profile that declares some:
                // NeoForge omits every checksum. It must still exist, or skipping would leave the
                // instance without its client jar.
                if ("PATCHED".equals(artifact) && Files.isRegularFile(Paths.get(path))) {
                    continue;
                }
                return false;
            }
            if (!matches(Paths.get(path), expected)) {
                return false;
            }
            verified++;
        }
        return verified > 0;
    }

    /// Returns whether the chain can be skipped because the sidecar records the artifacts of a
    /// successful run of the same installer and client jar, and those artifacts are unchanged.
    ///
    /// @param processors the client-side processors
    /// @param vars       the resolved install-profile variables
    /// @param installer  the installer JAR the artifacts were produced from
    /// @return `true` when running the processors would not change any file they are responsible for
    public static boolean canReuseRecordedArtifacts(List<Processor> processors, Map<String, String> vars, Path installer) {
        if (!isArtifactOnlyChain(processors)) {
            return false;
        }
        @Nullable Set<String> artifacts = outputArtifacts(processors, vars);
        @Nullable String patched = vars.get("PATCHED");
        if (artifacts == null || artifacts.isEmpty() || patched == null) {
            return false;
        }

        try {
            @Nullable Sidecar sidecar = readSidecar(sidecarPath(patched));
            if (sidecar == null
                    || !DigestUtils.digestToString("SHA-1", installer).equalsIgnoreCase(sidecar.installer)
                    || !matchesInput(vars.get("MINECRAFT_JAR"), sidecar.minecraftJar)) {
                return false;
            }
            for (String artifact : artifacts) {
                @Nullable String expected = sidecar.artifacts == null ? null : sidecar.artifacts.get(artifact);
                @Nullable String path = vars.get(artifact);
                if (expected == null || path == null || !matches(Paths.get(path), expected)) {
                    return false;
                }
            }
            return true;
        } catch (IOException | JsonParseException e) {
            LOG.info("Ignoring unreadable processor cache for " + patched, e);
            return false;
        }
    }

    /// Records the artifacts of a successful chain run in the sidecar file, together with the
    /// installer and client jar they were produced from.
    ///
    /// Failures are logged and ignored: the installation itself has already succeeded.
    ///
    /// @param processors the client-side processors that were run
    /// @param vars       the resolved install-profile variables
    /// @param installer  the installer JAR the artifacts were produced from
    public static void writeSidecar(List<Processor> processors, Map<String, String> vars, Path installer) {
        try {
            @Nullable Set<String> artifacts = outputArtifacts(processors, vars);
            @Nullable String patched = vars.get("PATCHED");
            @Nullable String minecraftJar = vars.get("MINECRAFT_JAR");
            if (artifacts == null || artifacts.isEmpty() || patched == null
                    || minecraftJar == null || !Files.isRegularFile(Paths.get(minecraftJar))) {
                // Without the input the sidecar is keyed by, the next install cannot validate it.
                return;
            }

            Sidecar sidecar = new Sidecar();
            sidecar.version = SIDECAR_VERSION;
            sidecar.installer = DigestUtils.digestToString("SHA-1", installer);
            sidecar.minecraftJar = DigestUtils.digestToString("SHA-1", Paths.get(minecraftJar));
            sidecar.artifacts = new LinkedHashMap<>();
            for (String artifact : artifacts) {
                @Nullable String path = vars.get(artifact);
                if (path == null) {
                    return;
                }
                Path file = Paths.get(path);
                if (!Files.isRegularFile(file)) {
                    // Never record a partial result: the next install must run the chain again.
                    return;
                }
                sidecar.artifacts.put(artifact, DigestUtils.digestToString("SHA-1", file));
            }

            Path file = sidecarPath(patched);
            // A relative `PATCHED` literal yields no parent; writing next to it is still correct.
            Path directory = file.getParent() != null ? file.getParent() : Paths.get(".");
            Files.createDirectories(directory);
            Path tmp = Files.createTempFile(directory, "hmcl-cache", ".tmp");
            try {
                Files.writeString(tmp, JsonUtils.GSON.toJson(sidecar), StandardCharsets.UTF_8);
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(tmp);
            }
        } catch (IOException e) {
            LOG.warning("Failed to write processor cache for " + installer, e);
        }
    }

    /// Returns whether every processor in the chain only writes artifacts into the library directory.
    ///
    /// @param processors the client-side processors
    /// @return `true` when skipping the chain cannot leave instance files behind
    private static boolean isArtifactOnlyChain(List<Processor> processors) {
        if (processors.isEmpty()) {
            return false;
        }
        for (Processor processor : processors) {
            if (!isArtifactOnlyProcessor(processor)) {
                return false;
            }
        }
        return true;
    }

    /// Returns whether a processor only writes artifacts into the library directory.
    ///
    /// @param processor the processor to classify
    /// @return `true` when skipping the processor cannot leave instance files behind
    private static boolean isArtifactOnlyProcessor(Processor processor) {
        Artifact jar = processor.getJar();
        String name = jar.getGroup() + ":" + jar.getName();
        if (ARTIFACT_ONLY_JARS.contains(name)) {
            return true;
        }
        if (!"net.minecraftforge:installertools".equals(name)
                && !"net.neoforged.installertools:installertools".equals(name)) {
            return false;
        }
        @Nullable String task = findTask(processor.getArgs());
        return task != null && ARTIFACT_ONLY_INSTALLER_TOOLS_TASKS.contains(task.toUpperCase(Locale.ROOT));
    }

    /// Returns the value of the `--task` option, or `null` when it is absent or has no value.
    ///
    /// @param args the processor arguments
    /// @return the task name
    private static @Nullable String findTask(List<String> args) {
        for (int i = 0; i < args.size(); i++) {
            String arg = args.get(i);
            if ("--task".equals(arg)) {
                return i + 1 < args.size() ? args.get(i + 1) : null;
            }
            if (arg.startsWith("--task=")) {
                return arg.substring("--task=".length());
            }
        }
        return null;
    }

    /// Returns the artifact variables the chain produces, or `null` when a processor writes a file
    /// this class cannot name.
    ///
    /// A processor contributes the keys of its declared outputs, the value of an `--output`
    /// argument, and the path arguments of the tools that name their output differently:
    /// jarsplitter's `--slim` and `--extra`, SpecialSource's `--out-jar`, and vignette's
    /// `--jar-out`. NeoForge declares neither outputs nor checksums, so its artifacts can only
    /// be found this way; Forge 1.13–1.16.5 and 1.17.1 declare none for their remapping step.
    ///
    /// @param processors the client-side processors
    /// @param vars       the resolved install-profile variables
    /// @return the artifact variable names
    private static @Nullable Set<String> outputArtifacts(List<Processor> processors, Map<String, String> vars) {
        Set<String> artifacts = new LinkedHashSet<>();
        for (Processor processor : processors) {
            Set<String> written = new LinkedHashSet<>();
            for (String output : processor.getOutputs().keySet()) {
                written.add(output);
            }
            written.addAll(writeTargets(processor));
            for (String expression : written) {
                if (!StringUtils.isSurrounded(expression, "{", "}")) {
                    return null;
                }
                artifacts.add(StringUtils.removeSurrounding(expression, "{", "}"));
            }
        }
        // The patched client jar is declared in `data` even for profiles whose binarypatcher
        // processor declares no outputs.
        if (vars.containsKey("PATCHED")) {
            artifacts.add("PATCHED");
        }
        return artifacts;
    }

    /// Returns the path arguments a processor writes, judging by the option names of the tools seen
    /// in Forge and NeoForge install profiles.
    ///
    /// Both `--option value` and `--option=value` forms are recognized.
    ///
    /// @param processor the processor to inspect
    /// @return the path expressions the processor writes
    private static Set<String> writeTargets(Processor processor) {
        List<String> args = processor.getArgs();
        Set<String> targets = new LinkedHashSet<>();
        boolean jarsplitter = processor.getJar().getName().contains("jarsplitter");
        boolean specialSource = "SpecialSource".equals(processor.getJar().getName());
        boolean vignette = "vignette".equals(processor.getJar().getName());
        for (int i = 0; i < args.size(); i++) {
            String arg = args.get(i);
            int separator = arg.indexOf('=');
            String name = separator >= 0 ? arg.substring(0, separator) : arg;
            if (!name.startsWith("--")) {
                continue;
            }

            String value;
            if (separator >= 0) {
                value = arg.substring(separator + 1);
            } else if (i + 1 < args.size()) {
                value = args.get(i + 1);
            } else {
                continue;
            }

            switch (name.substring("--".length())) {
                case "output" -> targets.add(value);
                case "slim", "extra" -> {
                    // Only jarsplitter writes these; other tools may reuse the names for inputs.
                    if (jarsplitter) {
                        targets.add(value);
                    }
                }
                case "out-jar" -> {
                    // SpecialSource writes its remapped jar here; `--in-jar` is the input.
                    if (specialSource) {
                        targets.add(value);
                    }
                }
                case "jar-out" -> {
                    // vignette writes its remapped jar here; `--jar-in` is the input.
                    if (vignette) {
                        targets.add(value);
                    }
                }
                default -> {
                }
            }
        }
        return targets;
    }

    /// Returns whether `file` exists and has the expected SHA-1.
    ///
    /// @param file     the artifact file
    /// @param expected the expected SHA-1
    /// @return `true` when the file is present and unchanged
    /// @throws IOException if the file cannot be read
    private static boolean matches(Path file, String expected) throws IOException {
        if (!Files.isRegularFile(file)) {
            return false;
        }
        if (!DigestUtils.digestToString("SHA-1", file).equalsIgnoreCase(expected)) {
            LOG.info("Existing artifact " + file + " does not match the expected checksum");
            return false;
        }
        return true;
    }

    /// Returns whether the client jar the current install reads is the one the sidecar was
    /// produced from.
    ///
    /// The installer hash alone cannot tell apart installs whose client jar differs, such as a
    /// modpack manifest that overrides the client download with another file.
    ///
    /// @param path     the resolved `MINECRAFT_JAR` path, or `null` when the variables have none
    /// @param expected the recorded SHA-1, or `null` when the sidecar did not record one
    /// @return `true` when the file exists and matches
    /// @throws IOException if the file cannot be read
    private static boolean matchesInput(@Nullable String path, @Nullable String expected) throws IOException {
        return path != null && expected != null && matches(Paths.get(path), expected);
    }

    /// Returns the sidecar path for the patched client jar.
    ///
    /// @param patched the patched client jar path
    /// @return the sidecar path
    private static Path sidecarPath(String patched) {
        return Paths.get(patched + SIDECAR_SUFFIX);
    }

    /// Reads the sidecar file.
    ///
    /// @param file the sidecar path
    /// @return the recorded state, or `null` when the file is missing or from another format version
    /// @throws IOException if the file cannot be read
    private static @Nullable Sidecar readSidecar(Path file) throws IOException {
        if (!Files.isRegularFile(file)) {
            return null;
        }
        @Nullable Sidecar sidecar = JsonUtils.fromJsonFile(file, Sidecar.class);
        return sidecar != null && sidecar.version == SIDECAR_VERSION ? sidecar : null;
    }

    /// Serialized content of a processor sidecar file.
    private static final class Sidecar {
        /// Sidecar format version.
        private int version;

        /// SHA-1 of the installer JAR that produced the recorded artifacts.
        private @Nullable String installer;

        /// SHA-1 of the client jar the recorded artifacts were produced from.
        private @Nullable String minecraftJar;

        /// Artifact variable name to SHA-1 of the file the last successful run produced.
        private @Nullable Map<String, String> artifacts;
    }
}
