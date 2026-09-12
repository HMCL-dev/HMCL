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
package org.jackhuang.hmcl;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/// Exercises the packaged shell bootstrap without a real Java installation or desktop tools.
@NotNullByDefault
@EnabledOnOs({OS.LINUX, OS.MAC})
public final class HMCLauncherScriptTest {
    /// Finds the selected SDKMAN runtime when shell startup files have not been loaded.
    @Test
    public void discoversSelectedRuntime(@TempDir Path root) throws Exception {
        Fixture fixture = new Fixture(root);
        fixture.selectJava(fixture.home.resolve(".sdkman/candidates"), "sdkman");
        fixture.assertJava("sdkman");
    }

    /// Resolves custom roots, with the candidates directory taking precedence over SDKMAN_DIR.
    @ParameterizedTest
    @ValueSource(strings = {"sdkman", "candidates", "both"})
    public void discoversCustomRoots(String configuration, @TempDir Path root) throws Exception {
        Fixture fixture = new Fixture(root);
        Path sdkman = root.resolve("custom sdkman");
        Path candidates = root.resolve("custom candidates");
        fixture.selectJava(fixture.home.resolve(".sdkman/candidates"), "default");
        if (!configuration.equals("candidates")) {
            fixture.environment.put("SDKMAN_DIR", sdkman.toString());
            fixture.selectJava(sdkman.resolve("candidates"), "custom");
        }
        if (!configuration.equals("sdkman")) {
            fixture.environment.put("SDKMAN_CANDIDATES_DIR", candidates.toString());
            fixture.selectJava(candidates, "custom");
        }
        fixture.assertJava("custom");
    }

    /// Empty SDKMAN variables use the default root or the supplied SDKMAN_DIR.
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    public void emptyVariablesFallBack(boolean customRoot, @TempDir Path root) throws Exception {
        Fixture fixture = new Fixture(root);
        Path sdkman = customRoot ? root.resolve("custom sdkman") : fixture.home.resolve(".sdkman");
        fixture.environment.put("SDKMAN_DIR", customRoot ? sdkman.toString() : "");
        fixture.environment.put("SDKMAN_CANDIDATES_DIR", "");
        fixture.selectJava(sdkman.resolve("candidates"), "sdkman");
        fixture.assertJava("sdkman");
    }

    /// Missing HOME must not turn any default SDKMAN location into a working-directory lookup.
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    public void missingHomeDoesNotSearchRelativePaths(boolean empty, @TempDir Path root) throws Exception {
        Fixture fixture = new Fixture(root);
        fixture.environment.remove("HOME");
        if (empty) {
            fixture.environment.put("HOME", "");
            fixture.environment.put("SDKMAN_DIR", "");
            fixture.environment.put("SDKMAN_CANDIDATES_DIR", "");
        }
        fixture.selectJava(root.resolve(".sdkman/candidates"), "relative-home");
        fixture.selectJava(root.resolve("candidates"), "relative-sdkman");
        fixture.selectJava(root, "relative-candidates");
        fixture.assertMissingJava();
    }

    /// Explicit SDKMAN locations remain usable without HOME.
    @ParameterizedTest
    @ValueSource(strings = {"SDKMAN_DIR", "SDKMAN_CANDIDATES_DIR"})
    public void customRootsDoNotRequireHome(String variable, @TempDir Path root) throws Exception {
        Fixture fixture = new Fixture(root);
        fixture.environment.remove("HOME");
        Path custom = root.resolve("custom root");
        fixture.environment.put(variable, custom.toString());
        fixture.selectJava(variable.equals("SDKMAN_DIR") ? custom.resolve("candidates") : custom, "sdkman");
        fixture.assertJava("sdkman");
    }

