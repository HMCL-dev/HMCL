/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
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

import org.jackhuang.hmcl.download.ArtifactMalformedException;
import org.jackhuang.hmcl.download.DefaultDependencyManager;
import org.jackhuang.hmcl.download.forge.ForgeNewInstallProfile.Processor;
import org.jackhuang.hmcl.download.game.GameLibrariesTask;
import org.jackhuang.hmcl.download.game.GameInstanceJsonDownloadTask;
import org.jackhuang.hmcl.game.*;
import org.jackhuang.hmcl.task.FileDownloadTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.util.DigestUtils;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.ZlibUtils;
import org.jackhuang.hmcl.util.function.ExceptionalFunction;
import org.jackhuang.hmcl.util.gson.JsonUtils;
import org.jackhuang.hmcl.util.io.ChecksumMismatchException;
import org.jackhuang.hmcl.util.io.CompressingUtils;
import org.jackhuang.hmcl.util.io.FileUtils;
import org.jackhuang.hmcl.util.platform.CommandBuilder;
import org.jackhuang.hmcl.java.JavaRuntime;
import org.jackhuang.hmcl.util.platform.SystemUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.zip.ZipException;

import static org.jackhuang.hmcl.util.logging.Logger.LOG;
import static org.jackhuang.hmcl.util.gson.JsonUtils.fromNonNullJson;

public class ForgeNewInstallTask extends Task<GameInstancePatch> {

    private class ProcessorTask extends Task<Void> {

        private Processor processor;
        private Map<String, String> vars;

        public ProcessorTask(@NotNull Processor processor, @NotNull Map<String, String> vars) {
            this.processor = processor;
            this.vars = vars;
            setSignificance(TaskSignificance.MODERATE);
        }

        @Override
        public void execute() throws Exception {
            Map<String, String> outputs = new HashMap<>();
            for (Map.Entry<String, String> entry : processor.getOutputs().entrySet()) {
                String key = parseLiteral(entry.getKey(), vars);
                String value = parseLiteral(entry.getValue(), vars);

                if (key == null || value == null) {
                    throw new ArtifactMalformedException("Invalid forge installation configuration");
                }

                outputs.put(key, value);
            }

            // Every file this step is supposed to produce: the ones the profile declares, plus the
            // ones an earlier build of the same loader was seen to write. Upstream profiles declare
            // outputs for only a minority of processors, so without the second source the remaining
            // steps would keep rebuilding artifacts that already sit in the shared libraries.
            LinkedHashMap<Path, String> targets = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : outputs.entrySet())
                targets.put(Paths.get(entry.getKey()), entry.getValue());
            collectRecordedTargets(targets);

            boolean miss = false;
            for (Map.Entry<Path, String> entry : targets.entrySet()) {
                Path artifact = entry.getKey();
                if (Files.exists(artifact)) {
                    String code;
                    try (InputStream stream = Files.newInputStream(artifact)) {
                        code = (DigestUtils.digestToString("SHA-1", stream));
                    }

                    if (!Objects.equals(code, entry.getValue())) {
                        Files.delete(artifact);
                        LOG.info("Found existing file is not valid: " + artifact);

                        miss = true;
                    }
                } else {
                    miss = true;
                }
            }

            if (!targets.isEmpty() && !miss) {
                // Fold the checksums just verified into the record this installation writes, or the
                // next installation would have to rebuild this step all over again.
                for (Map.Entry<Path, String> entry : targets.entrySet()) {
                    @Nullable String targetKey = LoaderBuildManifest.keyOf(gameRepository, entry.getKey());
                    if (targetKey != null)
                        recordedOutputs.put(targetKey, entry.getValue());
                }
                return;
            }

            Path jar = gameRepository.getLayout().getArtifactFile(processor.getJar());
            if (!Files.isRegularFile(jar))
                throw new FileNotFoundException("Game processor file not found, should be downloaded in preprocess");

            String mainClass;
            try (JarFile jarFile = new JarFile(jar.toFile())) {
                mainClass = jarFile.getManifest().getMainAttributes().getValue(Attributes.Name.MAIN_CLASS);
            }

