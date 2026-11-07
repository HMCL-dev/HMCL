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
import org.jackhuang.hmcl.task.Task;
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
import java.util.zip.ZipFile;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;

/// Decides whether a Forge-family processor chain can be skipped because its artifacts are current.
///
/// A chain is only skipped when every artifact it writes is known and still current: the files
/// named by the processors themselves, plus the patched jar recorded in the install profile data.
/// Profiles that declare a SHA-1 for each of those artifacts are verified by those declarations.
/// NeoForge declares none, and some older Forge profiles declare only a few, so the artifacts of
/// the last successful run are additionally recorded in a sidecar file next to the patched jar,
/// together with the installer and client jar they were produced from.
///
/// A chain that extracts libraries from a bundler client jar is never skipped: the extracted tree
/// cannot be named or recorded, so its presence cannot be proven.
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

    /// Returns whether the chain can be skipped because every artifact it declares is present and
    /// still current.
    ///
    /// The checksums from the install profile verify the artifacts, except for the two files HMCL
    /// cannot reproduce faithfully: the patched jar of profiles that declare no checksum for it,
    /// and the sanitized mappings file, which HMCL replaces with Mojang's raw download. Those two
    /// only need to exist.
    ///
    /// Only a chain made exclusively of artifact-only processors qualifies, and the chain is only
    /// skipped when at least one other artifact was actually verified.
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
        if (artifacts == null || writesExtractedLibraries(processors, vars)) {
            return false;
        }

        boolean sanitizedMojmaps = sanitizesMojmaps(processors);
        int verified = 0;
        for (String artifact : artifacts) {
            @Nullable String path = vars.get(artifact);
            if (path == null) {
                return false;
            }
            Path file = Paths.get(path);
            @Nullable String expected = vars.get(artifact + "_SHA");
            if (expected == null || sanitizedMojmaps && "MOJMAPS".equals(artifact)) {
                // Only the patched jar may lack a declared checksum in a profile that declares some,
                // and the sanitized mappings can never match the raw file HMCL downloads. Both must
                // still exist, or skipping would leave the chain without an input or the instance
                // without its client jar.
                if (("PATCHED".equals(artifact) || "MOJMAPS".equals(artifact))
                        && Files.isRegularFile(file)) {
                    continue;
                }
                return false;
            }
            if (!matches(file, expected)) {
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
        if (artifacts == null || artifacts.isEmpty() || patched == null
                || writesExtractedLibraries(processors, vars)) {
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
        writeSidecar(processors, vars, installer, Map.of());
    }

    /// Records the artifacts of a successful chain run, reusing hashes the processor tasks have
    /// already verified.
    ///
    /// Failures are logged and ignored: the installation itself has already succeeded.
    ///
    /// @param processors  the client-side processors that were run
    /// @param vars        the resolved install-profile variables
    /// @param installer   the installer JAR the artifacts were produced from
    /// @param knownHashes SHA-1 values the processor tasks computed, keyed by artifact path
    public static void writeSidecar(List<Processor> processors, Map<String, String> vars, Path installer,
                                    Map<Path, String> knownHashes) {
        try {
            @Nullable Set<String> artifacts = outputArtifacts(processors, vars);
            @Nullable String patched = vars.get("PATCHED");
            if (artifacts == null || artifacts.isEmpty()) {
                LOG.info("Not caching the processor chain from " + installer
                        + ": a processor writes a path the cache cannot verify");
                return;
            }
            if (writesExtractedLibraries(processors, vars)) {
                LOG.info("Not caching the processor chain from " + installer
                        + ": the client jar is a bundler");
                return;
            }
            @Nullable String minecraftJar = vars.get("MINECRAFT_JAR");
            @Nullable Path minecraftJarFile = minecraftJar != null ? Paths.get(minecraftJar) : null;
            if (patched == null || minecraftJarFile == null || !Files.isRegularFile(minecraftJarFile)) {
                // Without the input the sidecar is keyed by, the next install cannot validate it.
                LOG.info("Not caching the processor chain from " + installer
                        + ": the client jar is unavailable");
                return;
            }

            Sidecar sidecar = new Sidecar();
            sidecar.version = SIDECAR_VERSION;
            sidecar.installer = DigestUtils.digestToString("SHA-1", installer);
            sidecar.minecraftJar = DigestUtils.digestToString("SHA-1", minecraftJarFile);
            sidecar.artifacts = new LinkedHashMap<>();
            for (String artifact : artifacts) {
                @Nullable String path = vars.get(artifact);
                if (path == null) {
                    LOG.info("Not caching the processor chain for " + patched
                            + ": " + artifact + " has no path");
                    return;
                }
                Path file = Paths.get(path);
                if (!Files.isRegularFile(file)) {
                    // Never record a partial result: the next install must run the chain again.
                    LOG.info("Not caching the processor chain for " + patched + ": " + file + " is missing");
                    return;
                }
                @Nullable String known = knownHashes.get(file);
                sidecar.artifacts.put(artifact, known != null ? known : DigestUtils.digestToString("SHA-1", file));
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

    /// Returns the task that runs the processor tasks, or an already-completed task when the chain
    /// can be skipped.
    ///
    /// The sidecar is written only after a real run; a skipped chain leaves the recorded cache as
    /// it is.
    ///
    /// @param processors     the client-side processors
    /// @param vars           the resolved install-profile variables
    /// @param installer      the installer JAR the artifacts are produced from
    /// @param canSkip        whether the reuse checks proved every artifact is current
    /// @param skipMessage    the message logged when the chain is skipped
    /// @param processorTasks the tasks that run the processors, in order
    /// @param knownHashes    SHA-1 values the processor tasks already verified, keyed by path
    /// @return the task that completes after the processors, or immediately when skipped
    public static Task<?> createProcessorsTask(
            List<Processor> processors,
            Map<String, String> vars,
            Path installer,
            boolean canSkip,
            String skipMessage,
            List<? extends Task<?>> processorTasks,
            Map<Path, String> knownHashes) {
        if (canSkip) {
            LOG.info(skipMessage);
            return Task.completed(null);
        }
        return Task.runSequentially(processorTasks.toArray(Task<?>[]::new))
                .thenRunAsync(() -> writeSidecar(processors, vars, installer, knownHashes));
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

    /// Returns the value of the `--task` option, or `null` when it is absent.
    ///
    /// @param args the processor arguments
    /// @return the task name, or the empty string when the option has no value
    private static @Nullable String findTask(List<String> args) {
        return parseOptions(args).get("task");
    }

    /// Returns the `--option value` and `--option=value` pairs of a processor.
    ///
    /// Options without a value are mapped to the empty string, matching how the install tasks
    /// parse their arguments.
    ///
    /// @param args the processor arguments
    /// @return the option values by name
    private static Map<String, String> parseOptions(List<String> args) {
        Map<String, String> options = new LinkedHashMap<>();
        @Nullable String optionName = null;
        for (String arg : args) {
            if (arg.startsWith("--")) {
                if (optionName != null) {
                    options.put(optionName, "");
                }
                int separator = arg.indexOf('=');
                if (separator >= 0) {
                    options.put(arg.substring("--".length(), separator), arg.substring(separator + 1));
                    optionName = null;
                } else {
                    optionName = arg.substring("--".length());
                }
            } else if (optionName != null) {
                options.put(optionName, arg);
                optionName = null;
            }
        }
        if (optionName != null) {
            options.put(optionName, "");
        }
        return options;
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
    /// @param processor the processor to inspect
    /// @return the path expressions the processor writes
    private static Set<String> writeTargets(Processor processor) {
        Map<String, String> options = parseOptions(processor.getArgs());
        Set<String> targets = new LinkedHashSet<>();
        addWriteTarget(targets, options.get("output"));
        if (processor.getJar().getName().contains("jarsplitter")) {
            // Only jarsplitter writes these; other tools may reuse the names for inputs.
            addWriteTarget(targets, options.get("slim"));
            addWriteTarget(targets, options.get("extra"));
        }
        if ("SpecialSource".equals(processor.getJar().getName())) {
            // SpecialSource writes its remapped jar here; `--in-jar` is the input.
            addWriteTarget(targets, options.get("out-jar"));
        }
        if ("vignette".equals(processor.getJar().getName())) {
            // vignette writes its remapped jar here; `--jar-in` is the input.
            addWriteTarget(targets, options.get("jar-out"));
        }
        return targets;
    }

    /// Adds a write target when the option has a value.
    ///
    /// @param targets the collected path expressions
    /// @param value   the option value, or `null` when it is absent
    private static void addWriteTarget(Set<String> targets, @Nullable String value) {
        if (value != null && !value.isEmpty()) {
            targets.add(value);
        }
    }

    /// Returns whether the chain downloads Mojang mappings through the sanitizing installertools
    /// task.
    ///
    /// HMCL replaces that processor with a plain download of Mojang's raw file, so the checksum the
    /// profile declares for the sanitized file cannot verify it.
    ///
    /// @param processors the client-side processors
    /// @return `true` when the mappings HMCL writes are not the file the profile describes
    private static boolean sanitizesMojmaps(List<Processor> processors) {
        for (Processor processor : processors) {
            Map<String, String> options = parseOptions(processor.getArgs());
            if ("DOWNLOAD_MOJMAPS".equalsIgnoreCase(options.get("task")) && options.containsKey("sanitize")) {
                return true;
            }
        }
        return false;
    }

    /// Returns whether the chain extracts files from the client jar into the libraries directory.
    ///
    /// `PROCESS_MINECRAFT_JAR` performs that extraction when it is given `--extract-libraries-to`
    /// and the input is a bundler. The extracted tree cannot be named or recorded, so such an input
    /// must never be skipped; a plain client jar makes the option a no-op.
    ///
    /// @param processors the client-side processors
    /// @param vars       the resolved install-profile variables
    /// @return `true` when a skipped chain could leave extracted files stale or missing
    private static boolean writesExtractedLibraries(List<Processor> processors, Map<String, String> vars) {
        for (Processor processor : processors) {
            if (parseOptions(processor.getArgs()).containsKey("extract-libraries-to")) {
                @Nullable String minecraftJar = vars.get("MINECRAFT_JAR");
                return minecraftJar == null || isBundler(Paths.get(minecraftJar));
            }
        }
        return false;
    }

    /// Returns whether the client jar is a Mojang bundler.
    ///
    /// @param jar the client jar
    /// @return `true` when the jar stores a version file list, or cannot be inspected
    private static boolean isBundler(Path jar) {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            return zip.getEntry("META-INF/versions.list") != null;
        } catch (IOException e) {
            return true;
        }
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