    /// Each existing lookup independently wins over SDKMAN's selected runtime.
    @ParameterizedTest
    @ValueSource(strings = {"HMCL_JAVA_HOME", "adjacent", "JAVA_HOME", "PATH"})
    public void existingLookupsTakePrecedence(String source, @TempDir Path root) throws Exception {
        Fixture fixture = new Fixture(root);
        fixture.selectJava(fixture.home.resolve(".sdkman/candidates"), "sdkman");
        Path javaHome = root.resolve("preferred java");
        switch (source) {
            case "adjacent" -> javaHome = fixture.launcher.getParent().resolve("jre-x64");
            case "PATH" -> javaHome = root;
            default -> fixture.environment.put(source, javaHome.toString());
        }
        fixture.writeJava(javaHome.resolve("bin/java"), "preferred");
        fixture.assertJava("preferred");
    }

    /// An invalid explicit override remains an error even with a usable SDKMAN selection.
    @Test
    public void invalidExplicitOverrideFails(@TempDir Path root) throws Exception {
        Fixture fixture = new Fixture(root);
        fixture.selectJava(fixture.home.resolve(".sdkman/candidates"), "sdkman");
        fixture.environment.put("HMCL_JAVA_HOME", root.resolve("missing java").toString());
        assertEquals(1, fixture.run());
        assertTrue(Files.readString(fixture.output).contains("HMCL_JAVA_HOME is invalid"));
        assertFalse(Files.exists(fixture.arguments));
    }

    /// Missing, dangling, or non-executable selections do not select another installed version.
    @ParameterizedTest
    @ValueSource(strings = {"missing", "dangling", "non-executable"})
    public void unusableSelectionFails(String state, @TempDir Path root) throws Exception {
        Fixture fixture = new Fixture(root);
        Path candidates = fixture.home.resolve(".sdkman/candidates");
        fixture.writeJava(candidates.resolve("java/other-version/bin/java"), "other");
        Path current = candidates.resolve("java/current");
        if (state.equals("dangling")) {
            Files.createSymbolicLink(current, Path.of("missing-version"));
        } else if (state.equals("non-executable")) {
            Path java = current.resolve("bin/java");
            fixture.writeJava(java, "non-executable");
            assertTrue(java.toFile().setExecutable(false, false));
        }
        fixture.assertMissingJava();
    }

    /// Custom and explicitly empty VM options retain the bootstrap's existing argument splitting.
    @ParameterizedTest
    @ValueSource(strings = {"-Xmx256m -Dfixture=true", ""})
    public void preservesCustomOptions(String options, @TempDir Path root) throws Exception {
        Fixture fixture = new Fixture(root);
        fixture.selectJava(fixture.home.resolve(".sdkman/candidates"), "sdkman");
        fixture.environment.put("HMCL_JAVA_OPTS", options);
        fixture.expectedOptions = options.isEmpty() ? List.of() : List.of("-Xmx256m", "-Dfixture=true");
        fixture.assertJava("sdkman");
    }

    /// A hermetic launcher process and Java stub shared by the discovery scenarios.
    @NotNullByDefault
    private static final class Fixture {
        /// Home directory used for the default SDKMAN installation.
        private final Path home;
        /// Copy of the packaged bootstrap, deliberately named with spaces.
        private final Path launcher;
        /// Stub invocation record, one argument per line after the runtime identifier.
        private final Path arguments;
        /// Combined subprocess output, redirected to avoid pipe-buffer deadlocks.
        private final Path output;
        /// Process builder whose environment never inherits shell initialization or Java settings.
        private final ProcessBuilder builder;
        /// Mutable environment belonging only to the subprocess.
        private final Map<String, String> environment;
        /// Expected options before the unchanged JAR invocation.
        private @Unmodifiable List<String> expectedOptions = List.of(
                "-XX:MinHeapFreeRatio=5", "-XX:MaxHeapFreeRatio=15", "-XX:G1PeriodicGCInterval=900000",
                "-XX:G1PeriodicGCSystemLoadThreshold=0.6", "-XX:+UseStringDeduplication");