            if (StringUtils.isBlank(mainClass))
                throw new Exception("Game processor jar does not have main class " + jar);

            List<String> command = new ArrayList<>();
            command.add(JavaRuntime.getDefault().getBinary().toString());
            command.add("-cp");

            List<String> classpath = new ArrayList<>(processor.getClasspath().size() + 1);
            for (Artifact artifact : processor.getClasspath()) {
                Path file = gameRepository.getLayout().getArtifactFile(artifact);
                if (!Files.isRegularFile(file))
                    throw new Exception("Game processor dependency missing");
                classpath.add(file.toString());
            }
            classpath.add(jar.toString());
            command.add(String.join(File.pathSeparator, classpath));

            command.add(mainClass);

            List<String> args = new ArrayList<>(processor.getArgs().size());
            for (String arg : processor.getArgs()) {
                String parsed = parseLiteral(arg, vars);
                if (parsed == null)
                    throw new ArtifactMalformedException("Invalid forge installation configuration");
                args.add(parsed);
            }

            command.addAll(args);

            LOG.info("Executing external processor " + processor.getJar().toString() + ", command line: " + new CommandBuilder().addAll(command).toString());
            int exitCode = SystemUtils.callExternalProcess(command);
            if (exitCode != 0)
                throw new IOException("Game processor exited abnormally with code " + exitCode);

            for (Map.Entry<String, String> entry : outputs.entrySet()) {
                Path artifact = Paths.get(entry.getKey());
                if (!Files.isRegularFile(artifact))
                    throw new FileNotFoundException("File missing: " + artifact);

                String code;
                try (InputStream stream = Files.newInputStream(artifact)) {
                    code = DigestUtils.digestToString("SHA-1", stream);
                }

                if (!Objects.equals(code, entry.getValue())) {
                    if (!ZlibUtils.IS_ZLIB_COMPATIBLE && FileUtils.getExtension(artifact).equals("jar")) {
                        // Forge/NeoForge generates JARs dynamically during installation.
                        // When native compression libraries such as zlib-ng are in use,
                        // the resulting JAR may be compressed differently, causing its
                        // SHA-1 hash to differ from the expected value recorded in the
                        // install profile. In this case, fall back to verifying that the
                        // file is at least a structurally valid ZIP/JAR archive.
                        try {
                            FileDownloadTask.ZIP_INTEGRITY_CHECK_HANDLER.checkIntegrity(artifact, artifact);
                            LOG.info("Ignoring SHA-1 mismatch for " + artifact + " due to non-standard zlib compression output");
                            continue;
                        } catch (Exception ignored) {
                        }
                    }


                    Files.delete(artifact);
                    throw new ChecksumMismatchException("SHA-1", entry.getValue(), code);
                }
            }

            recordProducedFiles(args);
        }

        /// Adds the artifacts an earlier build of the same loader recorded for this step.
        ///
        /// @param targets collects path to expected SHA-1
        private void collectRecordedTargets(Map<Path, String> targets) {
            if (buildManifest == null)
                return;

            for (String operand : outputOperands(processor.getArgs())) {
                @Nullable String path = parseLiteral(operand, vars);
                if (path == null)
                    continue;

                Path file = toAbsolutePath(path);
                if (file == null)
                    continue;

                @Nullable String sha1 = buildManifest.sha1Of(gameRepository, file);
                if (sha1 != null)
                    targets.put(file, sha1);
            }
        }

        /// Records the checksum of every file this step wrote, so a later installation can tell
        /// whether the step still has to run.
        ///
        /// @param args the arguments actually passed to the processor
        private void recordProducedFiles(List<String> args) {
            for (String path : outputOperands(args)) {
                Path file = toAbsolutePath(path);
                if (file == null || !Files.isRegularFile(file))
                    continue;

                @Nullable String key = LoaderBuildManifest.keyOf(gameRepository, file);
                if (key == null)
                    continue;

                try {
                    recordedOutputs.put(key, DigestUtils.digestToString("SHA-1", file));
                } catch (Exception e) {
                    LOG.warning("Failed to record loader build output " + file, e);
                }
            }
        }

        /// Returns the operands of the output flags in `args`, left unparsed.
        ///
        /// @param args processor arguments, either as written in the profile or already resolved
        /// @return the operands naming files the processor writes
        private static List<String> outputOperands(List<String> args) {
            List<String> operands = new ArrayList<>();
            for (int i = 0; i + 1 < args.size(); i++) {
                if (OUTPUT_FLAGS.contains(args.get(i)))
                    operands.add(args.get(i + 1));
            }
            return operands;
        }

        /// Resolves a path a processor named.
        ///
        /// Processors inherit the launcher's working directory, so a relative argument names a file
        /// below it rather than below the repository.
        ///
        /// @param path the path as named in the processor arguments
        /// @return the resolved path, or `null` when it cannot be parsed
        private static @Nullable Path toAbsolutePath(String path) {
            try {
                return Path.of(path).toAbsolutePath().normalize();
            } catch (InvalidPathException e) {
                return null;
            }
        }
    }

    private final DefaultDependencyManager dependencyManager;
    private final DefaultGameRepository gameRepository;
    private final GameInstanceManifest manifest;
    /// Source vanilla client JAR copied before processors are invoked.
    private final Path minecraftJar;
    /// Forge installer JAR, or `null` when [#buildManifest] proves the build already ran.
    private final @Nullable Path installer;
    /// Record of an earlier build of this loader, reused instead of rebuilding it.
    private @Nullable LoaderBuildManifest buildManifest;
    private final List<Task<?>> dependents = new ArrayList<>(1);
    private final List<Task<?>> dependencies = new ArrayList<>(1);

    private ForgeNewInstallProfile profile;
    private List<Processor> processors;
    private GameInstanceManifest forgeVersion;
    private final String selfVersion;
    /// Key the build record is stored under.
    private final String buildKey;
    /// Raw installer profile, kept because a successful build is recorded from it.
    private String installProfileText;
    /// Raw loader manifest, kept because a successful build is recorded from it.
    private String versionJsonText;
    /// SHA-1 of every file this build produced, keyed as in [LoaderBuildManifest].
    private final Map<String, String> recordedOutputs = new LinkedHashMap<>();

    private Path tempDir;
    private final AtomicInteger processorDoneCount = new AtomicInteger(0);

    /// Flags whose operand is a file a processor writes.
    ///
    /// The installers spell every produced file with one of these and every consumed file with
    /// another flag, so following the flag is enough to tell an output from an input. `--srg` is
    /// deliberately absent: `jarsplitter` reads it while `ForgeAutoRenamingTool` writes
    /// `--output` instead.
    private static final Set<String> OUTPUT_FLAGS = Set.of("--output", "--slim", "--extra", "--to");

    /// Creates a Forge processor installation task.
    ///
    /// @param dependencyManager repository-scoped download services
    /// @param manifest          working manifest receiving the Forge patch
    /// @param minecraftJar      source vanilla client JAR copied for processor use
    /// @param selfVersion       Forge version recorded in the returned patch
    /// @param installer         Forge installer JAR
    public ForgeNewInstallTask(
            DefaultDependencyManager dependencyManager,
            GameInstanceManifest manifest,
            Path minecraftJar,
            String selfVersion,
            Path installer) {
        this(dependencyManager, manifest, minecraftJar, selfVersion, installer, null, null);
    }

    /// Creates a loader processor installation task.
    ///
    /// @param dependencyManager repository-scoped download services
    /// @param manifest          working manifest receiving the loader patch
    /// @param minecraftJar      source vanilla client JAR copied for processor use
    /// @param selfVersion       loader version recorded in the returned patch
    /// @param installer         loader installer JAR, or `null` when `buildManifest` is supplied
    /// @param buildManifest     record of an earlier build of this loader, or `null` to build;
    ///                          a record that fails verification is discarded and the build runs
    /// @param buildKey          the key `buildManifest` is stored under, or `null` to derive one
    ///                          from a Forge build; NeoForge has to name its own because the
    ///                          version it reports comes from the installer profile, which cannot
    ///                          be read before deciding whether the installer must be fetched
    public ForgeNewInstallTask(
            DefaultDependencyManager dependencyManager,
            GameInstanceManifest manifest,
            Path minecraftJar,
            String selfVersion,
            @Nullable Path installer,
            @Nullable LoaderBuildManifest buildManifest,
            @Nullable String buildKey) {
        this.dependencyManager = dependencyManager;
        this.gameRepository = dependencyManager.getGameRepository();
        this.manifest = manifest;
        this.minecraftJar = minecraftJar;
        this.installer = installer;
        this.selfVersion = selfVersion;
        this.buildManifest = buildManifest;
        this.buildKey = buildKey != null
                ? buildKey
                : LoaderBuildManifest.buildKey(GameComponentType.FORGE.getPatchId(), selfVersion, "client");

        setSignificance(TaskSignificance.MAJOR);
    }

    private static String replaceTokens(Map<String, String> tokens, String value) {
        StringBuilder buf = new StringBuilder();
        for (int x = 0; x < value.length(); x++) {
            char c = value.charAt(x);
            if (c == '\\') {
                if (x == value.length() - 1)
                    throw new IllegalArgumentException("Illegal pattern (Bad escape): " + value);
                buf.append(value.charAt(++x));
            } else if (c == '{' || c == '\'') {
                StringBuilder key = new StringBuilder();
                for (int y = x + 1; y <= value.length(); y++) {
                    if (y == value.length())
                        throw new IllegalArgumentException("Illegal pattern (Unclosed " + c + "): " + value);
                    char d = value.charAt(y);
                    if (d == '\\') {
                        if (y == value.length() - 1)
                            throw new IllegalArgumentException("Illegal pattern (Bad escape): " + value);
                        key.append(value.charAt(++y));
                    } else {
                        if (c == '{' && d == '}') {
                            x = y;
                            break;
                        }
                        if (c == '\'' && d == '\'') {
                            x = y;
                            break;
                        }
                        key.append(d);
                    }
                }
                if (c == '\'') {
                    buf.append(key);
                } else {
                    if (!tokens.containsKey(key.toString()))
                        throw new IllegalArgumentException("Illegal pattern: " + value + " Missing Key: " + key);
                    buf.append(tokens.get(key.toString()));
                }
            } else {
                buf.append(c);
            }
        }
        return buf.toString();
    }

    private <E extends Exception> String parseLiteral(String literal, Map<String, String> var, ExceptionalFunction<String, String, E> plainConverter) throws E {
        if (StringUtils.isSurrounded(literal, "{", "}"))
            return var.get(StringUtils.removeSurrounding(literal, "{", "}"));
        else if (StringUtils.isSurrounded(literal, "'", "'"))
            return StringUtils.removeSurrounding(literal, "'");
        else if (StringUtils.isSurrounded(literal, "[", "]"))
            return gameRepository.getLayout().getArtifactFile(Artifact.fromDescriptor(StringUtils.removeSurrounding(literal, "[", "]"))).toString();
        else
            return plainConverter.apply(replaceTokens(var, literal));
    }

    private String parseLiteral(String literal, Map<String, String> var) {
        return parseLiteral(literal, var, ExceptionalFunction.identity());
    }

    @Override
    public Collection<Task<?>> getDependents() {
        return dependents;
    }

    @Override
    public Collection<Task<?>> getDependencies() {
        return dependencies;
    }

    @Override
    public boolean doPreExecute() {
        return true;
    }

    @Override
    public void preExecute() throws Exception {
        // A record of this same build is still worth looking for when none was handed in, because
        // the key names the loader version, so any earlier installation of this loader left one.
        // A record that was handed in has already been verified against the same artifacts.
        if (buildManifest == null) {
            buildManifest = LoaderBuildManifest.load(gameRepository, buildKey);
            if (buildManifest != null && !buildManifest.verify(gameRepository))
                buildManifest = null;
        }

        if (buildManifest != null) {
            // Every artifact this build would produce is on disk and unchanged, so the processors
            // have nothing left to do. The profile and the loader manifest are read straight out of
            // the record, which is why the installer JAR does not have to exist on this path.
            installProfileText = buildManifest.getInstallProfile();
            versionJsonText = buildManifest.getVersionJson();
            profile = JsonUtils.fromNonNullJson(installProfileText, ForgeNewInstallProfile.class);
            processors = profile.getProcessors();
            forgeVersion = JsonUtils.fromNonNullJson(versionJsonText, GameInstanceManifest.class);
            return;
        }

        if (installer == null || !Files.isRegularFile(installer))
            throw new IOException("Forge installer JAR not found: " + installer);

        try (FileSystem fs = CompressingUtils.createReadOnlyZipFileSystem(installer)) {
            installProfileText = Files.readString(fs.getPath("install_profile.json"));
            profile = JsonUtils.fromNonNullJson(installProfileText, ForgeNewInstallProfile.class);
            processors = profile.getProcessors();
            versionJsonText = Files.readString(fs.getPath(profile.getJson()));
            forgeVersion = JsonUtils.fromNonNullJson(versionJsonText, GameInstanceManifest.class);

            for (Library library : profile.getLibraries()) {
                Path file = fs.getPath("maven").resolve(library.getPath());
                if (Files.exists(file)) {
                    Path dest = gameRepository.getLayout().getLibraryFile(manifest.id(), library);
                    FileUtils.copyFile(file, dest);
                }
            }

            if (profile.getPath().isPresent()) {
                Path mainJar = profile.getPath().get().getPath(fs.getPath("maven"));
                if (Files.exists(mainJar)) {
                    Path dest = gameRepository.getLayout().getArtifactFile(profile.getPath().get());
                    FileUtils.copyFile(mainJar, dest);
                }
            }
        } catch (ZipException ex) {
            throw new ArtifactMalformedException("Malformed forge installer file", ex);
        }

        dependents.add(new GameLibrariesTask(dependencyManager, manifest, true, profile.getLibraries()));
    }

    private Map<String, String> parseOptions(List<String> args, Map<String, String> vars) {
        Map<String, String> options = new LinkedHashMap<>();
        String optionName = null;
        for (String arg : args) {
            if (arg.startsWith("--")) {
                if (optionName != null) {
                    options.put(optionName, "");
                }
                optionName = arg.substring(2);
            } else {
                if (optionName == null) {
                    // ignore
                } else {
                    options.put(optionName, parseLiteral(arg, vars));
                    optionName = null;
                }
            }
        }
        if (optionName != null) {
            options.put(optionName, "");
        }
        return options;
    }

    private Task<?> patchDownloadMojangMappingsTask(Processor processor, Map<String, String> vars) {
        Map<String, String> options = parseOptions(processor.getArgs(), vars);
        if (!"DOWNLOAD_MOJMAPS".equals(options.get("task")) || !"client".equals(options.get("side")))
            return null;
        String version = options.get("version");
        String output = options.get("output");
        if (version == null || output == null)
            return null;

        LOG.info("Patching DOWNLOAD_MOJMAPS task");
        return new GameInstanceJsonDownloadTask(version, dependencyManager)
                .thenComposeAsync(json -> {
                    DownloadInfo mappings = fromNonNullJson(json, GameInstanceManifest.class)
                            .getDownloads().get(DownloadType.CLIENT_MAPPINGS);
                    if (mappings == null) {
                        throw new Exception("client_mappings download info not found");
                    }

                    List<URI> mappingsUrl = dependencyManager.getDownloadProvider()
                            .injectURLWithCandidates(mappings.getUrl());
                    var mappingsTask = new FileDownloadTask(
                            mappingsUrl,
                            Path.of(output),
                            FileDownloadTask.IntegrityCheck.of("SHA-1", mappings.getSha1()));
                    mappingsTask.setCaching(true);
                    mappingsTask.setCacheRepository(dependencyManager.getCacheRepository());
                    return mappingsTask;
                });
    }

    private Task<?> createProcessorTask(Processor processor, Map<String, String> vars) {
        Task<?> task = patchDownloadMojangMappingsTask(processor, vars);
        if (task == null) {
            task = new ProcessorTask(processor, vars);
        }
        task.onDone().register(
                () -> updateProgress(processorDoneCount.incrementAndGet(), processors.size()));
        return task;
    }

    @Override
    public void execute() throws Exception {
        tempDir = Files.createTempDirectory("forge_installer");

        if (buildManifest != null) {
            // The record proved every artifact this build produces is present and unchanged, so the
            // processors have nothing left to do and only the launch manifest has to be returned.
            // The libraries are still checked for completeness, because the step that downloads
            // them was skipped along with the build.
            dependencies.add(dependencyManager.checkComponentCompletionAsync(forgeVersion, true));
            setResult(GameInstancePatch.fromManifest(
                    forgeVersion,
                    GameComponentType.FORGE.getPatchId(),
                    selfVersion,
                    GameInstancePatch.PRIORITY_LOADER));
            return;
        }

        if (!Files.isRegularFile(minecraftJar)) {
            throw new FileNotFoundException("Minecraft client JAR not found: " + minecraftJar);
        }
        // External processors must not receive the shared cache path.
        Path isolatedMinecraftJar = tempDir.resolve("minecraft.jar");
        FileUtils.copyFile(minecraftJar, isolatedMinecraftJar);

        Map<String, String> vars = new HashMap<>();

        try (FileSystem fs = CompressingUtils.createReadOnlyZipFileSystem(installer)) {
            for (Map.Entry<String, String> entry : profile.getData().entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();

                vars.put(key, parseLiteral(value,
                        Collections.emptyMap(),
                        str -> {
                            Path dest = Files.createTempFile(tempDir, null, null);
                            FileUtils.copyFile(fs.getPath(str), dest);
                            return dest.toString();
                        }));
            }
        } catch (ZipException ex) {
            throw new ArtifactMalformedException("Malformed forge installer file", ex);
        }

        vars.put("SIDE", "client");
        vars.put("MINECRAFT_JAR", FileUtils.getAbsolutePath(isolatedMinecraftJar));
        vars.put("MINECRAFT_VERSION", profile.getMinecraft());
        vars.put("ROOT", FileUtils.getAbsolutePath(gameRepository.getBaseDirectory()));
        vars.put("INSTALLER", installer.toAbsolutePath().toString());
        vars.put("LIBRARY_DIR", FileUtils.getAbsolutePath(gameRepository.getLayout().getLibrariesDirectory()));

        updateProgress(0, processors.size());

        Task<?> processorsTask = Task.runSequentially(
                processors.stream()
                        .map(processor -> createProcessorTask(processor, vars))
                        .toArray(Task<?>[]::new));

        dependencies.add(
                processorsTask.thenComposeAsync(
                        dependencyManager.checkComponentCompletionAsync(forgeVersion, true)));

        setResult(GameInstancePatch.fromManifest(
                forgeVersion,
                GameComponentType.FORGE.getPatchId(),
                selfVersion,
                GameInstancePatch.PRIORITY_LOADER));
    }

    @Override
    public boolean doPostExecute() {
        return true;
    }

    @Override
    public void postExecute() throws Exception {
        try {
            if (buildManifest == null && installer != null && Files.isRegularFile(installer))
                recordBuild();
        } finally {
            FileUtils.deleteDirectory(tempDir);
        }
    }

    /// Writes the record of this build, so that installing the same loader again can skip it.
    ///
    /// The installers declare checksums for only a minority of their processors, so this record is
    /// the only place a later installation can learn which files the remaining steps produce.
    private void recordBuild() {
        try {
            if (recordedOutputs.isEmpty() || installProfileText == null || versionJsonText == null)
                return;

            LoaderBuildManifest.of(
                    buildKey,
                    DigestUtils.digestToString("SHA-1", installer),
                    installProfileText,
                    versionJsonText,
                    recordedOutputs).save(gameRepository);
            LOG.info("Recorded loader build " + selfVersion + " covering " + recordedOutputs.size() + " outputs");
        } catch (Exception e) {
            LOG.warning("Failed to record loader build " + selfVersion, e);
        }
    }
}