        /// Copies the real resource and exposes only the utilities the bootstrap needs.
        private Fixture(Path root) throws IOException {
            assumeTrue(Files.isExecutable(Path.of("/bin/bash")), "Bash is required");
            home = Files.createDirectories(root.resolve("home"));
            launcher = Files.createDirectories(root.resolve("launcher directory")).resolve("HMCL launcher.sh");
            try (@Nullable InputStream resource = HMCLauncherScriptTest.class.getResourceAsStream("/assets/HMCLauncher.sh")) {
                assertNotNull(resource, "Missing packaged launcher resource");
                Files.copy(resource, launcher);
            }
            arguments = root.resolve("arguments");
            output = root.resolve("output");
            Path bin = Files.createDirectories(root.resolve("bin"));
            // Fix architecture so the adjacent-runtime case also works on ARM hosts.
            writeExecutable(bin.resolve("uname"), "#!/bin/bash\nprintf '%s\\n' x86_64\n");
            writeExecutable(bin.resolve("dirname"), "#!/bin/bash\nexec /usr/bin/dirname \"$@\"\n");
            builder = new ProcessBuilder("/bin/bash", "--noprofile", "--norc", launcher.toString());
            builder.directory(root.toFile()).redirectErrorStream(true).redirectOutput(output.toFile());
            environment = builder.environment();
            environment.clear();
            environment.put("PATH", bin.toString());
            environment.put("HOME", home.toString());
            environment.put("LANG", "C");
            environment.put("STUB_ARGUMENTS", arguments.toString());
            // An unset JAVA_HOME probes /bin/java in the existing script (a macOS system shim).
            if (Files.exists(Path.of("/bin/java"))) {
                environment.put("JAVA_HOME", root.resolve("missing java").toString());
            }
        }

        /// Creates the usual relative current symlink to a controlled installed version.
        private void selectJava(Path candidates, String name) throws IOException {
            writeJava(candidates.resolve("java/test-version/bin/java"), name);
            Files.createSymbolicLink(candidates.resolve("java/current"), Path.of("test-version"));
        }

        /// Records the chosen runtime and exact arguments, then exits with a distinctive status.
        private void writeJava(Path java, String name) throws IOException {
            writeExecutable(java, "#!/bin/bash\nprintf '%s\\n' '" + name
                    + "' \"$@\" > \"$STUB_ARGUMENTS\"\nexit 37\n");
        }

        /// Writes an executable script without relying on chmod in the controlled PATH.
        private void writeExecutable(Path path, String script) throws IOException {
            Files.createDirectories(path.getParent());
            Files.writeString(path, script);
            assertTrue(path.toFile().setExecutable(true, false));
        }

        /// Bounds execution and forcibly reaps hung processes and their children on failure.
        private int run() throws IOException, InterruptedException {
            Files.deleteIfExists(arguments);
            Process process = builder.start();
            try {
                assertTrue(process.waitFor(10, TimeUnit.SECONDS), "Launcher timed out");
                return process.exitValue();
            } finally {
                if (process.isAlive()) {
                    process.descendants().forEach(ProcessHandle::destroyForcibly);
                    process.destroyForcibly();
                    assertTrue(process.waitFor(5, TimeUnit.SECONDS), "Launcher did not terminate");
                }
            }
        }

        /// Checks exec status propagation and every option and JAR-path argument.
        private void assertJava(String name) throws IOException, InterruptedException {
            assertEquals(37, run(), () -> "Launcher output: " + readOutput());
            List<String> expected = new ArrayList<>();
            expected.add(name);
            expected.addAll(expectedOptions);
            expected.add("-jar");
            expected.add(launcher.toString());
            assertEquals(expected, Files.readAllLines(arguments));
        }

        /// Supplies failure diagnostics without hiding the original exit-status assertion.
        private String readOutput() {
            try {
                return Files.readString(output);
            } catch (IOException e) {
                return e.toString();
            }
        }

        /// Checks the existing console warning and proves no Java stub was executed.
        private void assertMissingJava() throws IOException, InterruptedException {
            assertEquals(1, run());
            assertTrue(Files.readString(output).contains("The Java runtime environment is required to run HMCL."));
            assertFalse(Files.exists(arguments));
        }
    }
}
